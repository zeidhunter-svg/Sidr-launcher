package com.sidr.launcher.consumer.jvm.bridge

import com.sidr.launcher.consumer.jvm.text.JdkTextFolding
import com.sidr.launcher.domain.sort.SortCandidate
import com.sidr.launcher.domain.sort.SortResolution
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Paths

/**
 * The «file» sort (A4′ phase 3b, Task 13; `ArgSorts.FILE`): the user's words for a file → one sandbox file,
 * or the user's choice, never a guess. Fixture names are invented (R14-43); assertions compare by equality.
 */
class SandboxFileSortTest {

    @get:Rule val temp = TemporaryFolder()

    private fun sort() = SandboxFileSort(temp.root.toPath(), JdkTextFolding)

    @Test
    fun `a file name resolves to its path`() = runTest {
        val orvelle = temp.newFile("orvelle.txt").toPath().toRealPath()
        temp.newFile("pallimer.md")

        assertEquals(SortResolution.Resolved(orvelle.toString()), sort().resolve("orvelle.txt"))
    }

    /** `find_file` takes the first match in walk order; the sort refuses to: two are the user's to pick. */
    @Test
    fun `two files sharing a name are a choice`() = runTest {
        val first = File(temp.newFolder("kestun"), "orvelle.txt").apply { createNewFile() }.toPath().toRealPath()
        val second = File(temp.newFolder("lindmoss"), "orvelle.txt").apply { createNewFile() }.toPath().toRealPath()

        assertEquals(
            SortResolution.Choice(
                candidates = listOf(
                    SortCandidate(value = first.toString(), label = Paths.get("kestun", "orvelle.txt").toString()),
                    SortCandidate(value = second.toString(), label = Paths.get("lindmoss", "orvelle.txt").toString()),
                ),
                confirm = false,
            ),
            sort().resolve("orvelle.txt"),
        )
    }
}
