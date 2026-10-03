package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.mesh.MeshCrypto
import com.swp81x.nrsuite.core.mesh.MeshNodeStatus
import com.swp81x.nrsuite.core.mesh.StoredMeshCredentials
import com.swp81x.nrsuite.core.session.ConnectionState
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import org.json.JSONObject

// Mesh Foundation Phase 1/2: app-side key derivation, provisioning, auth,
// activation, and live status/event handling.

private const val MIN_MESH_PASSPHRASE_LENGTH = 8
private const val MESH_COMMAND_TIMEOUT_MS = 8_000L

internal fun MainViewModel.refreshMeshStatusImpl() {
    val activeSession = session ?: run {
        appendLog("Connect to a device before reading mesh status.")
        return
    }

    scope.launch {
        val response = activeSession.sendCommand("MESH_STATUS", timeoutMs = 5_000)
        if (response?.optBoolean("ok") == true) {
            updateMeshStatusFromJsonImpl(response)
        } else {
            appendLog("Mesh status request failed: ${response?.optString("msg") ?: "timeout"}")
        }
    }
}

internal fun MainViewModel.setMeshPassphraseImpl(value: String) {
    _meshPassphrase.value = value
    _meshPassphraseMatch.value = null
    _meshSetupMessage.value = null
}

internal fun MainViewModel.setMeshKeyIdImpl(value: String) {
    _meshKeyId.value = value
}

internal fun MainViewModel.forgetMeshPassphraseImpl() {
    removeStoredCredentialsForNodeImpl()
    removeGlobalCredentialsImpl()
    _meshPassphrase.value = ""
    _meshKeyId.value = ""
    _meshPassphraseMatch.value = null
    _meshSetupMessage.value = "Saved mesh credentials removed from this app."
    _meshCheckingPassphrase.value = false
    appendLog("Saved mesh credentials forgotten on this app.", tag = "mesh")
}

internal fun MainViewModel.verifyMeshPassphraseImpl(passphrase: String) {
    val cleanPassphrase = passphrase.trim()
    if (cleanPassphrase.length < MIN_MESH_PASSPHRASE_LENGTH) {
        val message = "Mesh passphrase must be at least $MIN_MESH_PASSPHRASE_LENGTH characters."
        _meshSetupMessage.value = message
        _meshLastError.value = message
        return
    }

    val activeSession = session ?: run {
        val message = "Connect to a device before checking mesh membership."
        _meshSetupMessage.value = message
        _meshLastError.value = message
        return
    }

    if (!_meshInitialized.value) {
        _meshPassphraseMatch.value = null
        _meshSetupMessage.value = "This device is not provisioned yet."
        return
    }

    val keys = runCatching { MeshCrypto.deriveKeys(cleanPassphrase) }.getOrElse { error ->
        val message = "Mesh key derivation failed: ${error.message}"
        _meshSetupMessage.value = message
        _meshLastError.value = message
        return
    }

    _meshCheckingPassphrase.value = true
    _meshLastError.value = null
    _meshSetupMessage.value = null
    _meshPassphraseMatch.value = null

    scope.launch {
        try {
            val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val challenge = activeSession.sendCommand(
                "MESH_AUTH_CHALLENGE",
                JSONObject().put("nonce", MeshCrypto.base64Encode(nonce)),
                timeoutMs = MESH_COMMAND_TIMEOUT_MS,
            )
            if (challenge?.optBoolean("ok") != true) {
                val message = challenge?.optString("msg") ?: "challenge timeout"
                _meshSetupMessage.value = "Could not check passphrase: $message"
                _meshLastError.value = message
                return@launch
            }

            val returnedHmac = MeshCrypto.base64Decode(challenge.optString("hmac"))
            val expectedHmac = MeshCrypto.hmacSha256(keys.authKey, nonce)
            val match = returnedHmac != null && MessageDigest.isEqual(expectedHmac, returnedHmac)
            _meshPassphraseMatch.value = match
            _meshSetupMessage.value = if (match) {
                "This device is already provisioned with this passphrase."
            } else {
                "This device is provisioned with a different passphrase."
            }
        } finally {
            _meshCheckingPassphrase.value = false
        }
    }
}

