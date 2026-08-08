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
        public fun parseOrNull(token: String): EnhancedStatusCode? = TODO("M-13")
    }
}
