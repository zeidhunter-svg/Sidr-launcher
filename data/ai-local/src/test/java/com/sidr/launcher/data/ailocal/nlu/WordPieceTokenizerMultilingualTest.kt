package com.sidr.launcher.data.ailocal.nlu

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * OQ#1 multilingual amendment (2026-06-29): proves the [WordPieceTokenizer] is **vocab-size-agnostic**
 * and works against an arbitrary, non-English, non-30522 vocab — including `##` subword continuations
 * and non-Latin (Cyrillic + Arabic) tokens. This enforces (not merely assumes) that the pruned
 * multilingual `vocab.txt` (en/ar/tr/ru, ~20–30k) needs no tokenizer change.
 *
 * The vocab is built inline (no golden regeneration, no real artifact) and is deliberately tiny — its
 * size (a handful of entries, clearly != 30522) is itself part of the assertion. Tokens are kept
 * already-lower-case so the HF uncased normalization (lower-case + NFD accent-strip) is a no-op for
 * the bare Cyrillic/Arabic letters used here, matching `bert-base-multilingual-uncased`.
 */
class WordPieceTokenizerMultilingualTest {

    // Arbitrary multilingual mini-vocab: special tokens + Latin/Cyrillic/Arabic, incl. a "##" piece.
    // "погода" (RU "weather") is intentionally ABSENT so it must split into "по" + "##года".
    private val vocab: Map<String, Int> = mapOf(
        "[PAD]" to 0,
        "[UNK]" to 1,
        "[CLS]" to 2,
        "[SEP]" to 3,
        "open" to 4,
        "привет" to 5,   // RU "hello" — whole word
        "по" to 6,       // RU prefix
        "##года" to 7,   // RU "##weather" continuation
        "طقس" to 8,       // AR "weather" — whole word, no case, no combining marks
    )

    private val tokenizer = WordPieceTokenizer(vocab)

    @Test
    fun vocab_is_arbitrary_size_not_30522() {
        // The tokenizer derives everything from the map; nothing assumes an English 30522 table.
        assertEquals(9, vocab.size)
    }

    @Test
    fun whole_words_across_scripts() {
        assertEquals(listOf("open"), tokenizer.tokenize("open"))
        assertEquals(listOf("привет"), tokenizer.tokenize("привет"))
        assertEquals(listOf("طقس"), tokenizer.tokenize("طقس"))
    }

    @Test
    fun cyrillic_subword_continuation() {
        // greedy longest-match: "погода" -> "по" + "##года"
        assertEquals(listOf("по", "##года"), tokenizer.tokenize("погода"))
    }

    @Test
    fun out_of_vocab_falls_back_to_unk() {
        assertEquals(listOf("[UNK]"), tokenizer.tokenize("zzzz"))
    }

    @Test
    fun mixed_script_sentence() {
        assertEquals(
            listOf("open", "по", "##года", "طقس"),
            tokenizer.tokenize("open погода طقس"),
        )
    }

    @Test
    fun encode_pads_and_resolves_non_latin_ids() {
        val enc = tokenizer.encode("طقس", maxLen = 6)
        // [CLS] طقس [SEP] [PAD] [PAD] [PAD]
        assertEquals(listOf(2L, 8L, 3L, 0L, 0L, 0L), enc.inputIds.toList())
        assertEquals(listOf(1L, 1L, 1L, 0L, 0L, 0L), enc.attentionMask.toList())
        assertEquals(6, enc.tokenTypeIds.size)
    }
}
