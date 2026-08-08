package io.github.youndie.smtp.sasl

import org.kotlincrypto.hash.sha1.SHA1
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.macs.hmac.md.HmacMD5
import org.kotlincrypto.macs.hmac.sha1.HmacSHA1
import org.kotlincrypto.macs.hmac.sha2.HmacSHA256
import org.kotlincrypto.random.CryptoRand

/** HMAC-MD5, the one thing CRAM-MD5 needs (`docs/rfc/rfc2195.txt:135`). */
internal fun hmacMd5(
    key: ByteArray,
    message: ByteArray,
): ByteArray = HmacMD5(key).doFinal(message)

internal fun mac(
    key: ByteArray,
    message: ByteArray,
    algorithm: ScramAlgorithm,
): ByteArray =
    when (algorithm) {
        ScramAlgorithm.SHA_1 -> HmacSHA1(key).doFinal(message)
        ScramAlgorithm.SHA_256 -> HmacSHA256(key).doFinal(message)
    }

internal fun hash(
    data: ByteArray,
    algorithm: ScramAlgorithm,
): ByteArray =
    when (algorithm) {
        ScramAlgorithm.SHA_1 -> SHA1().digest(data)
        ScramAlgorithm.SHA_256 -> SHA256().digest(data)
    }

/**
 * `Hi` from `docs/rfc/rfc5802.txt` — PBKDF2 with one output block.
 *
 * Written out rather than taken from a library because KotlinCrypto has no KDF module, and one
 * block of PBKDF2 is a loop over HMAC.
 */
internal fun hi(
    password: ByteArray,
    salt: ByteArray,
    iterations: Int,
    algorithm: ScramAlgorithm,
): ByteArray {
    // U1 = HMAC(password, salt || INT(1))
    var block = mac(password, salt + byteArrayOf(0, 0, 0, 1), algorithm)
    val result = block.copyOf()

    repeat(iterations - 1) {
        block = mac(password, block, algorithm)
        for (index in result.indices) {
            result[index] = (result[index].toInt() xor block[index].toInt()).toByte()
        }
    }
    return result
}

/** Lower-case hex, the form CRAM-MD5 puts on the wire. */
internal fun ByteArray.toHex(): String =
    buildString(size * 2) {
        this@toHex.forEach { byte ->
            append(HEX[(byte.toInt() shr 4) and 0xF])
            append(HEX[byte.toInt() and 0xF])
        }
    }

private const val HEX = "0123456789abcdef"

/**
 * A nonce from a cryptographic source.
 *
 * `kotlin.random.Random` is not one, and SCRAM leans on the client nonce being unpredictable.
 */
internal fun defaultNonce(): String {
    val bytes = ByteArray(NONCE_BYTES)
    CryptoRand.Default.nextBytes(bytes)
    // Printable ASCII without ',' — the message syntax uses it as a separator.
    return bytes.joinToString("") { byte -> NONCE_ALPHABET[(byte.toInt() and 0xFF) % NONCE_ALPHABET.length].toString() }
}

private const val NONCE_BYTES = 24
private const val NONCE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
