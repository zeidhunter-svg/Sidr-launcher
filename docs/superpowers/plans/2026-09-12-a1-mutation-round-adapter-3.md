# A1″ adapter #3 — mutation round (plan item 7a)

Prover round on the guards added or strengthened by `f8d0051` (the `app_shortcut` adapter) and its two
fix rounds `737f3a5` / `c502bea`. Repo `/home/Suleiman/Sidr-launcher`, branch `launcher--7`, HEAD
`c502bea`, tree clean before and after.

**Method.** One mutation per shell invocation; plant → assert on file content that the old text is gone
and the new text is present at the expected count → run Gradle → `trap 'git checkout -- "$F"' EXIT`
restores. `git status --short` verified empty between every mutation. No production code was written,
nothing was fixed, and no guard assertion was widened or relaxed. Two guard *test* files were mutated
(M6, M14) — deliberately, to measure whether a fix-round addition is load-bearing; both were reverted.

**Toolchain.** `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 <tasks> --rerun-tasks [--continue]`.
Every run below printed `N actionable tasks: N executed`. No `--rerun`, no `tail`, no connected tests.
Counts read from the JUnit XML, never the console.

**Baseline** (`--rerun-tasks`, `289 actionable tasks: 289 executed`, exit 0):
`:app` 59/0 · `:feature:launcher` 191/0 · `:data:repository` 296/0 — matching `c502bea`'s commit message.

**A methodological correction made mid-round, recorded because it is the round's own failure mode.**
The first run of M13 used two test tasks without `--continue`. The second task never executed, and the
XML left on disk from the *previous* mutation was read as if it were this run's result — a false RED that
would have inverted M13's conclusion. Every multi-module run thereafter deletes
`<module>/build/test-results` before Gradle and passes `--continue`; M2 and M3, the two earlier
multi-module runs, were re-run under that rule and are reported from the re-runs.

---

## Results

18 mutations planted, 18 behaved as predicted. `GE` = Gradle exit code.

