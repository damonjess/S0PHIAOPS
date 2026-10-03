package com.sophia.ops.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import kotlin.jvm.Volatile

class SecureActionAgent(
    private val context: Context,
    private val modelPath: String,
) {
    private val tag = "SecureActionAgent"

    @Volatile
    private var llmInference: LlmInference? = null
    
    @Volatile
    private var isClosed = false

    fun initializeEngine(): Boolean {
        if (llmInference != null) return true

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.i(tag, "Model file not found at $modelPath. Operating in offline fallback assistant mode.")
            return true
        }

        return try {
            val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .build()

            llmInference = LlmInference.createFromOptions(context, inferenceOptions)
            Log.i(tag, "Engine initialized successfully.")
            true
        } catch (t: Throwable) {
            Log.e(tag, "Init failed, falling back to offline assistant mode", t)
            llmInference = null
            true
        }
    }

    fun assessTacticalConcern(
        primaryConcern: String,
        environment: String,
        threatLevel: Int,
        evidence: List<AssessmentEvidence> = emptyList(),
        changes: List<ScanChange> = emptyList(),
    ): AiAnalysisResult {
        val severity = when {
            threatLevel >= 85 -> AssessmentSeverity.CRITICAL
            threatLevel >= 60 -> AssessmentSeverity.HIGH
            threatLevel >= 30 -> AssessmentSeverity.MEDIUM
            else -> AssessmentSeverity.LOW
        }
        val confidence = when {
            evidence.size >= 3 && changes.isNotEmpty() -> AssessmentConfidence.HIGH
            evidence.isNotEmpty() -> AssessmentConfidence.MEDIUM
            else -> AssessmentConfidence.LOW
        }
        val action = when (severity) {
            AssessmentSeverity.CRITICAL,
            AssessmentSeverity.HIGH -> "Open the flagged device details, verify whether it is recognized, and choose a follow-up action only after review."
            AssessmentSeverity.MEDIUM -> "Review the recorded changes and mark familiar devices as trusted if you can confirm them."
            AssessmentSeverity.LOW -> "No immediate action is needed. Keep monitoring for repeat observations or meaningful changes."
        }
        val uncertainty = when {
            evidence.isEmpty() -> "No supporting device evidence is available yet; this assessment is provisional."
            environment.contains("Low-Noise", ignoreCase = true) -> "Radio metadata shows proximity and change patterns, but cannot establish ownership, identity, or intent."
            else -> "Radio metadata can indicate change and proximity, but cannot establish ownership, identity, or malicious intent."
        }

        return AiAnalysisResult(
            assessment = AiAssessment(
                severity = severity,
                confidence = confidence,
                headline = primaryConcern,
                evidence = evidence.take(5),
                changes = changes.take(5),
                uncertainty = uncertainty,
                recommendedAction = action,
                requiresConfirmation = severity >= AssessmentSeverity.MEDIUM,
            ),
        )
    }

    fun analyzeDevice(
        name: String,
        address: String,
        vendor: String?,
        type: String,
        signal: Int,
        timesSeen: Int,
        riskScore: Int
    ): String {
        // The verdict itself is fully deterministic — no model involved, so it can never be wrong or invented.
        val verdict = when {
            riskScore > 70 -> "This device is flagged as high risk and worth investigating."
            riskScore > 30 -> "This device has an elevated risk score — keep an eye on it."
            timesSeen > 20 -> "This is a frequently-seen, low-risk device — likely something nearby you own or pass often."
            else -> "This appears to be an ordinary, low-risk device."
        }

        val vendorNote = if (vendor == "Private Address (Randomized)") {
            " Its address is randomized for privacy, which is normal for modern phones and can't be traced to a manufacturer."
        } else if (!vendor.isNullOrBlank() && vendor != "Unknown Vendor") {
            " Identified vendor: $vendor."
        } else {
            ""
        }

        return verdict + vendorNote
    }    @Synchronized
    fun askQuestion(
        question: String,
        environmentContext: String,
        history: List<ChatTurn> = emptyList(),
    ): String {
        val inference = llmInference
        if (inference == null || isClosed || !File(modelPath).exists()) {
            return generateFallbackAnswer(question, environmentContext)
        }

        val prompt = buildChatPrompt(question, environmentContext, history)

        return try {
            val raw = inference.generateResponse(prompt)
            sanitizeChatResponse(raw)
        } catch (t: Throwable) {
            Log.e(tag, "Chat question failed", t)
            generateFallbackAnswer(question, environmentContext)
        }
    }

    private fun generateFallbackAnswer(question: String, environmentContext: String): String {
        val q = question.lowercase()
        return when {
            q.contains("how many") || q.contains("count") || q.contains("how much") -> {
                val wifiCount = Regex("wifi=(\\d+)").find(environmentContext)?.groupValues?.get(1)
                val btCount = Regex("bluetooth=(\\d+)").find(environmentContext)?.groupValues?.get(1)
                if (wifiCount != null && btCount != null) {
                    "My current scan context shows $wifiCount Wi-Fi network(s) and $btCount Bluetooth device(s). Open the Devices tab for the full list."
                } else {
                    "I can't confirm exact counts in offline mode — check the Devices tab for the live list."
                }
            }
            q.contains("public wi-fi") || q.contains("public wifi") || q.contains("safe to use") ->
                "Public Wi-Fi networks can expose your traffic to interception. Always use a trusted VPN and verify HTTPS connections before transmitting sensitive data."
            q.contains("vpn") ->
                "A VPN encrypts your traffic on untrusted networks, but it does not make a malicious hotspot safe — still verify sites use HTTPS and avoid entering credentials."
            q.contains("bluetooth") || q.contains("ble") || q.contains("beacon") ->
                "Unknown Bluetooth devices and beacons nearby may track your movement. Consider disabling Bluetooth when not in use or ignoring unrecognized pairings."
            q.contains("threat") || q.contains("risk") || q.contains("secure") -> 
                "Review the radar screen for any high-risk signals or unauthorized devices. Ensure your device firmware and security settings are up to date."
            q.contains("password") || q.contains("credential") -> 
                "Never enter credentials over unencrypted HTTP or suspicious Wi-Fi networks. Use multi-factor authentication everywhere."
            else -> 
                "SOPHIA security assistant active (offline mode). Based on current environment ($environmentContext), monitor surrounding signals and investigate any unfamiliar devices immediately."
        }
    }

    fun close() {
        isClosed = true
        try {
            llmInference?.close()
        } catch (t: Throwable) {
            Log.e(tag, "Error closing inference", t)
        }
        llmInference = null
    }
}

