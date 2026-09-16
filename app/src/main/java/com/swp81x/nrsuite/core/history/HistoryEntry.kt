package com.swp81x.nrsuite.core.history

enum class HistoryLevel { INFO, SUCCESS, ERROR }

data class HistoryEntry(
    val id: Long,
    val timestamp: String,
    val module: String,
    val summary: String,
    val level: HistoryLevel = HistoryLevel.INFO,
)