internal fun MainViewModel.provisionMeshImpl(passphrase: String, keyId: String?) {
    val cleanPassphrase = passphrase.trim()
    if (cleanPassphrase.length < MIN_MESH_PASSPHRASE_LENGTH) {
        val message = "Mesh passphrase must be at least $MIN_MESH_PASSPHRASE_LENGTH characters."
        _meshLastError.value = message
        appendLog(message, tag = "mesh")
        return
    }

    val activeSession = session ?: run {
        appendLog("Connect to a device before provisioning mesh keys.", tag = "mesh")
        return
    }

    val keys = runCatching { MeshCrypto.deriveKeys(cleanPassphrase) }.getOrElse { error ->
        appendLog("Mesh key derivation failed: ${error.message}", tag = "mesh")
        return
    }

    _meshProvisioning.value = true
    _meshLastError.value = null
    _meshSetupMessage.value = null
    _meshPassphrase.value = cleanPassphrase
    _meshKeyId.value = keyId.orEmpty().trim()
    _meshPassphraseMatch.value = null

    scope.launch {
        try {
            val args = JSONObject().apply {
                put("auth_key", MeshCrypto.base64Encode(keys.authKey))
                put("transport_key", MeshCrypto.base64Encode(keys.transportKey))
                if (!keyId.isNullOrBlank()) put("key_id", keyId.trim())
            }
            val response = activeSession.sendCommand(
                "MESH_PROVISION_KEY",
                args,
                timeoutMs = MESH_COMMAND_TIMEOUT_MS,
            )
            if (response?.optBoolean("ok") == true) {
                val stored = StoredMeshCredentials(
                    authKey = keys.authKey,
                    transportKey = keys.transportKey,
                    keyId = keyId?.trim()?.takeIf { it.isNotBlank() },
                )
                persistProvisionedCredentialsImpl(stored)
                persistGlobalCredentialsImpl(stored)
                _meshPassphraseMatch.value = true
                _meshSetupMessage.value = "Mesh setup complete. Saved credentials will be used for future activation."
                appendLog("Mesh derived keys provisioned; raw passphrase was never sent.", tag = "mesh")
                addHistory("mesh", "Mesh keys provisioned", HistoryLevel.SUCCESS)
                refreshMeshFeaturesImpl()
                refreshMeshStatusImpl()
            } else {
                val message = response?.optString("msg") ?: "timeout"
                _meshLastError.value = message
                _meshSetupMessage.value = "Mesh provisioning failed: $message"
                appendLog("Mesh provisioning failed: $message", tag = "mesh")
            }
        } finally {
            _meshProvisioning.value = false
        }
    }
}

internal fun MainViewModel.provisionMeshWithSavedCredentialsImpl() {
    val credentials = meshGlobalCredentials ?: run {
        refreshMeshGlobalCredentialsImpl()
        meshGlobalCredentials
    } ?: run {
        val message = "No saved mesh credentials on this app. Enter the passphrase once to set up a mesh."
        _meshSetupMessage.value = message
        _meshLastError.value = message
        appendLog(message, tag = "mesh")
        return
    }

    val activeSession = session ?: run {
        val message = "Connect to a device before provisioning mesh keys."
        _meshSetupMessage.value = message
        _meshLastError.value = message
        appendLog(message, tag = "mesh")
        return
    }

    _meshProvisioning.value = true
    _meshLastError.value = null
    _meshSetupMessage.value = null

    scope.launch {
        try {
            val args = JSONObject().apply {
                put("auth_key", MeshCrypto.base64Encode(credentials.authKey))
                put("transport_key", MeshCrypto.base64Encode(credentials.transportKey))
                credentials.keyId?.takeIf { it.isNotBlank() }?.let { put("key_id", it) }
            }
            val response = activeSession.sendCommand(
                "MESH_PROVISION_KEY",
                args,
                timeoutMs = MESH_COMMAND_TIMEOUT_MS,
            )
            if (response?.optBoolean("ok") == true) {
                persistProvisionedCredentialsImpl(credentials)
                _meshPassphraseMatch.value = true
                _meshSetupMessage.value = "Device set up using the saved mesh credentials."
                appendLog("Mesh keys provisioned from saved app credentials.", tag = "mesh")
                addHistory("mesh", "Mesh keys provisioned from saved credentials", HistoryLevel.SUCCESS)
                refreshMeshFeaturesImpl()
                refreshMeshStatusImpl()
            } else {
                val message = response?.optString("msg") ?: "timeout"
                _meshLastError.value = message
                _meshSetupMessage.value = "Mesh provisioning failed: $message"
                appendLog("Mesh provisioning failed: $message", tag = "mesh")
            }
        } finally {
            _meshProvisioning.value = false
        }
    }
}

