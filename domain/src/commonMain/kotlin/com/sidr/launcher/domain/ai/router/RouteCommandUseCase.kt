package com.sidr.launcher.domain.ai.router

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.agent.UnresolvedTarget
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.MentionBinder
import com.sidr.launcher.domain.ai.bridge.ModelPlanner
import com.sidr.launcher.domain.ai.bridge.ModelPlanning
import com.sidr.launcher.domain.ai.bridge.plan
import com.sidr.launcher.domain.connectivity.ConnectivityChecker
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.FastPathAnswer
import com.sidr.launcher.domain.intent.FastPathYield
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.memory.alias.appPackageOrNull
import com.sidr.launcher.domain.memory.plan.LearnedPlans
import com.sidr.launcher.domain.memory.plan.Replay
import com.sidr.launcher.domain.memory.plan.needsCard
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.sort.ArgSorts
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import kotlinx.coroutines.flow.first

/**
 * The deterministic gate in front of the model planner (Этап 4.0, ADR 1/4).
 *
 * **Understanding belongs to the model; execution belongs to the deterministic layer.** FastPath
 * ([HandleUserCommandUseCase]) is a latency optimization, not a filter on understanding: it answers
 * frequent exact commands without a model, and a FastPath *miss* is no longer grounds to answer
 * "Unknown command". What stays deterministic is everything on the execution side — nothing the
 * planner proposes auto-executes (Fork R4) except a learned command (step 2c, D3 as amended) and, since D3's second
 * amendment (A4′ acceptance tail, task 4), a fresh reading that owes no pick and sends no model text off the phone
 * (step 7) — both start their sessions through the engine, whose consent card for a risky step is never skipped — and
 * every reason understanding is unavailable is stated
 * honestly rather than disguised as an unrecognized command.
 *
 * Order of decision — an ordered chain of early returns, so **at most one** of the three
 * understanding-unavailable messages can be reached for a single command. The three states those messages
 * name — local-only, a provider, the network — are read **once**, before step 1 (controller ruling K14), and
 * steps 1′, 3, 5 and 6 all decide on those same values: there is no "the path closed between the gate and the
 * answer" branch, because no gate is read twice.
 * 1. Run FastPath ([HandleUserCommandUseCase.answer]) exactly once — offline, and the source of the recorded
 *    side effects.
 * 1′. **The yield** (owner decision 13: Б, A4′ acceptance tail plan §0.2). When the model is reachable — not
 *    local-only, a provider, online — FastPath does not run a **confident search read from a search verb**: it
 *    answers [CommandOutcome.LowConfidence] and hands the search over ([FastPathYield.Search]), so the command is
 *    undecided and goes down the chain — 2b and 2c first, as any undecided text, then step 7 — to the model.
 *    Nothing else yields — not the search FastPath makes out of an unsafe address (`UrlDetector`'s
 *    `SearchFallback`: a defence that keeps a homograph or an unknown TLD from being opened, never handed to a
 *    model that could read it back as `open_url`; task 1 review round 1, ruling (a)), not an address FastPath
 *    opens, not a store search. If the model then cannot take it — `Declined`, `Unavailable` — step 7 runs exactly the
 *    search FastPath would have run ([HandleUserCommandUseCase.runYielded], controller ruling K1), so a broken
 *    key or a timeout never costs the user a working search. **Owner decision 14** (task 2 of the tail plan): FastPath
 *    yields its «нет такого приложения» the same way ([FastPathYield.MissingApp]) — the model is asked first and may
 *    correct the name (amendment D1: «открой клод» → «Claude», bound on the device to an exact label or alias
 *    only); the store plan is step 7's answer when the model cannot take the command (K1) or names an app that is
 *    not here (K3), in the user's words. When the model is not reachable there is no yield, and FastPath answers
 *    byte for byte as before.
 * 1a. **The whole command is an alias ⇒ its app opens, on the device** (A4′ acceptance tail, task 3; owner decision 3,
 *    controller ruling K10 — «алиасы не спрашивать»). Only when FastPath did not decide — `isUndecided()` and nothing
 *    yielded (a yielded command is one FastPath decided, and without the model it never reaches here) — and above
 *    2b: the alias is the user's explicit memory, so «камера» made an alias opens its app, and the tool trigger 2b
 *    would have matched (`open_camera`) is not planned. One `launch_app` pinned to the alias's package, started as a
 *    2b plan is ([aliasLaunch]); no alias, its app gone, a failed read or start — the chain goes on unchanged. A
 *    DECIDED outcome is never shadowed («помощь» stays FastPath's help though «помощь» be an alias), and «открой tg»
 *    is not this step's: FastPath's own launch reads the alias (`IntentActionResolver`), so there is no «нет такого
 *    приложения» to yield. In every state — the owner accepted aliases in local-only.
 * 2. **A goal FastPath DECIDED but did not ACHIEVE ⇒ the agent** ([StartAgentSessionUseCase], A0
 *    Task 11). Exactly one outcome qualifies: [CommandMessage.NoAppFound] — FastPath understood
 *    "open X" and found no such app. Not "any Message", not "anything that did not execute";
 *    widening the list is a separate decision for a later block. Since decision 14 a DECIDED `NoAppFound` exists
 *    only when the model is not reachable — reachable, it is yielded (1′) — so this step starts the store plan
 *    in exactly the states it always did, and the same plan ([storePlan]) is step 7's fallback for the yield.
 * 2b. **A goal FastPath could not decide at all ⇒ offer it to the deterministic planner** (A1' Task
 *    9). The same [StartAgentSessionUseCase], and this gate learns **nothing** about tools: it hands
 *    over the raw command as a [GoalShape.Free] goal and the planner answers. A plan starts a session.
 *    A `NoPlan` — the state of the world for any text with no registered tool behind it — leaves
 *    `ruleOutcome` untouched and the whole chain below runs exactly as it did before. **An
 *    `Unresolved` is answered here, on the device, and never goes further** (A4′ phase 3b, R14-39
 *    closed): a registered tool claimed the text and its target did not bind by itself, so the user
 *    gets «нет такого приложения» in their own words, a pick ([CommandOutcome.ChooseTarget]), or — the
 *    app list unreadable, or a value sort the device could not consult — a generic failure; the model is
 *    not asked. **One exception, controller ruling K2** (task 2 of the tail plan): with the model reachable, an
 *    APP target the device read and found NO candidate for («удали приложение клод») goes on down the chain to
 *    step 7, the target remembered — the same class as decision 14's «нет такого приложения», so the model may
 *    correct the name; if the model declines, cannot answer or names an app that is not here, the target is
 *    answered on the device exactly as before (K11). This partly reverses fork 3b-E — online only, an empty app
 *    target only; local-only, with no provider, offline it is as above. **A VALUE the vocabulary claimed and
 *    its sort cannot read is not answered here**: the planner gives `NoPlan` and the chain below runs as for
 *    any unclaimed text (controller ruling E6-R1, phase 3b Task 6 — a value sort is a pure function of the command text, so nothing of the device
 *    decides what is sent). `StartAgentSessionUseCase.planning`
 *    persists nothing, so an unrecognised or unresolved command leaves no `goal_text` row behind.
 * 2c. **A command the user taught ⇒ replay it on the device, without the model** (A4′ phase 4, spec §8;
 *    plan §0.2 B-5…B-7). Only when FastPath was undecided **and** 2b answered `NoPlan`: it sits in 2b's
 *    `NoPlan` arm, so a text FastPath decided or 2b claimed never reads the learned plans.
 *    [LearnedPlans.replay] fits the command to the one pinned form it matches (a form and template the user
 *    confirmed three times) and judges the filled template exactly as step 7 judges the model's answer — the
 *    same validator, against today's offering; the same [MentionBinder] — with no model. A bound replay that
 *    owes a pick, or sends the model's own text off the phone ([needsCard]), is the interpretation card,
 *    marked as learned ([CommandOutcome.Interpreted.learned]); any other starts its session at once
 *    ([CommandOutcome.AgentSessionStarted.learned]), through the engine, every gate of which — the consent
 *    card included — is unchanged. **A miss is not an answer**: nothing fits, the words bind nothing, the
 *    store cannot be read, the start fails — the chain below runs exactly as if nothing were learned (B-5;
 *    E6-R1: a deterministic miss is not a filter on understanding). An unreadable store therefore fails open
 *    to step 7, named beside A1′ residual (3).
 * 3. **`localOnlyMode` on ⇒ byte-for-byte FastPath parity** — the planner is never consulted and
 *    nothing leaves the device. **Local-only: no yield — FastPath searches byte for byte** (step 1′ never
 *    fires, so a confident search ran in step 1 exactly as before decision 13). A FastPath hit returns
 *    untouched; a miss says so plainly ([CommandMessage.UnderstandingLocalOnly]) instead of claiming the
 *    command was unknown. The same holds with no provider and offline (steps 5, 6): no yield, FastPath's
 *    search returned at step 4.
 * 4. FastPath decided confidently ⇒ return its outcome untouched; the planner is not consulted.
 * 5. **No provider configured ⇒ [CommandMessage.UnderstandingNeedsProvider]**, planner never
 *    consulted. This check lives *here*, in the gate, and not in the planner implementation (Этап
 *    4.0 fork F4): with the flag inverted it is a privacy guarantee — "no provider ⇒ no outbound
 *    call" — and a guarantee may not rest on how one adapter happens to be written.
 *    [AiProviderConfigRepository] is an existing `commonMain` port, so the gate stays KMP-portable
 *    and gains no new module edge (ADR 3/4). The API key deliberately stays the planner's business:
 *    the gate decides *whether to ask*, never touches secrets.
 * 6. **Offline ⇒ [CommandMessage.UnderstandingNeedsNetwork]** — no socket the planner would abandon.
 * 7. **The one model path** (A4′ phase 3a, D3): ask the [ModelPlanner] — having said so first, once, through
 *    [ModelAskedSignal]: the launcher shows its wait on that and on nothing else (A4′ acceptance tail, task 6, controller
 *    ruling K9), so no path above, all of them answered on the device, raises it; bind its step on the device
 *    ([MentionBinder]); **start a bound reading at once when it needs no card** ([needsCard] `false`: no pick owed,
 *    no model-written text going out — D3, second amendment, A4′ acceptance tail task 4; the outcome carries
 *    [CommandOutcome.AgentSessionStarted.proposedByModel], and the engine's consent card still stops a risky step;
 *    a start that fails falls back to the card), or hand back a reading for the card ([CommandOutcome.Interpreted]), the assistant
 *    with the question, a verbatim clarify, an honest «model unavailable», what the binding found instead
 *    of a card («нет такого приложения», «не понял» for a value the device could not read — phase 3b,
 *    Task 6 — or a failure; the model is asked once and told none of it), or — when the model declines
 *    — FastPath's outcome untouched. What was handed to the model is the exception, on both `Declined` and
 *    `Unavailable` ([answerWithoutModel]): a search FastPath yielded at step 1′ is run as FastPath would have run
 *    it (K1); a yielded «нет такого приложения» is A0's store plan (K1); 2b's empty app target is «нет такого
 *    приложения» (K11) — the device's own deterministic answers, not anything the model proposed. And when the
 *    model named an app that is not here ([Binding.NotFound]) for a yielded «нет такого приложения» or a 2b
 *    target, the same device answer stands in the user's words ([notFoundWithoutModel], K3, K11) — the model's
 *    word goes neither to the store nor to the screen. Otherwise nothing here executes (`DOC-NYH-1`, Fork R4): a
 *    session started without a card has acted on nothing yet — the engine runs it, behind its consent gate; the
 *    model is below gates 3, 5 and 6 (`DOC-ADL-3`). A bound reading on the card also carries what it would teach
 *    ([CommandOutcome.Interpreted.learnable], A4′ phase 4 B-8): computed here, from the validated steps
 *    before binding, never inside `toCommandOutcome` (the remainder card shares that and teaches nothing);
 *    nothing is recorded here — the launcher records it once «Выполнить» has started the session. A reading started
 *    without a card teaches nothing (owner answer I5 «не засчитывать»): the cache is not asked. A reading for
 *    2b's empty app target (K2), or after a yielded «нет такого приложения», teaches nothing (review round 1 of
 *    task 2, I1: 2c, where a lesson is replayed, is not on its way back without the model).
 *
 * **Step 1a sits above step 3, and that is `DOC-ADL-3` applied, not bent** (A4′ acceptance tail, task 3; no
 * amendment). The rule's parity is two claims — the model planner is not consulted, and nothing leaves the device —
 * and since the 2026-08-22 amendment the deterministic local path may change an outcome FastPath did not decide.
 * Step 1a is that path: it reads the on-device alias store and app list, consults no model (it has no port to one),
 * transmits nothing, and its launch acts only through the engine, as a 2b plan's does. In local-only mode it answers
 * the alias instead of the local-only line — the user's own memory doing its job. Its exits are a started session or
 * a fall-through that changes nothing, so it can neither produce one of the three understanding-unavailable messages
 * nor suppress another branch's: they stay mutually exclusive for step 2b's reason (below). Online it takes the alias's
 * words away from step 7 — principle 2 («whatever can run on the device runs there»). Should 1a ever reach a model, it
 * moves below step 3; it is not excused. Held by `a bare alias starts its launch on the device and the model is never
 * asked`, `the words of an alias never reach the model` and `a bare alias never shadows a decided FastPath outcome`.
 *
 * **Step 2c sits above step 3, and that is `DOC-ADL-3` applied, not bent** (plan §0.2 B-6; no amendment).
 * The rule's parity is two claims — the model planner is not consulted, and nothing leaves the device — and
 * deterministic plan replay is part of the local path, which since the 2026-08-22 amendment may change an
 * outcome FastPath did not decide. Step 2c consults no model — [LearnedPlans] has no port to one; it reads the
 * on-device store, the registry and the binder — and the route transmits nothing; a replayed step acts only
 * through the engine, as a 2b plan's does. In local-only mode it answers with the command the user taught
 * instead of the local-only line: the deterministic local path doing its job. The three
 * understanding-unavailable messages stay mutually exclusive for step 2b's reason (below): 2c's exits are a
 * started session, a card, or a fall-through that changes nothing — none of them one of the three. Should 2c
 * ever reach a model, it moves below step 3; it is not excused.
 *
 * **Step 2 sits above step 3 and that does not weaken the chain.** The three
 * understanding-unavailable messages stay mutually exclusive because step 2 is keyed on an outcome
 * FastPath *produced* — a decided [CommandMessage.NoAppFound] — and not on a system state, whereas
 * steps 3, 5 and 6 are each keyed on one of the three states. A command that reaches step 2 was never
 * going to reach any of them: `NoAppFound` is a decided outcome, so `isUndecided()` is false and
 * `orHonestly` would have returned it verbatim at step 3 anyway.
 *
 * **Step 2b sits above step 3 too, and that does not weaken the chain either.** Step 2b is the one
 * branch above step 3 that *is* keyed on an undecided outcome, so the argument above (about step 2)
 * does not cover it and it needs its own. It holds for a different reason: **step 2b reports none of
 * the three causes.** Its exits are a [CommandOutcome.AgentSessionStarted], the device answers to an
 * `Unresolved` (A4′ phase 3b: [CommandMessage.NoAppFound], [CommandOutcome.ChooseTarget],
 * [CommandOutcome.Failed], and the fail-safe [CommandMessage.ValueNotUnderstood] that no production
 * planner produces since E6-R1) — none of them one of the three messages — or a fall-through that changes
 * nothing; so it can neither produce one of the three nor suppress another branch's. Whichever of
 * steps 3, 5 and 6 a command was going to reach, it still reaches, with the same message; the only
 * commands step 2b removes from that chain are the ones a registered tool matched, and those never
 * reach it at all. K2's fall-through with a remembered target (controller ruling K2) changes none of this: it
 * happens only when the model is reachable — local-only off, a provider, online — the one state in which none of
 * steps 3, 5 and 6 can answer. The exits are held separately: `all three causes at once - only the outermost is
 * reported` holds the fall-through — it runs with the default
 * [com.sidr.launcher.domain.agent.TemplatePlanner], which answers `NoPlan` to [GoalShape.Free], and it
 * passed unmodified across both changes — `a matching tool starts an agent session even in local-only
 * mode` holds the [CommandOutcome.AgentSessionStarted] exit, and `in local-only mode an unresolved 2b target is
 * still answered on the device`, `an unreadable 2b target fails on the device…`, `a 2b pick starts the session on
 * the tap…` hold the three device answers a production planner can reach (K2's: `online an unresolved 2b app with
 * no candidates goes to the model` and, for its refusal, `online an unresolved 2b app the model declines, cannot
 * answer or names something absent - no such app in the user's words`); `not understood is said in the user's
 * words` holds the fail-safe.
 *
 * **An `Unresolved` answered above step 3 is `DOC-ADL-3` applied, not bent.** It consults no model and
 * transmits nothing — in local-only mode it replaces the "understanding is local-only" line with the
 * honest answer about the target, which is a deterministic path changing an outcome FastPath did not
 * decide, exactly what the rule's 2026-08-22 amendment permits.
 *
 * Steps 2 and 2b are disjoint by construction, not by ordering: `NoAppFound` is a decided
 * [CommandOutcome.Message], so `isUndecided()` is false for it and a `NoAppFound` step 2 answered is never
 * re-planned at 2b. A YIELDED «нет такого приложения» (decision 14, the model reachable) is not step 2's: its
 * outcome is `LowConfidence`, undecided, so it goes down the chain as a yielded search does — 2b and 2c, as any
 * undecided text, then step 7 — and the store plan is step 7's fallback. Step 2c is inside 2b's `NoPlan` arm, so
 * it is disjoint from both the same way: a 2b plan whose start failed falls through to step 3, not to 2c, and so
 * does K2's remembered target. Step 1a opens the same undecided block, above 2b: disjoint from step 2 the same way,
 * and a started alias session reaches neither 2b nor 2c; a 1a that answers `null` leaves them exactly as before.
 *
 * **What the ordering DOES break, stated rather than glossed.** `DOC-ADL-3` used to read "…and
 * every outcome FastPath **decided** is returned byte-for-byte". `NoAppFound` is an outcome FastPath
 * decided, and under `localOnlyMode` this branch replaces it with
 * [CommandOutcome.AgentSessionStarted], so that clause was false exactly where the rule was written to
 * bite. **The amendment landed 2026-08-22** (A0 Task 14, commit `ccf7426`; matrix §6 journal row): the
 * rule now says what it always meant in substance — **no model is consulted and nothing leaves the
 * device** — because deterministic plan replay is part of the local path and may legitimately change an
 * outcome FastPath decided. What survives byte-for-byte is an outcome FastPath decided **and
 * achieved**; `NoAppFound` is decided and *not* achieved, which is why this branch may take it.
 * Cite the rule by ID: it has been amended twice and the §6 journal carries both texts.
 *
 * What the ordering does *not* break is the part that matters: A0's
 * [com.sidr.launcher.domain.agent.Planner] is deterministic and offline, so this branch consults no
 * model and transmits nothing, which is exactly what the signed local-only copy promises. Should that
 * ever stop being true, this branch has to move below step 3, not be excused.
 */
