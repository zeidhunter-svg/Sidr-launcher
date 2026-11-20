# Sidr Launcher

An AI-first Android launcher (Android 9+, API 28+) on its way to an **agentic OS**: you type, say or tap
a command; Sidr understands it, proposes what it would do, and does it only through deterministic,
consented gates. The launcher core — home, app drawer, settings, app launch — works fully offline,
with no AI at all.

The agent core is portable and has **two consumers**: the Android launcher and a headless JVM harness
(`:consumer:jvm`) that runs the same engine contracts on the desktop with no Android artifact.

## Principles

- **Understanding belongs to the model; execution belongs to the deterministic layer**
  (ADR 1/4). A model may read the command; nothing it proposes runs, gains rights or leaves the
  device except through the gates below.
- **Boundaries first.** Registry as the only path to the world, argument validation, consent, loop
  limits, trace, an outbound allow-list and undo were laid on the first slice, not added later.
- **Local by default; the cloud is an accelerator.** Whatever can run on the device runs there.

## How a command is handled

`RouteCommandUseCase` (`domain/ai/router`) is an ordered chain; the first step that answers wins:

1. **FastPath** — `RuleBasedIntentMatcher`, deterministic, `en` / `ru` / `tr` vocabulary. A latency
   optimization, not a filter on understanding: a miss is never «Unknown command».
2. **The deterministic agent** — FastPath's «no such app» becomes a two-step plan (launch → offer the
   store); **2b** tries an undecided command against the registered tool vocabulary; **2c** replays a
   **learned command** — a model reading the user confirmed three times, kept as a form with slots and
   a validated template, replayed on the device with no model.
3. **Honest gates** — local-only mode, no provider, offline: each says which it is; nothing is sent.
4. **The bridge — the one model path.** BYOK, any OpenAI-compatible endpoint, key in the Android
   Keystore. The command and the authored tool descriptions go out; the model's answer is validated,
   bound to apps on the device and shown as an **interpretation card**. Nothing runs before
   «Выполнить» (Run), and then only through the engine.

Every acting path ends in the **engine**: `AgentExecutor` → `ToolExecutor`, the only path to the
world (one call site, below the consent checkpoint), over a federated `ToolRegistry` — launcher
actions, Android system intents, app shortcuts as tools, the launcher's own memory. Risky steps ask
every time, learned commands included; sessions persist in Room and survive process death; finished
plans offer undo for what can be undone.

## Privacy

- **Local-only mode** (`flag_local_only`): the model is never consulted and nothing leaves the device;
  FastPath, the tool vocabulary and learned commands still work.
- **Outbound content is a positive allow-list** (`OutboundContextPolicy`), widened only by an ADR with
  a guard test proving a planted sentinel never leaves the device.
- **Memory stays on the device**: learned resolutions, aliases and learned commands are visible and
  deletable in Settings, and every database and DataStore file is excluded from Auto Backup and device
  transfer (`BackupRulesGuardTest`).

## Modules

From `settings.gradle.kts`:

| Module | Role |
|---|---|
| `:app` | Composition root, Hilt graph, the single `NavHost`, guard tests |
| `:domain` | Pure Kotlin Multiplatform (`android` + `jvm`): models, ports, use cases, the agent engine, the bridge, the learned-plan cache |
| `:data:repository` | Repositories, FastPath, tool sources and workers, Room (schema 5) and DataStore |
| `:data:ai-cloud` | Ktor client: assistant streaming (SSE) and the bridge's transport |
| `:data:prayer` | Offline prayer-time calculation (`adhan2`), bundled city index |
| `:core:common` · `:core:ui` · `:core:android` · `:core:testing` | State/navigation contracts · design system · Android helpers · shared fakes |
| `:feature:launcher` · `:feature:assistant` · `:feature:suggestions` · `:feature:settings` · `:feature:prayer` · `:feature:permission_education` | Feature UI and ViewModels; no feature depends on another |
| `:consumer:jvm` | Second consumer: headless JVM harness, depends on `:domain` only |
| `:baselineprofile` | Baseline profile generator |

Architecture in detail: [docs/architecture.md](docs/architecture.md); agentic target architecture:
[docs/agentic-os-architecture.md](docs/agentic-os-architecture.md).

## Build

Kotlin 2.4.10, AGP 9.3.1, Gradle 9.5.0, compileSdk 37, Jetpack Compose, Hilt, Room, DataStore, Ktor,
WorkManager. **Gradle needs JDK 17** — a newer default JDK breaks it.

```bash
./gradlew assembleDebug -Porg.gradle.java.installations.paths=<path to JDK 17>
tools/gate.sh            # the full gate (boundary run); SIDR_JDK17 points it at a JDK 17
```

The gate is `:domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test` (`testDebugUnitTest`
reaches neither `:domain` nor `:consumer:jvm`, so both are named). `tools/gate.sh` clears every
`build/test-results`, passes `--rerun-tasks`, counts tests from the JUnit XML and says `GATE GREEN`
only with zero failures and a total at or above its floor. Never pipe `gradlew` through `tail`: it
hides the exit code.

## Status and history

- Current state, rules and known debt: [CLAUDE.md](CLAUDE.md) and
  [ai-context/current-status.md](ai-context/current-status.md).
- Active plan: [docs/superpowers/plans/2026-08-18-agentic-track-restart.md](docs/superpowers/plans/2026-08-18-agentic-track-restart.md);
  governing documents: [docs/governing/](docs/governing/).
- All history, one ADR per closed block: [ai-context/decisions.md](ai-context/decisions.md).
