package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.agent.memory.MemoryToolIds
import com.sidr.launcher.domain.sort.ArgSorts
import com.sidr.launcher.domain.sort.ArgumentSorts
import com.sidr.launcher.domain.sort.SortedArg
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import javax.inject.Inject

/**
 * The launcher's **production** [ArgumentSorts]: which argument of which authored tool carries which
 * sort (A4′ phase 3b, fork 3b-C; ADR «2026-11-07 — A4′ ф3b: сорт — отдельная ось; F6 закрыт»). Until 3b
 * this catalog (phase 0's minimum) knew one sort and spelled it in a method name, `appBindingFor`; the
 * sort system now exists — open `ArgSort` values, a resolver registered per sort (`SortProvidesModule`
 * in `:app`) — and this catalog is Android's declaration over it. `ArgType` stays `STRING`: the type is
 * the wire form, the sort is meaning plus a resolver, declared here beside `ToolDescriptor` rather than
 * inside it.
 *
 * The [ToolPermissionCatalog] shape, for the same reason: a hand-written column in a test is as green
 * when it is wrong as when it is right. `ToolArgumentSortGuardTest` holds the rows to the descriptors
 * in both directions.
 *
 * **A missing row is not "resolve it anyway"** ([ArgumentSorts]' rule): an argument with no row is
 * passed through as the vocabulary or the model produced it.
 *
 * **Who reads which rows, said rather than implied.** Since phase 3b's Task 6 `ToolMatchPlanner` (step
 * 2b) and `DeviceMentionBinder` (the bridge) read [all] and send every `DURATION`, `CLOCK_TIME` and `URL`
 * value through the resolver the sort registry holds for it, before any session or card; the workers
 * still read the same values through the same pure sorts (`DurationSort`, `ClockTimeSort`, `UrlSort`)
 * after «Выполнить», as a second line. The `APP` rows are read through [appArgOf], by the same two
 * callers and the description tests — the app keeps its own searches (windows at 2b, candidates with
 * learned choices at the bridge), not the registry's app resolver.
 *
 * **Two constructors on purpose.** The `@Inject` one is production and takes no arguments, so Dagger
 * sees exactly what it saw before; the `internal` one lets a test state its own rows for a synthetic
 * tool without that tool appearing in production data. The cost, named in phase 0 and still true: only
 * `:data:repository` can supply rows to THIS catalog — a consumer in another module declares its own
 * sorts by implementing [ArgumentSorts], which the port now allows (the JVM sandbox's file sort is
 * planned for phase 3b's Task 13, not built yet).
 */
class CatalogArgumentSorts internal constructor(
    private val rows: Map<ToolId, Map<String, SortedArg>>,
) : ArgumentSorts {
    @Inject constructor() : this(ROWS)

    init {
        // One mention binds one package: a tool with two app arguments is not a shape any caller can
        // bind, so it is refused where the rows are built rather than half-bound where they are read.
        val doubled = rows.filterValues { args -> args.values.count { it.sort == ArgSorts.APP } > 1 }.keys
        require(doubled.isEmpty()) { "more than one app-sorted argument on ${doubled.map { it.value }}" }
    }

    override fun sortOf(tool: ToolId, arg: String): SortedArg? = rows[tool]?.get(arg)

    override fun all(): Map<ToolId, Map<String, SortedArg>> = rows

    /**
     * The one argument of [tool] that carries an app, with its declaration — the read step 2b's planner,
     * the bridge's binder and the description tests make for the app (the two callers read the value
     * sorts through [all] since phase 3b's Task 6). It looks for
     * [ArgSorts.APP] by value, so a tool whose only row is another sort (`set_timer`'s duration) answers
     * `null` here exactly as it did before that row existed.
     */
    fun appArgOf(tool: ToolId): Pair<String, SortedArg>? =
        rows[tool]?.entries?.firstOrNull { it.value.sort == ArgSorts.APP }?.toPair()

    private companion object {
        val ROWS: Map<ToolId, Map<String, SortedArg>> = mapOf(
            // A4′ phase 3a (plan §0.2 B-3): the bridge binds the model's word for an app BEFORE the
            // card — the package into the pin `app`, the user's words into `query`, which the step
            // line, the `resolved_query` output and a "not found" message all speak. `query` is
            // REQUIRED, which this catalog's KDoc says a label argument must not be; that rule exists
            // because ToolMatchPlanner checks required arguments before resolving, and launch_app is
            // never selected by the vocabulary at step 2b. The bridge fills both before validation.
            ToolIds.LAUNCH_APP to mapOf("app" to SortedArg(ArgSorts.APP, labelArg = "query")),
            // A1″ phase 3a, Task 6b / fork F5: resolved ABOVE the consent gate, so the CONFIRM card
            // names the package that will actually be removed rather than the words typed.
            Tier0ToolIds.UNINSTALL_APP to mapOf("app" to SortedArg(ArgSorts.APP, labelArg = "app_label")),
            // A1″ phase 3b, Task 4. No companion label argument — see SortedArg's KDoc.
            Tier0ToolIds.OPEN_APP_INFO to mapOf("app" to SortedArg(ArgSorts.APP, labelArg = null)),
            // A1″ phase 3a, Task 9. The agent changes its OWN memory, on explicit command.
            MemoryToolIds.SET_APP_ALIAS to mapOf("app" to SortedArg(ArgSorts.APP, labelArg = "app_label")),
            // A4′ phase 3b, fork 3b-C: the three pure sorts, resolved before any card by step 2b and the
            // bridge since Task 6 (see the class KDoc); the workers read these values through the same sorts.
            Tier0ToolIds.SET_TIMER to mapOf("duration" to SortedArg(ArgSorts.DURATION, labelArg = null)),
            Tier0ToolIds.SET_ALARM to mapOf("time" to SortedArg(ArgSorts.CLOCK_TIME, labelArg = null)),
            InAppToolIds.OPEN_URL to mapOf("url" to SortedArg(ArgSorts.URL, labelArg = null)),
        )
    }
}
