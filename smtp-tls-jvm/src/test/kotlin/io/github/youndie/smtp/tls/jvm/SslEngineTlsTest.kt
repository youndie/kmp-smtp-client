package io.github.youndie.smtp.tls.jvm

import io.github.youndie.smtp.transport.TlsConfig
import io.github.youndie.smtp.transport.TlsException
import io.github.youndie.smtp.transport.ktor.connectSmtp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * TLS on the JVM, against the same server the native tests use.
 *
 * The same three questions are asked here as of OpenSSL, and for the same reason: an
 * implementation that encrypts without verifying looks exactly like one that works.
 */
class SslEngineTlsTest {
    @Test
    fun `a certificate signed by a trusted authority is accepted`() =
        withServer { host, port, ca ->
            val transport = connectSmtp(host, port)
            try {
                transport.upgrade {
                    SslEngineTlsProvider.handshake(it, TlsConfig(serverName = SERVER_NAME, caBundlePath = ca))
                }
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
                assertFailsWith<TlsException> {
                    transport.upgrade {
                        SslEngineTlsProvider.handshake(it, TlsConfig(serverName = SERVER_NAME))
                    }
                }
            } finally {
                transport.close()
            }
        }

    @Test
    fun `a certificate issued for another name is refused`() =
        withServer { host, port, ca ->
            // rfc7817.txt: a valid chain says the certificate is genuine, not that it belongs to
            // the server on the other end of this socket.
            val transport = connectSmtp(host, port)
            try {
                assertFailsWith<TlsException> {
                    transport.upgrade {
                        SslEngineTlsProvider.handshake(
                            it,
                            TlsConfig(serverName = "wrong.example.com", caBundlePath = ca),
                        )
                    }
                }
            } finally {
                transport.close()
            }
        }

    private fun withServer(block: suspend (host: String, port: Int, ca: String) -> Unit) =
        runTest {
            val host = System.getenv("SMTP_TLS_E2E_HOST")
            val ca = System.getenv("SMTP_TLS_E2E_CA")

            if (host == null || ca == null) {
                if (System.getenv("SMTP_E2E_REQUIRED") != null) {
                    fail("SMTP_E2E_REQUIRED is set but SMTP_TLS_E2E_HOST/SMTP_TLS_E2E_CA are not")
                }
                println("SKIPPED SslEngineTlsTest: run `docker compose up -d` and set SMTP_TLS_E2E_HOST")
                return@runTest
            }

            val port = System.getenv("SMTP_TLS_E2E_PORT")?.toInt() ?: DEFAULT_TLS_PORT
            withContext(Dispatchers.Default) { block(host, port, ca) }
        }

    private companion object {
        const val DEFAULT_TLS_PORT = 1465
        const val SERVER_NAME = "localhost"
    }
}
