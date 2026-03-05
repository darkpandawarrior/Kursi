package com.kursi.designsystem.moment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

// ═══════════════════════════════════════════════════════════════════════════════
// TableAnchorRegistry.kt — MEASURED table geometry for the moment stamp-theatre.
//
// The proportional-ellipse fallback in feature:game placed coin-trails / stamps /
// turn-handoffs at GUESSED seat positions that did not match where the plates,
// hand and medallion actually landed on screen. This registry lets the real game
// layout REPORT each element's measured on-screen bounds (in root/window coords),
// keyed by a stable [AnchorKey]. The overlay then resolves measured offsets into
// its OWN local coordinate space (by subtracting the overlay's measured origin),
// so every moment fires at the REAL seat / hand / treasury — not an ellipse guess.
//
// Coordinate contract:
//   • Every reported Rect is captured via boundsInRoot() → window/root space.
//   • The overlay also reports its own boundsInRoot() as [overlayBounds].
//   • localOf(key) = measuredRootCenter(key) − overlayBounds.topLeft, giving an
//     Offset in the overlay's fillMaxSize() coordinate space (what the beats want).
//
// Fallback: until a key is measured (first frame, off-screen render harness, or a
// just-mounted plate), localOf() returns null and the caller uses the proportional
// ellipse anchor for that slot. Mixed measured/fallback is fine — keys resolve
// independently.
// ═══════════════════════════════════════════════════════════════════════════════

/** A stable key identifying one measurable table element. */
sealed interface AnchorKey {
    /** An opponent plate or the human's own seat plate, keyed by SeatId. */
    data class Seat(val seat: SeatId) : AnchorKey
    /** The human player's hand (where their cards rest). */
    object Hand : AnchorKey
    /** The central deck / treasury medallion — the table heart. */
    object Treasury : AnchorKey
}

/**
 * Mutable, recomposition-aware store of measured element bounds in ROOT coordinates.
 * Lives for the duration of one game screen; provided via [LocalTableAnchorRegistry].
 *
 * Writes come from [Modifier.reportAnchor]; reads come from the overlay through
 * [measuredAnchors]. State-backed so a late measurement re-triggers anchor rebuild.
 */
@Stable
class TableAnchorRegistry {
    /** key → bounds in root/window space. */
    private val bounds = mutableStateMapOf<AnchorKey, Rect>()

    /** The overlay's own bounds in root space — the frame everything is rebased against. */
    var overlayBounds: Rect? by mutableStateOf(null)
        private set

    fun report(key: AnchorKey, rect: Rect) { bounds[key] = rect }
    fun reportOverlay(rect: Rect) { overlayBounds = rect }

    /** Root-space center of [key], or null if not yet measured. */
    fun rootCenterOf(key: AnchorKey): Offset? = bounds[key]?.center

    /**
     * Builds MEASURED [TableAnchors] in the overlay's LOCAL coordinate space for all
     * [seatCount] seats plus the treasury, using [fallback] for any slot that has not
     * been measured yet. Returns null only when the overlay itself is unmeasured (the
     * caller then uses the full proportional fallback).
     */
    fun measuredAnchors(seatCount: Int, fallback: TableAnchors): TableAnchors? {
        val origin = overlayBounds?.topLeft ?: return null

        val seatCenters = HashMap<SeatId, Offset>(seatCount)
        for (seat in 0 until seatCount) {
            val measured = bounds[AnchorKey.Seat(seat)]?.center
            seatCenters[seat] = if (measured != null) measured - origin
                                else fallback.seat(seat)
        }
        val treasury = bounds[AnchorKey.Treasury]?.center?.minus(origin)
            ?: fallback.treasuryCenter
        // Off-table entry: rebase the human hand if we have it, else keep fallback edge.
        val handLocal = bounds[AnchorKey.Hand]?.center?.minus(origin)

        return TableAnchors(
            seatCenters = seatCenters,
            treasuryCenter = treasury,
            offTableEntry = handLocal ?: fallback.offTableEntry,
        )
    }
}

/**
 * CompositionLocal carrying the live [TableAnchorRegistry]. Defaults to a fresh,
 * never-written registry so reads outside a game screen are safe (they just yield
 * the proportional fallback). The game screen overrides it with a remembered instance.
 */
val LocalTableAnchorRegistry = compositionLocalOf { TableAnchorRegistry() }

/**
 * Reports this composable's measured root bounds into the ambient [TableAnchorRegistry]
 * under [key]. Attach to each opponent plate, the human hand, and the treasury medallion.
 * No-op cost beyond the existing layout pass; updates only when bounds actually change.
 */
@Composable
fun Modifier.reportAnchor(key: AnchorKey): Modifier {
    val registry = LocalTableAnchorRegistry.current
    return this.onGloballyPositioned { coords ->
        registry.report(key, coords.boundsInRoot())
    }
}

/** Reports the overlay's own root bounds — the rebase frame for all measured anchors. */
@Composable
fun Modifier.reportOverlayBounds(): Modifier {
    val registry = LocalTableAnchorRegistry.current
    return this.onGloballyPositioned { coords ->
        registry.reportOverlay(coords.boundsInRoot())
    }
}
