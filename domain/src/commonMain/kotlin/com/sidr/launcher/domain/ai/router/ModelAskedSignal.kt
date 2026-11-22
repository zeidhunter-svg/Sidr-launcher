package com.sidr.launcher.domain.ai.router

/**
 * «The model is being asked» — said by [RouteCommandUseCase] once per command, immediately before its one call to the
 * model (step 7, `modelPlanner.plan`), and on no other path (A4′ acceptance tail, task 6; owner decision 6, controller
 * ruling K9). The launcher shows its wait — «Разбираю команду…», with «Отмена» — on this signal, and only on it: a
 * command answered on the device (FastPath, an alias, a 2b plan, a learned command, or the honest line of local-only /
 * no provider / offline) shows no wait however long it takes, and a command the model is reading shows it at once,
 * with no timer in between.
 *
 * **A port, not a parameter of `route`** (K9; plan review I8): `CommandRouteStep` and `ResolvedCommandStep`, the two
 * decorators' steps the route is called through, are `fun interface`s — their single member takes no default value,
 * so a new argument would touch every one of their SAM lambdas. The route is given the port once, at construction.
 *
 * It carries nothing — no command, no text: it leaves no trace of what was asked, and it is not a channel to anything
 * but the launcher's own wait. Which command it belongs to is the launcher's business: the route raises it from inside
 * the command's own coroutine, and the launcher ties what it sees to the command it has pending
 * (`LauncherCommandSession.submit`). `:app` binds it to the launcher's events (`BridgeProvidesModule`); [NONE] — the
 * default of every construction that predates it — says nothing to anyone.
 */
fun interface ModelAskedSignal {

    /** The route is about to ask the model about the command it is routing. Must not block, suspend or throw. */
    fun modelAsked()

    companion object {
        /** No one is listening: every route built without a signal routes exactly as before. */
        val NONE: ModelAskedSignal = ModelAskedSignal {}
    }
}