| # | Edit | File | Command (module scope) | Predicted | Observed | Failing test(s) + assertion | Revert verified |
|---|---|---|---|---|---|---|---|
| 1 | `shortcutSource()`'s fake query → `query = { emptyList() }` | `app/src/test/.../doctrine/DoctrineGuardTest.kt` | `:app:testDebugUnitTest --tests "*DoctrineGuardTest*"` | RED | **RED**, GE=1, 5/8 red | `no two registered tools share an id`, `every registered tool's declared risk is pinned here`, `every tool in the production federation has a non-generic label on the surface` — all three on the shared floor: *"The app_shortcut adapter declared no tool, so every loop in this class skipped the whole third adapter in silence … Adapters currently declare: [launch_app, play_store_search, set_timer, open_system_settings] expected:<true> but was:<false>"*; `the graph's own federation reaches exactly the declared tool levels` — *"Reached: [in_app, system_intent]"*; `every tool the graph's own federation reaches has its risk pinned here` — *"The graph's federation reached no app_shortcut tool, so the family pin below checked nothing"* | ✅ `git status --short` empty |
| 2 | `risk = ActionRiskLevel.SAFE` → `CONFIRM` | `data/repository/.../shortcut/ShortcutToolSource.kt` | `:app:testDebugUnitTest :data:repository:testDebugUnitTest --continue` | RED | **RED**, GE=1 | `:app` — `every registered tool's declared risk is pinned here` and `every tool the graph's own federation reaches has its risk pinned here`, both: *"Drifted: [shortcut:com.example.chat/new_message: family-pinned SAFE, declared CONFIRM] expected:<[]>"*. `:data:repository` — `ShortcutToolSourceTest > every shortcut becomes one descriptor with shortcut provenance`: *"expected:<SAFE> but was:<CONFIRM>"* | ✅ |
| 3 | `snapshot()`: `adapters.map {…}` → `adapters.filter { it.level != ToolLevels.APP_SHORTCUT }.map {…}` (adapter stays registered) | `domain/src/commonMain/.../tool/ToolFederation.kt` | `:app:testDebugUnitTest :domain:jvmTest --continue` | RED (**headline**) | **RED**, GE=1, 5 red in `:app`; `:domain:jvmTest` 438/0 **green** | `DoctrineGuardTest > the graph's own federation reaches exactly the declared tool levels` — *"Reached: [in_app, system_intent]"*; `DoctrineGuardTest > every tool the graph's own federation reaches has its risk pinned here`; and all three `ToolRegistryPermissionGuardTest` tests that read `productionFederation()` — *"No app_shortcut tool reached this guard, so every assertion below skipped the whole third adapter in silence … Registered: [launch_app, play_store_search, set_timer, open_system_settings]"* | ✅ |
| 4 | `provideDynamicToolLabels` body → `emptyMap<ToolId, DynamicToolLabel>()` | `app/src/main/.../di/AgentProvidesModule.kt` | `:app:testDebugUnitTest` | RED | **RED**, GE=1, 3 red | all three `DynamicToolLabelWiringTest` tests; e.g. `the projection carries both halves of a dynamic name across the module boundary`: *"expected:<{ToolId(value=shortcut:com.a/new_chat)=DynamicToolLabel(qualifier=Telegram, name=New message)}> but was:<{}>"* | ✅ |
| 5 | `provideDynamicToolNames` returns a **second, freshly constructed** `ShortcutToolSource` (over its own `ShortcutCatalog`) instead of the injected singleton | `app/src/main/.../di/AgentProvidesModule.kt` | `:app:testDebugUnitTest` | RED | **RED**, GE=1, 3 red | `DynamicToolLabelWiringTest > the names face and the registry face are one instance, so a name cannot label a tool that is gone` fails on the `assertSame` itself: *"provideDynamicToolNames must hand back the SAME object the federation registers … expected same:<ShortcutToolSource@2de56eb2> was not:<ShortcutToolSource@…>"*; the other two `DynamicToolLabelWiringTest` tests also red | ✅ |
| 6 | Delete the third `ToolAdapter`, the `shortcutSource()` helper and **both** `PREFIX` filters; floor in `productionFederation()` left in place | `app/src/test/.../agent/ToolRegistryPermissionGuardTest.kt` | `:app:testDebugUnitTest --tests "*ToolRegistryPermissionGuardTest*"` | RED in 3 of 4 | **RED in exactly 3 of 4**, GE=1 | `every permission a registered tool needs is declared in the manifest`, `every registered tool has a permission row`, `the registry this guard reads contains the tools this federation is known to ship` — all on the floor: *"No app_shortcut tool reached this guard … [REQUIRED_TOOL_IDS] cannot catch this — a shortcut id is device-dependent and so cannot be written down"*. `the manifest scan reads real permissions` stayed green: it is the one test that does not read `productionFederation()` | ✅ |
| 7 | `import java.net.URL` + `private val probeHost: String = URL("https://example.com/").host` | `data/repository/.../shortcut/ShortcutRefreshTrigger.kt` | `:app:testDebugUnitTest --tests "*DoctrineGuardTest*"` | RED | **RED**, GE=1, exactly 1 red | `DoctrineGuardTest > no registered adapter names a network type`: *"ShortcutRefreshTrigger.kt names java.net — a tool source may not reach the network expected:<false> but was:<true>"* | ✅ |
| 8 | Remove the `ToolLevels.APP_SHORTCUT.value ->` arm from `provenanceLabelFor` | `feature/launcher/.../agent/AgentSessionPresentation.kt` | `:feature:launcher:testDebugUnitTest` | RED | **RED**, GE=1, exactly 1 red | `LauncherScreenAgentProvenanceTest > a shortcut tool renders its own name and its app_shortcut provenance`: *"Expected exactly '1' node but could not find any node that satisfies: (ContentDescription = 'source APP SHORTCUT · EXTERNAL')"* — `DOC-ILM-2` held behaviourally, through a rendered node, not a mapping table | ✅ |
| 9 | Remove the third `ToolAdapter(…)` from `provideToolFederation` | `app/src/main/.../di/AgentProvidesModule.kt` | `:app:testDebugUnitTest` | RED (both new real-graph tests) | **RED**, GE=1, 3 red | `the graph's own federation reaches exactly the declared tool levels`; `every tool the graph's own federation reaches has its risk pinned here`; **plus** the textual count-pin `the composition root registers exactly the declared adapters`: *"ToolAdapter( constructions found in AgentProvidesModule.kt: 2 expected:<3> but was:<2>"* | ✅ |
| 10 | Drop `remember(session)` from both dynamic maps | `feature/launcher/.../LauncherScreen.kt` | `:feature:launcher:testDebugUnitTest` | RED | **RED**, GE=1, exactly 1 red | `LauncherScreenAgentProvenanceTest > typing does not re-walk the federation behind the agent surface`: *"Eight keystrokes re-walked the registry … expected:<1> but was:<9>"* — the exact number fix round 1 claimed | ✅ |
| 11 | `usable()`: round-trip equality → `parse(of(p, s)) != null` | `data/repository/.../shortcut/ShortcutToolSource.kt` | `:data:repository:testDebugUnitTest` | RED | **RED**, GE=1, exactly 1 red | `ShortcutToolSourceTest > a shortcut whose id round-trips to a different pair is advertised in neither face`: *"expected:<[ToolId(value=shortcut:com.a/new_chat)]> but was:<[ToolId(value=shortcut:com.a/b/x), ToolId(value=shortcut:com.a/new_chat)]>"* | ✅ |
| 12 | `ShortcutToolIds.of` mints `…/<id>#<counter>` (unstable) | `data/repository/.../shortcut/ShortcutToolIds.kt` | `:data:repository:testDebugUnitTest` | RED | **RED**, GE=1, 8 red | `ShortcutToolIdsTest > an id round-trips through parse`: *"expected:<ToolId(value=shortcut:com.example.chat/new_message)> but was:<ToolId(value=shortcut:com.example.chat/new_message#9)>"*; `ShortcutToolIdsTest > only the first slash separates…`; six `ShortcutToolSourceTest` tests. **See finding F2**: `an id is stable across a refresh that re-labels the same shortcut` died on `NoSuchElementException: List is empty`, *not* on its stability assertion | ✅ |
| 12b *(mine)* | `all()` derives the id from `shortcut.shortcutLabel` instead of `shortcutId` — unstable **and** still round-trips, so `usable()` does not filter it out | `data/repository/.../shortcut/ShortcutToolSource.kt` | `:data:repository:testDebugUnitTest` | RED, on the stability assertion | **RED**, GE=1, 5 red | `ShortcutToolSourceTest > an id is stable across a refresh that re-labels the same shortcut` — now on its own assertion: *"expected:<ToolId(value=shortcut:com.a/New message)> but was:<ToolId(value=shortcut:com.a/Новое сообщение)>"* | ✅ |
| 13 *(mine)* | `PlanStep.line`: `val dynamic = dynamicLabels[invocation.id]` → `val dynamic: DynamicToolLabel? = if (dynamicLabels.isEmpty()) null else null` — the dynamic-name branch becomes unreachable while the resource id stays in the file | `feature/launcher/.../agent/AgentSessionPresentation.kt` | `:app:testDebugUnitTest :feature:launcher:testDebugUnitTest --continue` | `:app` **GREEN** (scan blind), `:feature:launcher` RED | `:app` **GREEN 59/0**; `:feature:launcher` **RED**, 2 red | `LauncherScreenAgentProvenanceTest > a shortcut tool renders its own name and its app_shortcut provenance` and `> typing does not re-walk the federation behind the agent surface`, both: *"The component with Text … contains 'Open “Example Chat — New message”' as substring is not displayed!"* — **finding F1** | ✅ |
| 14 *(mine)* | `shortcutToolPermissions = listOf("com.sidr.NEVER_DECLARED")` | `app/src/test/.../agent/ToolRegistryPermissionGuardTest.kt` | `:app:testDebugUnitTest --tests "*ToolRegistryPermissionGuardTest*"` | RED (the one direction the KDoc claims) | **RED**, GE=1, exactly 1 red | `every permission a registered tool needs is declared in the manifest`: *"Registered, reachable, and refused by ActivityTaskManager at every invocation: [(shortcut:com.example.chat/new_message, com.sidr.NEVER_DECLARED)]"* — the row's claimed direction is real | ✅ |
| 15 *(mine)* | `ToolFederation` computes `snapshot()` once into `private val cached` and both faces + the executor read it (the pre-A1″ shape) | `domain/src/commonMain/.../tool/ToolFederation.kt` | `:domain:jvmTest :app:testDebugUnitTest :data:repository:testDebugUnitTest :feature:launcher:testDebugUnitTest :consumer:jvm:test --continue` | unknown | **RED in `:domain` only**, GE=1 | `ToolFederationTest > a source that gains a tool after construction is visible through both faces[jvm]`: *"expected:<[first, second]> but was:<[first]>"*; `> a source that loses a tool stops advertising and stops routing it[jvm]`. `:app` 59/0, `:data:repository` 296/0, `:feature:launcher` 191/0, `:consumer:jvm` 56/0 all green | ✅ |
| 16 *(mine)* | `ShortcutToolSource.names()` → `emptyList()` (tool registered, unnamed) | `data/repository/.../shortcut/ShortcutToolSource.kt` | `:app:testDebugUnitTest :data:repository:testDebugUnitTest --continue` | RED | **RED**, GE=1, 4 red in `:app`, 3 in `:data:repository` | `DoctrineGuardTest > every tool in the production federation has a non-generic label on the surface`: *"'shortcut:com.example.chat/new_message' is registered but absent from DynamicToolNames.names(), so the surface has no name for it and it will render as the generic step line … forever"* — Task 8's positive requirement bites; plus all three `DynamicToolLabelWiringTest` tests and three `ShortcutToolSourceTest` tests | ✅ |
| 17 *(mine)* | Move the `APP_SHORTCUT` adapter **first** in `provideToolFederation`'s list (first-adapter-wins precedence inverted; count unchanged at 3) | `app/src/main/.../di/AgentProvidesModule.kt` | `:app:testDebugUnitTest` | RED | **RED**, GE=1, 2 red | `the composition root registers exactly the declared adapters`: *"Found: [ToolLevels.APP_SHORTCUT, ToolLevels.IN_APP, ToolLevels.SYSTEM_INTENT] expected:<[ToolLevels.IN_APP, ToolLevels.SYSTEM_INTENT, ToolLevels.APP_SHORTCUT]>"*; and `the graph's own federation reaches exactly the declared tool levels`. The textual scan's claimed unique value — seeing **order** — is real | ✅ |

---

## Properties now proved

Each of the following is proved in the only sense this repository accepts: a mutation that compiles,
violates the property, and turns a **named** test red on the assertion that names it.

1. **The non-vacuity floors are load-bearing** — in `DoctrineGuardTest.productionAdapters()` (M1) and in
   `ToolRegistryPermissionGuardTest.productionFederation()` (M3, M6). An adapter that registers nothing
   no longer passes in silence, in either file.
2. **The `app_shortcut` risk family is pinned** in both readings — pre-dedup over the replica list and
   post-dedup over the graph's own federation (M2).
3. **The headline is closed.** A federation that silently drops the entire third adapter while leaving it
   registered turns `:app` red — five tests across two files (M3). Before fix round 1 every floor read
   `productionAdapters().flatMap { registry.all() }` and this mutation passed.
4. **`provideDynamicToolLabels` really projects, and really re-reads** (M4, M16).
5. **The same-instance claim on `provideDynamicToolNames` is held by an `assertSame` that fires** (M5).
6. **`ToolRegistryPermissionGuardTest`'s third adapter is no longer inert** (M6): the exact deletion that
   left all four tests green before fix round 1 now reddens three. The fourth is the manifest-scan
   sanity test, which by design does not read the federation.
7. **The network-type scan now covers `ShortcutRefreshTrigger.kt`** (M7) — the file fix round 1 named as
   the glaring omission.
8. **`DOC-ILM-2` provenance for `app_shortcut` is held behaviourally** (M8), through a rendered node.
9. **Adapter arrival/disappearance is held twice over** (M9) — by the real-graph level read and by the
   textual count-pin — and **adapter order** is held by the scan alone (M17), exactly as its KDoc claims.
10. **`remember(session)` is held by a read-counting test that reports `expected:<1> but was:<9>`** (M10).
11. **The round-trip filter is an equality, not a null check, and that difference is tested** (M11).
12. **A shortcut id derived from anything but the (package, shortcutId) pair is caught** (M12, M12b).
13. **A registered-but-unnamed dynamic tool is caught** (M16) — Task 8's positive requirement, not an
    exemption.
14. **`shortcutToolPermissions` is load-bearing in the one direction its KDoc claims** (M14).
15. **`ToolFederation`'s per-call derivation is held** — by `:domain`'s own `ToolFederationTest` (M15).

## Unproved — GREEN where a property is named, or unreachable by any compiling mutation

### F1 — `DoctrineGuardTest`'s dynamic-label surface rule is a literal-presence scan, and it is measurably blind (M13)

The shortcut branch of `every tool in the production federation has a non-generic label on the surface`
ends with:

```kotlin
assertEquals(
    "AgentSessionPresentation.kt has no launcher_agent_step_shortcut arm, so a tool " +
        "whose name is data has nowhere to be rendered",
    true,
    presentationText.contains("R.string.launcher_agent_step_shortcut"),
)
```

M13 made `PlanStep.line` ignore `dynamicLabels` entirely — every shortcut step then renders
`launcher_agent_step_generic`, which is precisely the defect this rule's own KDoc says it exists to
catch — and **`:app` stayed green at 59/0**. The resource id is still present in the file, in a branch
that can no longer be taken, and `String.contains` cannot tell the difference. The guard's KDoc already
names this class of limit ("the check is `String.contains`, not 'is a `when` arm'"); this round moves it
from *stated* to *measured*, and shows the failure it admits to is not hypothetical — it is one edit away
in the very function the rule is about.

The property **is** held, but only in `:feature:launcher`, by `LauncherScreenAgentProvenanceTest >
a shortcut tool renders its own name and its app_shortcut provenance`, which asserts on the rendered
text. A guard in `:app` closing this would have to read what `line` *does* with the map rather than
whether a resource id appears in the file — which needs `toolLabelFor`/`line` reachable from `:app`
(they are `internal` to `:feature:launcher`), or the assertion relocated beside the behavioural test.
Until then `:app`'s copy of this rule is a spelling check, and the doctrine claim rests on
`:feature:launcher`.

### F2 — `an id is stable across a refresh that re-labels the same shortcut` cannot observe an instability introduced in `ShortcutToolIds.of` (M12 vs M12b)

`ShortcutToolSource.usable()` admits a shortcut only if `parse(of(p, s)) == (p, s)`. **Any** instability
injected into `of` also breaks that round trip, so `all()` returns an empty list and the stability test
dies on `java.util.NoSuchElementException: List is empty` from `.single()` — never reaching its
`assertEquals`. There is no compiling mutation of `of` that is unstable *and* round-trips: `parse`
recovers the pair by `removePrefix(PREFIX)` and the first `/`, so every byte of the id is compared back.

So the test's name is broader than what it can see. It observed the property only under M12b, where the
instability was introduced **downstream of the filter** (`all()` deriving from `shortcutLabel`), which is
the shape the test's own KDoc is about — a refresh that re-labels. What actually pins `of` itself is
`ShortcutToolIdsTest > an id round-trips through parse`, which compares against a written-out literal.
Neither is wrong; the pair is only complete because both exist, and that is not said anywhere today.

### F3 — `"app_shortcut"`, the `ToolLevel` wire value, is pinned by no test (not mutated; no claim exists)

Every reference to the level in production and in tests is symbolic (`ToolLevels.APP_SHORTCUT`); the
literal `"app_shortcut"` appears in exactly one place, its declaration in `ToolLevel.kt`. No guard claims
it is frozen, it is not persisted in schema 4, and `provenanceLabelFor` matches it symbolically — so this
is an **absence of a claim**, not a failed one, and no mutation was planted for it. Recorded because
`ToolLevel`'s KDoc calls levels "the axis that grows", and the day one is written into a trace row, a
session row, or an outbound payload, nothing in the tree will notice the value changing.

### F4 — the two halves of the federation's contract are held in different modules, and neither module holds both

- M3 (federation drops an adapter's tools by level): `:app` red in 5 tests, **`:domain:jvmTest` green at
  438/0**.
- M15 (federation caches its snapshot at construction): **`:app` green at 59/0**, `:data:repository`,
  `:feature:launcher` and `:consumer:jvm` green, `:domain:jvmTest` red in 2 tests.

Each property is genuinely held, so nothing here is unproved — but no single module's suite is a
sufficient check of `ToolFederation`, and `:app`'s guards are structurally unable to see staleness
because they build their catalogs already-refreshed. Worth knowing for whoever scopes a future run.

### F5 — what no mutation in this round could reach, restated

The guards' own KDocs name three evasions that a regex over one hard-coded file cannot see —
`someAdapter.copy(registry = …)`, `listOf(…) + adaptersBuiltElsewhere`, and a second `@Module` in
`:app` providing adapters of its own. The first two are now covered *for the level set* by the real-graph
tests (M9, M17 confirm those tests fire), because those call `provideToolFederation` directly. The third
is not: nothing in this tree couples any guard to **Hilt's actual set of `ToolAdapter` providers**, and a
second `@Module` would be invisible to the textual scan and to `graphFederation()` alike. No compiling
mutation of a single file can demonstrate it without adding a whole Hilt module, which is a new
production file rather than a mutation; it is therefore reported as unreachable by this round and
unchanged by it.

---

## Tree state

`git status --short` empty, `git diff --stat` and `git diff --cached --stat` empty, `git log --oneline -1`
= `c502bea`. Final confirmation run `:app:testDebugUnitTest :feature:launcher:testDebugUnitTest
:data:repository:testDebugUnitTest --rerun-tasks --continue` → exit 0, `289 actionable tasks: 289
executed`, `:app` 59/0 · `:feature:launcher` 191/0 · `:data:repository` 296/0 — identical to baseline.
