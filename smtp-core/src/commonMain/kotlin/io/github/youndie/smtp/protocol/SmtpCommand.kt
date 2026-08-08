package io.github.youndie.smtp.protocol

/**
 * Команда клиента.
 *
 * [line] — строка без `CRLF`, [encode] — то, что уходит в сокет. Предел длины проверяется в
 * [encode], а не в конструкторе: у `AUTH` слишком длинный начальный ответ — не ошибка, а повод
 * перейти на отдельный шаг обмена (`docs/rfc/rfc4954.txt:208`), и спросить об этом нужно до
 * отправки, через [fitsLineLimit].
 */
public sealed class SmtpCommand {
    /** Команда без завершающего `CRLF`. */
    public abstract val line: String

    /** Помещается ли команда в предел строки — `docs/rfc/rfc5321.txt:3498`. */
    public val fitsLineLimit: Boolean
        get() = line.encodeToByteArray().size <= MAX_LINE_OCTETS_WITHOUT_CRLF

    /** Строка вместе с `CRLF` — то, что уходит в сокет. */
    public fun encode(): String {
        line.forEach { character ->
            if (character == '\r' || character == '\n') {
                throw SmtpProtocolException("Перевод строки внутри команды: '$line'")
            }
        }

        if (!fitsLineLimit) {
            throw SmtpProtocolException(
                "Командная строка длиннее $MAX_LINE_OCTETS_WITHOUT_CRLF октетов без CRLF: " +
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

    /** `HELO` — только как откат для серверов без ESMTP. */
    public data class Helo(
        public val clientIdentity: String,
    ) : SmtpCommand() {
        override val line: String get() = "HELO $clientIdentity"
    }

    /**
     * `MAIL FROM:` — `docs/rfc/rfc5321.txt:1913`.
     *
     * [sender] `null` — пустой обратный путь `<>` (`docs/rfc/rfc5321.txt:2260`): так отправляются
     * отчёты о недоставке, которые сами не должны порождать отчётов.
     */
    public data class MailFrom(
        public val sender: Mailbox?,
        public val parameters: List<String> = emptyList(),
    ) : SmtpCommand() {
        override val line: String
            get() = "MAIL FROM:${sender?.path ?: "<>"}".withParameters(parameters)
    }

    /** `RCPT TO:` — `docs/rfc/rfc5321.txt:1913`, по одной команде на получателя. */
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

    /** `RSET` — сбрасывает транзакцию, не трогая аутентификацию. */
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

    /** `NOOP` — при PIPELINING служит точкой синхронизации (`docs/rfc/rfc2920.txt:137`). */
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
        /** `docs/rfc/rfc5321.txt:3498`: 512 октетов вместе с `CRLF`. */
        private const val MAX_LINE_OCTETS_WITHOUT_CRLF = 510

        private const val CRLF = "\r\n"

        private fun String.withParameters(parameters: List<String>): String =
            if (parameters.isEmpty()) this else parameters.joinToString(" ", prefix = "$this ")
    }
}