internal fun MainViewModel.authenticateAndActivateMeshImpl(passphrase: String) {
    val cleanPassphrase = passphrase.trim()
    if (cleanPassphrase.length < MIN_MESH_PASSPHRASE_LENGTH) {
        val message = "Enter the mesh passphrase before authenticating."
        _meshLastError.value = message
        appendLog(message, tag = "mesh")
        return
    }

    val keys = runCatching { MeshCrypto.deriveKeys(cleanPassphrase) }.getOrElse { error ->
        appendLog("Mesh key derivation failed: ${error.message}", tag = "mesh")
        return
    }

    _meshPassphrase.value = cleanPassphrase
    authenticateAndActivateWithAuthKey(keys.authKey, "passphrase")
}

internal fun MainViewModel.authenticateAndActivateStoredMeshImpl() {
    val credentials = meshStoredCredentials ?: run {
        val message = "No saved credentials for this node. Enter the mesh passphrase again."
        _meshLastError.value = message
        _meshSetupMessage.value = message
        appendLog(message, tag = "mesh")
        refreshStoredCredentialsForNodeImpl()
        return
    }

    authenticateAndActivateWithAuthKey(
        authKey = credentials.authKey,
        sourceLabel = credentials.keyId ?: "saved credentials",
    )
}

private fun MainViewModel.authenticateAndActivateWithAuthKey(
    authKey: ByteArray,
    sourceLabel: String,
) {
    val activeSession = session ?: run {
        val message = "Connect to a device before authenticating mesh membership."
        _meshLastError.value = message
        _meshSetupMessage.value = message
        appendLog(message, tag = "mesh")
        return
    }

    val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
    _meshLastError.value = null
    _meshSetupMessage.value = null
    _meshActionInProgress.value = true

    scope.launch {
        try {
        val challenge = activeSession.sendCommand(
            "MESH_AUTH_CHALLENGE",
            JSONObject().put("nonce", MeshCrypto.base64Encode(nonce)),
            timeoutMs = MESH_COMMAND_TIMEOUT_MS,
        )
        if (challenge?.optBoolean("ok") != true) {
            val message = challenge?.optString("msg") ?: "challenge timeout"
            _meshLastError.value = message
            _meshSetupMessage.value = "Mesh authentication challenge failed: $message"
            appendLog("Mesh authentication challenge failed: $message", tag = "mesh")
            return@launch
        }

        val nodeId = challenge.optString("node_id")
        if (nodeId.isNotBlank()) {
            _meshNodeId.value = nodeId
        }

        val returnedHmac = MeshCrypto.base64Decode(challenge.optString("hmac"))
        val expectedHmac = MeshCrypto.hmacSha256(authKey, nonce)

        if (returnedHmac == null || !MessageDigest.isEqual(expectedHmac, returnedHmac)) {
            _meshPassphraseMatch.value = false
            val message = "Mesh authentication failed: node HMAC did not match the stored credentials."
            _meshLastError.value = message
            _meshSetupMessage.value = "Saved credentials do not match this node. Re-enter the mesh passphrase."
            appendLog(message, tag = "mesh")
            if (sourceLabel != "passphrase") {
                removeStoredCredentialsForNodeImpl()
            }
            return@launch
        }

        _meshPassphraseMatch.value = true
        _meshSetupMessage.value = "Identity verified using $sourceLabel."
        appendLog("Mesh identity verified for ${nodeId.ifBlank { "device" }}.", tag = "mesh")

        val activation = activeSession.sendCommand(
            "MESH_ACTIVATE",
            timeoutMs = MESH_COMMAND_TIMEOUT_MS,
        )
        if (activation?.optBoolean("ok") == true) {
            _meshActive.value = true
            _meshRole.value = activation.optString("role", "candidate")
            appendLog("Mesh activation requested; waiting for election.", tag = "mesh")
            addHistory("mesh", "Mesh activation requested", HistoryLevel.SUCCESS)
        } else {
            val message = activation?.optString("msg") ?: "timeout"
            _meshLastError.value = message
            _meshSetupMessage.value = "Mesh activation failed: $message"
            appendLog("Mesh activation failed: $message", tag = "mesh")
        }
        } finally {
            _meshActionInProgress.value = false
        }
    }
}

