package com.sidr.launcher.domain.tool

import com.sidr.launcher.domain.intent.CommandFailure

/**
 * One source's worker: what actually performs a tool call for the adapter that declares it.
 *
 * **This is not a second boundary.** The boundary is [ToolExecutor], of which [ToolFederation] provides
 * the single implementation; a worker is reachable only from that implementation, and
 * `ToolWorkerCallSiteGuardTest` holds that mechanically. What federation adds is one hop, guarded
 * twice — not a strengthening, and this KDoc must not be read as claiming one.
 */
interface ToolWorker {
    suspend fun invoke(invocation: ResolvedInvocation): ToolResult
}

/** One federated source: its level, what it offers, and how it runs. */
data class ToolAdapter(
    val level: ToolLevel,
    val registry: ToolRegistry,
    val worker: ToolWorker,
)

/**
 * The federation. **One object, two faces, one list** — and that is load-bearing rather than tidy.
 *
 * Two independent combinators each taking a `List<ToolAdapter>` would receive their list by wiring
 * convention; a graph that ever provided them separately could hand them different lists, and the
 * registry would advertise a tool the dispatcher cannot route. That is the shape of A0 review finding
 * `F2`, where the consent gate read `risk` from the persisted plan and `permissionGate` from the live
 * registry — two sources for one decision. Deriving both faces from [adapters] here makes "the same
 * list" a property of construction rather than of discipline: `all()` and `find()` on [registry] both
 * read the same derived list, so they cannot disagree about what exists.
 *
 * **Since A1″ that derivation runs per call rather than once in the constructor.** That strengthens
 * the argument above rather than replacing it: both faces still read one derivation, and now that
 * derivation also cannot be stale. See [snapshot].
 *
 * **Collision policy: first adapter wins.** A duplicate [ToolId] is dropped from the later adapter, in
 * both faces, from the one index below. Symmetric exclusion was rejected: under a future third-party
 * source it is a capability-denial vector — naming a collision would take the built-in down too.
 * Precedence is therefore the composition root's ordering decision, which is why [ToolLevel] needs no
 * order of its own. Production is additionally held collision-free by `DoctrineGuardTest`, so this
 * runtime rule is defence in depth, not the mechanism.
 */
class ToolFederation(private val adapters: List<ToolAdapter>) {

    /**
     * One derivation of the federated tool set: what exists, and who runs each of them.
     *
     * `descriptorById` is derived from the very `descriptors` list [registry]'s `all()` returns, so
     * `find` does not re-derive membership from `byId`. There is no separate "is this id actually in
     * `descriptors`" check to have forgotten or gotten wrong, because there is only one list they
     * both read.
     */
    private class Snapshot(
        val descriptors: List<ToolDescriptor>,
        val byId: Map<ToolId, ToolAdapter>,
    ) {
        val descriptorById: Map<ToolId, ToolDescriptor> = descriptors.associateBy { it.id }
    }

    /**
     * Derived on every call, by both faces.
     *
     * This was computed once in the constructor until A1″. That was correct while every source was
     * static and wrong the moment one was not: the composition root provides this class as a
     * `@Singleton`, so a source whose tool set moves — an app installed, a shortcut removed — would
     * have been read once per process and never again.
     *
     * **Each adapter is read exactly once here, and both maps are built from that one read.** Calling
     * `all()` twice — once to build [Snapshot.byId], once to filter the descriptor list — would reopen
     * the gap this class exists to close, because a moving source may answer the two reads
     * differently: a tool dropped between them lands in `byId` but not in `descriptors`, and the two
     * faces of a single snapshot then disagree about what exists. Static sources cannot show this,
     * which is why it was invisible before A1″.
     *
     * Cost: one `all()` per adapter plus two maps, per call. Measured rather than assumed — see this
     * block's ADR.
     */
    private fun snapshot(): Snapshot {
        val declared = adapters.map { adapter -> adapter to adapter.registry.all() }
        val byId = buildMap {
            declared.forEach { (adapter, tools) ->
                tools.forEach { descriptor ->
                    if (descriptor.id !in this) put(descriptor.id, adapter)
                }
            }
        }
        val descriptors = declared.flatMap { (adapter, tools) ->
            tools.filter { byId[it.id] === adapter }
        }
        return Snapshot(descriptors, byId)
    }

    val registry: ToolRegistry = object : ToolRegistry {
        override fun all(): List<ToolDescriptor> = snapshot().descriptors
        override fun find(id: ToolId): ToolDescriptor? = snapshot().descriptorById[id]
    }

    val executor: ToolExecutor = object : ToolExecutor {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
            // Reachable since A1″, and it was not before: a dynamic source may lose a tool between the
            // moment a plan was made and the moment this step runs. `InvocationValidator.validate`
            // still rejects an unregistered tool at plan time and on resume; this is the narrower
            // window it cannot cover, and it fails closed rather than routing to a stale adapter.
            // Still not named in `CommandFailure` — the user-facing story is "that is no longer
            // available", which `Generic` already carries, and a variant per unreachable-ish branch is
            // the F2/D10 disease (spec §4.4).
            val adapter = snapshot().byId[invocation.id]
                ?: return ToolResult.Failed(CommandFailure.Generic)
            return adapter.worker.invoke(invocation)
        }
    }
}
