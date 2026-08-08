package io.github.youndie.smtp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Разбор ответа на `EHLO`.
 *
 * ABNF — `docs/rfc/rfc5321.txt:1841`; ключевые слова и параметры — `:1858`, `:1860`, `:1864`.
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
    fun `первая строка — приветствие и не расширение`() {
        // rfc5321.txt:1841: ehlo-ok-rsp начинается с Domain [SP ehlo-greet], расширения идут
        // со второй строки.
        val capabilities = Capabilities.parse(fullReply)

        assertEquals("smtp.example.com Hello client.example.com", capabilities.greeting)
        assertFalse("SMTP.EXAMPLE.COM" in capabilities)
    }

    @Test
    fun `ключевые слова читаются независимо от регистра`() {
        // rfc5321.txt:1869: "Although EHLO keywords may be specified in upper, lower, or mixed
        // case, they MUST always be recognized and processed in a case-insensitive manner."
        val capabilities = Capabilities.parse(fullReply)

        assertTrue("PIPELINING" in capabilities)
        assertTrue("pipelining" in capabilities)
        assertTrue("PiPeLiNiNg" in capabilities)
    }

    @Test
    fun `расширения в нижнем регистре понимаются так же`() {
        val capabilities = Capabilities.parse(SmtpReply.parse(listOf("250-smtp.example.com", "250 starttls")))

        assertTrue("STARTTLS" in capabilities)
    }

    @Test
    fun `параметры ключевого слова сохраняются`() {
        // rfc5321.txt:1858: ehlo-line = ehlo-keyword *( SP ehlo-param )
        val capabilities = Capabilities.parse(fullReply)

        assertEquals(listOf("PLAIN", "LOGIN", "XOAUTH2"), capabilities.parametersOf("AUTH"))
        assertEquals(emptyList(), capabilities.parametersOf("PIPELINING"))
    }

    @Test
    fun `SIZE отдаёт предельный размер письма`() {
        assertEquals(35882577L, Capabilities.parse(fullReply).maxMessageSize)
    }

    @Test
    fun `SIZE без параметра и SIZE ноль означают отсутствие известного предела`() {
        // rfc1870.txt:79: "A parameter value of 0 (zero) indicates that no fixed maximum message
        // size is in force. If the parameter is omitted no information is conveyed".
        assertNull(Capabilities.parse(SmtpReply.parse(listOf("250-host", "250 SIZE"))).maxMessageSize)
        assertNull(Capabilities.parse(SmtpReply.parse(listOf("250-host", "250 SIZE 0"))).maxMessageSize)
    }

    @Test
    fun `механизмы AUTH приводятся к верхнему регистру`() {
        val capabilities = Capabilities.parse(SmtpReply.parse(listOf("250-host", "250 AUTH plain login")))

        assertEquals(setOf("PLAIN", "LOGIN"), capabilities.authMechanisms)
    }

    @Test
    fun `однострочный ответ означает сервер без расширений`() {
        val capabilities = Capabilities.parse(SmtpReply.parse(listOf("250 smtp.example.com")))

        assertEquals("smtp.example.com", capabilities.greeting)
        assertEquals(emptySet(), capabilities.keywords)
        assertFalse(capabilities.supportsStartTls)
    }

    @Test
    fun `неизвестное расширение сохраняется вместе с параметрами`() {
        // Реестр расширений пополняется; выбрасывать незнакомое значит терять то, ради чего
        // библиотеку потом придётся править.
        val capabilities = Capabilities.parse(SmtpReply.parse(listOf("250-host", "250 FUTURERELEASE 86400 2038")))

        assertTrue("FUTURERELEASE" in capabilities)
        assertEquals(listOf("86400", "2038"), capabilities.parametersOf("FUTURERELEASE"))
    }

    @Test
    fun `известные расширения доступны отдельными свойствами`() {
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
    fun `ответ не 250 расширениями не является`() {
        assertFailsWith<SmtpProtocolException> {
            Capabilities.parse(SmtpReply.parse(listOf("500 Command not recognized")))
        }
    }
}
