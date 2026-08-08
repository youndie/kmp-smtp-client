package io.github.youndie.smtp.protocol

/**
 * Адрес конверта: локальная часть и домен — `docs/rfc/rfc5321.txt:2314`.
 *
 * Это **адрес конверта**, а не адрес из заголовков письма: у них разные грамматики (5321 против
 * 5322) и совпадать они не обязаны.
 */
public class Mailbox private constructor(
    public val localPart: String,
    public val domain: String,
) {
    /** `localPart@domain` без угловых скобок. */
    public val address: String get() = "$localPart@$domain"

    /** Путь для `MAIL FROM:` и `RCPT TO:` — `docs/rfc/rfc5321.txt:2264`. */
    public val path: String get() = "<$address>"

    override fun toString(): String = address

    override fun equals(other: Any?): Boolean =
        other is Mailbox && other.localPart == localPart && other.domain == domain

    override fun hashCode(): Int = 31 * localPart.hashCode() + domain.hashCode()

    public companion object {
        private const val MAX_LOCAL_PART_OCTETS = 64
        private const val MAX_DOMAIN_OCTETS = 255
        private const val MAX_PATH_OCTETS = 256

        /** Длина угловых скобок вокруг адреса — они входят в предел пути. */
        private const val PATH_BRACKETS_OCTETS = 2

        public fun parse(text: String): Mailbox {
            // Проверяется раньше всего: с CRLF внутри адрес перестаёт быть адресом и становится
            // способом дописать серверу команду (rfc5321.txt:763).
            text.forEach { character ->
                if (character == '\r' || character == '\n') {
                    throw SmtpProtocolException("Перевод строки внутри адреса: '$text'")
                }
                if (character == '<' || character == '>') {
                    throw SmtpProtocolException("Угловая скобка внутри адреса: '$text'")
                }
            }

            // Локальная часть бывает quoted-string и может содержать '@' (rfc5321.txt:2322),
            // поэтому разделитель — последний, а не первый.
            val separator = text.lastIndexOf('@')
            if (separator <= 0 || separator == text.lastIndex) {
                throw SmtpProtocolException("Адрес не имеет вида localPart@domain: '$text'")
            }

            val localPart = text.substring(0, separator)
            val domain = text.substring(separator + 1)

            checkOctets(localPart, MAX_LOCAL_PART_OCTETS, "Локальная часть адреса")
            checkOctets(domain, MAX_DOMAIN_OCTETS, "Домен адреса")

            // rfc5321.txt:3493: предел пути задан "including the punctuation and element
            // separators" — то есть вместе с '@' и обеими скобками.
            checkOctets(text, MAX_PATH_OCTETS - PATH_BRACKETS_OCTETS, "Путь")

            return Mailbox(localPart, domain)
        }

        /** Пределы заданы в октетах (`docs/rfc/rfc5321.txt:3484`), а с SMTPUTF8 это не то же самое, что символы. */
        private fun checkOctets(
            value: String,
            limit: Int,
            what: String,
        ) {
            val octets = value.encodeToByteArray().size
            if (octets > limit) {
                throw SmtpProtocolException("$what длиннее $limit октетов: $octets")
            }
        }
    }
}
