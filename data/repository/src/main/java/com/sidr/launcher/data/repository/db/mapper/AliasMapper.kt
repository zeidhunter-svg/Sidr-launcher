package com.sidr.launcher.data.repository.db.mapper

import com.sidr.launcher.data.repository.db.entity.AliasEntity
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasTarget

/** Entity <-> domain mapping for `aliases` (S2-2). `target_type` is a closed vocabulary (v1: "app"). */
internal object AliasMapper {
    private const val TARGET_TYPE_APP = "app"

    fun toEntity(alias: Alias): AliasEntity {
        val (type, value) = when (val target = alias.target) {
            is AliasTarget.App -> TARGET_TYPE_APP to target.packageName
        }
        return AliasEntity(
            phrase = alias.phrase,
            targetType = type,
            targetPackage = value,
            createdAt = alias.createdAtEpochMs,
        )
    }

    /** Returns null when `target_type` is not a recognized discriminator. */
    fun toDomain(entity: AliasEntity): Alias? {
        val target = when (entity.targetType) {
            TARGET_TYPE_APP -> AliasTarget.App(entity.targetPackage)
            else -> return null
        }
        return Alias(
            phrase = entity.phrase,
            target = target,
            createdAtEpochMs = entity.createdAt,
        )
    }
}
