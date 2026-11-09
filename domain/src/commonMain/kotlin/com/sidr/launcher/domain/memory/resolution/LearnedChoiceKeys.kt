package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.text.TextFolding

/**
 * One key for a learned launch choice on every path (A4′ phase 3b, plan §0.2 B-4): FastPath, step 2b and
 * the device binding of the model's mention all key a learned choice for `launch_app` here, on the APP
 * NAME the path extracted — not on the whole command. The slot is [LaunchSlotExtractor]'s, of the
 * normalized name (its documented input), folded by the device's [TextFolding], so «Vemblic'i» and a
 * vowel-marked name key the same word as their folded forms.
 *
 * Launch only: a choice learned for OPENING an app is never a key for removing one (B-3). Rows learned
 * before phase 3b under a `ru`/`tr` whole phrase stop matching; the user is asked again — safe, no
 * migration.
 */
object LearnedChoiceKeys {
    fun launch(name: String, folding: TextFolding): CapabilityKey =
        CapabilityKey(ActionIds.LAUNCH_APP, folding.fold(LaunchSlotExtractor.slotOf(CommandNormalizer.normalize(name))))
}
