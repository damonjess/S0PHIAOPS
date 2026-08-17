package com.sophia.ops.data

import android.content.Context
import android.util.Base64
import com.sophia.ops.ai.AiAssessment
import com.sophia.ops.ai.AssessmentSeverity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DeviceDisposition {
    UNREVIEWED,
    TRUSTED,
    WATCHLIST,
}

enum class IncidentStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED,
}

data class IncidentRecord(
    val id: String,
    val createdAt: Long,
    val severity: AssessmentSeverity,
    val confidence: String,
    val headline: String,
    val evidenceSummary: String,
    val changeSummary: String,
    val recommendedAction: String,
    val status: IncidentStatus = IncidentStatus.OPEN,
    val acknowledgedAt: Long? = null,
    val resolvedAt: Long? = null,
)

data class DailyBrief(
    val dateLabel: String,
    val incidentCount: Int,
    val openCount: Int,
    val watchlistCount: Int,
    val highestSeverity: AssessmentSeverity?,
    val headline: String,
    val summary: String,
)

/**
 * Stores user review labels and compact assessment summaries locally. This
 * deliberately avoids a Room schema migration for user annotations and keeps
 * all investigation metadata on the device.
 */
class InvestigationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "sophia_investigation_store",
        Context.MODE_PRIVATE,
    )

    fun loadDispositions(): Map<String, DeviceDisposition> {
        val raw = preferences.getString(DISPOSITIONS_KEY, "").orEmpty()
        if (raw.isBlank()) return emptyMap()
        return raw.split(',').mapNotNull { entry ->
            val parts = entry.split(':', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val address = decode(parts[0]) ?: return@mapNotNull null
            val disposition = runCatching { DeviceDisposition.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
            address to disposition
        }.toMap()
    }

    fun saveDisposition(address: String, disposition: DeviceDisposition) {
        val values = loadDispositions().toMutableMap()
        if (disposition == DeviceDisposition.UNREVIEWED) values.remove(address) else values[address] = disposition
        val serialized = values.entries
            .sortedBy { it.key }
            .joinToString(",") { "${encode(it.key)}:${it.value.name}" }
        preferences.edit().putString(DISPOSITIONS_KEY, serialized).apply()
    }

    fun loadIncidents(): List<IncidentRecord> {
        val raw = preferences.getString(INCIDENTS_KEY, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull(::decodeIncident).sortedByDescending { it.createdAt }.toList()
    }

    fun addIncident(assessment: AiAssessment): IncidentRecord {
        val incident = IncidentRecord(
            id = "${assessment.createdAt}-${assessment.severity.name}",
            createdAt = assessment.createdAt,
            severity = assessment.severity,
            confidence = assessment.confidence.name,
            headline = assessment.headline,
            evidenceSummary = assessment.evidence.joinToString(" | ") { "${it.label}: ${it.detail}" },
            changeSummary = assessment.changes.joinToString(" | ") { "${it.label}: ${it.detail}" },
            recommendedAction = assessment.recommendedAction,
        )
        persistIncidents((listOf(incident) + loadIncidents()).distinctBy { it.id }.take(MAX_INCIDENTS))
        return incident
    }

    fun updateIncidentStatus(id: String, status: IncidentStatus) {
        val now = System.currentTimeMillis()
        val records = loadIncidents().map { incident ->
            if (incident.id != id) incident else when (status) {
                IncidentStatus.OPEN -> incident.copy(status = status, acknowledgedAt = null, resolvedAt = null)
                IncidentStatus.ACKNOWLEDGED -> incident.copy(status = status, acknowledgedAt = incident.acknowledgedAt ?: now, resolvedAt = null)
                IncidentStatus.RESOLVED -> incident.copy(status = status, resolvedAt = now)
            }
        }
        persistIncidents(records)
    }

    fun buildDailyBrief(now: Long = System.currentTimeMillis()): DailyBrief {
        val startOfDay = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val today = loadIncidents().filter { it.createdAt >= startOfDay }
        val open = today.filter { it.status != IncidentStatus.RESOLVED }
        val watchlist = today.count { it.evidenceSummary.contains("Watchlist device", ignoreCase = true) }
        val highest = today.maxByOrNull { severityRank(it.severity) }?.severity
        val headline = when {
            today.isEmpty() -> "No local analyst incidents have been recorded today."
            open.any { it.severity == AssessmentSeverity.CRITICAL || it.severity == AssessmentSeverity.HIGH } -> "${open.size} incident(s) still need review."
            open.isNotEmpty() -> "${open.size} incident(s) are awaiting review."
            else -> "All of today’s local incidents are resolved."
        }
        val summary = buildString {
            append("${today.size} incident(s) today")
            if (watchlist > 0) append(" · $watchlist watchlist observation(s)")
            if (highest != null) append(" · highest severity ${highest.name.lowercase()}")
        }
        return DailyBrief(
            dateLabel = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(now)),
            incidentCount = today.size,
            openCount = open.size,
            watchlistCount = watchlist,
            highestSeverity = highest,
            headline = headline,
            summary = summary,
        )
    }

    fun clearIncidents() {
        preferences.edit().remove(INCIDENTS_KEY).apply()
    }

    fun clearAll() {
        preferences.edit().remove(INCIDENTS_KEY).remove(DISPOSITIONS_KEY).apply()
    }

    private fun persistIncidents(records: List<IncidentRecord>) {
        preferences.edit().putString(
            INCIDENTS_KEY,
            records.sortedByDescending { it.createdAt }.take(MAX_INCIDENTS).joinToString("\n", transform = ::encodeIncident),
        ).apply()
    }

    private fun encodeIncident(record: IncidentRecord): String = listOf(
        record.id,
        record.createdAt.toString(),
        record.severity.name,
        record.confidence,
        record.headline,
        record.evidenceSummary,
        record.changeSummary,
        record.recommendedAction,
        record.status.name,
        record.acknowledgedAt?.toString().orEmpty(),
        record.resolvedAt?.toString().orEmpty(),
    ).joinToString("\t") { encode(it) }

    private fun decodeIncident(line: String): IncidentRecord? {
        val fields = line.split('\t').mapNotNull(::decode)
        if (fields.size !in setOf(8, 11)) return null
        return runCatching {
            IncidentRecord(
                id = fields[0],
                createdAt = fields[1].toLong(),
                severity = AssessmentSeverity.valueOf(fields[2]),
                confidence = fields[3],
                headline = fields[4],
                evidenceSummary = fields[5],
                changeSummary = fields[6],
                recommendedAction = fields[7],
                status = fields.getOrNull(8)?.let(IncidentStatus::valueOf) ?: IncidentStatus.OPEN,
                acknowledgedAt = fields.getOrNull(9)?.toLongOrNull(),
                resolvedAt = fields.getOrNull(10)?.toLongOrNull(),
            )
        }.getOrNull()
    }

    private fun severityRank(severity: AssessmentSeverity): Int = when (severity) {
        AssessmentSeverity.LOW -> 1
        AssessmentSeverity.MEDIUM -> 2
        AssessmentSeverity.HIGH -> 3
        AssessmentSeverity.CRITICAL -> 4
    }

    private fun encode(value: String): String = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun decode(value: String): String? = runCatching {
        String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val DISPOSITIONS_KEY = "device_dispositions_v1"
        const val INCIDENTS_KEY = "incident_history_v1"
        const val MAX_INCIDENTS = 20
    }
}