internal fun MainViewModel.deactivateMeshImpl() {
    val activeSession = session
    _meshActionInProgress.value = true
    scope.launch {
        try {
            val response = activeSession?.sendCommand("MESH_DEACTIVATE", timeoutMs = 5_000)
            if (response?.optBoolean("ok") == true) {
                appendLog("Mesh deactivated; stored keys remain provisioned.", tag = "mesh")
                resetMeshRuntimeStateImpl()
            } else {
                appendLog("Mesh deactivate request failed or timed out.", tag = "mesh")
                refreshMeshStatusImpl()
            }
        } finally {
            _meshActionInProgress.value = false
        }
    }
}

internal fun MainViewModel.clearMeshKeysImpl() {
    val activeSession = session
    scope.launch {
        val response = activeSession?.sendCommand("MESH_CLEAR_KEY", timeoutMs = 5_000)
        if (response?.optBoolean("ok") == true) {
            _meshInitialized.value = false
            _meshPassphraseMatch.value = null
            removeStoredCredentialsForNodeImpl()
            _meshSetupMessage.value = "Mesh keys cleared. This device is no longer provisioned."
            appendLog("Mesh keys cleared from the device.", tag = "mesh")
            addHistory("mesh", "Mesh keys cleared", HistoryLevel.SUCCESS)
            refreshMeshFeaturesImpl()
        } else {
            appendLog("Mesh key clear request failed or timed out.", tag = "mesh")
        }
        resetMeshRuntimeStateImpl()
    }
}

