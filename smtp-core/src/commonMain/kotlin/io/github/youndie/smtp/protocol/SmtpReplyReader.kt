package io.github.youndie.smtp.protocol

/**
 * Собирает ответы сервера из строк, приходящих по одной.
 *
 * Читателю не сообщают, на какую команду ждут ответ, и это не упущение: при PIPELINING
 * сопоставление идёт счётом ответов, а по коду или тексту — прямо запрещено
 * (`docs/rfc/rfc2920.txt:177`).
 *
 * Не потокобезопасен: у одного соединения один читатель, живущий в его корутине.
 */
public class SmtpReplyReader {
    private val texts = mutableListOf<String>()
    private var code: SmtpReplyCode? = null

    /** Ни одной строки незавершённого ответа не накоплено. */
    public val isIdle: Boolean
        get() = code == null

    /**
     * Принимает очередную строку без завершающего `CRLF`.
     *
     * Возвращает ответ, если строка была финальной, и `null`, если ответ продолжается.
     */
    public fun feed(line: String): SmtpReply? {
        val parsed = SmtpReplyLine.parse(line)

        val started = code
        if (started != null && started != parsed.code) {
            // rfc5321.txt:2771: код на всех строках одного ответа обязан совпадать.
            throw SmtpProtocolException("Код в строках одного ответа различается: $started и ${parsed.code}")
        }

        code = parsed.code
        texts += parsed.text

        if (!parsed.isFinal) {
            return null
        }

        val reply = SmtpReply(parsed.code, texts.toList())
        texts.clear()
        code = null
        return reply
    }
}
