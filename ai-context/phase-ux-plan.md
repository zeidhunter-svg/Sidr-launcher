# Phase UX — Home Redesign & Design System

> **Status: PLAN (not started).** Owner decision 2026-07-02: the project is a shipping MVP; the
> current "wall of all app icons + type-a-command" home is too primitive. This phase makes the
> interface **simple, understandable, and properly configurable**, and **removes the full icon grid
> from the home surface** (it overloads the view). Runs on the existing Compose + single-`NavHost` +
> MVVM stack — **no AI required**; the rule + cloud + heuristic-suggestion pipelines are untouched.
>
> **Sequencing:** MVP-critical — runs **next**. It also *helps* the open cold-start perf work: moving
> the all-apps grid off the first frame shrinks the largest startup cost (PackageManager enumeration +
> full-grid render). Do this phase **before or interleaved with** the cold-start fix; they are
> synergistic, not competing.

---

## 1. Why this phase exists (the problem, concretely)

Grounded in the current code (`feature/launcher/LauncherScreen.kt`):

- **Home = every installed app.** `SuccessContent → AppGrid` renders the full installed-apps list on
  the home surface. Visual overload + it's the prime cold-start cost.
- **No visible entry points.** Settings and Assistant are reachable **only** by typing `settings` /
  `assistant` into the command bar (`RuleBasedIntentMatcher` `SIMPLE_COMMANDS`). A first-time user
  cannot discover them. The code even notes a real entry point is "a later phase" (that's this phase).
- **Settings is a stub.** `LauncherSettingsScreen` (in `:app`) is one acceptance toggle
  (`aiSuggestionsEnabled`) — not a real, discoverable settings surface.
- **No design system.** `core/ui` (reserved as "Design system / theme" in the module contract) is
  effectively empty; there is no shared token set, typography, or component library.

## 2. Goals / Non-goals

**Goals**
- A **minimal, uncluttered home**: no full icon wall; only a small, useful set (favorites +
  suggestions) plus a unified search/command field and **discoverable** entry points.
- **All apps moved to a separate App Drawer** (opened on demand), searchable and ordered.
- **Discoverable Assistant + Settings** via visible affordances (icons), not typed commands.
- A **real, pragmatic design system** in `core/ui` (theme, typography, shape, spacing, core components).
- A **proper Settings surface** ("conveniently configured"): theme, suggestions, voice, default-launcher
  helper, favorites.

**Non-goals (explicit)**
- No new AI paths; `IntentMatcher` / `GenerativeAiEngine` / `SuggestionEngine` contracts unchanged.
- No OQ#1/#2/#3 model work; `:data:ai-local` stays inert.
- No Phase-8 accessibility automation (that's its own phase). Basic a11y hygiene (content
  descriptions, touch targets) IS in scope here.
- No home-screen widgets / folders / multi-page desktop in this phase (candidate for a later phase).

## 3. Design direction (chosen — owner delegated discretion)

A **search-first, decluttered launcher** (think "minimal home + drawer"), not an icon desktop:

```
┌─────────────────────────────┐
│  09:41            ⚙  🤖      │  ← lightweight top bar: Settings + Assistant icons
│                             │
│  ┌───────────────────────┐  │
│  │ 🔍  Search or type a   │  │  ← unified search + command field
│  │      command…      🎙 │  │     (mic when available)
│  └───────────────────────┘  │
│                             │
│  Suggestions                │  ← existing Suggestions row (single-owner state)
│  [Clock] [Maps] [Music]     │
│                             │
│  Favorites                  │  ← small set: top-N most-used (auto), optional manual pin
│  ▢  ▢  ▢  ▢                 │
│                             │
│            ▲ All apps       │  ← button (+ optional swipe-up) → App Drawer
└─────────────────────────────┘
```

- **Home** shows: (top bar) Settings + Assistant icons; a **unified search/command field**;
  **Suggestions**; **Favorites** (top-N used apps); an **All apps** affordance. That's it — no wall.
- **App Drawer** (separate surface): the full installed-app list moves here — vertically scrollable,
  **alphabetical with section headers + fast-scroll**, and **filtered live** by the same search field.
- **Search + command are one field.** Typing filters the drawer/apps live *and* still submits to the
  existing command pipeline on enter (typed commands keep working byte-for-byte). No behavior lost.
- **Discoverability.** Assistant and Settings become tappable icons; `assistant`/`settings` typed
  commands remain as power-user shortcuts.

Rationale: this is the standard, well-understood modern-launcher model (minimal home + drawer), it
directly satisfies "don't keep all icons visible," and it's the cheapest first frame (smallest home =
fastest cold start).

