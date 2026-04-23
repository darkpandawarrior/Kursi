package com.kursi.server

import io.ktor.server.engine.*
import io.ktor.server.netty.*

/**
 * Entrypoint. Starts Ktor on Netty at port 8080 (overridable via PORT env var).
 *
 * Not called during tests — [testApplication] bypasses this entirely.
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = { module() }).start(wait = true)
}
