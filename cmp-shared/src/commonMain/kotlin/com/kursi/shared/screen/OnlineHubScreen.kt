package com.kursi.shared.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddharth.kmp.network.LanHost
import com.kursi.designsystem.*
import com.kursi.feature.game.HubPhase
import com.kursi.feature.game.LobbyState
import com.kursi.feature.game.OnlineHubUiState
import com.kursi.shared.strings.LocalKursiStrings

/**
 * S-ONLINE — ONLINE MEHFIL hub (License-Raj Deco): the front desk where a player hosts a PRIVATE
 * kamra, JOINs by code, finds a public QUICK-MATCH, or BROWSEs the LAN. A server-address field (default
 * localhost for desktop testing) sits at the top; below it the four requisition modes. When a connection
 * opens the screen flips to a WAITING ROOM (who has joined + the share code) until the match starts.
 *
 * Pure renderer over [OnlineHubUiState] — every action is a callback the app layer wires to the
 * `OnlineHubController` in `:feature:game`. The controller owns all the networking.
 */
@Composable
fun OnlineHubScreen(
    state: OnlineHubUiState,
    onBack: () -> Unit,
    onCreatePrivate: (host: String, port: Int, players: Int) -> Unit,
    onJoinByCode: (host: String, port: Int, code: String) -> Unit,
    onQuickMatch: (host: String, port: Int, players: Int) -> Unit,
    onStartLanBrowse: () -> Unit,
    onStopLanBrowse: () -> Unit,
    onJoinLanHost: (LanHost) -> Unit,
    onLeaveLobby: () -> Unit,
    modifier: Modifier = Modifier,
    initialHost: String = "localhost",
    initialPort: Int = 8080,
) {
    val s = LocalKursiStrings.current

    var serverField by remember { mutableStateOf(if (initialPort == 8080) initialHost else "$initialHost:$initialPort") }
    var joinCode by remember { mutableStateOf("") }
    var playerCount by remember { mutableIntStateOf(4) }

    fun parsedHost(): String = serverField.substringBefore(':').trim().ifEmpty { "localhost" }

    fun parsedPort(): Int = serverField.substringAfter(':', "").trim().toIntOrNull() ?: 8080

    Box(modifier = modifier.fillMaxSize().background(BrandTokens.TeakInk)) {
        Column(Modifier.fillMaxSize()) {
            OnlineHeader(title = s.onlineHubHeader, onBack = onBack)

            // The lobby (waiting room) takes over the body once a connection opens.
            if (state.phase == HubPhase.Lobby && state.lobby != null) {
                WaitingRoom(
                    lobby = state.lobby!!,
                    onLeave = onLeaveLobby,
                    modifier = Modifier.weight(1f),
                )
                return@Column
            }

            val scroll = rememberScrollState()
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scroll)
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Error banner (server down / bad code / rejected) — honest, in-identity.
                    state.error?.let { err ->
                        ErrorBanner(err)
                    }

                    // Server address (default localhost for desktop testing).
                    SectionCard(label = s.onlineHubServerLabel, sublabel = s.onlineHubServerHint) {
                        BrassField(
                            value = serverField,
                            onValueChange = { serverField = it },
                            placeholder = s.onlineHubServerHint,
                            singleLine = true,
                        )
                    }

                    // Player count (used by CREATE + QUICK-MATCH).
                    SectionCard(label = s.onlinePlayersLabel, sublabel = "⊙ $playerCount") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("2", style = KursiType.caption, color = KursiNeutrals.TextMuted)
                            Text("⊙ $playerCount", style = KursiType.title.copy(fontSize = 18.sp), color = BrandTokens.GoldAntique)
                            Text("10", style = KursiType.caption, color = KursiNeutrals.TextMuted)
                        }
                        Slider(
                            value = playerCount.toFloat(),
                            onValueChange = { playerCount = it.toInt() },
                            valueRange = 2f..10f,
                            steps = 7,
                            colors =
                                SliderDefaults.colors(
                                    thumbColor = BrandTokens.GoldAntique,
                                    activeTrackColor = BrandTokens.BrassAged,
                                    inactiveTrackColor = BrandTokens.BrassDark.copy(alpha = 0.4f),
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    val working = state.phase == HubPhase.Working
                    if (working) {
                        WorkingBanner(s.onlineWorking)
                    }

                    // ── CREATE PRIVATE ROOM (hero) ──
                    HubModeChit(
                        label = s.onlineCreateLabel,
                        sublabel = s.onlineCreateSub,
                        stamp = "MUHAR",
                        hero = true,
                        enabled = !working,
                        onClick = { onCreatePrivate(parsedHost(), parsedPort(), playerCount) },
                    )

                    // ── JOIN BY CODE ──
                    SectionCard(label = s.onlineJoinLabel, sublabel = s.onlineJoinSub) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) {
                                BrassField(
                                    value = joinCode,
                                    onValueChange = { joinCode = it.uppercase().take(8) },
                                    placeholder = s.onlineJoinCodeHint,
                                    singleLine = true,
                                    capitalize = true,
                                    monospace = true,
                                )
                            }
                            SmallStamp(
                                label = s.onlineJoinCta,
                                enabled = !working && joinCode.isNotBlank(),
                                onClick = { onJoinByCode(parsedHost(), parsedPort(), joinCode) },
                            )
                        }
                    }

                    // ── QUICK-MATCH (public) ──
                    HubModeChit(
                        label = s.onlineQuickLabel,
                        sublabel = s.onlineQuickSub,
                        stamp = "BOLI",
                        enabled = !working,
                        onClick = { onQuickMatch(parsedHost(), parsedPort(), playerCount) },
                    )

                    // ── LAN BROWSE ──
                    LanBrowseCard(
                        browsing = state.lanBrowsing,
                        hosts = state.lanHosts,
                        onBrowse = onStartLanBrowse,
                        onStop = onStopLanBrowse,
                        onJoin = onJoinLanHost,
                        enabled = !working,
                    )
                }
            }
        }
    }

    // Stop LAN discovery when this screen leaves composition (back / route change).
    DisposableEffect(Unit) {
        onDispose { onStopLanBrowse() }
    }
}

