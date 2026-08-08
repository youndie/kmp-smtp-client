package io.github.youndie.smtp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Сериализация команд.
 *
 * Синтаксис команд — `docs/rfc/rfc5321.txt:1913` и далее; предел командной строки —
 * `docs/rfc/rfc5321.txt:3498`.
 */
class SmtpCommandTest {
    @Test
    fun `команда завершается CRLF`() {
        // rfc5321.txt:763: CRLF — единственный разделитель строк протокола.
        assertEquals("EHLO client.example.com\r\n", SmtpCommand.Ehlo("client.example.com").encode())
    }

    @Test
    fun `MAIL FROM пишется без пробела после двоеточия`() {
        // rfc5321.txt:1913: mail = "MAIL FROM:" Reverse-path [SP Mail-parameters] CRLF —
        // двоеточие входит в литерал, пробела после него нет.
        val command = SmtpCommand.MailFrom(Mailbox.parse("user@example.com"))

        assertEquals("MAIL FROM:<user@example.com>\r\n", command.encode())
    }

    @Test
    fun `пустой обратный путь пишется как угловые скобки`() {
        // rfc5321.txt:2260: Reverse-path = Path / "<>". Так отправляются отчёты о недоставке.
        assertEquals("MAIL FROM:<>\r\n", SmtpCommand.MailFrom(sender = null).encode())
    }

    @Test
    fun `RCPT TO несёт одного получателя`() {
        val command = SmtpCommand.RcptTo(Mailbox.parse("user@example.com"))

        assertEquals("RCPT TO:<user@example.com>\r\n", command.encode())
    }

    @Test
    fun `параметры отделяются пробелом`() {
        val command =
            SmtpCommand.MailFrom(
                sender = Mailbox.parse("user@example.com"),
                parameters = listOf("SIZE=1000", "BODY=8BITMIME"),
            )

        assertEquals("MAIL FROM:<user@example.com> SIZE=1000 BODY=8BITMIME\r\n", command.encode())
    }

    @Test
    fun `команды без аргументов сериализуются одним словом`() {
        assertEquals("DATA\r\n", SmtpCommand.Data.encode())
        assertEquals("RSET\r\n", SmtpCommand.Rset.encode())
        assertEquals("QUIT\r\n", SmtpCommand.Quit.encode())
        assertEquals("NOOP\r\n", SmtpCommand.Noop().encode())
        // rfc3207.txt: STARTTLS аргументов не имеет.
        assertEquals("STARTTLS\r\n", SmtpCommand.StartTls.encode())
    }

    @Test
    fun `AUTH передаёт механизм и начальный ответ`() {
        // rfc4954.txt:699: auth-command = "AUTH" SP sasl-mech [SP initial-response]
        assertEquals("AUTH PLAIN AGFiYwBkZWY=\r\n", SmtpCommand.Auth("PLAIN", "AGFiYwBkZWY=").encode())
        assertEquals("AUTH LOGIN\r\n", SmtpCommand.Auth("LOGIN").encode())
    }

    @Test
    fun `командная строка длиннее 512 октетов — ошибка`() {
        // rfc5321.txt:3498: 512 октетов вместе с CRLF, то есть 510 без него.
        val fits = SmtpCommand.Ehlo("d".repeat(510 - "EHLO ".length))
        assertEquals(510 + 2, fits.encode().length)

        val tooLong = SmtpCommand.Ehlo("d".repeat(510 - "EHLO ".length + 1))
        assertFailsWith<SmtpProtocolException> { tooLong.encode() }
    }

    @Test
    fun `у AUTH предел можно проверить заранее`() {
        // rfc4954.txt:208: "If use of the initial response argument would cause the AUTH command
        // to exceed this length, the client MUST NOT use the initial response parameter".
        // Токены OAuth это задевает всерьёз, поэтому спрашивать надо до отправки.
        assertTrue(SmtpCommand.Auth("PLAIN", "AGFiYwBkZWY=").fitsLineLimit)
        assertFalse(SmtpCommand.Auth("XOAUTH2", "x".repeat(600)).fitsLineLimit)
    }

    @Test
    fun `предел командной строки считается в октетах`() {
        // rfc5321.txt:3498 задаёт предел в октетах; с SMTPUTF8 (rfc6531.txt) в команде бывает
        // не-ASCII.
        assertFalse(SmtpCommand.Ehlo("я".repeat(300)).fitsLineLimit)
    }

    @Test
    fun `перевод строки в аргументе команды — ошибка`() {
        // Иначе клиент своими руками отправляет серверу лишнюю команду.
        assertFailsWith<SmtpProtocolException> { SmtpCommand.Ehlo("client\r\nQUIT").encode() }
        assertFailsWith<SmtpProtocolException> { SmtpCommand.Auth("PLAIN", "dGVzdA==\r\nQUIT").encode() }
        assertFailsWith<SmtpProtocolException> {
            SmtpCommand.MailFrom(Mailbox.parse("user@example.com"), listOf("SIZE=1\r\nQUIT")).encode()
        }
    }
}
