package com.tgfinance.server.auth

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Argon2id password hashing (pure Java via Bouncy Castle, no native library needed).
 * Defaults follow the OWASP recommendation: m = 19 MiB, t = 2, p = 1.
 * Hashes are stored in the PHC string format: $argon2id$v=19$m=…,t=…,p=…$salt$hash
 */
class PasswordHasher(
    private val memoryKiB: Int = 19 * 1024,
    private val iterations: Int = 2,
    private val parallelism: Int = 1,
    private val random: SecureRandom = SecureRandom(),
) {
    private val encoder = Base64.getEncoder().withoutPadding()
    private val decoder = Base64.getDecoder()

    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = derive(password, salt, memoryKiB, iterations, parallelism)
        return "\$argon2id\$v=19\$m=$memoryKiB,t=$iterations,p=$parallelism\$${encoder.encodeToString(salt)}\$${encoder.encodeToString(hash)}"
    }

    fun verify(password: String, encoded: String): Boolean {
        val parts = encoded.split('$')
        if (parts.size != 6 || parts[1] != "argon2id") return false
        val params = parts[3].split(',').associate { it.substringBefore('=') to it.substringAfter('=').toIntOrNull() }
        val m = params["m"] ?: return false
        val t = params["t"] ?: return false
        val p = params["p"] ?: return false
        val salt = runCatching { decoder.decode(parts[4]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[5]) }.getOrNull() ?: return false
        val actual = derive(password, salt, m, t, p)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: String, salt: ByteArray, m: Int, t: Int, p: Int): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(m)
            .withIterations(t)
            .withParallelism(p)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator().apply { init(params) }
        val out = ByteArray(HASH_BYTES)
        generator.generateBytes(password.toByteArray(Charsets.UTF_8), out)
        return out
    }

    private companion object {
        const val SALT_BYTES = 16
        const val HASH_BYTES = 32
    }
}
