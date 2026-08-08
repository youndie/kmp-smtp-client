package io.github.youndie.smtp.transport

import io.github.youndie.smtp.protocol.SmtpProtocolException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Splitting a byte stream into protocol lines.
 *
 * This sits above [ByteConnection] and below the session, which is exactly where TLS has to be
 * inserted: the handshake works on bytes, and line framing must not survive it
 * (docs/research/research-architecture.md, consequence 3 of 1.1).
 */
class LineFramedTransportTest {
    @Test
    fun `lines are split on CRLF`() =
        runTest {
            val transport = LineFramedTransport(fakeConnection("220 hi\r\n250-a\r\n250 b\r\n"))

            assertEquals("220 hi", transport.readLine())
            assertEquals("250-a", transport.readLine())
            assertEquals("250 b", transport.readLine())
        }

    @Test
    fun `a line split across reads is still one line`() =
        runTest {
            // TCP has no message boundaries: a reply can arrive in as many pieces as the network
            // feels like.
            val transport = LineFramedTransport(fakeConnection("220 ", "smtp.example", ".com ESMTP\r\n"))

            assertEquals("220 smtp.example.com ESMTP", transport.readLine())
        }

    @Test
    fun `two lines arriving in one read are handed out separately`() =
        runTest {
            val transport = LineFramedTransport(fakeConnection("250-first\r\n250 second\r\n"))

            assertEquals("250-first", transport.readLine())
            assertEquals("250 second", transport.readLine())
        }

    @Test
    fun `a bare LF is accepted when reading`() =
        runTest {
            // rfc5321.txt:763 allows only CRLF, and this client never writes anything else. When
            // reading, a lone LF is tolerated: sloppy servers exist, and refusing to parse their
            // replies helps nobody.
            val transport = LineFramedTransport(fakeConnection("220 hi\n250 ok\n"))

            assertEquals("220 hi", transport.readLine())
            assertEquals("250 ok", transport.readLine())
        }

    @Test
    fun `non-ASCII survives the round trip`() =
        runTest {
            // SMTPUTF8 (rfc6531.txt) puts UTF-8 into reply text, and a multi-byte character may be
            // cut in half between two reads.
            val bytes = "250 ответ\r\n".encodeToByteArray()
            val transport =
                LineFramedTransport(
                    fakeConnection(bytes.copyOfRange(0, 7), bytes.copyOfRange(7, bytes.size)),
                )

            assertEquals("250 ответ", transport.readLine())
        }

    @Test
    fun `an unterminated line at end of stream is a transport failure`() =
        runTest {
            val transport = LineFramedTransport(fakeConnection("220 hi"))

            assertFailsWith<SmtpTransportException> { transport.readLine() }
        }

    @Test
    fun `a server that never ends a line is cut off`() =
        runTest {
            // Without a cap a hostile server exhausts memory before the protocol layer ever gets
            // to enforce the 512-octet limit of rfc5321.txt:3504.
            val transport =
                LineFramedTransport(fakeConnection("x".repeat(9000)), maxLineOctets = 4096)

            val failure = assertFailsWith<SmtpTransportException> { transport.readLine() }
            assertTrue(failure.message!!.contains("4096"))
        }

    @Test
    fun `what is written reaches the connection as UTF-8`() =
        runTest {
            val connection = fakeConnection("")
            val transport = LineFramedTransport(connection)

            transport.write("EHLO client\r\n")

            assertEquals("EHLO client\r\n", connection.written())
        }

    @Test
    fun `upgrading swaps the connection underneath`() =
        runTest {
            val plain = fakeConnection("220 plain\r\n")
            val secured = fakeConnection("250 secured\r\n")
            val transport = LineFramedTransport(plain)

            assertEquals("220 plain", transport.readLine())
            transport.upgrade { secured }

            assertEquals("250 secured", transport.readLine())
        }

    @Test
    fun `bytes buffered before the handshake are refused`() =
        runTest {
            // The point of the check: a server (or a man in the middle) that sends data right
            // after "220 ready to start TLS" gets it treated as if it had arrived encrypted. That
            // is command injection across the handshake, and it has bitten real implementations.
            val plain = fakeConnection("220 ready\r\n250 injected\r\n")
            val transport = LineFramedTransport(plain)

            assertEquals("220 ready", transport.readLine())

            assertFailsWith<SmtpProtocolException> { transport.upgrade { fakeConnection("") } }
        }
}