/**
 * Grounding rules placed in the current user turn, right before generation,
 * so the model always sees them alongside the freshest scan context.
 */
private val GROUNDING_RULES = """
You are S0PHIA, a network security assistant that runs fully on-device inside a Wi-Fi and Bluetooth scanning app.
Ground every answer in the SCAN CONTEXT below. Never invent device names, addresses, IP addresses, or scan results: if the context does not contain the answer, say you do not have that data on-device.
Answer in 2-3 plain sentences. No markdown, no bullet points, no labels.
Only mention the scan context when it is actually relevant to the question — otherwise answer generally.
""".trimIndent()

/**
 * Assembles the Gemma-format chat prompt. Prior conversation turns come first
 * so follow-up questions keep their meaning, then the current turn re-states
 * the grounding rules and the latest scan context.
 */
internal fun buildChatPrompt(
    question: String,
    environmentContext: String,
    history: List<ChatTurn> = emptyList(),
): String = buildString {
    history.forEach { turn ->
        if (turn.role == ChatTurn.ROLE_USER) {
            append("<start_of_turn>user\n${turn.text}\n<end_of_turn>\n")
        } else {
            append("<start_of_turn>model\n${turn.text}\n<end_of_turn>\n")
        }
    }
    append("<start_of_turn>user\n")
    append(GROUNDING_RULES)
    append("\n\nSCAN CONTEXT:\n")
    append(environmentContext)
    append("\n\nQuestion: ")
    append(question)
    append("\n<end_of_turn>\n")
    append("<start_of_turn>model")
}

/**
 * Cleans a raw model response: strips generation tags, leftover markdown,
 * role labels, and runaway length so answers render cleanly in the chat card.
 */
internal fun sanitizeChatResponse(raw: String): String {
    var text = raw
        .substringBefore("<end_of_turn>")
        .replace(Regex("<.*?>"), "")
        .replace(Regex("[`*#~_]+"), "")
        .replace(Regex("\\s+"), " ")
        .replace(
            Regex("^(?:model\\s+)?(?:s0phia|sophia|assistant|model)\\s*:\\s*", RegexOption.IGNORE_CASE),
            "",
        )
        .trim()

    if (text.length > 600) {
        val cut = text.lastIndexOf(' ', 600)
        text = text.substring(0, if (cut > 200) cut else 600).trimEnd() + "…"
    }

    return text.ifBlank { "I don't have a good answer for that right now." }
}
