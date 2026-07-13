# Explicit Aliases (S2-2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **STATUS (2026-07-13): PAUSED pending DS-7 grey UI — and the last mile lives on `launcher-4` only.**
> Phases A–C (domain `memory/alias/` + Room data layer + `MemoryBindsModule` DI) are merged into the
> main line (`launcher--7`), but the code is INERT there: `LauncherViewModel` has zero alias references.
> Three finishing commits exist **only on branch `launcher-4`** (pushed to origin) and were never carried
> forward:
> - `b5469bc` `feat(s2-2): wire alias decorator into LauncherViewModel (fill-the-gap launch)` — the
>   activation; do NOT cherry-pick before DS-7 provides the alias management surface (recorded owner
>   pause), and expect conflicts: `LauncherViewModel`/tests evolved through S2-1/DS-4/Vision MVP since.
> - `680268c` `test(s2-2): privacy/scope/dependency/room-inventory guards + zero-widen allow-list` —
>   guard tests; reconcile against the current `RoomColumnNames` inventory rather than blind-picking
>   (the main line is already green with the alias table, so the inventory may partially overlap).
> - `cef4111` `docs(s2-2): ADR + status sync + mark plan code-closed` — stale vs. later doc evolution;
>   salvage the ADR text when S2-2 resumes, do not cherry-pick.
> When DS-7 Memory Surfaces starts, fold "recover launcher-4 last mile" into its conditional Aliases
> task: re-apply the two code commits by hand, re-derive the docs, then close S2-2 properly.

**Goal:** Let the user declare, entirely on-device, an explicit nickname → app mapping ("рабочий чат" → Telegram) that launches the app when the rule matcher would otherwise return `Unknown`.

**Architecture:** A pure `Alias` concept (`domain/memory/alias/`) + `AliasStore` port + use-cases; a decorator `ResolveCommandWithAliasUseCase` wraps S2-1's `ResolveCommandWithPreferenceUseCase` (via a `ResolvedCommandStep` seam) and, **only** when the inner result is `Outcome(CommandOutcome.Unknown)`, resolves an alias to an `AutoLaunch` directive; a Room table `aliases` persists it; a dedicated Settings → Aliases screen manages it. `HandleUserCommandUseCase`/`RouteCommandUseCase`/the rule matcher are untouched → no-alias / non-Unknown ⇒ byte-for-byte pre-S2-2.

**Tech Stack:** Kotlin, Coroutines/Flow, Room, Hilt, Jetpack Compose. Mirrors the S2-1 "Learned Resolutions" code (same modules, same idioms).

**Spec:** [docs/superpowers/specs/2026-07-10-explicit-aliases-design.md](../specs/2026-07-10-explicit-aliases-design.md).

## Global Constraints

- **Domain purity:** `domain` is stdlib + coroutines only. No Android, no `core/*`, no `AiRequest`/generative import in `domain/memory/alias/`.
- **Never throw to UI:** repo/use-case ops return `OperationResult<T>`; rethrow `CancellationException`, map any other failure to a category-only `OperationError.UnknownError(reason=...)`.
- **Rule-first parity:** the alias path fires **only** on `CommandOutcome.Unknown`; every other outcome passes through unchanged. `HandleUserCommandUseCase` / `RouteCommandUseCase` / the rule matcher are not modified.
- **Privacy:** alias phrase + package are local-sensitive metadata, never enter an `AiRequest`; the outbound allow-list is widened by **zero**. Phrase never logged. `MAX_ALIAS_PHRASE_LENGTH = 64`, non-empty.
- **No `feature → feature` / `data → data` edges.** UI holds no business logic. `core/ui` components take strings + lambdas only (no `domain → ui`).
- **Room schema discipline:** any entity change bumps `version`, adds a `Migration` under `db/migrations/`, commits the golden schema JSON, and is covered by the instrumented `MigrationTest`.
- **Build gate command** (machine JDK has rolled to 25/26; Gradle 8.10.2 needs 17–21). Run every Gradle command as:
  `env -u JAVA_HOME JAVA_HOME=~/Загрузки/android-studio/jbr ./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 <tasks>`
  (Abbreviated below as `./gradlew <tasks>`.)
- **Commit style:** `feat(s2-2): …` / `test(s2-2): …` / `docs(s2-2): …`, footer `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

---

## File Structure

**New (`domain/memory/alias/`):** `Alias.kt` (model + `AliasTarget` + `appPackageOrNull` + `MAX_ALIAS_PHRASE_LENGTH` + `AliasView`), `AliasStore.kt` (port), `SaveAliasUseCase.kt`, `DeleteAliasUseCase.kt`, `ObserveAliasesUseCase.kt`, `PruneUnavailableAliasesUseCase.kt`, `ResolveCommandWithAliasUseCase.kt` (`ResolvedCommandStep` seam + decorator).

**New (`:data:repository`):** `db/entity/AliasEntity.kt`, `db/dao/AliasDao.kt`, `db/mapper/AliasMapper.kt`, `db/AliasStoreImpl.kt`, `db/migrations/Migration2To3.kt`, `schemas/…/3.json`.

**New (`:core:testing`):** `FakeAliasStore.kt`.

**New (`:core:ui`):** `component/AliasRow.kt`.

**New (`:feature:settings`):** `AliasesViewModel.kt`, `AliasesScreen.kt`.

**Modified:** `db/SidrDatabase.kt` (register entity + bump v3), `di/DatabaseModule.kt` (+DAO provider, +migration), `di/MemoryProvidesModule.kt` (+use-case providers), `di/MemoryBindsModule.kt` (+`AliasStore` bind), `db/RoomColumnNames.kt` (+`aliases`), `feature/launcher/LauncherViewModel.kt` (swap `resolveCommand` type), `core/common/navigation/Routes.kt` (+`Aliases`), `feature/settings/SettingsScreen.kt` + `SettingsViewModel.kt` (+entry), `navigation/AppNavHost.kt` (+composable).

---

## Phase A — Pure domain (zero behavior change; nothing consumes it yet)

### Task 1: `Alias` model + `AliasStore` port + `FakeAliasStore`

**Files:**
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/alias/Alias.kt`
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/alias/AliasStore.kt`
- Create: `core/testing/src/main/java/com/sidr/launcher/core/testing/FakeAliasStore.kt`
- Test: `domain/src/test/java/com/sidr/launcher/domain/memory/alias/AliasTest.kt`

**Interfaces:**
- Produces: `Alias(phrase: String, target: AliasTarget, createdAtEpochMs: Long)`; `sealed interface AliasTarget { data class App(val packageName: String) }`; `fun AliasTarget.appPackageOrNull(): String?`; `const val MAX_ALIAS_PHRASE_LENGTH = 64`; `data class AliasView(phrase, targetPackageName, targetLabel)`; `interface AliasStore { suspend find(phrase): OperationResult<Alias?>; suspend upsert(Alias): OperationResult<Unit>; suspend delete(phrase): OperationResult<Unit>; observeAll(): Flow<List<Alias>> }`; `FakeAliasStore`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.domain.memory.alias

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AliasTest {
    @Test fun `appPackageOrNull returns package for app target`() {
        val target: AliasTarget = AliasTarget.App("com.telegram")
        assertEquals("com.telegram", target.appPackageOrNull())
    }

    @Test fun `max phrase length is 64`() {
        assertEquals(64, MAX_ALIAS_PHRASE_LENGTH)
    }

    @Test fun `alias holds phrase target and timestamp`() {
        val a = Alias(phrase = "work chat", target = AliasTarget.App("com.telegram"), createdAtEpochMs = 5L)
        assertEquals("work chat", a.phrase)
        assertEquals(AliasTarget.App("com.telegram"), a.target)
        assertEquals(5L, a.createdAtEpochMs)
        assertNull((a.target as AliasTarget.App).packageName.let { if (it.isEmpty()) it else null })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests '*AliasTest'`
