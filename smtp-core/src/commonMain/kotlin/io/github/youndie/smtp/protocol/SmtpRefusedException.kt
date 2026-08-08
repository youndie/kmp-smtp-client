package io.github.youndie.smtp.protocol

/**
 * The server refused a command.
 *
 * Not a protocol violation — the server behaved correctly, it simply said no. The distinction the
 * caller actually needs is [isTransient] versus [isPermanent] (`docs/rfc/rfc5321.txt:2642`):
 * retrying a 4xx makes sense, retrying a 5xx does not.
 *
 * A refusal of a single recipient is deliberately **not** an exception — see `DeliveryResult` in
 * `:smtp-client`.
 */
public class SmtpRefusedException(
    public val command: String,
    public val reply: SmtpReply,
) : RuntimeException("$command refused: ${reply.code} ${reply.lines.firstOrNull().orEmpty()}") {
    /** 4xx — the same request may succeed later. */
    public val isTransient: Boolean get() = reply.code.severity == SmtpReplySeverity.TRANSIENT_NEGATIVE

    /** 5xx — retrying the same request is pointless. */
    public val isPermanent: Boolean get() = reply.code.severity == SmtpReplySeverity.PERMANENT_NEGATIVE
}