// ─────────────────────────── Waiting room ───────────────────────────

@Composable
private fun WaitingRoom(
    lobby: LobbyState,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalKursiStrings.current
    val statusText =
        when {
            lobby.isLost -> s.onlineLobbyLost
            lobby.isWaiting -> s.onlineLobbyWaiting
            else -> s.onlineLobbyConnecting
        }
    val statusColor =
        when {
            lobby.isLost -> BrandTokens.StampRed
            lobby.isWaiting -> KursiSemantics.Success
            else -> BrandTokens.PendingAmber
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                s.onlineLobbyHeader,
                style = KursiType.display.copy(fontSize = 18.sp, letterSpacing = 2.sp),
                color = BrandTokens.GoldAntique,
            )

            // Status pip + text (live region so a screen reader announces connect → waiting).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier =
                    Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = statusText
                    },
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(statusColor))
                Text(statusText, style = KursiType.title.copy(fontSize = 14.sp), color = KursiNeutrals.TextPrimary)
            }

            // Share-code plaque (only meaningful when hosting / quick-match — still legible otherwise).
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(BrandTokens.BrassDark.copy(alpha = 0.5f), BrandTokens.TeakDark.copy(alpha = 0.7f)),
                            ),
                        ).border(1.5.dp, BrandTokens.GoldAntique.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        .padding(20.dp)
                        .semantics { contentDescription = "${s.onlineLobbyShareLabel}: ${lobby.code}" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    s.onlineLobbyShareLabel,
                    style = KursiType.label.copy(fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold),
                    color = BrandTokens.BrassAged,
                )
                Text(
                    lobby.code,
                    style = KursiType.numeric.copy(fontSize = 36.sp, letterSpacing = 8.sp),
                    color = BrandTokens.GoldAntique,
                )
                Text(
                    "${lobby.host}:${lobby.port}",
                    style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                    color = KursiNeutrals.TextMuted,
                )
            }

            // Roster line — seated count + my seat confirmation.
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    s.onlineLobbyRoster(lobby.joinedSeats, lobby.seatCount),
                    style = KursiType.title.copy(fontSize = 15.sp),
                    color = KursiNeutrals.TextPrimary,
                )
                lobby.mySeat?.let { seat ->
                    Text(
                        s.onlineLobbySeated(seat),
                        style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                        color = BrandTokens.GoldAntique,
                    )
                }
            }

            // Seat chips — a small Hazri-style row of filled/empty chairs.
            if (lobby.seatCount in 2..10) {
                SeatChips(joined = lobby.joinedSeats, total = lobby.seatCount, mySeat = lobby.mySeat)
            }

            Spacer(Modifier.height(6.dp))

            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, BrandTokens.StampRed.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onLeave)
                        .semantics {
                            role = androidx.compose.ui.semantics.Role.Button
                            contentDescription = s.onlineLobbyLeave
                        }.padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(s.onlineLobbyLeave, style = KursiType.title.copy(fontSize = 13.sp), color = BrandTokens.StampRed)
            }
        }
    }
}

