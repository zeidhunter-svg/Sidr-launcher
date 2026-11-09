package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.sort.SortCandidate
import com.sidr.launcher.domain.sort.SortResolution
import com.sidr.launcher.domain.sort.SortResolver
import javax.inject.Inject

/**
 * The app sort's resolver on Android (A4′ phase 3b, fork 3b-C): a thin adapter over
 * [AppTargetResolver.candidates], which answers — it never guesses and consults aliases first. Each
 * answer keeps its own meaning: one app binds its package, several are the USER's choice (never a
 * tie-break), a word form is a choice to CONFIRM (`confirm = true`, phase 3b fork 3b-G — never a bind),
 * none is «нет такого приложения», and a list that could not be read is neither.
 *
 * The `when` is exhaustive without an `else` on purpose: a new [AppCandidates] value must make a
 * deliberate decision here rather than fall into a default — as [AppCandidates.Confirm] did.
 * `AppSortResolverTest` pins all five rows by equality.
 *
 * Registered for `ArgSorts.APP` by `SortProvidesModule` (`:app`); resolved by no caller until phase 3b's
 * wiring tasks, which is why routing is unchanged by this file.
 */
class AppSortResolver @Inject constructor(
    private val targets: AppTargetResolver,
) : SortResolver {

    override suspend fun resolve(words: String): SortResolution = when (val found = targets.candidates(words)) {
        AppCandidates.None -> SortResolution.NotFound
        AppCandidates.Unavailable -> SortResolution.Unavailable
        is AppCandidates.One -> SortResolution.Resolved(found.app.packageName)
        is AppCandidates.Several -> SortResolution.Choice(
            candidates = found.apps.map { SortCandidate(value = it.packageName, label = it.label) },
            confirm = false,
        )
        is AppCandidates.Confirm -> SortResolution.Choice(
            candidates = found.apps.map { SortCandidate(value = it.packageName, label = it.label) },
            confirm = true,
        )
    }
}
