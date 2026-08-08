package io.github.youndie.smtp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Адрес и путь конверта.
 *
 * ABNF — `docs/rfc/rfc5321.txt:2314` (Mailbox) и `:2264` (Path); пределы длин —
 * `docs/rfc/rfc5321.txt:3484`, `:3489`, `:3493`.
 */
class MailboxTest {
    @Test
    fun `адрес разбирается на локальную часть и домен`() {
        val mailbox = Mailbox.parse("user@example.com")

        assertEquals("user", mailbox.localPart)
        assertEquals("example.com", mailbox.domain)
        assertEquals("user@example.com", mailbox.address)
    }

    @Test
    fun `путь оборачивается в угловые скобки`() {
        // rfc5321.txt:2264: Path = "<" [ A-d-l ":" ] Mailbox ">"
        assertEquals("<user@example.com>", Mailbox.parse("user@example.com").path)
    }

    @Test
    fun `регистр локальной части сохраняется`() {
        // rfc5321.txt:2316: "Local-part ... MAY be case-sensitive". Приведение к нижнему регистру
        // — молчаливая порча чужого адреса.
        val mailbox = Mailbox.parse("John.Doe@Example.COM")

        assertEquals("John.Doe", mailbox.localPart)
        assertEquals("Example.COM", mailbox.domain)
    }

    @Test
    fun `собака в адресе ищется с конца`() {
        // Quoted-string в локальной части может содержать '@' (rfc5321.txt:2322).
        val mailbox = Mailbox.parse("\"weird@local\"@example.com")

        assertEquals("\"weird@local\"", mailbox.localPart)
        assertEquals("example.com", mailbox.domain)
    }

    @Test
    fun `локальная часть длиннее 64 октетов — ошибка`() {
        // rfc5321.txt:3484
        Mailbox.parse("${"a".repeat(64)}@example.com")

        assertFailsWith<SmtpProtocolException> { Mailbox.parse("${"a".repeat(65)}@example.com") }
    }

    @Test
    fun `домен длиннее 255 октетов — ошибка`() {
        // rfc5321.txt:3489
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("user@${"d".repeat(256)}") }
    }

    @Test
    fun `путь длиннее 256 октетов — ошибка при законных частях`() {
        // rfc5321.txt:3493: предел пути — 256 октетов "including the punctuation and element
        // separators". 64 + 1 + 200 + 2 = 267: обе части в пределах, путь — нет.
        assertFailsWith<SmtpProtocolException> {
            Mailbox.parse("${"a".repeat(64)}@${"d".repeat(200)}")
        }
    }

    @Test
    fun `пределы считаются в октетах — не в символах`() {
        // Там же; с SMTPUTF8 (rfc6531.txt) адрес бывает не-ASCII, и 40 кириллических символов —
        // это 80 октетов.
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("${"я".repeat(40)}@example.com") }
    }

    @Test
    fun `перевод строки в адресе — ошибка`() {
        // Иначе адрес становится способом дописать команду: всё, что после CRLF, сервер прочтёт
        // как следующую команду (rfc5321.txt:763 — CRLF разделяет строки протокола).
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("user\r\nRCPT TO:<evil@example.com>@example.com") }
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("user@example.com\nDATA") }
    }

    @Test
    fun `угловые скобки внутри адреса — ошибка`() {
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("user<@example.com") }
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("user@example.com>") }
    }

    @Test
    fun `адрес без собаки или без одной из частей — ошибка`() {
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("user") }
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("user@") }
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("@example.com") }
        assertFailsWith<SmtpProtocolException> { Mailbox.parse("") }
    }
}
