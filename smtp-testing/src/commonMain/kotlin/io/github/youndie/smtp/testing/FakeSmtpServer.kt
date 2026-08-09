package io.github.youndie.smtp.testing

/** One message as the fake server received it, with transparency already undone. */
public class ReceivedMessage(
    public val sender: String,
    public val recipients: List<String>,
    public val body: List<String>,
)

public class FakeSmtpServer(
    private val extensions: List<String> = listOf("PIPELINING", "8BITMIME"),
    private val rejectRecipients: Set<String> = emptySet(),
    private val strict: Boolean = false,
) {
    public val port: Int get() = TODO("M-34")

    public val received: List<ReceivedMessage> get() = TODO("M-34")

    public suspend fun start(): Unit = TODO("M-34")

    public suspend fun stop(): Unit = TODO("M-34")
}