## 4. Forks (decide before coding — "forks before code" discipline)

| # | Fork | Options | Recommendation |
|---|---|---|---|
| **U1** | Home model | (a) keep full grid, (b) **minimal home + App Drawer** | **(b) — DECIDED 2026-07-02 (owner)**; declutter + perf win |
| **U2** | Open drawer | (a) button only, (b) swipe-up only, (c) **both** | **(c)** button always (discoverable) + swipe-up (fast); button is the MVP-must, swipe is additive |
| **U3** | Favorites source | (a) manual pin only, (b) **auto top-N from `UsageHistoryRepository`**, (c) both | **(b)** for MVP (zero setup, reuses existing usage data); add manual pin in a later block/phase |
| **U4** | Design-system depth | (a) full token system, (b) **pragmatic M3 theme + core components** | **(b)** — ship value now, expand later; no over-engineering for an MVP |
| **U5** | Settings home | (a) keep in `:app`, (b) **promote to `:feature:settings`** | **(b) — DECIDED 2026-07-02 (owner)**; a real feature module (single-`NavHost` rule already supports it; keeps `:app` a thin composition root) |
| **U6** | Search scope | (a) filter apps only, (b) **filter apps + still run commands on submit** | **(b)** — one field, no regression to the command pipeline |
| **U7** | Default-launcher UX | (a) ignore, (b) **in-Settings "Set as default" helper + first-run nudge** | **(b)** — a launcher that can't be set default isn't shippable; small `RoleManager`/intent helper |

*(U1/U5 are the load-bearing ones. If the owner disagrees with (b) on either, the block breakdown
below changes — surface before starting Block X2/X5.)*

## 5. Block breakdown

Blocks are vertical slices; each ends green (`testDebugUnitTest` + `assembleDebug`) and keeps the hard
rules. Suggested letters continue the project's sequence conceptually (call them **X1…X6**).

### Block X1 — Design-system foundation (`core/ui`) — ✅ DONE 2026-07-03
- Fill the reserved `core/ui`: **theme** (color scheme light/dark, `MaterialTheme` wiring), typography,
  shapes, **spacing/size tokens**; a small **component library**: `SidrScaffold`, `SidrSearchField`
  (unified search/command look), `AppTile`, `SectionHeader`, `TopBarIcon`, `EmptyState`, `ErrorState`.
- **No behavior change** — pure presentation primitives. Existing screens keep working.
- Acceptance: theme applies app-wide; components have previews; no `feature→feature`/`domain→ui` edges.

### Block X2 — Home redesign (declutter) — ✅ DONE 2026-07-03
- Rebuild `LauncherScreen` home: top bar (**Settings + Assistant icons** → `navigateTo` via existing
  `NavigationEvent`/`Routes`), unified **`SidrSearchField`** (wraps today's `CommandInputBar` behavior
  + mic), **Suggestions** row (unchanged single-owner `LauncherUiState.suggestions`), **Favorites**
  (new, top-N from usage), **All apps** affordance.
- **Remove `AppGrid` from the home surface** (it moves to the drawer in X3). Home renders a *small*
  first frame.
- `LauncherViewModel`: add favorites (derive from `UsageHistoryRepository`, cap N), drawer-open nav
  event; keep the 3-flow design + `UiState` contract. `HandleUserCommandUseCase` untouched.
- Acceptance: home shows no full grid; Settings/Assistant reachable by icon on device; typed commands
  still work; offline intact.
