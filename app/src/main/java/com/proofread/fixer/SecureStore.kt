package com.proofread.fixer

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores secrets (API keys) encrypted with a key that lives in the Android Keystore. */
object SecureStore {
    private const val ALIAS = "proofread_key_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(ALIAS, null)
        if (existing is SecretKey) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String = try {
        val parts = stored.split(":")
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ct = Base64.decode(parts[1], Base64.NO_WRAP)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(c.doFinal(ct), Charsets.UTF_8)
    } catch (e: Exception) {
        ""
    }

    /** Reads a secret. A plain-text value saved by an older version is encrypted on first read. */
    fun get(prefs: SharedPreferences, name: String): String {
        val enc = prefs.getString(name + "_enc", null)
        if (enc != null) return decrypt(enc)
        val plain = prefs.getString(name, "").orEmpty()
        if (plain.isNotEmpty()) put(prefs, name, plain)
        return plain
    }

    fun put(prefs: SharedPreferences, name: String, value: String) {
        if (value.isEmpty()) {
            prefs.edit().remove(name + "_enc").remove(name).apply()
            return
        }
        try {
            prefs.edit().putString(name + "_enc", encrypt(value)).remove(name).apply()
        } catch (e: Exception) {
            // Keystore unavailable on this device: fall back so the app still works.
            prefs.edit().putString(name, value).remove(name + "_enc").apply()
        }
    }
}
