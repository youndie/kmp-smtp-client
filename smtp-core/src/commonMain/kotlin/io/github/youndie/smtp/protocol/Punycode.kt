package io.github.youndie.smtp.protocol

/**
 * Punycode — `docs/rfc/rfc3492.txt`.
 *
 * Only encoding is here: a client turns names into A-labels, it never has to read one back.
 */
public object Punycode {
    private const val BASE = 36
    private const val TMIN = 1
    private const val TMAX = 26
    private const val SKEW = 38
    private const val DAMP = 700
    private const val INITIAL_BIAS = 72
    private const val INITIAL_N = 128
    private const val DELIMITER = '-'

    /** Encodes one label. The result carries no `xn--` prefix — that belongs to IDNA. */
    public fun encode(input: String): String {
        val codePoints = input.toCodePoints()
        val output = StringBuilder()

        // Basic (ASCII) code points are copied out first, then a delimiter if there were any.
        codePoints.filter { it < INITIAL_N }.forEach { output.append(it.toChar()) }
        val basicLength = output.length
        if (basicLength > 0) output.append(DELIMITER)

        var handled = basicLength
        var n = INITIAL_N
        var delta = 0
        var bias = INITIAL_BIAS

        while (handled < codePoints.size) {
            val next = codePoints.filter { it >= n }.min()
            delta += (next - n) * (handled + 1)
            n = next

            codePoints.forEach { codePoint ->
                if (codePoint < n) delta++
                if (codePoint == n) {
                    var q = delta
                    var k = BASE
                    while (true) {
                        val t = threshold(k, bias)
                        if (q < t) break
                        output.append(digit(t + (q - t) % (BASE - t)))
                        q = (q - t) / (BASE - t)
                        k += BASE
                    }
                    output.append(digit(q))
                    bias = adapt(delta, handled + 1, handled == basicLength)
                    delta = 0
                    handled++
                }
            }

            delta++
            n++
        }

        return output.toString()
    }

    private fun threshold(
        k: Int,
        bias: Int,
    ): Int =
        when {
            k <= bias + TMIN -> TMIN
            k >= bias + TMAX -> TMAX
            else -> k - bias
        }

    private fun adapt(
        delta: Int,
        numPoints: Int,
        first: Boolean,
    ): Int {
        var d = if (first) delta / DAMP else delta / 2
        d += d / numPoints

        var k = 0
        while (d > ((BASE - TMIN) * TMAX) / 2) {
            d /= BASE - TMIN
            k += BASE
        }
        return k + (BASE - TMIN + 1) * d / (d + SKEW)
    }

    /** 0..25 map to 'a'..'z', 26..35 to '0'..'9'. */
    private fun digit(value: Int): Char = if (value < 26) ('a' + value) else ('0' + (value - 26))

    /** Surrogate pairs are one code point; treating them as two would encode a different name. */
    private fun String.toCodePoints(): List<Int> {
        val points = mutableListOf<Int>()
        var index = 0
        while (index < length) {
            val character = this[index]
            if (character.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
                val high = character.code - 0xD800
                val low = this[index + 1].code - 0xDC00
                points += 0x10000 + (high shl 10) + low
                index += 2
            } else {
                points += character.code
                index++
            }
        }
        return points
    }
}

/**
 * Domain names in their ASCII form — `docs/rfc/rfc5891.txt`.
 *
 * **What is done:** each label that is not already ASCII is punycoded and gets the `xn--` prefix.
 *
 * **What is not done: the IDNA2008 mapping and validation steps.** Case folding, normalisation and
 * the tables of allowed code points need Unicode data that Kotlin's common standard library does
 * not carry. The caller is expected to pass names that are already valid U-labels — which is what
 * a name copied from a mail address normally is. Tracked as M-68a.
 */
public object Idna {
    private const val ACE_PREFIX = "xn--"

    public fun toAscii(domain: String): String =
        domain
            .split('.')
            .joinToString(".") { label ->
                if (label.all { it.code < 128 }) label else ACE_PREFIX + Punycode.encode(label)
            }
}
