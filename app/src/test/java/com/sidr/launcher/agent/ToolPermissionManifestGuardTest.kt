package com.sidr.launcher.agent

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * **A registered tool whose intent needs a permission the manifest does not declare is a capability
 * that exists and can never run.** This guard is the answer to the A1′ device-acceptance defect
 * (2026-09-05): `set_timer` shipped registered, reachable, `SAFE` and documented seven times over as
 * needing "zero new permissions", and on the phone every invocation died at
 *
 * ```
 * W ActivityTaskManager: Permission Denial: starting Intent { act=android.intent.action.SET_TIMER … }
 *   from ProcessRecord{… com.sidr.launcher …} requires com.android.alarm.permission.SET_ALARM
 * ```
 *
 * because `AlarmClock.ACTION_SET_TIMER` requires `com.android.alarm.permission.SET_ALARM` and
 * `app/src/main/AndroidManifest.xml` declared it nowhere.
 *
 * **Why no existing test could catch it, and why this one is textual.** `Tier0IntentToolWorkerTest`
 * injects a fake `IntentLauncher`, so the real `startActivity` — and therefore the real permission
 * check — is never reached; that is the right design for testing the duration parse, and it is
 * structurally blind to this. A manifest omission has no unit-test signature at all. The two obvious
 * stronger designs are both unavailable here:
 *  - **Robolectric against the real merged manifest** — `:app` has no Robolectric and cannot get it:
 *    4.16.1 caps supported SDK at 36 and this module's `targetSdk` is held at 36 for exactly that
 *    reason (`app/build.gradle.kts`), so a Robolectric test here is a toolchain change, not a test.
 *  - **Deriving the requirement from the SDK** — checked, and it is not there. The platform's external
 *    annotations (`platforms/android-37.0/data/annotations.zip`) carry **no** `@RequiresPermission` for
 *    `AlarmClock.ACTION_SET_TIMER`; the string `SET_ALARM` does not occur anywhere in that database.
 *    The action → permission rule is enforced inside `ActivityTaskManager` at runtime and exists in no
 *    artifact on any build or test classpath.
 *
 * **So the mapping below is hand-written, and that is this guard's weak point — stated, not implied
 * absent.** [REQUIRED_PERMISSIONS] is a claim about the Android platform that nothing here verifies.
 * If someone writes `emptyList()` for an action that in fact needs a permission, every test in this
 * class passes and the capability is dead on the phone again. What the guard *does* hold mechanically
 * is the three things that failed this time:
 *  1. **totality** — every intent action a scanned worker issues has an entry, so a new Tier-0 intent
 *     (A1″'s entire content) cannot ship without someone answering the permission question;
 *  2. **satisfaction** — every permission any entry names is declared in the app manifest;
 *  3. **coverage** — the set of intent-issuing tool workers is pinned, so a *new* worker cannot
 *     quietly fall outside the scan.
 *
 * Only (1) and (3) are new information; (2) is the assertion that would have gone red on 2026-09-03.
 * The residual hole is one hand-written column, reviewable in the same diff as the tool it describes —
 * which is strictly better than the previous state, where the claim lived in seven prose comments and
 * was reviewable nowhere.
 *
 * **Those claims were then measured, not assumed** (independent mutation round, 2026-09-05). Removing
 * the manifest declaration goes RED naming the missing permission; an unmapped new intent action goes
 * RED; a legitimate *complete* addition stays GREEN, so the guard does not over-pin; and on an empty
 * scan 3 of these 4 tests still fire, so it is not vacuous. The hand-written column is exactly as weak
 * as stated above and no weaker — rewriting [requiredPermissions] to claim `AlarmClock.ACTION_SET_TIMER`
 * needs nothing leaves all four tests green and the app in its pre-fix broken state.
 *
 * **The same round measured two further limits, and neither is a live defect today** — both shipped
 * workers construct their intents inline and the manifest is correct. They are limits on what this
 * guard can *see*, and they are written down because the person at risk is the next author, not this
 * one:
 *
 *  1. **An `Intent(…)` moved one file sideways is invisible to the whole guard.** Every assertion here
 *     starts from [intentIssuingWorkerFiles], which admits a file only when it both declares a
 *     `ToolWorker` **and** contains `Intent(` itself. A worker that delegates intent construction to a
 *     plain helper object therefore issues intents this guard never reads, and the coverage pin does
 *     not help: the helper is not a `ToolWorker`, so it is not in the scanned set either. Measured with
 *     a `call_number` tool whose worker calls a helper that builds `Intent.ACTION_CALL`, with
 *     `android.permission.CALL_PHONE` absent from the manifest — **green 4/4**: the same defect shape
 *     this guard exists for, one refactor away. It matters concretely because A1″'s entire content is
 *     more Tier-0 intents, and a shared intent-building helper is a natural thing to write when adding
 *     a dozen of them.
 *  2. **The guard keys on workers, not on registered tools.** Totality triggers on `Intent(` in a
 *     worker file, not on the `ToolDescriptor`s a `ToolRegistry` advertises, so a tool registered in a
 *     source with no corresponding worker branch is invisible to every assertion here. The question
 *     this class actually answers is *"does every intent a scanned worker issues have a declared
 *     permission"*, not *"can every registered tool actually run"* — which is the question the
 *     2026-09-05 defect was an instance of.
 *
 * Closing either means keying the scan on what the registry advertises rather than on worker files —
 * a different guard, not a wider regex. Recorded, not built: `CLAUDE.md`'s Known debt and the track
 * plan's `§HANDOFF` (its "before you write adapter #3" section) both point here rather than restating
 * this.
 */
class ToolPermissionManifestGuardTest {

    private val repoRoot = File("..")

    /**
     * Intent action → the permissions the platform requires of the caller, keyed by the constant
     * **as written in the worker source**, because that is the token the scan can actually see.
     *
     * `com.android.alarm.permission.SET_ALARM` is `protectionLevel:normal` (verified 2026-09-05 on the
     * SM-A325F itself: `adb shell pm list permissions -f -g` reports `protectionLevel:normal`,
     * `package:android`), so it is granted at install and needs no runtime request — which is why
     * declaring it is the whole of the fix and no permission-education flow is involved.
     *
     * An `emptyList()` value is a deliberate "this action needs none", not a missing row: totality is
     * asserted, so the two are distinguishable.
     */
    private val requiredPermissions: Map<String, List<String>> = mapOf(
        "AlarmClock.ACTION_SET_TIMER" to listOf("com.android.alarm.permission.SET_ALARM"),
        "Settings.ACTION_SETTINGS" to emptyList(),
    )

    /**
     * The worker files this guard reads. Pinned rather than derived-and-trusted so that adding an
     * intent-issuing worker is a red test, not a silent widening of the unguarded surface — the same
     * shape as `ToolWorkerCallSiteGuardTest`'s holder list, and for the same reason.
     */
    private val expectedIntentIssuingWorkers = listOf("Tier0IntentToolWorker.kt")

    /** Same five roots the two call-site guards walk, and inheriting the same named fail-open limits. */
    private val productionRoots: List<File> =
        kmpProductionRoots(File(repoRoot, "domain/src")) +
            listOf(
                "data/repository/src/main/java",
                "feature/launcher/src/main/java",
                "app/src/main/java",
                "consumer/jvm/src/main/kotlin",
            ).map { File(repoRoot, it) }

    private val declaresToolWorker = Regex(""":\s*ToolWorker\b""")

    /** `Intent(SomeClass.ACTION_NAME)` — the only intent shape any scanned worker uses today. */
    private val intentWithAction = Regex("""\bIntent\(\s*([A-Za-z_][A-Za-z0-9_.]*)\s*\)""")

    private val anyIntentConstruction = Regex("""\bIntent\(""")

    private fun intentIssuingWorkerFiles(): List<File> =
        productionRoots
            .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" } }
            .filter { file ->
                val text = stripComments(file.readText())
                declaresToolWorker.containsMatchIn(text) && anyIntentConstruction.containsMatchIn(text)
            }
            .sortedBy { it.name }

    private val manifestFile = File(repoRoot, "app/src/main/AndroidManifest.xml")

    /**
     * `<uses-permission android:name="…"/>` entries, minus any the manifest merger is told to strip.
     * A `tools:node="remove"` entry declares the opposite of a grant, and reading it as one would make
     * this guard green on a manifest that removes the permission it is checking for.
     */
    private fun declaredPermissions(): Set<String> {
        assertEquals("missing app manifest: ${manifestFile.canonicalPath}", true, manifestFile.isFile)
        return Regex("""<uses-permission\b[^>]*?/?>""")
            .findAll(manifestFile.readText())
            .filterNot { it.value.contains("""tools:node="remove"""") }
            .mapNotNull { Regex("""android:name\s*=\s*"([^"]+)"""").find(it.value)?.groupValues?.get(1) }
            .toSet()
    }

    /**
     * Non-vacuity. Every other test here is an assertion over a set produced by a regex against a file
     * found by a relative path; if the file moves or the attribute spelling changes, those sets go
     * empty and `forEach {}` over nothing passes loudly silent. This names a permission that has been
     * in the manifest since Block A and must always be there.
     */
    @Test
    fun `the manifest scan finds the manifest and reads real permissions from it`() {
        val declared = declaredPermissions()

        assertEquals(
            "the uses-permission scan came back without a permission this app has always declared — " +
                "the manifest moved or the attribute spelling changed, and every other assertion in " +
                "this class is now passing over an empty set. Found: ${declared.sorted()}",
            true,
            "android.permission.INTERNET" in declared,
        )
    }

    /**
     * Coverage pin. `Tier0IntentToolWorker` is the only `ToolWorker` that builds an `Intent` itself
     * (`SystemIntentToolWorker` delegates to the unchanged `ExecuteActionUseCase` chain,
     * `SandboxToolWorker` is the JVM consumer's and touches no Android, `ToolFederation` is the adapter
     * type). A1″ ships more Tier-0 intents; if it does so from a new worker, that worker's actions are
     * outside this scan until this list says otherwise.
     */
    @Test
    fun `the set of intent-issuing tool workers is exactly the known one`() {
        val found = intentIssuingWorkerFiles().map { it.name }

        assertEquals(
            "a ToolWorker that constructs an Intent is a worker whose permission needs this guard " +
                "must read. Found: $found",
            expectedIntentIssuingWorkers,
            found,
        )
    }

    /**
     * Totality, plus the scan's own parse-completeness. The second half matters as much as the first:
     * [intentWithAction] reads exactly one intent shape, so an `Intent()` written any other way —
     * `Intent(context, Foo::class.java)`, an action held in a local `val` — would be skipped in
     * silence. Counting every `Intent(` and requiring the action regex to have accounted for all of
     * them turns "this guard cannot read that" into a red test rather than a blind spot.
     */
    @Test
    fun `every intent action a scanned worker issues has a permission entry`() {
        intentIssuingWorkerFiles().forEach { file ->
            val text = stripComments(file.readText())
            val actions = intentWithAction.findAll(text).map { it.groupValues[1] }.toList()

            assertEquals(
                "${file.name} constructs an Intent in a shape this guard cannot read, so its " +
                    "permission requirement would go unchecked. Readable: $actions",
                anyIntentConstruction.findAll(text).count(),
                actions.size,
            )

            val unmapped = actions.filterNot { it in requiredPermissions }
            assertEquals(
                "${file.name} issues an intent with no entry in requiredPermissions. Decide what the " +
                    "platform requires of the caller and record it — `emptyList()` if genuinely none. " +
                    "Unmapped: $unmapped",
                emptyList<String>(),
                unmapped,
            )
        }
    }

    /**
     * **The defect.** Red on `2fe6fbf`: `set_timer` is registered and reachable, its intent requires
     * `com.android.alarm.permission.SET_ALARM`, and the manifest declared no such permission.
     */
    @Test
    fun `every permission a registered tool's intent needs is declared in the app manifest`() {
        val declared = declaredPermissions()

        val missing = intentIssuingWorkerFiles()
            .flatMap { file ->
                intentWithAction.findAll(stripComments(file.readText()))
                    .map { it.groupValues[1] }
                    .flatMap { action -> (requiredPermissions[action] ?: emptyList()).asSequence() }
                    .map { permission -> "${file.name}: $permission" }
            }
            .filterNot { it.substringAfter(": ") in declared }
            .distinct()
            .sorted()

        assertEquals(
            "a registered tool issues an intent the OS refuses to start, because the permission it " +
                "requires is not declared in app/src/main/AndroidManifest.xml. The tool is reachable, " +
                "the plan runs, and the step fails on every device. Missing: $missing",
            emptyList<String>(),
            missing,
        )
    }
}
