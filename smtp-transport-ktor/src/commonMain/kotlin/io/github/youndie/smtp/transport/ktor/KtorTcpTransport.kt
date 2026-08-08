package io.github.youndie.smtp.transport.ktor

import io.github.youndie.smtp.transport.SmtpTransport
import io.github.youndie.smtp.transport.SmtpTransportException
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers

/**
 * A TCP transport over `ktor-network`.
 *
 * The only place in the project that mentions Ktor at all (docs/research/research-architecture.md,
 * decision D1). Ktor is taken for exactly one thing: a coroutine-aware selector already ported to
 * every native target. TLS is **not** taken from it — on Kotlin/Native `ktor-network-tls` is a stub
 * that throws at runtime (research 1.1), so `ktor-network-tls` must never appear in the
 * dependencies.
 *
 * Reads and writes go through two independent channels, so a reply can be read while a command
 * group is still being written — the condition PIPELINING needs (`docs/rfc/rfc2920.txt:183`).
 */
public class KtorTcpTransport internal constructor(
    private val selector: SelectorManager,
    private val socket: Socket,
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel,
) : SmtpTransport {
    override suspend fun readLine(): String =
        try {
            input.readLine()
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            throw SmtpTransportException("Failed to read a line from ${socket.remoteAddress}", cause)
        } ?: throw SmtpTransportException("Connection closed while waiting for a reply line")

    override suspend fun write(data: String) {
        try {
            output.writeString(data)
            output.flush()
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            throw SmtpTransportException("Failed to write to ${socket.remoteAddress}", cause)
        }
    }

    override suspend fun close() {
        // Closed in the reverse order of acquisition, and the selector last: it owns the worker
        // thread the native implementation polls on.
        output.flushAndClose()
        socket.close()
        selector.close()
    }

    public companion object {
        public suspend fun connect(
            host: String,
            port: Int,
        ): KtorTcpTransport {
            val selector = SelectorManager(Dispatchers.Default)
            val socket =
                try {
                    aSocket(selector).tcp().connect(host, port)
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    selector.close()
                    throw SmtpTransportException("Failed to connect to $host:$port", cause)
                }

            return KtorTcpTransport(
                selector = selector,
                socket = socket,
                input = socket.openReadChannel(),
                output = socket.openWriteChannel(),
            )
        }
    }
}
