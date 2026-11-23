package com.example.auth

import android.os.Build
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {

    private const val ITERATIONS = 65536
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun hashPassword(password: String): Pair<String, String> {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        val saltHex = salt.toHex()
        val algorithm = defaultAlgorithm()
        val hashBytes = pbkdf2(password.toCharArray(), salt, algorithm)
        return saltHex to "$algorithm$${hashBytes.toHex()}"
    }

    fun verifyPassword(password: String, saltHex: String, storedHash: String): Boolean {
        val parts = storedHash.split("$", limit = 2)
        if (parts.size != 2) return false
        val algorithm = parts[0]
        val expectedBytes = parts[1].hexToBytes() ?: return false
        val saltBytes = saltHex.hexToBytes() ?: return false
        val actualBytes = pbkdf2(password.toCharArray(), saltBytes, algorithm)
        return MessageDigest.isEqual(expectedBytes, actualBytes)
    }

    private fun defaultAlgorithm(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "PBKDF2WithHmacSHA256"
        else "PBKDF2WithHmacSHA1"

    private fun pbkdf2(chars: CharArray, salt: ByteArray, algorithm: String): ByteArray {
        val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0) return null
        return try {
            ByteArray(length / 2) { i ->
                substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
