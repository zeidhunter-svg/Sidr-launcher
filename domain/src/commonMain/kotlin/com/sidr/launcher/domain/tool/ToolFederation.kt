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
 * read the same derived [descriptors] list, so they cannot disagree about what exists.
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
     * `id -> adapter`, first declaration winning. Built once; the dispatcher reads it, so it cannot
     * disagree with [registry] about who runs a tool.
     */
    private val byId: Map<ToolId, ToolAdapter> = buildMap {
        adapters.forEach { adapter ->
            adapter.registry.all().forEach { descriptor ->
                if (descriptor.id !in this) put(descriptor.id, adapter)
            }
        }
    }

    private val descriptors: List<ToolDescriptor> = adapters.flatMap { adapter ->
        adapter.registry.all().filter { byId[it.id] === adapter }
    }

    /**
     * `id -> descriptor`, derived from the very [descriptors] list [registry]'s `all()` returns.
     * [find] reads this map rather than re-deriving membership from [byId], so the two faces agree by
     * construction: there is no separate check for "is this id actually in `descriptors`" to have
     * forgotten or gotten wrong, because there is only one source they both read.
     */
    private val descriptorById: Map<ToolId, ToolDescriptor> = descriptors.associateBy { it.id }

    val registry: ToolRegistry = object : ToolRegistry {
        override fun all(): List<ToolDescriptor> = descriptors
        override fun find(id: ToolId): ToolDescriptor? = descriptorById[id]
    }

    val executor: ToolExecutor = object : ToolExecutor {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
            // Unreachable in a well-formed graph: `InvocationValidator.validate` already rejected an
            // unregistered tool against THIS registry, and `byId` is what that registry is built from.
            // Kept fail-closed and labelled as defence in depth rather than named in `CommandFailure`
            // — a variant for a branch that cannot run is the F2/D10 disease (spec §4.4).
            val adapter = byId[invocation.id] ?: return ToolResult.Failed(CommandFailure.Generic)
            return adapter.worker.invoke(invocation)
        }
    }
}

/**
 * **A1' Task 3 compile bridge, not a second production implementation of [ToolExecutor].** A consumer
 * that has not yet been rewired through [ToolFederation] — today, `consumer/jvm`'s `ConsoleHarness` —
 * still constructs `AgentExecutor` with a single [ToolExecutor], and its one registered [ToolWorker] has
 * to reach that shape somehow. Task 4 deletes every caller of this function and replaces it with a real
 * `ToolFederation(listOf(ToolAdapter(...)))`, at which point this bridge has no remaining use and should
 * be deleted, not extended.
 *
 * Declared here rather than inline at each call site so the mechanical guards
 * (`ToolExecutorCallSiteGuardTest`, `ToolWorkerCallSiteGuardTest`) see one more declaration in a file
 * they already recognise as holding both types, instead of a new holder file per bridged consumer.
 */
fun ToolWorker.asToolExecutorBridge(): ToolExecutor = object : ToolExecutor {
    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
        this@asToolExecutorBridge.invoke(invocation)
}
