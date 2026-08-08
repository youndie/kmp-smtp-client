package io.github.youndie.smtp.client

import io.github.youndie.smtp.protocol.Mailbox
import io.github.youndie.smtp.protocol.SmtpProtocolException
import io.github.youndie.smtp.protocol.SmtpRefusedException
import io.github.youndie.smtp.testing.ScriptedTransport
import io.github.youndie.smtp.testing.scriptedTransport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * ESMTP parameters on `MAIL FROM` and `RCPT TO`.
 *
 * The rule running through all of them: a parameter is only sent when the server announced the
 * extension. Sending one it never offered is a syntax error on its side of the wire, and the
 * message does not go out.
 */
class SendOptionsTest {
    @Test
    fun `SIZE is declared when the server offers it`() =
        runTest {
            // rfc1870.txt:84: the SIZE parameter of MAIL FROM carries the message size.
            val transport =
                scriptedTransport {
                    greeting("SIZE 35882577")
                    clientWrites("MAIL FROM:<sender@example.com> SIZE=42\r\n")
                    serverSays("250 Ok")
                    recipientAndBody()
                }

            send(transport, SendOptions(declaredSize = 42))

            transport.assertScriptCompleted()
        }

    @Test
    fun `a message larger than the server allows is refused before anything is sent`() =
        runTest {
            // rfc1870.txt:293: a 552 means the message can never be accepted, so asking is a waste
            // of a round trip when the server already published its limit.
            val transport = scriptedTransport { greeting("SIZE 100") }

            val failure =
                assertFailsWith<SmtpProtocolException> { send(transport, SendOptions(declaredSize = 1000)) }

            assertTrue(failure.message!!.contains("100"))
        }

    @Test
    fun `SIZE is not sent to a server that never offered it`() =
        runTest {
            val transport =
                scriptedTransport {
                    greeting()
                    clientWrites("MAIL FROM:<sender@example.com>\r\n")
                    serverSays("250 Ok")
                    recipientAndBody()
                }

            send(transport, SendOptions(declaredSize = 42))

            transport.assertScriptCompleted()
        }

    @Test
    fun `8BITMIME is declared as a body type`() =
        runTest {
            // rfc6152.txt: BODY=8BITMIME on MAIL FROM.
            val transport =
                scriptedTransport {
                    greeting("8BITMIME")
                    clientWrites("MAIL FROM:<sender@example.com> BODY=8BITMIME\r\n")
                    serverSays("250 Ok")
                    recipientAndBody()
                }

            send(transport, SendOptions(bodyEncoding = BodyEncoding.EIGHT_BIT))

            transport.assertScriptCompleted()
        }

    @Test
    fun `asking for 8BITMIME from a server without it is an error rather than a silent downgrade`() =
        runTest {
            // Quietly sending 8-bit content down a 7-bit path is how mail arrives corrupted.
            val transport = scriptedTransport { greeting() }

            assertFailsWith<SmtpProtocolException> {
                send(transport, SendOptions(bodyEncoding = BodyEncoding.EIGHT_BIT))
            }
        }

    @Test
    fun `SMTPUTF8 is declared when the envelope needs it`() =
        runTest {
            // rfc6531.txt: the parameter says the envelope may hold non-ASCII.
            val transport =
                scriptedTransport {
                    greeting("SMTPUTF8")
                    clientWrites("MAIL FROM:<sender@example.com> SMTPUTF8\r\n")
                    serverSays("250 Ok")
                    recipientAndBody()
                }

            send(transport, SendOptions(internationalized = true))

            transport.assertScriptCompleted()
        }

    @Test
    fun `a non-ASCII address without SMTPUTF8 support is refused`() =
        runTest {
            // rfc6531.txt: without the extension the server has no way to take such an address,
            // and guessing an encoding for it would deliver mail to somebody else.
            val transport = scriptedTransport { greeting() }

            assertFailsWith<SmtpProtocolException> {
                sendTo(transport, "почта@example.com", SendOptions())
            }
        }

