package io.github.youndie.smtp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parsing the `EHLO` reply.
 *
 * ABNF — `docs/rfc/rfc5321.txt:1841`; keywords and parameters — `:1858`, `:1860`, `:1864`.
 */
class CapabilitiesTest {
    private val fullReply =
        SmtpReply.parse(
            listOf(
                "250-smtp.example.com Hello client.example.com",
                "250-PIPELINING",
                "250-SIZE 35882577",
                "250-STARTTLS",
                "250-AUTH PLAIN LOGIN XOAUTH2",
                "250-8BITMIME",
                "250-SMTPUTF8",
                "250 ENHANCEDSTATUSCODES",
            ),
        )

    @Test
    fun `the first line is the greeting and not an extension`() {
        // rfc5321.txt:1841: ehlo-ok-rsp starts with Domain [SP ehlo-greet]; extensions begin on
        // the second line.
        val capabilities = Capabilities.parse(fullReply)

        assertEquals("smtp.example.com Hello client.example.com", capabilities.greeting)
        assertFalse("SMTP.EXAMPLE.COM" in capabilities)
    }

    @Test
    fun `keywords are read case-insensitively`() {
        // rfc5321.txt:1869: "Although EHLO keywords may be specified in upper, lower, or mixed
        // case, they MUST always be recognized and processed in a case-insensitive manner."
        val capabilities = Capabilities.parse(fullReply)

        assertTrue("PIPELINING" in capabilities)
        assertTrue("pipelining" in capabilities)
        assertTrue("PiPeLiNiNg" in capabilities)
    }

    @Test
    fun `extensions announced in lower case are understood the same way`() {
        val capabilities = Capabilities.parse(SmtpReply.parse(listOf("250-smtp.example.com", "250 starttls")))

        assertTrue("STARTTLS" in capabilities)
    }

    @Test
    fun `keyword parameters are preserved`() {
        // rfc5321.txt:1858: ehlo-line = ehlo-keyword *( SP ehlo-param )
        val capabilities = Capabilities.parse(fullReply)

        assertEquals(listOf("PLAIN", "LOGIN", "XOAUTH2"), capabilities.parametersOf("AUTH"))
        assertEquals(emptyList(), capabilities.parametersOf("PIPELINING"))
    }

    @Test
    fun `SIZE yields the maximum message size`() {
        assertEquals(35882577L, Capabilities.parse(fullReply).maxMessageSize)
    }

    @Test
    fun `SIZE without a value and SIZE zero both mean no known limit`() {
        // rfc1870.txt:79: "A parameter value of 0 (zero) indicates that no fixed maximum message
        // size is in force. If the parameter is omitted no information is conveyed".
        assertNull(Capabilities.parse(SmtpReply.parse(listOf("250-host", "250 SIZE"))).maxMessageSize)
        assertNull(Capabilities.parse(SmtpReply.parse(listOf("250-host", "250 SIZE 0"))).maxMessageSize)
    }

    @Test
    fun `AUTH mechanisms are upper-cased`() {
        val capabilities = Capabilities.parse(SmtpReply.parse(listOf("250-host", "250 AUTH plain login")))

        assertEquals(setOf("PLAIN", "LOGIN"), capabilities.authMechanisms)
    }

    @Test
    fun `a single line reply means a server without extensions`() {
        val capabilities = Capabilities.parse(SmtpReply.parse(listOf("250 smtp.example.com")))

        assertEquals("smtp.example.com", capabilities.greeting)
        assertEquals(emptySet(), capabilities.keywords)
        assertFalse(capabilities.supportsStartTls)
    }

    @Test
    fun `an unknown extension is kept along with its parameters`() {
        // The extension registry keeps growing; dropping what we do not recognise loses exactly
        // the thing the library would later have to be patched for.
        val capabilities = Capabilities.parse(SmtpReply.parse(listOf("250-host", "250 FUTURERELEASE 86400 2038")))

        assertTrue("FUTURERELEASE" in capabilities)
        assertEquals(listOf("86400", "2038"), capabilities.parametersOf("FUTURERELEASE"))
    }

    @Test
    fun `known extensions are exposed as separate properties`() {
        val capabilities = Capabilities.parse(fullReply)

        assertTrue(capabilities.supportsPipelining)
        assertTrue(capabilities.supportsStartTls)
        assertTrue(capabilities.supports8BitMime)
        assertTrue(capabilities.supportsSmtpUtf8)
        assertTrue(capabilities.supportsEnhancedStatusCodes)
        assertFalse(capabilities.supportsChunking)
        assertFalse(capabilities.supportsDsn)
    }

    @Test
    fun `a reply other than 250 announces no extensions`() {
        assertFailsWith<SmtpProtocolException> {
            Capabilities.parse(SmtpReply.parse(listOf("500 Command not recognized")))
        }
    }
}
