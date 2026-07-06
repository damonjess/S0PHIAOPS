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
        threatLevel: Int
    ): AiAnalysisResult {
        val inference = llmInference
        if (inference == null || isClosed) {
            return AiAnalysisResult("Subsystem Standby.", "Core initialization pending.", "low")
        }

        val prompt = """
<start_of_turn>user
SIGINT SITUATION REPORT
ZONE CONTEXT: $environment
LOCAL ALERT: $primaryConcern
THREAT LEVEL: $threatLevel/100

TASK: 
As SOPHIA, cross-reference the Local Alert with the Zone Context. 
Provide a high-level technical implication and one tactical directive.
Do not use conversational filler. Provide exactly two technical sentences.
<end_of_turn>
<start_of_turn>model
Analysis:""".trimIndent()

        return try {
            val raw = inference.generateResponse(prompt)
            Log.d(tag, "AI Raw: $raw")
            
            // Clean up model output aggressively
            val cleaned = raw.substringAfter("Analysis:")
                .replace(Regex("(?i)okay,.*"), "")
                .replace(Regex("(?i)sure,.*"), "")
                .replace(Regex("(?i)directive:"), "")
                .replace(Regex("\\*\\*"), "")
                .replace(Regex("\\\\n"), " ")
                .replace(Regex("\\n"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            val sentences = cleaned.split(Regex("(?<=[.!?])\\s+"))
            val summary = sentences.getOrNull(0) ?: "Target signature identified in environment."
            val action = sentences.drop(1).joinToString(" ").ifBlank { "Maintain current monitoring posture." }

            AiAnalysisResult(
                summary.take(200), 
                action.take(150), 
                if (threatLevel > 50) "high" else "medium"
            )
        } catch (t: Throwable) {
            Log.e(tag, "Assessment failed", t)
            AiAnalysisResult("Strategic analysis suspended.", "Monitor logs for anomalies.", "low")
        }
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
        val factualPrefix = if (vendor == "Private Address (Randomized)") {
            "This is a randomized privacy MAC address, common on modern smartphones — it changes periodically and cannot be traced to a specific manufacturer. "
        } else {
            ""
        }

        val inference = llmInference ?: return factualPrefix + "AI engine unavailable for further analysis."

        val vendorLine = if (!vendor.isNullOrBlank() && vendor != "Unknown Vendor" && vendor != "Private Address (Randomized)") {
            "Vendor: $vendor"
        } else {
            "Vendor: unknown"
        }

        val prompt = """
<start_of_turn>user
Device: "$name" ($type)
$vendorLine
Signal strength: ${signal}dBm
Times seen: $timesSeen
Risk score: $riskScore/100

In one plain sentence, comment on whether this specific device looks ordinary or worth keeping an eye on, referencing at least one concrete detail above (its name, vendor, how often it's been seen, or its risk score).
Only use the facts given above. Do not invent radio/technical terms (e.g. do not mention spectral density, modulation, or similar) that were not provided.
<end_of_turn>
<start_of_turn>model
""".trimIndent()

        return try {
            val raw = inference.generateResponse(prompt)
            factualPrefix + sanitizeAdvice(raw)
        } catch (t: Throwable) {
            Log.e(tag, "Device analysis failed", t)
            factualPrefix + "Verdict unavailable due to an analysis error."
        }
    }

    private fun sanitizeAdvice(raw: String): String {
        return raw.substringAfter("Analysis:")
            .replace(Regex("(?i)okay,.*"), "")
            .replace(Regex("(?i)sure,.*"), "")
            .replace(Regex("\\*\\*"), "")
            .replace(Regex("\\\\n"), " ")
            .replace(Regex("\\n"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
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
