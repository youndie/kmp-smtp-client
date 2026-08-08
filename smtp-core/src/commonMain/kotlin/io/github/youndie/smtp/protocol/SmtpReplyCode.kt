package io.github.youndie.smtp.protocol

import kotlin.jvm.JvmInline

/** Исход, заданный первой цифрой кода ответа — `docs/rfc/rfc5321.txt:2642`. */
public enum class SmtpReplySeverity {
    /** 2xx — команда выполнена. */
    POSITIVE_COMPLETION,

    /** 3xx — команда принята, сервер ждёт продолжения (`354` перед телом письма). */
    POSITIVE_INTERMEDIATE,

    /** 4xx — временный отказ: то же самое имеет смысл повторить позже. */
    TRANSIENT_NEGATIVE,

    /** 5xx — постоянный отказ: повтор в том же виде бессмыслен. */
    PERMANENT_NEGATIVE,
}

/**
 * Трёхзначный код ответа SMTP.
 *
 * ABNF — `docs/rfc/rfc5321.txt:2600`: `Reply-code = %x32-35 %x30-35 %x30-39`. Проверяется только
 * первая цифра: `docs/rfc/rfc5321.txt:3043` требует от клиента обрабатывать неизвестные коды
 * «by interpreting the first digit only», и падать на второй цифре, выходящей за ABNF, значит
 * ломаться из-за чужой небрежности там, где протокол велит быть терпимым.
 */
@JvmInline
public value class SmtpReplyCode(
    public val value: Int,
) {
    init {
        if (value !in MIN_VALUE..MAX_VALUE) {
            throw SmtpProtocolException("Код ответа вне диапазона 2xx…5xx: $value")
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
