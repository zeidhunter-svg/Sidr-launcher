package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.text.TextFolding

/**
 * The one key for a learned launch choice on every path that learns one (A4′ phase 3b, plan §0.2 B-4).
 *
 * **What the key is:** `launch_app` and the APP NAME the path extracted — never the command it came in —
 * normalised ([CommandNormalizer.normalize]), stripped of English launch fillers by [LaunchSlotExtractor]
 * (a leading «open»/«my»/…, a trailing «app»; its documented input is normalised text, hence the order), and
 * read as the device's [TextFolding] WORDS joined by one space (owner decision 2026-11-17, phase 3b close
 * M5): the matchers compare words, so the key does too — «Tessar.» and «tessar» are one choice, and
 * «Vemblic'i» and a vowel-marked name key the same word as their folded forms. A name with no word at all
 * (symbols only) keys its folded form, so two such names never share an empty key. For a name the fold
 * and the word cut leave alone (no marks, apostrophes, symbols or Arabic letter variants; space-separated
 * words) the key is byte for byte what `slotOf(normalize(name))` gave before folding existed (E4-F3;
 * `LearnedChoiceKeysTest`). Rows keyed before the decision under a symbol («tessar.») are orphans —
 * visible and deletable in Settings.
 *
 * **Who keys here, as of Task 7:**
 *  - FastPath (`ResolveCommandWithPreferenceUseCase`), on its outcome's `query` — the matcher's
 *    `displayNameQuery`: «открой tessar» keys `tessar`;
 *  - the device binding of the model's mention (`DeviceMentionBinder`): the mention «tessar» keys `tessar`.
 *  So a choice learned on one path is the one the other reads (`DeviceMentionBinderTest > a choice learned
 *  by FastPath for «открой tessar» is the one the bridge reads for «tessar»`). `forget_learned_choice`
 *  (`MemoryToolWorker`) deletes by this key of the phrase it is given, so it removes the row those paths
 *  wrote («vemblic'i» → `vemblici`; `MemoryToolWorkerTest > forget_learned_choice deletes the very key
 *  FastPath wrote for a confirmed word form`). Step 2b keys no launch choice at all.
 *
 * Launch only: a choice learned for OPENING an app is never a key for removing one (B-3). Rows FastPath
 * learned before Task 7 under a `ru`/`tr` whole phrase («открой tessar») no longer match: the user is asked
 * again — safe, no migration.
 */
object LearnedChoiceKeys {
    fun launch(name: String, folding: TextFolding): CapabilityKey {
        val slot = LaunchSlotExtractor.slotOf(CommandNormalizer.normalize(name))
        val words = folding.words(slot)
        // No word at all (a symbols-only name): its folded form, so two such names never share one empty key.
        return CapabilityKey(ActionIds.LAUNCH_APP, if (words.isEmpty()) folding.fold(slot) else words.joinToString(" "))
    }
}
