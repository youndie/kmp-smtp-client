package io.github.youndie.smtp.transport.ktor

/**
 * Reads an environment variable.
 *
 * Only the E2E tests need this, and only to find the server from docker-compose. There is no
 * common API for it, so each platform brings its own two lines.
 */
internal expect fun environmentVariable(name: String): String?
