# Design — Auto-hiding bottom navigation (idle-hide + reveal handle)

**Date:** 2026-07-12 · **Status:** approved (owner) · **Track:** owner feature (not a DS block)

## Goal

Make Home (and the other tab roots) feel calmer and more spacious. The owner wants the bottom
navigation to recede by default and be summoned on demand — motivations **A (cleanliness/minimalism)**
and **C (calm/focus)** from the brainstorm.

The naive version the owner first proposed — *"hidden by default, tap anywhere to reveal"* — is an
anti-pattern for primary navigation (discoverability loss + the reveal tap collides with app-launch
taps and the just-shipped tap-to-dismiss-keyboard). The approved model replaces it with **hide-on-idle**.

## Behaviour

- **Applies to all five tab roots** (Home, Apps, Tasks, Agents, Activity), uniformly. On Home the
  hidden chrome is the `SidrAppFooter` **and** `SidrTabBar`; on the other tabs it is just `SidrTabBar`.
- **Visible on entry.** When a tab root appears, its bottom chrome is shown.
- **Hides after idle.** After `NAV_AUTO_HIDE_MILLIS` (5 s) with no navigation interaction, the chrome
  slides down/collapses, leaving a thin, low-contrast **handle** (a drawer-style pill) at the bottom.
- **No friction while navigating.** Tapping a tab re-navigates → the destination re-enters composition
  → its chrome starts visible again, so active tab-hopping never requires summoning the bar.
- **Reveal = tap the handle.** Restores the chrome and restarts the idle timer. (Deliberately *not* a
  bottom-edge swipe — that collides with Android's system gesture-nav home swipe.)
- **Pin escape hatch.** A Settings toggle **"Always show navigation bar"** (off by default). When on,
  the chrome is permanently visible and the handle/timer are inert.

## Architecture (presentation-only; no VM/domain/nav-graph change)

- **State lives per tab-root in `TabRootScaffold`** (`app/navigation/AppNavHost.kt`): a plain
  `remember { mutableStateOf(true) }` `chromeVisible`. Plain `remember` (not `rememberSaveable`) is
  deliberate — Navigation-Compose disposes a non-current tab root, so each *arrival* re-initialises to
  `true` = "visible on entry" for free. A `LaunchedEffect(alwaysShowNav, chromeVisible)` runs the 5 s
  idle timer (guarded off when pinned).
- **Animation:** `AnimatedVisibility` (`expandVertically`/`shrinkVertically` + fade, from the bottom)
  on the chrome, and a fade on the handle. Because the chrome sits in the `Scaffold` `bottomBar`, its
  collapsing height animates the content inset → content smoothly breathes into the freed space.
- **New primitive `SidrChromeHandle`** (`app/navigation/SidrTabScaffold.kt`, next to `SidrTabBar`): a
  centred ~36×4 dp pill in a full-width tappable row, `navigationBarsPadding()` so it clears the system
  gesture area, `onClickLabel = "Show navigation bar"` for TalkBack.
- **Toggle plumbing** (mirrors the existing `accentColor`/`micInputEnabled` pattern exactly):
  - `UserPreferences.alwaysShowNavBar: Boolean = false`
  - `PreferencesKeys.USER_ALWAYS_SHOW_NAV_BAR = "user_always_show_nav_bar"` (denylist-clean; added to
    `ALL_KEY_NAMES` so `PrivacyInventoryGuardTest` covers it) + `PreferencesMapper` read/write.
  - `SettingsUiState.alwaysShowNavBar` + `SettingsViewModel.setAlwaysShowNavBar()` + a `SidrToggleRow`
    in the **APPEARANCE** section of `SettingsScreen`.
  - `LauncherActivity` already collects `UserPreferences`; it passes `alwaysShowNav =
    preferences.alwaysShowNavBar` into `AppNavHost`, which threads it into every `TabRootScaffold`.
    No new ViewModel.

## Non-goals / YAGNI

- No hide-on-scroll (portrait Home rarely scrolls → wouldn't serve the calm goal).
- No first-run coach-mark for v1 (the handle is a visible affordance; revisit if users miss it).
- No per-tab configurability, no configurable timeout in Settings (5 s fixed).

## Testing

- Reuse existing unit suites (no VM/domain change → all ViewModel tests pass byte-for-byte).
- Add `PreferencesMapper` round-trip coverage for the new field via the existing preferences test.
- Device acceptance on SM-A325F: visible-on-entry, hide-after-5 s, tap-handle-reveals, tab-hop keeps it
  up, Settings pin forces always-on, portrait + landscape.
