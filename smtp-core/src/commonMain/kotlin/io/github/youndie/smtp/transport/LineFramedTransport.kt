package io.github.youndie.smtp.transport

import io.github.youndie.smtp.protocol.SmtpProtocolException

/**
 * Turns a byte pipe into the line-based [SmtpTransport] the session speaks.
 *
 * Everything about TLS that this class knows is [upgrade]: the connection underneath can be
 * replaced, and no byte may cross that boundary.
 *
 * @param maxLineOctets how long a single line may grow before the server is cut off. The protocol
 *   caps a reply line at 512 octets (`docs/rfc/rfc5321.txt:3504`), but that check happens a layer
 *   up — by then a hostile server sending no `CRLF` at all would already have exhausted memory.
 */
public class LineFramedTransport(
    connection: ByteConnection,
    private val maxLineOctets: Int = DEFAULT_MAX_LINE_OCTETS,
) : SmtpTransport {
    private var connection: ByteConnection = connection
    private var pendingBytes = ByteArray(0)
    private val readBuffer = ByteArray(DEFAULT_READ_BUFFER)

    override suspend fun readLine(): String {
        while (true) {
            extractLine()?.let { return it }

            if (pendingBytes.size > maxLineOctets) {
                throw SmtpTransportException(
                    "The server sent more than $maxLineOctets octets without ending a line",
                )
            }

            val read = connection.read(readBuffer)
            if (read < 0) {
                throw SmtpTransportException("Connection closed in the middle of a line")
            }
            pendingBytes += readBuffer.copyOf(read)
        }
    }

    override suspend fun write(data: String) {
        val bytes = data.encodeToByteArray()
        connection.write(bytes, bytes.size)
    }

    override suspend fun close() {
        connection.close()
    }

    /**
     * Replaces the connection underneath — the handshake of `STARTTLS`.
     *
     * Refuses to run while bytes are still buffered. A server, or somebody sitting between,
     * that sends data immediately after `220 Ready to start TLS` would otherwise have it read as
     * though it had arrived inside the encrypted session; that is command injection across the
     * handshake, and it is why `docs/rfc/rfc3207.txt:210` insists everything learned earlier is
     * discarded.
     */
    public suspend fun upgrade(handshake: suspend (ByteConnection) -> ByteConnection) {
        if (pendingBytes.isNotEmpty()) {
            throw SmtpProtocolException(
                "The server sent ${pendingBytes.size} octet(s) before the TLS handshake; " +
                    "they cannot be trusted and the session must not continue",
            )
        }

        connection = handshake(connection)
    }

    /**
     * Takes one line out of what has been read so far.
     *
     * Decoding happens per line rather than per read, because a UTF-8 character can be split
     * across two reads and half a character decodes to a replacement mark that never comes back.
     */
    private fun extractLine(): String? {
        val end = pendingBytes.indexOfFirst { it == LF }
        if (end < 0) return null

        var lineEnd = end
        if (lineEnd > 0 && pendingBytes[lineEnd - 1] == CR) lineEnd--

        val line = pendingBytes.decodeToString(0, lineEnd)
        pendingBytes = pendingBytes.copyOfRange(end + 1, pendingBytes.size)
        return line
    }

    private companion object {
        const val DEFAULT_MAX_LINE_OCTETS = 8192
        const val DEFAULT_READ_BUFFER = 4096
        const val CR: Byte = 13
        const val LF: Byte = 10
    }
}
