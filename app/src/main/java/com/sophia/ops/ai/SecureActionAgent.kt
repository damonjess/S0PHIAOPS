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
    private var llmSession: LlmInferenceSession? = null
    private var isReady = false

    fun initializeEngine(): Boolean {
        if (isReady && llmInference != null && llmSession != null) return true

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
                .setMaxTokens(64)
                .build()

            llmInference = LlmInference.createFromOptions(context, inferenceOptions)

            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .build()

            llmSession = LlmInferenceSession.createFromOptions(
                llmInference!!,
                sessionOptions
            )

            isReady = true
            true
        } catch (t: Throwable) {
            Log.e(tag, "Init failed", t)
            isReady = false
            llmSession = null
            llmInference = null
            false
        }
    }

    fun generateActionAdvice(threatScore: Int, activeDevices: String): String {
        val session = llmSession
            ?: return "Tactical AI Unavailable: Core initialization pending or failed."

        return try {
            val prompt = """
Threat level: $threatScore
Environment: $activeDevices

Provide exactly 2 short sentences of tactical network defense advice.
Do not use lists, labels, or extra explanation.
""".trimIndent()

            session.addQueryChunk(prompt)
            val raw = session.generateResponse()
            limitToTwoSentences(normalizeResponse(raw))
        } catch (t: Throwable) {
            Log.e(tag, "Inference failed", t)
            "Strategic analysis suspended: Maintain current defensive posture and monitor logs."
        }
    }

    fun close() {
        try {
            llmSession?.close()
        } catch (t: Throwable) {
            Log.e(tag, "Error closing session", t)
        }

        try {
            llmInference?.close()
        } catch (t: Throwable) {
            Log.e(tag, "Error closing inference", t)
        }

        llmSession = null
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
            parts.isEmpty() -> "Strategic analysis suspended: Maintain current defensive posture and monitor logs."
            parts.size == 1 -> parts[0]
            else -> parts.take(2).joinToString(" ")
        }
    }
}