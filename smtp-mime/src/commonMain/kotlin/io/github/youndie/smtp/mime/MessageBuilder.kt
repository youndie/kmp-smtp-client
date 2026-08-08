package io.github.youndie.smtp.mime

import io.github.youndie.smtp.protocol.Mailbox
import io.github.youndie.smtp.protocol.SmtpProtocolException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.io.encoding.Base64
import kotlin.time.Instant

/** A file travelling with the message. */
public class Attachment(
    public val fileName: String,
    public val contentType: String,
    public val content: ByteArray,
)

/**
 * Builds a message — `docs/rfc/rfc5322.txt` and the MIME set.
 *
 * A different specification from the envelope: `docs/rfc/rfc5321.txt` says who the message is for,
 * 5322 says what is inside, and the two are not required to agree. That is why the addresses given
 * here do not have to be the ones handed to `send`.
 *
 * The body comes out as lines **without** dot-stuffing: transparency belongs to the transfer
 * (`docs/rfc/rfc5321.txt:3423`) and is applied by `MailData`. Doing it twice leaves a doubled
 * period in the delivered message.
 */
public class MessageBuilder(
    private val from: Mailbox,
    private val to: List<Mailbox>,
    private val cc: List<Mailbox> = emptyList(),
) {
    public var subject: String = ""
    public var text: String? = null
    public var html: String? = null

    private val attachments = mutableListOf<Attachment>()

    public fun attach(
        fileName: String,
        contentType: String,
        content: ByteArray,
    ) {
        attachments += Attachment(fileName, contentType, content)
    }

    public fun build(
        sentAt: Instant,
        messageIdDomain: String,
        messageIdSource: () -> String = { defaultMessageIdPart(sentAt) },
        boundarySource: (Int) -> String = { index -> "${defaultMessageIdPart(sentAt)}-$index" },
    ): List<String> {
        val headers =
            buildList {
                add("Date" to rfc5322Date(sentAt))
                add("From" to from.path)
                if (to.isNotEmpty()) add("To" to to.joinToString(", ") { it.path })
                if (cc.isNotEmpty()) add("Cc" to cc.joinToString(", ") { it.path })
                if (subject.isNotEmpty()) add("Subject" to encodeHeaderValue(subject))
                add("Message-ID" to "<${messageIdSource()}@$messageIdDomain>")
                add("MIME-Version" to "1.0")
            }

        val body = buildBody(boundarySource)

        return buildList {
            headers.forEach { (name, value) -> addAll(foldHeader(name, value)) }
            addAll(body.headers)
            add("")
            addAll(body.lines)
        }
    }

    private class BodyPart(
        val headers: List<String>,
        val lines: List<String>,
    )

    private fun buildBody(boundarySource: (Int) -> String): BodyPart {
        val content = contentPart(boundarySource)
        if (attachments.isEmpty()) return content

        // rfc2046.txt: files sit beside the content, so the outermost type is multipart/mixed.
        val boundary = boundarySource(0)
        val lines =
            buildList {
                add("--$boundary")
                addAll(content.headers)
                add("")
                addAll(content.lines)
                attachments.forEach { attachment ->
                    add("--$boundary")
                    add("Content-Type: ${attachment.contentType}; name=\"${attachment.fileName}\"")
                    add("Content-Transfer-Encoding: base64")
                    add("Content-Disposition: attachment; filename=\"${attachment.fileName}\"")
                    add("")
                    // Base64.Mime wraps at 76 characters, which is the limit rfc2045.txt sets.
                    addAll(Base64.Mime.encode(attachment.content).split("\r\n"))
                }
                add("--$boundary--")
            }

        return BodyPart(listOf("Content-Type: multipart/mixed; boundary=\"$boundary\""), lines)
    }

    private fun contentPart(boundarySource: (Int) -> String): BodyPart {
        val plain = text
        val rich = html

        if (plain != null && rich != null) {
            // rfc2046.txt: least capable form first, so a reader that stops at the first part it
            // understands still shows something readable.
            val boundary = boundarySource(1)
            val lines =
                buildList {
                    add("--$boundary")
                    add("Content-Type: text/plain; charset=utf-8")
                    add("")
                    addAll(plain.toLines())
                    add("--$boundary")
                    add("Content-Type: text/html; charset=utf-8")
                    add("")
                    addAll(rich.toLines())
                    add("--$boundary--")
                }
            return BodyPart(listOf("Content-Type: multipart/alternative; boundary=\"$boundary\""), lines)
        }

        val single = plain ?: rich ?: ""
        val type = if (plain != null || rich == null) "text/plain" else "text/html"
        return BodyPart(listOf("Content-Type: $type; charset=utf-8"), single.toLines())
    }

    private fun String.toLines(): List<String> = split("\r\n", "\n")

    /**
     * `docs/rfc/rfc5322.txt` section 3.3, always in UTC.
     *
     * A local offset would say where the sender's machine stands, which is information the message
     * did not ask to carry.
     */
    private fun rfc5322Date(instant: Instant): String {
        val time = instant.toLocalDateTime(TimeZone.UTC)
        val day = DAY_NAMES[time.dayOfWeek.ordinal]
        val month = MONTH_NAMES[time.month.ordinal]
        val hour = time.hour.pad()
        val minute = time.minute.pad()
        val second = time.second.pad()
        return "$day, ${time.day} $month ${time.year} $hour:$minute:$second +0000"
    }

    private fun Int.pad(): String = if (this < 10) "0$this" else toString()

    private fun defaultMessageIdPart(sentAt: Instant): String = sentAt.toEpochMilliseconds().toString(RADIX)

    /**
     * `docs/rfc/rfc2047.txt`: a header value is ASCII, so anything else is encoded in place.
     *
     * The whole value is encoded as one word rather than split per character run: simpler, and the
     * limit that matters (75 characters per encoded word) is handled by folding afterwards.
     */
    private fun encodeHeaderValue(value: String): String {
        checkNoLineBreak(value)
        if (value.all { it.code in 32..126 }) return value
        return "=?utf-8?B?${Base64.encode(value.encodeToByteArray())}?="
    }

    private fun checkNoLineBreak(value: String) {
        if (value.any { it == '\r' || it == '\n' }) {
            // Otherwise a subject is a way to add headers of somebody else's choosing, or to end
            // the header section early.
            throw SmtpProtocolException("A header value contains a line break: '$value'")
        }
    }

    /**
     * `docs/rfc/rfc5322.txt` section 2.2.3: long fields are split on white space and continued on
     * lines starting with white space.
     */
    private fun foldHeader(
        name: String,
        value: String,
    ): List<String> {
        checkNoLineBreak(value)

        val first = "$name: $value"
        if (first.length <= MAX_HEADER_LINE) return listOf(first)

        val lines = mutableListOf<String>()
        var current = StringBuilder("$name:")

        value.split(' ').forEach { word ->
            if (current.length + 1 + word.length > MAX_HEADER_LINE) {
                lines += current.toString()
                current = StringBuilder(" ")
                current.append(word)
            } else {
                current.append(' ').append(word)
            }
        }
        lines += current.toString()
        return lines
    }

    private companion object {
        const val MAX_HEADER_LINE = 78
        const val RADIX = 36
        val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val MONTH_NAMES =
            listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    }
}
