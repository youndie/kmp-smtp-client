package io.github.youndie.smtp.testing

import io.github.youndie.smtp.transport.SmtpTransportException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The scripted transport is a test tool, so its own tests cite no RFC: there is nothing to cite.
 * What they do check is that a mismatch is loud — a fake that quietly tolerates the wrong command
 * makes every test above it worthless.
 */
class ScriptedTransportTest {
    @Test
    fun `server lines are handed out one at a time`() =
        runTest {
            val transport =
                scriptedTransport {
                    serverSays("220 smtp.example.com ESMTP", "250-PIPELINING", "250 SIZE 100")
                }

            assertEquals("220 smtp.example.com ESMTP", transport.readLine())
            assertEquals("250-PIPELINING", transport.readLine())
            assertEquals("250 SIZE 100", transport.readLine())
        }

    @Test
    fun `what the client writes is checked against the script`() =
        runTest {
            val transport =
                scriptedTransport {
                    clientWrites("EHLO client.example.com\r\n")
                    serverSays("250 smtp.example.com")
                }

            transport.write("EHLO client.example.com\r\n")
            assertEquals("250 smtp.example.com", transport.readLine())
            transport.assertScriptCompleted()
        }

    @Test
    fun `a client writing the wrong thing fails the test`() =
        runTest {
            val transport = scriptedTransport { clientWrites("QUIT\r\n") }

            assertFailsWith<AssertionError> { transport.write("RSET\r\n") }
        }

    @Test
    fun `reading when the script expects a write fails the test`() =
        runTest {
            val transport = scriptedTransport { clientWrites("QUIT\r\n") }

            assertFailsWith<AssertionError> { transport.readLine() }
        }

    @Test
    fun `a script left unfinished fails the test`() =
        runTest {
            val transport =
                scriptedTransport {
                    serverSays("220 ready")
                    clientWrites("QUIT\r\n")
                }

            assertEquals("220 ready", transport.readLine())
            assertFailsWith<AssertionError> { transport.assertScriptCompleted() }
        }

    @Test
    fun `a server that hangs up is reported as a transport failure`() =
        runTest {
            val transport = scriptedTransport { serverCloses() }

            assertFailsWith<SmtpTransportException> { transport.readLine() }
        }

    @Test
    fun `a server that never answers suspends forever`() =
        runTest {
            val transport = scriptedTransport { serverHangs() }

            // Virtual time makes this instant, and a timeout around it is what M-24 tests.
            assertFailsWith<kotlinx.coroutines.TimeoutCancellationException> {
                kotlinx.coroutines.withTimeout(1_000) { transport.readLine() }
            }
        }
}
