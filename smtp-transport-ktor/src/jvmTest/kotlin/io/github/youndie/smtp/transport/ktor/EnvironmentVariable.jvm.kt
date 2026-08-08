package io.github.youndie.smtp.transport.ktor

internal actual fun environmentVariable(name: String): String? = System.getenv(name)
