# SIDR Visual Acceptance Spec v1.1

**Статус:** Target-state visual contract
**Назначение:** дать разработчику и reviewer однозначные критерии экранов и состояний
**Примечание:** спецификация описывает композицию и поведение, а не пиксельный Figma-макет

---

# 1. Global visual rules

## Screen margins

```text
Compact phone: 16 dp
Standard phone: 20 dp
Large phone: 24 dp
```

Default SIDR phone margin:

```text
20 dp
```

## Vertical rhythm

```text
Related text: 4–8 dp
Component content: 12–16 dp
Between components: 16–20 dp
Between sections: 24–32 dp
Sacred breathing space: 24–40 dp
```

## Touch target

```text
48 × 48 dp minimum
```

## Main radius

```text
8 dp
```

## Border

```text
1 dp standard
2 dp focus/risk only
```

## Typography

```text
Interface Sans:
content, settings, explanations, results

JetBrains Mono:
commands, statuses, metadata, execution

Arabic type:
Shahada only and future Arabic content
```

---

# 2. Home — Idle

## Purpose

Provide spiritual anchor, current prayer context, Universal Input and lightweight launcher access.

## Composition

```text
┌─────────────────────────────────────┐
│                                     │
│ لا إله إلا الله محمد رسول الله       │
│                                     │
│ THERE IS NO DEITY EXCEPT ALLAH      │
│ MUHAMMAD IS THE MESSENGER OF ALLAH  │
│                                     │
│ FAJR 04:12              ISHA 22:22  │
│ Next: Fajr · 1h 14m                 │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ > Ask, search, open or automate │ │
│ └─────────────────────────────────┘ │
│                                     │
│ CONTINUE                            │
│ Relevant suggestion or recent task │
│                                     │
│ FAVORITES                           │
│ App tiles                           │
│                                     │
│ ALL APPS                         →  │
└─────────────────────────────────────┘
```

## Rules

* Shahada is visually dominant but not oversized.
* No frame around Shahada.
* No animation.
* Prayer summary is secondary.
* Universal Input is the main interactive element.
* Favorites may appear below context.
* No large app grid.
* Empty space is acceptable.
* No random “AI insight” card.

## Acceptance

```text
[ ] Shahada not clickable
[ ] Arabic not truncated
[ ] translation remains readable
[ ] Universal Input visible without scroll on standard phone
[ ] no network required for first frame
[ ] no Loading spinner on first frame
[ ] 2.0 font scale remains usable
```

---

# 3. Home — Typing / Search Overtakes

## Composition

```text
Sacred Header becomes compact but remains visible
Prayer summary may collapse to one line

┌─────────────────────────────────────┐
│ > telegr▮                       ×   │
├─────────────────────────────────────┤
│ APP   WEB   SITE   ASK              │
│                                     │
│ Telegram                            │
│ Telegram X                          │
│                                     │
│ Search web for “telegr”             │
│ Ask assistant about “telegr”        │
└─────────────────────────────────────┘
```

## Rules

* Results become dominant.
* Favorites/context move below or disappear temporarily.
* Search results do not become cards unless necessary.
* Route chips use clear selected state.
* Block caret appears only in focused input.
* Voice icon is hidden or transformed while typing only according to existing behavior.
* App results remain local.

## Acceptance

```text
[ ] input remains stable during result updates
[ ] no layout jump from async icon load
[ ] route labels fit or wrap
[ ] selected route visible without relying only on color
[ ] clear action touch target 48 dp
```

---

# 4. Home — Ambiguous App Choice

## Composition

```text
> Open bank

CHOOSE AN APPLICATION

I found two matching applications.

┌─────────────────────────────────────┐
│ icon  Türkiye Finans                │
│       Banking                       │
├─────────────────────────────────────┤
│ icon  Ziraat Mobile                 │
│       Banking                       │
└─────────────────────────────────────┘

CANCEL
```

## Rules

* This is clarification, not error.
* No candidate launches until selected.
* Selection may continue to Action Gate if required.
* Maximum five choices before scroll.
* Package names shown only when they help distinguish apps.
* No fake confidence percentages.

## Acceptance

