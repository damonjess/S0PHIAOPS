package com.sophia.ops.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.jvm.Volatile

object CyberDefenseAnalyst {
    @Volatile
    private var llmInference: LlmInference? = null
    private const val MODEL_PATH = "/data/local/tmp/gemma-2b-it-cpu.bin"

    /**
     * Initializes the local LLM. This should be called on a background thread.
     */
    fun initialize(context: Context) {
        if (llmInference != null) return
        
        val modelFile = File(MODEL_PATH)
        if (!modelFile.exists()) {
            Log.e("CyberDefenseAnalyst", "AI Analyst Error: Model file not found at $MODEL_PATH")
            return
        }

        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(MODEL_PATH)
                .setMaxTokens(512)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            Log.i("CyberDefenseAnalyst", "AI Analyst initialized successfully")
        } catch (e: Throwable) {
            Log.e("CyberDefenseAnalyst", "Failed to initialize AI Analyst: ${e.message}", e)
        }
    }

    /**
     * Generates a defense strategy based on the current threat metrics.
     */
    suspend fun analyzeThreat(threatScore: Int, deviceCount: Int): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are an autonomous cyber-defense analyst for the SOPHIA OPS tactical system.
            Current Environment:
            - Total Detected Signals: $deviceCount
            - System Threat Metric: $threatScore%
            
            Task: Provide a concise, professional 3-sentence countermeasure strategy for the user. 
            Focus on privacy and physical signal security. Do not use markdown.
        """.trimIndent()

        return@withContext try {
            // Ensure generateResponse is called on a background thread consistently.
            // Using synchronized to prevent concurrent access to the engine if it's not thread-safe.
            synchronized(this) {
                llmInference?.generateResponse(prompt) ?: "Analyst offline: Model not initialized."
            }
        } catch (e: Exception) {
            "Analyst Error: ${e.localizedMessage}"
        }
    }
}