package io.github.youndie.smtp.protocol

/**
 * Assembles server replies from lines arriving one at a time.
 *
 * The reader is never told which command a reply belongs to, and that is not an omission: under
 * PIPELINING replies are matched to commands by counting, and matching on the reply code or text
 * is expressly forbidden (`docs/rfc/rfc2920.txt:177`).
 *
 * Not thread-safe: one connection owns one reader, living in that connection's coroutine.
 */
public class SmtpReplyReader {
    private val texts = mutableListOf<String>()
    private var code: SmtpReplyCode? = null

    /** No lines of an unfinished reply are buffered. */
    public val isIdle: Boolean
        get() = code == null

    /**
     * Accepts the next line, without its trailing `CRLF`.
     *
     * Returns the reply if the line was final, and `null` while the reply continues.
     */
    public fun feed(line: String): SmtpReply? {
        val parsed = SmtpReplyLine.parse(line)

        val started = code
        if (started != null && started != parsed.code) {
            // rfc5321.txt:2771: the code must be the same on every line of one reply.
            throw SmtpProtocolException("Reply lines carry different codes: $started and ${parsed.code}")
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
