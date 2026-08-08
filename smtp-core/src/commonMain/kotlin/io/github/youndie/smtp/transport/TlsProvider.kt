package io.github.youndie.smtp.transport

/**
 * How a plain connection becomes an encrypted one.
 *
 * Bytes in, bytes out: the provider wraps a [ByteConnection] and returns another one. That shape is
 * what makes `STARTTLS` an ordinary swap of the layer underneath the same conversation, and it is
 * forced on us anyway — a Ktor socket does not expose its file descriptor, so handing an `fd` to
 * OpenSSL was never an option (docs/research/research-architecture.md, consequence 3 of 1.1).
 */
public interface TlsProvider {
    public suspend fun handshake(
        connection: ByteConnection,
        config: TlsConfig,
    ): ByteConnection
}

/**
 * What the client expects of the server's certificate.
 *
 * @param serverName the name sent in SNI **and** checked against the certificate
 *   (`docs/rfc/rfc7817.txt`, `docs/rfc/rfc9525.txt`). Not the IP address the socket connected to:
 *   a certificate says who the server claims to be, and an address cannot vouch for that.
 * @param caBundlePath a PEM file to trust instead of the system store. Meant for tests and for
 *   private relays with an internal certificate authority.
 * @param dangerouslyDisableCertificateVerification turns off both the chain and the name check.
 *   Named so that nobody types it into production by accident: with it on, TLS still encrypts but
 *   stops proving anything, and an active attacker is indistinguishable from the real relay.
 */
public data class TlsConfig(
    public val serverName: String,
    public val caBundlePath: String? = null,
    public val dangerouslyDisableCertificateVerification: Boolean = false,
)

/** The handshake failed, or the certificate did not check out. */
public class TlsException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