internal fun MainViewModel.handleMeshEventImpl(event: JSONObject) {
    when (event.optString("type")) {
        "mesh_status" -> {
            updateMeshStatusFromJsonImpl(event)
            event.optString("reason").takeIf { it.isNotBlank() }?.let { reason ->
                appendLog("Mesh status: ${event.optString("role", "disabled")} ($reason).", tag = "mesh")
            }
        }

        "mesh_activation_result" -> {
            val role = event.optString("role", "disabled")
            val ok = event.optBoolean("ok", false)
            val sessionId = event.optLong("session_id", 0L).takeIf { it > 0L }
            _meshRole.value = role
            _meshActive.value = role == "master" || role == "client" || role == "candidate"
            _meshSessionId.value = sessionId
            if (ok) {
                appendLog("Mesh activation complete: role=$role" +
                    (sessionId?.let { ", session=$it" } ?: ""), tag = "mesh")
            } else {
                val message = event.optString("reason", "election conflict")
                _meshLastError.value = message
                appendLog("Mesh activation failed: $message", tag = "mesh")
            }
        }

        "mesh_heartbeat" -> {
            val nodeId = event.optString("node_id")
            if (nodeId.isBlank()) return
            val role = event.optString("role", "unknown")
            val sessionId = event.optLong("session_id", 0L).takeIf { it > 0L }
            val rssi = if (event.has("rssi")) event.optInt("rssi") else null
            val chip = event.optString("chip").takeIf { it.isNotBlank() }
            val now = System.currentTimeMillis()
            _meshNodes.update { current ->
                (current.filterNot { it.nodeId == nodeId } + MeshNodeStatus(
                    nodeId = nodeId,
                    role = role,
                    sessionId = sessionId,
                    rssi = rssi,
                    online = true,
                    lastSeenAtMs = now,
                    chip = chip,
                )).sortedByDescending { it.lastSeenAtMs }
            }
        }

        "mesh_node_joined" -> {
            val nodeId = event.optString("node_id")
            if (nodeId.isBlank()) return
            val sessionId = event.optLong("session_id", 0L).takeIf { it > 0L }
            val rssi = if (event.has("rssi")) event.optInt("rssi") else null
            val chip = event.optString("chip").takeIf { it.isNotBlank() }
            _meshNodes.update { current ->
                (current.filterNot { it.nodeId == nodeId } + MeshNodeStatus(
                    nodeId = nodeId,
                    role = "client",
                    sessionId = sessionId,
                    rssi = rssi,
                    online = true,
                    lastSeenAtMs = System.currentTimeMillis(),
                    chip = chip,
                )).sortedByDescending { it.lastSeenAtMs }
            }
            appendLog("Mesh node joined: $nodeId", tag = "mesh")
        }

        "mesh_node_left" -> {
            val nodeId = event.optString("node_id")
            if (nodeId.isBlank()) return
            val reason = event.optString("reason", "timeout")
            _meshNodes.update { current ->
                current.map { node ->
                    if (node.nodeId == nodeId) node.copy(online = false) else node
                }
            }
            appendLog("Mesh node left: $nodeId ($reason)", tag = "mesh")
            refreshMeshStatusImpl()
        }

        "mesh_error" -> {
            val message = "${event.optString("code", "mesh_error")}: ${event.optString("msg")}"
            _meshLastError.value = message
            appendLog("Mesh error: $message", tag = "mesh")
        }
    }
}

internal fun MainViewModel.resetMeshRuntimeStateImpl() {
    _meshActive.value = false
    _meshRole.value = "disabled"
    _meshSessionId.value = null
    _meshPeerCount.value = 0
    _meshNodes.value = emptyList()
    _meshProvisioning.value = false
}

internal fun MainViewModel.refreshMeshGlobalCredentialsImpl() {
    val credentials = meshCredentialStore.loadGlobal()
    meshGlobalCredentials = credentials
    _meshHasGlobalCredentials.value = credentials != null
    _meshGlobalKeyId.value = credentials?.keyId
}

private fun MainViewModel.persistGlobalCredentialsImpl(credentials: StoredMeshCredentials) {
    meshCredentialStore.saveGlobal(credentials)
    meshGlobalCredentials = credentials
    _meshHasGlobalCredentials.value = true
    _meshGlobalKeyId.value = credentials.keyId
}

private fun MainViewModel.removeGlobalCredentialsImpl() {
    meshCredentialStore.removeGlobal()
    meshGlobalCredentials = null
    _meshHasGlobalCredentials.value = false
    _meshGlobalKeyId.value = null
}

private fun MainViewModel.persistProvisionedCredentialsImpl(credentials: StoredMeshCredentials) {
    val nodeId = _meshNodeId.value.trim()
    if (nodeId.isNotBlank()) {
        meshCredentialStore.save(nodeId, credentials)
        pendingMeshCredentials = null
    } else {
        pendingMeshCredentials = credentials
    }
    meshStoredCredentials = credentials
    _meshHasStoredCredentials.value = true
    _meshStoredKeyId.value = credentials.keyId
}

