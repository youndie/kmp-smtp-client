package io.github.youndie.smtp.tls.jvm

import io.github.youndie.smtp.transport.ByteConnection
import io.github.youndie.smtp.transport.TlsConfig
import io.github.youndie.smtp.transport.TlsProvider

public object SslEngineTlsProvider : TlsProvider {
    override suspend fun handshake(
        connection: ByteConnection,
        config: TlsConfig,
    ): ByteConnection = TODO("M-71")
}
