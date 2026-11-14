package com.sidr.launcher.consumer.jvm.bridge

import com.sidr.launcher.consumer.jvm.tool.SandboxKeys
import com.sidr.launcher.consumer.jvm.tool.SandboxToolIds
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.sort.ArgSorts
import com.sidr.launcher.domain.sort.ArgumentSorts
import com.sidr.launcher.domain.sort.SortCandidate
import com.sidr.launcher.domain.sort.SortResolution
import com.sidr.launcher.domain.sort.SortResolver
import com.sidr.launcher.domain.sort.SortedArg
import com.sidr.launcher.domain.text.AppNameMatcher
import com.sidr.launcher.domain.text.NameMatch
import com.sidr.launcher.domain.text.TextFolding
import com.sidr.launcher.domain.tool.ToolId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The «file» sort's resolver (A4′ phase 3b, Task 13; `ArgSorts.FILE`): the user's words for a file → the
 * sandbox files whose name answers to them, by the ONE name matcher every consumer uses
 * ([AppNameMatcher.matchWhole], word-form rule B-2) under this platform's [folding]. It never guesses:
 *  - one exact match → [SortResolution.Resolved] with its absolute path;
 *  - several exact matches → [SortResolution.Choice] — the user's, never the first found (where `find_file`
 *    takes the first match in walk order);
 *  - only word FORMS of a name → [SortResolution.Choice] with `confirm = true`, never a bind;
 *  - nothing → [SortResolution.NotFound]; the folder could not be read → [SortResolution.Unavailable].
 *
 * Candidates are in the order of their path relative to the sandbox, which is also their label — so two
 * files of one name in two folders read apart. Each file is carried through the matcher as a name with a
 * value, the shape `AppTargetResolver` already gives an alias («pseudo-app»): its value is the absolute
 * path, its label the file name; no app is involved.
 *
 * It walks the folder as `find_file` does — from its real path, everything under it, the harness's own
 * `.sidr-agent` store included (the store's own KDoc names that exposure: `JvmAgentSessionStore`) — but
 * offers no symbolic link: neither `delete_file` nor `rename_file` acts on one (`NOFOLLOW_LINKS`), so a
 * link resolved here would only become a card that fails. The walk runs on [context] (`Main.kt`: the
 * harness's world context, `Dispatchers.IO`).
 */
class SandboxFileSort(
    private val root: Path,
    private val folding: TextFolding,
    private val context: CoroutineContext = EmptyCoroutineContext,
) : SortResolver {

    override suspend fun resolve(words: String): SortResolution = try {
        withContext(context) { resolveIn(root.toAbsolutePath().normalize().toRealPath(), words) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A vanished root, an unreadable subfolder: the device could not be asked — not «no such file».
        SortResolution.Unavailable
    }

    private fun resolveIn(base: Path, words: String): SortResolution {
        val files = Files.walk(base).use { stream ->
            stream.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }.toList()
        }
        val named = files.map { InstalledApp(packageName = it.toAbsolutePath().toString(), label = it.fileName.toString()) }
        return when (val match = AppNameMatcher.matchWhole(words, named, folding)) {
            is NameMatch.Exact ->
                if (match.apps.size == 1) SortResolution.Resolved(match.apps.single().packageName)
                else SortResolution.Choice(candidatesOf(match.apps, base), confirm = false)
            is NameMatch.Edge -> SortResolution.Choice(candidatesOf(match.apps, base), confirm = true)
            NameMatch.None -> SortResolution.NotFound
        }
    }

    private fun candidatesOf(files: List<InstalledApp>, base: Path): List<SortCandidate> =
        files.map { SortCandidate(value = it.packageName, label = base.relativize(Path.of(it.packageName)).toString()) }
            .sortedBy { it.label }
}

/**
 * Which sandbox arguments carry a sort (phase 3b, fork 3b-C) — the JVM's [ArgumentSorts], declared beside
 * `SandboxToolSource`'s descriptors and never in them, as `CatalogArgumentSorts` is on Android. Two rows:
 * the `path` of `delete_file` and of `rename_file` is a FILE. `find_file`'s `query` is not sorted — the tool
 * itself searches by it — and its `root` is the device's own value (`SandboxMentionBinder`). No row has a
 * `labelArg`: the sandbox tools keep no companion argument for the words.
 */
object SandboxArgumentSorts : ArgumentSorts {

    private val file = SortedArg(ArgSorts.FILE, labelArg = null)

    private val table: Map<ToolId, Map<String, SortedArg>> = mapOf(
        SandboxToolIds.DELETE_FILE to mapOf(SandboxKeys.PATH to file),
        SandboxToolIds.RENAME_FILE to mapOf(SandboxKeys.PATH to file),
    )

    override fun sortOf(tool: ToolId, arg: String): SortedArg? = table[tool]?.get(arg)

    override fun all(): Map<ToolId, Map<String, SortedArg>> = table
}
