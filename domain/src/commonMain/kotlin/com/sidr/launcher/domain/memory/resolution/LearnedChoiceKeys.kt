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
 * folded by the device's [TextFolding], so «Vemblic'i» and a vowel-marked name key the same word as their
 * folded forms. For a name the fold leaves alone (no marks, apostrophes or Arabic letter variants) the key
 * is byte for byte what `slotOf(normalize(name))` gave before folding existed (E4-F3).
 *
 * **Who keys here, as of Task 7:**
 *  - FastPath (`ResolveCommandWithPreferenceUseCase`), on its outcome's `query` — the matcher's
 *    `displayNameQuery`: «открой tessar» keys `tessar`;
 *  - the device binding of the model's mention (`DeviceMentionBinder`): the mention «tessar» keys `tessar`.
 *  So a choice learned on one path is the one the other reads (`DeviceMentionBinderTest > a choice learned
 *  by FastPath for «открой tessar» is the one the bridge reads for «tessar»`). Step 2b keys no launch
 *  choice at all.
 *
 * Launch only: a choice learned for OPENING an app is never a key for removing one (B-3). Rows FastPath
 * learned before Task 7 under a `ru`/`tr` whole phrase («открой tessar») no longer match: the user is asked
 * again — safe, no migration.
 */
object LearnedChoiceKeys {
    fun launch(name: String, folding: TextFolding): CapabilityKey =
        CapabilityKey(ActionIds.LAUNCH_APP, folding.fold(LaunchSlotExtractor.slotOf(CommandNormalizer.normalize(name))))
}
