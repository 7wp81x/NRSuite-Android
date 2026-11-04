package com.swp81x.nrsuite.core.terminal

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class TerminalUiSettings(
    val showLauncherOnHome: Boolean = true,
    val autoconnect: Boolean = true,
    val savePassword: Boolean = false,
    val defaultFontSize: Int = 28,
    val cursorStyle: Int = 1,
    val cursorBlinkMs: Int = 280,
)

internal class TerminalPreferences(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("nrsuite_terminal_settings", Context.MODE_PRIVATE)
    private val secretStore = TerminalSecretStore()

    fun load(): TerminalUiSettings = TerminalUiSettings(
        showLauncherOnHome = preferences.getBoolean(KEY_SHOW_HOME, true),
        autoconnect = if (preferences.getBoolean(KEY_AUTOCONNECT_USER_SET, false)) {
            preferences.getBoolean(KEY_AUTOCONNECT, true)
        } else {
            true
        },
        savePassword = preferences.getBoolean(KEY_SAVE_PASSWORD, false),
        defaultFontSize = preferences.getInt(KEY_FONT_SIZE, 28).coerceIn(9, 34),
        cursorStyle = preferences.getInt(KEY_CURSOR_STYLE, 1).coerceIn(0, 2),
        cursorBlinkMs = preferences.getInt(KEY_BLINK_MS, 280).coerceIn(100, 2000),
    )

    fun save(settings: TerminalUiSettings) {
        preferences.edit()
            .putBoolean(KEY_SHOW_HOME, settings.showLauncherOnHome)
            .putBoolean(KEY_AUTOCONNECT, settings.autoconnect)
            .putBoolean(KEY_AUTOCONNECT_USER_SET, true)
            .putBoolean(KEY_SAVE_PASSWORD, settings.savePassword)
            .putInt(KEY_FONT_SIZE, settings.defaultFontSize.coerceIn(9, 34))
            .putInt(KEY_CURSOR_STYLE, settings.cursorStyle.coerceIn(0, 2))
            .putInt(KEY_BLINK_MS, settings.cursorBlinkMs.coerceIn(100, 2000))
            .apply()
    }

    fun savePassword(password: String) {
        if (password.isEmpty()) return
        runCatching { secretStore.encrypt(password) }
            .getOrNull()
            ?.let { preferences.edit().putString(KEY_SAVED_PASSWORD, it).apply() }
    }

    fun loadPassword(): String? {
        val saved = preferences.getString(KEY_SAVED_PASSWORD, null) ?: return null
        return runCatching { secretStore.decrypt(saved) }.getOrNull()
    }

    fun clearPassword() {
        preferences.edit().remove(KEY_SAVED_PASSWORD).apply()
    }

    fun hasPrivateKey(): Boolean = preferences.contains(KEY_PRIVATE_KEY)

    fun savePrivateKey(
        name: String,
        bytes: ByteArray,
        publicBytes: ByteArray?,
        passphrase: String,
    ) {
        val encodedKey = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val encryptedKey = secretStore.encrypt(encodedKey)
        val encryptedPublicKey = publicBytes?.let {
            secretStore.encrypt(Base64.encodeToString(it, Base64.NO_WRAP))
        }
        val encryptedPassphrase = secretStore.encrypt(passphrase)
        preferences.edit()
            .putString(KEY_PRIVATE_KEY_NAME, name)
            .putString(KEY_PRIVATE_KEY, encryptedKey)
            .putString(KEY_PRIVATE_KEY_PUBLIC, encryptedPublicKey)
            .putString(KEY_PRIVATE_KEY_PASSPHRASE, encryptedPassphrase)
            .apply()
    }

    fun loadPrivateKey(): StoredPrivateKey? {
        val encryptedKey = preferences.getString(KEY_PRIVATE_KEY, null) ?: return null
        val keyBase64 = runCatching { secretStore.decrypt(encryptedKey) }.getOrNull() ?: return null
        val bytes = runCatching {
            Base64.decode(keyBase64, Base64.NO_WRAP)
        }.getOrNull() ?: return null
        val publicBytes = preferences.getString(KEY_PRIVATE_KEY_PUBLIC, null)
            ?.let { runCatching { secretStore.decrypt(it) }.getOrNull() }
            ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
        val passphrase = preferences.getString(KEY_PRIVATE_KEY_PASSPHRASE, null)
            ?.let { runCatching { secretStore.decrypt(it) }.getOrNull() }
            .orEmpty()
        return StoredPrivateKey(
            name = preferences.getString(KEY_PRIVATE_KEY_NAME, "Terminal key").orEmpty(),
            bytes = bytes,
            publicBytes = publicBytes,
            passphrase = passphrase,
        )
    }

    fun clearPrivateKey() {
        preferences.edit()
            .remove(KEY_PRIVATE_KEY)
            .remove(KEY_PRIVATE_KEY_NAME)
            .remove(KEY_PRIVATE_KEY_PUBLIC)
            .remove(KEY_PRIVATE_KEY_PASSPHRASE)
            .apply()
    }

    private companion object {
        const val KEY_SHOW_HOME = "show_launcher_on_home"
        const val KEY_AUTOCONNECT = "autoconnect"
        const val KEY_AUTOCONNECT_USER_SET = "autoconnect_user_set"
        const val KEY_SAVE_PASSWORD = "save_password"
        const val KEY_FONT_SIZE = "default_font_size"
        const val KEY_CURSOR_STYLE = "cursor_style"
        const val KEY_BLINK_MS = "cursor_blink_ms"
        const val KEY_SAVED_PASSWORD = "saved_password"
        const val KEY_PRIVATE_KEY = "private_key"
        const val KEY_PRIVATE_KEY_NAME = "private_key_name"
        const val KEY_PRIVATE_KEY_PUBLIC = "private_key_public"
        const val KEY_PRIVATE_KEY_PASSPHRASE = "private_key_passphrase"
    }
}

data class StoredPrivateKey(
    val name: String,
    val bytes: ByteArray,
    val publicBytes: ByteArray?,
    val passphrase: String,
)

data class TerminalKeyInfo(
    val name: String,
    val fingerprint: String,
    val publicKeyText: String?,
)

private class TerminalSecretStore {
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(value: String): String {
        val combined = Base64.decode(value, Base64.NO_WRAP)
        require(combined.size > IV_SIZE) { "Invalid encrypted value" }
        val iv = combined.copyOfRange(0, IV_SIZE)
        val encrypted = combined.copyOfRange(IV_SIZE, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(TAG_BITS, iv),
        )
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "nrsuite_terminal_password"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
