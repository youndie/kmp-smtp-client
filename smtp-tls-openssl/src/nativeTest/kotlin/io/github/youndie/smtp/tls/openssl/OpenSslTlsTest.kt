package io.github.youndie.smtp.tls.openssl

import io.github.youndie.smtp.transport.LineFramedTransport
import io.github.youndie.smtp.transport.TlsConfig
import io.github.youndie.smtp.transport.TlsException
import io.github.youndie.smtp.transport.ktor.connectSmtp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail
import platform.posix.getenv

/**
 * TLS against a real server from `docker-compose.yml`.
 *
 * The two failing cases matter more than the passing one: TLS that encrypts but proves nothing
 * looks exactly like TLS that works, right up until somebody is in the middle
 * (docs/research/research-architecture.md, risk 2).
 */
class OpenSslTlsTest {
    @Test
    fun `the process is linked against OpenSSL 3`() {
        // 1.x and 3.x are not ABI compatible, and a build that found 3.x headers can still load a
        // 1.x library at runtime.
        assertTrue(
            OpenSslTlsProvider.libraryVersion.startsWith("3."),
            "linked against ${OpenSslTlsProvider.libraryVersion}",
        )
    }

    @Test
    fun `a certificate signed by a trusted authority is accepted`() =
        withServer { host, port, ca ->
            val transport = connectSmtp(host, port)
            try {
                transport.upgrade { OpenSslTlsProvider.handshake(it, TlsConfig(serverName = SERVER_NAME, caBundlePath = ca)) }

                // The greeting only arrives once the handshake succeeded, so reading it proves the
                // encrypted session actually carries data.
                assertTrue(transport.readLine().startsWith("220"))
            } finally {
                transport.close()
            }
        }

    @Test
    fun `an unknown authority is refused`() =
        withServer { host, port, _ ->
            val transport = connectSmtp(host, port)
            try {
                // No CA bundle: the system trust store knows nothing about this certificate, which
                // is the same position a client is in when a self-signed certificate shows up.
                val failure =
                    assertFailsWith<TlsException> {
                        transport.upgrade { OpenSslTlsProvider.handshake(it, TlsConfig(serverName = SERVER_NAME)) }
                    }
                assertTrue(
                    failure.message!!.contains("certificate", ignoreCase = true),
                    "unexpected message: ${failure.message}",
                )
            } finally {
                transport.close()
            }
        }

    @Test
    fun `a certificate issued for another name is refused`() =
        withServer { host, port, ca ->
            // rfc7817.txt and rfc9525.txt: a valid chain says the certificate is genuine, not that
            // it belongs to the server being talked to.
            val transport = connectSmtp(host, port)
            try {
                assertFailsWith<TlsException> {
                    transport.upgrade {
                        OpenSslTlsProvider.handshake(
                            it,
                            TlsConfig(serverName = "wrong.example.com", caBundlePath = ca),
                        )
                    }
                }
            } finally {
                transport.close()
            }
        }

    @Test
    fun `verification can be disabled only by asking for it in so many words`() =
        withServer { host, port, _ ->
            val transport = connectSmtp(host, port)
            try {
                transport.upgrade {
                    OpenSslTlsProvider.handshake(
                        it,
                        TlsConfig(
                            serverName = "wrong.example.com",
                            dangerouslyDisableCertificateVerification = true,
                        ),
                    )
                }
                assertTrue(transport.readLine().startsWith("220"))
            } finally {
                transport.close()
            }
        }

    private fun withServer(block: suspend (host: String, port: Int, ca: String) -> Unit) =
        runTest {
            val host = environment("SMTP_TLS_E2E_HOST")
            val ca = environment("SMTP_TLS_E2E_CA")

            if (host == null || ca == null) {
                if (environment("SMTP_E2E_REQUIRED") != null) {
                    fail("SMTP_E2E_REQUIRED is set but SMTP_TLS_E2E_HOST/SMTP_TLS_E2E_CA are not")
                }
                println("SKIPPED OpenSslTlsTest: run `docker compose up -d` and set SMTP_TLS_E2E_HOST")
                return@runTest
            }

            val port = environment("SMTP_TLS_E2E_PORT")?.toInt() ?: DEFAULT_TLS_PORT
            withContext(Dispatchers.Default) { block(host, port, ca) }
        }

    @OptIn(ExperimentalForeignApi::class)
    private fun environment(name: String): String? = getenv(name)?.toKString()

    private companion object {
        const val DEFAULT_TLS_PORT = 1465

        /** Matches the SAN in `tools/generate-test-certs.sh`. */
        const val SERVER_NAME = "localhost"
    }
}
