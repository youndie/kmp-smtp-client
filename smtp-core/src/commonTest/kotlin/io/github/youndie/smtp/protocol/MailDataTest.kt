package io.github.youndie.smtp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Передача тела письма.
 *
 * Прозрачность — `docs/rfc/rfc5321.txt:3423`, предел строки — `docs/rfc/rfc5321.txt:3510`.
 */
class MailDataTest {
    @Test
    fun `ведущая точка удваивается`() {
        // rfc5321.txt:3423: "If it is a period, one additional period is inserted at the
        // beginning of the line."
        assertEquals("..", MailData.encodeLine("."))
        assertEquals("..текст", MailData.encodeLine(".текст"))
    }

    @Test
    fun `удваивается только первая точка`() {
        assertEquals("...", MailData.encodeLine(".."))
    }

    @Test
    fun `точка не в начале строки не трогается`() {
        assertEquals("a.b", MailData.encodeLine("a.b"))
        assertEquals("текст.", MailData.encodeLine("текст."))
    }

    @Test
    fun `тело завершается точкой на отдельной строке`() {
        // rfc5321.txt:3423: конец тела — <CRLF>.<CRLF>.
        assertEquals(
            "Subject: hi\r\n\r\nтело\r\n.\r\n",
            MailData.encode(listOf("Subject: hi", "", "тело")),
        )
    }

    @Test
    fun `пустое тело — это одна точка`() {
        assertEquals(".\r\n", MailData.encode(emptyList()))
    }

    @Test
    fun `строка тела длиннее 1000 октетов — ошибка`() {
        // rfc5321.txt:3510: 1000 октетов вместе с CRLF, то есть 998 без него.
        MailData.encodeLine("x".repeat(998))

        assertFailsWith<SmtpProtocolException> { MailData.encodeLine("x".repeat(999)) }
    }

    @Test
    fun `предел считается до удвоения точки`() {
        // rfc5321.txt:3512: "(not counting the leading dot duplicated for transparency)".
        // Строка из 998 символов с точкой в начале законна, хотя на проводе станет 999.
        val line = "." + "x".repeat(997)

        assertEquals(999, MailData.encodeLine(line).length)
    }

    @Test
    fun `предел строки тела считается в октетах`() {
        // Там же; с 8BITMIME (rfc6152.txt) и SMTPUTF8 (rfc6531.txt) тело бывает не-ASCII.
        assertFailsWith<SmtpProtocolException> { MailData.encodeLine("я".repeat(500)) }
    }

    @Test
    fun `перевод строки внутри строки тела — ошибка`() {
        // rfc5321.txt:763: строки протокола разделяет CRLF. Спрятанный внутри перевод строки —
        // это либо потерянная прозрачность, либо чужая команда.
        assertFailsWith<SmtpProtocolException> { MailData.encodeLine("Subject: hi\r\n.\r\nQUIT") }
        assertFailsWith<SmtpProtocolException> { MailData.encodeLine("Subject: hi\nfrom nowhere") }
    }

    @Test
    fun `точка на отдельной строке внутри тела не обрывает письмо`() {
        // Ровно та дыра, ради которой прозрачность и придумана: без удвоения сервер принял бы
        // обрубок как целое письмо.
        val encoded = MailData.encode(listOf("до", ".", "после"))

        assertEquals("до\r\n..\r\nпосле\r\n.\r\n", encoded)
    }
}