    @Test
    fun `DSN parameters go on MAIL FROM and RCPT TO`() =
        runTest {
            // rfc3461.txt:1475 shows exactly this shape.
            val transport =
                scriptedTransport {
                    greeting("DSN")
                    clientWrites("MAIL FROM:<sender@example.com> RET=HDRS ENVID=QQ314159\r\n")
                    serverSays("250 Ok")
                    clientWrites("RCPT TO:<rcpt@example.com> NOTIFY=SUCCESS,FAILURE ORCPT=rfc822;rcpt@example.com\r\n")
                    serverSays("250 Ok")
                    body()
                }

            send(
                transport,
                SendOptions(
                    deliveryStatus =
                        DeliveryStatusRequest(
                            notify = setOf(DsnNotify.SUCCESS, DsnNotify.FAILURE),
                            returnFullMessage = false,
                            envelopeId = "QQ314159",
                            originalRecipient = true,
                        ),
                ),
            )

            transport.assertScriptCompleted()
        }

    @Test
    fun `NOTIFY NEVER cannot be combined with anything else`() =
        runTest {
            // rfc3461.txt:401: NEVER is exclusive; a server reading "NEVER,FAILURE" is entitled to
            // reject the whole recipient.
            assertFailsWith<IllegalArgumentException> {
                DeliveryStatusRequest(notify = setOf(DsnNotify.NEVER, DsnNotify.FAILURE))
            }
        }

    @Test
    fun `a server that refuses the size gives a transient failure that can be retried`() =
        runTest {
            // rfc1870.txt:221: 452 means "not now", 552 means "never".
            val transport =
                scriptedTransport {
                    greeting("SIZE 0")
                    clientWrites("MAIL FROM:<sender@example.com> SIZE=42\r\n")
                    serverSays("452 4.3.1 Insufficient system storage")
                }

            val failure =
                assertFailsWith<SmtpRefusedException> { send(transport, SendOptions(declaredSize = 42)) }

            assertTrue(failure.isTransient)
        }

    private suspend fun send(
        transport: ScriptedTransport,
        options: SendOptions,
    ) = sendTo(transport, "rcpt@example.com", options)

    private suspend fun sendTo(
        transport: ScriptedTransport,
        recipient: String,
        options: SendOptions,
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
                    recipients = listOf(Mailbox.parse(recipient)),
                ),
            body = listOf("Subject: hi", "", "body"),
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

        fun ScriptedTransport.Builder.recipientAndBody() {
            clientWrites("RCPT TO:<rcpt@example.com>\r\n")
            serverSays("250 Ok")
            body()
        }

        fun ScriptedTransport.Builder.body() {
            clientWrites("DATA\r\n")
            serverSays("354 Go ahead")
            clientWrites("Subject: hi\r\n\r\nbody\r\n.\r\n")
            serverSays("250 Ok")
        }
    }

    @Test
    fun `everything at once composes into one MAIL FROM`() =
        runTest {
            val transport =
                scriptedTransport {
                    greeting("SIZE 100000", "8BITMIME", "SMTPUTF8", "DSN")
                    clientWrites(
                        "MAIL FROM:<sender@example.com> SIZE=42 BODY=8BITMIME SMTPUTF8 RET=FULL\r\n",
                    )
                    serverSays("250 Ok")
                    clientWrites("RCPT TO:<rcpt@example.com> NOTIFY=FAILURE\r\n")
                    serverSays("250 Ok")
                    body()
                }

            send(
                transport,
                SendOptions(
                    declaredSize = 42,
                    bodyEncoding = BodyEncoding.EIGHT_BIT,
                    internationalized = true,
                    deliveryStatus =
                        DeliveryStatusRequest(
                            notify = setOf(DsnNotify.FAILURE),
                            returnFullMessage = true,
                        ),
                ),
            )

            transport.assertScriptCompleted()
        }

    @Test
    fun `the order of parameters is stable`() =
        runTest {
            // Not cosmetic: a scripted test compares the whole line, and an unstable order would
            // make every one of them flaky.
            val transport =
                scriptedTransport {
                    greeting("SIZE 100000", "8BITMIME")
                    clientWrites("MAIL FROM:<sender@example.com> SIZE=1 BODY=8BITMIME\r\n")
                    serverSays("250 Ok")
                    recipientAndBody()
                }

            send(transport, SendOptions(declaredSize = 1, bodyEncoding = BodyEncoding.EIGHT_BIT))

            assertEquals(true, transport.isClosed || true)
            transport.assertScriptCompleted()
        }
}
