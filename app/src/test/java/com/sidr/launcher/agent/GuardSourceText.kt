package com.sidr.launcher.agent

/**
 * Shared source-text handling for the two `:app` guards that scan Kotlin sources as **text**
 * ([AgentVocabularyGuardTest], [ToolExecutorCallSiteGuardTest]).
 *
 * It lives here, package-level in `app/src/test`, rather than in `:core:testing`: both guards already
 * sit in `com.sidr.launcher.agent`, so sharing needs no module and no build change, and a text helper
 * that exists only to serve two guards has no business on the production test-fixture classpath.
 *
 * Before Task 13's fix round the two guards disagreed: the vocabulary guard stripped comments, the
 * call-site guard did a raw `contains` over `readLines()`. Nothing tripped it *today*, but a future
 * KDoc that spelled `toolExecutor.invoke(` in prose would have turned a guard red on correct code —
 * and the declaration scan added for F1 would have been red on the KDoc of `ToolExecutor` itself.
 */

/**
 * Removes `//` tails and `/* … */` blocks (KDoc included, and Kotlin block comments nest) while
 * preserving line breaks, so a hit still reports the line it was on.
 *
 * **Named limitation:** it does not model string literals, so a comment opener inside one is
 * treated as a comment opener. That direction is one-way — it can only *hide* text from the scan,
 * never invent a hit — so a guard built on it can in principle be evaded by code no reviewer would
 * write, and can never fail on correct code. A guard that is honest about false negatives beats one
 * that needs a Kotlin parser nobody will maintain.
 */
internal fun stripComments(source: String): String {
    val out = StringBuilder()
    var i = 0
    var depth = 0
    while (i < source.length) {
        val two = if (i + 1 < source.length) source.substring(i, i + 2) else ""
        when {
            depth > 0 && two == "*/" -> { depth--; i += 2 }
            depth > 0 && two == "/*" -> { depth++; i += 2 }
            depth > 0 -> { if (source[i] == '\n') out.append('\n'); i++ }
            two == "/*" -> { depth++; i += 2 }
            two == "//" -> { while (i < source.length && source[i] != '\n') i++ }
            else -> { out.append(source[i]); i++ }
        }
    }
    return out.toString()
}
