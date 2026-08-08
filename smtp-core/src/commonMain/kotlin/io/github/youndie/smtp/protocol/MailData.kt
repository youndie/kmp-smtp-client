package io.github.youndie.smtp.protocol

/**
 * Подготовка тела письма к передаче после `DATA`.
 *
 * Здесь только прозрачность и пределы строк (`docs/rfc/rfc5321.txt:3423`, `:3510`) — содержимое
 * письма (заголовки, MIME) этот слой не касается вовсе: это разные спецификации, 5321 против
 * 5322/2045.
 */
public object MailData {
    /** Терминатор тела — `docs/rfc/rfc5321.txt:3423`. */
    public const val TERMINATOR: String = ".\r\n"

    /**
     * Готовит одну строку тела: удваивает ведущую точку и проверяет предел длины.
     *
     * Возвращает строку **без** `CRLF`.
     */
    public fun encodeLine(line: String): String {
        line.forEach { character ->
            if (character == '\r' || character == '\n') {
                // rfc5321.txt:763: строки протокола разделяет CRLF. Спрятанный внутри перевод
                // строки — это либо потерянная прозрачность, либо чужая команда.
                throw SmtpProtocolException("Перевод строки внутри строки тела письма")
            }
        }

        // rfc5321.txt:3512: предел задан "not counting the leading dot duplicated for
        // transparency" — то есть считается до удвоения, а не после.
        val octets = line.encodeToByteArray().size
        if (octets > MAX_LINE_OCTETS_WITHOUT_CRLF) {
            throw SmtpProtocolException(
                "Строка тела длиннее $MAX_LINE_OCTETS_WITHOUT_CRLF октетов без CRLF: $octets",
            )
        }

        return if (line.startsWith('.')) ".$line" else line
    }

    /**
     * Собирает тело целиком: каждая строка с `CRLF`, в конце — точка на отдельной строке.
     *
     * Пустое тело — один терминатор: письмо без содержимого законно, а вот отсутствие
     * терминатора подвесит сессию.
     */
    public fun encode(lines: List<String>): String =
        buildString {
            lines.forEach { line ->
                append(encodeLine(line))
                append(CRLF)
            }
            append(TERMINATOR)
        }

    /** `docs/rfc/rfc5321.txt:3510`: 1000 октетов вместе с `CRLF`. */
    private const val MAX_LINE_OCTETS_WITHOUT_CRLF = 998

    private const val CRLF = "\r\n"
}
