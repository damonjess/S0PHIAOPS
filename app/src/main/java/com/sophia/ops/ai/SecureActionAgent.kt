package com.sophia.ops.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecureActionAgent(context: Context, private val modelPath: String) {

    private var llmInference: LlmInference? = null

    init {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(128)
            .build()
        llmInference = LlmInference.createFromOptions(context, options)
    }

    suspend fun analyzeThreatBrief(threatScore: Int, systemLogs: String): String = withContext(Dispatchers.Default) {
        val prompt = """
            You are SOPHIA OPS Tactical AI. System status parameters:
            - Current Threat Vector Score: $threatScore%
            - Hardware Environmental Logs: $systemLogs
            
            Provide a strict, 2-sentence tactical recommendation to protect the user's data sovereignty. 
            Do not use greetings. Be direct.
        """.trimIndent()

        return@withContext try {
            llmInference?.generateResponse(prompt) ?: "Inference engine unavailable."
        } catch (e: Exception) {
            "Analysis failed: ${e.localizedMessage}"
        }
    }
}