private fun MainViewModel.refreshStoredCredentialsForNodeImpl() {
    val nodeId = _meshNodeId.value.trim()
    if (nodeId.isBlank()) {
        meshStoredCredentials = null
        _meshHasStoredCredentials.value = false
        _meshStoredKeyId.value = null
        return
    }

    val credentials = meshCredentialStore.load(nodeId)
    meshStoredCredentials = credentials
    _meshHasStoredCredentials.value = credentials != null
    _meshStoredKeyId.value = credentials?.keyId

    // Seed the app-wide saved mesh credentials when this is the first stored
    // node we encounter. This lets new nodes join the same mesh without
    // re-typing the passphrase, even for users upgrading from an earlier build.
    if (credentials != null && meshGlobalCredentials == null) {
        persistGlobalCredentialsImpl(credentials)
    }
}

private fun MainViewModel.removeStoredCredentialsForNodeImpl() {
    val nodeId = _meshNodeId.value.trim()
    if (nodeId.isNotBlank()) {
        meshCredentialStore.remove(nodeId)
    }
    pendingMeshCredentials = null
    meshStoredCredentials = null
    _meshHasStoredCredentials.value = false
    _meshStoredKeyId.value = null
}

private fun MainViewModel.refreshMeshFeaturesImpl() {
    val activeSession = session ?: return
    scope.launch {
        val status = activeSession.sendCommand("STATUS", timeoutMs = 5_000) ?: return@launch
        val features = mutableSetOf<String>()
        status.optJSONArray("features")?.let { array ->
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotBlank()) features += value
            }
        }

        val connected = _connectionState.value as? ConnectionState.Connected ?: return@launch
        val updated = connected.copy(features = features)
        _connectionState.value = updated
        activeDeviceFingerprint?.let { fingerprint ->
            _deviceConnectionStates.update { it + (fingerprint to updated) }
        }
    }
}

private fun MainViewModel.updateMeshStatusFromJsonImpl(json: JSONObject) {
    val initialized = json.optBoolean("initialized", _meshInitialized.value)
    _meshInitialized.value = initialized
    if (!initialized) {
        _meshPassphraseMatch.value = null
    }
    val role = json.optString("role", "disabled").ifBlank { "disabled" }
    _meshRole.value = role
    _meshActive.value = json.optBoolean(
        "active",
        role == "master" || role == "client" || role == "candidate" || role == "idle",
    )
    _meshSessionId.value = json.optLong("session_id", 0L).takeIf { it > 0L }
    _meshNodeId.value = json.optString("node_id", _meshNodeId.value).ifBlank { _meshNodeId.value }
    _meshPeerCount.value = json.optInt("peer_count", _meshPeerCount.value)

    json.optJSONArray("peers")?.let { peersArray ->
        _meshNodes.update { current ->
            val merged = current.associateBy { it.nodeId }.toMutableMap()
            for (index in 0 until peersArray.length()) {
                val peer = peersArray.optJSONObject(index) ?: continue
                val peerNodeId = peer.optString("node_id")
                if (peerNodeId.isBlank()) continue
                merged[peerNodeId] = MeshNodeStatus(
                    nodeId = peerNodeId,
                    role = peer.optString("role", "unknown"),
                    sessionId = peer.optLong("session_id", 0L).takeIf { it > 0L },
                    rssi = peer.optInt("rssi", 0).takeIf { it != 0 },
                    online = peer.optBoolean("online", true),
                    lastSeenAtMs = System.currentTimeMillis() -
                        peer.optLong("last_seen_ms", 0L).coerceAtLeast(0L),
                    chip = peer.optString("chip").takeIf { it.isNotBlank() },
                )
            }
            merged.values.sortedByDescending { it.lastSeenAtMs }
        }
    }

    if (_meshNodeId.value.isNotBlank()) {
        pendingMeshCredentials?.let { credentials ->
            meshCredentialStore.save(_meshNodeId.value, credentials)
            pendingMeshCredentials = null
            meshStoredCredentials = credentials
            _meshHasStoredCredentials.value = true
            _meshStoredKeyId.value = credentials.keyId
        }
        if (initialized) {
            refreshStoredCredentialsForNodeImpl()
        } else {
            removeStoredCredentialsForNodeImpl()
        }
    }
}
