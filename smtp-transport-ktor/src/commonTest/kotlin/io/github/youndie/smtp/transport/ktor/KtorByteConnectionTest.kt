package io.github.youndie.smtp.transport.ktor

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
 * The Ktor-backed byte pipe, checked against an in-process server so that the test needs no
 * container and no network beyond the loopback interface.
 */
class KtorByteConnectionTest {
    @Test
    fun `bytes travel in both directions`() {
        var received: String? = null

        withServer(
            script = { input, output ->
                output.writeString("220 smtp.example.com ESMTP\r\n")
                output.flush()
                received = input.readLine()
            },
        ) { transport ->
            assertEquals("220 smtp.example.com ESMTP", transport.readLine())
            transport.write("EHLO client.example.com\r\n")
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
        block: suspend (io.github.youndie.smtp.transport.LineFramedTransport) -> Unit,
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

                    val transport = connectSmtp("127.0.0.1", port)
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
