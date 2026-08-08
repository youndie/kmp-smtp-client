package io.github.youndie.smtp.sasl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Preparation of credentials — `docs/rfc/rfc4013.txt`.
 *
 * The examples are the ones the RFC itself lists in section 3.
 */
class SaslPrepTest {
    @Test
    fun `ASCII passes through unchanged`() {
        assertEquals("user", SaslPrep.prepare("user"))
        assertEquals("p@ssw0rd!", SaslPrep.prepare("p@ssw0rd!"))
    }

    @Test
    fun `a soft hyphen is removed`() {
        // rfc4013.txt section 3: "I<U+00AD>X" prepares to "IX".
        assertEquals("IX", SaslPrep.prepare("I\u00ADX"))
    }

    @Test
    fun `a non-breaking space becomes an ordinary space`() {
        // rfc4013.txt section 3: "a<U+00A0>b" prepares to "a b".
        assertEquals("a b", SaslPrep.prepare("a\u00A0b"))
    }

    @Test
    fun `a control character is refused`() {
        // Not a formality here: a CR or LF inside a password writes a new SMTP command.
        assertFailsWith<SaslException> { SaslPrep.prepare("password") }
        assertFailsWith<SaslException> { SaslPrep.prepare("pass\r\nQUIT") }
    }
}
