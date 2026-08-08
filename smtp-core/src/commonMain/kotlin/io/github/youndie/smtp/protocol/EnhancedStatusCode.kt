package io.github.youndie.smtp.protocol

/**
 * An enhanced status code such as `5.7.1` — `docs/rfc/rfc3463.txt:128`.
 *
 * The registry of values lives in `docs/rfc/rfc5248.txt`. Only parsing happens here, with no
 * attempt to explain what each pair of sub-codes means: the registry keeps growing, and a table
 * baked into the code would rot before the library does.
 */
public data class EnhancedStatusCode(
    public val statusClass: Int,
    public val subject: Int,
    public val detail: Int,
) {
    override fun toString(): String = "$statusClass.$subject.$detail"

    public companion object {
        /**
         * Parses the first word of a reply's text.
         *
         * Returns `null` when the word is not an enhanced status code: reply text is an ordinary
         * string, and mistaking one for a code is worse than missing one.
         */
        public fun parseOrNull(token: String): EnhancedStatusCode? {
            val parts = token.split('.')
            if (parts.size != PART_COUNT) {
                return null
            }

            val statusClass = parts[0].toSubCodeOrNull() ?: return null
            if (statusClass !in ALLOWED_CLASSES) {
                // rfc3463.txt:129: class = "2" / "4" / "5".
                return null
            }

            val subject = parts[1].toSubCodeOrNull() ?: return null
            val detail = parts[2].toSubCodeOrNull() ?: return null

            return EnhancedStatusCode(statusClass, subject, detail)
        }

        private const val PART_COUNT = 3
        private const val MAX_SUB_CODE_DIGITS = 3
        private val ALLOWED_CLASSES = setOf(2, 4, 5)

        /**
         * A sub-code is one to three digits with no leading zeros (`docs/rfc/rfc3463.txt:128`,
         * `:138`).
         *
         * `toIntOrNull` is not enough here: it happily swallows `+2`, `007` and surrounding
         * whitespace.
         */
        private fun String.toSubCodeOrNull(): Int? {
            if (length !in 1..MAX_SUB_CODE_DIGITS) return null
            if (!all { it in '0'..'9' }) return null
            if (length > 1 && this[0] == '0') return null
            return toInt()
        }
    }
}
