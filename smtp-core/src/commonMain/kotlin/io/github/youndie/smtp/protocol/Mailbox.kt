package io.github.youndie.smtp.protocol

/**
 * An envelope address: a local part and a domain — `docs/rfc/rfc5321.txt:2314`.
 *
 * This is the **envelope** address, not an address from the message headers: the two follow
 * different grammars (5321 versus 5322) and are not required to match.
 */
public class Mailbox private constructor(
    public val localPart: String,
    public val domain: String,
) {
    /** `localPart@domain` without the angle brackets. */
    public val address: String get() = "$localPart@$domain"

    /** The path for `MAIL FROM:` and `RCPT TO:` — `docs/rfc/rfc5321.txt:2264`. */
    public val path: String get() = "<$address>"

    override fun toString(): String = address

    override fun equals(other: Any?): Boolean =
        other is Mailbox && other.localPart == localPart && other.domain == domain

    override fun hashCode(): Int = 31 * localPart.hashCode() + domain.hashCode()

    public companion object {
        private const val MAX_LOCAL_PART_OCTETS = 64
        private const val MAX_DOMAIN_OCTETS = 255
        private const val MAX_PATH_OCTETS = 256

        /** The angle brackets around the address; they count towards the path limit. */
        private const val PATH_BRACKETS_OCTETS = 2

        public fun parse(text: String): Mailbox {
            // Checked before anything else: with a CRLF inside, an address stops being an address
            // and becomes a way to append a command of one's choosing (rfc5321.txt:763).
            text.forEach { character ->
                if (character == '\r' || character == '\n') {
                    throw SmtpProtocolException("Line break inside an address: '$text'")
                }
                if (character == '<' || character == '>') {
                    throw SmtpProtocolException("Angle bracket inside an address: '$text'")
                }
            }

            // The local part can be a quoted string and may contain '@' (rfc5321.txt:2322), so the
            // separator is the last one, not the first.
            val separator = text.lastIndexOf('@')
            if (separator <= 0 || separator == text.lastIndex) {
                throw SmtpProtocolException("Address is not of the form localPart@domain: '$text'")
            }

            val localPart = text.substring(0, separator)
            val domain = text.substring(separator + 1)

            checkOctets(localPart, MAX_LOCAL_PART_OCTETS, "Address local part")
            checkOctets(domain, MAX_DOMAIN_OCTETS, "Address domain")

            // rfc5321.txt:3493: the path limit is stated "including the punctuation and element
            // separators" — that is, together with the '@' and both brackets.
            checkOctets(text, MAX_PATH_OCTETS - PATH_BRACKETS_OCTETS, "Path")

            return Mailbox(localPart, domain)
        }

        /**
         * The limits are given in octets (`docs/rfc/rfc5321.txt:3484`), which with SMTPUTF8 is not
         * the same thing as characters.
         */
        private fun checkOctets(
            value: String,
            limit: Int,
            what: String,
        ) {
            val octets = value.encodeToByteArray().size
            if (octets > limit) {
                throw SmtpProtocolException("$what longer than $limit octets: $octets")
            }
        }
    }
}
