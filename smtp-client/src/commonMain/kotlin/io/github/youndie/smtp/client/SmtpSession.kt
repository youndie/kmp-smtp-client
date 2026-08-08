package io.github.youndie.smtp.client

import io.github.youndie.smtp.protocol.Capabilities
import io.github.youndie.smtp.protocol.MailData
import io.github.youndie.smtp.protocol.Mailbox
import io.github.youndie.smtp.protocol.SmtpCommand
import io.github.youndie.smtp.protocol.SmtpProtocolException
import io.github.youndie.smtp.protocol.SmtpRefusedException
import io.github.youndie.smtp.protocol.SmtpReply
import io.github.youndie.smtp.protocol.SmtpReplyReader
import io.github.youndie.smtp.protocol.SmtpReplySeverity
import io.github.youndie.smtp.sasl.SaslException
import io.github.youndie.smtp.sasl.SaslMechanism
import io.github.youndie.smtp.transport.LineFramedTransport
import io.github.youndie.smtp.transport.SmtpTransport
import io.github.youndie.smtp.transport.TlsConfig
import io.github.youndie.smtp.transport.TlsProvider
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** What is being sent, as the envelope sees it — not to be confused with the message headers. */
public data class Envelope(
    public val sender: Mailbox?,
    public val recipients: List<Mailbox>,
)

/** A recipient the server would not take, with the reply that says why. */
public data class RejectedRecipient(
    public val mailbox: Mailbox,
    public val reply: SmtpReply,
)

/**
 * The outcome of one transaction.
 *
 * A partial refusal is data, not an exception: collapsing it into a throw would lose the knowledge
 * of who *did* get the message, and that knowledge is the only thing standing between a retry and
 * a duplicate.
 *
 * [acceptance] is `null` when no recipient was accepted, so the message was never sent.
 */
public data class DeliveryResult(
    public val accepted: List<Mailbox>,
    public val rejected: List<RejectedRecipient>,
    public val acceptance: SmtpReply?,
)

/**
 * How long the client waits.
 *
 * The defaults are the minimums from `docs/rfc/rfc5321.txt:3610`: waiting less than this violates
 * the specification, so shortening them is the caller's decision to make knowingly.
 */
public data class SmtpTimeouts(
    /** `docs/rfc/rfc5321.txt:3623` */
    public val greeting: Duration = 5.minutes,
    /** `docs/rfc/rfc5321.txt:3631` */
    public val mailCommand: Duration = 5.minutes,
    /** `docs/rfc/rfc5321.txt:3633` */
    public val recipientCommand: Duration = 5.minutes,
    /** `docs/rfc/rfc5321.txt:3647` */
    public val dataInitiation: Duration = 2.minutes,
    /** `docs/rfc/rfc5321.txt:3651` */
    public val dataBlock: Duration = 3.minutes,
    /** `docs/rfc/rfc5321.txt:3656` */
    public val dataTermination: Duration = 10.minutes,
    /**
     * Everything the RFC gives no number for: `EHLO`, `HELO`, `RSET`, `QUIT`, `STARTTLS`.
     *
     * Chosen here, not quoted — five minutes matches the neighbouring commands.
     */
    public val otherCommands: Duration = 5.minutes,
)

public data class SmtpClientConfig(
    /** What goes into `EHLO`; the server may check it against the connecting address. */
    public val clientIdentity: String,
    public val timeouts: SmtpTimeouts = SmtpTimeouts(),
)

/** The server stayed silent past the limit. */
public class SmtpTimeoutException(
    public val what: String,
    public val limit: Duration,
) : RuntimeException("Timed out after $limit waiting for $what")

/**
 * One SMTP conversation over one connection.
 *
 * Owns the phase the session is in: the greeting, the announced extensions, and the fact that
 * `STARTTLS` invalidates them (`docs/rfc/rfc3207.txt:210`). Knows nothing about sockets or TLS —
 * both arrive through [SmtpTransport] and through the lambda handed to [startTls].
 *
 * Not thread-safe, and not meant to be: one connection, one session, one coroutine.
 */
