package com.sophia.ops.ai

enum class AssessmentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

enum class AssessmentConfidence {
    LOW,
    MEDIUM,
    HIGH,
}

data class DeviceSummary(
    val address: String,
    val name: String,
    val vendor: String,
    val type: String,
    val riskScore: Int,
    val isNew: Boolean,
    val signal: Int,
    val timesSeen: Int = 1,
    val security: String? = null,
)

data class AssessmentEvidence(
    val label: String,
    val detail: String,
)

data class ScanChange(
    val label: String,
    val detail: String,
)

data class AiAssessment(
    val severity: AssessmentSeverity,
    val confidence: AssessmentConfidence,
    val headline: String,
    val evidence: List<AssessmentEvidence>,
    val changes: List<ScanChange>,
    val uncertainty: String,
    val recommendedAction: String,
    val requiresConfirmation: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
)

data class AiAnalysisResult(
    val assessment: AiAssessment,
) {
    val riskSummary: String get() = assessment.headline
    val recommendedAction: String get() = assessment.recommendedAction
    val confidence: String get() = assessment.confidence.name.lowercase()
}
