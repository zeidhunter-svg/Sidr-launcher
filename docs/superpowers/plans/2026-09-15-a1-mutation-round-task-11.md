# Task 11 — mutation round: "an authored trigger cannot be shadowed by a third-party shortcut name"

Prover round on the guard `ToolVocabularyReachabilityTest > an authored trigger cannot be shadowed by a
third-party shortcut name`, its private `collisionFloor` helper, `guardedEntries()`, and the
`SHADOW_CANDIDATE` constant — all in
`data/repository/src/test/java/com/sidr/launcher/data/repository/agent/ToolVocabularyReachabilityTest.kt`.
Repo `/home/Suleiman/Sidr-launcher`, branch `launcher--7`, HEAD `c05fd7a`, tree clean before and after.
No production code was written, nothing was fixed, no guard assertion was widened or relaxed.

**Method.** One mutation per shell invocation: plant with a Python `str.replace` that asserts the exact
old text occurs **once** before touching the file, `trap 'git checkout -- "$F"' EXIT` set before the
plant so an interrupted call cannot leave a mutation behind, run Gradle in the foreground, let the trap
restore on exit. `git status --short` and `git diff --stat` verified empty after every single mutation,
before planting the next one.

**Toolchain.** `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :data:repository:test --rerun-tasks --continue`.
Every run below printed `79 actionable tasks: 79 executed`. No `--rerun`, no `tail`, no connected tests.
`data/repository/build/test-results` deleted before every run. Counts read from the JUnit XML via a
small `xml.etree.ElementTree` script, never the console.

**Baseline** (clean tree, before any mutation): exit 0, `310 tests completed, 0 failed` — matches the
task's stated baseline exactly. **Final confirmation run**, after all five mutations and reverts: exit 0,
`310`/`0` again, `git status --short` and `git diff --stat` both empty, `git log --oneline -1` unchanged
at `c05fd7a`.

---

## Results

5 mutations planted, **5 behaved as predicted**.

| # | Edit | File | Predicted | Observed | Failing test(s) + assertion message | Revert verified |
|---|---|---|---|---|---|---|
| 1 — delete rule 1 | Removed `vocabulary.match(normalized)?.let { return it }` from `ToolSelector.select` | `data/repository/.../agent/ToolSelector.kt` | Shadowing test **RED**, headline: if it stays green the guard is vacuous | **RED**, GE=1, `310 tests completed, 9 failed` | Target: `ToolVocabularyReachabilityTest > an authored trigger cannot be shadowed by a third-party shortcut name` — *"an authored trigger stopped recognising its own sample once a shortcut claimed it:\nprefix: set a timer for 10 minutes\nprefix: set timer for 10 minutes\nprefix: timer for 10 minutes\nprefix: поставь таймер на 10 minutes\nprefix: заведи таймер на 10 minutes\nprefix: таймер на 10 minutes\nsuffix: 10 minutes zamanlayıcı ayarla\nsuffix: 10 minutes sayaç ayarla\nprefix: system settings\nprefix: android settings\nprefix: системные настройки\nprefix: настройки андроид\nprefix: sistem ayar[ları]"* — **all 14** generated commands failed, i.e. authored recognition was gone outright once rule 1 was removed. Plus 8 collateral failures elsewhere (`ToolSelectorTest`, `FreeTextGoalEndToEndTest` ×2, `Tier0ToolExecutionEndToEndTest` ×2, `ToolMatchPlannerTest` ×3) — expected fallout: rule 1 is load-bearing for every caller of `ToolSelector`, not only this guard | ✅ clean |
| 2 — invert rule 1 | `select()` rewritten to consult `dynamicNames` first, fall back to `vocabulary.match` | `data/repository/.../agent/ToolSelector.kt` | The shape a careless refactor would produce; predict only the one command that literally collides with `SHADOW_CANDIDATE`'s exact text fails, everything else still falls through to the vocabulary correctly | **RED**, GE=1, `310 tests completed, 3 failed` | Target reddened on **exactly one** of the 14 commands: *"an authored trigger stopped recognising its own sample once a shortcut claimed it:\nprefix: set timer for 10 minutes"* — precisely the literal-equality case, as predicted (the other two `en` forms, `"set a timer for"`/`"timer for"`, do not contain `"set timer for"` as a contiguous word-bounded run, so they still fall through correctly to the vocabulary). Collateral: `ToolSelectorTest > an authored trigger wins outright…` and `ToolSelectorTest > a text two authored entries both claim selects nothing…` (same dynamic-first ordering breaks both) | ✅ clean |
| 3 — break the collision premise | `set_timer`'s `en` prefix form `"set timer for"` reworded to `"start a timer for"` | `data/repository/.../agent/ToolVocabulary.kt` | `collisionFloor` must go RED with a message explaining the planted name no longer collides — the whole point of the floor | **RED**, GE=1, `310 tests completed, 2 failed` | Target: `collisionFloor`'s own assertion fired verbatim: *"the planted shadow candidate (qualifier 'Timer', name 'set timer for') no longer collides with any of the 14 generated commands — this test is no longer testing shadowing, and the planted name must be re-chosen to collide with a real authored trigger again"*. One collateral failure, an artifact of the specific replacement word chosen rather than of the guard: `no vocabulary trigger is claimed by FastPath before the planner is asked` — *"'start a timer for 10 minutes' (set_timer/en) is claimed by FastPath as LaunchAppIntent…"*, because `"start"` happens to be a FastPath `LAUNCH_VERBS` prefix. Not a finding about the guard under test; noted so the reword isn't mistaken for guard-caused | ✅ clean |
| 4 — truncate the table | Removed the `OPEN_SYSTEM_SETTINGS` entry from `DEFAULT_ENTRIES` | `data/repository/.../agent/ToolVocabulary.kt` | Non-vacuity floor in `guardedEntries()` must redden, so the shadowing test cannot pass against a hollowed-out table | **RED**, GE=1, `310 tests completed, 9 failed` | Target and its two siblings all fail identically, all three calling `guardedEntries()` first: `an authored trigger cannot be shadowed…`, `every trigger recognises its own sample command`, `no vocabulary trigger is claimed by FastPath…` — *"ToolVocabulary has no entry for open_system_settings (the table holds 1 entry). All three tests in this guard loop over that table, so it would otherwise report 'no trigger is claimed by FastPath' after examining no triggers at all."* Collateral: `ToolVocabularyLocaleGuardTest`'s own analogous floor (3 tests, its own near-identical message), `ToolSelectorTest`'s literal-collision test, `ToolVocabularyTest`'s zero-arg match test | ✅ clean |
| 5 — weaken the ambiguity fix | Removed `if (vocabulary.isAmbiguous(normalized)) return null` from `ToolSelector.select` | `data/repository/.../agent/ToolSelector.kt` | Confirms Task 10's Critical fix is held by a test, not only by prose; expect `ToolSelectorTest`'s ambiguity test to redden — and expect the **shadowing test to stay green**, since this is a different failure mode from shadowing | **RED**, GE=1, `310 tests completed, 1 failed` | **Exactly** `ToolSelectorTest > a text two authored entries both claim selects nothing even when a dynamic name would match`: *"expected null, but was:<ToolMatch(id=ToolId(value=shortcut:com.a/thing), args={})>"*. `ToolVocabularyReachabilityTest`'s shadowing test stayed **green**, confirming it is correctly scoped to the "authored beats dynamic" property and does not also cover ambiguity-fallthrough — that is Task 10's own guard's job, not this one's | ✅ clean |

