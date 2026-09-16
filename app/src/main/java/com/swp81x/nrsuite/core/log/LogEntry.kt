package com.swp81x.nrsuite.core.log

enum class LogLevel { INFO, USB, SUCCESS, ERROR }

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
)
