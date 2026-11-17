package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.sort.ArgSort
import com.sidr.launcher.domain.sort.ArgSorts
import com.sidr.launcher.domain.sort.ClockTimeSort
import com.sidr.launcher.domain.sort.DurationSort
import com.sidr.launcher.domain.sort.SortResolver
import com.sidr.launcher.domain.sort.UrlSort

/**
 * The sort registry this module's tests hand [ToolMatchPlanner] and [DeviceMentionBinder] (A4′ phase 3b,
 * Task 6) — the same four rows `SortProvidesModule.provideSortResolvers` (`:app`) registers in production,
 * which this module cannot call. Shared, the [appTargetsOf] way, so the test files that build either
 * class cannot drift apart on what "the registry" is.
 *
 * **What holds this copy to production, said rather than implied:** `SortRegistryGuardTest` (`:app`) holds
 * the production side — every declared sort HAS a resolver there, and (since phase 3b's close, final review)
 * each of the three pure value sorts is registered under its own sort, by identity. Nothing holds THIS copy
 * to it but this comment: a wrong row here would mislead this module's tests only, never production.
 */
internal fun sortResolversOf(appTargets: AppTargetResolver = appTargetsOf()): Map<ArgSort, SortResolver> = mapOf(
    ArgSorts.APP to AppSortResolver(appTargets),
    ArgSorts.DURATION to DurationSort.resolver,
    ArgSorts.CLOCK_TIME to ClockTimeSort.resolver,
    ArgSorts.URL to UrlSort.resolver,
)