- **Done:** `SidrScaffold` top bar (`TopBarIcon` Settings + Assistant painter) → `navigateTo`;
  `SidrSearchField` replaced `CommandInputBar` (verbatim `onMicTap`); `HomeContent` renders
  Suggestions (unchanged) + `SectionHeader("Favorites")` + `LazyRow`/`AppTile` (top-N) + bottom "All
  apps" `TextButton` → new `Routes.AppDrawer.ROUTE` (X3 registers the destination; safe-fallback until
  then). `AppGrid`/`AppItem`/`CommandInputBar` deleted. `LauncherUiState.favorites` derived in the
  existing combine (`deriveFavorites`, cap `FAVORITES_COUNT = 8`, usage-order ∩ installed, no new VM
  dep). `core/ui`: `ic_assistant_24.xml` + `TopBarIcon(painter=…)` overload, 0 new deps. 7 new JVM
  tests; `assembleDebug` + `testDebugUnitTest` green. Device pass (§8) pending. See decisions.md
  "ADR 2026-07-03 — Phase UX Block X2 complete".

### Block X3 — App Drawer (all apps, on demand) — ✅ DONE 2026-07-03
- New surface (a destination in the single `NavHost`; a `:feature:appdrawer` module **or** a second
  screen inside `feature/launcher` — pick per U-topology, no `feature→feature` dep): the **full
  installed-apps list** (the old grid), **alphabetical + section headers + fast-scroll**, launch on tap
  (reuses the existing launch path / usage recording).
- Opened via the **All apps button** (U2) + optional **swipe-up** gesture.
- Acceptance: every installed app is reachable in the drawer; scroll/label render is smooth on device;
  launching from the drawer records usage (feeds Favorites/Suggestions).
- **Done:** drawer is a **screen inside `feature/launcher`** (U-topology, no new module). `AppNavHost`
  registers `composable(Routes.AppDrawer.ROUTE)` (mirrors the Settings destination; Back →
  safe-fallback), retiring X2's interim fallback. `AppDrawerViewModel` (`@HiltViewModel`, no
  `HandleUserCommandUseCase`/`IntentMatcher`) exposes `UiState<AppDrawerUiState>` from a **pure**
  `groupIntoSections` (label-sorted, lettered buckets, trailing `#`). `AppDrawerScreen` = `SidrScaffold`
  + Back `TopBarIcon` + `LazyColumn` with `stickyHeader` `SectionHeader`s and compact icon+label
  `DrawerAppRow`s; `Empty`/`Error(retry)` via `core/ui`. Icon helpers `rememberAppIcon`/`toImageBitmap`
  lifted from `LauncherScreen` into an `internal AppIcon.kt` (home + drawer share them). launch/usage is
  a **verbatim copy** of `LauncherViewModel`'s (owner-confirmed; no shared use-case). Fast-scroll =
  sticky headers (A–Z side rail deferred, non-blocking). 15 new JVM tests; `assembleDebug` +
  `testDebugUnitTest` green; 0 new deps. Device pass (§8) pending. See decisions.md "ADR 2026-07-03 —
  Phase UX Block X3 complete".

### Block X4 — Search ⇄ command unification — ✅ DONE 2026-07-03
- The home/drawer **search field filters the app list live** (typed-prefix over installed apps) **and**
  still **submits to the command pipeline** on enter (U6). One field, two behaviors, no regression.
- Optional (cheap, no AI): when the query looks like a question/long phrase, surface a subtle
  "Ask assistant" affordance (routes to `Routes.Assistant`, prompt prefilled) — heuristic only.
- Acceptance: typing filters apps; enter runs commands exactly as today; assistant affordance routes
  correctly; `CommandNormalizer`/matcher unchanged.
