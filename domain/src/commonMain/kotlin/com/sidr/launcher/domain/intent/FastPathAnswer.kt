package com.sidr.launcher.domain.intent

/**
 * What FastPath decided and then **handed to the model** instead of doing (owner decision 13: Б, A4′ acceptance
 * tail plan §0.2): with the model reachable — not local-only, a provider, the network — a confident answer of this
 * kind is not executed by FastPath; `RouteCommandUseCase` asks the model, and if the model cannot answer
 * (`Declined`, `Unavailable` — controller ruling K1) runs exactly what FastPath would have run.
 *
 * A closed sum, but its `when`s live only in `RouteCommandUseCase` (`:domain`); nothing outside `:domain` sees it.
 */
sealed interface FastPathYield {

    /** A confident web search (`LauncherIntent.SearchIntent` past the auto-execute gate), not yet resolved or run. */
    data class Search(val intent: LauncherIntent.SearchIntent) : FastPathYield

    /**
     * «No such app», handed to the model before the store (owner decision 14; task 2 of the tail plan): a confident
     * launch whose whole name resolved to nothing. The model may correct the name (amendment D1); if it cannot take
     * the command, or names an app that is not here, the router starts A0's store plan in the user's words
     * ([message]'s `query` — controller rulings K1, K3).
     */
    data class MissingApp(val message: CommandMessage.NoAppFound) : FastPathYield
}

/**
 * FastPath's answer to one command: the [outcome] it returns, and — only when it was asked to yield and the command
 * was of a kind it yields — what it [yielded] instead of doing. A yielded answer's [outcome] is
 * [CommandOutcome.LowConfidence], so the router treats the command as undecided.
 */
data class FastPathAnswer(val outcome: CommandOutcome, val yielded: FastPathYield? = null)
