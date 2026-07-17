package com.kursi.server

import com.kursi.protocol.wire.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.UUID

/** Wire DTO for the /standings endpoint. Sorted best-first by position. */
@Serializable
data class StandingDto(
    val position: Int,
    val name: String,
    val rating: Int,
)

/** Demo ladder — replaced by a persistent store once ratings are tracked. */
private val demoStandings: List<StandingDto> =
    listOf(
        StandingDto(1, "Pappu Bhai", 2_850),
        StandingDto(2, "Chhota Don", 2_740),
        StandingDto(3, "Sarkari Babu", 2_680),
        StandingDto(4, "Gali ka Neta", 2_610),
        StandingDto(5, "Chamcha No. 1", 2_540),
        StandingDto(6, "Afwaah Queen", 2_470),
        StandingDto(7, "Sting Operator", 2_390),
        StandingDto(8, "Badla Bhai", 2_310),
        StandingDto(9, "Alliance Uncle", 2_230),
        StandingDto(10, "Jugaadu Ji", 2_150),
    )

/**
 * All routes: WebSocket `/play` game endpoint + HTTP `/health` liveness probe.
 */
fun Application.configureRouting(registry: RoomRegistry) {
    routing {
        // ── Liveness probe ──────────────────────────────────────────────────
        get("/health") {
            call.respondText("OK")
        }

        // ── Build info ───────────────────────────────────────────────────────
        get("/version") {
            call.respondText(BuildInfo.FINGERPRINT)
        }

        // ── Online leaderboard ──────────────────────────────────────────────
        get("/standings") {
            call.respond(demoStandings)
        }

        // ── Main game WebSocket endpoint ────────────────────────────────────
        webSocket("/play") {
            val connectionId = UUID.randomUUID().toString()

            // 1. Read the first frame — must be a JoinRoom ClientMessage
            val firstText =
                (incoming.receive() as? Frame.Text)?.readText()
                    ?: run {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Expected JoinRoom"))
                        return@webSocket
                    }

            val joinMsg: ClientMessage =
                try {
                    KursiJson.decodeFromString(firstText)
                } catch (e: Exception) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid ClientMessage: ${e.message}"))
                    return@webSocket
                }

            if (joinMsg !is ClientMessage.JoinRoom) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "First message must be JoinRoom"))
                return@webSocket
            }

            // 2. Find or create the room
            val roomCode = joinMsg.roomCode.uppercase()
            val actor =
                registry.findRoom(roomCode)
                    ?: run {
                        // Auto-create a 2-player room if the code doesn't exist (for simple discovery)
                        // In a real deployment the client would call a REST endpoint to create rooms first.
                        // For the test harness and demo, we auto-create if the code is the matchId literal.
                        registry.createRoom(2).let { newCode ->
                            // The caller used a specific code; we can't honor it after creation since our
                            // registry generates its own codes. Return an error.
                            val errMsg: ServerMessage =
                                ServerMessage.Error(
                                    matchId = roomCode,
                                    seq = 0,
                                    clientSeq = -1,
                                    reason = "Room '$roomCode' not found. Create a room first.",
                                )
                            send(Frame.Text(KursiJson.encodeToString(errMsg)))
                            close(CloseReason(CloseReason.Codes.NORMAL, "Room not found"))
                            return@webSocket
                        }
                    }

            // 3. Register this connection with the match actor (honouring a reconnect-seat request)
            val replyDeferred = CompletableDeferred<PlayerJoinedResult>()
            actor.post(
                MatchCommand.PlayerJoined(
                    connectionId = connectionId,
                    session = this,
                    reconnectSeat = joinMsg.reconnectSeat,
                    replyChannel = replyDeferred,
                ),
            )

            val joinResult =
                try {
                    replyDeferred.await()
                } catch (e: Exception) {
                    val errMsg: ServerMessage =
                        ServerMessage.Error(
                            matchId = roomCode,
                            seq = 0,
                            clientSeq = -1,
                            reason = "Could not join: ${e.message}",
                        )
                    send(Frame.Text(KursiJson.encodeToString(errMsg)))
                    close(CloseReason(CloseReason.Codes.NORMAL, "Join failed"))
                    return@webSocket
                }

            // 4. RoomJoined is sent by the actor itself (in the serial actor loop) so it always
            //    precedes any StateUpdate — see MatchActor.handleJoin. Here we only act on metadata.
            //
            // Once a quick-match room is full, retire it from the open queue so the next
            // quick-match request opens a fresh room rather than landing in a started game.
            if (joinResult.playerCount >= actor.seatCount) {
                registry.markFilled(roomCode)
            }

            // 5. Message loop: read intents and forward to the actor
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val text = frame.readText()
                    val msg: ClientMessage =
                        try {
                            KursiJson.decodeFromString(text)
                        } catch (e: Exception) {
                            val errMsg: ServerMessage =
                                ServerMessage.Error(
                                    matchId = roomCode,
                                    seq = -1,
                                    clientSeq = -1,
                                    reason = "Invalid message: ${e.message}",
                                )
                            send(Frame.Text(KursiJson.encodeToString(errMsg)))
                            continue
                        }

                    when (msg) {
                        is ClientMessage.SubmitIntent -> {
                            actor.post(
                                MatchCommand.IntentSubmitted(
                                    connectionId = connectionId,
                                    clientSeq = msg.seq,
                                    wireIntent = msg.intent,
                                ),
                            )
                        }
                        is ClientMessage.Pass -> {
                            // The Pass fast-path: look up the seat's current state to determine
                            // which Pass intent to submit. We encode it as a WireIntent.Pass for
                            // the seat assigned on join.
                            val wirePass = WireIntent.Pass(actor = joinResult.seat)
                            actor.post(
                                MatchCommand.IntentSubmitted(
                                    connectionId = connectionId,
                                    clientSeq = msg.seq,
                                    wireIntent = wirePass,
                                ),
                            )
                        }
                        is ClientMessage.JoinRoom -> {
                            // Ignore subsequent JoinRoom frames
                        }
                    }
                }
            } finally {
                // 6. Notify the actor the connection dropped
                actor.post(MatchCommand.PlayerLeft(connectionId))
            }
        }

        // ── Room creation REST endpoint ─────────────────────────────────────
        post("/rooms/{playerCount}") {
            val playerCount =
                call.parameters["playerCount"]?.toIntOrNull()
                    ?: run {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, "Invalid player count")
                        return@post
                    }
            if (playerCount !in 2..10) {
                call.respond(io.ktor.http.HttpStatusCode.BadRequest, "Player count must be 2..10")
                return@post
            }
            val code = registry.createRoom(playerCount)
            call.respondText(code)
        }

        // ── Public quick-match endpoint ─────────────────────────────────────
        // Returns the code of a WAITING public room of the requested size (creating one if none is
        // open), so two callers asking for the same size are paired into the same room. The client
        // then connects to /play with this code exactly as for a private room.
        post("/quickmatch/{playerCount}") {
            val playerCount =
                call.parameters["playerCount"]?.toIntOrNull()
                    ?: run {
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest, "Invalid player count")
                        return@post
                    }
            if (playerCount !in 2..10) {
                call.respond(io.ktor.http.HttpStatusCode.BadRequest, "Player count must be 2..10")
                return@post
            }
            val code = registry.quickMatch(playerCount)
            call.respondText(code)
        }
    }
}
