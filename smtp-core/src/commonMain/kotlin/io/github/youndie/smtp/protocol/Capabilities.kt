package io.github.youndie.smtp.protocol

/**
 * The extensions a server announced in its `EHLO` reply — `docs/rfc/rfc5321.txt:1841`.
 *
 * The value belongs to one phase of a session: after `STARTTLS` (`docs/rfc/rfc3207.txt:210`) and
 * after `AUTH` with a security layer (`docs/rfc/rfc4954.txt:297`) the previous list is void and
 * must be replaced. Binding it to a phase is M-21; this is parsing only.
 */
public class Capabilities internal constructor(
    /** The first reply line: the server domain and its greeting. Not an extension. */
    public val greeting: String,
    private val entries: Map<String, List<String>>,
) {
    /** The announced keywords, upper-cased. */
    public val keywords: Set<String> get() = entries.keys

    /** Keywords are case-insensitive — `docs/rfc/rfc5321.txt:1869`. */
    public operator fun contains(keyword: String): Boolean = keyword.uppercase() in entries

    /** Parameters of a keyword; empty both when there are none and when the keyword is absent. */
    public fun parametersOf(keyword: String): List<String> = entries[keyword.uppercase()].orEmpty()

    public val supportsPipelining: Boolean get() = PIPELINING in this
    public val supportsStartTls: Boolean get() = STARTTLS in this
    public val supports8BitMime: Boolean get() = EIGHT_BIT_MIME in this
    public val supportsSmtpUtf8: Boolean get() = SMTPUTF8 in this
    public val supportsEnhancedStatusCodes: Boolean get() = ENHANCED_STATUS_CODES in this
    public val supportsChunking: Boolean get() = CHUNKING in this
    public val supportsDsn: Boolean get() = DSN in this

    /**
     * The maximum message size from `SIZE` — `docs/rfc/rfc1870.txt:70`.
     *
     * `null` means "the limit is unknown": the server either named no number or named `0`, which
     * per `docs/rfc/rfc1870.txt:79` means "no fixed maximum is in force". It does **not** mean
     * zero bytes.
     */
    public val maxMessageSize: Long?
        get() = parametersOf(SIZE).firstOrNull()?.toLongOrNull()?.takeIf { it > 0 }

    /** SASL mechanisms from `AUTH`, upper-cased — `docs/rfc/rfc4954.txt`. */
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
        private const val EHLO_OK = 250

        public fun parse(reply: SmtpReply): Capabilities {
            if (reply.code != SmtpReplyCode(EHLO_OK)) {
                // rfc5321.txt:1841: extensions are announced only by a positive 250 reply.
                throw SmtpProtocolException("EHLO reply is not 250, so it announces no extensions: ${reply.code}")
            }

            val entries = LinkedHashMap<String, List<String>>()
            // The first line is Domain [SP ehlo-greet], not an ehlo-line (rfc5321.txt:1841).
            for (line in reply.lines.drop(1)) {
                val tokens = line.split(' ').filter { it.isNotEmpty() }
                val keyword = tokens.firstOrNull() ?: continue
                // rfc5321.txt:1869: keywords are processed case-insensitively.
                entries[keyword.uppercase()] = tokens.drop(1)
            }

            return Capabilities(reply.lines.first(), entries)
        }
    }
}
