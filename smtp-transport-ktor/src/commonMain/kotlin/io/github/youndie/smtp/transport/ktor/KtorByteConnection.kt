package io.github.youndie.smtp.transport.ktor

import io.github.youndie.smtp.transport.ByteConnection
import io.github.youndie.smtp.transport.LineFramedTransport
import io.github.youndie.smtp.transport.SmtpTransportException
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers

/**
 * A TCP byte pipe over `ktor-network`.
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
public class KtorByteConnection internal constructor(
    private val selector: SelectorManager,
    private val socket: Socket,
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel,
) : ByteConnection {
    override suspend fun read(destination: ByteArray): Int =
        try {
            input.readAvailable(destination)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            throw SmtpTransportException("Failed to read from ${socket.remoteAddress}", cause)
        }

    override suspend fun write(
        source: ByteArray,
        length: Int,
    ) {
        try {
            output.writeFully(source, 0, length)
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
        ): KtorByteConnection {
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

            return KtorByteConnection(
                selector = selector,
                socket = socket,
                input = socket.openReadChannel(),
                output = socket.openWriteChannel(),
            )
        }
    }
}

/**
 * Connects and wraps the connection in line framing — what a session needs.
 *
 * The result exposes `upgrade`, which is where TLS is inserted on M4.
 */
public suspend fun connectSmtp(
    host: String,
    port: Int,
): LineFramedTransport = LineFramedTransport(KtorByteConnection.connect(host, port))
