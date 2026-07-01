package com.sophia.ops.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.jvm.Volatile

class SecureActionAgent(private val context: Context, private val modelPath: String) {

    @Volatile
    private var llmEngine: LlmInference? = null
    
    // Status flag to report back to your ViewModels safely
    var isReady = false
        private set

    /**
     * Shifts the heavy weight-loading disk operations off the Main UI Thread.
     * Returns true if initialized correctly, or false if the file is missing or corrupted.
     * 
     * NOTE: This is now a standard function to support dynamic reflection-based loading.
     * Ensure this is called from a background thread (e.g. Dispatchers.IO).
     */
    fun initializeEngine(): Boolean {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            android.util.Log.e("SecureActionAgent", "Model file does not exist at $modelPath")
            isReady = false
            return false
        }
        
        if (!modelFile.canRead()) {
            android.util.Log.e("SecureActionAgent", "Model file exists but is NOT READABLE at $modelPath. Check permissions.")
            isReady = false
            return false
        }

        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(90)
                .setTemperature(0.2f)
                .build()
            
            // This blocking operational layer now executes entirely on the caller's thread
            llmEngine = LlmInference.createFromOptions(context, options)
            isReady = true
            android.util.Log.i("SecureActionAgent", "AI Engine initialized successfully")
            true
        } catch (e: Throwable) {
            android.util.Log.e("SecureActionAgent", "Failed to initialize AI Engine: ${e.message}", e)
            isReady = false
            false
        }
    }

    /**
     * Executes AI inference. Ensure this is called from a background thread.
     */
    fun generateActionAdvice(threatScore: Int, activeDevices: String): String {
        if (!isReady || llmEngine == null) {
            return "SOPHIA AI Engine Offline: Missing or uninitialized model file weights."
        }

        val prompt = """
            You are SOPHIA OPS Cyber-Defense Tactical AI. System Parameters:
            - Threat Vector Level: $threatScore%
            - Environment Scan Snapshot: $activeDevices
            
            Provide a strict, maximum two-sentence action-oriented operational recommendation to protect the user's network perimeter. 
            Do not use greetings or introductions. Be direct.
        """.trimIndent()

        return try {
            synchronized(this) {
                llmEngine?.generateResponse(prompt) ?: "Inference engine failed to respond."
            }
        } catch (e: Throwable) {
            android.util.Log.e("SecureActionAgent", "Inference failed", e)
            "Analysis failed: ${e.localizedMessage}"
        }
    }
}