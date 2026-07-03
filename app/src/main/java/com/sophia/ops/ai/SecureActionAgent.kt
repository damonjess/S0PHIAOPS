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

    fun analyzeDevice(
        name: String,
        address: String,
        vendor: String?,
        type: String,
        signal: Int,
        timesSeen: Int,
        riskScore: Int
    ): String {
        val inference = llmInference
        if (inference == null || isClosed) return "Tactical AI Unavailable."

        val prompt = """
<start_of_turn>user
[TASK]
Identify likely device class and operational intent for this electronic signature. 
Do not repeat the target data. Provide exactly 2 short technical sentences.

[DATA]
Target: $name ($address)
Vendor: ${vendor ?: "Unknown"}
RSSI: ${signal}dBm / RISK: $riskScore
<end_of_turn>
<start_of_turn>model
Analysis:""".trimIndent()

        return try {
            val raw = inference.generateResponse(prompt)
            val output = raw.substringAfter("Analysis:")
                .replace(Regex("(?i)okay,.*"), "")
                .replace(Regex("(?i)sure,.*"), "")
                .replace(Regex("\\*\\*"), "")
                .replace(Regex("\\\\n"), " ")
                .replace(Regex("\\n"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            
            if (output.length < 10) {
                "Signature analysis complete. Target categorized as $type device with $riskScore% threat probability."
            } else {
                output
            }
        } catch (t: Throwable) {
            Log.e(tag, "Device analysis failed", t)
            "Analysis failed: ${t.localizedMessage ?: t.javaClass.simpleName}"
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
