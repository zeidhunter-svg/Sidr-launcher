package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.model.InstalledApp

/**
 * Live universal-input results (AIL-3 / DF-1 "search overtakes"). Derived from the typed/spoken buffer:
 * when [active], the home body yields to app matches + route chips. Empty/inactive → the home body
 * (favorites / suggestions / all apps) renders unchanged.
 */
data class HomeInputResults(
    val active: Boolean = false,
    val appMatches: List<InstalledApp> = emptyList(),
    val chips: List<RouteChipKind> = emptyList(),
)

/** The explicit route lanes offered as chips. WEB/ASK for any non-blank buffer; SITE only for a safe URL. */
enum class RouteChipKind { WEB, ASK, SITE }

/** One line of the hidden developer Command console (AIL-3 / DF-1): a submitted command + its outcome. */
data class ConsoleLine(val command: String, val result: String)
