# AIL-3 — Universal Input — Design (agreed 2026-07-05)

> Brainstorming deliverable for the AIL-3 block of
> [ai-launcher-mvp-plan.md](ai-launcher-mvp-plan.md) (§3.3, §5 AIL-3, §4 R8, §4A DF-1/2/3).
> This doc captures the **agreed forks + component design**; the authoritative closure record stays
> the ADR in `decisions.md`. Implementation plan is produced next via writing-plans.

## 1. Goal (this block)

One **universal home input** that additively routes typed **and** spoken natural language to the safest
correct target — **without touching the proven command pipeline**. The launcher stays fully offline; no
LLM (that is AIL-4).

Lanes over the single home field:
- **app filter (live)** — as the user types, matching installed apps appear inline (tap → launch).
- **existing command pipeline (byte-for-byte)** — Enter/IME submit → `HandleUserCommandUseCase`, unchanged.
- **web / site / Play Store** — already routed by the command pipeline since AIL-2; surfaced as explicit
  accelerator chips.
- **assistant (prefilled)** — an `ask` chip routes to the assistant with the buffer prefilled (reuses the
  drawer's X6-C `Routes.Assistant.routeFor(Uri.encode(...))` mechanism).
- **voice (same path as typed, R8)** — partials update the buffer (so live filter/chips react); Final
  submits through the unchanged command path. No new voice code.

## 2. Forks decided (owner, 2026-07-05)

| Fork | Decision |
|---|---|
| **Routing model** | **Additive router.** Enter → `HandleUserCommandUseCase` is byte-for-byte untouched. The router only feeds the live results/chips; taps/chips are explicit new lanes (and are themselves implemented by reusing the existing pipeline / existing nav, so they add no new execution logic). |
| **DF-1 home layout** | **"Search overtakes" is the primary mode:** while the buffer is non-empty the home body (favorites / suggestions / all-apps) yields to a results panel; clearing restores home. **Plus a hidden developer "Command console" mode** (transcript layout) behind a two-factor unlock — see §5. |
| **DF-2 input field** | **Terminal `>` prompt:** leading `>` glyph (replaces the magnifier), JetBrains Mono, static block caret (blink is DF-5/LOW_END-gated motion, deferred), thin grid border with glowing corner ticks, mic as a bracketed `[ ● ]` trailing affordance with idle/listening/error states. |
| **DF-3 results** | **Hybrid:** app matches as an icon+label list/grid (icons aid recognition); route actions as a single bracketed chip row pinned under the field — `[ ⌕ web ]  [ ✦ ask ]  [ ⌂ site ]` (`site` only when the buffer is a safe openable URL). |

## 3. Architecture

### 3.1 New pure domain seam — `UniversalInputRouter` + `InputIntent`

`domain/input/` (pure Kotlin, stdlib only; reuses the proven `domain/intent/UrlDetector`):

```
sealed interface InputIntent {
    data object Empty : InputIntent                      // blank buffer → show home
    data object DevSentinel : InputIntent                // the literal "//dev-mode" toggle string
    data class  Query(                                   // normal typing
        val raw: String,
        val siteUrl: String?,                            // non-null iff UrlDetector → safe openable Url
    ) : InputIntent
}

interface UniversalInputRouter {                         // impl trivial/pure; may be an object
    fun classify(buffer: String): InputIntent
}
```

- The router's only real classification work is (a) the `//dev-mode` sentinel and (b) delegating to
  `UrlDetector` to decide whether the `site` chip is offered and what URL it targets. `web` and `ask`
  chips are unconditional for any non-empty `Query`. App matching is **not** the router's job (it needs
  the installed-app list → stays in the ViewModel, like the drawer's `filterApps`).
- Pure + `UrlDetector`-backed ⇒ cheap to unit-test exhaustively; no Android.

### 3.2 ViewModel (`LauncherViewModel`) — additive state, no pipeline change

- New derived `StateFlow<HomeInputResults>` combining `commandInput` × loaded apps × `router.classify(...)`:
  - `appMatches: List<InstalledApp>` — filter installed apps by label (reuse the drawer's filter logic;
    a small shared helper is acceptable).
  - `chips: List<RouteChip>` — built from `InputIntent.Query` (`web`, `ask`, and `site` when `siteUrl`).
  - `Empty` ⇒ empty results ⇒ home body renders as today.
- **Submit (unchanged path):** `onCommandSubmitted(buffer)` still calls `HandleUserCommandUseCase`, with
  one additive pre-check: if `router.classify(buffer) is DevSentinel` **and** dev-mode is armed, toggle the
  console and consume the input (never reaches the use case). `//dev-mode` is not and never was a valid
  command (it resolves to Unknown today), so intercepting exactly that sentinel does not regress any real
  typed command — parity holds for every real input.
- **Chip dispatch (explicit lanes, reuse existing paths):**
  - `web` → `onCommandSubmitted("search $raw")` — routes through the unchanged pipeline (AIL-2 web search,
    already redacted). No new executor code.
  - `site` → `onCommandSubmitted(raw)` — the buffer is already URL-like, so the pipeline routes it to
    `OpenUrl` (AIL-2). The chip is just a one-tap "submit as-is".
  - `ask` → screen builds `Routes.Assistant.routeFor(Uri.encode(raw))`, VM emits `NavigationEvent`
    (identical to the drawer's X6-C; VM stays Android-free, no feature→feature edge).
  - app tap → existing `onAppClicked(app)` / `launchApp(...)`.
- **Voice (R8):** already flows through `commandInput` (partials) + `onCommandSubmitted` (Final). Because
  the results flow derives from `commandInput`, voice partials drive the live filter/chips for free. No
  change to `SpeechInputSource` / voice wiring.

### 3.3 Presentation (`LauncherScreen`, `core/ui`)

- New `core/ui` field component (DF-2): a terminal `>`-prompt input (either a restyled `SidrSearchField`
  or a sibling `SidrCommandPrompt`; decided in the plan — leaning to a new component so the drawer's
  search field is unaffected). Mic affordance keeps the existing gate (`showMic`, `onMicTap`).
- New results surface (DF-3 hybrid): app-match list/grid + a bracketed route-chip row. Pure presentation;
  callbacks handed down from the screen.
- Home body switches on results-empty: non-empty ⇒ results panel ("search overtakes"); empty ⇒ today's
  favorites/suggestions/all-apps. `SIDR//` wordmark added at the **start** of the top bar (terminal
  system-line identity) — it doubles as the dev-mode arm target (§5).

## 4. Byte-for-byte parity (closure gate #1)

- `CommandNormalizer` / `IntentMatcher` / `HandleUserCommandUseCase` / `ExecutableAction` / resolver /
  executor are **untouched**.
- Every real typed command reaches the use case exactly as before. The only inputs the ViewModel
  intercepts before the use case are the two dev sentinels (armed `//dev-mode`), which have no existing
  command behavior. This is stated explicitly in the ADR.
- Chips route *through* the existing pipeline (or existing nav), so they cannot diverge from it.

## 5. Hidden developer mode — "Command console" (DF-1 addendum)

Two-factor unlock (both required), **session-only / in-memory** (resets on process death — no persisted
key, so the privacy denylist guard is untouched):

1. **Arm:** tap the `SIDR//` wordmark **7×** within a short window → VM `armDevMode()` sets `armed=true`
   (brief accent flash via existing `commandFeedback`). Tap counting is transient screen state.
2. **Toggle:** with dev-mode armed, submitting the literal `//dev-mode` → router returns `DevSentinel` →
   VM toggles `consoleOn` (feedback "dev console on/off"), clears input. Un-armed `//dev-mode` is a no-op
   passthrough (Unknown), so the sentinel stays inert for normal users.

When `consoleOn`, the home body renders a **Command Console transcript** (session-only
`List<ConsoleLine>`): each submit appends `> <command>` + a one-line outcome summary derived from the
existing `CommandOutcome`/feedback (no new domain, no new execution). Results still use the DF-3 hybrid
presentation. Turning it off restores "search overtakes". Dev-only; never advertised.

## 6. Topology / hard-rules compliance

- `domain` gains `input/` (`UniversalInputRouter` port + `InputIntent`), stdlib-only, vendor-neutral.
- `data/*` unchanged (router impl is trivial/pure — may live in `domain` as an `object`, or a tiny impl;
  decided in the plan). No `data → data` edge.
- `feature/launcher` hosts the universal input + results + console; nav via `NavigationEvent`; **no
  `feature → feature` edge**; VM stays Android-free (screen does `Uri.encode`).
- `core/ui` gains the terminal field + results/chip components (presentation only; depends only on
  `core/common`).
- `:app` wires the router binding + any DI. Single `NavHost` unchanged.
- `HandleUserCommandUseCase` / `IntentMatcher` / `GenerateReplyUseCase` contracts **unchanged**. Launcher
  core fully offline.

## 7. Testing

- `UniversalInputRouterTest` (JVM, pure): empty → `Empty`; `//dev-mode` → `DevSentinel`; URL-like → `Query`
  with `siteUrl`; plain text → `Query` with null `siteUrl`; UrlDetector edge reuse (punycode/unknown TLD →
  no site chip).
- `LauncherViewModel` tests: results derivation (app matches + chips) from buffer; **submit still calls the
  use case verbatim** for real commands (parity); dev sentinel armed→toggle vs un-armed→passthrough; chip
  dispatch routes through the expected pipeline/nav; voice-partial → live results.
- `./gradlew --no-daemon testDebugUnitTest assembleDebug` green. Device acceptance opportunistic (mandatory
  at AIL-6).

## 8. Out of scope (explicit)

- No LLM routing (AIL-4). No new persisted context. No confirmation-card redesign (DF-4/AIL-5). No CRT
  motion / blink / scanlines (DF-5/AIL-6). No accent-switcher UI (DF-7). App-filter logic is label-match
  only (no fuzzy/semantic — that is later). The dev console is a minimal transcript, not a full REPL.

## 9. Closure (per plan §0)

Typed commands byte-for-byte; hard rules intact; `testDebugUnitTest` + `assembleDebug` green; ADR appended
to `decisions.md`; `CLAUDE.md` Current goal → AIL-4; `current-status.md` synced; plan §5 AIL-3 marked ✅.
