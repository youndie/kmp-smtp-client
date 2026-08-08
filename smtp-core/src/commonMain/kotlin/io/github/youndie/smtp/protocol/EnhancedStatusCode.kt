package io.github.youndie.smtp.protocol

/**
 * Расширенный код состояния вида `5.7.1` — `docs/rfc/rfc3463.txt:128`.
 *
 * Реестр значений — `docs/rfc/rfc5248.txt`; здесь только разбор, без попытки объяснить смысл
 * каждой пары подкодов: реестр пополняется, и зашитая таблица устареет раньше библиотеки.
 */
public data class EnhancedStatusCode(
    public val statusClass: Int,
    public val subject: Int,
    public val detail: Int,
) {
    override fun toString(): String = "$statusClass.$subject.$detail"

    public companion object {
        /**
         * Разбирает первое слово текста ответа.
         *
         * Возвращает `null`, если слово расширенным кодом не является: текст ответа — обычная
         * строка, и принять её за код опаснее, чем не заметить настоящий.
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
         * Подкод — от одной до трёх цифр без ведущих нулей (`docs/rfc/rfc3463.txt:128`, `:138`).
         *
         * `toIntOrNull` здесь недостаточно: он проглотит и `+2`, и `007`, и пробелы.
         */
        private fun String.toSubCodeOrNull(): Int? {
            if (length !in 1..MAX_SUB_CODE_DIGITS) return null
            if (!all { it in '0'..'9' }) return null
            if (length > 1 && this[0] == '0') return null
            return toInt()
        }
    }
}
