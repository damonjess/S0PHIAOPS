package com.sophia.ops.ai

data class DeviceSummary(
    val name: String,
    val vendor: String,
    val type: String,
    val riskScore: Int,
    val isNew: Boolean
)

data class AiAnalysisResult(
    val riskSummary: String,
    val recommendedAction: String,
    val confidence: String
)