No sixth mutation was planted. The five assigned mutations already isolate every mechanism the guard's
own KDoc names — rule-1 deletion (vacuity headline), rule-1 inversion (precision check, single named
failure), the collision-floor's own non-vacuity (mutation 3), the entry-table's own non-vacuity
(mutation 4), and the boundary between this guard's property and Task 10's (mutation 5, which correctly
did **not** redden the shadowing test). A further mutation targeting rule 2/3 (the app-qualifier or
equal-candidates checks) would be testing `ToolSelectorTest`'s own dynamic-branch guards, not the
authored-vs-dynamic property this file's guard claims — out of scope for this round, so none was added.

---

## Properties now proved

1. **Rule 1 is load-bearing for the shadowing guard, and the guard is not vacuous.** Deleting it turns
   the target test red on all 14 generated commands (mutation 1) — the guard does not pass by accident
   when authored priority is gone.
2. **The guard is precise, not just sensitive.** Inverting rule 1 (a plausible careless refactor, not a
   deletion) reddens the target test on **exactly** the one command that literally collides with
   `SHADOW_CANDIDATE`, and no other — the guard does not over- or under-fire relative to the actual
   collision surface (mutation 2).
3. **`collisionFloor` is itself load-bearing**, not decorative. A legitimate, guard-permitted reword of
   the colliding trigger reddens `collisionFloor` with the exact message its own KDoc promises, catching
   the vacuity that would otherwise silently reduce the shadowing test to a duplicate of "every trigger
   recognises its own sample command" (mutation 3).
4. **`guardedEntries()`'s non-vacuity floor is load-bearing.** Truncating `DEFAULT_ENTRIES` reddens all
   three tests in the class, including the shadowing test, before any of them examines a single trigger
   (mutation 4).
5. **The shadowing guard is correctly scoped and does not silently absorb a different guard's job.**
   Removing the ambiguity check reddens `ToolSelectorTest`'s dedicated ambiguity test and **only** that
   test — the shadowing guard stayed green, confirming Task 10's Critical fix is held by its own test,
   separately from this one (mutation 5).

## Not proved / no findings of blindness

No guard under test was found blind in this round. Every mutation that should have reddened the target
test did; the one mutation that should **not** redden it (mutation 5) correctly left it green. No
mutation produced a green result where red was predicted, so there is no headline finding to report
here — the outcome itself (all five behaving exactly as predicted, with assertion messages that name the
planted defect precisely, including `collisionFloor`'s and `guardedEntries()`'s own explanatory text)
is the finding: this guard holds the property it claims.

One non-finding worth recording so it isn't mistaken for one: mutation 3's collateral failure in
`no vocabulary trigger is claimed by FastPath before the planner is asked` is an artifact of the specific
replacement word (`"start"` is a FastPath launch verb) and says nothing about the guard under test or
about `collisionFloor`; a different legitimate reword (e.g. `"begin a timer for"`) would very likely have
reproduced the target failure without that collateral.

---

## Tree state

`git status --short` empty, `git diff --stat` empty, `git log --oneline -1` = `c05fd7a
fix(agentic-5.5/A1"): fix round 1 on Task 11 — three stale silence comments, an unpinned shadowing
collision, and two narrowed fidelity claims`. Final confirmation run
`:data:repository:test --rerun-tasks --continue` → exit 0, `79 actionable tasks: 79 executed`,
`310 tests completed, 0 failed` — identical to the pre-mutation baseline.
