package com.swp81x.nrsuite.core.mesh

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

data class StoredMeshCredentials(
    val authKey: ByteArray,
    val transportKey: ByteArray,
    val keyId: String?,
)

/**
 * Persists app-side mesh credentials for each provisioned ESP32.
 *
 * The ESP32 stores its derived keys in NVS. The app needs its own copy of the
 * derived auth/transport keys to authenticate and activate an already-
 * provisioned node after the app restarts. The payload is encrypted with an
 * Android Keystore AES-GCM key before it is written to app-private prefs.
 *
 * The raw passphrase is never persisted.
 */
class MeshCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(nodeId: String): StoredMeshCredentials? {
        val nodeKey = nodeKey(nodeId)
        val encrypted = prefs.getString(nodeKey, null) ?: return null
        return runCatching {
            val plain = decrypt(encrypted)
            val json = JSONObject(String(plain, Charsets.UTF_8))
            val authKey = MeshCrypto.base64Decode(json.optString("auth_key"))
            val transportKey = MeshCrypto.base64Decode(json.optString("transport_key"))
            if (authKey == null || transportKey == null ||
                authKey.size != MeshCrypto.KEY_LENGTH ||
                transportKey.size != MeshCrypto.KEY_LENGTH
            ) {
                null
            } else {
                StoredMeshCredentials(
                    authKey = authKey,
                    transportKey = transportKey,
                    keyId = json.optString("key_id").takeIf { it.isNotBlank() },
                )
            }
        }.getOrNull()
    }

    fun save(nodeId: String, credentials: StoredMeshCredentials): Boolean {
        return runCatching {
            val json = JSONObject().apply {
                put("auth_key", MeshCrypto.base64Encode(credentials.authKey))
                put("transport_key", MeshCrypto.base64Encode(credentials.transportKey))
                if (!credentials.keyId.isNullOrBlank()) {
                    put("key_id", credentials.keyId)
                }
            }
            val encrypted = encrypt(json.toString().toByteArray(Charsets.UTF_8))
            prefs.edit().putString(nodeKey(nodeId), encrypted).apply()
            true
        }.getOrDefault(false)
    }

    fun remove(nodeId: String) {
        prefs.edit().remove(nodeKey(nodeId)).apply()
    }

    fun loadGlobal(): StoredMeshCredentials? = load(GLOBAL_NODE_ID)

    fun saveGlobal(credentials: StoredMeshCredentials): Boolean = save(GLOBAL_NODE_ID, credentials)

    fun removeGlobal() {
        remove(GLOBAL_NODE_ID)
    }

    private fun nodeKey(nodeId: String): String = "node_${nodeId.trim().uppercase()}"

    private fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        return base64(iv) + ":" + base64(ciphertext)
    }

    private fun decrypt(value: String): ByteArray {
        val parts = value.split(":", limit = 2)
        require(parts.size == 2) { "invalid stored mesh credential format" }
        val iv = Base64.getDecoder().decode(parts[0])
        val ciphertext = Base64.getDecoder().decode(parts[1])
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    companion object {
        private const val GLOBAL_NODE_ID = "__GLOBAL_MESH_CREDENTIALS__"
        private const val PREFS_NAME = "nrsuite_mesh_credentials"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "nrsuite_mesh_credential_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
