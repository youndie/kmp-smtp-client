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

    /**
     * Расширенный код состояния, если сервер его прислал (`docs/rfc/rfc2034.txt:100`).
     *
     * `null` означает «сервер не прислал» — а не «всё хорошо».
     */
    public val enhancedStatus: EnhancedStatusCode?
        get() {
            val firstWord = lines.first().substringBefore(' ')
            val parsed = EnhancedStatusCode.parseOrNull(firstWord) ?: return null

            // rfc2034.txt:105: класс расширенного кода обязан совпадать с первой цифрой ответа.
            // Несогласованное значение — просто текст; выдать его за код хуже, чем потерять.
            return parsed.takeIf { it.statusClass == code.value / 100 }
        }

    public companion object {
        /**
         * Разбирает ответ целиком: строки без завершающего `CRLF`, последняя обязана быть
         * финальной — с пробелом после кода, а не с дефисом (`docs/rfc/rfc5321.txt:2597`).
         */
        public fun parse(rawLines: List<String>): SmtpReply {
            if (rawLines.isEmpty()) {
                throw SmtpProtocolException("Ответ не содержит ни одной строки")
            }

            val reader = SmtpReplyReader()
            var parsed: SmtpReply? = null

            for (line in rawLines) {
                if (parsed != null) {
                    throw SmtpProtocolException("После финальной строки ответа идёт ещё одна: '$line'")
                }
                parsed = reader.feed(line)
            }

            return parsed
                ?: throw SmtpProtocolException("Ответ оборван: последняя строка помечена как продолжение")
        }
    }
}

/**
 * Одна строка ответа: код, признак финальной строки и текст без кода и разделителя.
 *
 * Отдельный тип нужен потоковому чтению (M-11): там строки приходят по одной, и решение
 * «ответ закончился» принимается по [isFinal].
 */
internal data class SmtpReplyLine(
    val code: SmtpReplyCode,
    val isFinal: Boolean,
    val text: String,
) {
    companion object {
        private const val CODE_LENGTH = 3

        /** Предел строки ответа — 512 октетов **вместе с CRLF** (`docs/rfc/rfc5321.txt:3504`). */
        private const val MAX_OCTETS_WITHOUT_CRLF = 510

        fun parse(line: String): SmtpReplyLine {
            // Предел задан в октетах, а не в символах: с SMTPUTF8 (rfc6531.txt) текст ответа
            // бывает не-ASCII, и посчитанная по символам длина занижена вдвое-втрое.
            val octets = line.encodeToByteArray().size
            if (octets > MAX_OCTETS_WITHOUT_CRLF) {
                throw SmtpProtocolException(
                    "Строка ответа длиннее $MAX_OCTETS_WITHOUT_CRLF октетов без CRLF: $octets",
                )
            }

            if (line.length < CODE_LENGTH) {
                throw SmtpProtocolException("Строка ответа короче трёхзначного кода: '$line'")
            }

            val digits = line.substring(0, CODE_LENGTH)
            if (!digits.all { it in '0'..'9' }) {
                throw SmtpProtocolException("Строка ответа не начинается с трёхзначного кода: '$line'")
            }

            val code = SmtpReplyCode(digits.toInt())

            // rfc5321.txt:2571: код без текста — с пробелом или совсем без него — законен.
            if (line.length == CODE_LENGTH) {
                return SmtpReplyLine(code, isFinal = true, text = "")
            }

            return when (val separator = line[CODE_LENGTH]) {
                '-' -> SmtpReplyLine(code, isFinal = false, text = line.substring(CODE_LENGTH + 1))

                ' ' -> SmtpReplyLine(code, isFinal = true, text = line.substring(CODE_LENGTH + 1))

                else -> throw SmtpProtocolException(
                    "После кода ответа ожидались пробел или дефис, а не '$separator': '$line'",
                )
            }
        }
    }
}
