package io.github.youndie.smtp.protocol

/**
 * Ответ сервера: код и текстовые строки без кода и разделителя.
 *
 * Однострочный ответ — одна строка в [lines]; ответ без текста — одна пустая строка
 * (`docs/rfc/rfc5321.txt:2571`).
 */
public data class SmtpReply(
    public val code: SmtpReplyCode,
    public val lines: List<String>,
) {
    /** Первая цифра `2` — Positive Completion, `docs/rfc/rfc5321.txt:2642`. */
    public val isPositiveCompletion: Boolean
        get() = code.severity == SmtpReplySeverity.POSITIVE_COMPLETION

    public companion object {
        /**
         * Разбирает ответ целиком: строки без завершающего `CRLF`, последняя обязана быть
         * финальной — с пробелом после кода, а не с дефисом (`docs/rfc/rfc5321.txt:2597`).
         */
        public fun parse(rawLines: List<String>): SmtpReply = TODO("M-10")
    }
}