- **Done (fork X4-A = filter in the App Drawer; owner-decided):** home stays command-first (X2
  behaviour, zero regression) — live filtering lives in the drawer, which already owns the full list +
  `groupIntoSections`. Pure `filterApps(apps, query)` (case-insensitive **substring**, blank/whitespace
  → full list) added next to `groupIntoSections` in `AppDrawerUiState.kt`. `AppDrawerViewModel`:
  `_query: MutableStateFlow<String>` + `onQueryChanged`, exposed `query: StateFlow<String>`, `uiState =
  combine(_rawAppsResult, _query)` → `filterApps` → `groupIntoSections`; IME "search" →
  `onQuerySubmitted()` launches the **top alphabetical match** through the existing `launchApp`/usage
  path (drawer stays command-free — no `HandleUserCommandUseCase`/`IntentMatcher` added, preserving the
  X3 topology decision). `AppDrawerScreen`: `SidrSearchField` under the top bar (`showMic=false` — voice
  stays on home; built-in Clear), content wrapped in a `Column` + `imePadding()`; `Empty` message keyed
  off `query.isBlank()` ("No apps found." vs "Nothing found."). **"Ask assistant" affordance deferred to
  X6** (owner-decided): routing without prefill is a dead-end and a real prompt-prefill needs a
  deliberate nav-arg design (optional prompt, consumed once, **never** in saved state) — bundled with X6
  polish; the "key never in saved state" invariant is untouched. `HandleUserCommandUseCase`/
  `IntentMatcher`/`CommandNormalizer`/`RuleBasedIntentMatcher` unchanged; `core/ui` untouched; 0 new
  Gradle deps. 10 new JVM tests (`filterApps` substring/case/blank/no-match/trim; VM query→filtered
  sections, clear→full, no-match→Empty, submit→top match, submit no-op). `assembleDebug` +
  `testDebugUnitTest` green. Device pass (SM-A325F) pending (batched). See decisions.md "ADR 2026-07-03
  — Phase UX Block X4 complete".

### Block X5 — Real Settings surface (`:feature:settings`) ✅ DONE (2026-07-03)
- Promote `LauncherSettingsScreen` into a proper, discoverable **`:feature:settings`** (U5) reached by
  the top-bar icon. Options: **theme** (system/light/dark), **AI suggestions** (existing toggle),
  **voice input** on/off, **favorites count**, **Assistant provider** entry (link to the existing
  provider form), **"Set as default launcher"** helper (U7 — `RoleManager`/`ACTION_HOME_SETTINGS`).
- Back it with the existing `UserPreferences`/`FeatureFlags` DataStore repos; no new persistence
  patterns. `:app` keeps only composition-root wiring.
- Acceptance: Settings reachable by icon; each toggle persists across restart; default-launcher helper
  opens the correct system screen; privacy guard (`ALL_KEY_NAMES`) stays green.
- **Shipped (MVP slice, Fork X5-B):** theme + AI suggestions + Assistant provider entry + Set-as-default.
  **voice on/off + favorites-count deferred to X6** (they need new pref fields + `PreferencesKeys`/
  `ALL_KEY_NAMES`/mapper + consumer wiring; deferring keeps X5 off persistence migrations, so
  `PrivacyInventoryGuardTest` is untouched/green). New `:feature:settings` module (Compose + Hilt kapt,
  0 new Gradle deps) holds `SettingsScreen`/`SettingsViewModel`/`SettingsUiState`; the old
  `com.sidr.launcher.settings.*` stub + its test were deleted from `:app`. **Fork X5-A:** new
  `SuggestionScheduling` `:domain` port, impl `SuggestionSchedulingImpl` in `:app` over
  `SuggestionsWorkScheduler`, Hilt-bound — the toggle re-syncs WorkManager without a `feature→:app` edge.
  **Fork X5-C:** default-launcher intent fired from the screen via `LocalContext` (RoleManager
  `ROLE_HOME` on API 29+, else `ACTION_HOME_SETTINGS`), no new port. **Fork X5-D:** `LauncherActivity`
  observes `UserPreferencesRepository` → maps `themeName` → `SidrTheme(darkTheme=…)`; `dynamicColor`
  stays on. 6 new JVM tests (flag toggle+re-sync via fake port, write-failure→error+no-resync, theme
  persist, theme write-failure, assistant nav); `assembleDebug` + `testDebugUnitTest` green. Device pass
  deferred (accumulated per §7). Details: decisions.md "2026-07-03 — Phase UX Block X5 complete".

### Block X6 — Polish, a11y hygiene, first-run, device acceptance — ✅ DONE 2026-07-03
- Loading/empty/error states via `core/ui`; **content descriptions**, **≥48dp touch targets**, focus
  order; light **motion** (drawer open, list reveal); **first-run nudge** (set-as-default + a one-line
  "type or search" hint).