Expected: FAIL — `Alias` / `AliasTarget` unresolved.

- [ ] **Step 3: Write `Alias.kt`**

```kotlin
package com.sidr.launcher.domain.memory.alias

/** Max stored alias-phrase length; longer phrases are never persisted (privacy + sanity). */
const val MAX_ALIAS_PHRASE_LENGTH: Int = 64

/** An explicit, user-declared nickname → target mapping. `phrase` is the normalized full phrase (key). */
data class Alias(
    val phrase: String,
    val target: AliasTarget,
    val createdAtEpochMs: Long,
)

/** The thing an alias points at. v1 = app only; sealed so it generalizes without breaking the port. */
sealed interface AliasTarget {
    data class App(val packageName: String) : AliasTarget
}

/** The app package for an [AliasTarget.App], or null for a non-app target. Exhaustive, cast-free. */
fun AliasTarget.appPackageOrNull(): String? = when (this) {
    is AliasTarget.App -> packageName
}

/** Display projection for the management screen (target resolved against installed apps). */
data class AliasView(
    val phrase: String,
    val targetPackageName: String,
    val targetLabel: String,
)
```

- [ ] **Step 4: Write `AliasStore.kt`**

```kotlin
package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

interface AliasStore {
    suspend fun find(phrase: String): OperationResult<Alias?>
    suspend fun upsert(alias: Alias): OperationResult<Unit>
    suspend fun delete(phrase: String): OperationResult<Unit>
    fun observeAll(): Flow<List<Alias>>
}
```

- [ ] **Step 5: Write `FakeAliasStore.kt`** (mirrors `FakeResolutionPreferenceStore`)

```kotlin
package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAliasStore(
    var failReads: Boolean = false,
    var failWrites: Boolean = false,
) : AliasStore {
    private val state = MutableStateFlow<List<Alias>>(emptyList())

    override suspend fun find(phrase: String): OperationResult<Alias?> =
        if (failReads) OperationResult.Failure(OperationError.UnknownError("io"))
        else OperationResult.Success(state.value.firstOrNull { it.phrase == phrase })

    override suspend fun upsert(alias: Alias): OperationResult<Unit> {
        if (failWrites) return OperationResult.Failure(OperationError.UnknownError("io"))
        state.value = state.value.filterNot { it.phrase == alias.phrase } + alias
        return OperationResult.Success(Unit)
    }

    override suspend fun delete(phrase: String): OperationResult<Unit> {
        if (failWrites) return OperationResult.Failure(OperationError.UnknownError("io"))
        state.value = state.value.filterNot { it.phrase == phrase }
        return OperationResult.Success(Unit)
    }

    override fun observeAll(): Flow<List<Alias>> = state
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :domain:test --tests '*AliasTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add domain/src/main/java/com/sidr/launcher/domain/memory/alias/ \
        core/testing/src/main/java/com/sidr/launcher/core/testing/FakeAliasStore.kt \
        domain/src/test/java/com/sidr/launcher/domain/memory/alias/AliasTest.kt
git commit -m "feat(s2-2): alias domain model + store port + fake (Phase A)"
```

---

### Task 2: `SaveAliasUseCase` (normalize + validate + upsert)

**Files:**
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/alias/SaveAliasUseCase.kt`
- Test: `domain/src/test/java/com/sidr/launcher/domain/memory/alias/SaveAliasUseCaseTest.kt`

**Interfaces:**
- Consumes: `AliasStore`, `Alias`, `AliasTarget`, `MAX_ALIAS_PHRASE_LENGTH`, `CommandNormalizer.normalize` (from `com.sidr.launcher.domain.intent`).
- Produces: `SaveAliasUseCase.save(phrase: String, target: AliasTarget, nowEpochMs: Long = System.currentTimeMillis()): OperationResult<Unit>`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveAliasUseCaseTest {
    private val store = FakeAliasStore()
    private val useCase = SaveAliasUseCase(store)

    @Test fun `save normalizes phrase and upserts`() = runTest {
        val r = useCase.save("  Work   Chat ", AliasTarget.App("com.telegram"), nowEpochMs = 7L)
        assertTrue(r is OperationResult.Success)
        val stored = store.observeAll().first().single()
        assertEquals("work chat", stored.phrase)                 // trimmed, collapsed, lowercased
        assertEquals(AliasTarget.App("com.telegram"), stored.target)
        assertEquals(7L, stored.createdAtEpochMs)
    }

    @Test fun `blank phrase is a no-op success`() = runTest {
        val r = useCase.save("   ", AliasTarget.App("com.telegram"))
        assertTrue(r is OperationResult.Success)
        assertTrue(store.observeAll().first().isEmpty())
    }

    @Test fun `over-length phrase is a no-op success`() = runTest {
        val long = "a".repeat(MAX_ALIAS_PHRASE_LENGTH + 1)
        val r = useCase.save(long, AliasTarget.App("com.telegram"))
        assertTrue(r is OperationResult.Success)
        assertTrue(store.observeAll().first().isEmpty())
    }

    @Test fun `save is last-wins for the same phrase`() = runTest {
        useCase.save("bank", AliasTarget.App("com.a"))
        useCase.save("bank", AliasTarget.App("com.b"))
        val all = store.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(AliasTarget.App("com.b"), all.single().target)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests '*SaveAliasUseCaseTest'`
Expected: FAIL — `SaveAliasUseCase` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException

