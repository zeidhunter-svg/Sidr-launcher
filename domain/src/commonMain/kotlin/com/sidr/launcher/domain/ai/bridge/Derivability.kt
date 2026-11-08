package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.text.PlainTextFolding
import com.sidr.launcher.domain.text.TextFolding

/**
 * "The model wrote the user's words" made checkable (spec §7.12.1, D1 mechanism 1): a value is
 * derivable when its words appear, whole and in order, in the command — compared after [TextFolding]
 * (case, the two dropped mark ranges, Arabic letter variants, an apostrophe inside a word), punctuation
 * ignored. **Exact after folding** (A4′ phase 3b, B-17): «телегу» is not «телега», «Vemblic» is not the
 * «Vemblic'i» the user typed, and a reference to an earlier turn is not in the command at all. Each ends
 * as an honest refusal, never as a guess; relating a typed form to a label is the binder's job, not this one's.
 *
 * The default [PlainTextFolding] segments nothing; the bridge passes the platform's folding.
 */
object Derivability {

    fun isDerivable(mention: String, command: String, folding: TextFolding = PlainTextFolding): Boolean {
        val wanted = folding.words(mention)
        if (wanted.isEmpty()) return false
        val typed = folding.words(command)
        return typed.windowed(wanted.size).any { it == wanted }
    }

    fun originOf(value: String, command: String, folding: TextFolding = PlainTextFolding): ValueOrigin =
        if (isDerivable(value, command, folding)) ValueOrigin.SaidBy(PrincipalId.USER) else ValueOrigin.ModelGenerated
}
