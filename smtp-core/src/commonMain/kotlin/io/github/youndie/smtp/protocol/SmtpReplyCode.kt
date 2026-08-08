package io.github.youndie.smtp.protocol

import kotlin.jvm.JvmInline

/** The outcome carried by the first digit of a reply code — `docs/rfc/rfc5321.txt:2642`. */
public enum class SmtpReplySeverity {
    /** 2xx — the command succeeded. */
    POSITIVE_COMPLETION,

    /** 3xx — accepted, the server waits for more (`354` before the message body). */
    POSITIVE_INTERMEDIATE,

    /** 4xx — transient refusal: the same request may succeed later. */
    TRANSIENT_NEGATIVE,

    /** 5xx — permanent refusal: retrying the same request is pointless. */
    PERMANENT_NEGATIVE,
}

/**
 * A three-digit SMTP reply code.
 *
 * ABNF — `docs/rfc/rfc5321.txt:2600`: `Reply-code = %x32-35 %x30-35 %x30-39`. Only the first digit
 * is validated: `docs/rfc/rfc5321.txt:3043` requires a client to handle unknown codes "by
 * interpreting the first digit only", so rejecting a second digit outside the ABNF would mean
 * breaking on someone else's sloppiness exactly where the protocol asks for tolerance.
 */
@JvmInline
public value class SmtpReplyCode(
    public val value: Int,
) {
    init {
        if (value !in MIN_VALUE..MAX_VALUE) {
            throw SmtpProtocolException("Reply code outside the 2xx..5xx range: $value")
        }
    }

    public val severity: SmtpReplySeverity
        get() =
            when (value / 100) {
                2 -> SmtpReplySeverity.POSITIVE_COMPLETION
                3 -> SmtpReplySeverity.POSITIVE_INTERMEDIATE
                4 -> SmtpReplySeverity.TRANSIENT_NEGATIVE
                else -> SmtpReplySeverity.PERMANENT_NEGATIVE
            }

    override fun toString(): String = value.toString()

    public companion object {
        private const val MIN_VALUE = 200
        private const val MAX_VALUE = 599
    }
}
