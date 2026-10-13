package com.swp81x.nrsuite.core.sensors

import kotlin.math.abs
import kotlin.math.sqrt

enum class Ld2450Motion(val label: String) {
    APPROACHING("approaching"),
    STATIONARY("stationary"),
    RECEDING("receding"),
}

data class Ld2450Target(
    val slot: Int,
    val xMm: Int,
    val yMm: Int,
    val speedCmS: Int,
    val resolutionMm: Int,
) {
    val xCm: Float = xMm / 10f
    val yCm: Float = yMm / 10f
    val distanceCm: Float = sqrt(xCm * xCm + yCm * yCm)

    /**
     * With the LD2450 reference decoder, negative radial speed is
     * approaching and positive is receding.
     */
    val motion: Ld2450Motion = when {
        speedCmS < -10 -> Ld2450Motion.APPROACHING
        speedCmS > 10 -> Ld2450Motion.RECEDING
        else -> Ld2450Motion.STATIONARY
    }
}

data class Ld2450UiState(
    val active: Boolean = false,
    val starting: Boolean = false,
    val stopping: Boolean = false,
    val hasData: Boolean = false,
    val targets: List<Ld2450Target> = emptyList(),
    val targetCount: Int = 0,
    val validFrames: Int = 0,
    val invalidFrames: Int = 0,
    val droppedBytes: Int = 0,
    val lastFrameAgeMs: Long? = null,
    val errorKind: String? = null,
    val errorMessage: String? = null,
    val maxRangeCm: Int = 600,
    val zoneAlertEnabled: Boolean = false,
    val meshBroadcastEnabled: Boolean = false,
    val heartbeatEnabled: Boolean = false,
    val scanDurationMs: Int = 1000,
    val sleepDurationMs: Int = 2000,
    val limitDotCount: Boolean = false,
    val maxDots: Int = 8,
    val lastUpdatedAt: Long = 0L,
) {
    val running: Boolean get() = active

    val alertZoneBreach: Boolean
        get() = zoneAlertEnabled && targets.any { target ->
            target.yCm in 30f..300f && abs(target.xCm) <= 150f
        }
}