- Device pass on SM-A325F: discoverability (Settings/Assistant by icon), drawer completeness, search,
  home-declutter, and a **cold-start re-measure** (expected improvement from X2's smaller first frame).
- Acceptance: matrix updated; short ADR appended to `decisions.md` in the established format.
- **Done (forks X6-A…E all on the recommended option):** three deferred prefs land in `UserPreferences`
  (`favoritesCount=8` / `micInputEnabled=true` / `setupHintDismissed=false`; keys `user_favorites_count` /
  `user_mic_input_enabled` / `user_setup_hint_dismissed`, all denylist-clean — `PrivacyInventoryGuardTest`
  green). `:feature:settings` gained a HOME section (favorites `FilterChip` selector 4/6/8/10 + voice
  `Switch`) + `setFavoritesCount`/`setMicInputEnabled`. `LauncherViewModel` injects
  `UserPreferencesRepository`, exposes `showMic: StateFlow` (`micInputEnabled && recognizer available`),
  `deriveFavorites` reads the pref (`const FAVORITES_COUNT` removed), `startVoiceInput` no-ops when the
  mic pref is off, and `dismissSetupHint()` persists the one-shot nudge. `LauncherScreen` renders the
  dismissible first-run `SetupNudge` (shown only when `!isDefaultLauncher && !setupHintDismissed`; CTA →
  system launcher chooser) and routes empty/error through `core/ui` `EmptyState`/`ErrorState`. **X6-C**
  "Ask assistant" prefill: optional `Routes.Assistant.prompt` nav-arg, drawer affordance, seeded into the
  assistant input once — never auto-sent, **never in `SavedStateHandle`**. Cold-start = re-measure only
  (no startup-path code). New JVM tests across settings/launcher/persistence; all touched-module test
  tasks + `assembleDebug` green; 0 new Gradle deps. Device pass (SM-A325F, §8/§7) batched-pending. ADR:
  decisions.md "2026-07-03 — Phase UX Block X6 complete". **Phase UX CLOSED.**

## 6. Module & topology impact (hard-rules compliance)

- **`core/ui`** gains the design system (X1). It may depend on `core/common` only; **no** `domain`/
  `data`/`feature` deps into it.
- **`feature/launcher`** hosts the redesigned home (X2, X4). The App Drawer (X3) is either a screen in
  `feature/launcher` or a new **`:feature:appdrawer`** — **never a `feature→feature` edge**; both
  reachable through the single `NavHost` in `:app`, VMs emit `NavigationEvent` (never touch
  `NavHostController`).
- **`:feature:settings`** (X5) mirrors `:feature:permission_education` (Compose + Hilt), replacing the
  `:app` stub. Reads/writes go through existing `domain` repo ports only.
- **`domain` stays pure**; `HandleUserCommandUseCase`, `IntentMatcher`, `SuggestionEngine`,
  `GenerativeAiEngine` contracts **unchanged**. Launcher core stays fully offline.

## 7. Synergy with cold-start (call-out)

The largest cold-start suspect (Round-3 ADR) is the **PackageManager app enumeration + full-grid
render on the first frame**. Block X2 removes the full grid from home → the first frame renders only
favorites (top-N) + suggestions, and the **full app list loads lazily when the drawer opens**. So this
phase and the cold-start perf task reinforce each other; do X2 with a cold-start re-measure in X6.

## 8. Open questions (resolve during Forks)

- **UX-Q1 (U3):** Favorites — auto-only for MVP, or ship manual pin now? (Recommend auto-only; pin
  later.)
- **UX-Q2 (U5):** `:feature:settings` as a new module vs a real screen still in `:app`? (Recommend the
  module for cleanliness; acceptable to defer if module setup cost is high.)
- **UX-Q3 (U2):** Swipe-up gesture in the MVP, or button-only first? (Button is the must; swipe can be
  X3-optional.)
- **UX-Q4:** Visual identity — do we have brand color/name styling for `core/ui`, or pick a neutral M3
  palette now and theme later? (Default: neutral M3 + dynamic color on Android 12+.)

## 9. Definition of done (phase)

- Home shows **no full icon grid**; favorites + suggestions + unified search + discoverable
  Settings/Assistant icons.
- **App Drawer** holds all apps, searchable/ordered, reachable by a visible affordance.
- **Settings** is real, discoverable, and persists; default-launcher helper works.
- `core/ui` design system in place and applied app-wide.
- Hard rules intact; JVM + `assembleDebug` green; device pass on SM-A325F; ADR + `current-status.md`
  synced; cold-start re-measured.
