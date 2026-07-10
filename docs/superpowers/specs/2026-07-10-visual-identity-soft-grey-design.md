# SIDR Visual Identity — "Soft Classic Grey" (Design Spec)

> **Status: APPROVED (owner, 2026-07-10)** via brainstorming + live mockups. This spec defines SIDR's
> visual identity: the color system, type roles, shape/motion stance, the Home screen composition, and the
> governing visual rules. It is the source of truth for the **DS-1 token layer** and feeds **DS-4 (Home)**,
> **DS-5 (action/safety)**, and **DS-6A (sacred header)** of the design-system migration.
>
> **Supersession:** the color/accent portion of the imported design docs is **replaced** by this spec.
> The prior "green default / amber alt, user-selectable" accent system (AIL-0 / v1.1 Doctrine) and the
> "amber-only" v1 palette are **both dropped** in favour of a single neutral **soft classic grey** identity.
> Everything else in the doctrine — principles, base-4 spacing, motion policy, accessibility, "risk not by
> colour alone", the confirmation model, `core/ui` boundaries — remains in force.
>
> **Reference mockup:** dark + light Home + token/moment preview →
> https://claude.ai/code/artifact/177507de-9dc2-4cdb-95f1-75e92f1ca26f

## 1. Character

Neutral, timeless, low-drama. SIDR reads as a **precise instrument**, not a poster and not a cyberpunk
terminal. The palette is a single refined grey; colour is used sparingly and only to mean something. The
terminal DNA survives as **gestures** (the `>` prompt, the block caret, press-invert selection), not as a
green colour scheme. Warmth/identity comes from restraint and typography, not from an accent hue.

Design formula (unchanged from doctrine): **calm by default · system truth over visual effect · every
element has a function · visible agency · progressive disclosure · local-first, cloud visible.**

## 2. Color system

Dark is the default theme; light is a first-class "classic paper grey". **The brand accent and the
semantic status palette are two separate systems** (owner-locked): the accent is a quiet pewter used for
interactivity; status colours are fixed and never move — so risk/success/etc. always read the same
regardless of any future theming.

### 2.1 Dark (default)

| Token | Hex | Use |
|---|---|---|
| `ground` | `#131415` | screen base (soft near-black, neutral, slightly lifted) |
| `surface` | `#1B1C1E` | tiles, input field, raised rows |
| `raised` | `#212325` | modals, floating surfaces |
| `line` | `#2A2C30` | hairlines, dividers |
| `border` | `#3A3D42` | control borders |
| `text` | `#E8E9EB` | primary text; also the **press-invert fill** |
| `sacred` | `#CBCDD1` | Shahada text (dignified mid-tone, not faint) |
| `dim` | `#A0A2A8` | secondary text |
| `faint` | `#6E7076` | metadata, captions, provenance |
| `accent` | `#9BA1AB` | **pewter** — prompt/caret, focus, next-prayer hint. Sparingly. |
| `accent-border` | `#494D54` | input focus border |

### 2.2 Light ("classic paper")

| Token | Hex | Use |
|---|---|---|
| `ground` | `#ECEDED` | screen base |
| `surface` | `#F5F6F7` | tiles, input |
| `raised` | `#FFFFFF` | modals |
| `line` | `#E0E1E4` | hairlines |
| `border` | `#C9CBCF` | control borders |
| `text` | `#1C1E21` | primary / invert fill |
| `sacred` | `#3D4046` | Shahada |
| `dim` | `#5C5F65` | secondary |
| `faint` | `#8A8D93` | metadata |
| `accent` | `#5F6773` | pewter (darkened for contrast) |
| `accent-border` | `#BCC0C6` | focus border |

### 2.3 Semantic status (FIXED — separate from accent, both themes)

Never re-tinted by the accent. Every status also carries a text label + icon/marker (colour is never the
only signal — doctrine rule retained).

| Role | Dark | Light | Meaning |
|---|---|---|---|
| `success` | `#8AA892` | `#5E7D66` | local / safe / completed; the **LOCAL** dot |
| `attention` | `#C6A15C` | `#94702E` | cloud / warning; the **CLOUD** dot |
| `caution` | `#B8836A` | `#9A6142` | confirm / external handoff (terracotta) |
| `danger` | `#C2695C` | `#A24A3E` | destructive / failed |
| `info` | `#8593A0` | `#5B6675` | neutral status / muted slate |

## 3. Type roles (tri-font, boundary is *interface vs prose vs sacred*)

Owner-relevant deviation from the imported docs: **mono stays wide** — a launcher is mostly interface, and
the mono shell is the recognizable signature. Sans is reserved for genuine prose.

- **System — JetBrains Mono** (`--mono`): the `>` prompt, commands, statuses, prayer times, dates,
  provenance lines, section labels, chips, app captions, all metadata, execution/step text. The interface
  shell.
- **Human — system sans** (`system-ui`): app labels in prose contexts, buttons, descriptions, settings
  copy, assistant answers, permission-education paragraphs, long-form prose.
- **Sacred — system serif** (`--serif`, e.g. Iowan/Palatino/Georgia stack): the Shahada, and optionally
  long-form assistant prose. When Arabic is shown, a proper Naskh Arabic face (not mono, not the serif) —
  Arabic support is retained even though the default Home shows the English Shahada.

Scale carries hierarchy (base doctrine scale retained). Uppercase only for short system labels / statuses /
section headers, never for prose or the Shahada translation.

## 4. Shape & motion

