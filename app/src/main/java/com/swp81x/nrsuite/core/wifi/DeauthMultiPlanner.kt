package com.swp81x.nrsuite.core.wifi

import kotlin.math.max

internal const val DEAUTH_MULTI_MAX_TARGETS = 32

/**
 * Use the firmware-native multi-target deauth command only when the connected
 * firmware advertises it and the selection fits the protocol target cap.
 * Older firmware keeps using the app-side repeated DEAUTH_BURST fallback.
 */
internal fun shouldUseNativeDeauthMulti(
    features: Set<String>,
    targetCount: Int,
): Boolean = targetCount in 2..DEAUTH_MULTI_MAX_TARGETS && "deauth_multi" in features

/**
 * Give the firmware enough time to finish a synchronous multi-target burst.
 * Loop starts return immediately; their progress arrives through deauth_stats.
 */
internal fun deauthMultiCommandTimeoutMs(
    targetCount: Int,
    countPerTarget: Int,
    intervalMs: Int,
    loopMode: Boolean,
): Long {
    if (loopMode) return 30_000L
    val frames = targetCount.toLong().coerceAtLeast(1L) *
        countPerTarget.toLong().coerceAtLeast(1L)
    val estimatedWriteMs = frames * intervalMs.toLong().coerceAtLeast(0L)
    return max(30_000L, estimatedWriteMs + 30_000L).coerceAtMost(600_000L)
}
