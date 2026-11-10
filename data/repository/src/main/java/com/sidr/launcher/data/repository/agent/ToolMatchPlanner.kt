package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.Planner
import com.sidr.launcher.domain.agent.PlanningRequest
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.agent.UnresolvedTarget
import com.sidr.launcher.domain.intent.SecondGoal
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.sort.ArgSort
import com.sidr.launcher.domain.sort.ArgSorts
import com.sidr.launcher.domain.sort.SortResolution
import com.sidr.launcher.domain.sort.SortResolver
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolRegistry
import javax.inject.Inject

/**
 * **One generic arm, not a taxonomy** (spec §8.4). A recognised tool becomes a one-step plan, whatever
 * the tool is — so every tool `A1"` added cost one vocabulary entry and **zero** planner branches.
 * (The number is deliberately not written: this line said "twelve tools" and was already false when
 * phase 3b shipped the eleven navigating ones — R14-41.)
 * The rejected alternative was one `GoalShape` value and one arm per tool, which is linear per tool
 * and contradicts in code the claim this whole block exists to establish.
 *
 * `GoalShape` gains nothing: this plans the `Free` value A0.5 already added, which until now answered
 * `NoPlan` on Android. `StepRationale` gains nothing either — a one-step plan is `GOAL_DIRECT`, and
 * the surface names the tool rather than assuming a launch.
 *
 * **Risk comes from the registry, never from here.** The planner records what the registry declares at
 * plan time, and `AgentExecutor` gates on `maxOf(step.risk, registry.find(...)?.risk ?: DANGEROUS)`
 * (A0 finding F2), so a tool that becomes riskier between planning and execution still stops.
 *
 * **It recognises, and since phase 3b's Task 6 it reads every sorted value before any session.**
 * [ToolSelector] hands the remainder over **normalized** — lower-cased and whitespace-collapsed, never
 * the raw span; see `ToolVocabulary`'s KDoc for the free-text limitation that implies. Until Task 6 this
 * planner only checked that a required argument was *present*, so «поставь таймер на полчасика» planned,
 * started a session and failed at the worker. Now each argument [CatalogArgumentSorts] declares with a
 * VALUE sort (duration, clock time, address) is read by the resolver the sort registry
 * (`Map<ArgSort, SortResolver>`, `SortProvidesModule` in `:app`) holds for it — the same pure sorts the
 * workers read after «Выполнить» (fork 3b-C, B-5; `valuesOf` has the rules). A value that reads binds as
 * the sort gives it. **A value that does not read is `NoPlan` — left to the model** (controller ruling
 * E6-R1; ADR 1/4 rules 1 and 3): the vocabulary is a latency path, not a filter on understanding, so
 * «поставь таймер на пять минут» goes on to steps 3–7 like any text no tool claimed — the local-only,
 * no-provider or offline line where one applies, the model online — rather than «не понял» from a
 * parser that knows no number words. No session starts and no card is drawn here either way. R14-39's
 * reason for answering a claimed TARGET on the device does not carry over to a value: a value sort is a
 * pure function of the command text the model would receive anyway, so no device data decides what is
 * sent. A value sort the device cannot consult — no resolver registered, [SortResolution.Unavailable],
 * a [SortResolution.Choice] — fails closed instead (E6-F2): a broken registry is not a reason to send.
 * Not a richer `ArgType`, which stays `STRING` for good (ADR «… сорт — отдельная ось; F6 закрыт»), and
 * not a second parser here. The workers keep reading the same values: a belt under this check, not a
 * replacement for it. Reachable at 2b today: `set_timer`'s duration and `set_alarm`'s clock time — the
 * URL row is not, because no vocabulary entry claims `open_url` (a typed address is FastPath's
 * `UrlDetector`; controller ruling E6-P1).
 *
 * **An argument the tool DECLARES to carry an app is resolved to a package HERE, above the consent
 * checkpoint** (review finding C4, owner decision 2026-09-18; fork F5, «consent fires before argument
 * binding»). Which argument that is, if any, is read from [CatalogArgumentSorts.appArgOf] — never from
 * the argument's spelling (A4′ phase 0, spec §3 0.4). `AgentExecutor` evaluates `checkpointFor` before it
 * ever reaches `toolExecutor.invoke`, so anything a **worker** resolves is resolved *after* the user
 * has already consented: the card for «удали telegram»
 * could then only echo the words typed, while the package that would actually be uninstalled did not
 * yet exist — and on a device carrying two Telegram-like labels the user would confirm one thing and
 * get another. Resolving in the planner is what makes the plan, and therefore the card, name the target
 * that will be acted on. This does not close F5 in general; it closes it for the arguments this planner
 * can resolve deterministically.
 *
 * **A target that does not bind by itself is [PlanningResult.Unresolved] — never `NoPlan`** (A4′ phase
 * 3b, fork 3b-E as refined in plan §0.1; R14-39 closed). The decline still happens *before* a plan
 * exists, so no consent card is ever drawn for a name that did not resolve, and the resolver still
 * never "falls back to the raw text". What changed is who hears it: `NoPlan` at step 2b falls through to
 * the cloud model, so from A1″ Task 6b until this phase an unresolved name decided that a command a
 * registered tool had already claimed left the device as raw text. `Unresolved` is answered by step 2b
 * on the device. The search is [AppTargetResolver.windows] over the target's words:
 *  - the whole words name one app exactly → the plan, as before;
 *  - two apps answer exactly → the user picks (`confirm = false`);
 *  - a word form, or a name found in a **window shorter than the words** («удали приложение миравин
 *    пожалуйста» → «Миравин») → offered for confirmation, **never bound** (`confirm = true`): the extra
 *    words may name another app (phase 1's F1-2, «lumora studio»), and for `uninstall_app` a bind on a
 *    guess is an irreversible act;
 *  - nothing → «нет такого приложения» in the user's words; the list unreadable → a failure, marked
 *    `unreadable` (controller ruling E5-P1).
 *
 * **A second goal in the target's words still reaches the model** (B-15). Phase 1's `SecondGoal`
 * check runs on the words BEFORE the windows — «удали приложение lumora и проверь почту» is `NoPlan` —
 * because a window would otherwise offer «Lumora» and silently drop «проверь почту», where the model
 * plans both. Two limits, named: aliases answer the whole words only, never a window (controller ruling
 * E5-P5 — «телега пожалуйста» does not reach the alias «телега»); and the check reads the app argument
 * only. A second goal in a VALUE slot reaches the model by the value rule above instead, not by this
 * check: each value parser refuses trailing words (a unit word with whitespace in it, an anchored `H:MM`,
 * whitespace in an address), so «поставь будильник на 7:30 и открой lumora» reads as no clock time and
 * is `NoPlan` (E6-R1).
 *
 * **The sort is a catalog row, not a string convention** (A4′ phase 0). Until then a descriptor opted
 * in by declaring an argument literally named `app` — `const val APP_ARG`, a sort implemented by
 * comparing strings, which keyed on the name and never on `required` (R14-37: an OPTIONAL `app` the
 * vocabulary did not supply answered `NoPlan` for every goal, with the suite green). Now a tool opts in
 * only through an app-sorted row in [CatalogArgumentSorts]: the row's argument name is the argument to
 * resolve, and its [com.sidr.launcher.domain.sort.SortedArg.labelArg], when the row names one **and**
 * the descriptor declares it, receives the raw text the user typed, so a surface can show both what was
 * said and what it resolved to. Rows of the other sorts are read before this one, by `valuesOf` (see
 * above); the app's search stays its own — windows, which the registry's app resolver does not run.
 * Three consequences of the app row, stated rather than discovered later:
 *  - a tool with **no app row** is passed through untouched — an argument named `app` earns no
 *    resolution by its spelling, and a missing row resolves nothing rather than guessing;
 *  - a declared argument that is **optional** and that the vocabulary did not supply stays absent:
 *    nothing to resolve, nothing to decline (a **required** one never arrives blank — the
 *    required-argument check has already declined);
 *  - a descriptor must declare its label argument as **not required**, because the vocabulary never
 *    supplies it (this planner does) while the required-argument check runs *before* resolution.
 *
 * What holds the rows to the descriptors is a test, not the type system: `ToolArgumentSortGuardTest`
 * checks both directions over the production authored sources — every tool declaring an argument named
 * `app` has a row (the bridge from the old convention, which is why that test still reads the name), and
 * no row names an argument its tool does not declare. `F6` is closed by ADR in phase 3b (spec §7.8):
 * `ArgType` stays `STRING` permanently, and what an argument MEANS is its sort, declared in the catalog
 * beside the descriptor — so this stays a catalog held by a test, by decision rather than by debt.
 *
 * **Selection now also covers third-party names.** [ToolSelector] (Task 10) chooses between our
 * authored, localized vocabulary and a dynamic `app_shortcut` name, declining rather than guessing when
 * neither or both claim the text. This planner deliberately learns nothing about that distinction: a
 * [ToolMatch] arrives the same shape whichever source produced it, and the one-step plan built from it
 * cannot say which kind of tool it names.
 */
