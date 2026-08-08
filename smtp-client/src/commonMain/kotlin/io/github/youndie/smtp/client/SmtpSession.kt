package io.github.youndie.smtp.client

import io.github.youndie.smtp.protocol.Capabilities
import io.github.youndie.smtp.protocol.Mailbox
import io.github.youndie.smtp.protocol.SmtpReply
import io.github.youndie.smtp.transport.SmtpTransport
import kotlin.time.Duration

/** What is being sent, as the envelope sees it. */
public data class Envelope(
    public val sender: Mailbox?,
    public val recipients: List<Mailbox>,
)

/** A recipient the server would not take, with the reply that says why. */
public data class RejectedRecipient(
    public val mailbox: Mailbox,
    public val reply: SmtpReply,
)

/** The outcome of one transaction. */
public data class DeliveryResult(
    public val accepted: List<Mailbox>,
    public val rejected: List<RejectedRecipient>,
    public val acceptance: SmtpReply?,
)

/** How long the client waits, per `docs/rfc/rfc5321.txt:3610`. */
public data class SmtpTimeouts(
    public val greeting: Duration = Duration.ZERO,
    public val mailCommand: Duration = Duration.ZERO,
    public val recipientCommand: Duration = Duration.ZERO,
    public val dataInitiation: Duration = Duration.ZERO,
    public val dataBlock: Duration = Duration.ZERO,
    public val dataTermination: Duration = Duration.ZERO,
)

public data class SmtpClientConfig(
    public val clientIdentity: String,
    public val timeouts: SmtpTimeouts = SmtpTimeouts(),
)

/** The server stayed silent past the limit. */
public class SmtpTimeoutException(
    public val what: String,
    public val limit: Duration,
) : RuntimeException("Timed out after $limit waiting for $what")

public class SmtpSession internal constructor() {
    public val capabilities: Capabilities get() = TODO("M-20")

    public suspend fun send(
        envelope: Envelope,
        body: List<String>,
    ): DeliveryResult = TODO("M-25")

    public suspend fun reset(): Unit = TODO("M-27")

    public suspend fun quit(): Unit = TODO("M-20")

    public suspend fun startTls(upgrade: suspend () -> Unit): Unit = TODO("M-21")
}

public suspend fun openSmtpSession(
    transport: SmtpTransport,
    config: SmtpClientConfig,
): SmtpSession = TODO("M-20")