@Composable
private fun SeatChips(
    joined: Int,
    total: Int,
    mySeat: Int?,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.weight(1f))
        repeat(total) { i ->
            val filled = i < joined
            val isMe = mySeat == i
            Box(
                modifier =
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isMe -> BrandTokens.GoldAntique.copy(alpha = 0.85f)
                                filled -> BrandTokens.BrassAged.copy(alpha = 0.6f)
                                else -> BrandTokens.TeakDark
                            },
                        ).border(
                            1.dp,
                            if (filled || isMe) BrandTokens.GoldAntique.copy(alpha = 0.7f) else BrandTokens.BrassDark.copy(alpha = 0.4f),
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (isMe) {
                        "AAP"
                    } else if (filled) {
                        "✓"
                    } else {
                        "${i + 1}"
                    },
                    style = KursiType.caption.copy(fontSize = if (isMe) 7.sp else 10.sp, fontWeight = FontWeight.Bold),
                    color = if (filled || isMe) BrandTokens.TeakDark else KursiNeutrals.TextMuted,
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

// ─────────────────────────── LAN browse ───────────────────────────

@Composable
private fun LanBrowseCard(
    browsing: Boolean,
    hosts: List<LanHost>,
    onBrowse: () -> Unit,
    onStop: () -> Unit,
    onJoin: (LanHost) -> Unit,
    enabled: Boolean,
) {
    val s = LocalKursiStrings.current
    SectionCard(label = s.onlineLanLabel, sublabel = s.onlineLanSub) {
        SmallStamp(
            label = if (browsing) s.onlineLanSearching else s.onlineLanBrowseCta,
            enabled = enabled,
            onClick = { if (browsing) onStop() else onBrowse() },
            fillWidth = true,
        )
        if (hosts.isEmpty()) {
            if (browsing) {
                Text(
                    s.onlineLanSearching,
                    style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                    color = KursiNeutrals.TextMuted,
                )
            } else {
                Text(
                    s.onlineLanEmpty,
                    style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                    color = KursiNeutrals.TextMuted,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                hosts.forEach { h -> LanHostRow(h, onJoin = { onJoin(h) }) }
            }
        }
    }
}

@Composable
private fun LanHostRow(
    host: LanHost,
    onJoin: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(BrandTokens.BrassAged.copy(alpha = 0.10f))
                .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable(onClick = onJoin)
                .semantics(mergeDescendants = true) {
                    role = androidx.compose.ui.semantics.Role.Button
                    contentDescription = "${host.name}, ${host.host} port ${host.port}, room ${host.payload}"
                }.padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("📡", style = KursiType.title.copy(fontSize = 14.sp))
            Column(Modifier.weight(1f)) {
                Text(
                    host.name,
                    style = KursiType.name.copy(fontSize = 13.sp),
                    color = KursiNeutrals.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${host.host}:${host.port} · ${host.payload}",
                    style = KursiType.caption.copy(fontSize = 9.sp),
                    color = KursiNeutrals.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("AAO →", style = KursiType.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = BrandTokens.GoldAntique)
        }
    }
}

// ─────────────────────────── Shared primitives ───────────────────────────

@Composable
private fun OnlineHeader(
    title: String,
    onBack: () -> Unit,
) {
    val s = LocalKursiStrings.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BrandTokens.TeakDark)
                .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.4f), RoundedCornerShape(0.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            s.back,
            style = KursiType.body.copy(fontSize = 13.sp),
            color = BrandTokens.BrassAged,
            modifier = Modifier.clickable(onClick = onBack).semantics { contentDescription = s.back },
        )
        Spacer(Modifier.weight(1f))
        Text(title, style = KursiType.title.copy(fontSize = 16.sp, letterSpacing = 1.sp), color = KursiNeutrals.TextPrimary)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun SectionCard(
    label: String,
    sublabel: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BrandTokens.PaperCream.copy(alpha = 0.06f))
                .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = KursiType.label.copy(fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold), color = BrandTokens.BrassAged)
        Text(sublabel, style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic), color = KursiNeutrals.TextSecondary)
        Box(
            Modifier.fillMaxWidth().height(1.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent, BrandTokens.BrassAged.copy(alpha = 0.4f), Color.Transparent)),
            ),
        )
        content()
    }
}