class RouteCommandUseCase(
    private val handleUserCommand: HandleUserCommandUseCase,
    // A4′ phase 3a (D3): the ONE model path. Not `Planner` — that port is asked at step 2b and on resume,
    // where a model may never be called (`DOC-ADL-3`). This one is asked here, at step 7, only.
    private val modelPlanner: ModelPlanner,
    // Binding on the device, AFTER the model has answered: nothing it finds goes back to the model (D1).
    private val binder: MentionBinder,
    private val featureFlagRepository: FeatureFlagRepository,
    private val providerConfigRepository: AiProviderConfigRepository,
    private val connectivityChecker: ConnectivityChecker,
    private val startAgentSession: StartAgentSessionUseCase,
    // A4′ phase 4: step 2c's replay and step 7's `learnable`. The default replays and teaches nothing, so the
    // test construction sites stay as they were (B-12); the production provider takes it with no default
    // (`BridgeProvidesModule.provideRouteCommandUseCase`, phase 4 Task 6), so the graph never builds it with `NONE`.
    private val learnedPlans: LearnedPlans = LearnedPlans.NONE,
    // A4′ acceptance tail, task 3: step 1a's alias, and the app list that says whether its app is still here. Both
    // default to «no step 1a» (`NONE` finds nothing; no app list — nothing is launched), so every construction site
    // that predates it routes exactly as before (B-12); the production provider passes the device's store and list
    // (`BridgeProvidesModule.provideRouteCommandUseCase`).
    private val aliases: AliasStore = AliasStore.NONE,
    private val installedApps: InstalledAppsRepository? = null,
    // A4′ acceptance tail, task 6 (owner decision 6, controller ruling K9): said once, immediately before step 7's one
    // `modelPlanner.plan`, so the launcher's wait is shown only while the model is asked. `NONE` by default, so every
    // construction site that predates it routes exactly as before; the production provider passes the launcher's own
    // events (`BridgeProvidesModule.provideRouteCommandUseCase`, held by `ModelAskedSignalBindingGuardTest`).
    private val modelAsked: ModelAskedSignal = ModelAskedSignal.NONE,
) {

    suspend fun route(rawInput: String): CommandOutcome {
        // (0) Controller ruling K14: the three states that decide whether the model is reachable are read ONCE, here,
        // and steps 1′, 3, 5 and 6 all use these values — there is no branch in which the path closed between the
        // gate and the answer, because the gate is not read twice.
        val localOnly = featureFlagRepository.getFlags().first().localOnlyMode
        val provider = providerConfigRepository.activeConfig().first()
        val online = connectivityChecker.isOnline()
        val open = !localOnly && provider != null && online

        // (1) FastPath first — offline, runs exactly once (its recording side effect too).
        // (1′) Decision 13: Б — with the model reachable, a confident search is handed over, not run: `answer` returns
        // it as `yielded` with `LowConfidence`, so the chain below treats the command as undecided. Not reachable
        // (`open` false) ⇒ `answer` is exactly `handle`, byte for byte.
        val fastPath = handleUserCommand.answer(rawInput, yieldToModel = open)
        val ruleOutcome = fastPath.outcome

        // (2) A goal FastPath DECIDED but did not ACHIEVE: it understood "open X" and found no such
        // app. This is the one outcome A0 hands to the agent — not "any Message", not "anything that
        // did not execute". The branch sits above the localOnlyMode check on purpose: the planner
        // here is deterministic and offline, so it consults no model and transmits nothing, which is
        // what the signed local-only copy promises ("nothing is sent to your AI provider"). It DOES falsify
        // DOC-ADL-3's byte-for-byte clause, which Task 14 Step 1 narrows per spec §8.1 — see the
        // class KDoc. Widening this list is a separate decision for a later block.
        //
        // Owner decision 14: a DECIDED «нет такого приложения» exists only when the model is not reachable
        // (`open` false) — with the model reachable FastPath yields it instead (`FastPathYield.MissingApp`, its
        // outcome `LowConfidence`), and the store plan is step 7's answer when the model cannot take the command or
        // names an app that is not here (K1, K3). Keyed on what FastPath produced, so a decided `NoAppFound` is the
        // store plan in every state, byte for byte as before.
        val message = (ruleOutcome as? CommandOutcome.Message)?.message
        if (message is CommandMessage.NoAppFound) return storePlan(rawInput, message.query)

        // Controller ruling K2: the one target step 2b claimed that goes on to the model — set only when `open`.
        var claimed: UnresolvedTarget? = null

        // (2b) FastPath could not decide, but a registered tool may match the text deterministically.
        // This use case learns NOTHING about tools: it offers the raw goal and the planner answers.
        // `NoPlan` — the case where nothing matches — leaves `ruleOutcome` untouched; step 2c is tried, and on
        // its miss the chain below runs exactly as it did before, which is what the three parity tests pin.
        //
        // It sits above the localOnlyMode check for the same reason branch (2) does: the planner here
        // is deterministic and offline, so no model is consulted and nothing leaves the device. That
        // is precisely what DOC-ADL-3 permits since its 2026-08-22 amendment.
        //
        // Nothing is persisted unless a plan starts: `planning` writes nothing, and `startPlanned`
        // refuses an unrunnable plan before it touches the store, so an unrecognised command does not
        // leave a `goal_text` row behind for every miss. A store failure on a real plan still falls
        // through, as it always has (A1′ residual (3), named, owned elsewhere).
        if (ruleOutcome.isUndecided()) {
            // (1a) A4′ acceptance tail, task 3 (owner decision 3, K10): the whole command is an alias the user made — its
            // app opens through the engine, on the device, in every state; nothing below reads it, the model least of
            // all. Only a command FastPath did not decide: a yielded one (1′, 1″) FastPath DID decide, and without the
            // model the same command never gets here, so with the model it does not either.
            if (fastPath.yielded == null) aliasLaunch(rawInput)?.let { return it }

            val trimmed = rawInput.trim()
            val goal = AgentGoal(text = trimmed, shape = GoalShape.Free(trimmed))
            // Exhaustive with no `else` — this is one of `PlanningResult`'s addresses (its KDoc).
            when (val planned = startAgentSession.planning(goal)) {
                is PlanningResult.Planned -> {
                    val started = startAgentSession.startPlanned(goal, planned.plan)
                    if (started is OperationResult.Success && started.value != null) {
                        return CommandOutcome.AgentSessionStarted(started.value.id)
                    }
                }
                // R14-39 closed (A4′ phase 3b, fork 3b-E): a target the vocabulary claimed is answered
                // HERE and never reaches step 7 — except, since controller ruling K2, an APP target the device
                // could read and found NOTHING for, with the model reachable: the same class as decision 14's
                // «нет такого приложения», so the model may correct the name; its refusal is still answered
                // here (K11). Local-only, with no provider, offline, a pick, an unreadable list — as before.
                is PlanningResult.Unresolved -> {
                    if (open && planned.target.namesNoApp()) claimed = planned.target else return answerOnDevice(planned.target)
                }
                // Nothing claimed it: step 2c — a command the user taught — and on its miss the chain below,
                // exactly as before.
                PlanningResult.NoPlan -> replayLearned(trimmed)?.let { return it }
            }
        }

        // (3) Local-only ⇒ exact FastPath parity; the planner is never consulted. No yield happened (`open` false),
        // so a confident search was run by FastPath byte for byte and is returned here untouched.
        if (localOnly) {
            return ruleOutcome.orHonestly(CommandMessage.UnderstandingLocalOnly)
        }

        // (4) FastPath decided — a confident outcome is returned untouched.
        if (!ruleOutcome.isUndecided()) return ruleOutcome

        // (5) No provider ⇒ nothing may leave the device, and we say why (F4 — gate, not adapter).
        if (provider == null) {
            return CommandOutcome.Message(CommandMessage.UnderstandingNeedsProvider)
        }

        // (6) Offline ⇒ honest "needs network"; never open a socket the planner would abandon.
        if (!online) {
            return CommandOutcome.Message(CommandMessage.UnderstandingNeedsNetwork)
        }

        // (7) The one model path (A4′ phase 3a, D3). The model reads the command and our nineteen tools'
        // descriptions — nothing of this phone (D1). The device then binds the model's words to this
        // phone, and NOTHING the binding finds is sent back: a pick, a "not found", a value not understood
        // go to the user. And
        // nothing here runs anything (condition 1): a reading that owes a pick or sends the model's own text out becomes
        // a card, and only the user's «Выполнить» starts its session; any other bound reading starts its session at
        // once (D3, second amendment, task 4 of the tail plan) — a session, not an act: the engine runs it, and stops a
        // risky step at its consent card.
        val command = rawInput.trim()
        // A4′ acceptance tail, task 6 (owner decision 6, controller ruling K9): the launcher's wait is shown from HERE —
        // once, immediately before the one call to the model — and on no path above, which all answer on the device.
        modelAsked.modelAsked()
        return when (val answer = modelPlanner.plan(command)) {
            // The answer form is `steps` since phase 3b Task 8; since Task 9 the bridge offers and admits up to
            // the engine's own cap (`BridgeRendering.MAX_STEPS_OFFERED`, held by `BridgeValidator`'s size
            // check). The binder takes the WHOLE reading: one card (or one session) for all of its steps, or one
            // answer — the first step it cannot bind — for all of them.
            //
            // A4′ phase 4 (B-8): a BOUND reading carries what it would teach — `learnable`, from the validated
            // steps as the model wrote them (`ModelPlanning.Proposed.steps`, before binding: no package, the
            // user's words still words). Attached HERE and never inside `toCommandOutcome`, which the remainder
            // card shares: a remainder is not a fresh reading and teaches nothing. Nothing is recorded here —
            // the launcher records it once «Выполнить» has started the session.
            is ModelPlanning.Proposed -> {
                val binding = binder.bind(AgentGoal(text = command, shape = GoalShape.Free(command)), answer.steps)
                // K3, K11: the model named an app that is not here — what was handed to it is answered on the device,
                // in the user's words; the model's word goes nowhere.
                if (binding is Binding.NotFound) notFoundWithoutModel(fastPath, claimed, rawInput)?.let { return it }
                val outcome = binding.toCommandOutcome()
                // Review round 1, I1 (controller ruling (a)): a reading for 2b's empty app target (K2), or after
                // FastPath's «нет такого приложения» was yielded (decision 14), teaches nothing. A lesson is replayed
                // at 2c only, and K2's target passes 2c by (2c is 2b's `NoPlan` arm) while without the model 2b
                // answers it on the device; a yielded «нет такого приложения» is, without the model, step (2)'s
                // decided store plan, answered before 2c. Three confirmations would pin a learned command that never
                // runs in those states. (Online a yielded missing app is undecided and does reach 2c — ruled out all
                // the same: one rule for both, the lesson is not offered.)
                val teaches = claimed == null && fastPath.yielded !is FastPathYield.MissingApp
                // D3, second amendment (A4′ acceptance tail, task 4; owner answer «13 / D3: да»): a bound reading that
                // owes no pick and sends none of the model's own text off the phone starts at once, through the engine —
                // the same rule as a learned command at 2c ([needsCard]); a risky step still stops at the engine's
                // consent card. Such a start teaches nothing (owner answer I5 «не засчитывать»): the cache is not even
                // asked. A start that fails is not lost — the reading comes back as the card, as before the amendment.
                if (binding is Binding.Bound) startWithoutCard(binding.interpretation)?.let { return it }
                if (outcome is CommandOutcome.Interpreted) {
                    outcome.copy(learnable = if (teaches) learnedPlans.learnable(command, answer.steps) else null)
                } else {
                    outcome
                }
            }
            // Condition 3: a question goes to the assistant, prefilled and unsent — never to a web search.
            is ModelPlanning.ToAssistant -> CommandOutcome.OpenAssistant(answer.question)
            is ModelPlanning.Navigate -> CommandOutcome.Interpreted(Interpretation.Navigate(answer.destination))
            is ModelPlanning.Clarify -> CommandOutcome.Message(CommandMessage.Verbatim(answer.question))
            // K1, K11: what FastPath yielded or 2b handed over, and the model would not take, is answered as the device
            // would have answered it without the model.
            //
            // With nothing handed over, the model READ the command and found no tool for it: the user is told that
            // (task 5 of the tail plan; owner decision 2: б), not FastPath's «unknown command» — `ruleOutcome` is
            // undecided here (step 4 returned any decided one), and what it says blames the command.
            ModelPlanning.Declined ->
                answerWithoutModel(fastPath, claimed, rawInput) ?: CommandOutcome.Message(CommandMessage.UnderstoodNoAction)
            // Finding (c) of the A1″ acceptance: a broken key or a dead endpoint is said, not hidden — unless something
            // was handed to the model (K1, K11): then the user gets the device's own answer, not «model unavailable» in
            // place of a working search, a store plan or «нет такого приложения».
            is ModelPlanning.Unavailable ->
                answerWithoutModel(fastPath, claimed, rawInput)
                    ?: CommandOutcome.Message(CommandMessage.UnderstandingModelUnavailable(answer.reason))
        }
    }

    /**
     * What the device answers when the model could not take — `Declined`, `Unavailable` — what was handed to it, or
     * `null` when nothing was and the caller answers as before:
     *  - a search FastPath yielded at step 1′ is run exactly as FastPath would have run it (controller ruling K1);
     *  [HandleUserCommandUseCase.runYielded] resolves and executes without a second `intent_match` row;
     *  - FastPath's «нет такого приложения» yielded at step 1′ is A0's store plan, in the user's words (K1);
     *  - step 2b's empty app target handed over by K2 is «нет такого приложения» in the user's words (K11).
     * FastPath's yield comes first: it is what FastPath decided, and what the device did before decision 14.
     */
    private suspend fun answerWithoutModel(fastPath: FastPathAnswer, claimed: UnresolvedTarget?, raw: String): CommandOutcome? =
        when (val yielded = fastPath.yielded) {
            is FastPathYield.Search -> handleUserCommand.runYielded(yielded)
            is FastPathYield.MissingApp -> storePlan(raw, yielded.message.query)
            null -> claimed?.let { answerOnDevice(it) }
        }

    /**
     * The model answered, and the app it named is not on this phone (`Binding.NotFound`). What was handed to it for
     * a missing app is answered on the device in the USER's words — the model's word never reaches the store or the
     * screen (D1, egress): FastPath's yielded «нет такого приложения» is A0's store plan (controller ruling K3), 2b's
     * empty app target «нет такого приложения» (K11). A yielded search, or nothing handed over, is `null`: the
     * binding's own answer stands — `NoAppFound(mention)`, which since amendment D1 may be the MODEL's word («нет
     * такого приложения «Claudex»»), said with no model mark (the E6-R2 class; a marked line is the owner's
     * decision — ADR «2026-11-22 — D1, поправка…», «Чего поправка не обещает»). So is a 2b target when FastPath also
     * yielded a search: the yield answers first, and this returns `null` for it.
     */
    private suspend fun notFoundWithoutModel(fastPath: FastPathAnswer, claimed: UnresolvedTarget?, raw: String): CommandOutcome? =
        when (val yielded = fastPath.yielded) {
            is FastPathYield.MissingApp -> storePlan(raw, yielded.message.query)
            is FastPathYield.Search -> null
            null -> claimed?.let { answerOnDevice(it) }
        }

    /**
     * Step (2)'s own answer, also step 7's fallback for a yielded «нет такого приложения» (K1, K3): A0's two-step
     * plan for [query] — the app name FastPath extracted, in the user's words — started as a session
     * (`GoalShape.AppNotInstalled`); or, when no plan starts (`NoPlan`, any store failure), «нет такого приложения»
     * in those words, byte for byte the outcome the chain returned before (a decided `NoAppFound` was returned
     * untouched at step 3 or 4). Deterministic and offline: it consults no model and transmits nothing.
     */
    private suspend fun storePlan(raw: String, query: String): CommandOutcome {
        val goal = AgentGoal(text = raw.trim(), shape = GoalShape.AppNotInstalled(query))
        val started = startAgentSession.start(goal)
        return if (started is OperationResult.Success && started.value != null) {
            CommandOutcome.AgentSessionStarted(started.value)
        } else {
            CommandOutcome.Message(CommandMessage.NoAppFound(query))
        }
    }

    /**
     * Step 1a (A4′ acceptance tail, task 3; owner decision 3, K10): the WHOLE command — normalized as the alias screen
     * stores a phrase (`SaveAliasUseCase`) — is the phrase of an alias whose app is installed, so that app opens: one
     * `launch_app` pinned to the alias's package, the user's words beside it, started through
     * [StartAgentSessionUseCase.startPlanned] as a 2b plan is — validated against the registry, and the engine's gate
     * reads the registry's risk, not this plan's. `null` — no alias under these words, a non-app target, its app not
     * installed (the alias outlived it), a read that failed, no app list wired, a start that failed — and the chain
     * goes on exactly as if there were no alias. A word FORM of an alias is not read here: a bare «телегу» is not the
     * alias «телега» (FastPath's launch reads forms — «открой телегу» — `IntentActionResolver`).
     */
    private suspend fun aliasLaunch(raw: String): CommandOutcome? {
        val apps = installedApps ?: return null
        val words = raw.trim()
        val alias = (aliases.find(CommandNormalizer.normalize(words)) as? OperationResult.Success)?.value ?: return null
        val pkg = alias.target.appPackageOrNull() ?: return null
        val installed = (apps.getInstalledApps() as? OperationResult.Success)?.value ?: return null
        if (installed.none { it.packageName == pkg }) return null

        val goal = AgentGoal(text = words, shape = GoalShape.Free(words))
        val launch = PlanStep(
            index = 0,
            invocation = ToolInvocation(
                ToolIds.LAUNCH_APP,
                mapOf(LAUNCH_QUERY_ARG to ArgSource.Literal(words), LAUNCH_PIN_ARG to ArgSource.Literal(pkg)),
            ),
            risk = ActionRiskLevel.SAFE,
            precondition = StepPrecondition.None,
            rationale = StepRationale.GOAL_DIRECT,
        )
        val started = startAgentSession.startPlanned(goal, ExecutionPlan(listOf(launch)))
        return if (started is OperationResult.Success && started.value != null) {
            CommandOutcome.AgentSessionStarted(started.value.id)
        } else {
            null
        }
    }

    /**
     * Controller ruling K2's target: an APP target the device COULD read ([UnresolvedTarget.unreadable] false) and
     * found no candidate for — not a pick, not a value sort, not a failed read.
     */
    private fun UnresolvedTarget.namesNoApp(): Boolean = sort == ArgSorts.APP && candidates.isEmpty() && !unreadable

    /**
     * Step 7's start without a card (D3, second amendment; A4′ acceptance tail, task 4): a FRESH bound reading whose
     * plan owes no pick and sends none of the model's own text off the phone ([needsCard] `false` — the rule a learned
     * command replays by at 2c) is started at once through [StartAgentSessionUseCase.startPlanned], and the outcome
     * carries the model's mark ([CommandOutcome.AgentSessionStarted.proposedByModel], controller ruling K4). Every gate
     * of the engine is unchanged: `startPlanned` validates the plan against the registry, and the engine's consent
     * checkpoint stops a risky step (`requiresConsent(risk)`, `DOC-ADL-1`) — `uninstall_app`, `open_url`. `null` — the
     * reading needs the card, or the start failed ([OperationResult.Failure], a plan the registry refuses) — and step 7
     * answers with the card exactly as before the amendment.
     */
    private suspend fun startWithoutCard(act: Interpretation.Act): CommandOutcome? {
        val plan = act.plan() ?: return null
        if (act.needsCard()) return null
        val started = startAgentSession.startPlanned(act.goal, plan)
        return if (started is OperationResult.Success && started.value != null) {
            CommandOutcome.AgentSessionStarted(started.value.id, proposedByModel = true)
        } else {
            null
        }
    }

    /**
     * Step 2c (A4′ phase 4, plan §0.2 B-5…B-7): [command] replayed from the one pinned form it fits, or `null` —
     * and `null` means the chain goes on exactly as if nothing were learned. The cache never answers on
     * understanding's behalf: a [Replay.Miss] (nothing fits, the binding found nothing, the store unreadable) is
     * not «нет такого приложения» and not «не понял».
     *
     * A bound replay that owes a pick, or sends the model's own text off the phone ([needsCard]), is shown on the
     * interpretation card first, with its mark ([CommandOutcome.Interpreted.learned]) and nothing to teach. Any
     * other starts at once through `startPlanned` — the engine's every gate unchanged: `uninstall_app` still stops
     * at its consent card. A start that fails ([OperationResult.Failure], or a plan the registry refuses) is
     * `null` too — the fall-through step 2b gives its own failed start.
     */
    private suspend fun replayLearned(command: String): CommandOutcome? =
        when (val replay = learnedPlans.replay(command)) {
            Replay.Miss -> null
            is Replay.Bound -> {
                val plan = replay.act.plan()
                if (plan == null || replay.act.needsCard()) {
                    CommandOutcome.Interpreted(replay.act, learned = replay.form)
                } else {
                    val started = startAgentSession.startPlanned(replay.act.goal, plan)
                    if (started is OperationResult.Success && started.value != null) {
                        CommandOutcome.AgentSessionStarted(started.value.id, learned = replay.form)
                    } else {
                        null
                    }
                }
            }
        }

    /**
     * Step 2b's answer to a target a registered tool claimed and could not bind (A4′ phase 3b). The
     * order is the order of what the device knows: it could not ask ([UnresolvedTarget.unreadable] — the
     * app list unreadable, or a value sort the device could not consult; said as step 7 says
     * `Binding.Unavailable`, controller ruling E5-P1); it found candidates — the user picks; it found
     * nothing for an app — «нет такого приложения» in the user's words. Never step 7.
     *
     * The last branch is a **fail-safe with no production producer since controller ruling E6-R1**: an
     * empty, readable `Unresolved` of a value sort would be «не понял» in its words
     * ([CommandMessage.ValueNotUnderstood], ruling E6-P2) rather than a generic failure — but
     * `ToolMatchPlanner` answers a value its sort cannot read with `NoPlan`, which never reaches here. The
     * branch stays so that a future planner which does produce one says something true.
     */
    private fun answerOnDevice(target: UnresolvedTarget): CommandOutcome = when {
        target.unreadable -> CommandOutcome.Failed(CommandFailure.Generic)
        target.candidates.isNotEmpty() -> CommandOutcome.ChooseTarget(target)
        target.sort == ArgSorts.APP -> CommandOutcome.Message(CommandMessage.NoAppFound(target.words))
        else -> CommandOutcome.Message(CommandMessage.ValueNotUnderstood(target.words))
    }

    /**
     * FastPath could not decide — the only two states in which understanding is wanted at all.
     * [CommandOutcome.Empty] is deliberately *not* undecided: an empty submit is a hint, not a goal,
     * and must never cost a round-trip or an "understanding unavailable" line.
     */
    private fun CommandOutcome.isUndecided(): Boolean =
        this is CommandOutcome.Unknown || this is CommandOutcome.LowConfidence

    /** Keeps a decided FastPath outcome byte-for-byte; replaces only the "unknown command" claim. */
    private fun CommandOutcome.orHonestly(message: CommandMessage): CommandOutcome =
        if (isUndecided()) CommandOutcome.Message(message) else this

    private companion object {
        /**
         * `launch_app`'s two arguments, spelled in `:data:repository` (`SystemIntentToolSource`: the catalog's `query`,
         * and `PIN_ARG`), which `:domain` has no edge to. A drift fails closed: `startPlanned` validates the step
         * against the registry, an undeclared argument is not runnable, and step 1a answers `null` — and then, online,
         * the alias's words would go on to the model. So the drift is held, not only failed closed:
         * `AliasBindingGuardTest` (`:app`) starts step 1a's plan against the PRODUCTION registry.
         */
        const val LAUNCH_QUERY_ARG = "query"
        const val LAUNCH_PIN_ARG = "app"
    }
}

/**
 * What the device's binding of a reading becomes on screen — step 7's mapping, and the ONE mapping
 * (controller ruling E11-P5): the remainder card of a model plan that acted (`MentionBinder.rebind`, A4′
 * phase 3b, Task 11) says «нет такого приложения», «не понял» or the generic failure through this same
 * function, never a second spelling. A bound reading becomes the interpretation card; nothing here runs
 * anything, and nothing it finds goes back to the model (D1).
 */
fun Binding.toCommandOutcome(): CommandOutcome = when (this) {
    is Binding.Bound -> CommandOutcome.Interpreted(interpretation)
    is Binding.NotFound -> CommandOutcome.Message(CommandMessage.NoAppFound(mention))
    // A4′ phase 3b, Task 6: a value its sort could not read is said in the words written for it — to the
    // user, never back to the model, and not a reason to ask the model again.
    is Binding.NotUnderstood -> CommandOutcome.Message(CommandMessage.ValueNotUnderstood(words))
    Binding.Unavailable -> CommandOutcome.Failed(CommandFailure.Generic)
}
