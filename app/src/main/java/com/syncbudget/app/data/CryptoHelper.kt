package com.syncbudget.app.data

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {

    private const val SALT_LENGTH = 16
    private const val NONCE_LENGTH = 12
    private const val KEY_LENGTH = 256
    private const val ITERATIONS = 100_000

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()

        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)

        val nonce = ByteArray(NONCE_LENGTH)
        random.nextBytes(nonce)

        val derivedKey = deriveKey(password, salt)
        val keySpec = SecretKeySpec(derivedKey, "ChaCha20")
        val ivSpec = IvParameterSpec(nonce)

        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val ciphertext = cipher.doFinal(plaintext)

        return salt + nonce + ciphertext
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        if (data.size < SALT_LENGTH + NONCE_LENGTH + 1) {
            throw IllegalArgumentException("File too small to be a valid encrypted file")
        }

        val salt = data.copyOfRange(0, SALT_LENGTH)
        val nonce = data.copyOfRange(SALT_LENGTH, SALT_LENGTH + NONCE_LENGTH)
        val ciphertext = data.copyOfRange(SALT_LENGTH + NONCE_LENGTH, data.size)

        val derivedKey = deriveKey(password, salt)
        val keySpec = SecretKeySpec(derivedKey, "ChaCha20")
        val ivSpec = IvParameterSpec(nonce)

        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
}
