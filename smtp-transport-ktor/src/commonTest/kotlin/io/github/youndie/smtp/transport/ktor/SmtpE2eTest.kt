package io.github.youndie.smtp.transport.ktor

import io.github.youndie.smtp.protocol.Capabilities
import io.github.youndie.smtp.protocol.MailData
import io.github.youndie.smtp.protocol.Mailbox
import io.github.youndie.smtp.protocol.SmtpCommand
import io.github.youndie.smtp.protocol.SmtpReply
import io.github.youndie.smtp.protocol.SmtpReplyReader
import io.github.youndie.smtp.transport.SmtpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A whole conversation with a real SMTP server from `docker-compose.yml`.
 *
 * Everything built so far meets the wire here: the reply reader, the command serialiser, the EHLO
 * parser and dot-stuffing. Unit tests answer "does the code match the RFC as I read it"; this one
 * answers "does a server agree".
 *
 * Start the server with `docker compose up -d` and run with `SMTP_E2E_HOST=127.0.0.1`.
 */
class SmtpE2eTest {
    @Test
    fun `a real server accepts a message`() =
        runTest {
            val host = e2eHostOrSkip() ?: return@runTest
            val port = environmentVariable("SMTP_E2E_PORT")?.toInt() ?: DEFAULT_PORT

            withContext(Dispatchers.Default) {
                val transport = connectSmtp(host, port)
                val session = Conversation(transport)

                try {
                    val greeting = session.read()
                    assertEquals(220, greeting.code.value, "greeting")

                    val capabilities = Capabilities.parse(session.exchange(SmtpCommand.Ehlo(CLIENT_IDENTITY)))
                    assertTrue(capabilities.greeting.isNotEmpty(), "the server names itself in its EHLO reply")

                    assertEquals(250, session.exchange(SmtpCommand.MailFrom(Mailbox.parse(SENDER))).code.value)
                    assertEquals(250, session.exchange(SmtpCommand.RcptTo(Mailbox.parse(RECIPIENT))).code.value)
                    assertEquals(354, session.exchange(SmtpCommand.Data).code.value)

                    transport.write(MailData.encode(message()))
                    val accepted = session.read()
                    assertEquals(250, accepted.code.value, "the server accepted the message")

                    assertEquals(221, session.exchange(SmtpCommand.Quit).code.value)
                } finally {
                    transport.close()
                }
            }
        }

    /**
     * The body carries a line consisting of a single period.
     *
     * That is the point of the test rather than decoration: without dot-stuffing
     * (`docs/rfc/rfc5321.txt:3423`) the message would end right there, and the server would read
     * the remaining lines as commands — so the reply to the body would not be 250.
     */
    private fun message(): List<String> =
        listOf(
            "From: <$SENDER>",
            "To: <$RECIPIENT>",
            "Subject: kmp-smtp-client end-to-end",
            "",
            "before the period",
            ".",
            "after the period",
        )

    /** Drives one connection: writes a command, collects the reply the reader assembles. */
    private class Conversation(
        private val transport: SmtpTransport,
    ) {
        private val reader = SmtpReplyReader()

        suspend fun read(): SmtpReply {
            while (true) {
                reader.feed(transport.readLine())?.let { return it }
            }
        }

        suspend fun exchange(command: SmtpCommand): SmtpReply {
            transport.write(command.encode())
            return read()
        }
    }

    private companion object {
        const val DEFAULT_PORT = 1025
        const val CLIENT_IDENTITY = "kmp-smtp-client.test"
        const val SENDER = "sender@example.com"
        const val RECIPIENT = "recipient@example.com"

        /**
         * Without a server the test cannot run, and `kotlin.test` has no way to report a skip.
         *
         * So the absence of the variable is announced rather than passed over in silence, and in
         * CI — where `SMTP_E2E_REQUIRED` is set — it is a failure: a gate that quietly disables
         * itself is indistinguishable from a gate that passes.
         */
        fun e2eHostOrSkip(): String? {
            val host = environmentVariable("SMTP_E2E_HOST")
            if (host != null) return host

            if (environmentVariable("SMTP_E2E_REQUIRED") != null) {
                fail("SMTP_E2E_REQUIRED is set but SMTP_E2E_HOST is not: the E2E server is missing")
            }

            println("SKIPPED SmtpE2eTest: SMTP_E2E_HOST is not set, start the server with `docker compose up -d`")
            return null
        }
    }
}
