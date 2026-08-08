package io.github.youndie.smtp.transport

/**
 * A raw byte pipe: the layer TLS lives on.
 *
 * Line framing sits above this, which is what lets a handshake be inserted underneath an existing
 * conversation — the shape STARTTLS needs (docs/research/research-architecture.md, consequence 3
 * of 1.1). No foreign buffer type appears here for the same reason as in `SmtpTransport`:
 * a test fake must stay a dozen lines long.
 */
public interface ByteConnection {
    /** Reads into [destination], returning how many bytes were read, or `-1` at end of stream. */
    public suspend fun read(destination: ByteArray): Int

    /** Writes the first [length] bytes of [source] and makes sure they are on their way. */
    public suspend fun write(
        source: ByteArray,
        length: Int = source.size,
    )

    public suspend fun close()
}
