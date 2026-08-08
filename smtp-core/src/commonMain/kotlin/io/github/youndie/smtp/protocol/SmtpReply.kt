package io.github.youndie.smtp.protocol

/**
 * A server reply: the code plus the text lines with the code and separator stripped.
 *
 * A single-line reply is one entry in [lines]; a reply with no text is one empty entry
 * (`docs/rfc/rfc5321.txt:2571`).
 */
public data class SmtpReply(
    public val code: SmtpReplyCode,
    public val lines: List<String>,
) {
    /** A leading `2` means Positive Completion — `docs/rfc/rfc5321.txt:2642`. */
    public val isPositiveCompletion: Boolean
        get() = code.severity == SmtpReplySeverity.POSITIVE_COMPLETION

    /**
     * The enhanced status code, when the server sent one (`docs/rfc/rfc2034.txt:100`).
     *
     * `null` means "the server did not send one", not "everything is fine".
     */
    public val enhancedStatus: EnhancedStatusCode?
        get() {
            val firstWord = lines.first().substringBefore(' ')
            val parsed = EnhancedStatusCode.parseOrNull(firstWord) ?: return null

            // rfc2034.txt:105: the enhanced class must agree with the first digit of the reply.
            // A disagreeing value is just text; passing it off as a code is worse than losing it.
            return parsed.takeIf { it.statusClass == code.value / 100 }
        }

    public companion object {
        /**
         * Parses a whole reply: lines without the trailing `CRLF`, the last one being final —
         * a space after the code rather than a hyphen (`docs/rfc/rfc5321.txt:2597`).
         */
        public fun parse(rawLines: List<String>): SmtpReply {
            if (rawLines.isEmpty()) {
                throw SmtpProtocolException("Reply contains no lines")
            }

            val reader = SmtpReplyReader()
            var parsed: SmtpReply? = null

            for (line in rawLines) {
                if (parsed != null) {
                    throw SmtpProtocolException("Another line follows the final line of a reply: '$line'")
                }
                parsed = reader.feed(line)
            }

            return parsed
                ?: throw SmtpProtocolException("Truncated reply: the last line is marked as a continuation")
        }
    }
}

/**
 * A single reply line: the code, whether it is the final line, and the text without the code and
 * separator.
 *
 * A separate type exists for streaming (M-11): there lines arrive one at a time, and "the reply is
 * over" is decided by [isFinal].
 */
internal data class SmtpReplyLine(
    val code: SmtpReplyCode,
    val isFinal: Boolean,
    val text: String,
) {
    companion object {
        private const val CODE_LENGTH = 3

        /** A reply line is capped at 512 octets **including CRLF** (`docs/rfc/rfc5321.txt:3504`). */
        private const val MAX_OCTETS_WITHOUT_CRLF = 510

        fun parse(line: String): SmtpReplyLine {
            // The limit is in octets, not characters: with SMTPUTF8 (rfc6531.txt) reply text can
            // be non-ASCII, and a length counted in characters understates it two- or threefold.
            val octets = line.encodeToByteArray().size
            if (octets > MAX_OCTETS_WITHOUT_CRLF) {
                throw SmtpProtocolException(
                    "Reply line longer than $MAX_OCTETS_WITHOUT_CRLF octets without CRLF: $octets",
                )
            }

            if (line.length < CODE_LENGTH) {
                throw SmtpProtocolException("Reply line shorter than a three-digit code: '$line'")
            }

            val digits = line.substring(0, CODE_LENGTH)
            if (!digits.all { it in '0'..'9' }) {
                throw SmtpProtocolException("Reply line does not start with a three-digit code: '$line'")
            }

            val code = SmtpReplyCode(digits.toInt())

            // rfc5321.txt:2571: a bare code, with or without a trailing space, is legal.
            if (line.length == CODE_LENGTH) {
                return SmtpReplyLine(code, isFinal = true, text = "")
            }

            return when (val separator = line[CODE_LENGTH]) {
                '-' -> SmtpReplyLine(code, isFinal = false, text = line.substring(CODE_LENGTH + 1))

                ' ' -> SmtpReplyLine(code, isFinal = true, text = line.substring(CODE_LENGTH + 1))

                else -> throw SmtpProtocolException(
                    "Expected a space or a hyphen after the reply code, got '$separator': '$line'",
                )
            }
        }
    }
}
