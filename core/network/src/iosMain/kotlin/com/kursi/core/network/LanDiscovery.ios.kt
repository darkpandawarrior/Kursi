package com.kursi.core.network

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSData
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.darwin.NSObject

// ─────────────────────────────────────────────────────────────────────────────
// iOS LAN discovery — NSNetService / Bonjour (the Apple DNS-SD API).
//
// The advertiser publishes an NSNetService of type "_kursi._tcp." carrying the room code as a TXT
// record. The discoverer browses for that type, then resolves each found service to obtain its
// hostname + port. Bonjour is the native, zero-config equivalent of the Android NSD path.
// ─────────────────────────────────────────────────────────────────────────────

private const val DOMAIN_LOCAL = "local."
private const val TXT_ROOM_CODE = "room"

private fun serviceTypeWithDot(): String = "$KURSI_LAN_SERVICE_TYPE."

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class LanAdvertiser actual constructor() {
    private var service: NSNetService? = null

    actual fun start(
        serviceName: String,
        roomCode: String,
        port: Int,
    ) {
        val svc =
            NSNetService(
                domain = DOMAIN_LOCAL,
                type = serviceTypeWithDot(),
                name = serviceName,
                port = port,
            )
        // Encode the room code as a Bonjour TXT record so a peer learns which room to join.
        val data: NSData? = NSString.create(string = roomCode).dataUsingEncoding(NSUTF8StringEncoding)
        if (data != null) {
            val txt = mapOf<Any?, Any?>(TXT_ROOM_CODE to data)
            svc.setTXTRecordData(NSNetService.dataFromTXTRecordDictionary(txt))
        }
        service = svc
        svc.publish()
    }

    actual fun stop() {
        service?.stop()
        service = null
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class LanDiscoverer actual constructor() {
    actual fun discover(): Flow<LanHost> =
        callbackFlow {
            // Keep strong references to in-flight resolvers + their delegates so ARC does not free them
            // mid-resolution (NSNetService holds only a weak delegate).
            val resolving = mutableListOf<NSNetService>()
            val delegates = mutableListOf<NSObject>()

            val resolveDelegate =
                object : NSObject(), NSNetServiceDelegateProtocol {
                    override fun netServiceDidResolveAddress(sender: NSNetService) {
                        val host = sender.hostName ?: return
                        val port = sender.port.toInt()
                        val roomCode =
                            sender.TXTRecordData()?.let { data ->
                                val dict = NSNetService.dictionaryFromTXTRecordData(data)
                                (dict[TXT_ROOM_CODE] as? NSData)?.let { d ->
                                    NSString.create(data = d, encoding = NSUTF8StringEncoding)?.toString()
                                }
                            } ?: ""
                        trySend(
                            LanHost(host = host, port = port, roomCode = roomCode, name = sender.name),
                        )
                    }

                    override fun netService(
                        sender: NSNetService,
                        didNotResolve: Map<Any?, *>,
                    ) {
                        resolving.remove(sender)
                    }
                }

            val browser = NSNetServiceBrowser()
            val browserDelegate =
                object : NSObject(), NSNetServiceBrowserDelegateProtocol {
                    @ObjCSignatureOverride
                    override fun netServiceBrowser(
                        browser: NSNetServiceBrowser,
                        didFindService: NSNetService,
                        moreComing: Boolean,
                    ) {
                        didFindService.delegate = resolveDelegate
                        resolving.add(didFindService)
                        didFindService.resolveWithTimeout(5.0)
                    }

                    @ObjCSignatureOverride
                    override fun netServiceBrowser(
                        browser: NSNetServiceBrowser,
                        didRemoveService: NSNetService,
                        moreComing: Boolean,
                    ) {
                        resolving.remove(didRemoveService)
                    }

                    override fun netServiceBrowser(
                        browser: NSNetServiceBrowser,
                        didNotSearch: Map<Any?, *>,
                    ) {
                        // A hard browse failure (e.g. no local network entitlement) — end the flow cleanly.
                        close()
                    }
                }
            delegates.add(browserDelegate)
            delegates.add(resolveDelegate)
            browser.delegate = browserDelegate
            browser.searchForServicesOfType(serviceTypeWithDot(), inDomain = DOMAIN_LOCAL)

            awaitClose {
                browser.stop()
                resolving.forEach { it.stop() }
                resolving.clear()
                delegates.clear()
            }
        }
}
