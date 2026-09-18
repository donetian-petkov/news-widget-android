package com.ainews.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureProviderKeyStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("provider-key-store", Context.MODE_PRIVATE)

    fun save(key: String) {
        if (key.isBlank()) {
            clear()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("ciphertext", encrypted.toBase64())
            .putString("iv", cipher.iv.toBase64())
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun hasKey(): Boolean = prefs.contains("ciphertext") && prefs.contains("iv")

    fun load(): String? {
        val ciphertext = prefs.getString("ciphertext", null)?.fromBase64() ?: return null
        val iv = prefs.getString("iv", null)?.fromBase64() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun ByteArray.toBase64(): String =
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray =
        android.util.Base64.decode(this, android.util.Base64.NO_WRAP)

    companion object {
        private const val KEY_ALIAS = "ai-news-provider-key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
