package io.github.youndie.smtp.protocol

/**
 * A client command.
 *
 * [line] is the command without `CRLF`, [encode] is what goes into the socket. The length limit is
 * enforced in [encode] rather than in the constructor: for `AUTH`, an over-long initial response is
 * not an error but a reason to fall back to a separate exchange step (`docs/rfc/rfc4954.txt:208`),
 * and that has to be asked before sending — through [fitsLineLimit].
 */
public sealed class SmtpCommand {
    /** The command without its trailing `CRLF`. */
    public abstract val line: String

    /** Whether the command fits the line limit — `docs/rfc/rfc5321.txt:3498`. */
    public val fitsLineLimit: Boolean
        get() = line.encodeToByteArray().size <= MAX_LINE_OCTETS_WITHOUT_CRLF

    /** The line together with `CRLF` — what goes into the socket. */
    public fun encode(): String {
        line.forEach { character ->
            if (character == '\r' || character == '\n') {
                throw SmtpProtocolException("Line break inside a command: '$line'")
            }
        }

        if (!fitsLineLimit) {
            throw SmtpProtocolException(
                "Command line longer than $MAX_LINE_OCTETS_WITHOUT_CRLF octets without CRLF: " +
                    "${line.encodeToByteArray().size}",
            )
        }

        return line + CRLF
    }

    /** `EHLO` — `docs/rfc/rfc5321.txt:1836`. */
    public data class Ehlo(
        public val clientIdentity: String,
    ) : SmtpCommand() {
        override val line: String get() = "EHLO $clientIdentity"
    }

    /** `HELO` — only as a fallback for servers without ESMTP. */
    public data class Helo(
        public val clientIdentity: String,
    ) : SmtpCommand() {
        override val line: String get() = "HELO $clientIdentity"
    }

    /**
     * `MAIL FROM:` — `docs/rfc/rfc5321.txt:1913`.
     *
     * A `null` [sender] is the null reverse-path `<>` (`docs/rfc/rfc5321.txt:2260`): that is how
     * delivery status notifications are sent, since they must not trigger notifications of their
     * own.
     */
    public data class MailFrom(
        public val sender: Mailbox?,
        public val parameters: List<String> = emptyList(),
    ) : SmtpCommand() {
        override val line: String
            get() = "MAIL FROM:${sender?.path ?: "<>"}".withParameters(parameters)
    }

    /** `RCPT TO:` — `docs/rfc/rfc5321.txt:1913`, one command per recipient. */
    public data class RcptTo(
        public val recipient: Mailbox,
        public val parameters: List<String> = emptyList(),
    ) : SmtpCommand() {
        override val line: String get() = "RCPT TO:${recipient.path}".withParameters(parameters)
    }

    /** `DATA` — `docs/rfc/rfc5321.txt:1992`. */
    public data object Data : SmtpCommand() {
        override val line: String get() = "DATA"
    }

    /** `RSET` — resets the transaction without touching authentication. */
    public data object Rset : SmtpCommand() {
        override val line: String get() = "RSET"
    }

    /** `QUIT`. */
    public data object Quit : SmtpCommand() {
        override val line: String get() = "QUIT"
    }

    /** `STARTTLS` — `docs/rfc/rfc3207.txt`. */
    public data object StartTls : SmtpCommand() {
        override val line: String get() = "STARTTLS"
    }

    /** `NOOP` — under PIPELINING it doubles as a synchronisation point (`docs/rfc/rfc2920.txt:137`). */
    public data class Noop(
        public val text: String? = null,
    ) : SmtpCommand() {
        override val line: String get() = if (text == null) "NOOP" else "NOOP $text"
    }

    /** `AUTH` — `docs/rfc/rfc4954.txt:699`. */
    public data class Auth(
        public val mechanism: String,
        public val initialResponse: String? = null,
    ) : SmtpCommand() {
        override val line: String
            get() = if (initialResponse == null) "AUTH $mechanism" else "AUTH $mechanism $initialResponse"
    }

    public companion object {
        /** `docs/rfc/rfc5321.txt:3498`: 512 octets including `CRLF`. */
        private const val MAX_LINE_OCTETS_WITHOUT_CRLF = 510

        private const val CRLF = "\r\n"

        private fun String.withParameters(parameters: List<String>): String =
            if (parameters.isEmpty()) this else parameters.joinToString(" ", prefix = "$this ")
    }
}
