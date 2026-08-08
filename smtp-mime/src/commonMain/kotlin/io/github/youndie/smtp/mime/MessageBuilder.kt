package io.github.youndie.smtp.mime

import io.github.youndie.smtp.protocol.Mailbox
import kotlin.time.Instant

public class MessageBuilder(
    private val from: Mailbox,
    private val to: List<Mailbox>,
) {
    public var subject: String = ""
    public var text: String? = null
    public var html: String? = null

    public fun attach(
        fileName: String,
        contentType: String,
        content: ByteArray,
    ): Unit = TODO("M-93")

    public fun build(
        sentAt: Instant,
        messageIdDomain: String,
        messageIdSource: () -> String = { TODO("M-91") },
        boundarySource: (Int) -> String = { TODO("M-92") },
    ): List<String> = TODO("M-91")
}
