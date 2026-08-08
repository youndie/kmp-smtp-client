package io.github.youndie.smtp.testing

import io.github.youndie.smtp.transport.SmtpTransport
import io.github.youndie.smtp.transport.SmtpTransportException
import kotlinx.coroutines.awaitCancellation

/**
 * A transport that replays a written-down conversation: what the server says and what the client
 * is expected to write, in order.
 *
 * It exists so that the session layer can be tested without a socket — which is the whole point of
 * keeping I/O behind a port (docs/research/research-architecture.md, decision D3). Every deviation
 * from the script is an [AssertionError]: a fake that tolerates the wrong command turns the tests
 * above it into decoration.
 */
public class ScriptedTransport internal constructor(
    private val steps: List<Step>,
) : SmtpTransport {
    private val pending = ArrayDeque<String>()
    private var position = 0
    private var closed = false

    override suspend fun readLine(): String {
        pending.removeFirstOrNull()?.let { return it }

        return when (val step = currentStep("the client is reading a line")) {
            is Step.ServerSays -> {
                position++
                pending.addAll(step.lines)
                pending.removeFirst()
            }

            is Step.ServerCloses -> {
                position++
                throw SmtpTransportException("Connection closed while waiting for a reply line")
            }

            // The server never answers. Under `runTest` virtual time this costs nothing and lets
            // the timeout tests of M-24 finish instantly.
            is Step.ServerHangs -> {
                awaitCancellation()
            }

            is Step.ClientWrites -> {
                fail("the client is reading a line, but the script expects it to write '${step.text.visible()}'")
            }
        }
    }

    override suspend fun write(data: String) {
        val step = currentStep("the client wrote '${data.visible()}'")
        if (step !is Step.ClientWrites) {
            fail("the client wrote '${data.visible()}', but the script expects it to read")
        }

        if (step.text != data) {
            fail("the client wrote '${data.visible()}', but the script expects '${step.text.visible()}'")
        }

        position++
    }

    override suspend fun close() {
        closed = true
    }

    /** Fails unless the whole script was played out. A half-finished conversation proves nothing. */
    public fun assertScriptCompleted() {
        if (position != steps.size || pending.isNotEmpty()) {
            val left = steps.drop(position).joinToString { it.describe() }
            throw AssertionError("Script left unfinished at step ${position + 1} of ${steps.size}: $left")
        }
    }

    /** Whether [close] was called — a session that forgets to hang up is a leak. */
    public val isClosed: Boolean get() = closed

    private fun currentStep(what: String): Step = steps.getOrNull(position) ?: fail("$what, but the script is over")

    private fun fail(message: String): Nothing = throw AssertionError("Scripted transport: $message")

    internal sealed class Step {
        data class ServerSays(
            val lines: List<String>,
        ) : Step()

        data class ClientWrites(
            val text: String,
        ) : Step()

        data object ServerCloses : Step()

        data object ServerHangs : Step()

        fun describe(): String =
            when (this) {
                is ServerSays -> "server says ${lines.size} line(s)"
                is ClientWrites -> "client writes '${text.visible()}'"
                is ServerCloses -> "server closes"
                is ServerHangs -> "server hangs"
            }
    }

    public class Builder internal constructor() {
        internal val steps = mutableListOf<Step>()

        /** Lines the server sends, without their `CRLF` — the transport is line-based. */
        public fun serverSays(vararg lines: String) {
            steps += Step.ServerSays(lines.toList())
        }

        /** Exactly what the client is expected to write, `CRLF` included. */
        public fun clientWrites(text: String) {
            steps += Step.ClientWrites(text)
        }

        /** The server hangs up: the next read fails as a transport error. */
        public fun serverCloses() {
            steps += Step.ServerCloses
        }

        /** The server goes quiet forever — the shape of a timeout. */
        public fun serverHangs() {
            steps += Step.ServerHangs
        }
    }
}

/** `CRLF` shown as it is written, so that a mismatch message stays on one line. */
private fun String.visible(): String = replace("\r", "\\r").replace("\n", "\\n")

public fun scriptedTransport(block: ScriptedTransport.Builder.() -> Unit): ScriptedTransport =
    ScriptedTransport(
        ScriptedTransport
            .Builder()
            .apply(block)
            .steps
            .toList(),
    )
