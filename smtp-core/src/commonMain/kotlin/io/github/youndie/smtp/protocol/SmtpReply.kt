package io.github.youndie.smtp.protocol

/**
 * Ответ сервера: код и текст без кода.
 *
 * Многострочность и потоковый разбор появятся на M-10/M-11 — сейчас поддержан только
 * однострочный ответ (`docs/rfc/rfc5321.txt:2642`).
 */
public data class SmtpReply(
    public val code: Int,
    public val lines: List<String>,
) {
    /** Первая цифра `2` — Positive Completion, `docs/rfc/rfc5321.txt:2642`. */
    public val isPositiveCompletion: Boolean get() = code / 100 == 2

    public companion object {
        public fun parse(rawLines: List<String>): SmtpReply = TODO("M-07")
    }
}
