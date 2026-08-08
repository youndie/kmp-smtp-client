package io.github.youndie.smtp.transport

public class LineFramedTransport(
    connection: ByteConnection,
    private val maxLineOctets: Int = 8192,
) : SmtpTransport {
    override suspend fun readLine(): String = TODO("M-40")

    override suspend fun write(data: String): Unit = TODO("M-40")

    override suspend fun close(): Unit = TODO("M-40")

    public suspend fun upgrade(handshake: suspend (ByteConnection) -> ByteConnection): Unit = TODO("M-40")
}