- **Radius:** softened to **10dp** default (input, tiles, moments), 7dp chips, 12dp modals. Not brutalist-
  sharp, not pill. Hairline `1px` lines; `2px` only for focus/risk.
- **Retained terminal gestures:** `>` prompt marker, **block caret** (only on real input focus), **press-
  invert selection** (active chip = light-on-dark fill using `text`, colourless).
- **Removed globally:** CRT/scanlines, phosphor glow, always-on decorative motion. Allowed only in a
  developer console / boot experiment / optional visual mode.
- **Motion:** explains state change only; durations 100–360ms (doctrine tokens). **Reduce-motion:** block
  caret static, transitions shortened/fade, progress stays functional.

## 5. Home composition (approved)

Top → bottom. Dense and functional; ceremony is minimal.

1. **Android status bar** — the system's own; the app **does not render or duplicate** time/battery/signal.
2. **Brand row** — `SIDR` wordmark (mono) left; right = **date in Gregorian (bold) over Hijri** + a settings
   gear. **No clock** (Android owns it). Search is the universal input, so no separate search affordance.
3. **Shahada** — English, two lines, **serif**, `sacred` tone, centered, quiet. Non-interactive, no
   animation, no truncation, not a card, not a logo. (Doctrine sacred rules retained.)
4. **Hairline separator.**
5. **Prayer strip** — thin, quiet: five times in tiny mono; the **next** prayer marked by brightness (bolder
   `text`, not a hue) + a small `next · Maghrib 1h 17m` hint in `accent`; a **provenance line**
   (`Diyanet · Istanbul · updated 2h ago`, faint mono). Prayer *data* is a separate subsystem — see §7.
6. **Universal input** — prominent, the primary tool: `>` + block caret + placeholder ("Ask, search, open or
   automate") + mic. Focus border = `accent`.
7. **Route chips** — `APP / WEB / SITE / ASK`; active = **press-invert** (light fill), not a colour.
8. **Favorites** — 4-column grid, 8 apps, with labels.
9. **Recent** — compact list (last command, recent site, recent search) with faint meta on the right.
10. **All apps** — a single row link.
11. **Privacy line** — `LOCAL-FIRST · cloud only when you ask` (faint mono).

Idle Home is calm; typing overtakes with results (existing AIL-3 "search overtakes" behavior, unchanged).

## 6. Governing rules (carried + owner additions)

1. **Accent ≠ status.** Two separate palettes (§2.1/§2.2 vs §2.3). Accent is barely present; status is fixed.
2. **Provenance line — a named primitive.** `SOURCE · detail · freshness` (faint mono), used everywhere a
   thing has an origin: prayer (`Diyanet · updated`), memory (`LOCAL · last used`), results (`LOCAL · 14ms`),
   routing (`ROUTED BY AI`), local/cloud. This is "system truth" made reusable.
3. **Press-invert selection** — the active/selected state is colourless (light-on-dark), so selection never
   competes with status colour.
4. **The more ordinary the action, the less UI it generates.** A plain app launch produces **no** result
   card, **no** disclosure, **no** toast. Disclosures/result surfaces appear only for routed/agentic/risky
   actions or a meaningful learning event.
5. **Terminal gestures kept, terminal *colour* dropped.** `>`, block caret, press-invert stay; green
   phosphor / scanlines go.
6. **Risk never by colour alone** — label + marker + consequence text + separate confirm (doctrine).
7. **First frame** — cached prayer + static text + local theme; no network, no spinner, no white flash.
8. **Accessibility** — 48dp touch targets; usable at font-scale 2.0 (buttons wrap, rows stack); RTL-correct
   for Arabic when shown; status colour-independent; visible focus.

## 7. Prayer times — correctness bar (owner addition)

Prayer times are a **religious-correctness** surface, not a widget: a wrong time is a real error. The
subsystem (its own track — **DS-6B**, separate data spec, not part of DS-1) must: resolve locality; use an
explicit, user-selectable **calculation authority/method** (default a locally-accepted source, e.g. Diyanet
in TR); be timezone- and DST-correct; cache offline. **Never display a computed time without provenance
(source · method · freshness), and fail visibly rather than show a plausible-but-wrong time.** Precise
location never leaves the device / never enters an `AiRequest`.

## 8. Implementation sequencing (feeds the DS migration)

This spec is the *what*; the DS-block plan is the *how* (foundation-first, screens later):

- **DS-1 — tokens:** implement §2 (both themes) + §3 type roles + §4 shape/motion as `SidrColors` /
  semantic colours / `SidrTypography` / `SidrShapes` / `SidrMotion`, **additive**, zero screen change.
  Also stand up the **screenshot-test harness** (Roborazzi/Paparazzi) + preview matrix — a prerequisite the
  imported plan assumes but the repo lacks.
- **DS-2/3 — primitives & controls:** including the **provenance line** primitive (§6.2) and press-invert
  chips.
- **DS-4 — Home:** the §5 composition, preserving all routing/parity (no behavior change).
- **DS-6A — sacred header:** the Shahada component (English default; Arabic-capable).
- **DS-6B — prayer data:** the §7 subsystem (separate spec; brainstorm → plan of its own).

## 9. Scope / non-goals

- This spec changes **presentation only**. No routing/execution/privacy/memory/navigation semantics change;
  parity rules from the migration plan apply.
- Drops the user-selectable green/amber accent. If a future "accent choice" is wanted, it would be a small
  set of **grey temperatures** (neutral/warm/cool), not competing hues — deferred, not in scope now.
- Prayer *data* engineering (DS-6B), the 5-surface nav, Agents/Activity surfaces — all out of scope here.
