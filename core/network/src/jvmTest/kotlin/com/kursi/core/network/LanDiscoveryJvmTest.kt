package com.kursi.core.network

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM LAN discovery test: a host advertises a Kursi match via the REAL UDP-broadcast beacon and a
 * discoverer finds it on the local loopback/subnet, recovering the host's room code + port.
 *
 * This exercises the actual [java.net.DatagramSocket] beacon (no mocking) — the same code a desktop
 * host runs. It runs on loopback so it is deterministic and CI-safe.
 */
class LanDiscoveryJvmTest {
    @Test
    fun `discoverer finds an advertised host and recovers its join coordinates`() =
        runBlocking {
            val advertiser = LanAdvertiser()
            try {
                advertiser.start(serviceName = "Sid's table", roomCode = "ABCD42", port = 8080)

                // The probe-on-discover path means a live host answers quickly; allow generous slack for CI.
                val host =
                    withTimeout(15_000) {
                        LanDiscoverer().discover().first()
                    }

                assertEquals("ABCD42", host.roomCode, "discovered room code must match the advertised one")
                assertEquals(8080, host.port, "discovered port must match the advertised one")
                assertEquals("Sid's table", host.name, "discovered service name must match")
                assertTrue(host.host.isNotBlank(), "discovered host address must be resolvable/non-blank")
            } finally {
                advertiser.stop()
            }
        }

    @Test
    fun `beacon wire format round-trips room code port and name`() =
        runBlocking {
            // A second host with different coordinates must be distinguishable by the discoverer.
            val advertiser = LanAdvertiser()
            try {
                advertiser.start(serviceName = "Table-2", roomCode = "ZZ9XY1", port = 9090)
                val host = withTimeout(15_000) { LanDiscoverer().discover().first { it.roomCode == "ZZ9XY1" } }
                assertEquals(9090, host.port)
                assertEquals("Table-2", host.name)
            } finally {
                advertiser.stop()
            }
        }
}
