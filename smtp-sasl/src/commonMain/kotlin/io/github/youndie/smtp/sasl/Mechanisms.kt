package io.github.youndie.smtp.sasl

import kotlin.io.encoding.Base64

/**
 * `PLAIN` — `docs/rfc/rfc4616.txt`.
 *
 * The password travels readable, so this belongs inside TLS and nowhere else. The session refuses
 * to send it over a cleartext connection unless told to in so many words.
 */
public class PlainMechanism(
    private val username: String,
    private val password: String,
    private val authorizationIdentity: String? = null,
) : SaslMechanism {
    override val name: String get() = "PLAIN"

    override fun initialResponse(): ByteArray {
        // rfc4616.txt:83: message = [authzid] UTF8NUL authcid UTF8NUL passwd. NUL is the separator,
        // so a NUL inside a value would move the field boundaries without anybody noticing.
        val parts = listOfNotNull(authorizationIdentity.orEmpty(), username, password)
        parts.forEach { part ->
            if (part.contains(NUL)) {
                throw SaslException("PLAIN: a credential contains a NUL, which is the field separator")
            }
        }

        return parts.joinToString(NUL.toString()) { SaslPrep.prepare(it) }.encodeToByteArray()
    }

    override fun respond(challenge: ByteArray): ByteArray =
        throw SaslException("PLAIN has nothing more to say; the server sent a challenge anyway")

    override val isComplete: Boolean get() = true

    private companion object {
        const val NUL = '\u0000'
    }
}

/**
 * `LOGIN` — no RFC, only a draft that expired in 1998, and every mail provider on earth.
 *
 * Implemented for compatibility. `PLAIN` inside TLS is the same thing with fewer round trips.
 */
public class LoginMechanism(
    private val username: String,
    private val password: String,
) : SaslMechanism {
    private var step = 0

    override val name: String get() = "LOGIN"

    override fun initialResponse(): ByteArray? = null

    override fun respond(challenge: ByteArray): ByteArray {
        // The challenges are "Username:" and "Password:", but their exact text is not standardised,
        // so the order is what decides — matching on the text would break on a translated server.
        return when (step++) {
            0 -> SaslPrep.prepare(username).encodeToByteArray()
            1 -> SaslPrep.prepare(password).encodeToByteArray()
            else -> throw SaslException("LOGIN: the server asked a third question: '${challenge.decodeToString()}'")
        }
    }

    override val isComplete: Boolean get() = step >= 2
}

/**
 * `CRAM-MD5` — `docs/rfc/rfc2195.txt`.
 *
 * Keeps the password off the wire, but the server has to store it recoverably, and MD5 is long
 * past retirement. Offered because old relays still ask for it; prefer SCRAM where there is a
 * choice.
 */
public class CramMd5Mechanism(
    private val username: String,
    private val secret: String,
) : SaslMechanism {
    private var answered = false

    override val name: String get() = "CRAM-MD5"

    override fun initialResponse(): ByteArray? = null

    override fun respond(challenge: ByteArray): ByteArray {
        if (answered) throw SaslException("CRAM-MD5: the server sent a second challenge")
        answered = true

        // rfc2195.txt:135: HMAC-MD5 over the challenge, keyed with the shared secret, written as
        // lower-case hex after the user name and a space.
        val digest = hmacMd5(SaslPrep.prepare(secret).encodeToByteArray(), challenge)
        return "${SaslPrep.prepare(username)} ${digest.toHex()}".encodeToByteArray()
    }

    override val isComplete: Boolean get() = answered
}

/** Which hash a SCRAM exchange is built on. */
public enum class ScramAlgorithm(
    internal val mechanismName: String,
) {
    /** `docs/rfc/rfc5802.txt` */
    SHA_1("SCRAM-SHA-1"),

    /** `docs/rfc/rfc7677.txt` */
    SHA_256("SCRAM-SHA-256"),
}

/**
 * `SCRAM` — `docs/rfc/rfc5802.txt` and `docs/rfc/rfc7677.txt`.
 *
 * The only mechanism here that authenticates the server as well as the client. The server proof is
 * checked, and a mechanism whose proof did not check out never reports itself complete — the
 * session then refuses a `235` that arrives anyway.
 *
 * Channel binding (`-PLUS` variants) is not implemented; the message announces `n`, meaning the
 * client does not support it.
 */
