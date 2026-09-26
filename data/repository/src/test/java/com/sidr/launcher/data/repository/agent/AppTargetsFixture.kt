package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.domain.model.InstalledApp

/**
 * Shared by the three test files that build a [ToolMatchPlanner] (Task 6b, A1″ Phase 3a).
 *
 * [AppTargetResolver] is a concrete class over two ports rather than an interface, so a double is
 * built by faking its **inputs** rather than by subclassing it. That is the stronger fixture anyway:
 * every test that uses this runs the production resolution path — alias first, then a *unique* label
 * match, decline on everything else — instead of a stub that agrees with whatever the test wanted.
 * `appTargetsOf()` with no arguments is therefore "nothing is installed", i.e. a resolver that
 * declines every query, which is exactly what a test about a tool with no `app` argument needs.
 *
 * Same shape and same reason as [namesOf]: one `internal` function in this source set, so the two
 * end-to-end tests and `ToolMatchPlannerTest` cannot drift apart on what "this name resolves" means.
 */
internal fun appTargetsOf(vararg labelToPackage: Pair<String, String>): AppTargetResolver =
    AppTargetResolver(
        apps = FakeInstalledAppsRepository().apply {
            appsToReturn = labelToPackage.map { (label, pkg) ->
                InstalledApp(packageName = pkg, label = label)
            }
        },
        aliases = FakeAliasStore(),
    )
