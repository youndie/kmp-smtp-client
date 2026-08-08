package io.github.youndie.smtp.sasl

/**
 * One SASL mechanism, as a state machine over raw bytes.
 *
 * Base64 does not appear here: it belongs to the SMTP profile of SASL (`docs/rfc/rfc4954.txt:699`),
 * not to the mechanisms, and mixing the two is how a mechanism ends up unusable over any other
 * protocol.
 *
 * Instances are single-use and stateful. One authentication attempt, one instance.
 */
public interface SaslMechanism {
    /** The name announced in `AUTH`, upper-case (`docs/rfc/rfc4422.txt`). */
    public val name: String

    /**
     * The client-first message, or `null` for a mechanism that waits for the server to speak.
     *
     * An empty array is not the same as `null`: it means "a client-first message that happens to
     * be empty", which SMTP sends as `=` (`docs/rfc/rfc4954.txt:735`).
     */
    public fun initialResponse(): ByteArray?

    /** Answers a server challenge. */
    public fun respond(challenge: ByteArray): ByteArray

    /**
     * Whether the mechanism considers the exchange finished.
     *
     * Checked after the server reports success: a mechanism that authenticates the server too
     * (SCRAM does) must not be told "we are done" before it agrees.
     */
    public val isComplete: Boolean
}

/** The mechanism refused to continue: bad server message, failed server proof, unusable input. */
public class SaslException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
