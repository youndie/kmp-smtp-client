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
        private const val CODE_LENGTH = 3

        /**
         * Разбирает ответ сервера, состоящий из одной строки, без завершающего `CRLF`.
         *
         * Код — ровно три цифры (`docs/rfc/rfc5321.txt:2642`); за ними идёт либо ничего,
         * либо пробел и текст.
         */
        public fun parse(rawLines: List<String>): SmtpReply {
            val line =
                rawLines.singleOrNull()
                    ?: error("Многострочный ответ будет поддержан на M-10; получено строк: ${rawLines.size}")

            val code =
                line.take(CODE_LENGTH).takeIf { it.length == CODE_LENGTH && it.all(Char::isDigit) }?.toInt()
                    ?: error("Ответ не начинается с трёхзначного кода: '$line'")

            return SmtpReply(code, listOf(line.drop(CODE_LENGTH).removePrefix(" ")))
        }
    }
}
