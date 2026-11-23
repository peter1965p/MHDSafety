package com.paeffgen.mhdsafety.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.paeffgen.mhdsafety.BuildConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Anmeldedaten und Sync-Stand auf dem Handy.
 *
 * Der Refresh-Token liegt nur verschlüsselt in den Einstellungen; der Schlüssel dafür steckt im
 * Android-Keystore und verlässt das Gerät nie. Der Zugriffstoken lebt nur im Speicher.
 */
class AuthStore(context: Context) {
    private val prefs = context.getSharedPreferences("cloud_auth", Context.MODE_PRIVATE)

    @Volatile var accessToken: String? = null
    @Volatile var accessExpiresAt: Long = 0

    var apiUrl: String
        get() = prefs.getString(KEY_URL, null) ?: BuildConfig.API_URL
        set(value) = prefs.edit().putString(KEY_URL, value.trim().trimEnd('/')).apply()

    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)?.let { runCatching { decrypt(it) }.getOrNull() }
        set(value) = prefs.edit().putString(KEY_REFRESH, value?.let(::encrypt)).apply()

    /** Konto, dem die Daten auf diesem Handy gehören (null = noch nie synchronisiert). */
    var dataOwner: Long?
        get() = prefs.getLong(KEY_OWNER, -1).takeIf { it >= 0 }
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_OWNER) else putLong(KEY_OWNER, value) }.apply()

    fun cursor(kind: String): Long = prefs.getLong("cursor_$kind", 0)
    fun setCursor(kind: String, value: Long) = prefs.edit().putLong("cursor_$kind", value).apply()

    fun clearSession() {
        accessToken = null
        accessExpiresAt = 0
        prefs.edit().remove(KEY_REFRESH).remove(KEY_EMAIL).apply()
    }

    fun clearSyncState() {
        prefs.edit().remove(KEY_OWNER).remove("cursor_${Tombstone.ITEMS}").remove("cursor_${Tombstone.RECIPES}").apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val data = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
        return String(cipher.doFinal(data, 12, data.size - 12))
    }

    private companion object {
        const val ALIAS = "mhd_auth"
        const val KEY_URL = "api_url"
        const val KEY_EMAIL = "email"
        const val KEY_REFRESH = "refresh"
        const val KEY_OWNER = "data_owner"
    }
}
