package io.github.youndie.smtp.protocol

/**
 * Адрес конверта: локальная часть и домен — `docs/rfc/rfc5321.txt:2314`.
 *
 * Это **адрес конверта**, а не адрес из заголовков письма: у них разные грамматики (5321 против
 * 5322) и они не обязаны совпадать.
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
        public fun parse(text: String): Mailbox = TODO("M-16")
    }
}
