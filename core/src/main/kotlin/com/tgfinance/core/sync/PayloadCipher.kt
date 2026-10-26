package com.tgfinance.core.sync

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Encrypts record payloads before they leave the device (end-to-end encryption). */
interface PayloadCipher {
    fun encrypt(plain: String): String
    fun decrypt(encoded: String): String

    companion object {
        const val PREFIX = "e2e:v1:"
        fun isEncrypted(payload: String): Boolean = payload.startsWith(PREFIX)
    }
}

/** AES-256-GCM with a random 96 bit IV per payload; the IV is stored in front of the ciphertext. */
class AesGcmPayloadCipher(
    private val key: SecretKey,
    private val random: SecureRandom = SecureRandom(),
) : PayloadCipher {

    override fun encrypt(plain: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PayloadCipher.PREFIX + Base64.getEncoder().encodeToString(iv + encrypted)
    }

    override fun decrypt(encoded: String): String {
        require(PayloadCipher.isEncrypted(encoded)) { "Payload is not encrypted" }
        try {
            val bytes = Base64.getDecoder().decode(encoded.removePrefix(PayloadCipher.PREFIX))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            val plain = cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
            return String(plain, Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw SyncException(SyncException.Kind.ENCRYPTION, "Wrong encryption password or corrupt data", e)
        } catch (e: IllegalArgumentException) {
            throw SyncException(SyncException.Kind.ENCRYPTION, "Corrupt encrypted payload", e)
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/**
 * Derives the end-to-end key from a password. PBKDF2-HMAC-SHA256 is used because it is
 * available on every Android version from API 26 without native code. The salt comes from
 * the server (per user, not secret) so every device of the user derives the same key.
 */
object KeyDerivation {
    const val DEFAULT_ITERATIONS = 310_000

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun randomSalt(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(16).also(random::nextBytes)
}
