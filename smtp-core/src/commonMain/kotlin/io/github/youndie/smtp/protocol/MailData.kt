package io.github.youndie.smtp.protocol

/**
 * Preparing a message body for transmission after `DATA`.
 *
 * Only transparency and line limits live here (`docs/rfc/rfc5321.txt:3423`, `:3510`). The content
 * of the message — headers, MIME — is none of this layer's business: those are different
 * specifications, 5321 versus 5322/2045.
 */
public object MailData {
    /** The body terminator — `docs/rfc/rfc5321.txt:3423`. */
    public const val TERMINATOR: String = ".\r\n"

    /**
     * Prepares one body line: doubles a leading period and checks the length limit.
     *
     * Returns the line **without** `CRLF`.
     */
    public fun encodeLine(line: String): String {
        line.forEach { character ->
            if (character == '\r' || character == '\n') {
                // rfc5321.txt:763: CRLF separates protocol lines. A line break hidden inside is
                // either lost transparency or somebody else's command.
                throw SmtpProtocolException("Line break inside a message body line")
            }
        }

        // rfc5321.txt:3512: the limit is stated "not counting the leading dot duplicated for
        // transparency" — so it is measured before doubling, not after.
        val octets = line.encodeToByteArray().size
        if (octets > MAX_LINE_OCTETS_WITHOUT_CRLF) {
            throw SmtpProtocolException(
                "Body line longer than $MAX_LINE_OCTETS_WITHOUT_CRLF octets without CRLF: $octets",
            )
        }

        return if (line.startsWith('.')) ".$line" else line
    }

    /**
     * Assembles the whole body: every line with `CRLF`, then a period on a line of its own.
     *
     * An empty body is just the terminator: a message with no content is legal, whereas a missing
     * terminator hangs the session.
     */
    public fun encode(lines: List<String>): String =
        buildString {
            lines.forEach { line ->
                append(encodeLine(line))
                append(CRLF)
            }
            append(TERMINATOR)
        }

    /** `docs/rfc/rfc5321.txt:3510`: 1000 octets including `CRLF`. */
    private const val MAX_LINE_OCTETS_WITHOUT_CRLF = 998

    private const val CRLF = "\r\n"
}
