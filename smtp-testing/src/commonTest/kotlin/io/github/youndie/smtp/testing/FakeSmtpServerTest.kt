package io.github.youndie.smtp.testing

import io.github.youndie.smtp.client.Envelope
import io.github.youndie.smtp.client.SmtpClientConfig
import io.github.youndie.smtp.client.openSmtpSession
import io.github.youndie.smtp.protocol.Mailbox
import io.github.youndie.smtp.protocol.SmtpRefusedException
import io.github.youndie.smtp.transport.ktor.connectSmtp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The fake server, driven by the real client.
 *
 * A scripted transport proves the client writes what was expected; this proves the two halves fit
 * together over a socket — without a container, so it runs anywhere the tests run.
 */
class FakeSmtpServerTest {
    @Test
    fun `a message sent through the real client arrives`() =
        withServer { server, session ->
            val result =
                session.send(
                    envelope =
                        Envelope(
                            sender = Mailbox.parse("sender@example.com"),
                            recipients = listOf(Mailbox.parse("rcpt@example.com")),
                        ),
                    body = listOf("Subject: hi", "", "body"),
                )

            assertEquals(1, result.accepted.size)
            assertEquals(1, server.received.size)

            val message = server.received.single()
            assertEquals("sender@example.com", message.sender)
            assertEquals(listOf("rcpt@example.com"), message.recipients)
            assertEquals(listOf("Subject: hi", "", "body"), message.body)
        }

    @Test
    fun `dot-stuffing is undone by the receiver`() =
        withServer { server, session ->
            // The whole point of rfc5321.txt:3423 checked end to end: the client doubles the
            // period, the server removes it, and a line holding one period survives as content.
            session.send(
                envelope =
                    Envelope(
                        sender = Mailbox.parse("sender@example.com"),
                        recipients = listOf(Mailbox.parse("rcpt@example.com")),
                    ),
                body = listOf("before", ".", "after"),
            )

            assertEquals(listOf("before", ".", "after"), server.received.single().body)
        }

    @Test
    fun `a rejected recipient comes back as data`() =
        withServer(rejectRecipients = setOf("nobody@example.com")) { server, session ->
            val result =
                session.send(
                    envelope =
                        Envelope(
                            sender = Mailbox.parse("sender@example.com"),
                            recipients = listOf(Mailbox.parse("rcpt@example.com"), Mailbox.parse("nobody@example.com")),
                        ),
                    body = listOf("Subject: hi", "", "body"),
                )

            assertEquals(1, result.accepted.size)
            assertEquals(1, result.rejected.size)
            assertEquals(550, result.rejected.single().reply.code.value)
            assertEquals(1, server.received.size)
        }

    @Test
    fun `the server can be told to announce nothing`() =
        withServer(extensions = emptyList()) { _, session ->
            assertTrue(session.capabilities.keywords.isEmpty())
        }

    @Test
    fun `two messages travel over one connection`() =
        withServer { server, session ->
            repeat(2) {
                session.send(
                    envelope =
                        Envelope(
                            sender = Mailbox.parse("sender@example.com"),
                            recipients = listOf(Mailbox.parse("rcpt@example.com")),
                        ),
                    body = listOf("Subject: number $it", "", "body"),
                )
            }

            assertEquals(2, server.received.size)
            assertEquals("Subject: number 0", server.received.first().body.first())
            assertEquals("Subject: number 1", server.received.last().body.first())
        }

    @Test
    fun `a strict server refuses a command line over 512 octets`() =
        withServer(strict = true) { _, session ->
            // rfc5321.txt:3498. Mailpit takes such a line without complaint, which is exactly why
            // a strict fake is worth having.
            val long = "a".repeat(500)
            assertFailsWith<SmtpRefusedException> {
                session.send(
                    envelope =
                        Envelope(
                            sender = Mailbox.parse("$long@example.com"),
                            recipients = listOf(Mailbox.parse("rcpt@example.com")),
                        ),
                    body = listOf("Subject: hi", "", "body"),
                )
            }
        }

    private fun withServer(
        extensions: List<String> = listOf("PIPELINING", "8BITMIME"),
        rejectRecipients: Set<String> = emptySet(),
        strict: Boolean = false,
        block: suspend (FakeSmtpServer, io.github.youndie.smtp.client.SmtpSession) -> Unit,
    ) = runTest {
        withContext(Dispatchers.Default) {
            val server =
                FakeSmtpServer(
                    extensions = extensions,
                    rejectRecipients = rejectRecipients,
                    strict = strict,
                )
            server.start()
            try {
                val transport = connectSmtp("127.0.0.1", server.port)
                try {
                    val session =
                        openSmtpSession(
                            transport = transport,
                            config = SmtpClientConfig(clientIdentity = "client.example.com"),
                        )
                    block(server, session)
                } finally {
                    transport.close()
                }
            } finally {
                server.stop()
            }
        }
    }
}
