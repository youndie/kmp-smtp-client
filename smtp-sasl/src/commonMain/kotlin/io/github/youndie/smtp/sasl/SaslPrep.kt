package io.github.youndie.smtp.sasl

/**
 * Preparation of user names and passwords — `docs/rfc/rfc4013.txt`, superseded by
 * `docs/rfc/rfc8265.txt`.
 *
 * **What is done:** characters that map to nothing are removed, non-ASCII spaces become an ordinary
 * space, and control characters are refused. Those are the rules that catch the mistakes people
 * actually make — a soft hyphen pasted from a document, a non-breaking space from a web form, a
 * stray newline that would otherwise become a protocol injection.
 *
 * **What is not done: Unicode normalisation (NFKC).** Kotlin has no normalisation in its common
 * standard library, and shipping a normalisation table would double the size of this module. The
 * consequence is honest and narrow: a password typed in one normalisation form and stored in
 * another will not authenticate. ASCII credentials — the overwhelming majority — are unaffected.
 * Tracked as M-58a.
 */
public object SaslPrep {
    public fun prepare(value: String): String {
        val result = StringBuilder(value.length)

        value.forEach { character ->
            when {
                // rfc3454 B.1: mapped to nothing.
                character in MAPPED_TO_NOTHING -> Unit

                // rfc3454 C.1.2: non-ASCII space characters become an ordinary space.
                character in NON_ASCII_SPACES -> result.append(' ')

                // rfc3454 C.2.1 and C.2.2: control characters are prohibited. In this protocol
                // that is not a formality — a CR or LF inside a password writes a new command.
                character.isProhibitedControl() -> throw SaslException(
                    "A credential contains a control character (U+${character.code.toString(16).uppercase()})",
                )

                else -> result.append(character)
            }
        }

        return result.toString()
    }

    private fun Char.isProhibitedControl(): Boolean =
        code < 0x20 || code == 0x7F || (code in 0x80..0x9F) || code == 0x2028 || code == 0x2029

    private val MAPPED_TO_NOTHING =
        setOf(
            '\u00AD',
            '\u034F',
            '\u1806',
            '\u180B',
            '\u180C',
            '\u180D',
            '\u200B',
            '\u200C',
            '\u200D',
            '\u2060',
            '\uFE00',
            '\uFE01',
            '\uFE02',
            '\uFE03',
            '\uFE04',
            '\uFE05',
            '\uFE06',
            '\uFE07',
            '\uFE08',
            '\uFE09',
            '\uFE0A',
            '\uFE0B',
            '\uFE0C',
            '\uFE0D',
            '\uFE0E',
            '\uFE0F',
            '\uFEFF',
        )

    private val NON_ASCII_SPACES =
        setOf(
            '\u00A0',
            '\u1680',
            '\u2000',
            '\u2001',
            '\u2002',
            '\u2003',
            '\u2004',
            '\u2005',
            '\u2006',
            '\u2007',
            '\u2008',
            '\u2009',
            '\u200A',
            '\u202F',
            '\u205F',
            '\u3000',
        )
}
