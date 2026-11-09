package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.text.TextFolding

/**
 * The key for a learned launch choice, on the APP NAME a path extracted rather than on the whole command
 * (A4′ phase 3b, plan §0.2 B-4). The slot is [LaunchSlotExtractor]'s, taken from the normalized name (its
 * documented input) and folded by the device's [TextFolding], so «Vemblic'i» and a vowel-marked name key
 * the same word as their folded forms.
 *
 * **Who keys here, as of Task 4:**
 *  - Today the only caller is the device binding of the model's mention (`DeviceMentionBinder`).
 *  - FastPath still keys on the whole command (`ResolveCommandWithPreferenceUseCase`). It moves to this
 *    key in Task 7. Until then, a choice learned on one path is not seen by the other.
 *  - Step 2b keys no launch choice at all.
 *
 * Launch only: a choice learned for OPENING an app is never a key for removing one (B-3). Rows FastPath
 * learned under a `ru`/`tr` whole phrase keep matching until FastPath moves to this key. From then on they
 * stop matching, and the user is asked again — safe, no migration.
 */
object LearnedChoiceKeys {
    fun launch(name: String, folding: TextFolding): CapabilityKey =
        CapabilityKey(ActionIds.LAUNCH_APP, folding.fold(LaunchSlotExtractor.slotOf(CommandNormalizer.normalize(name))))
}
