# Kursi Launch Overhaul — Progress / Resumption Note

**Branch:** `feat/launch-overhaul` (all work here; not yet merged to `main`).
**Spec:** [specs/2026-07-18-kursi-launch-overhaul-design.md](specs/2026-07-18-kursi-launch-overhaul-design.md) (rev 3). **Plans:** `plans/2026-07-18-*`.
**Last certified:** full gate green (`:feature:game:jvmTest :core:designsystem:jvmTest :core:prefs:jvmTest testAndroidHostTest detekt ktlintCheck :cmp-desktop:renderScreens :cmp-android:compileNoGmsDebugKotlin`), replay tests pass, screenshots byte-unchanged.

## Done ✅

**Wave 0 (foundation, certified):**
- `DensityLayer` FOCUS/GUIDED/ANALYST axis (ANALYST default) — prefs → app → ViewModel.
- Beat-gate state machine — `BeatTier`/`PendingBeat`/`tierFor`, `awaitBeat`, `GameAction.ContinueBeat`.
- `KursiMotion` spring/duration tokens; compose string-resource scaffold.
- `GameScreen.kt` decomposition 5,522→1,021 lines (`board/ docks/ overlays/ coach/ status/ GamePhase.kt`); `GameViewModel` auto-play → `session/AutoPlayer.kt`.

**Wave 1 (in progress):**
- FOCUS mode real — surfaces gate on `densityLayer`; clean board in FOCUS, coach in GUIDED, ANALYST byte-identical. `overlays/BeatHeadline.kt` (AI-swappable Munshi seam), `overlays/BeatGatePrompt.kt` (tap-to-continue, a11y + reduced-motion).
- Guided funnel — interactive mechanic-at-a-time tutorial + `DensityLayer` graduation policy + first-run routing (Boot→Primer→funnel→FOCUS, gated on prefs `seenFunnel`).
- Art pipeline — `core/designsystem` composeResources asset track + `art/KursiArt.kt` `resolveArt()` fallback to Canvas glyphs (inert until a slot is added to `readySlots` + real file dropped). QA gate: `docs/art/STYLE_LOCK.md`.

## Next ⏳ (parallel agents were blocked on a session limit; resume after reset)

1. **Online track (REDO)** — server-side beat-ack timeout in `server/` + `shared-protocol/` (a human's tap-to-continue must not stall online matches; bounded timeout → auto-continue). Prior agent committed nothing.
2. **Renderer track** — `feature/game` board depth + `core/designsystem` shaders (AGSL/Skiko expect/actual; **spike per-target first** — bleeding-edge). Refs in `docs/resources.md` (ShaderX/Cloudy/Shady). Serialize in main checkout (touches `feature/game`).
3. **Intelligence track** — Munshi narrator into `overlays/BeatHeadline.kt` `headlineFor()` seam (grounded on engine state); coach/debrief; provider matrix (on-device + BYOK + templated fallback). `ai/` parts parallelizable; wiring serial.
4. **Emulator run** — the run-verification gap: launch app, watch FOCUS mode + funnel + tap-to-continue actually play (compile/test/render green ≠ runs).
5. Then: a11y sweep, platform polish, docs/screenshots/GIF regen (Wave 1 phases 6–8 in spec §12).

## Orchestration gotchas learned

- **Hardened gate must compile the shells** — `:cmp-android:compileNoGmsDebugKotlin` catches DI/wiring/import breakage that module tests miss.
- **Worktree base**: `isolation: worktree` agents branched from a pre-Wave-0 base, not current HEAD — merge by taking their **new files explicitly** (`git checkout <branch> -- <paths>`), not a blind `git merge` (drags unrelated base commits + conflicts on shared files like `core/designsystem/build.gradle.kts`).
- **Feature/game is the hot seam**: only ONE agent edits `feature/game` at a time; parallelize only module-disjoint tracks (`server/`, `core/designsystem` new files, `ai/`).
- Commits: explicit paths only (never `git add -A` — pre-existing WIP in `cmp-android/build.gradle.kts`, `gradle/libs.versions.toml`, `cmp-android/src/test/` must stay unstaged); **no AI-attribution trailer** (pre-bash hook rejects it).
