package com.hiro.mistakebook.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var model: String
        get() = prefs.getString("model", "deepseek-flash") ?: "deepseek-flash"
        set(value) = prefs.edit().putString("model", value).apply()

    var yearReminderEnabled: Boolean
        get() = prefs.getBoolean("yearReminderEnabled", true)
        set(value) = prefs.edit().putBoolean("yearReminderEnabled", value).apply()

    var lastSubjectFilter: String
        get() = prefs.getString("lastSubjectFilter", "全部") ?: "全部"
        set(value) = prefs.edit().putString("lastSubjectFilter", value).apply()

    var lastSort: String
        get() = prefs.getString("lastSort", "拍摄时间") ?: "拍摄时间"
        set(value) = prefs.edit().putString("lastSort", value).apply()

    fun saveApiKey(value: String) {
        if (value.isBlank()) {
            prefs.edit().remove("apiKeyCiphertext").remove("apiKeyIv").apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("apiKeyCiphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("apiKeyIv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun apiKey(): String {
        val encryptedValue = prefs.getString("apiKeyCiphertext", null) ?: return ""
        val ivValue = prefs.getString("apiKeyIv", null) ?: return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                encryptionKey(),
                GCMParameterSpec(128, Base64.decode(ivValue, Base64.NO_WRAP))
            )
            String(
                cipher.doFinal(Base64.decode(encryptedValue, Base64.NO_WRAP)),
                Charsets.UTF_8
            )
        }.getOrDefault("")
    }

    fun hasApiKey(): Boolean = apiKey().isNotBlank()

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "mistake_book_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