class ToolMatchPlanner @Inject constructor(
    private val selector: ToolSelector,
    private val appTargets: AppTargetResolver,
    private val sorts: CatalogArgumentSorts,
    private val resolvers: Map<ArgSort, @JvmSuppressWildcards SortResolver>,
) : Planner {

    override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult {
        // Exhaustive with no `else`: a third GoalShape must make a deliberate decision here.
        val text = when (val shape = request.goal.shape) {
            is GoalShape.Free -> shape.text
            is GoalShape.AppNotInstalled -> return PlanningResult.NoPlan
        }

        val match = selector.select(text) ?: return PlanningResult.NoPlan
        val descriptor = registry.find(match.id) ?: return PlanningResult.NoPlan

        // Every required argument must have a non-blank value. Nothing is defaulted or substituted:
        // an agent that silently runs a half-filled tool is worse than one that declines. The
        // vocabulary already refuses a trigger with nothing after it, so what this catches is the
        // vocabulary and the tool disagreeing about the argument's NAME — nothing pins those together.
        val required = descriptor.argSchema.filter { it.required }.map { it.name }
        if (required.any { match.args[it].isNullOrBlank() }) return PlanningResult.NoPlan

        // Every VALUE-sorted argument (a duration, a clock time, an address) is read HERE, through the
        // sort registry, before any session and any card (A4′ phase 3b, Task 6); one that does not read
        // is left to the model, `NoPlan` (E6-R1) — see the class KDoc.
        val args = when (val values = valuesOf(request, descriptor, match.args)) {
            is Values.Read -> values.args
            is Values.NotRead -> return values.answer
        }

        // An app name becomes a package HERE — above the consent checkpoint, see the class KDoc.
        //
        // **What decides is the tool's DECLARED sort, not the argument's spelling** (A4' phase 0,
        // spec §3 0.4). `const val APP_ARG = "app"` was a definition of a sort implemented by
        // comparing strings, and R14-37 is the failure mode that produced: it keyed on the NAME and
        // never on `required`, so a descriptor declaring an OPTIONAL `app` returned `NoPlan` for
        // every goal, forever, with a green suite. Both halves are fixed here — the sort is
        // declared, and an absent optional value is a value that was not needed.
        val binding = sorts.appArgOf(descriptor.id)
        val appArg = binding?.first?.let { name -> descriptor.argSchema.firstOrNull { it.name == name } }
        if (binding == null || appArg == null) return planned(descriptor, args)

        val (argName, sorted) = binding
        val raw = args[argName].orEmpty()
        // Declared, optional, and the vocabulary supplied nothing. There is nothing to resolve and
        // nothing to decline: an absent optional argument is absent.
        if (raw.isBlank() && !appArg.required) return planned(descriptor, args - argName)

        // B-15: a second goal in the target's words goes to the model, which plans both — never into a
        // window, which would offer the first app and drop the rest of the command (the class KDoc).
        if (SecondGoal.carriedBy(raw)) return PlanningResult.NoPlan

        val labelArg = sorted.labelArg?.takeIf { label -> descriptor.argSchema.any { it.name == label } }
        val rest = args - argName

        /** Answered by step 2b on the device; [UnresolvedTarget.bind] finishes [UnresolvedTarget.pending]. */
        fun unresolved(candidates: List<InstalledApp>, confirm: Boolean, unreadable: Boolean = false): PlanningResult {
            val target = UnresolvedTarget(
                goal = request.goal,
                pending = stepOf(descriptor, rest),
                arg = argName,
                labelArg = labelArg,
                sort = sorted.sort,
                words = raw,
                candidates = candidates,
                confirm = confirm,
                unreadable = unreadable,
            )
            return PlanningResult.Unresolved(target)
        }

        // The search: the whole words first, then windows of them (`AppTargetResolver.windows`).
        return when (val found = appTargets.windows(raw)) {
            // Only the whole words naming one app exactly bind; everything else is the user's.
            is AppCandidates.One ->
                planned(descriptor, rest + (argName to found.app.packageName) + listOfNotNull(labelArg?.let { it to raw }))
            is AppCandidates.Several -> unresolved(found.apps, confirm = false)
            is AppCandidates.Confirm -> unresolved(found.apps, confirm = true)
            AppCandidates.None -> unresolved(emptyList(), confirm = false)
            // E5-P1: the list could not be read — neither "not found" nor a reason to ask the model.
            AppCandidates.Unavailable -> unresolved(emptyList(), confirm = false, unreadable = true)
        }
    }

    private fun planned(descriptor: ToolDescriptor, args: Map<String, String>): PlanningResult =
        PlanningResult.Planned(ExecutionPlan(listOf(stepOf(descriptor, args))))

    /**
     * Each argument [descriptor] declares with a VALUE sort, read through that sort's registered
     * [SortResolver] (A4′ phase 3b, Task 6; fork 3b-C, B-5). The app sort is skipped: its search is the
     * windows below, a 2b-only search the registry's app resolver does not run. An argument the
     * descriptor does not declare is skipped too — [stepOf] drops it — and so is one the vocabulary left
     * absent or blank: nothing to read (a required one never arrives blank, the check above declined).
     *
     * [SortResolution.Resolved] replaces the words with the sort's value — the words themselves for a
     * duration or a clock time (they validate, they do not rewrite), the normalised address for a URL.
     * Unreadable words are [PlanningResult.NoPlan] — the command goes on down the route as one no tool
     * claimed, and the model reads the words when it may be asked (controller ruling E6-R1; the class
     * KDoc); so is [SortResolution.NotFound], which no value sort produces today. A sort with no resolver
     * registered, the device unable to ask ([SortResolution.Unavailable]), or a [SortResolution.Choice] —
     * which [UnresolvedTarget.candidates] (`InstalledApp`) cannot carry, and no value sort produces — fail
     * closed as an `unreadable` [UnresolvedTarget] (E6-F2): a generic failure on the device, never a
     * guess, never the raw words passed on, and not a reason to send. A value sort's `labelArg` is neither
     * read nor written: no value row declares one, which
     * `ToolArgumentSortGuardTest > duration, clock time and url arguments are declared` holds by equality.
     */
    private suspend fun valuesOf(request: PlanningRequest, descriptor: ToolDescriptor, args: Map<String, String>): Values {
        var read = args
        for ((name, sorted) in sorts.all()[descriptor.id].orEmpty()) {
            if (sorted.sort == ArgSorts.APP) continue
            if (descriptor.argSchema.none { it.name == name }) continue
            val words = read[name]
            if (words.isNullOrBlank()) continue
            // E6-F2: the device could not consult the sort — answered on the device, never sent.
            fun failClosed() = Values.NotRead(
                PlanningResult.Unresolved(
                    UnresolvedTarget(
                        goal = request.goal,
                        pending = stepOf(descriptor, read - name),
                        arg = name,
                        labelArg = null,
                        sort = sorted.sort,
                        words = words,
                        candidates = emptyList(),
                        confirm = false,
                        unreadable = true,
                    ),
                ),
            )
            when (val resolution = resolvers[sorted.sort]?.resolve(words) ?: SortResolution.Unavailable) {
                is SortResolution.Resolved -> read = read + (name to resolution.value)
                // E6-R1: words that do not read as the sort are the model's to read, like any unclaimed text.
                SortResolution.Unreadable, SortResolution.NotFound -> return Values.NotRead(PlanningResult.NoPlan)
                SortResolution.Unavailable, is SortResolution.Choice -> return failClosed()
            }
        }
        return Values.Read(read)
    }

    /** [valuesOf]'s answer: every value read, or the planner's answer for the first one that was not. */
    private sealed interface Values {
        data class Read(val args: Map<String, String>) : Values
        data class NotRead(val answer: PlanningResult) : Values
    }

    /**
     * The one step this planner ever builds. An argument the schema does not declare is dropped rather
     * than passed: `InvocationValidator` would reject the whole invocation as UNDECLARED_ARG, and a plan
     * that cannot validate is worse than no plan.
     */
    private fun stepOf(descriptor: ToolDescriptor, args: Map<String, String>): PlanStep =
        PlanStep(
            index = 0,
            invocation = ToolInvocation(
                descriptor.id,
                args
                    .filterKeys { key -> descriptor.argSchema.any { it.name == key } }
                    .mapValues { (_, value) -> ArgSource.Literal(value) },
            ),
            risk = descriptor.risk,
            precondition = StepPrecondition.None,
            rationale = StepRationale.GOAL_DIRECT,
        )
}
