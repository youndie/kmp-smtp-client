package io.github.youndie.smtp.client

import io.github.youndie.smtp.protocol.Mailbox
import io.github.youndie.smtp.testing.ScriptedTransport
import io.github.youndie.smtp.testing.scriptedTransport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `PIPELINING` — `docs/rfc/rfc2920.txt` — and `CHUNKING` — `docs/rfc/rfc3030.txt`.
 *
 * Both change what goes on the wire rather than what the caller asks for, so the tests compare
 * whole writes.
 */
class PipeliningTest {
    @Test
    fun `commands travel in one group when the server offers PIPELINING`() =
        runTest {
            // rfc2920.txt:137: RSET, MAIL FROM and RCPT TO may sit anywhere in a group; DATA has to
            // be last, which is exactly where it is.
            val transport =
                scriptedTransport {
                    greeting("PIPELINING")
                    clientWrites(
                        "MAIL FROM:<sender@example.com>\r\n" +
                            "RCPT TO:<first@example.com>\r\n" +
                            "RCPT TO:<second@example.com>\r\n" +
                            "DATA\r\n",
                    )
                    serverSays("250 Ok", "250 Ok", "250 Ok", "354 Go ahead")
                    clientWrites("Subject: hi\r\n\r\nbody\r\n.\r\n")
                    serverSays("250 Ok")
                }

            val result = send(transport, listOf("first@example.com", "second@example.com"))

            assertEquals(2, result.accepted.size)
            transport.assertScriptCompleted()
        }

    @Test
    fun `replies are matched to commands by counting`() =
        runTest {
            // rfc2920.txt:177: matching on the reply code or its text is expressly forbidden. Here
            // both recipients get 250 and the middle one is the rejection, so only the count tells
            // them apart.
            val transport =
                scriptedTransport {
                    greeting("PIPELINING")
                    clientWrites(
                        "MAIL FROM:<sender@example.com>\r\n" +
                            "RCPT TO:<first@example.com>\r\n" +
                            "RCPT TO:<second@example.com>\r\n" +
                            "DATA\r\n",
                    )
                    serverSays("250 Ok", "250 Ok", "550 No such user", "354 Go ahead")
                    clientWrites("Subject: hi\r\n\r\nbody\r\n.\r\n")
                    serverSays("250 Ok")
                }

            val result = send(transport, listOf("first@example.com", "second@example.com"))

            assertEquals(listOf(Mailbox.parse("first@example.com")), result.accepted)
            assertEquals(Mailbox.parse("second@example.com"), result.rejected.single().mailbox)
        }

    @Test
    fun `a dot still follows an accepted DATA even when every recipient was refused`() =
        runTest {
            // rfc2920.txt:160: "the client cannot assume that the DATA command will be rejected
            // just because none of the RCPT TO commands worked". Skipping the dot leaves the
            // server waiting for a message that never ends.
            val transport =
                scriptedTransport {
                    greeting("PIPELINING")
                    clientWrites(
                        "MAIL FROM:<sender@example.com>\r\n" +
                            "RCPT TO:<nobody@example.com>\r\n" +
                            "DATA\r\n",
                    )
                    serverSays("250 Ok", "550 No such user", "354 Go ahead")
                    clientWrites(".\r\n")
                    serverSays("554 5.5.1 No valid recipients")
                }

            val result = send(transport, listOf("nobody@example.com"))

            assertEquals(emptyList(), result.accepted)
            assertNull(result.acceptance, "nothing was delivered")
            transport.assertScriptCompleted()
        }

    @Test
    fun `a refused DATA after refused recipients needs no dot`() =
        runTest {
            val transport =
                scriptedTransport {
                    greeting("PIPELINING")
                    clientWrites(
                        "MAIL FROM:<sender@example.com>\r\n" +
                            "RCPT TO:<nobody@example.com>\r\n" +
                            "DATA\r\n",
                    )
                    serverSays("250 Ok", "550 No such user", "554 5.5.1 No valid recipients")
                }

            val result = send(transport, listOf("nobody@example.com"))

            assertEquals(emptyList(), result.accepted)
            transport.assertScriptCompleted()
        }

    @Test
    fun `pipelining can be turned off even where the server offers it`() =
        runTest {
            val transport =
                scriptedTransport {
                    greeting("PIPELINING")
                    clientWrites("MAIL FROM:<sender@example.com>\r\n")
                    serverSays("250 Ok")
                    clientWrites("RCPT TO:<first@example.com>\r\n")
                    serverSays("250 Ok")
                    clientWrites("DATA\r\n")
                    serverSays("354 Go ahead")
                    clientWrites("Subject: hi\r\n\r\nbody\r\n.\r\n")
                    serverSays("250 Ok")
                }

            send(transport, listOf("first@example.com"), SendOptions(pipelining = false))

            transport.assertScriptCompleted()
        }

    @Test
    fun `CHUNKING sends the body with BDAT and no dot at all`() =
        runTest {
            // rfc3030.txt:140: bdat-cmd = "BDAT" SP chunk-size [ SP end-marker ] CR LF.
            // The size counts octets, and the dot protocol does not apply — so no stuffing and no
            // terminating dot. Getting that wrong doubles a period inside the delivered message.
            val transport =
                scriptedTransport {
                    greeting("CHUNKING")
                    clientWrites("MAIL FROM:<sender@example.com>\r\n")
                    serverSays("250 Ok")
                    clientWrites("RCPT TO:<first@example.com>\r\n")
                    serverSays("250 Ok")
                    clientWrites("BDAT 22 LAST\r\n")
                    clientWrites("Subject: hi\r\n\r\n.body\r\n")
                    serverSays("250 Ok")
                }

            val result =
                send(
                    transport,
                    listOf("first@example.com"),
                    SendOptions(chunking = true, pipelining = false),
                    body = listOf("Subject: hi", "", ".body"),
                )

            assertEquals(250, result.acceptance?.code?.value)
            transport.assertScriptCompleted()
        }

    private suspend fun send(
        transport: ScriptedTransport,
        recipients: List<String>,
        options: SendOptions = SendOptions(),
        body: List<String> = listOf("Subject: hi", "", "body"),
    ): DeliveryResult {
        val session =
            openSmtpSession(
                transport = transport,
                config = SmtpClientConfig(clientIdentity = "client.example.com"),
            )
        return session.send(
            envelope =
                Envelope(
                    sender = Mailbox.parse("sender@example.com"),
                    recipients = recipients.map(Mailbox::parse),
                ),
            body = body,
            options = options,
        )
    }

    private companion object {
        fun ScriptedTransport.Builder.greeting(vararg extensions: String) {
            serverSays("220 smtp.example.com")
            clientWrites("EHLO client.example.com\r\n")
            val lines = listOf("smtp.example.com") + extensions.toList()
            serverSays(
                *lines
                    .mapIndexed { index, text ->
                        if (index == lines.lastIndex) "250 $text" else "250-$text"
                    }.toTypedArray(),
            )
        }
    }
}
