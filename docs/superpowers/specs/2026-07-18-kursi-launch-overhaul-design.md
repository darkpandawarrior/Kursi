# Kursi Launch Overhaul — Design Spec

**Date:** 2026-07-18
**Status:** Approved direction, pending final spec review
**Scope:** Full launch update — comprehension, pacing, graphics, AI, accessibility, platform polish, docs — across all four client targets (Android, iOS, desktop/JVM, web/Wasm).

---

## 1. Problem

Player feedback on the current build: *too hard to follow, difficult to crack, fast paced, too much info on screen, no direction, graphics not good enough.*

Root-cause analysis (verified in code):

- `feature/game/src/commonMain/kotlin/com/kursi/feature/game/GameScreen.kt` (5,522 lines) renders **simultaneously**: opponent role claims, suspicion pips, coach REAL/BLUFF badges, P(it-flies) odds pills, recommended-move stars, dossier chits, a 30-event teleprinter log, Darbar chat, a status spine, and a recap rail. It shows an expert's instrument panel to a first-time player.
- The bot round **auto-advances** (`GameViewModel.kt` `advanceJob`, delay tiers 1600/2400/4000 ms × `speedMultiplier`), so the board mutates on its own between the player's turns — the "fast paced / can't follow it" feel.
- Visuals are competent flat Canvas vector work with no depth, lighting, or tactility — "not good enough graphics" is the absence of materiality and juice, not the absence of bitmaps.
- Nothing on screen tells the player *what just happened and why it matters* in plain language.

None of this is a bug. It is density + self-advancing pacing + flat presentation. The fix is architectural, not cosmetic.

## 2. North star

> **Kursi should feel like operating a piece of confiscated government machinery to get away with a lie.**

Reference points: **Balatro** (tactility/juice on a humble card game), **Reigns** (diegetic, near-zero-chrome decisions), **Inscryption** (the interface is a physical table), **Blood on the Clocktower** digital (making a social read legible). Modern = Balatro's juice × Reigns' restraint × a material, lamplit world.

A bluffing game's job is to dramatize **hidden information and the social read**. The current UI shows a spreadsheet of suspicion; the new UI makes you *read people*.

## 3. Organizing spine: three density layers

One concept applied everywhere. Same game, revealed in three layers. Generalizes the existing `GameUiState.coachEnabled` boolean into:

```kotlin
enum class DensityLayer { FOCUS, GUIDED, ANALYST }
```

| Layer | Default for | On the board |
|---|---|---|
| **FOCUS** | First-timers; new global default | Whose turn · what just happened (one Munshi line, §8.1) · your hand · your legal actions. No pips, badges, odds, log, recap rail. |
| **GUIDED** | Mid-journey players | FOCUS + one plain-language suggestion at a time ("They claimed Neta — challenge if you think they're bluffing"). |
| **ANALYST** | Opt-in / graduated | Today's full instrument panel: suspicion pips, REAL/BLUFF badges, odds pills, dossier chits, teleprinter log, recap rail. Nothing deleted — it moves up a layer. |

Graduation FOCUS → GUIDED → ANALYST happens by playing (match count + `DecisionQuality` signals) or manually in Settings at any time. Every overlay composable gates on `DensityLayer`. The layer is persisted in `core/prefs`.

## 4. Three signature interactions

### 4.1 The Focus Pull (virtual camera)
The table gains depth and a virtual camera that pushes toward whoever is acting and racks the rest of the table into soft blur/scale falloff. The camera **is** the direction — it narrates whose beat it is. Implemented with `graphicsLayer` (scale, translation, blur) driven by the beat system (§5); anchored via the existing `TableAnchorRegistry`. Solves "fast", "too much on screen", and "no direction" in one move.

### 4.2 The Read (behavioral tells)
Suspicion stops being pips (in FOCUS/GUIDED) and becomes **performance**: opponent portraits behave — held breath, too-fast claim, over-confident lean — driven off the existing AI `BeliefModel` and persona traits. ANALYST keeps the numbers as backing. Tells are probabilistic and persona-dependent (a good liar has fewer tells) so they inform without solving the game.

