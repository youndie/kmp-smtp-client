package io.github.youndie.smtp.protocol

/**
 * Расширения, объявленные сервером в ответе на `EHLO` — `docs/rfc/rfc5321.txt:1841`.
 *
 * Значение живёт внутри одной фазы сессии: после `STARTTLS` (`docs/rfc/rfc3207.txt:210`) и после
 * `AUTH` с security layer (`docs/rfc/rfc4954.txt:297`) прежний список недействителен и его
 * обязаны заменить новым. Привязку к фазе делает M-21; здесь только разбор.
 */
public class Capabilities internal constructor(
    /** Первая строка ответа: домен сервера и приветствие. Расширением не является. */
    public val greeting: String,
    private val entries: Map<String, List<String>>,
) {
    /** Объявленные ключевые слова в верхнем регистре. */
    public val keywords: Set<String> get() = entries.keys

    /** Ключевые слова регистронезависимы — `docs/rfc/rfc5321.txt:1869`. */
    public operator fun contains(keyword: String): Boolean = keyword.uppercase() in entries

    /** Параметры ключевого слова; пустой список — и когда параметров нет, и когда нет слова. */
    public fun parametersOf(keyword: String): List<String> = entries[keyword.uppercase()].orEmpty()

    public val supportsPipelining: Boolean get() = PIPELINING in this
    public val supportsStartTls: Boolean get() = STARTTLS in this
    public val supports8BitMime: Boolean get() = EIGHT_BIT_MIME in this
    public val supportsSmtpUtf8: Boolean get() = SMTPUTF8 in this
    public val supportsEnhancedStatusCodes: Boolean get() = ENHANCED_STATUS_CODES in this
    public val supportsChunking: Boolean get() = CHUNKING in this
    public val supportsDsn: Boolean get() = DSN in this

    /**
     * Предельный размер письма из `SIZE` — `docs/rfc/rfc1870.txt:70`.
     *
     * `null` означает «предел неизвестен»: сервер либо не назвал число, либо назвал `0`, что по
     * `docs/rfc/rfc1870.txt:79` значит «фиксированного предела нет». Это **не** ноль байт.
     */
    public val maxMessageSize: Long?
        get() = parametersOf(SIZE).firstOrNull()?.toLongOrNull()?.takeIf { it > 0 }

    /** Механизмы SASL из `AUTH` в верхнем регистре — `docs/rfc/rfc4954.txt`. */
    public val authMechanisms: Set<String>
        get() = parametersOf(AUTH).mapTo(LinkedHashSet()) { it.uppercase() }

    public companion object {
        private const val PIPELINING = "PIPELINING"
        private const val STARTTLS = "STARTTLS"
        private const val EIGHT_BIT_MIME = "8BITMIME"
        private const val SMTPUTF8 = "SMTPUTF8"
        private const val ENHANCED_STATUS_CODES = "ENHANCEDSTATUSCODES"
        private const val CHUNKING = "CHUNKING"
        private const val DSN = "DSN"
        private const val SIZE = "SIZE"
        private const val AUTH = "AUTH"

        public fun parse(reply: SmtpReply): Capabilities {
            if (reply.code != SmtpReplyCode(EHLO_OK)) {
                // rfc5321.txt:1841: расширения объявляются только положительным ответом 250.
                throw SmtpProtocolException("Ответ на EHLO не 250, расширений в нём нет: ${reply.code}")
            }

            val entries = LinkedHashMap<String, List<String>>()
            // Первая строка — Domain [SP ehlo-greet], а не ehlo-line (rfc5321.txt:1841).
            for (line in reply.lines.drop(1)) {
                val tokens = line.split(' ').filter { it.isNotEmpty() }
                val keyword = tokens.firstOrNull() ?: continue
                // rfc5321.txt:1869: ключевые слова обрабатываются регистронезависимо.
                entries[keyword.uppercase()] = tokens.drop(1)
            }

            return Capabilities(reply.lines.first(), entries)
        }

        private const val EHLO_OK = 250
    }
}
