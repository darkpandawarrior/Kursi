package com.kursi.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

// ─────────────────────────────────────────────────────────────────────────────
// wasmJs LAN discovery — a safe NO-OP.
//
// A browser sandbox cannot open raw UDP sockets or use mDNS/Bonjour, so there is no way to advertise
// or discover hosts on the local network from wasm. Rather than fail to compile (which would break the
// :core:network "compiles on ALL targets" gate) or throw at runtime, the wasm actuals are inert:
// [LanAdvertiser.start]/[stop] do nothing and [LanDiscoverer.discover] emits no hosts. A web client
// joins matches by typing a room code / connecting to a known address instead.
// ─────────────────────────────────────────────────────────────────────────────

actual class LanAdvertiser actual constructor() {
    actual fun start(serviceName: String, roomCode: String, port: Int) { /* no-op: browser sandbox */ }
    actual fun stop() { /* no-op */ }
}

actual class LanDiscoverer actual constructor() {
    actual fun discover(): Flow<LanHost> = emptyFlow()
}
