package com.sophia.ops.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

class SecureActionAgent(
    private val context: Context,
    private val modelPath: String,
) {
    private val tag = "SecureActionAgent"

    private var llmInference: LlmInference? = null
    private var isReady = false

    fun initializeEngine(): Boolean {
        if (isReady && llmInference != null) return true

        return try {
            val modelFile = File(modelPath)
            Log.i(tag, "Model path: $modelPath")
            Log.i(tag, "Exists: ${modelFile.exists()} size=${modelFile.length()}")

            if (!modelFile.exists()) {
                isReady = false
                return false
            }

            val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .build()

            llmInference = LlmInference.createFromOptions(context, inferenceOptions)
            isReady = true
            true
        } catch (t: Throwable) {
            Log.e(tag, "Init failed", t)
            isReady = false
            llmInference = null
            false
        }
    }

    fun generateActionAdvice(threatScore: Int, activeDevices: String): String {
        val inference = llmInference
            ?: return "Tactical AI Unavailable: Core initialization pending or failed."

        val prompt = """
<start_of_turn>user
Threat level: $threatScore
Environment: $activeDevices

Provide exactly 2 short sentences of tactical network defense advice. 
Do not use lists, labels, or extra explanation.<end_of_turn>
<start_of_turn>model
""".trimIndent()

        return try {
            Log.d(tag, "Generating response directly...")
            val raw = inference.generateResponse(prompt)
            Log.d(tag, "Response received: length=${raw.length}")
            
            if (raw.isBlank()) {
                "Strategic analysis returned no actionable data. Maintain current posture."
            } else {
                limitToTwoSentences(normalizeResponse(raw))
            }
        } catch (t: Throwable) {
            Log.e(tag, "Inference failed", t)
            "Strategic analysis suspended: ${t.localizedMessage ?: t.javaClass.simpleName}"
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
            ?: return "Tactical AI Unavailable."

        val prompt = """
<start_of_turn>user
Identify this device:
NAME: $name
MAC: $address
VENDOR: ${vendor ?: "Unknown"}
TYPE: $type

If VENDOR is "Private Address", explain it is a modern smartphone privacy feature. Otherwise, analyze the name and MAC to guess the manufacturer. State if it is likely "Friendly" or "Suspicious". 
Provide exactly 2 sentences. Do not repeat these instructions.<end_of_turn>
<start_of_turn>model
""".trimIndent()

        return try {
            val raw = inference.generateResponse(prompt)
            cleanAiResponse(raw)
        } catch (t: Throwable) {
            Log.e(tag, "Device analysis failed", t)
            "Analysis failed: ${t.localizedMessage ?: t.javaClass.simpleName}"
        }
    }

    private fun cleanAiResponse(text: String): String {
        return text.replace(Regex("\\*\\*"), "") // Remove bold markers
            .replace(Regex("\\\\n"), " ")       // Remove literal \n strings
            .replace(Regex("\\n"), " ")         // Remove actual newlines
            .replace(Regex("\\s+"), " ")        // Collapse whitespace
            .trim()
    }

    fun close() {
        try {
            llmInference?.close()
        } catch (t: Throwable) {
            Log.e(tag, "Error closing inference", t)
        }

        llmInference = null
        isReady = false
    }

    private fun normalizeResponse(text: String): String {
        return cleanAiResponse(text)
    }

    private fun limitToTwoSentences(text: String): String {
        val parts = Regex("(?<=[.!?])\\s+")
            .split(text)
            .filter { it.isNotBlank() }

        return when {
            parts.isEmpty() -> "Tactical posture maintained. Monitor logs for anomalies."
            parts.size == 1 -> parts[0]
            else -> parts.take(2).joinToString(" ")
        }
    }
}
