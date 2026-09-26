package com.sidr.launcher.domain.intent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A4′ phase 1, spec §5.2. [SecondGoal] answers one question — does this remainder discard a second
 * goal? — and each test below pins one half of that answer. The phrases are invented, not the owner's
 * `B15` corpus: the corpus is the measurement, and a test written from it would measure the author.
 */
class SecondGoalTest {

    @Test fun `a listed verb right after the conjunction opens a second goal`() {
        assertTrue(SecondGoal.carriedBy("билеты в казань и закажи такси"))
        assertTrue(SecondGoal.carriedBy("the quarterly report and send it"))
    }

    /** `tr` is SOV: FastPath only claims a text ending in its own verb, so the clause BEFORE `ve` ends in its verb. */
    @Test fun `a listed verb right before the conjunction closes a first goal`() {
        assertTrue(SecondGoal.carriedBy("haberleri oku ve pilav tarifi"))
    }

    /** The case spec §5.2 exists to keep: a conjunction inside ONE query discards nothing. */
    @Test fun `a conjunction joining two nouns is one query, not two goals`() {
        assertFalse(SecondGoal.carriedBy("рецепт хлеба и соли"))
        assertFalse(SecondGoal.carriedBy("salt and pepper"))
        assertFalse(SecondGoal.carriedBy("tavuk ve pilav"))
    }

    /**
     * The verb must stand NEXT TO a conjunction. Each phrase puts a listed verb after position 0, so a
     * rule that stopped looking for the conjunction would fire on every one of them.
     */
    @Test fun `a listed verb with no conjunction beside it is not a second goal`() {
        assertFalse(SecondGoal.carriedBy("срочно закажи такси"))
        assertFalse(SecondGoal.carriedBy("please send it"))
        assertFalse(SecondGoal.carriedBy("tarifi oku lütfen"))
    }

    /**
     * A listed verb on BOTH sides of the conjunction is one clause with two verbs — a query about one
     * thing ("how to download and install python"), not a second goal. Without this, the commonest
     * how-to search shape would be withdrawn.
     */
    @Test fun `a conjunction between two listed verbs joins one clause, it does not open a second`() {
        assertFalse(SecondGoal.carriedBy("how to download and install python"))
        assertFalse(SecondGoal.carriedBy("скачай и установи майнкрафт"))
        assertFalse(SecondGoal.carriedBy("python indir ve kur"))
    }

    /** `FastPathLocaleGuardTest`'s shape, for a vocabulary that lives in `:domain`. */
    @Test fun `every supported locale has a conjunction and a verb on its side of it`() {
        val missing = listOf("en", "ru", "tr").filter { locale ->
            SecondGoal.CONJUNCTIONS_BY_LOCALE[locale].orEmpty().isEmpty() ||
                (SecondGoal.VERBS_AFTER_CONJUNCTION_BY_LOCALE[locale].orEmpty().isEmpty() &&
                    SecondGoal.VERBS_BEFORE_CONJUNCTION_BY_LOCALE[locale].orEmpty().isEmpty())
        }
        assertTrue("SecondGoal has no conjunction or no verb form for: $missing", missing.isEmpty())
    }
}
