package com.pkmk.bravy.data.model

data class PracticeResult(
    val confidenceScore: Int,
    val speechScore: Int,
    val totalScore: Int,
    val recommendation: String,
    val levelTitle: String
)