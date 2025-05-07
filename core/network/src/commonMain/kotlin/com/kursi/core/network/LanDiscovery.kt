package com.kursi.core.network

import kotlinx.coroutines.flow.Flow

/**
 * A Kursi match advertised on the local network, as seen by a discovering peer.
 *
 * Carries exactly what a peer needs to JOIN: where the host is ([host]:[port]) and which room
 * ([roomCode]). [name] is a human-friendly label for a "nearby games" list. No secret state is
 * ever advertised — only the public join coordinates.
 */
data class LanHost(
    /** Resolvable host address (IPv4/IPv6 literal or mDNS hostname) of the advertising server. */
    val host: String,
    /** TCP port the Kursi WebSocket server listens on. */
    val port: Int,
    /** The room code a peer should pass to [OnlineKursiClient.connect]. */
    val roomCode: String,
    /** Human-friendly service name for display (e.g. "Sid's table"). */
    val name: String,
)

/**
 * The Kursi LAN service type / discovery namespace. Android NSD and iOS/Bonjour use the DNS-SD form;
 * the JVM UDP beacon uses the bare token as a magic header so cross-implementation discovery on one
 * subnet is at least namespaced. Bumping this is a breaking change to discovery.
 */
const val KURSI_LAN_SERVICE_TYPE: String = "_kursi._tcp"

/** The UDP port the JVM/desktop broadcast beacon advertises and listens on. */
const val KURSI_LAN_UDP_PORT: Int = 45_678

/**
 * Advertises a Kursi match on the local network so nearby peers can discover and join it.
 *
 * Platform actuals:
 *  - **android** — [android.net.nsd.NsdManager] (DNS-SD).
 *  - **ios**     — `NSNetService` / Bonjour.
 *  - **jvm**     — a real UDP-broadcast beacon ([java.net.DatagramSocket]) on [KURSI_LAN_UDP_PORT].
 *  - **wasm**    — safe no-op (browsers cannot open raw sockets); start/stop succeed and do nothing.
 *
 * Lifecycle: construct, call [start] with the match coordinates, and [stop] when the match closes or
 * the host leaves. Implementations are NOT required to be restartable after [stop]; create a new
 * instance per advertisement.
 */
expect class LanAdvertiser() {
    /**
     * Begins advertising [host]:[port] / [roomCode] under [serviceName]. Safe to call once;
     * calling again before [stop] is implementation-defined (prefer one instance per ad).
     */
    fun start(serviceName: String, roomCode: String, port: Int)

    /** Stops advertising and releases platform resources. Idempotent. */
    fun stop()
}

/**
 * Discovers Kursi matches advertised on the local network.
 *
 * [discover] returns a cold [Flow] that emits a [LanHost] each time a new host is found (de-duplicated
 * by host+port+roomCode for the lifetime of the collection). Cancelling the collector tears down the
 * platform listener. The JVM actual additionally sends a one-shot UDP "probe" so already-advertising
 * hosts answer immediately rather than only on their next beacon tick.
 *
 * Platform actuals mirror [LanAdvertiser]: android NSD resolve, iOS Bonjour browse+resolve, jvm UDP
 * listen, wasm an empty flow.
 */
expect class LanDiscoverer() {
    fun discover(): Flow<LanHost>
}
