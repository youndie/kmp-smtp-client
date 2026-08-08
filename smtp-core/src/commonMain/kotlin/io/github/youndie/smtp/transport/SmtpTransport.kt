package io.github.youndie.smtp.transport

/**
 * A byte pipe to the server, seen from the protocol layer.
 *
 * Deliberately narrow and free of any foreign buffer type — neither Ktor's `ByteReadChannel` nor
 * kotlinx-io's `Source` appears here (see docs/research/research-architecture.md, decision D8).
 * A test fake then costs a dozen lines instead of implementing somebody else's interface.
 *
 * Splitting the incoming stream into lines belongs to the transport: the protocol layer never sees
 * bytes. Reading and writing must be usable concurrently — under PIPELINING a client that cannot
 * read replies while still writing risks a deadlock once a command group outgrows the TCP window
 * (`docs/rfc/rfc2920.txt:183`).
 */
public interface SmtpTransport {
    /**
     * Reads one line, without its trailing `CRLF`.
     *
     * Throws [SmtpTransportException] when the connection ends before a line arrives: an SMTP
     * session is closed by `QUIT`, so an EOF in the middle of one is a failure, not an end.
     */
    public suspend fun readLine(): String

    /** Writes the text as is and flushes it; the caller supplies any `CRLF` itself. */
    public suspend fun write(data: String)

    /** Closes the connection and releases everything the transport holds. */
    public suspend fun close()
}

/** The connection broke, or was never established. Not a protocol violation — see `SmtpProtocolException`. */
public class SmtpTransportException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