```text
[ ] candidates have 48 dp target
[ ] TalkBack reads app name before metadata
[ ] Cancel is visible
[ ] no app is preselected as if already approved
```

---

# 5. SAFE Routed Proposal

## Composition

```text
PROPOSED ACTION

Open Telegram

Routed from:
“Open my messages”

SAFE

[ OPEN ]
[ CANCEL ]
```

## Rules

* Proposal is clearly not completed.
* One tap may execute SAFE action.
* It must still be a deliberate tap.
* No countdown.
* No automatic execution after animation.
* “SAFE” is supplementary, not the only explanation.

## Acceptance

```text
[ ] action not executed on render
[ ] action not executed on focus
[ ] action executes exactly once
[ ] Cancel dismisses without side effects
```

---

# 6. CONFIRM Action Gate

## Composition

```text
REQUIRES CONFIRMATION

Open:
https://github.com

Reason:
You asked to open the official GitHub website.

Consequence:
This action leaves SIDR and opens
an external application.

EXTERNAL · CONFIRM

[ CANCEL ]            [ CONTINUE ]
```

## Rules

* Consequence is always visible.
* Cancel has equal discoverability.
* Confirm is not red unless destructive.
* Buttons stack vertically at large font.
* URL target must be readable.
* Long URL may wrap; it must not silently truncate the origin.
* Dismiss/back equals Cancel, never Confirm.

## Acceptance

```text
[ ] no execution before Continue
[ ] Cancel produces no action
[ ] double tap does not execute twice
[ ] loading disables controls
[ ] risk represented by text and marker
```

---

# 7. Result — Completed

## Composition

```text
COMPLETED

Opened Türkiye Finans.

Based on your learned preference:
“Open bank”

LOCAL · 14 MS

[ CHANGE ]
```

## Rules

* Result states what happened.
* Learned preference disclosure appears only when useful.
* Metadata remains secondary.
* “Change” does not silently delete memory; it opens correction flow.

---

# 8. Result — Partial

## Composition

```text
PARTIALLY COMPLETED

The route was prepared.

The reminder was not created because
calendar permission is disabled.

[ ENABLE PERMISSION ]
[ VIEW ROUTE ]
```

## Rules

* Never label partial success as completed.
* Completed and failed portions are distinguishable.
* No generic error banner over a successful result.

---

# 9. Learned Choices — List

## Composition

```text
LEARNED CHOICES

Preferences learned from confirmed app choices.
Stored only on this device.

┌─────────────────────────────────────┐
│ LEARNED PREFERENCE                  │
│                                     │
│ “Open bank”                         │
│ Türkiye Finans                      │
│                                     │
│ 3 confirmed choices                 │
│ Last used: 8 July 2026              │
│                                     │
│ LOCAL · ACTIVE                      │
│                            [ OPEN ]  │
└─────────────────────────────────────┘
```

## Rules

* One memory entity per surface.
* Do not expose internal fingerprint.
* Evidence is human-readable.
* Local-only state is visible.
* Delete/Forget is not the primary list action.
* Needs-reconfirmation status is visually distinct.

## Empty state

```text
NO LEARNED CHOICES

SIDR has not saved any app preferences yet.
Preferences appear only after confirmed choices.
```

---

# 10. Forget Memory Gate

```text
FORGET LEARNED CHOICE?

“Open bank” will no longer prefer
Türkiye Finans.

The next ambiguous request will ask
you to choose again.

[ CANCEL ]             [ FORGET ]
```

## Rules

* State exact consequence.
* Do not use vague “Delete data?”.
* Forget is destructive but reversible through relearning.
* No biometric confirmation needed for this low-risk deletion.

---

# 11. Settings

## Composition

```text
SYSTEM

INTELLIGENCE
Smart command routing                 ON
AI suggestions                        ON
Assistant provider                    OpenRouter >

ACTIONS & CONFIRMATIONS
Risk policy                           Standard >
Permission education                  >

MEMORY
Learned choices                       3 >
Usage personalization                 ON

HOME
Favorites                             8 >
Voice input                           ON
Set SIDR as default launcher          >

APPEARANCE
Theme                                 System >
Accent                                Amber >

PRIVACY
Local and cloud processing            >
Stored data                           >

ABOUT
Version                               1.x
```

