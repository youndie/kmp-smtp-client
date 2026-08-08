package io.github.youndie.smtp.transport.ktor

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
internal actual fun environmentVariable(name: String): String? = getenv(name)?.toKString()
