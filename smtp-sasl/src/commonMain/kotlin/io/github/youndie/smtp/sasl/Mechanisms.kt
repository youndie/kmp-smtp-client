package io.github.youndie.smtp.sasl

public class PlainMechanism(
    private val username: String,
    private val password: String,
    private val authorizationIdentity: String? = null,
) : SaslMechanism {
    override val name: String get() = "PLAIN"

    override fun initialResponse(): ByteArray = TODO("M-53")

    override fun respond(challenge: ByteArray): ByteArray = TODO("M-53")

    override val isComplete: Boolean get() = true
}

public class LoginMechanism(
    private val username: String,
    private val password: String,
) : SaslMechanism {
    override val name: String get() = "LOGIN"

    override fun initialResponse(): ByteArray? = null

    override fun respond(challenge: ByteArray): ByteArray = TODO("M-54")

    override val isComplete: Boolean get() = TODO("M-54")
}

public class CramMd5Mechanism(
    private val username: String,
    private val secret: String,
) : SaslMechanism {
    override val name: String get() = "CRAM-MD5"

    override fun initialResponse(): ByteArray? = null

    override fun respond(challenge: ByteArray): ByteArray = TODO("M-55")

    override val isComplete: Boolean get() = TODO("M-55")
}

public enum class ScramAlgorithm { SHA_1, SHA_256 }

public class ScramMechanism(
    private val username: String,
    private val password: String,
    private val algorithm: ScramAlgorithm,
    private val nonceSource: () -> String = { TODO("M-56") },
) : SaslMechanism {
    override val name: String get() = TODO("M-56")

    override fun initialResponse(): ByteArray = TODO("M-56")

    override fun respond(challenge: ByteArray): ByteArray = TODO("M-56")

    override val isComplete: Boolean get() = TODO("M-56")
}

public class XOAuth2Mechanism(
    private val username: String,
    private val accessToken: String,
) : SaslMechanism {
    override val name: String get() = "XOAUTH2"

    override fun initialResponse(): ByteArray = TODO("M-57")

    override fun respond(challenge: ByteArray): ByteArray = TODO("M-57")

    override val isComplete: Boolean get() = true
}

public class OAuthBearerMechanism(
    private val username: String,
    private val accessToken: String,
    private val host: String,
    private val port: Int,
) : SaslMechanism {
    override val name: String get() = "OAUTHBEARER"

    override fun initialResponse(): ByteArray = TODO("M-57")

    override fun respond(challenge: ByteArray): ByteArray = TODO("M-57")

    override val isComplete: Boolean get() = true
}
