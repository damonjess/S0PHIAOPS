package com.sophia.ops.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
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
                .setMaxTokens(128)
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

        var session: LlmInferenceSession? = null

        return try {
            Log.d(tag, "Creating session...")
            session = LlmInferenceSession.createFromOptions(
                inference,
                LlmInferenceSession.LlmInferenceSessionOptions.builder().build()
            )

            Log.d(tag, "Adding query chunk...")
            session.addQueryChunk(prompt)
            
            Log.d(tag, "Generating response...")
            val raw = session.generateResponse()
            Log.d(tag, "Response received: length=${raw.length}")
            
            if (raw.isBlank()) {
                "Strategic analysis returned no actionable data. Maintain current posture."
            } else {
                limitToTwoSentences(normalizeResponse(raw))
            }
        } catch (t: Throwable) {
            Log.e(tag, "Inference failed", t)
            "Strategic analysis suspended: ${t.localizedMessage ?: t.javaClass.simpleName}"
        } finally {
            try {
                session?.close()
            } catch (closeError: Throwable) {
                Log.e(tag, "Error closing inference session", closeError)
            }
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
Analyze this network device for potential security risks:
Name: $name
Address: $address
Vendor: ${vendor ?: "Unknown"}
Type: $type
Signal: ${signal}dBm
Times Seen: $timesSeen
Internal Risk Score: $riskScore/100

Provide a 1-sentence tactical assessment of what this device likely is and if it should be trusted.<end_of_turn>
<start_of_turn>model
""".trimIndent()

        var session: LlmInferenceSession? = null
        return try {
            session = LlmInferenceSession.createFromOptions(
                inference,
                LlmInferenceSession.LlmInferenceSessionOptions.builder().build()
            )
            session.addQueryChunk(prompt)
            val raw = session.generateResponse()
            normalizeResponse(raw)
        } catch (t: Throwable) {
            "Analysis failed: ${t.localizedMessage}"
        } finally {
            session?.close()
        }
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
        return text.replace(Regex("\\s+"), " ").trim()
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