@Composable
private fun BrassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    capitalize: Boolean = false,
    monospace: Boolean = false,
) {
    val textStyle =
        (if (monospace) KursiType.numeric else KursiType.body).copy(
            fontSize = if (monospace) 18.sp else 14.sp,
            color = KursiNeutrals.TextPrimary,
            letterSpacing = if (monospace) 4.sp else 0.sp,
        )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(BrandTokens.TeakInk.copy(alpha = 0.6f))
                .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(BrandTokens.GoldAntique),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = if (capitalize) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                    imeAction = ImeAction.Done,
                ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = textStyle.copy(color = KursiNeutrals.TextMuted, letterSpacing = 0.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun HubModeChit(
    label: String,
    sublabel: String,
    stamp: String,
    hero: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (hero) BrandTokens.BrassAged else Color(0xFF1E1610))
                .border(if (hero) 1.5.dp else 1.dp, if (hero) BrandTokens.GoldAntique else BrandTokens.BrassAged.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .alpha(if (enabled) 1f else 0.5f)
                .semantics(mergeDescendants = true) {
                    role = androidx.compose.ui.semantics.Role.Button
                    contentDescription = "$label. $sublabel"
                }.padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = KursiType.title.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    color = if (hero) BrandTokens.TeakDark else KursiNeutrals.TextPrimary,
                )
                Text(
                    sublabel,
                    style = KursiType.caption.copy(fontSize = 10.sp),
                    color = if (hero) BrandTokens.BrassDark else KursiNeutrals.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier =
                    Modifier
                        .padding(start = 12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (hero) BrandTokens.StampRed.copy(alpha = 0.9f) else BrandTokens.BrassAged.copy(alpha = 0.2f))
                        .border(1.dp, if (hero) BrandTokens.Oxblood else BrandTokens.GoldAntique.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    stamp,
                    style = KursiType.caption.copy(fontSize = 8.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = if (hero) KursiNeutrals.Cream else BrandTokens.GoldAntique,
                )
            }
        }
    }
}

@Composable
private fun SmallStamp(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    fillWidth: Boolean = false,
) {
    val mod =
        (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(BrandTokens.BrassAged.copy(alpha = if (enabled) 0.3f else 0.12f))
            .border(1.dp, if (enabled) BrandTokens.GoldAntique.copy(alpha = 0.7f) else BrandTokens.BrassDark.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .semantics {
                role = androidx.compose.ui.semantics.Role.Button
                contentDescription = label
            }.padding(horizontal = 18.dp, vertical = 12.dp)
    Box(mod, contentAlignment = Alignment.Center) {
        Text(label, style = KursiType.title.copy(fontSize = 13.sp), color = BrandTokens.GoldAntique, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(BrandTokens.StampRed.copy(alpha = 0.12f))
                .border(1.dp, BrandTokens.StampRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .semantics {
                    liveRegion = LiveRegionMode.Assertive
                    contentDescription = message
                }.padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(message, style = KursiType.body.copy(fontSize = 12.sp), color = BrandTokens.StampRed)
    }
}

@Composable
private fun WorkingBanner(message: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(BrandTokens.PendingAmber.copy(alpha = 0.10f))
                .border(1.dp, BrandTokens.PendingAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = message
                }.padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(message, style = KursiType.body.copy(fontSize = 12.sp, fontStyle = FontStyle.Italic), color = BrandTokens.PendingAmber)
    }
}
