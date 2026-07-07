package com.sidr.launcher.data.repository.db.mapper

import com.sidr.launcher.data.repository.db.entity.ResolutionPreferenceEntity
import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.memory.resolution.CandidateSetFingerprint
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.PreferenceEvidence
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import com.sidr.launcher.domain.memory.resolution.ResolutionPreference
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget

/**
 * Entity <-> domain mapping for `resolution_preferences` (Stage-2 S2-1, Phase B / Task 8).
 *
 * `context_key` / `preferred_target_type` are closed vocabularies (v1: "none" / "app"). [toDomain]
 * returns `null` for any unrecognized discriminator so the caller (repository `observeAll`) can skip
 * a malformed row instead of crashing the whole stream — this is the only validation point; the
 * domain model itself is unaware of the wire encoding.
 */
internal object ResolutionPreferenceMapper {

    private const val CONTEXT_NONE = "none"
    private const val TARGET_TYPE_APP = "app"

    fun toEntity(preference: ResolutionPreference): ResolutionPreferenceEntity {
        val (targetType, targetValue) = when (val target = preference.preferredTarget) {
            is ResolvedTarget.App -> TARGET_TYPE_APP to target.packageName
        }
        return ResolutionPreferenceEntity(
            actionId = preference.capabilityKey.actionId.value,
            query = preference.capabilityKey.query,
            contextKey = toContextKey(preference.context),
            preferredTargetType = targetType,
            preferredTargetValue = targetValue,
            streak = preference.evidence.streak,
            totalChoices = preference.evidence.totalChoices,
            lastChosenAtEpochMs = preference.evidence.lastChosenAtEpochMs,
            learnedInFingerprint = preference.learnedInSetFingerprint.value,
        )
    }

    /** Returns `null` when `contextKey`/`preferredTargetType` is not a recognized discriminator. */
    fun toDomain(entity: ResolutionPreferenceEntity): ResolutionPreference? {
        val context = toContext(entity.contextKey) ?: return null
        val target = toTarget(entity.preferredTargetType, entity.preferredTargetValue) ?: return null
        return ResolutionPreference(
            capabilityKey = CapabilityKey(actionId = ActionId(entity.actionId), query = entity.query),
            context = context,
            preferredTarget = target,
            evidence = PreferenceEvidence(
                streak = entity.streak,
                totalChoices = entity.totalChoices,
                lastChosenAtEpochMs = entity.lastChosenAtEpochMs,
            ),
            learnedInSetFingerprint = CandidateSetFingerprint(entity.learnedInFingerprint),
        )
    }

    private fun toContextKey(context: ResolutionContext): String = when (context) {
        is ResolutionContext.None -> CONTEXT_NONE
    }

    private fun toContext(contextKey: String): ResolutionContext? = when (contextKey) {
        CONTEXT_NONE -> ResolutionContext.None
        else -> null
    }

    private fun toTarget(targetType: String, targetValue: String): ResolvedTarget? = when (targetType) {
        TARGET_TYPE_APP -> ResolvedTarget.App(packageName = targetValue)
        else -> null
    }
}