class SaveAliasUseCase(private val store: AliasStore) {
    /**
     * Creates/updates an alias. The phrase is normalized to the same form looked up at input time.
     * A blank or over-[MAX_ALIAS_PHRASE_LENGTH] phrase is a no-op [OperationResult.Success] (scope
     * contract). Never throws (rethrows CancellationException).
     */
    suspend fun save(
        phrase: String,
        target: AliasTarget,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): OperationResult<Unit> {
        val normalized = CommandNormalizer.normalize(phrase)
        if (normalized.isBlank() || normalized.length > MAX_ALIAS_PHRASE_LENGTH) {
            return OperationResult.Success(Unit)
        }
        return try {
            store.upsert(Alias(phrase = normalized, target = target, createdAtEpochMs = nowEpochMs))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            OperationResult.Failure(OperationError.UnknownError(t.message ?: "save alias failed"))
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :domain:test --tests '*SaveAliasUseCaseTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/sidr/launcher/domain/memory/alias/SaveAliasUseCase.kt \
        domain/src/test/java/com/sidr/launcher/domain/memory/alias/SaveAliasUseCaseTest.kt
git commit -m "feat(s2-2): SaveAliasUseCase (normalize + validate + upsert)"
```

---

### Task 3: Delete / Observe / Prune use-cases

**Files:**
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/alias/DeleteAliasUseCase.kt`
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/alias/ObserveAliasesUseCase.kt`
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/alias/PruneUnavailableAliasesUseCase.kt`
- Test: `domain/src/test/java/com/sidr/launcher/domain/memory/alias/AliasManagementUseCasesTest.kt`

**Interfaces:**
- Consumes: `AliasStore`, `InstalledAppsRepository`, `AliasView`, `appPackageOrNull`, `AliasTarget`.
- Produces: `DeleteAliasUseCase.delete(phrase): OperationResult<Unit>`; `ObserveAliasesUseCase.observe(): Flow<List<AliasView>>`; `PruneUnavailableAliasesUseCase.prune(): OperationResult<Unit>`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.domain.model.InstalledApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AliasManagementUseCasesTest {
    private val store = FakeAliasStore()
    private val apps = FakeInstalledAppsRepository()

    @Test fun `observe hides aliases whose target is uninstalled and resolves label`() = runTest {
        apps.appsToReturn = listOf(InstalledApp("com.telegram", "Telegram"))
        store.upsert(Alias("work chat", AliasTarget.App("com.telegram"), 1L))
        store.upsert(Alias("dead", AliasTarget.App("com.gone"), 2L))
        val views = ObserveAliasesUseCase(store, apps).observe().first()
        assertEquals(listOf(AliasView("work chat", "com.telegram", "Telegram")), views)
    }

    @Test fun `delete removes by phrase`() = runTest {
        store.upsert(Alias("bank", AliasTarget.App("com.a"), 1L))
        DeleteAliasUseCase(store).delete("bank")
        assertEquals(0, store.observeAll().first().size)
    }

    @Test fun `prune deletes aliases with uninstalled targets`() = runTest {
        apps.appsToReturn = listOf(InstalledApp("com.a", "A"))
        store.upsert(Alias("keep", AliasTarget.App("com.a"), 1L))
        store.upsert(Alias("drop", AliasTarget.App("com.gone"), 2L))
        PruneUnavailableAliasesUseCase(store, apps).prune()
        assertEquals(listOf("keep"), store.observeAll().first().map { it.phrase })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests '*AliasManagementUseCasesTest'`
Expected: FAIL — use-cases unresolved.

- [ ] **Step 3: Write `DeleteAliasUseCase.kt`**

```kotlin
package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.domain.result.OperationResult

class DeleteAliasUseCase(private val store: AliasStore) {
    suspend fun delete(phrase: String): OperationResult<Unit> = store.delete(phrase)
}
```

- [ ] **Step 4: Write `ObserveAliasesUseCase.kt`** (mirrors `ObserveLearnedChoicesUseCase`)

```kotlin
package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveAliasesUseCase(
    private val store: AliasStore,
    private val installedApps: InstalledAppsRepository,
) {
    fun observe(): Flow<List<AliasView>> = store.observeAll().map { aliases ->
        val installed = (installedApps.getInstalledApps() as? OperationResult.Success)?.value.orEmpty()
        val byPkg = installed.associateBy { it.packageName }
        aliases.mapNotNull { alias ->
            val pkg = alias.target.appPackageOrNull() ?: return@mapNotNull null
            val app = byPkg[pkg] ?: return@mapNotNull null // filter uninstalled
            AliasView(phrase = alias.phrase, targetPackageName = pkg, targetLabel = app.label)
        }
    }
}
```

- [ ] **Step 5: Write `PruneUnavailableAliasesUseCase.kt`** (mirrors the S2-1 prune)

```kotlin
package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.first

class PruneUnavailableAliasesUseCase(
    private val store: AliasStore,
    private val installedApps: InstalledAppsRepository,
) {
    /** Best-effort: delete aliases whose target app is no longer installed. Never throws. */
    suspend fun prune(): OperationResult<Unit> {
        val installed = (installedApps.getInstalledApps() as? OperationResult.Success)?.value.orEmpty()
            .map { it.packageName }.toSet()
        store.observeAll().first().forEach { alias ->
            val pkg = alias.target.appPackageOrNull() ?: return@forEach
            if (pkg !in installed) store.delete(alias.phrase)
        }
        return OperationResult.Success(Unit)
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :domain:test --tests '*AliasManagementUseCasesTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add domain/src/main/java/com/sidr/launcher/domain/memory/alias/ \
        domain/src/test/java/com/sidr/launcher/domain/memory/alias/AliasManagementUseCasesTest.kt
git commit -m "feat(s2-2): delete/observe/prune alias use-cases"
```

---

### Task 4: `ResolveCommandWithAliasUseCase` decorator (+ `ResolvedCommandStep` seam)

**Files:**
- Create: `domain/src/main/java/com/sidr/launcher/domain/memory/alias/ResolveCommandWithAliasUseCase.kt`
- Test: `domain/src/test/java/com/sidr/launcher/domain/memory/alias/ResolveCommandWithAliasUseCaseTest.kt`

**Interfaces:**
- Consumes: `ResolvedCommand` (from `com.sidr.launcher.domain.memory.resolution`), `CommandOutcome` (`com.sidr.launcher.domain.intent`), `ResolvedTarget` (`…resolution`), `CommandNormalizer`, `AliasStore`, `appPackageOrNull`, `InstalledAppsRepository`.
- Produces: `fun interface ResolvedCommandStep { suspend fun resolve(rawInput: String): ResolvedCommand }`; `ResolveCommandWithAliasUseCase(inner: ResolvedCommandStep, store: AliasStore, installedApps: InstalledAppsRepository).resolve(rawInput: String): ResolvedCommand`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.memory.resolution.ResolvedCommand
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget
import com.sidr.launcher.domain.model.InstalledApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveCommandWithAliasUseCaseTest {
    private val store = FakeAliasStore()
    private val apps = FakeInstalledAppsRepository().apply {
        appsToReturn = listOf(InstalledApp("com.telegram", "Telegram"))
    }
    private fun step(result: ResolvedCommand) = ResolvedCommandStep { result }
    private fun useCase(inner: ResolvedCommand) = ResolveCommandWithAliasUseCase(step(inner), store, apps)

    @Test fun `unknown with installed alias hit becomes AutoLaunch with unknown fallback`() = runTest {
        store.upsert(Alias("work chat", AliasTarget.App("com.telegram"), 1L))
        val unknown = ResolvedCommand.Outcome(CommandOutcome.Unknown("work chat"), null)
        val out = useCase(unknown).resolve("Work Chat")
        assertTrue(out is ResolvedCommand.AutoLaunch)
        out as ResolvedCommand.AutoLaunch
        assertEquals(ResolvedTarget.App("com.telegram"), out.target)
        assertEquals(CommandOutcome.Unknown("work chat"), out.fallback)
    }

    @Test fun `unknown with no alias passes through unchanged`() = runTest {
        val unknown = ResolvedCommand.Outcome(CommandOutcome.Unknown("nope"), null)
        val out = useCase(unknown).resolve("nope")
        assertSame(unknown, out)
    }

    @Test fun `unknown with alias to uninstalled target passes through`() = runTest {
        store.upsert(Alias("work chat", AliasTarget.App("com.gone"), 1L))
        val unknown = ResolvedCommand.Outcome(CommandOutcome.Unknown("work chat"), null)
        val out = useCase(unknown).resolve("work chat")
        assertSame(unknown, out)
    }

    @Test fun `non-unknown outcome passes through untouched (parity)`() = runTest {
        store.upsert(Alias("telegram", AliasTarget.App("com.telegram"), 1L))
        val executed = ResolvedCommand.Outcome(CommandOutcome.Executed, null)
        assertSame(executed, useCase(executed).resolve("telegram"))
    }

    @Test fun `inner AutoLaunch passes through untouched`() = runTest {
        val auto = ResolvedCommand.AutoLaunch(ResolvedTarget.App("com.x"), CommandOutcome.Unknown("x"))
        assertSame(auto, useCase(auto).resolve("x"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests '*ResolveCommandWithAliasUseCaseTest'`
Expected: FAIL — `ResolveCommandWithAliasUseCase` / `ResolvedCommandStep` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.memory.resolution.ResolvedCommand
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult

/** The wrapped resolver (S2-1's ResolveCommandWithPreferenceUseCase), as a seam for trivial testing. */
fun interface ResolvedCommandStep { suspend fun resolve(rawInput: String): ResolvedCommand }

/**
 * Explicit-alias decorator (S2-2). Wraps [inner] and, ONLY when the inner result is an
 * [ResolvedCommand.Outcome] whose outcome is [CommandOutcome.Unknown] (the rule pipeline found
 * nothing), resolves a user-declared alias to an [ResolvedCommand.AutoLaunch] directive. Every other
 * result — including S2-1's NeedsConfirmation/RankFirst/AutoLaunch and any non-Unknown outcome —
 * passes through byte-for-byte. Alias is fill-the-gap: real names/commands are never shadowed.
 */
class ResolveCommandWithAliasUseCase(
    private val inner: ResolvedCommandStep,
    private val store: AliasStore,
    private val installedApps: InstalledAppsRepository,
) {
    suspend fun resolve(rawInput: String): ResolvedCommand {
        val resolved = inner.resolve(rawInput)
        if (resolved !is ResolvedCommand.Outcome) return resolved
        val unknown = resolved.outcome as? CommandOutcome.Unknown ?: return resolved

        val phrase = CommandNormalizer.normalize(rawInput)
        val alias = (store.find(phrase) as? OperationResult.Success)?.value ?: return resolved
        val pkg = alias.target.appPackageOrNull() ?: return resolved

        val installed = (installedApps.getInstalledApps() as? OperationResult.Success)?.value.orEmpty()
        if (installed.none { it.packageName == pkg }) return resolved

        return ResolvedCommand.AutoLaunch(target = ResolvedTarget.App(pkg), fallback = unknown)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :domain:test --tests '*ResolveCommandWithAliasUseCaseTest'`
Expected: PASS.

- [ ] **Step 5: Run the whole domain suite (regression / parity)**

Run: `./gradlew :domain:test`
Expected: BUILD SUCCESSFUL (S2-1 suites still green).

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/com/sidr/launcher/domain/memory/alias/ResolveCommandWithAliasUseCase.kt \
        domain/src/test/java/com/sidr/launcher/domain/memory/alias/ResolveCommandWithAliasUseCaseTest.kt
git commit -m "feat(s2-2): ResolveCommandWithAliasUseCase decorator (fill-the-gap on Unknown)"
```

---

## Phase B — Persistence (`:data:repository`)

### Task 5: Room `aliases` table — entity + DAO + `SidrDatabase` v3 + `Migration2To3` + golden schema

**Files:**
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/entity/AliasEntity.kt`
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/dao/AliasDao.kt`
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/migrations/Migration2To3.kt`
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/SidrDatabase.kt`
- Create (generated + committed): `data/repository/schemas/com.sidr.launcher.data.repository.db.SidrDatabase/3.json`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/db/AliasDaoTest.kt`
- Test (instrumented): `data/repository/src/androidTest/java/com/sidr/launcher/data/repository/db/MigrationTest.kt` (add a 2→3 case)

**Interfaces:**
- Produces: `AliasEntity(phrase, targetType, targetPackage, createdAt)` table `aliases`; `AliasDao { findByPhrase, upsert, deleteByPhrase, deleteByTargetPackage, observeAll }`; `Migration2To3`; `SidrDatabase.aliasDao()` at `version = 3`.

- [ ] **Step 1: Write the failing DAO test** (Robolectric, mirrors `ResolutionPreferenceDaoTest`)

```kotlin
package com.sidr.launcher.data.repository.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.data.repository.db.dao.AliasDao
import com.sidr.launcher.data.repository.db.entity.AliasEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AliasDaoTest {
    private lateinit var db: SidrDatabase
    private lateinit var dao: AliasDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), SidrDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.aliasDao()
    }

    @After fun tearDown() = db.close()

    @Test fun `upsert then find returns entity`() = runTest {
        dao.upsert(AliasEntity("work chat", "app", "com.telegram", 5L))
        assertEquals("com.telegram", dao.findByPhrase("work chat")?.targetPackage)
    }

    @Test fun `upsert replaces on same phrase`() = runTest {
        dao.upsert(AliasEntity("bank", "app", "com.a", 1L))
        dao.upsert(AliasEntity("bank", "app", "com.b", 2L))
        assertEquals("com.b", dao.findByPhrase("bank")?.targetPackage)
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test fun `deleteByPhrase removes`() = runTest {
        dao.upsert(AliasEntity("bank", "app", "com.a", 1L))
        dao.deleteByPhrase("bank")
        assertNull(dao.findByPhrase("bank"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:repository:testDebugUnitTest --tests '*AliasDaoTest'`
Expected: FAIL — `AliasEntity`/`AliasDao`/`aliasDao()` unresolved.

- [ ] **Step 3: Write `AliasEntity.kt`**

```kotlin
package com.sidr.launcher.data.repository.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "aliases", primaryKeys = ["phrase"])
data class AliasEntity(
    @ColumnInfo(name = "phrase") val phrase: String,
    @ColumnInfo(name = "target_type") val targetType: String,      // v1 always "app"
    @ColumnInfo(name = "target_package") val targetPackage: String, // packageName
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
```

- [ ] **Step 4: Write `AliasDao.kt`**

```kotlin
package com.sidr.launcher.data.repository.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sidr.launcher.data.repository.db.entity.AliasEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AliasDao {
    @Query("SELECT * FROM aliases WHERE phrase = :phrase LIMIT 1")
    suspend fun findByPhrase(phrase: String): AliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AliasEntity)

    @Query("DELETE FROM aliases WHERE phrase = :phrase")
    suspend fun deleteByPhrase(phrase: String)

    @Query("DELETE FROM aliases WHERE target_package = :packageName")
    suspend fun deleteByTargetPackage(packageName: String)

    @Query("SELECT * FROM aliases ORDER BY created_at DESC")
    fun observeAll(): Flow<List<AliasEntity>>
}
```

- [ ] **Step 5: Register the entity + bump version in `SidrDatabase.kt`**

Add `AliasEntity::class` to the `entities` array, add `import …entity.AliasEntity` + `import …dao.AliasDao`, change `version = 2` → `version = 3`, and add the abstract accessor:

```kotlin
    abstract fun aliasDao(): AliasDao
```

Update the class KDoc to say four learning tables + `aliases` (S2-2), v3 via `Migration2To3`.

- [ ] **Step 6: Write `Migration2To3.kt`** (mirrors `Migration1To2`)

```kotlin
package com.sidr.launcher.data.repository.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 -> v3: adds the `aliases` table (Stage-2 S2-2). The CREATE TABLE below is copied byte-for-byte
 * from the generated golden schema (`3.json`, `aliases` entity's createSql). No data migration —
 * the table is new.
 */
val Migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `aliases` (" +
                "`phrase` TEXT NOT NULL, " +
                "`target_type` TEXT NOT NULL, " +
                "`target_package` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`phrase`))",
        )
    }
}
```

- [ ] **Step 7: Regenerate + commit the golden schema, then run the DAO test**

Run: `./gradlew :data:repository:testDebugUnitTest --tests '*AliasDaoTest'`
Expected: PASS (Room emits `schemas/…/3.json` during the kapt/compile).
Then confirm `data/repository/schemas/com.sidr.launcher.data.repository.db.SidrDatabase/3.json` now exists and contains the `aliases` table. If the `CREATE TABLE` string in `Migration2To3` differs from `3.json`'s `createSql` (column order / `NOT NULL`), copy the golden string verbatim into the migration.

- [ ] **Step 8: Add the 2→3 case to `MigrationTest.kt`**

Mirror the existing 1→2 instrumented case: build the DB at v2 via `MigrationTestHelper`, then `runMigrationsAndValidate(TEST_DB, 3, true, Migration2To3)`. (Add `Migration2To3` to the migrations passed to the helper.)

- [ ] **Step 9: Commit**

```bash
git add data/repository/src/main/java/com/sidr/launcher/data/repository/db/entity/AliasEntity.kt \
        data/repository/src/main/java/com/sidr/launcher/data/repository/db/dao/AliasDao.kt \
        data/repository/src/main/java/com/sidr/launcher/data/repository/db/migrations/Migration2To3.kt \
        data/repository/src/main/java/com/sidr/launcher/data/repository/db/SidrDatabase.kt \
        data/repository/schemas/ \
        data/repository/src/test/java/com/sidr/launcher/data/repository/db/AliasDaoTest.kt \
        data/repository/src/androidTest/java/com/sidr/launcher/data/repository/db/MigrationTest.kt
git commit -m "feat(s2-2): SidrDatabase v3 + aliases entity/DAO + Migration2To3 + golden schema"
```

---

### Task 6: `AliasStoreImpl` + `AliasMapper`

**Files:**
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/mapper/AliasMapper.kt`
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/AliasStoreImpl.kt`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/db/AliasStoreImplTest.kt`

**Interfaces:**
- Consumes: `AliasDao`, `Alias`, `AliasTarget`, `@IoDispatcher CoroutineDispatcher`.
- Produces: `AliasStoreImpl @Inject constructor(dao, ioDispatcher) : AliasStore`; `AliasMapper.toEntity/toDomain` (`toDomain` returns null on unknown `target_type`).

- [ ] **Step 1: Write the failing test** (mirrors `ResolutionPreferenceStoreImplTest`)

```kotlin
package com.sidr.launcher.data.repository.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AliasStoreImplTest {
    private lateinit var db: SidrDatabase
    private lateinit var store: AliasStoreImpl

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), SidrDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = AliasStoreImpl(db.aliasDao(), Dispatchers.Unconfined)
    }

    @After fun tearDown() = db.close()

    @Test fun `round-trips an alias`() = runTest {
        store.upsert(Alias("work chat", AliasTarget.App("com.telegram"), 5L))
        val found = store.find("work chat")
        assertTrue(found is OperationResult.Success)
        assertEquals(AliasTarget.App("com.telegram"), (found as OperationResult.Success).value?.target)
        assertEquals(listOf("work chat"), store.observeAll().first().map { it.phrase })
    }

    @Test fun `find missing returns Success null`() = runTest {
        val r = store.find("nope")
        assertTrue(r is OperationResult.Success && r.value == null)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:repository:testDebugUnitTest --tests '*AliasStoreImplTest'`
Expected: FAIL — `AliasStoreImpl` unresolved.

- [ ] **Step 3: Write `AliasMapper.kt`**

```kotlin
package com.sidr.launcher.data.repository.db.mapper

import com.sidr.launcher.data.repository.db.entity.AliasEntity
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasTarget

/** Entity <-> domain mapping for `aliases` (S2-2). `target_type` is a closed vocabulary (v1: "app"). */
internal object AliasMapper {

    private const val TARGET_TYPE_APP = "app"

    fun toEntity(alias: Alias): AliasEntity {
        val (type, value) = when (val t = alias.target) {
            is AliasTarget.App -> TARGET_TYPE_APP to t.packageName
        }
        return AliasEntity(
            phrase = alias.phrase,
            targetType = type,
            targetPackage = value,
            createdAt = alias.createdAtEpochMs,
        )
    }

    /** Returns null when `target_type` is not a recognized discriminator (caller skips the row). */
    fun toDomain(entity: AliasEntity): Alias? {
        val target = when (entity.targetType) {
            TARGET_TYPE_APP -> AliasTarget.App(entity.targetPackage)
            else -> return null
        }
        return Alias(phrase = entity.phrase, target = target, createdAtEpochMs = entity.createdAt)
    }
}
```

- [ ] **Step 4: Write `AliasStoreImpl.kt`** (mirrors `ResolutionPreferenceStoreImpl`)

```kotlin
package com.sidr.launcher.data.repository.db

import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.repository.db.dao.AliasDao
import com.sidr.launcher.data.repository.db.mapper.AliasMapper
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AliasStoreImpl @Inject constructor(
    private val dao: AliasDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AliasStore {

    override suspend fun find(phrase: String): OperationResult<Alias?> = withContext(ioDispatcher) {
        try {
            OperationResult.Success(dao.findByPhrase(phrase)?.let(AliasMapper::toDomain))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(OperationError.UnknownError(reason = "db_alias_read_failed"))
        }
    }

    override suspend fun upsert(alias: Alias): OperationResult<Unit> = withContext(ioDispatcher) {
        try {
            dao.upsert(AliasMapper.toEntity(alias))
            OperationResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(OperationError.UnknownError(reason = "db_alias_write_failed"))
        }
    }

    override suspend fun delete(phrase: String): OperationResult<Unit> = withContext(ioDispatcher) {
        try {
            dao.deleteByPhrase(phrase)
            OperationResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(OperationError.UnknownError(reason = "db_alias_delete_failed"))
        }
    }

    override fun observeAll(): Flow<List<Alias>> =
        dao.observeAll()
            .catch { emit(emptyList()) }
            .map { rows -> rows.mapNotNull { row -> runCatching { AliasMapper.toDomain(row) }.getOrNull() } }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :data:repository:testDebugUnitTest --tests '*AliasStoreImplTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add data/repository/src/main/java/com/sidr/launcher/data/repository/db/mapper/AliasMapper.kt \
        data/repository/src/main/java/com/sidr/launcher/data/repository/db/AliasStoreImpl.kt \
        data/repository/src/test/java/com/sidr/launcher/data/repository/db/AliasStoreImplTest.kt
git commit -m "feat(s2-2): Room-backed AliasStoreImpl + mapper"
```

---

## Phase C — DI + VM wiring

### Task 7: DI providers/binds (graph-only; no behavior change yet)

**Files:**
- Modify: `app/src/main/java/com/sidr/launcher/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/sidr/launcher/di/MemoryBindsModule.kt`
- Modify: `app/src/main/java/com/sidr/launcher/di/MemoryProvidesModule.kt`

**Interfaces:**
- Consumes: `AliasDao`, `AliasStoreImpl`, `AliasStore`, all alias use-cases, `ResolveCommandWithPreferenceUseCase` (existing provider), `InstalledAppsRepository`.
- Produces: Hilt bindings for `AliasStore`, `SaveAliasUseCase`, `DeleteAliasUseCase`, `ObserveAliasesUseCase`, `PruneUnavailableAliasesUseCase`, and `ResolveCommandWithAliasUseCase` (holding a `ResolvedCommandStep` adapter over the S2-1 use-case) + the DAO provider + `Migration2To3` registration.

- [ ] **Step 1: `DatabaseModule` — provide the DAO + register the migration**

Add the DAO provider:

```kotlin
    @Provides
    fun provideAliasDao(db: SidrDatabase): AliasDao = db.aliasDao()
```

and add `Migration2To3` to the builder: `.addMigrations(Migration1To2, Migration2To3)` (import both). Add imports for `AliasDao` and `Migration2To3`.

- [ ] **Step 2: `MemoryBindsModule` — bind the store**

```kotlin
    @Binds
    @Singleton
    abstract fun bindAliasStore(impl: AliasStoreImpl): AliasStore
```

(add imports `AliasStoreImpl`, `AliasStore`).

- [ ] **Step 3: `MemoryProvidesModule` — provide alias use-cases + the alias decorator**

Add (import the alias package types + `InstalledAppsRepository`):

```kotlin
    @Provides
    @Singleton
    fun provideSaveAliasUseCase(store: AliasStore): SaveAliasUseCase = SaveAliasUseCase(store)

    @Provides
    @Singleton
    fun provideDeleteAliasUseCase(store: AliasStore): DeleteAliasUseCase = DeleteAliasUseCase(store)

    @Provides
    @Singleton
    fun provideObserveAliasesUseCase(
        store: AliasStore,
        installedApps: InstalledAppsRepository,
    ): ObserveAliasesUseCase = ObserveAliasesUseCase(store, installedApps)

    @Provides
    @Singleton
    fun providePruneUnavailableAliasesUseCase(
        store: AliasStore,
        installedApps: InstalledAppsRepository,
    ): PruneUnavailableAliasesUseCase = PruneUnavailableAliasesUseCase(store, installedApps)

    @Provides
    @Singleton
    fun provideResolveCommandWithAliasUseCase(
        inner: ResolveCommandWithPreferenceUseCase,
        store: AliasStore,
        installedApps: InstalledAppsRepository,
    ): ResolveCommandWithAliasUseCase = ResolveCommandWithAliasUseCase(
        inner = ResolvedCommandStep { rawInput -> inner.resolve(rawInput) },
        store = store,
        installedApps = installedApps,
    )
```

- [ ] **Step 4: Verify the Hilt graph compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (graph valid; nothing on the runtime path consumes the new decorator yet — VM still injects the S2-1 use-case).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sidr/launcher/di/DatabaseModule.kt \
        app/src/main/java/com/sidr/launcher/di/MemoryBindsModule.kt \
        app/src/main/java/com/sidr/launcher/di/MemoryProvidesModule.kt
git commit -m "feat(s2-2): DI providers for alias store + use-cases + decorator (Phase C)"
```

---

### Task 8: Wire the decorator into `LauncherViewModel` (swap the injected resolver)

**Files:**
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt`
- Test: `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/LauncherViewModelTest.kt`

**Interfaces:**
- Consumes: `ResolveCommandWithAliasUseCase` (Task 4/7). Its `resolve(rawInput): ResolvedCommand` has the same shape the VM already handles (`Outcome | AutoLaunch`), so `onCommandSubmitted` is unchanged.

- [ ] **Step 1: Add a failing VM test — alias Unknown → direct launch**

In `LauncherViewModelTest`, the VM is constructed with a `resolveCommand` collaborator. Change the test setup's `resolveCommand` type to `ResolveCommandWithAliasUseCase` (built over a `ResolvedCommandStep` lambda + `FakeAliasStore` + `FakeInstalledAppsRepository`), then:

```kotlin
    @Test fun `alias hit on unknown launches the app directly`() = runTest {
        // installed app + a stored alias
        installedAppsRepository.appsToReturn = listOf(InstalledApp("com.telegram", "Telegram"))
        aliasStore.upsert(Alias("work chat", AliasTarget.App("com.telegram"), 1L))
        // inner resolver returns Unknown for this phrase
        innerResult = ResolvedCommand.Outcome(CommandOutcome.Unknown("work chat"), null)

        viewModel.onCommandSubmitted("work chat")
        advanceUntilIdle()

        // launchApp was invoked for com.telegram (assert via the fake ActionExecutor / launch recorder
        // already used by existing AutoLaunch tests), and the command input was cleared on success.
        assertEquals("", viewModel.commandInput.value)
    }
```

(Reuse the existing test's launch-assertion mechanism — the S2-1 `AutoLaunch` tests already assert a package launch; follow that exact pattern.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:launcher:testDebugUnitTest --tests '*LauncherViewModelTest'`
Expected: FAIL — VM still injects `ResolveCommandWithPreferenceUseCase`; the new collaborator type doesn't compile / the alias path isn't reached.

- [ ] **Step 3: Swap the injected type in `LauncherViewModel`**

Change the constructor parameter (and its import) from:

```kotlin
import com.sidr.launcher.domain.memory.resolution.ResolveCommandWithPreferenceUseCase
...
    private val resolveCommand: ResolveCommandWithPreferenceUseCase,
```

to:

```kotlin
import com.sidr.launcher.domain.memory.alias.ResolveCommandWithAliasUseCase
...
    private val resolveCommand: ResolveCommandWithAliasUseCase,
```

Update the constructor KDoc to note the resolver is now the alias decorator wrapping the S2-1 preference decorator (fill-the-gap on Unknown). **No other change** — `onCommandSubmitted` calls `resolveCommand.resolve(text)` and handles `Outcome | AutoLaunch` exactly as before.

- [ ] **Step 4: Run the test + the full VM suite (parity)**

Run: `./gradlew :feature:launcher:testDebugUnitTest`
Expected: PASS — the new alias test passes AND every pre-existing `LauncherViewModelTest` case still passes (non-Unknown / no-alias parity).

- [ ] **Step 5: Verify the Hilt graph still compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt \
        feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/LauncherViewModelTest.kt
git commit -m "feat(s2-2): wire alias decorator into LauncherViewModel (fill-the-gap launch)"
```

---

## Phase D — Management UI (Settings → Aliases)

### Task 9: `Routes.Aliases` + `core/ui` `AliasRow`

**Files:**
- Modify: `core/common/src/main/java/com/sidr/launcher/core/common/navigation/Routes.kt`
- Create: `core/ui/src/main/java/com/sidr/launcher/core/ui/component/AliasRow.kt`

**Interfaces:**
- Produces: `Routes.Aliases.ROUTE = "aliases"`; `@Composable AliasRow(phrase: String, label: String, onDelete: () -> Unit, modifier: Modifier)`.

- [ ] **Step 1: Add the route** (in `Routes.kt`, after `LearnedChoices`)

```kotlin
    object Aliases : Routes() {
        const val ROUTE = "aliases"
    }
```

- [ ] **Step 2: Write `AliasRow.kt`** (mirrors `LearnedChoiceRow`, no state chip)

```kotlin
package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * Terminal-style alias row: `> phrase → label` followed by a delete action. Presentation only —
 * the caller supplies display-safe strings and callbacks (no `domain → ui` dependency).
 */
@Composable
fun AliasRow(
    phrase: String,
    label: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .heightIn(min = Sizes.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "> $phrase → $label",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "[ x ]",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClickLabel = "Delete alias", role = Role.Button, onClick = onDelete)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = Spacing.sm),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun AliasRowPreview() {
    SidrTheme(darkTheme = true) {
        AliasRow(phrase = "work chat", label = "Telegram", onDelete = {})
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL; no `domain → ui` import in `AliasRow.kt`.

- [ ] **Step 4: Commit**

```bash
git add core/common/src/main/java/com/sidr/launcher/core/common/navigation/Routes.kt \
        core/ui/src/main/java/com/sidr/launcher/core/ui/component/AliasRow.kt
git commit -m "feat(s2-2): Aliases route + core/ui AliasRow"
```

---

### Task 10: `AliasesViewModel` (`:feature:settings`)

**Files:**
- Create: `feature/settings/src/main/java/com/sidr/launcher/feature/settings/AliasesViewModel.kt`
- Test: `feature/settings/src/test/java/com/sidr/launcher/feature/settings/AliasesViewModelTest.kt`

**Interfaces:**
- Consumes: `ObserveAliasesUseCase`, `SaveAliasUseCase`, `DeleteAliasUseCase`, `PruneUnavailableAliasesUseCase`, `InstalledAppsRepository` (for the picker list), `AliasView`, `AliasTarget`, `@IoDispatcher`.
- Produces: `AliasesUiState(aliases, pickerApps, isLoading, errorMessage, canRetry)`; `AliasesViewModel { retry(); onSave(phrase, packageName); onDelete(phrase) }`.

- [ ] **Step 1: Write the failing test** (mirrors `LearnedChoicesViewModelTest`)

```kotlin
package com.sidr.launcher.feature.settings

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.memory.alias.DeleteAliasUseCase
import com.sidr.launcher.domain.memory.alias.ObserveAliasesUseCase
import com.sidr.launcher.domain.memory.alias.PruneUnavailableAliasesUseCase
import com.sidr.launcher.domain.memory.alias.SaveAliasUseCase
import com.sidr.launcher.domain.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AliasesViewModelTest {
    private val store = FakeAliasStore()
    private val apps = FakeInstalledAppsRepository().apply {
        appsToReturn = listOf(InstalledApp("com.telegram", "Telegram"))
    }
    private fun vm() = AliasesViewModel(
        observeAliases = ObserveAliasesUseCase(store, apps),
        saveAlias = SaveAliasUseCase(store),
        deleteAlias = DeleteAliasUseCase(store),
        pruneUnavailable = PruneUnavailableAliasesUseCase(store, apps),
        installedApps = apps,
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test fun `save then observe surfaces the alias view`() = runTest {
        val vm = vm()
        vm.onSave("work chat", "com.telegram")
        val state = vm.uiState.first { it.aliases.isNotEmpty() }
        assertEquals("work chat", state.aliases.single().phrase)
        assertEquals("Telegram", state.aliases.single().targetLabel)
    }

    @Test fun `delete removes the alias`() = runTest {
        store.upsert(Alias("bank", AliasTarget.App("com.telegram"), 1L))
        val vm = vm()
        vm.onDelete("bank")
        val state = vm.uiState.first { it.aliases.isEmpty() && !it.isLoading }
        assertEquals(0, state.aliases.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:settings:testDebugUnitTest --tests '*AliasesViewModelTest'`
Expected: FAIL — `AliasesViewModel` unresolved.

- [ ] **Step 3: Write `AliasesViewModel.kt`** (guarded flow, prune-on-load; mirrors `LearnedChoicesViewModel`)

```kotlin
package com.sidr.launcher.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.memory.alias.AliasView
import com.sidr.launcher.domain.memory.alias.DeleteAliasUseCase
import com.sidr.launcher.domain.memory.alias.ObserveAliasesUseCase
import com.sidr.launcher.domain.memory.alias.PruneUnavailableAliasesUseCase
import com.sidr.launcher.domain.memory.alias.SaveAliasUseCase
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AliasesUiState(
    val aliases: List<AliasView> = emptyList(),
    val pickerApps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AliasesViewModel @Inject constructor(
    private val observeAliases: ObserveAliasesUseCase,
    private val saveAlias: SaveAliasUseCase,
    private val deleteAlias: DeleteAliasUseCase,
    private val pruneUnavailable: PruneUnavailableAliasesUseCase,
    private val installedApps: InstalledAppsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val reloadSignal = MutableStateFlow(0)

    val uiState: StateFlow<AliasesUiState> = reloadSignal
        .flatMapLatest { observeState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AliasesUiState())

    fun retry() { reloadSignal.value = reloadSignal.value + 1 }

    fun onSave(phrase: String, packageName: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                saveAlias.save(phrase, AliasTarget.App(packageName))
            } catch (e: CancellationException) {
                throw e
            } catch (_: RuntimeException) {
                // Best-effort: a save failure must not crash the settings surface.
            }
        }
    }

    fun onDelete(phrase: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                deleteAlias.delete(phrase)
            } catch (e: CancellationException) {
                throw e
            } catch (_: RuntimeException) {
            }
        }
    }

    private fun observeState(): Flow<AliasesUiState> = flow {
        emit(AliasesUiState(isLoading = true))
        pruneBestEffort()
        val picker = (installedApps.getInstalledApps() as? OperationResult.Success)?.value.orEmpty()
        emitAll(
            observeAliases.observe().map { aliases ->
                AliasesUiState(aliases = aliases, pickerApps = picker, isLoading = false)
            },
        )
    }.catch { throwable ->
        if (throwable is CancellationException) throw throwable
        emit(AliasesUiState(isLoading = false, errorMessage = LOAD_ERROR, canRetry = true))
    }

    private suspend fun pruneBestEffort() {
        try {
            pruneUnavailable.prune()
        } catch (e: CancellationException) {
            throw e
        } catch (_: RuntimeException) {
        }
    }

    private companion object {
        const val LOAD_ERROR = "couldn't load aliases · retry"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:settings:testDebugUnitTest --tests '*AliasesViewModelTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/settings/src/main/java/com/sidr/launcher/feature/settings/AliasesViewModel.kt \
        feature/settings/src/test/java/com/sidr/launcher/feature/settings/AliasesViewModelTest.kt
git commit -m "feat(s2-2): AliasesViewModel (observe + save + delete + prune)"
```

---

### Task 11: `AliasesScreen` + Settings entry + NavHost registration

**Files:**
- Create: `feature/settings/src/main/java/com/sidr/launcher/feature/settings/AliasesScreen.kt`
- Modify: `feature/settings/src/main/java/com/sidr/launcher/feature/settings/SettingsScreen.kt`
- Modify: `feature/settings/src/main/java/com/sidr/launcher/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/sidr/launcher/navigation/AppNavHost.kt`

**Interfaces:**
- Consumes: `AliasesViewModel`, `AliasRow`, `Routes.Aliases`, existing `SidrScaffold`/`SectionHeader`/`EmptyState`/`ErrorState`/`TopBarIcon` primitives, the S2-1 `SettingsViewModel.openLearnedChoices()` navigation precedent.
- Produces: `@Composable AliasesScreen(onBack)`; a `[ aliases ]` Settings button; `SettingsViewModel.openAliases()`; a `composable(Routes.Aliases.ROUTE)` NavHost node.

- [ ] **Step 1: Add `openAliases()` to `SettingsViewModel`** — mirror `openLearnedChoices()` exactly, emitting `NavigationEvent.NavigateTo(Routes.Aliases.ROUTE)`.

- [ ] **Step 2: Add the Settings entry** — in `SettingsScreen`'s MEMORY section, below the `[ learned choices ]` button, add a `[ aliases ]` button wired to a new `onAliases: () -> Unit` content param (add it to `SettingsContent`'s signature and to the `SettingsScreen` call site as `onAliases = viewModel::openAliases`):

```kotlin
            Button(
                onClick = onAliases,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(text = "[ aliases ]")
            }
```

- [ ] **Step 3: Write `AliasesScreen.kt`** — mirror `LearnedChoicesScreen`'s structure (`SidrScaffold` + back top bar titled "Aliases", `SectionHeader("ALIASES")`, loading/error/empty/list states, `LazyColumn` of `AliasRow` with the app-icon resolver copied from `LearnedChoicesScreen`). Add an **"Add alias"** area: a `TextField` for the phrase + a simple selectable list/dropdown of `uiState.pickerApps` (label + package); a `[ + add ]` button calls `viewModel.onSave(phrase, selectedPackage)` then clears the field. If the normalized phrase equals an installed app's label (case-insensitive), show an advisory line "an app already matches this — the app will win" (non-blocking). Keep all business logic in the VM; the screen only formats + dispatches. Resolve the app icon from `packageName` in the UI layer (copy `rememberAppIcon`/`toImageBitmap` from `LearnedChoicesScreen`).

- [ ] **Step 4: Register the NavHost node** — in `AppNavHost`, after the `Routes.LearnedChoices` composable:

```kotlin
        composable(Routes.Aliases.ROUTE) {
            AliasesScreen(
                onBack = { handleNavigationEvent(navController, NavigationEvent.NavigateBack) },
            )
        }
```

(add `import com.sidr.launcher.feature.settings.AliasesScreen`).

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL; Settings → Aliases is navigable.

- [ ] **Step 6: Commit**

```bash
git add feature/settings/src/main/java/com/sidr/launcher/feature/settings/AliasesScreen.kt \
        feature/settings/src/main/java/com/sidr/launcher/feature/settings/SettingsScreen.kt \
        feature/settings/src/main/java/com/sidr/launcher/feature/settings/SettingsViewModel.kt \
        app/src/main/java/com/sidr/launcher/navigation/AppNavHost.kt
git commit -m "feat(s2-2): Aliases screen + Settings entry + NavHost route"
```

---

## Phase E — Guards, parity, build gate, acceptance

### Task 12: Privacy / scope / dependency / room-inventory guard tests

**Files:**
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/RoomColumnNames.kt`
- Create: `domain/src/test/java/com/sidr/launcher/domain/memory/alias/AliasPrivacyScopeGuardTest.kt`
- Modify (or create alongside): the existing outbound allow-list guard test + a `data:repository` `RoomColumnNamesGuardTest` case.

**Interfaces:**
- Consumes: `OutboundContextPolicy` (existing), `RoomColumnNames` (existing inventory).
- Produces: guard tests proving (a) outbound allow-list unchanged, (b) no generative import in `domain/memory/alias/`, (c) `aliases` table/columns in the Room inventory, (d) `SaveAliasUseCase` scope no-op.

- [ ] **Step 1: Add `aliases` to `RoomColumnNames`** — add the table name `"aliases"` to the `TABLE_NAMES` set and its columns (`phrase`, `target_type`, `target_package`, `created_at`) to the column inventory, following the existing `resolution_preferences` entry. Document that `aliases.phrase` is local-sensitive metadata (same scoping note as `resolution_preferences.query`).

- [ ] **Step 2: Write the failing guard test**

```kotlin
package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scope guard for S2-2: recording/saving an alias is bounded (non-empty, <= MAX_ALIAS_PHRASE_LENGTH).
 * The privacy contract (no alias in an AiRequest, outbound allow-list widened by zero) is proven by the
 * outbound allow-list guard in :domain (unchanged) — this asserts the local scope contract.
 */
class AliasPrivacyScopeGuardTest {
    @Test fun `save no-ops on blank and over-length phrase`() = runTest {
        val store = FakeAliasStore()
        val save = SaveAliasUseCase(store)
        assertTrue(save.save("   ", AliasTarget.App("com.x")) is OperationResult.Success)
        assertTrue(save.save("a".repeat(MAX_ALIAS_PHRASE_LENGTH + 1), AliasTarget.App("com.x")) is OperationResult.Success)
        assertEquals(0, store.observeAll().first().size)
    }
}
```

- [ ] **Step 3: Add a dependency grep guard** — in an existing `:domain` architecture/vendor-neutrality test (or a new `AliasDependencyGuardTest`), assert the `domain/memory/alias/` source directory contains no `AiRequest` / generative import (read the `.kt` files under that dir, fail if any line matches `import .*\.ai\.` other than allowed, mirroring the existing S2-1 `ResolutionPrivacyScopeGuardTest` grep precedent).

- [ ] **Step 4: Assert the outbound allow-list is unchanged** — extend the existing `AiRequestGuardTest` / `OutboundContextPolicy` test to assert its allow-list set equals its prior literal value (the alias work must add nothing). If that assertion already exists, add a comment noting S2-2 widened it by zero; no new value.

- [ ] **Step 5: Run the guard suites**

Run: `./gradlew :domain:test :data:repository:testDebugUnitTest`
Expected: PASS (all guards green; `RoomColumnNamesGuardTest` sees the new table; outbound allow-list unchanged).

- [ ] **Step 6: Commit**

```bash
git add data/repository/src/main/java/com/sidr/launcher/data/repository/db/RoomColumnNames.kt \
        domain/src/test/java/com/sidr/launcher/domain/memory/alias/AliasPrivacyScopeGuardTest.kt \
        domain/src/test/ data/repository/src/test/
git commit -m "test(s2-2): privacy/scope/dependency/room-inventory guards + zero-widen allow-list"
```

---

### Task 13: Full build gate + device acceptance + docs

- [ ] **Step 1: Build gate**

Run: `./gradlew :domain:test testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL (all module unit tests + debug APK).

- [ ] **Step 2: SM-A325F device acceptance** (install debug; drive the slice; capture screenshots):
  - Settings → Aliases → Add "рабочий чат" → pick Telegram → save; the row `> рабочий чат → Telegram` appears.
  - Home: type "рабочий чат" → Telegram launches directly (no candidate list, no confirm card).
  - **Parity:** type a real installed app name → that app opens (alias never shadows it); a genuinely unknown phrase with no alias → the usual "Unknown command" fallback.
  - Settings → Aliases → delete the alias → typing "рабочий чат" again yields Unknown (no launch).
  - Uninstall Telegram → the alias row is gone from the screen and typing the phrase does not launch (no stale target).

- [ ] **Step 3: Docs** — append ADR "2026-07-…​ — S2-2 Explicit Aliases complete (code-closed; device-pending or accepted)" to `ai-context/decisions.md`; sync `CLAUDE.md` (Current goal → S2-2) + `ai-context/current-status.md`; mark this plan CODE-CLOSED. Commit:

```bash
git add ai-context/decisions.md CLAUDE.md ai-context/current-status.md docs/superpowers/plans/2026-07-10-explicit-aliases.md
git commit -m "docs(s2-2): ADR + status sync + mark plan code-closed"
```

**DoD:** build green; device slice observed end-to-end; parity confirmed on device; outbound allow-list provably unchanged; ADR + docs synced.

---

## Self-Review (author checklist — completed)

- **Spec coverage:** §2 scope → T1–T4 (domain) + T5–T6 (persistence) + T7–T8 (wiring) + T9–T11 (UI) + T12 (guards) + T13 (gate/acceptance/docs). §3 model → T1; §3.1 port → T1; §3.2 use-cases → T2 (save), T3 (delete/observe/prune); §4 decorator → T4 + T8; §5 persistence → T5–T6; §6 UI → T9–T11; §7 privacy/parity → T12 (+ parity assertions in T4/T8); §8 edge behavior → T4 (uninstalled/miss/passthrough), T6 (store never-throws), T10 (guarded VM); §9 testing → each task; §10 DoD → T13.
- **Placeholder scan:** none. Every code step shows concrete code; the two prose-described UI/test steps (T5 Step 8 migration case, T11 Step 3 screen) explicitly say "mirror <named existing file>" and name the exact primitives to copy — no `TODO`/`TBD`/"handle edge cases".
- **Type consistency:** `Alias(phrase,target,createdAtEpochMs)`, `AliasTarget.App(packageName)`, `appPackageOrNull()`, `MAX_ALIAS_PHRASE_LENGTH`, `AliasView(phrase,targetPackageName,targetLabel)`, `AliasStore.{find(phrase),upsert(alias),delete(phrase),observeAll()}`, `ResolvedCommandStep`, `ResolveCommandWithAliasUseCase(inner,store,installedApps).resolve(rawInput)`, `AliasEntity(phrase,targetType,targetPackage,createdAt)` cols `phrase/target_type/target_package/created_at`, `AliasDao.{findByPhrase,upsert,deleteByPhrase,deleteByTargetPackage,observeAll}`, `Migration2To3`, `AliasesUiState(aliases,pickerApps,isLoading,errorMessage,canRetry)`, `AliasesViewModel.{retry,onSave(phrase,packageName),onDelete(phrase)}`, `Routes.Aliases.ROUTE="aliases"` — used consistently across tasks. The VM swap keeps the method name `resolve` and return type `ResolvedCommand` so `onCommandSubmitted` is untouched.
- **Ordering:** Room forces entity-registration + version-bump + migration together (T5). DI (T7) precedes VM swap (T8) so the graph resolves. UI route (T9) precedes screen (T11).
