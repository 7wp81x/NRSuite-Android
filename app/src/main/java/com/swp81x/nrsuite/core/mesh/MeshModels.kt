package com.swp81x.nrsuite.core.mesh

data class MeshStatus(
    val initialized: Boolean = false,
    val role: String = "disabled",
    val sessionId: Long? = null,
    val nodeId: String? = null,
    val peerCount: Int = 0,
    val active: Boolean = false,
)

data class MeshNodeStatus(
    val nodeId: String,
    val role: String,
    val sessionId: Long?,
    val rssi: Int?,
    val online: Boolean,
    val lastSeenAtMs: Long,
    val chip: String? = null,
)