public class ScramMechanism(
    private val username: String,
    private val password: String,
    private val algorithm: ScramAlgorithm,
    private val nonceSource: () -> String = { defaultNonce() },
) : SaslMechanism {
    private var clientFirstBare: String? = null
    private var clientNonce: String? = null
    private var expectedServerSignature: ByteArray? = null
    private var verified = false

    override val name: String get() = algorithm.mechanismName

    override fun initialResponse(): ByteArray {
        val nonce = nonceSource()
        clientNonce = nonce

        // rfc5802.txt: the "n," says this client does not do channel binding; the empty authzid
        // follows. Both are part of what the proof is computed over, so they cannot be changed
        // later.
        val bare = "n=${escapeSaslName(SaslPrep.prepare(username))},r=$nonce"
        clientFirstBare = bare
        return "$GS2_HEADER$bare".encodeToByteArray()
    }

    override fun respond(challenge: ByteArray): ByteArray {
        val message = challenge.decodeToString()
        return if (expectedServerSignature == null) handleServerFirst(message) else handleServerFinal(message)
    }

    override val isComplete: Boolean get() = verified

    private fun handleServerFirst(message: String): ByteArray {
        val attributes = parseAttributes(message)
        attributes["e"]?.let { throw SaslException("${algorithm.mechanismName}: the server refused: $it") }

        val serverNonce =
            attributes["r"] ?: throw SaslException("${algorithm.mechanismName}: no nonce in the server message")
        val salt = attributes["s"] ?: throw SaslException("${algorithm.mechanismName}: no salt in the server message")
        val iterations =
            attributes["i"]?.toIntOrNull()
                ?: throw SaslException("${algorithm.mechanismName}: no iteration count in the server message")

        // The server nonce must extend the client one; anything else is somebody replaying an
        // exchange that was not ours.
        val ourNonce = clientNonce ?: throw SaslException("${algorithm.mechanismName}: used out of order")
        if (!serverNonce.startsWith(ourNonce)) {
            throw SaslException("${algorithm.mechanismName}: the server nonce does not extend the client nonce")
        }

        // A tiny iteration count makes the stored password cheap to attack; rfc7677.txt recommends
        // at least 4096 and no server has a reason to offer less.
        if (iterations < MIN_ITERATIONS) {
            throw SaslException("${algorithm.mechanismName}: the server asked for only $iterations iterations")
        }

        val saltedPassword =
            hi(SaslPrep.prepare(password).encodeToByteArray(), Base64.decode(salt), iterations, algorithm)
        val clientKey = mac(saltedPassword, "Client Key".encodeToByteArray(), algorithm)
        val storedKey = hash(clientKey, algorithm)

        val clientFinalWithoutProof = "c=$CHANNEL_BINDING,r=$serverNonce"
        // rfc5802.txt: the proof covers all three messages verbatim, including the server's.
        val authMessage = "${clientFirstBare!!},$message,$clientFinalWithoutProof"

        val clientSignature = mac(storedKey, authMessage.encodeToByteArray(), algorithm)
        val proof = ByteArray(clientKey.size) { (clientKey[it].toInt() xor clientSignature[it].toInt()).toByte() }

        val serverKey = mac(saltedPassword, "Server Key".encodeToByteArray(), algorithm)
        expectedServerSignature = mac(serverKey, authMessage.encodeToByteArray(), algorithm)

        return "$clientFinalWithoutProof,p=${Base64.encode(proof)}".encodeToByteArray()
    }

    private fun handleServerFinal(message: String): ByteArray {
        val attributes = parseAttributes(message)
        attributes["e"]?.let { throw SaslException("${algorithm.mechanismName}: the server refused: $it") }

        val signature = attributes["v"] ?: throw SaslException("${algorithm.mechanismName}: no server signature")
        val expected = expectedServerSignature ?: throw SaslException("${algorithm.mechanismName}: used out of order")

        if (!Base64.decode(signature).contentEquals(expected)) {
            // This is the half that authenticates the server. Skipping it turns SCRAM into a
            // slower PLAIN with extra steps.
            throw SaslException("${algorithm.mechanismName}: the server signature does not match")
        }

        verified = true
        return ByteArray(0)
    }

    private fun parseAttributes(message: String): Map<String, String> =
        message
            .split(',')
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) null else part.substring(0, separator) to part.substring(separator + 1)
            }.toMap()

    /** rfc5802.txt: `=` and `,` are escaped in the user name, since both are message syntax. */
    private fun escapeSaslName(value: String): String = value.replace("=", "=3D").replace(",", "=2C")

    private companion object {
        /** No channel binding, no authorization identity. */
        const val GS2_HEADER = "n,,"

        /** base64 of the GS2 header, which is what `c=` carries. */
        const val CHANNEL_BINDING = "biws"

        const val MIN_ITERATIONS = 4096
    }
}

/**
 * `XOAUTH2` — Google's format, never standardised, and what most OAuth relays actually accept.
 *
 * `OAUTHBEARER` (`docs/rfc/rfc7628.txt`) is the standard successor; offer that first where the
 * server announces it.
 */
public class XOAuth2Mechanism(
    private val username: String,
    private val accessToken: String,
) : SaslMechanism {
    override val name: String get() = "XOAUTH2"

    override fun initialResponse(): ByteArray =
        "user=$username\u0001auth=Bearer $accessToken\u0001\u0001".encodeToByteArray()

    override fun respond(challenge: ByteArray): ByteArray {
        // A failure arrives as a challenge carrying JSON; the exchange still has to be closed off
        // before the server will report it properly.
        throw SaslException("XOAUTH2: the server refused the token: ${challenge.decodeToString()}")
    }

    override val isComplete: Boolean get() = true
}

/** `OAUTHBEARER` — `docs/rfc/rfc7628.txt`. */
public class OAuthBearerMechanism(
    private val username: String,
    private val accessToken: String,
    private val host: String,
    private val port: Int,
) : SaslMechanism {
    override val name: String get() = "OAUTHBEARER"

    override fun initialResponse(): ByteArray =
        (
            "n,a=$username,\u0001host=$host\u0001port=$port\u0001" +
                "auth=Bearer $accessToken\u0001\u0001"
        ).encodeToByteArray()

    override fun respond(challenge: ByteArray): ByteArray {
        // rfc7628.txt: on failure the server sends a JSON error and expects a single ^A back
        // before it reports the failure with a proper reply code.
        return "\u0001".encodeToByteArray()
    }

    override val isComplete: Boolean get() = true
}
