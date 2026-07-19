package com.kursi.designsystem.audio

// ═══════════════════════════════════════════════════════════════════════════════
// KursiSound.kt — the finalized 17-clip CC0 SFX manifest (docs/experience-assets.md §1 / the
// "Finalized SFX manifest" table). Every clip is Kenney CC0 (public domain), OGG, bundled under
// composeResources/files/audio/. [fileName] is the path SoundPlayer resolves via Res.readBytes.
// ═══════════════════════════════════════════════════════════════════════════════

/** One resolvable SFX clip. See [SoundPlayer] for playback and docs/experience-assets.md for the beat map. */
enum class KursiSound(
    val fileName: String,
) {
    CoinSingle("coin_single.ogg"),
    CoinDouble("coin_double.ogg"),
    CoinCascade("coin_cascade.ogg"),
    CoinSwipe("coin_swipe.ogg"),
    CardDeal("card_deal.ogg"),
    CardSlide("card_slide.ogg"),
    CardPlaceHard("card_place_hard.ogg"),
    CardFan("card_fan.ogg"),
    ImpactBlade("impact_blade.ogg"),
    ImpactGavel("impact_gavel.ogg"),
    StampSlam("stamp_slam.ogg"),
    StingWin("sting_win.ogg"),
    StingTrue("sting_true.ogg"),
    StingBluff("sting_bluff.ogg"),
    UiTap("ui_tap.ogg"),
    UiConfirm("ui_confirm.ogg"),
    UiBack("ui_back.ogg"),
}
