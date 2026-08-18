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

        return try {
            val modelFile = File(modelPath)
            if (!modelFile.exists()) return false

            val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .build()

            llmInference = LlmInference.createFromOptions(context, inferenceOptions)
            Log.i(tag, "Engine initialized successfully.")
            true
        } catch (t: Throwable) {
            Log.e(tag, "Init failed", t)
            llmInference = null
            false
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

    @Synchronized
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
    }


    @Synchronized
    fun askQuestion(question: String, environmentContext: String): String {
        val inference = llmInference
        if (inference == null || isClosed) return "AI engine not active. Please initialize it first."

        val prompt = """
<start_of_turn>user
You are SOPHIA, a network security assistant embedded in a scanning app.
Current environment: $environmentContext

Answer the user's question in 2-3 plain sentences. Only mention the environment info above if it's actually relevant to the question — otherwise just answer generally. Do not use markdown, labels, or bullet points.

Question: $question
<end_of_turn>
<start_of_turn>model
""".trimIndent()

        return try {
            val raw = inference.generateResponse(prompt)
            sanitizeChatResponse(raw)
        } catch (t: Throwable) {
            Log.e(tag, "Chat question failed", t)
            "Sorry, I couldn't process that question: ${t.localizedMessage ?: t.javaClass.simpleName}"
        }
    }

    private fun sanitizeChatResponse(raw: String): String {
        return raw
            .replace(Regex("<.*?>"), "")
            .replace(Regex("\\*\\*"), "")
            .replace(Regex("\\n+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "I don't have a good answer for that right now." }
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