## Rules

* No card around every row.
* Sections separated primarily by spacing.
* Toggle rows have one clear interaction.
* Status/value aligned but wraps safely.
* Destructive controls live in separate section.
* Raw Material controls may remain internally but must visually match SIDR.

## Acceptance

```text
[ ] no double toggle
[ ] descriptions do not reduce touch target
[ ] font scale 2.0 uses vertical layout where needed
[ ] switches retain system accessibility roles
```

---

# 12. App Drawer

## Composition

```text
←  ALL APPS

┌─────────────────────────────────────┐
│ Search applications             ×  │
└─────────────────────────────────────┘

A
icon  App name
icon  App name

B
icon  App name
```

## Rules

* Utility surface, not agentic Home.
* No Shahada duplication.
* No suggestions.
* No command router.
* Sticky alphabet headers.
* Search stays local.
* Ask Assistant affordance may appear only for non-empty unmatched query.

---

# 13. Permission Education

## Composition

```text
MICROPHONE ACCESS

Voice input lets you speak commands
instead of typing them.

SIDR uses the microphone only while
voice input is active.

Without this permission:
Voice commands remain unavailable.
Typing continues to work normally.

[ CONTINUE ]
[ NOT NOW ]
```

## Rules

* Do not imitate Android permission dialog.
* State what continues to work without permission.
* No pressure language.
* No permission request at startup.
* “Not now” always available.

---

# 14. Assistant

## Composition direction

```text
← ASSISTANT

User prompt
Interface Sans

Assistant answer
Interface Sans

CLOUD · OPENROUTER
System Mono

Input composer
```

## Rules

* Long answers never use Mono.
* Provider metadata uses Mono.
* Cloud disclosure accessible from provider/status area.
* Streaming cursor is not the same as Universal Input block caret.
* Provider key never displayed back.
* Retry appears only for retryable errors.

---

# 15. Activity — Future Contract

## Composition

```text
ACTIVITY

TODAY

● Opened Türkiye Finans
  Based on learned preference
  14:32 · LOCAL

✓ Opened GitHub
  Confirmed external handoff
  13:51

× Web action failed
  No compatible browser
  12:10
```

## Rules

* Timeline reflects real events only.
* Do not display raw sensitive command text by default.
* Expandable detail may show redacted command information.
* No fake agent history.

---

# 16. Execution Stream — Future Contract

```text
EXECUTION

✓ Calendar checked
  LOCAL · 18 MS

● Calculating route
  Navigation tool

○ Preparing reminder
  Waiting

[ CANCEL TASK ]
```

## Rules

* Use only with real multi-step execution.
* Completed means completed.
* Queued means not started.
* Cancel Task does not pretend to undo completed steps.
* Tool names are secondary.
* No constant animated glow.

---

# 17. Theme acceptance

Each major surface must be reviewed in:

```text
Dark Green
Dark Amber
Light Green
Light Amber
Dark Green at 2.0 font scale
RTL where applicable
```

Green and Amber must remain one-accent-at-a-time themes.

No screen may combine both as competing primary accents.

---

# 18. Performance acceptance

Visual redesign fails acceptance if it introduces:

* startup network dependency;
* blocking prayer calculation;
* remote font download;
* shader-heavy background;
* continuous global animation;
* app-wide scanline;
* first-frame Loading spinner;
* visible white flash;
* material cold-start regression attributable to UI effects.

---

# 19. Final visual acceptance checklist

```text
[ ] Islamic spirit is expressed through order and restraint
[ ] Shahada is presented respectfully
[ ] Home remains calm
[ ] Universal Input remains central
[ ] proposal differs from execution
[ ] confirmation explains consequence
[ ] Cancel is always available
[ ] memory is visible and removable
[ ] cloud/local processing is honest
[ ] long content uses Interface Sans
[ ] system metadata uses Mono
[ ] no meaningless cyberpunk decoration
[ ] no card around every section
[ ] all controls meet 48 dp target
[ ] large font remains usable
[ ] RTL is correct
[ ] first frame remains local and stable
```
