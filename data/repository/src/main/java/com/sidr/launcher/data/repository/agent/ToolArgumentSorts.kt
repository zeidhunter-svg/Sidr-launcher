package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.agent.memory.MemoryToolIds
import com.sidr.launcher.domain.tool.ToolId
import javax.inject.Inject

/**
 * Which argument of a tool carries an app, and where the words the user actually typed should go.
 *
 * **There is no `ArgumentSort` enum here, and its absence is the decision.** `ToolMatchPlanner`
 * carried `const val APP_ARG = "app"` with the KDoc "*the argument declares that its value is an
 * app, resolve it for me*" — which is a definition of a sort implemented by comparing strings. What
 * phase 0 removes is the string; a one-value enum would be a taxonomy with one member and no
 * dispatch, which is the shape §4 forbids building ahead of a requirement that can only be guessed.
 * The sort **system** — a resolver registered per sort — is phase 3, where `B10` is a consumer whose
 * requirement can be NAMED (spec §7.8), and `F6` is closed there by ADR rather than silently. Until
 * then the sort this catalog speaks of is in the method's name.
 *
 * [labelArg] is `null` for a tool that declares no companion: `open_app_info` has none, which is why
 * its step line renders the resolved package rather than the typed word (A1″ residual (10) — a
 * legibility limit, named and deliberately not softened).
 */
data class AppArgumentBinding(val arg: String, val labelArg: String?)

/**
 * The one **production** statement of which tool arguments carry an app — the [ToolPermissionCatalog]
 * shape, for the same reason: a hand-written column in a test is as green when it is wrong as when
 * it is right.
 *
 * **A missing row is not "resolve it anyway".** An id with no row means nobody has declared a sort,
 * and the planner then resolves nothing and passes the value through as the vocabulary produced it.
 * That is the conservative direction: resolving an undeclared argument would be the guess
 * `AppTargetResolver` exists to refuse.
 *
 * **Two constructors on purpose.** The `@Inject` one is production and takes no arguments, so Dagger
 * sees exactly what it saw before; the `internal` one lets a test state its own rows for a synthetic
 * tool without that tool appearing in production data. `RoomAgentSessionStore`'s shape, except that
 * its primary constructor is public and this one is not.
 *
 * **Not the shape the spec named, said rather than blurred.** Spec §3 0.4 asked for "a parallel port
 * à la `DynamicToolNames`"; what ships is a concrete catalog in the [ToolPermissionCatalog] shape
 * instead. What that form was FOR is kept — the declaration sits beside `ToolDescriptor`, not inside
 * it, so `ToolDescriptor` and `ArgType` are untouched. What is dropped is the interface, because
 * nothing here needed one yet: `DynamicToolNames` is a port because a tool SOURCE produces its data
 * (`ShortcutToolSource` implements it from the same runtime snapshot as its descriptors) and more
 * than one reader consumes it, whereas these rows are static, hand-declared facts about authored
 * tools with a single reader, [ToolMatchPlanner], and a test varies them through the `internal`
 * constructor rather than through a fake. The cost, named: with the primary constructor `internal`,
 * only `:data:repository` can supply rows, so a future adapter in another module cannot declare its
 * own sort without editing the map below — which a port implemented by each source would have
 * allowed. That is an input for phase 3's sort system, not a property to rely on.
 *
 * It lives in `:data:repository` because `AppTargetResolver` — the only thing that can answer this
 * sort today — lives here, and because `:domain` is `commonMain`.
 */
class ToolArgumentSorts internal constructor(
    private val rows: Map<ToolId, AppArgumentBinding>,
) {
    @Inject constructor() : this(ROWS)

    fun appBindingFor(id: ToolId): AppArgumentBinding? = rows[id]

    fun rows(): Map<ToolId, AppArgumentBinding> = rows

    private companion object {
        val ROWS: Map<ToolId, AppArgumentBinding> = mapOf(
            // A1″ phase 3a, Task 6b / fork F5: resolved ABOVE the consent gate, so the CONFIRM card
            // names the package that will actually be removed rather than the words typed.
            Tier0ToolIds.UNINSTALL_APP to AppArgumentBinding(arg = "app", labelArg = "app_label"),
            // A1″ phase 3b, Task 4. No companion label argument — see AppArgumentBinding's KDoc.
            Tier0ToolIds.OPEN_APP_INFO to AppArgumentBinding(arg = "app", labelArg = null),
            // A1″ phase 3a, Task 9. The agent changes its OWN memory, on explicit command.
            MemoryToolIds.SET_APP_ALIAS to AppArgumentBinding(arg = "app", labelArg = "app_label"),
        )
    }
}
