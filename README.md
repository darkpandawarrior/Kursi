<div align="center">

<img src="docs/assets/banner.svg" alt="Kursi — Kursi ke liye kuch bhi karega (he'll do anything for the chair)" width="900" />

*Kursi ke liye kuch bhi karega.*  
*He'll do anything for the chair.*

[![CI](https://github.com/darkpandawarrior/Kursi/actions/workflows/ci.yml/badge.svg)](https://github.com/darkpandawarrior/Kursi/actions/workflows/ci.yml)
[![Quality](https://github.com/darkpandawarrior/Kursi/actions/workflows/quality.yml/badge.svg)](https://github.com/darkpandawarrior/Kursi/actions/workflows/quality.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20--Beta1-7F52FF?logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.12.0--beta01-4285F4?logo=jetpackcompose&logoColor=white)
![Platforms](https://img.shields.io/badge/Android%20%7C%20iOS%20%7C%20Desktop%20%7C%20Web-3DDC84)
[![License: CC BY-NC-SA 4.0](https://img.shields.io/badge/license-CC%20BY--NC--SA%204.0-blue)](LICENSE)

</div>

![DARBAR — Afwaah arc live at a 4-player table](docs/screenshots/darbar_table.png)

*सब मिले हुए हैं।*  
Everyone is in on it.

---

Bluffing card game set in a satirical India corporate-political underworld. 2–10 players. Five roles — six at large tables. Every player hides two cards face-down. Every player lies about what they are.

Inspired by Coup (Indie Boards and Cards, 2012). Theme, characters, code, visuals — all wholly original.  
Built in Kotlin Multiplatform. One codebase, four targets: Android, iOS, JVM desktop, Kotlin/Wasm.

---

## The world

The Neta has been making promises since before you were born and has the file extensions to prove it. The Bhai owns three silences and a steel foundry. The Babu's approval has been pending since 2007. The Jugaadu knows someone who knows someone who knows a shortcut. The Vakil has already read the clause that saves him. The Patrakaar has a source inside. 

You're at the table. So are they.  
The chair at the head is empty.

---

## Home

![Home flow — fresh install, GAUNTLET and KISSA tile previews, then a full career with ranked, daily and gauntlet strips](docs/gifs/home.gif)

*Fresh install → GAUNTLET preview → KISSA preview → a loaded career (ranked + daily + gauntlet) → mid-match resume.*

The home screen has eight mode tiles across two columns — **New Game**, **KISSA** (story campaign), **GAUNTLET** (rung ladder), **TAMASHA** (spectate AI), **Tutorial**, **Rules / Gazette**, **Settings**, and **Multiplayer** (online, flagged PENDING SANCTION until server key is present). Selecting any tile opens a mode preview in the right panel with a full description, key details, and an ENTER button.

Above the grid, continuity strips surface everything in progress: the **Aaj ki Chunauti** daily challenge with your current streak, your **ELO rank** (SECTION OFFICER → UNDER SECRETARY → DEWAN and above), **TARAKKI KI SEEDHI** gauntlet progress, career win-rate, and a one-tap **resume strip** when a match is in flight. A rotating ROZNAMCHA ticker scrolls satirical headlines across the top. The brass seal and your current on-duty bot persona appear in the right panel by default.

---

## The six roles

![Niyam Gazette — role reference](docs/screenshots/gazette_roles.png)

All six roles are in the Niyam Gazette, available mid-game without interrupting play.

Everyone at the table holds two cards — face-down. On your turn you claim any role and take the matching action, whether you actually hold it or not. Challenges and blocks are also claims. The whole table is lying. The question is who gets caught.

---

### Neta — Netaji Dhanpat Rai Vachan
*The Eternal Candidate. Made entirely of promises.*

Takes **GHOTALA** — the party's share of FDI, routed correctly, he assures you. +3 Khokha (coins). Has taken it every time. Will take it again. Also blocks everyone else from taking **FDI** (Foreign Aid, +2) — that money was already committed to a constituency project.

**Action:** GHOTALA +3 · **Blocks:** FDI (Foreign Aid)

---

### Bhai — Bhai Teja  
*Owns the Silence. Quiet, slow, unbothered.*

Issues **SUPARI**. It costs 3 Khokha, and the target loses an influence card. Bhai doesn't explain. He also doesn't need to. No one blocks Supari except the one lawyer everyone keeps around for exactly this.

**Action:** SUPARI — pay 3, target loses a card · **Blocks:** —

---

### Babu — Babu Filewala
*Approver of Nothing. Power through the comma.*

Does **VASOOLI** — extracts 2 Khokha directly from another player. The collection is official. The paperwork is in order. Also blocks Vasooli from others — that file for unauthorized extraction has already been rejected at his desk.

**Action:** VASOOLI — steal 2 from target · **Blocks:** VASOOLI (Steal)

---

### Jugaadu — Jugaadu Chhotu
*The Fix-It Man. Solutions mostly illegal.*

Does **SETTING** — draws two extra cards from the pile, keeps what he wants, returns the rest. Nobody knows what he's holding after a Setting. Also blocks Vasooli — something in his arrangement makes the extraction impractical.

**Action:** SETTING — draw 2, keep what you need · **Blocks:** VASOOLI (Steal)

---

### Vakil — Vakil Loophole
*The Silver Tongue. Knows three exceptions to every rule.*

Has no action of his own. Doesn't need one. Blocks **SUPARI** — the Assassin's move hits a procedural wall every time. Whatever exception applies, Vakil has already filed the brief.

**Action:** — · **Blocks:** SUPARI (Assassinate)

---

### Patrakaar — *the 6th role, enters at larger tables*
*Has a source inside. Knows things no one said out loud.*

Does **JAANCH** — privately examines one of a target's face-down cards. The Patrakaar alone learns what it is. Then decides: let it stay, or force the target to shuffle it back into the deck and draw again. An information-then-disruption move. Not blockable.

**Action:** JAANCH — secretly examine a target's card; optionally force reshuffle · **Blocks:** —

---

## How you play

![A full turn — pick an action, declare and confirm, survive the reaction window, block, exchange, lose influence, and reach game over and the results certificate](docs/gifs/turn.gif)

*One turn end-to-end: pick an action → confirm the claim → the table reacts → a block with live odds → an exchange → influence lost → game over → the stamped result.*

Everyone starts with 2 Khokha and 2 cards. On your turn:

- **DEHAADI** — take 1 coin. No claim, no block, no challenge. Always legal.
- **FDI** — take 2 coins. No claim needed. Neta can block it.
- **GHOTALA** — claim Neta, take 3. Anyone can challenge your claim.
- **SUPARI** — claim Bhai, pay 3, target loses a card. Vakil can block. Anyone can challenge.
- **VASOOLI** — claim Babu, steal 2 from a target. Babu or Jugaadu can block. Anyone can challenge.
- **SETTING** — claim Jugaadu, draw 2 and pick which to keep. Anyone can challenge.
- **JAANCH** — claim Patrakaar (6+ player tables), privately examine a target's card. Anyone can challenge.
- **KHELA** — pay 7, pick a target, they lose a card. No claim, no block, no challenge. Mandatory at 10 coins.

**Challenge:** If you think a claim is false, challenge it. The claimant flips the card. Caught lying — they lose influence. Truthful — the challenger loses influence instead. A surviving, proven claim gets shuffled back and redrawn.

**Block:** Claim the blocking role. The original actor can challenge your block — same rules apply. If no one challenges, the block stands.

Last player with influence wins the **Gaddi**.

---

## DARBAR — the social layer

![DARBAR flow — the KISSA arc picker, then an Afwaah rumour unfolding at a live table with bot speech and the player's chat suggestions](docs/gifs/darbar.gif)

*Pick a KISSA arc → a live Darbar table with an Afwaah in flight: bots pile on, the tray shows what you can say to fuel it or let it burn.*

DARBAR is the layer above the engine. Bots have memory, moods, and grudges. They talk to each other. They form pacts. They spread stories. And you can intervene — strategically.

Four story arcs run simultaneously, triggered by in-game events:

**GATHBANDHAN** — Two bots make a quiet agreement to not target each other. The whisper is visible at the table. The defection, eventually, is not. Watch for who breaks first.

**AFWAAH** — Someone starts a rumour about another player's role. It spreads across the table. Players begin acting on it — targeting the accused, avoiding them, adjusting their own claims — even when the rumour isn't true. Especially when it isn't.

**STING** — A bot "leaks" a real card claim. Now the whole table knows what that player is supposedly holding. The target has to decide: act normally and invite the read, or overcorrect and invite suspicion.

**BADLA** — You SUPARI someone. They announce it. They follow through on it — even when it costs them strategically. Grudges don't expire at the end of the round.

As the human, you can **fuel an Afwaah**, **broker a Gathbandhan** with a bot, **taunt** someone into a Badla, or **leak** information to start a Sting. The chat suggestions in the DARBAR tray show what you can say and what arc it advances.

The social layer runs on a **deterministic narrative RNG** — separate from the game engine, never affecting card state, resumes byte-for-byte across save/reload.

---

## KISSA — story arcs mode

KISSA is the story-mode entry point to DARBAR. Pick a narrative arc to start with: run a Gathbandhan from turn one, or seed an Afwaah before the first GHOTALA. The bots have backstories and the table has a script. What you do with it is up to you.

---

## The ten personas

![Hazri Register — bot roster with assigned difficulty and monogram](docs/screenshots/lobby.png)

Ten bot personalities, assigned at match start by the **Hazri Register** (lobby). Each bluffs differently. Each has tells.

| Persona | Their thing |
|---------|-------------|
| **Netaji Vachan** | Claims his role even when holding someone else's card. Always has. Always will. Completely unbothered. |
| **Bhai Teja** | Goes silent before he strikes. The longer the pause between actions, the closer the Supari. |
| **Babu Filewala** | Taxis to DEHAADI obsessively — builds patiently, never exposes himself — until the moment he doesn't have to. |
| **Jugaadu Chhotu** | Does something unpredictable every time. Difficult to read because there is genuinely nothing to read. |
| **Vakil Loophole** | Challenges on principle. Every claim is suspicious. Every block is grounds for inquiry. |
| **Didi** | Vengeance is her love language. If you touch her, she follows through. No hurry. |
| **Madam Ji** | Always two moves ahead. Doesn't react — repositions. Never acts from emotion. |
| **Sharmaji's Son** | Learns nothing. Makes the same call every time. Infuriating if you expect any adaptation. |
| **Inspector Reddy** | By-the-book — for the right bribe. Claims blocking roles constantly. Procedure is leverage. |
| **Startup Bro** | Pivots every round. Each turn is a new grand strategy. Disrupts his own positions regularly. |

Each persona plays a statistically distinct policy. Didi's retaliation timing, Madam Ji's patience, Inspector Reddy's block-frequency — all measurable in the career head-to-head record.

---

## Decision Coach

The coach runs **ISMCTS** (Information Set Monte Carlo Tree Search) in the background — the same algorithm running the bots, turned toward advising you. It has only the information you have: the table-visible event log, standing claims, who got caught bluffing and when.

![Decision Coach flow — recommended move starred with odds on claims, an opponent dossier chit, a bluff-risk chit, the reaction-window read, and the same table with coach off](docs/gifs/coach.gif)

*Coach on the action dock → long-press an opponent for their dossier → long-press your move for the bluff-risk chit → coach on the reaction window → coach off, table silent → rival claims still visible on every plate.*

Every action chip gets annotated: the **recommended move** gets a brass star. Claim-bearing actions get a **REAL** or **BLUFF** badge and a **P(it flies)** odds pill. On the target-pick dock, the **weakest target** gets flagged.

Long-press an opponent's plate to open their **dossier chit**: posterior probability over their likely roles, all standing role claims, bluff-caught count, and inferred bluff rate — built from the public event record.

Long-press your pending action to open the **risk chit**: the probability your claim gets challenged, and the EV impact if it does.

Coach can be toggled off in Settings. When off, the table is silent — no odds, no stars, no badges. All observational tools (dossier chit, risk chit, standing claims, suspicion pips, event log) remain visible regardless. Coach is advisory; the read is always yours.

---

## Game modes

![Game modes flow — Setup, Team Khel toggle, the GAUNTLET ladder, a team table with faction badges, TAMASHA spectator mode, and the pass-and-play handoff guard](docs/gifs/modes.gif)

*Setup → Team Khel toggle → GAUNTLET ladder → a 2v2 team table → TAMASHA watch-mode → the pass-and-play handoff guard.*

### vs AI
1 human vs 1–9 bot opponents. Set player count, difficulty, and whether DARBAR is active.

**Difficulty levels:** Easy (scripted play-caller), Medium (ISMCTS 200 iterations), Hard (ISMCTS 2000 + coach-level search), Expert, Grandmaster.

---

### GAUNTLET — Tarakki ki Seedhi (Promotion Ladder)

Five rungs. Win to climb. Lose and stay. You keep your rung — no snakes, no falls.

| Rung | Players | Difficulty |
|------|---------|------------|
| Rung 1 | 3p | Easy |
| Rung 2 | 3p | Medium |
| Rung 3 | 4p | Hard |
| Rung 4 | 5p | Expert |
| Rung 5 | 6p | Grandmaster |

Clear Rung 5 and the Gaddi is yours.

---

### Team Khel — Faction Play

Two factions. **Allies are never legal targets** — engine rule, not convention. Targeting your own teammate is a structurally illegal move. Faction badges appear on every plate. Win as a team; the last surviving faction takes the table.

---

### DARBAR
The full social-layer mode: all four arcs active, bots chat, player suggestions live in the tray. Separate from ranked play — ranked runs the clean AI with no social layer; DARBAR is for narrative, story, and exhibition.

---

### TAMASHA — Watch Mode

AI plays every seat. Spectator banner, no action controls. Watch ten personas destroy each other, form pacts, break them, and fight over the Gaddi. Good for a demonstration or for understanding how the personas read the table differently.

---

### Pass-and-play — Hot-seat Multiplayer

Multiple humans, one device. After each turn, a **handoff guard** blanks the screen and prompts the next player to take the device — so face-down cards stay hidden between turns. No accounts, no network required.

---

### Vishesh Modes — Experimental Variants

Seven additive rule variants, toggleable per-match from the Setup screen. All default **off**; classic rules are byte-for-byte unchanged when all are disabled. Variants can be combined freely.

| Variant | Hindi name | What it does |
|---------|-----------|--------------|
| **Bail Pe Bahar** | बेल पे बाहर | Pay 9 coins to flip one revealed (face-up) card back face-down. Buys a second life — at a cost. |
| **Bali Khel** | बलि खेल | Sacrifice one of your own face-down cards to gain 3 coins. Voluntary influence loss for tempo. |
| **Hawala** | हवाला | Gift up to 5 coins directly to any alive opponent, bypassing the treasury. Under-the-table transfers. |
| **Adhyadesh** | अध्यादेश | Once you've **earned** ≥ 25 lifetime coins, you can declare Emergency — pay all your coins and force every other player to lose one card. One-time nuclear option. |
| **Khazana Raj** | खजाना राज | First player to **accumulate** a target amount (25 / 50 / 100) of lifetime earned coins wins the game outright — regardless of how many players are still alive. Changes the game from elimination to a coin race with four Darja milestones: Mukhiya → Sahib → Mantri → Sarkar. |
| **Mehengai** | महँगाई | All coin costs (Coup + Assassinate) increase by 1 every few turns — inflation is real. |
| **Tangi** | तंगी | Total coin supply is capped below the classic formula. Hoarding and denial become dominant strategies. |

---

### 2–10 players

![Table sizes flow — the 2-player duel, a 4-player table, and the full 10-player table where the Patrakaar enters](docs/gifs/table_sizes.gif)

*The same engine at 2, 4, and 10 seats — the table reshapes from a fast duel to a loud ten-hand darbar.*

At 2 players, SUPARI + KHELA dynamics dominate — the coin race is fast. At 10 players, the PATRAKAAR (6th role) enters the deck; the Jaanch information-asymmetry and the social layer make the table genuinely loud.

---

## Career, replay, and ranking

![Career flow — the stamped results certificate, the empty-record state, the Roznamcha career dossier, local ELO, online standings, the replay scrubber and the recent-matches list](docs/gifs/career.gif)

*Results certificate → record-expired empty state → the Roznamcha career dossier → local ELO with spark-line → online standings → the replay scrubber with advisor annotation → recent matches.*

### Results certificate

After every game: a stamped certificate. Winner, final standings, bluffs held, bluffs caught, and a **decision-quality recap** — how closely your moves matched the ISMCTS best-move at each decision point, average EV bled, and challenge accuracy. Share it directly.

---

### Roznamcha — Career Dossier

Lifetime stats: games, wins, bluffs held, bluffs caught. Head-to-head records against each bot persona — who you've played, and who you've beaten. A **decision-quality ledger** that accumulates across all games: accuracy against ISMCTS best-move, average EV bled per decision, challenge accuracy %, bluff success rate.

---

### Darja-suchi — Local Ranking

Local ELO with a 14-game rating history spark-line. Daily challenge (Aaj ki Chunauti) that resets each day. Streak counter with best-streak history. When connected to the Ktor server, an **online standings board** appears alongside local — server-backed real-time rankings.

---

### Replay Scrubber

Every completed match is stored as `(seed, intentLog)`. The replay reconstructs the game state **byte-for-byte** from that pair — no snapshots required. Step through each decision. At annotated moments, see what the coach would have recommended and how your move compared. Available from the career screen for recent matches.

---

## Online play

![Online flow — the mode picker, LAN browse with discovered hosts, the waiting room with room code and seats, and the reconnect banner after a dropped connection](docs/gifs/online.gif)

*Mode picker → LAN browse (discovered hosts) → waiting room with code + seats → a dropped connection and its reconnect banner.*

**Private room by code** — create a room, share the 4-letter code. Anyone with the code joins.

**Quick-match** — matchmake against whoever is waiting.

**LAN discovery** — Bonjour/mDNS browse shows all Kursi games on the local network. No manual IP entry.

The authoritative server is **Ktor/Netty**. All game state lives server-side. Clients receive only their redacted `PlayerView` — the server never sends information a player shouldn't have. A disconnected player auto-passes on their turn until they reconnect.

---

## Onboarding

![Onboarding flow — Pehli Hazri profile setup, the scripted tutorial table, the guaranteed bluff-caught JHOOTH stamp, and arriving at Home](docs/gifs/onboarding.gif)

*First run: set your name, avatar and seat colour (PEHLI HAZRI) → the scripted tutorial table → the guaranteed bluff-caught teaching beat → land on Home.*

### Pehli Hazri — Interactive Tutorial

Scripted table with guaranteed teaching beats. Beat 7 is the lesson that matters: a NETA claim gets challenged and the card flips to BHAI. The **JHOOTH** (liar) verdict stamp lands. The bluffer loses influence. You can't exit the tutorial without seeing it happen.

---

### Setup

Set player count (2–10), difficulty, play mode (vs AI / DARBAR / TAMASHA / Pass-and-play), and whether Team Khel is active. Team Khel appears when 4+ players are selected and is the only option that restructures the engine-level legality of targets.

---

### Niyam Gazette — Rule Reference

Pull up mid-game without leaving the table. Three tabs: roles and their powers, all actions with coin costs, and the DARBAR arc reference. Includes PATRAKAAR when the 6th role is in play.

---

## Reference & accessibility

![Reference flow — the Niyam Gazette rule reference, the reduced-motion static-frame gallery, and the settings screen](docs/gifs/reference.gif)

*The Niyam Gazette (roles / actions / arcs) → the reduced-motion static-frame gallery → Settings.*

Every game moment (income, coin steal, role reveal, influence loss, elimination, coup, win) has a **tailored static end-frame** when reduced motion is on — not a generic fade. GHOTALA shows a held stamp. SUPARI shows the tipped chair. KHELA shows the KURSI crest. Okabe-Ito CVD-safe palette for all role colours. Full VoiceOver/TalkBack support with semantic properties and traversal order.

---

## Architecture

```
Kursi/
├── engine/           # (GameState, Intent) → GameState — zero deps, RNG in state
├── ai/               # ISMCTS + 10 personas + social model + cloud/on-device LLM layer
├── server/           # Ktor/Netty authoritative server
├── shared-protocol/  # Wire types (server ↔ client)
├── core/
│   ├── designsystem/ # KursiTheme, RoleGlyph, all UI primitives
│   ├── prefs/        # Career stats, gauntlet progress, resume snapshot, API key store
│   ├── network/      # Ktor WS client + LAN discovery
│   └── feedback/     # Haptics, notification channels, share sheet (expect/actual)
├── feature/game/     # GameScreen, GameViewModel, GameSession, DARBAR narrative engine
├── cmp-shared/       # NavHost + all screens (shared Compose UI)
├── cmp-android/      # Android shell — FCM, adaptive icons, in-app review/update
├── cmp-ios/          # iOS KMP framework — APNs, StoreKit review, App Store update check
├── cmp-desktop/      # JVM desktop + headless render harness
└── cmp-web/          # Kotlin/Wasm browser + PWA manifest
```

### Module dependency graph

Direct `project(":...")` dependencies as declared in each module's `build.gradle.kts` — not aspirational, this is what actually resolves:

```mermaid
graph TD
    subgraph domain["Domain core — pure Kotlin, zero UI deps"]
        engine["engine<br/>(GameState, Intent) -&gt; GameState"]
        shared_protocol["shared-protocol<br/>wire types"]
        ai["ai<br/>ISMCTS + personas"]
    end

    subgraph coresvc["Core services"]
        designsystem["core:designsystem"]
        network["core:network"]
        prefs["core:prefs"]
        feedback["core:feedback"]
    end

    subgraph featui["Feature + shared UI"]
        game["feature:game<br/>MVI GameViewModel"]
        cmp_shared["cmp-shared<br/>NavHost + screens"]
    end

    subgraph shells["App shells"]
        cmp_android["cmp-android"]
        cmp_ios["cmp-ios"]
        cmp_desktop["cmp-desktop"]
        cmp_web["cmp-web"]
    end

    server["server<br/>Ktor/Netty authoritative"]

    ai --> engine
    shared_protocol --> engine
    network --> engine
    network --> shared_protocol
    designsystem --> engine
    designsystem --> feedback

    game --> engine
    game --> ai
    game --> designsystem
    game --> feedback
    game --> network
    game --> shared_protocol

    cmp_shared --> game
    cmp_shared --> designsystem
    cmp_shared --> feedback
    cmp_shared --> prefs
    cmp_shared --> network

    cmp_android --> cmp_shared
    cmp_ios --> cmp_shared
    cmp_desktop --> cmp_shared
    cmp_web --> cmp_shared

    server --> engine
    server --> shared_protocol
    server --> ai
```

Simplified for readability: a few app shells also take a direct `project()` dependency on `engine`/`ai`/`core:designsystem` for platform-specific wiring (e.g. `cmp-desktop`'s headless render harness) in addition to the path shown through `cmp-shared`/`feature:game`. `engine` itself has zero `project()` dependencies — every arrow in this graph ultimately terminates there.

Things worth calling out:

**Engine is a pure function.** `(GameState, Intent) → GameState` with a counter-based SplitMix64 RNG carried in state. No `Date.now()`, no global random. Any game replays byte-for-byte from `(seed, intentLog)`. Resume, replay, and server authority all work without snapshots — `MatchResumeTest` proves this.

**Secrecy boundary is structural, not convention.** `redact(state, viewer) → PlayerView` is a type-level projection. Another player's face-down roles cannot structurally appear in the view. Bots only see their `PlayerView` — engine-level cheating is impossible by construction, not policy.

**DARBAR narrative doesn't touch the engine.** Two RNG streams: `nudgeRng` advances in strict bot-step order (deterministic across resume) and a cosmetic `rng` for chat timings (never game-affecting). `NarrativeResumeTest` covers this.

**Design system is the enforcement layer.** Every surface routes through `BrassParchmentSurface`, `decoPopoverPaper`, `WaxSeal`, `drawRoleGlyph`. The License Raj Deco visual identity — 1950s–70s government-issue document aesthetic, teak `#1A1A2E` / brass `#C99A3B` / cream `#F4ECD8` — is structurally enforced, not left to per-screen taste.

**AI layer is provider-agnostic.** The `AiProvider` interface abstracts Anthropic, OpenAI, and Gemini cloud calls, on-device Gemini Nano (Android), and Apple FoundationModels (iOS 26). ISMCTS is the offline fallback. BYOK (bring your own key) stored in EncryptedSharedPreferences / Keychain.

---

## Technical deep dive

Two things worth reading the source for — not the callouts above, not generic KMP boilerplate.

**The RNG is a value, not a service.** `engine/src/commonMain/kotlin/com/kursi/engine/Rng.kt` is 53 lines and carries the entire determinism guarantee. `Rng` wraps an immutable `RngState(seed, step)`; every draw (`nextLong`, `nextInt`, `draw`) takes the current `Rng` and returns `(value, advancedRng)` — nothing mutates, and there's no `kotlin.random.Random` anywhere in `engine`. The mixer is a plain SplitMix64 (`z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9`, integer-only), so `(seed, step)` produces identical output on every target the module compiles for — JVM, Android, iOS/Native, Wasm — with no floating point and no platform `Random` implementation to diverge between them. Because the RNG is state threaded through pure functions rather than a mutable service the engine holds onto, a whole match replays byte-for-byte from `(seed, intentLog)` alone — no snapshots. `ScalingGoldenTest` and `MatchResumeTest` (`engine/src/commonTest`, `feature/game/src/commonTest`) are what actually pin that guarantee down.

**A vendored MVI core, not a copy-pasted one.** `GameViewModel` (`feature/game/src/commonMain/kotlin/com/kursi/feature/game/GameViewModel.kt`) is built on a plain `CoroutineScope`, not `androidx.lifecycle.ViewModel` — `cmp-ios` and `cmp-web` can't depend on AndroidX, so the MVI contract has to be platform-neutral. One-shot signals (`GameEffect.IllegalMove`, etc.) go through `EffectEmitter`, which lives in the `mvi-core` module of the [`kmp-toolkit`](https://github.com/darkpandawarrior/kmp-toolkit) monorepo — a *separate git repository*, vendored in as a submodule (`external/kmp-toolkit`) and wired through `includeBuild` in `settings.gradle.kts`, not a module of this repo. The composite build declares an explicit `dependencySubstitution` mapping `com.siddharth.kmp:mvi-core` → `project(":mvi-core")` (and `com.siddharth.kmp:feedback` → `project(":feedback")`), so the published coordinates resolve to the local monorepo modules. Same MVI primitive, reused across projects, versioned once instead of copy-pasted per project.

---

## Getting started

```bash
git clone https://github.com/darkpandawarrior/Kursi.git
cd Kursi

# Fastest path to the full game
./gradlew :cmp-desktop:run

# Android (gms = full-feature build with Firebase/Play Core; noGms = F-Droid/FOSS build)
./gradlew :cmp-android:assembleGmsDebug
adb install -r cmp-android/build/outputs/apk/gms/debug/cmp-android-gms-debug.apk

# All JVM tests
./gradlew jvmTest

# Render screen fixtures (no device needed)
./gradlew :cmp-desktop:renderScreens
```

Signing: `cp keystore.properties.template keystore.properties` and fill in your keystore.

Version bump: `scripts/bump_version.sh --patch|--minor|--major`

---

## Build targets

| Command | Output |
|---------|--------|
| `:cmp-android:assembleGmsDebug` | Debug APK, full feature set |
| `:cmp-android:bundleGmsRelease` | `.aab` for Play Store / Indus Appstore |
| `:cmp-android:assembleNoGmsRelease -Pfdroid` | Reproducible FOSS APK (no Firebase/Play Core) for F-Droid |
| `:cmp-desktop:run` | Launch desktop app |
| `:cmp-desktop:packageDmg` / `packageMsi` / `packageDeb` | Native installers (macOS/Windows/Linux runners respectively) |
| `:cmp-web:wasmJsBrowserDistribution` | Wasm browser build |
| `:cmp-ios:linkReleaseFrameworkIosArm64` | `KursiKit.framework` |
| `:server:installDist` | Runnable server dir (used by `server/Dockerfile`) |
| `make all` | All targets into `outputs/` |

Fastlane:

```bash
bundle install
bundle exec fastlane android internal
bundle exec fastlane ios testflight
```

### Distribution pipelines

All gated on repo secrets and no-ops until configured — see each workflow's header comment:

| Workflow | Target |
|---|---|
| `release.yml` | Play Store internal/beta/production (Android) + TestFlight/App Store (iOS) |
| `publish-fdroid.yml` | Signs the reproducible `noGms` build, publishes to GitHub Releases (fdroiddata binary host) |
| `indus-deploy.yml` | PhonePe Indus Appstore |
| `amazon-appstore-deploy.yml` | Amazon Appstore (App Submission API) |
| `huawei-appgallery-deploy.yml` | Huawei AppGallery (AGC Publish API) |
| `samsung-galaxy-store-deploy.yml` | Samsung Galaxy Store (Content Publish API) |
| `aptoide-deploy.yml` | Aptoide (Uploader API) |
| `desktop-release.yml` | Dmg/Msi/Deb native installers → GitHub Release, per-OS runner matrix |
| `web.yml` | GitHub Pages (wasmJs) |
| `server-deploy.yml` | Fly.io (`fly.toml` + `server/Dockerfile`) |

Not automatable, no CI job:
- **Uptodown** — no public submission API; manual web-form upload.
- **TapTap** — no public submission API; manual upload/contact-support via the developer console
  (gaming-only anyway, but Kursi qualifies).
- **[Obtainium](https://github.com/ImranR98/Obtainium)** — not a store; it tracks the GitHub
  Releases `release.yml` already publishes, no separate config needed.

---

## Version history

No release has been tagged yet. `git tag` returns exactly one entry — `backup/pre-scrub-2026-07-06`, a pre-scrub safety marker, not a version — so there's no `v1.0.0`-style tag history to show. `VERSION` currently reads `1.0.0` and `BUILD_NUMBER` reads `0`; both are bumped by `scripts/bump_version.sh`, but neither has been cut as a GitHub Release yet.

What the 88 commits since the project's scaffold actually shipped, dated from `git log`:

| Date | Milestone |
|------|-----------|
| 2026-01-06 | Project scaffold — KMP setup, Gradle wrapper, version catalog |
| 2026-01-13 | `engine`: game types, the `redact` secrecy boundary, SplitMix64 counter RNG |
| 2026-02-10 | `ai`: ISMCTS — determinizer, node sampling, belief model |
| 2026-02-17 | Difficulty tiers Easy → Grandmaster and the 10 named bot personas |
| 2026-03-05 | `core:designsystem`: License Raj Deco theme tokens, role glyphs |
| 2026-03-19 | `feature:game`: game session with deterministic resume, MVI state |
| 2026-04-02 | All 4 client shells (Android, iOS, desktop, web) building |
| 2026-04-23 | `server`: Ktor authoritative game server, invite-code rooms |
| 2026-05-07 | DARBAR: bot chat, social model, alliance system |
| 2026-05-13 | Four DARBAR story arcs — Gathbandhan, Afwaah, Sting, Badla |
| 2026-06-18 | Android FCM push, notification channels, in-app review/update |
| 2026-06-26 | Seven Vishesh Modes (additive gameplay variants) |
| 2026-07-05 | `kmp-mvi-core` vendored in; `GameViewModel` wired to `EffectEmitter` |
| 2026-07-09 | Toolchain + dependencies bumped to the versions in the Tech section below |

Full list: `git log --oneline`.

---

## Docs

- [Game rules PDF](docs/Kursi_Game_Rules-v2.pdf)
- [Visual identity guide](docs/brand/BRAND.md)

---

## Tech

Kotlin Multiplatform 2.4.20-Beta1 · Compose Multiplatform 1.12.0-beta01 · Gradle 9.7.0-milestone-2 · AGP 9.4.0-alpha03 · Ktor 3.5.1 · multiplatform-settings 1.3.0 · kotlinx.serialization 1.11.0 · detekt 2.0.0-alpha.5 · ktlint 14.2.0 · Fastlane · GitHub Actions

---

## License

CC BY-NC-SA 4.0 — Source code is available for study and non-commercial modification at https://github.com/darkpandawarrior/Kursi. Commercial use requires explicit written permission. 

Copyright (c) 2026 darkpandwarrior.

Inspired by *Coup* (Rikki Tahta, Indie Boards and Cards, 2012). Game mechanics are uncopyrightable; all original expression is wholly original. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

*सारे पात्र काल्पनिक हैं।*  
All characters and events are fictional. Satire only.
