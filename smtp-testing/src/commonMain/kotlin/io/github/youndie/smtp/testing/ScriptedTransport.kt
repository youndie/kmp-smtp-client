package io.github.youndie.smtp.testing

import io.github.youndie.smtp.transport.SmtpTransport

/**
 * A transport that replays a written-down conversation.
 */
public class ScriptedTransport internal constructor() : SmtpTransport {
    override suspend fun readLine(): String = TODO("M-31")

    override suspend fun write(data: String): Unit = TODO("M-31")

    override suspend fun close(): Unit = TODO("M-31")

    public fun assertScriptCompleted(): Unit = TODO("M-31")

    public class Builder internal constructor() {
        public fun serverSays(vararg lines: String): Unit = TODO("M-31")

        public fun clientWrites(text: String): Unit = TODO("M-31")

        public fun serverCloses(): Unit = TODO("M-31")

        public fun serverHangs(): Unit = TODO("M-31")
    }
}

public fun scriptedTransport(block: ScriptedTransport.Builder.() -> Unit): ScriptedTransport = TODO("M-31")
