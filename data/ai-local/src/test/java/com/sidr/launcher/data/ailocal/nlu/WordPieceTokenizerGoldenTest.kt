package com.sidr.launcher.data.ailocal.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Base64

/**
 * Block P / Fork P6-8: the WordPiece tokenizer is asserted **byte-exact** against golden vectors
 * produced by an INDEPENDENT reference implementation (`tools/nlu/gen_golden_vectors.py`, the
 * HuggingFace `BertTokenizer` algorithm). A "deterministic but wrong" tokenizer would pass a
 * determinism-only test yet feed the model garbage — this is the #1 silent-failure risk of the
 * block, so equality (not just stability) is the assertion.
 *
 * Golden + mini vocab are generated offline (stdlib only) and checked into test resources. When
 * the real model + real vocab.txt land, regenerate the golden from the real HF tokenizer
 * (device-pending, like Block J) and re-run this test.
 */
class WordPieceTokenizerGoldenTest {

    private data class Case(
        val text: String,
        val tokens: List<String>,
        val ids: LongArray,
        val mask: LongArray,
        val types: LongArray,
    )

    private fun resource(path: String): String {
        val stream = requireNotNull(javaClass.getResourceAsStream(path)) { "missing test resource $path" }
        return stream.bufferedReader(Charsets.UTF_8).readText()
    }

    private fun loadVocab(): Map<String, Int> =
        javaClass.getResourceAsStream("/nlu/vocab.mini.txt")!!.use { WordPieceTokenizer.loadVocab(it) }

    private fun loadGolden(): Pair<Int, List<Case>> {
        val lines = resource("/nlu/golden_tokenization.txt").trim().lines()
        val maxLen = lines.first().split("\t")[1].toInt()
        val cases = lines.drop(1).map { line ->
            val f = line.split("\t")
            Case(
                text = String(Base64.getDecoder().decode(f[0]), Charsets.UTF_8),
                tokens = f[1].split(" "),
                ids = f[2].split(" ").map { it.toLong() }.toLongArray(),
                mask = f[3].split(" ").map { it.toLong() }.toLongArray(),
                types = f[4].split(" ").map { it.toLong() }.toLongArray(),
            )
        }
        return maxLen to cases
    }

    @Test
    fun tokenizer_matches_golden_vectors_exactly() {
        val tokenizer = WordPieceTokenizer(loadVocab())
        val (maxLen, cases) = loadGolden()
        assertNotNull(cases)
        assertEquals("expected golden cases", true, cases.isNotEmpty())

        for (case in cases) {
            val enc = tokenizer.encode(case.text, maxLen)
            assertEquals("tokens for '${case.text}'", case.tokens, enc.tokens)
            assertEquals("input_ids for '${case.text}'", case.ids.toList(), enc.inputIds.toList())
            assertEquals("attention_mask for '${case.text}'", case.mask.toList(), enc.attentionMask.toList())
            assertEquals("token_type_ids for '${case.text}'", case.types.toList(), enc.tokenTypeIds.toList())
        }
    }

    @Test
    fun token_type_ids_are_all_zero_for_single_sequence() {
        val tokenizer = WordPieceTokenizer(loadVocab())
        val enc = tokenizer.encode("open camera", 12)
        assertEquals(List(12) { 0L }, enc.tokenTypeIds.toList())
    }

    @Test
    fun arrays_are_exactly_maxLen_long() {
        val tokenizer = WordPieceTokenizer(loadVocab())
        val enc = tokenizer.encode("open camera", 12)
        assertEquals(12, enc.inputIds.size)
        assertEquals(12, enc.attentionMask.size)
        assertEquals(12, enc.tokenTypeIds.size)
    }
}
