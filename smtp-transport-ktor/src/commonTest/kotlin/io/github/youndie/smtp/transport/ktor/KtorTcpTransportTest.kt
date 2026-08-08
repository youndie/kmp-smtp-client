package io.github.youndie.smtp.transport.ktor

import io.github.youndie.smtp.protocol.SmtpCommand
import io.github.youndie.smtp.transport.SmtpTransportException
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The Ktor-backed TCP transport, checked against an in-process server so that the test needs no
 * container and no network beyond the loopback interface.
 */
class KtorTcpTransportTest {
    @Test
    fun `lines are split on CRLF`() =
        withServer(
            script = { _, output ->
                output.writeString("220 smtp.example.com ESMTP\r\n250-PIPELINING\r\n250 SIZE 100\r\n")
                output.flush()
            },
        ) { transport ->
            assertEquals("220 smtp.example.com ESMTP", transport.readLine())
            assertEquals("250-PIPELINING", transport.readLine())
            assertEquals("250 SIZE 100", transport.readLine())
        }

    @Test
    fun `what the protocol layer encodes is what reaches the server`() {
        var received: String? = null

        withServer(
            script = { input, _ ->
                received = input.readLine()
            },
        ) { transport ->
            transport.write(SmtpCommand.Ehlo("client.example.com").encode())
        }

        assertEquals("EHLO client.example.com", received)
    }

    @Test
    fun `a connection closed mid-session is a transport failure`() =
        withServer(
            // The server says nothing and hangs up.
            script = { _, _ -> },
        ) { transport ->
            assertFailsWith<SmtpTransportException> { transport.readLine() }
        }

    /**
     * Starts a one-shot TCP server, hands the connected transport to [block], then tears both down.
     *
     * Real sockets need a real dispatcher, hence `withContext(Dispatchers.Default)` inside
     * `runTest`: the test scheduler's virtual time has nothing to offer here.
     */
    private fun withServer(
        script: suspend (input: io.ktor.utils.io.ByteReadChannel, output: io.ktor.utils.io.ByteWriteChannel) -> Unit,
        block: suspend (KtorTcpTransport) -> Unit,
    ) = runTest {
        withContext(Dispatchers.Default) {
            val selector = SelectorManager(Dispatchers.Default)
            val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
            val port = (server.localAddress as InetSocketAddress).port

            try {
                coroutineScope {
                    val served =
                        async {
                            server.accept().use { socket ->
                                script(socket.openReadChannel(), socket.openWriteChannel(autoFlush = true))
                            }
                        }

                    val transport = KtorTcpTransport.connect("127.0.0.1", port)
                    try {
                        block(transport)
                    } finally {
                        transport.close()
                    }
                    served.await()
                }
            } finally {
                server.close()
                selector.close()
            }
        }
    }
}
