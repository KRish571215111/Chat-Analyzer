package com.example.utils

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object EncryptionHelper {
    private const val ALGORITHM = "AES"
    
    // Static 16-byte key for local sqlite database encryption
    private val keyBytes = byteArrayOf(
        0x47, 0x72, 0x6f, 0x75, 0x70, 0x41, 0x6e, 0x61,
        0x6c, 0x79, 0x7a, 0x65, 0x72, 0x41, 0x45, 0x53
    )
    private val secretKey = SecretKeySpec(keyBytes, ALGORITHM)

    fun encrypt(value: String, enabled: Boolean): String {
        if (!enabled || value.isBlank()) return value
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encryptedVal = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            "[ENC]" + Base64.encodeToString(encryptedVal, Base64.NO_WRAP)
        } catch (e: Exception) {
            value
        }
    }

    fun decrypt(value: String, enabled: Boolean): String {
        if (!enabled || !value.startsWith("[ENC]")) return value
        return try {
            val base64Data = value.substring(5)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val decodedVal = Base64.decode(base64Data, Base64.NO_WRAP)
            String(cipher.doFinal(decodedVal), Charsets.UTF_8)
        } catch (e: Exception) {
            value
        }
    }
}
