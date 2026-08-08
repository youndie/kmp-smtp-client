package io.github.youndie.smtp.transport.ktor

import io.github.youndie.smtp.transport.SmtpTransport

/**
 * The only place in the project where the word "ktor" appears
 * (see docs/research/research-architecture.md, decision D1).
 */
public class KtorTcpTransport internal constructor() : SmtpTransport {
    override suspend fun readLine(): String = TODO("M-32")

    override suspend fun write(data: String): Unit = TODO("M-32")

    override suspend fun close(): Unit = TODO("M-32")

    public companion object {
        public suspend fun connect(
            host: String,
            port: Int,
        ): KtorTcpTransport = TODO("M-32")
    }
}