### 4.3 The Tactile Hand
The player's two cards are physical objects: **hold-to-peek** (cards stay face-down until pressed, like palming real cards), spring-physics deal/flip, felt parallax on device tilt (gyro where available). Diegetic UI: dossier, ledger, and cards are things on a table, not stacked panels.

## 5. Workstream B — Pacing

- **Beat gate:** in FOCUS/GUIDED, the bot round pauses on each *meaningful* beat (claim, challenge, block, reveal, influence loss) and waits for tap-to-continue. Trivial beats (income tick) flow through. Built on the existing `pauseFor` tier classification — a tier maps to "wait for ack" instead of a timed delay.
- **Speed settings:** SLOW/NORMAL/FAST become `AUTO` speeds (today's behavior, ANALYST default). Tap-to-continue is the newcomer default. Desktop: Space = continue.
- **Telegraphing:** every beat renders as "X is about to act" → action → outcome, synced with the Focus Pull, so nothing resolves before the player registers it.

## 6. Workstream A — Comprehension architecture

- **`GameScreen.kt` decomposition** (prerequisite for everything): split into `board/`, `docks/`, `overlays/`, `coach/`, `camera/` packages inside `feature/game`. Run `cielara query` on `GameScreen.kt` and `GameViewModel.kt` before cutting. `@Immutable` / `ImmutableList` stability pass during the split; recomposition counts checked with the compose-performance-audit skill before/after.
- **"What's happening" headline:** single calm sentence of the current beat (Munshi-written when AI available, templated otherwise), replacing the recap rail in FOCUS.
- **Guided funnel:** rework `TutorialScreen` into a mechanic-at-a-time interactive sequence (claim → challenge → block → coup → exchange), each beat playable against a scripted bot, feeding into the first real match. First-run routes: Boot → Primer → funnel → first match in FOCUS.
- **Progressive bottom sheets** replace stacked overlays (Gazette, dossier, settings-in-game) on phone; side panels on ≥840dp.

## 7. Workstream C — Graphics: layered material renderer

Not flat Canvas, not plain bitmaps — a compositor:

1. **Canvas base** — existing felt/guilloché/emblem drawing, deepened (lighting gradient, vignette, warm key light: the lamplit-desk look).
2. **AGSL / Skia runtime shader layer** — film grain, felt weave, brass specular that sweeps with device tilt, bloom on hero beats. `expect/actual` shader host per platform (Android AGSL / Skiko `RuntimeEffect` elsewhere); graceful no-shader fallback path (web/older devices) that must look intentional, not broken.
3. **Particle layer** — coin trails, stamp-ink spray, dust motes; capped counts, object-pooled.
4. **Asset layer** — new `composeResources` art track: 10 persona portraits, 6 role card faces, hero-moment art (KURSI crest, tipped chair). Shipped with **AI-generated placeholder art in-style** ("License Raj Deco → Sarkari Noir" treatment); final art is a drop-in swap. WebP with per-density variants; total asset budget ≤ 8 MB per target.
5. **Moment overlays** — existing `MomentHost`/`ActionMomentOverlay` system, extended with the new beats; every new moment gets a `StaticMomentFrame` reduced-motion end-state.

Plus: full wiring of the bundled Rozha One / Marcellus / DM Mono (removing the Serif/Monospace fallbacks in `KursiType`), a spring-physics motion-token set in `core/designsystem` (durations/damping named, not scattered), and a **distinct haptic signature per action class** (Android `HapticFeedback`/Vibrator, iOS Core Haptics via expect/actual; silent no-op on desktop/web).

## 8. Workstream G — Intelligence & AI

Kursi already ships ISMCTS (`ai/IsmctsSearch.kt`), `BeliefModel`, 10 personas, `MoveAdvisor`, and a provider-agnostic LLM layer (`OnDeviceAiProvider` expect/actual + cloud `AiProvider` BYOK). This workstream fires that loaded gun.

### 8.1 The Munshi (AI narrator)
A diegetic court-scribe turns engine events into in-character plain language: *"Bahenji stamped the Neta's seal for tax — bold; she's been silent three turns."* Replaces the log in FOCUS. Prompted with the redacted `PlayerView` + recent events only (never hidden cards).

### 8.2 Natural-language coach & debrief
The GUIDED "why?" affordance and post-match "How did I lose?" route to the LLM, **grounded on the actual ISMCTS eval and belief state** — the model explains numbers the engine computed; it cannot invent board state. Post-match debrief uses `MatchDecisionTally` to name the 2–3 pivotal decisions.

### 8.3 Living personas (Darbar table-talk)
Bots banter, taunt, and react in-character; a bluffing bot talks differently from an honest one — table-talk is a tell channel feeding The Read (§4.2). Routed through the existing `SocialDirector` seam.

### 8.4 Adaptive difficulty & dynamic tutorial
`DecisionQuality` feeds bot aggression and tutorial pacing. Pure engine-side heuristics (no LLM required).

### 8.5 Provider matrix — ready for every user type

| Tier | Provider | User |
|---|---|---|
| 1 | **On-device LLM** via `OnDeviceAiProvider` (Android AICore/Gemini Nano; iOS Apple Foundation Models; desktop local runtime if present) | Privacy-first / offline users — zero setup |
| 2 | **BYOK cloud** via `AiProvider` (Anthropic, OpenAI, Gemini endpoints; key stored per-platform secure storage — Keystore / Keychain; never in prefs plaintext) | Power users wanting best quality |
| 3 | **Templated strings** (deterministic, localizable) | Everyone else / AI-off / web-no-provider — the game is 100% playable and legible with AI fully off |

Selection: auto-detect tier 1 → fall back to tier 3; tier 2 is explicit opt-in in Settings. Every AI surface individually toggleable.

### 8.6 Hard guardrails
- The deterministic engine remains the **sole source of truth**. AI output never enters `humanIntentLog`, never mutates `GameState`, never gates a legal action.
- Byte-for-byte replay (`seed + humanIntentLog`) is untouched; AI text is regenerated or omitted on replay, never persisted into the replay record.
- All prompts are built from **redacted** views (`Engine.redact`) — the LLM never sees hidden cards, so it cannot leak them.
- Latency rule: AI responses render when ready and never block a beat; the templated string renders first and upgrades in place.

## 9. Workstream D — Accessibility

Executed with the android-accessibility + adaptive skills; applies to all new and existing surfaces:

- Semantics/`contentDescription` on every meaningful element; merged semantics per opponent plate ("Bahenji, 2 influence, 4 coins, claims Neta").
- Logical traversal order (turn owner → board → your hand → actions); TalkBack + VoiceOver passes on device.
- Touch targets ≥ 48dp; contrast audit against the Deco palette; dynamic type / font-scale support to 200%.
- CVD: keep the Okabe-Ito role palette + `RoleFramePattern` non-color channels; verify in the new asset art too (patterns embedded in card frames).
- Reduced motion: every new moment/camera/parallax/shader effect honors `reducedMotion` via `StaticMomentFrame` equivalents; the Focus Pull degrades to a highlight ring, not a camera move.
- Beat gate is itself an a11y feature: nothing time-critical, no information that scrolls away.

## 10. Workstream E — Platform-specific enhancements

- **Android** (`cmp-android`): edge-to-edge, predictive back, haptic signatures, adaptive layouts (phone/foldable/tablet — multi-pane ≥840dp via the adaptive skill), ChromeOS keyboard support, gyro parallax.
- **iOS** (`cmp-ios`/`iosApp`): safe-area handling, swipe-back interop, Core Haptics, ProMotion-friendly animation, CoreMotion parallax. `CADisableMinimumFrameDurationOnPhone` already present — keep. **Runtime verification on a physical device only** (CMP/Skiko crashes on Xcode-26 simulators — known upstream issue; do not chase it).
- **Desktop** (`cmp-desktop`): keyboard shortcuts (Space = continue, 1–8 = actions, L = log, Esc = menu), hover states/tooltips on all interactive elements, min-window-size, proper resize behavior.
- **Web** (`cmp-web`): responsive breakpoints, keyboard nav + focus-visible, no-shader fallback verified, load-size budget for the asset track.

## 11. Workstream F — Screenshots, GIFs, READMEs (last)

- Extend `buildFixtures()` in `cmp-desktop/src/jvmMain/kotlin/com/kursi/desktop/Screenshots.kt`: FOCUS/GUIDED/ANALYST variants of the table, guided-funnel beats, beat-gate states, Munshi narration, new art, phone/tablet/desktop widths, reduced-motion frames.
- Regenerate all baselines (`:cmp-desktop:renderScreens` → `docs/screenshots/`), restitch flow GIFs (`scripts/make_flow_gifs.sh` — add flows: `focus_mode`, `graduation`, `tap_to_continue`, `munshi`, `the_read`); CI `screenshots.yml` continues to auto-commit.
- Capture **real per-platform** screenshots/recordings (Android device/emulator, iOS physical device, desktop, web) for a platform matrix section.
- Rewrite `README.md` + module READMEs around the three-layer story with GIFs inline (beautify-github-readme skill); update `docs/brand/BRAND.md` with the Sarkari Noir evolution + motion/haptic tokens.

## 12. Sequencing

| Phase | Content | Ships when |
|---|---|---|
| 1 | Comprehension spine: `GameScreen` decomposition, `DensityLayer`, FOCUS default, headline line | build+tests+lint green, screenshots reviewed |
| 2 | Pacing: beat gate, tap-to-continue, speed settings, telegraphing | " |
| 3 | Guided funnel + graduation | " |
| 4 | Graphics: renderer layers, shaders, assets, fonts, haptics, motion tokens, Focus Pull, Tactile Hand | " |
| 5 | Intelligence: Munshi, coach/debrief, personas, The Read, provider matrix | " |
| 6 | A11y sweep across 1–5 | TalkBack/VoiceOver device pass |
| 7 | Platform polish (Android/iOS/desktop/web) | each target **run**, not just compiled |
| 8 | Docs: fixtures, baselines, GIFs, READMEs, brand doc | CI screenshot workflow green |

Each phase independently shippable. Verification gate per phase: `assembleDebug` + unit tests + host-test aggregate (`testAndroidHostTest` — library-module commonTest is not covered by app-variant test tasks) + detekt/ktlint + screenshot render.

Execution machinery: multi-agent Workflow orchestration (Sonnet volume execution, Opus orchestration per model-routing rules), Figma MCP for the design-system iteration (requires Figma connector auth), ralph-loop for pass/fail regen gates, Cielara queries before shared-file edits.

## 13. Risks & mitigations

- **Bleeding-edge toolchain** (Kotlin 2.4.20-Beta1, CMP 1.12.0-beta02, AGP alpha): AGSL/RuntimeEffect and gyro APIs verified per-target early in Phase 4 with a spike before committing the renderer design.
- **Scope**: phases are ordered so that if the effort stops after Phase 3, the actual player complaints are already fixed.
- **AI latency/cost**: never blocks gameplay (§8.6); templated tier is the floor everywhere.
- **Web performance**: shader layer and particles are capability-gated; web gets the no-shader path by default until profiled.
- **Determinism**: replay tests (existing `ReplaySession` byte-for-byte checks) run in every phase gate; any AI-adjacent diff that touches `engine/` is rejected by design.

## 14. Out of scope

- Online multiplayer feature work (server unchanged except where redaction surfaces feed AI prompts — read-only).
- Final commissioned art (pipeline + placeholders only).
- Monetization, store-listing assets, localization of new strings beyond en (structure localizable, translations later).
