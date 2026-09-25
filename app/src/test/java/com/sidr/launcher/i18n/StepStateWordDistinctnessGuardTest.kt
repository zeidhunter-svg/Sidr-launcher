package com.sidr.launcher.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * **`AgentStepState` has eight values and four markers, so the WORD is what tells six of them
 * apart** (`R-ADL-2`: status is never colour alone). `AgentSessionPresentationTest` cannot hold
 * this — `AgentStepState.word()` is `@Composable` and unreachable from a JVM test — so it is held
 * where the words actually are.
 *
 * Two states sharing a marker AND a word would be invisibly identical in greyscale and to TalkBack.
 * The pairs at risk are real, not theoretical: `OBSERVED`/`SKIPPED`/`PENDING` all render `INFO`,
 * and `HANDED_OFF`/`CURRENT`/`WAITING` all render `ATTENTION`.
 *
 * Working directory is the module dir (`app`), so the repo root is `..` — the idiom
 * `LocaleCompletenessGuardTest` uses.
 */
class StepStateWordDistinctnessGuardTest {

    private val keys = listOf(
        "launcher_agent_step_state_done",
        "launcher_agent_step_state_observed",
        "launcher_agent_step_state_handed_off",
        "launcher_agent_step_state_skipped",
        "launcher_agent_step_state_failed",
        "launcher_agent_step_state_current",
        "launcher_agent_step_state_waiting",
        "launcher_agent_step_state_pending",
    )

    @Test
    fun `every step state reads differently in every locale`() {
        listOf("values", "values-ru", "values-tr").forEach { dir ->
            val xml = File("../feature/launcher/src/main/res/$dir/strings.xml").readText()
            val values = keys.map { key ->
                Regex("""<string name="$key">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
                    .find(xml)?.groupValues?.get(1)
                    ?: error("$dir: '$key' is missing — LocaleCompletenessGuardTest should have caught this first")
            }

            assertEquals(
                "$dir: two step states render the same word. Eight states share four markers, so " +
                    "the word is the only thing telling six of them apart (R-ADL-2). " +
                    "Collisions: " + values.groupBy { it }.filter { it.value.size > 1 }.keys,
                keys.size,
                values.toSet().size,
            )
        }
    }
}
