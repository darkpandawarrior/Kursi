package com.kursi.shared.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.core.prefs.AppPrefs
import com.kursi.designsystem.*

// ══════════════════════════════════════════════════════════════════════════════
// ProfileSetupScreen — shown after the primer (first run) or from Settings.
//
// The player fills out three fields:
//   1. Display name (text field, max 18 chars, defaults to "Khiladi")
//   2. Avatar emoji (12-option grid from AVATAR_ROSTER)
//   3. Seat accent color (10 swatches from KursiSeatColors)
//
// Matches the Kursi cream-certificate aesthetic (teak background, paper card).
// onDone() is called once the player taps MUHAR LAGAO (confirm stamp).
// ══════════════════════════════════════════════════════════════════════════════

/** Canonical avatar emoji roster — political/strategy theme, 12 options. */
val AVATAR_ROSTER =
    listOf(
        "🪑", // The Chair itself
        "👑", // Crown
        "⚖️", // Scales — law/vakil
        "🎯", // Precision
        "🃏", // Joker card
        "🎭", // Masks — bluff/drama
        "🏛️", // Institution
        "📜", // Official scroll
        "🔑", // Keys to power
        "🎪", // Tamasha
        "🦁", // Sher — lion
        "🦊", // Siyaar — cunning fox
    )

@Composable
fun ProfileSetupScreen(
    prefs: AppPrefs,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** True when accessed from Settings (shows "WAPAS" back button instead of "skip"). */
    fromSettings: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(prefs.playerName.ifBlank { "" }) }
    var avatarIdx by remember { mutableIntStateOf(prefs.playerAvatarIdx.coerceAtLeast(0)) }
    var colorArgb by remember {
        mutableLongStateOf(
            prefs.playerColorArgb.takeIf { it != 0L }
                ?: 0xFFE63946L, // default warm red
        )
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val nameFocusRequester = remember { FocusRequester() }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BrandTokens.TeakInk)
                .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (fromSettings && onBack != null) {
                Text(
                    text = "← WAPAS",
                    style = KursiType.label_sm.copy(letterSpacing = 0.8.sp),
                    color = BrandTokens.BrassAged,
                    modifier =
                        Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { onBack() }
                            .padding(end = 16.dp),
                )
            }
            Column {
                Text(
                    text = if (fromSettings) "APNA PROFILE" else "PEHLI HAZRI",
                    style = KursiType.label_micro.copy(letterSpacing = 1.8.sp),
                    color = BrandTokens.BrassAged.copy(alpha = 0.7f),
                )
                Text(
                    text = if (fromSettings) "Edit your identity" else "Welcome to the Darbar",
                    style = KursiType.title.copy(fontSize = 22.sp),
                    color = BrandTokens.PaperCream,
                )
            }
        }

        // ── Cream certificate card ─────────────────────────────────────────
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandTokens.PaperCream.copy(alpha = 0.97f))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ── Live preview monogram ──────────────────────────────────────
            val previewColor = Color(colorArgb)
            val previewEmoji = AVATAR_ROSTER.getOrNull(avatarIdx) ?: "🪑"
            val previewMonogram =
                name
                    .trim()
                    .take(2)
                    .uppercase()
                    .ifBlank { "KH" }

            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(previewColor)
                        .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = previewEmoji,
                    fontSize = 32.sp,
                )
            }
            Text(
                text = previewMonogram,
                style = KursiType.label_micro.copy(letterSpacing = 1.2.sp, fontSize = 11.sp),
                color = BrandTokens.TeakInk.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── FORM FIELD 1 — Display name ────────────────────────────────
            FormSection(label = "AAPKA NAAM") {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandTokens.TeakInk.copy(alpha = 0.06f))
                            .border(
                                1.dp,
                                BrandTokens.BrassDark.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp),
                            ).padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { if (it.length <= 18) name = it },
                        textStyle =
                            TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = BrandTokens.TeakInk,
                                fontFamily = KursiType.name.fontFamily,
                            ),
                        cursorBrush = SolidColor(BrandTokens.BrassDark),
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done,
                            ),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(nameFocusRequester),
                        decorationBox = { inner ->
                            if (name.isEmpty()) {
                                Text(
                                    "Khiladi",
                                    style =
                                        TextStyle(
                                            fontSize = 18.sp,
                                            color = BrandTokens.TeakInk.copy(alpha = 0.30f),
                                            fontFamily = KursiType.name.fontFamily,
                                        ),
                                )
                            }
                            inner()
                        },
                    )
                }
                Text(
                    text = "${name.length}/18",
                    style = KursiType.label_micro.copy(fontSize = 10.sp),
                    color = BrandTokens.TeakInk.copy(alpha = 0.35f),
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                )
            }

            // ── FORM FIELD 2 — Avatar emoji ────────────────────────────────
            FormSection(label = "AVATAR CHUNEIN") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                    // 2 rows × (48dp + 8dp gap)
                    userScrollEnabled = false,
                ) {
                    itemsIndexed(AVATAR_ROSTER) { idx, emoji ->
                        val selected = idx == avatarIdx
                        val borderColor by animateColorAsState(
                            if (selected) previewColor else Color.Transparent,
                            animationSpec = tween(180),
                            label = "avatarBorder",
                        )
                        Box(
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) {
                                            previewColor.copy(alpha = 0.15f)
                                        } else {
                                            BrandTokens.TeakInk.copy(alpha = 0.06f)
                                        },
                                    ).border(2.dp, borderColor, CircleShape)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { avatarIdx = idx }
                                    .semantics { contentDescription = "Avatar $emoji" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = emoji, fontSize = 22.sp)
                        }
                    }
                }
            }

            // ── FORM FIELD 3 — Seat color ──────────────────────────────────
            FormSection(label = "KURSI KA RANG") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    KursiSeatColors.all.forEach { swatch ->
                        val swatchArgb = swatch.value.toLong()
                        val selected = swatchArgb == colorArgb
                        val borderColor by animateColorAsState(
                            if (selected) BrandTokens.TeakInk else Color.Transparent,
                            animationSpec = tween(180),
                            label = "swatchBorder",
                        )
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(2.dp, borderColor, CircleShape)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { colorArgb = swatchArgb }
                                    .semantics { contentDescription = "Seat color" },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── MUHAR LAGAO — commit button ────────────────────────────────────
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandTokens.BrassDark)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        prefs.playerName = name.trim().ifBlank { "Khiladi" }
                        prefs.playerAvatarIdx = avatarIdx
                        prefs.playerColorArgb = colorArgb
                        onDone()
                    }.padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "MUHAR LAGAO ✦",
                style = KursiType.label_sm.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold),
                color = BrandTokens.PaperCream,
            )
        }

        // ── Skip link (first-run only) ─────────────────────────────────────
        if (!fromSettings) {
            Text(
                text = "BAAD MEIN KAREIN",
                style = KursiType.label_micro.copy(letterSpacing = 0.8.sp),
                color = BrandTokens.BrassAged.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            // Save defaults even on skip so hasPlayerProfile triggers only on explicit set.
                            if (prefs.playerName.isBlank()) prefs.playerName = "Khiladi"
                            if (prefs.playerAvatarIdx < 0) prefs.playerAvatarIdx = 0
                            if (prefs.playerColorArgb == 0L) prefs.playerColorArgb = 0xFFE63946L
                            onDone()
                        },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FormSection(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = KursiType.label_micro.copy(letterSpacing = 1.2.sp, fontSize = 10.sp),
            color = BrandTokens.TeakInk.copy(alpha = 0.5f),
        )
        content()
    }
}
