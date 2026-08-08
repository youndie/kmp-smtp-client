package io.github.youndie.smtp.transport

/** A connection that hands out prepared chunks and records everything written to it. */
internal class FakeByteConnection(
    private val chunks: List<ByteArray>,
) : ByteConnection {
    private var next = 0
    private var offsetInChunk = 0
    private val sent = mutableListOf<Byte>()

    override suspend fun read(destination: ByteArray): Int {
        if (next >= chunks.size) return -1

        val chunk = chunks[next]
        val length = minOf(destination.size, chunk.size - offsetInChunk)
        chunk.copyInto(destination, 0, offsetInChunk, offsetInChunk + length)

        offsetInChunk += length
        if (offsetInChunk == chunk.size) {
            next++
            offsetInChunk = 0
        }
        return length
    }

    override suspend fun write(
        source: ByteArray,
        length: Int,
    ) {
        repeat(length) { sent += source[it] }
    }

    override suspend fun close() = Unit

    fun written(): String = sent.toByteArray().decodeToString()
}

internal fun fakeConnection(vararg chunks: String) =
    FakeByteConnection(chunks.filter { it.isNotEmpty() }.map { it.encodeToByteArray() })

internal fun fakeConnection(vararg chunks: ByteArray) = FakeByteConnection(chunks.toList())