public class SmtpSession internal constructor(
    private val transport: SmtpTransport,
    private val config: SmtpClientConfig,
) {
    private val reader = SmtpReplyReader()
    private lateinit var announced: Capabilities
    private var encrypted = false

    /** Whether the conversation is running inside TLS. */
    public val isEncrypted: Boolean get() = encrypted

    /**
     * What the server announced in the current phase.
     *
     * Always current: after `STARTTLS` this is a different value, and the previous one starts
     * throwing (see `Capabilities.markStale`).
     */
    public val capabilities: Capabilities get() = announced

    /**
     * Runs one transaction: `MAIL FROM`, a `RCPT TO` per recipient, then `DATA` and the body.
     *
     * Throws [SmtpRefusedException] when the server refuses the transaction as a whole; a refusal
     * of individual recipients comes back inside [DeliveryResult].
     */
    public suspend fun send(
        envelope: Envelope,
        body: List<String>,
    ): DeliveryResult {
        val mail = exchange(SmtpCommand.MailFrom(envelope.sender), "MAIL FROM", config.timeouts.mailCommand)
        if (!mail.isPositiveCompletion) throw SmtpRefusedException("MAIL FROM", mail)

        val accepted = mutableListOf<Mailbox>()
        val rejected = mutableListOf<RejectedRecipient>()

        for (recipient in envelope.recipients) {
            val reply = exchange(SmtpCommand.RcptTo(recipient), "RCPT TO", config.timeouts.recipientCommand)
            // 250 and 251 both mean accepted (`docs/rfc/rfc5321.txt:2642`).
            if (reply.isPositiveCompletion) accepted += recipient else rejected += RejectedRecipient(recipient, reply)
        }

        if (accepted.isEmpty()) {
            // Nobody left to send to. RSET rather than DATA, so the connection stays usable for
            // the next message instead of being torn down.
            reset()
            return DeliveryResult(accepted = emptyList(), rejected = rejected, acceptance = null)
        }

        val data = exchange(SmtpCommand.Data, "DATA", config.timeouts.dataInitiation)
        if (data.code.severity != SmtpReplySeverity.POSITIVE_INTERMEDIATE) {
            throw SmtpRefusedException("DATA", data)
        }

        write(MailData.encode(body), "sending the message body", config.timeouts.dataBlock)
        val acceptance = readReply("the reply to the end of the message", config.timeouts.dataTermination)
        if (!acceptance.isPositiveCompletion) throw SmtpRefusedException("end of DATA", acceptance)

        return DeliveryResult(accepted = accepted, rejected = rejected, acceptance = acceptance)
    }

    /** `RSET` — drops the current transaction, keeps the session and any authentication. */
    public suspend fun reset() {
        val reply = exchange(SmtpCommand.Rset, "RSET", config.timeouts.otherCommands)
        if (!reply.isPositiveCompletion) throw SmtpRefusedException("RSET", reply)
    }

    /**
     * `QUIT` and close.
     *
     * A rude answer is not turned into an exception: the session is over either way, and failing
     * here would only mask whatever the caller was really doing.
     */
    public suspend fun quit() {
        exchange(SmtpCommand.Quit, "QUIT", config.timeouts.otherCommands)
        transport.close()
    }

    /**
     * `STARTTLS` — `docs/rfc/rfc3207.txt`.
     *
     * [upgrade] performs the handshake over the same connection; the session does not know how,
     * and that is the seam the TLS module plugs into on M4.
     *
     * Afterwards everything learned before the handshake is discarded and `EHLO` is sent again,
     * because `docs/rfc/rfc3207.txt:210` requires exactly that — a client that keeps the old
     * extension list can be talked into a downgrade by an active attacker.
     */
    public suspend fun startTls(upgrade: suspend () -> Unit) {
        val reply = exchange(SmtpCommand.StartTls, "STARTTLS", config.timeouts.otherCommands)
        if (!reply.isPositiveCompletion) throw SmtpRefusedException("STARTTLS", reply)

        upgrade()

        if (!reader.isIdle) {
            throw SmtpProtocolException("The server sent data before the TLS handshake was finished")
        }

        announced.markStale()
        identify()
    }

    /**
     * `STARTTLS` with a provider doing the handshake.
     *
     * Needs a transport whose byte layer can be swapped — that is what [LineFramedTransport] is
     * for. A scripted or already encrypted transport cannot do it, and saying so out loud beats
     * pretending the upgrade happened.
     */
    public suspend fun startTls(
        provider: TlsProvider,
        config: TlsConfig,
    ) {
        val framed =
            transport as? LineFramedTransport
                ?: throw SmtpProtocolException(
                    "STARTTLS needs a transport that can swap its byte layer, got ${transport::class.simpleName}",
                )

        startTls { framed.upgrade { connection -> provider.handshake(connection, config) } }
    }

    /**
     * `AUTH` — `docs/rfc/rfc4954.txt`.
     *
     * Refuses to run over a cleartext connection unless [allowOverPlaintext] says otherwise:
     * credentials sent in the clear are credentials given away, and `docs/rfc/rfc8314.txt` calls
     * cleartext submission obsolete. The flag exists for test servers and for tunnels the library
     * cannot see.
     *
     * On success everything learned before authentication is discarded and `EHLO` is sent again,
     * exactly as after `STARTTLS`: `docs/rfc/rfc4954.txt:297` requires it, and a client that keeps
     * the old list can be walked into a downgrade.
     */
    public suspend fun authenticate(
        mechanism: SaslMechanism,
        allowOverPlaintext: Boolean = false,
    ) {
        if (!isEncrypted && !allowOverPlaintext) {
            throw SmtpProtocolException(
                "Refusing to send credentials over a cleartext connection. Run STARTTLS first, " +
                    "or pass allowOverPlaintext = true if the channel is protected in a way this " +
                    "library cannot see.",
            )
        }

        var reply = begin(mechanism)

        while (reply.code.severity == SmtpReplySeverity.POSITIVE_INTERMEDIATE) {
            val challenge = decodeChallenge(reply, mechanism)
            val response =
                try {
                    mechanism.respond(challenge)
                } catch (cause: SaslException) {
                    cancel()
                    throw cause
                }
            reply = exchangeRaw(Base64.encode(response), "the SASL response")
        }

        if (!reply.isPositiveCompletion) throw SmtpRefusedException("AUTH", reply)

        if (!mechanism.isComplete) {
            throw SaslException(
                "The server reported success before ${mechanism.name} was satisfied; " +
                    "it has not proven who it is",
            )
        }

        announced.markStale()
        identify()
    }

    /**
     * Sends `AUTH`, using the initial response only when it fits.
     *
     * `docs/rfc/rfc4954.txt:208`: if the initial response would push the command past the line
     * limit, the client **must not** use that parameter and has to send the same bytes as an
     * ordinary response instead. OAuth tokens hit this routinely.
     */
    private suspend fun begin(mechanism: SaslMechanism): SmtpReply {
        val initial =
            mechanism.initialResponse() ?: return exchange(SmtpCommand.Auth(mechanism.name), "AUTH", authTimeout)

        val encoded = if (initial.isEmpty()) EMPTY_INITIAL_RESPONSE else Base64.encode(initial)
        val withInitial = SmtpCommand.Auth(mechanism.name, encoded)
        if (withInitial.fitsLineLimit) return exchange(withInitial, "AUTH", authTimeout)

        val started = exchange(SmtpCommand.Auth(mechanism.name), "AUTH", authTimeout)
        if (started.code.severity != SmtpReplySeverity.POSITIVE_INTERMEDIATE) return started
        return exchangeRaw(encoded, "the SASL response")
    }

    private fun decodeChallenge(
        reply: SmtpReply,
        mechanism: SaslMechanism,
    ): ByteArray {
        val text = reply.lines.first()
        if (text.isEmpty()) return ByteArray(0)

        return try {
            Base64.decode(text)
        } catch (cause: IllegalArgumentException) {
            throw SaslException("${mechanism.name}: the server challenge is not valid base64: '$text'", cause)
        }
    }

    /** Cancels the exchange with a single `*` — `docs/rfc/rfc4954.txt:194`. */
    private suspend fun cancel() {
        runCatching { exchangeRaw(SASL_CANCEL, "the reply to a cancelled SASL exchange") }
    }

    private suspend fun exchangeRaw(
        line: String,
        what: String,
    ): SmtpReply {
        write(line + CRLF, "sending $what", authTimeout)
        return readReply("the reply to $what", authTimeout)
    }

    private val authTimeout get() = config.timeouts.otherCommands

    internal suspend fun handshake() {
        val greeting = readReply("the server greeting", config.timeouts.greeting)
        if (!greeting.isPositiveCompletion) throw SmtpRefusedException("greeting", greeting)
        identify()
    }

    /**
     * `EHLO`, falling back to `HELO`.
     *
     * A server that predates ESMTP answers 500 or 502; that is not a failure, only an older
     * server, and `HELO` announces no extensions at all.
     */
    private suspend fun identify() {
        val ehlo = exchange(SmtpCommand.Ehlo(config.clientIdentity), "EHLO", config.timeouts.otherCommands)
        if (ehlo.isPositiveCompletion) {
            announced = Capabilities.parse(ehlo)
            return
        }

        val helo = exchange(SmtpCommand.Helo(config.clientIdentity), "HELO", config.timeouts.otherCommands)
        if (!helo.isPositiveCompletion) throw SmtpRefusedException("HELO", helo)
        announced = Capabilities.parse(helo)
    }

    private suspend fun exchange(
        command: SmtpCommand,
        name: String,
        limit: Duration,
    ): SmtpReply {
        write(command.encode(), "sending $name", limit)
        return readReply("the reply to $name", limit)
    }

    private suspend fun write(
        data: String,
        what: String,
        limit: Duration,
    ) {
        withLimit(what, limit) { transport.write(data) }
    }

    /**
     * Reads lines until the reader says the reply is complete.
     *
     * The reader is asked, rather than the line inspected here, because a multiline reply ends on
     * a line the session has no business parsing (`docs/rfc/rfc2920.txt:193`).
     */
    private suspend fun readReply(
        what: String,
        limit: Duration,
    ): SmtpReply =
        withLimit(what, limit) {
            var reply: SmtpReply? = null
            while (reply == null) {
                reply = reader.feed(transport.readLine())
            }
            reply
        }

    private companion object {
        /** An empty client-first message is written as `=` — `docs/rfc/rfc4954.txt:735`. */
        const val EMPTY_INITIAL_RESPONSE = "="

        /** A single `*` cancels the exchange — `docs/rfc/rfc4954.txt:737`. */
        const val SASL_CANCEL = "*"

        const val CRLF = "\r\n"
    }

    private suspend fun <T> withLimit(
        what: String,
        limit: Duration,
        block: suspend () -> T,
    ): T =
        try {
            withTimeout(limit) { block() }
        } catch (_: TimeoutCancellationException) {
            // The cause is dropped on purpose: it says nothing beyond "it timed out", and keeping
            // it would drag a coroutines type into what the caller sees.
            throw SmtpTimeoutException(what, limit)
        }
}

/**
 * Opens a session over an already connected [transport]: reads the greeting and identifies the
 * client.
 *
 * Connecting is not this function's business — that is what the transport module is for.
 */
public suspend fun openSmtpSession(
    transport: SmtpTransport,
    config: SmtpClientConfig,
): SmtpSession = SmtpSession(transport, config).apply { handshake() }
