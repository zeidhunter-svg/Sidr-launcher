package com.sidr.launcher.data.ailocal.nlu

import java.io.InputStream
import java.text.Normalizer

/**
 * BERT **uncased** WordPiece tokenizer — a faithful Kotlin port of HuggingFace `BertTokenizer`
 * (`BasicTokenizer(do_lower_case=true)` + `WordpieceTokenizer`). Pure: no `ai.onnxruntime`, no
 * Android — part of the P2a JVM-testable layer, and reusable by Phase 7's `TextEmbedder` impl
 * (Fork P6-6) without dragging in the ONNX shell.
 *
 * A "deterministic but wrong" tokenizer feeds the model garbage and still returns a result, so
 * this is the #1 silent-failure risk of Block P. It is asserted **byte-exact** against golden
 * vectors produced by an independent reference implementation (`tools/nlu/gen_golden_vectors.py`,
 * the HF algorithm) — see `WordPieceTokenizerGoldenTest`.
 *
 * Pipeline (matching HF exactly):
 *  1. clean text — drop control / replacement chars, normalize whitespace to a single space.
 *  2. add spaces around CJK chars.
 *  3. whitespace-split; per token: lowercase, NFD-strip accents (drop `Mn`), split on punctuation.
 *  4. WordPiece each resulting token: greedy longest-match-first with `##` continuation; whole
 *     token → `[UNK]` if any sub-span is unknown or the token exceeds [MAX_INPUT_CHARS_PER_WORD].
 *  5. wrap with `[CLS] … [SEP]`, truncate content to `maxLen-2`, pad to `maxLen` with `[PAD]`.
 */
class WordPieceTokenizer(
    private val vocab: Map<String, Int>,
    unkToken: String = "[UNK]",
    clsToken: String = "[CLS]",
    sepToken: String = "[SEP]",
    padToken: String = "[PAD]",
) {
    private val unkId = vocab.getValue(unkToken)
    private val clsId = vocab.getValue(clsToken)
    private val sepId = vocab.getValue(sepToken)
    private val padId = vocab.getValue(padToken)
    private val unk = unkToken

    /** Tokenized result. The three arrays are exactly [OnnxModelSpec.maxLen] long (int64-bound). */
    data class Encoded(
        val tokens: List<String>,
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray,
    )

    /** WordPiece string tokens (no special tokens, no padding) — used by tests + the encoder. */
    fun tokenize(text: String): List<String> {
        val out = ArrayList<String>()
        for (token in basicTokenize(text)) {
            out.addAll(wordpiece(token))
        }
        return out
    }

    /** Full model input: `[CLS] … [SEP]`, truncated to `maxLen-2` content, padded to [maxLen]. */
    fun encode(text: String, maxLen: Int): Encoded {
        val pieces = tokenize(text)
        val content = if (pieces.size > maxLen - 2) pieces.subList(0, maxLen - 2) else pieces
        val tokens = ArrayList<String>(content.size + 2)
        tokens.add("[CLS]"); tokens.addAll(content); tokens.add("[SEP]")

        val ids = LongArray(maxLen)
        val mask = LongArray(maxLen)
        val types = LongArray(maxLen) // all zeros — single sequence
        for (i in 0 until maxLen) {
            if (i < tokens.size) {
                val tok = tokens[i]
                ids[i] = (if (i == 0) clsId else if (i == tokens.size - 1) sepId else vocab[tok] ?: unkId).toLong()
                mask[i] = 1L
            } else {
                ids[i] = padId.toLong()
                mask[i] = 0L
            }
        }
        return Encoded(tokens, ids, mask, types)
    }

    // ---- BasicTokenizer ----

    private fun basicTokenize(text: String): List<String> {
        val cleaned = tokenizeChinese(cleanText(text))
        val split = ArrayList<String>()
        for (token in cleaned.split(WHITESPACE).filter { it.isNotEmpty() }) {
            val lowered = stripAccents(token.lowercase())
            split.addAll(splitOnPunctuation(lowered))
        }
        return split
    }

    private fun cleanText(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val cp = ch.code
            if (cp == 0 || cp == 0xFFFD || isControl(ch)) continue
            sb.append(if (isWhitespace(ch)) ' ' else ch)
        }
        return sb.toString()
    }

    private fun tokenizeChinese(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            if (isChinese(ch.code)) {
                sb.append(' ').append(ch).append(' ')
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun stripAccents(token: String): String {
        val nfd = Normalizer.normalize(token, Normalizer.Form.NFD)
        val sb = StringBuilder(nfd.length)
        for (ch in nfd) {
            if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) sb.append(ch)
        }
        return sb.toString()
    }

    private fun splitOnPunctuation(token: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (ch in token) {
            if (isPunctuation(ch)) {
                if (cur.isNotEmpty()) { out.add(cur.toString()); cur.setLength(0) }
                out.add(ch.toString())
            } else {
                cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    // ---- WordpieceTokenizer ----

    private fun wordpiece(token: String): List<String> {
        if (token.length > MAX_INPUT_CHARS_PER_WORD) return listOf(unk)
        val out = ArrayList<String>()
        var start = 0
        val chars = token
        while (start < chars.length) {
            var end = chars.length
            var cur: String? = null
            while (start < end) {
                var substr = chars.substring(start, end)
                if (start > 0) substr = "##$substr"
                if (vocab.containsKey(substr)) { cur = substr; break }
                end--
            }
            if (cur == null) return listOf(unk)
            out.add(cur)
            start = end
        }
        return out
    }

    // ---- char classes (mirroring HF) ----

    private fun isControl(ch: Char): Boolean {
        if (ch == '\t' || ch == '\n' || ch == '\r') return false
        return when (Character.getType(ch)) {
            Character.CONTROL.toInt(), Character.FORMAT.toInt(),
            Character.PRIVATE_USE.toInt(), Character.SURROGATE.toInt(),
            Character.UNASSIGNED.toInt() -> true
            else -> false
        }
    }

    private fun isWhitespace(ch: Char): Boolean {
        if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') return true
        return Character.getType(ch) == Character.SPACE_SEPARATOR.toInt()
    }

    private fun isPunctuation(ch: Char): Boolean {
        val cp = ch.code
        if (cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126) return true
        return when (Character.getType(ch)) {
            Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt() -> true
            else -> false
        }
    }

    private fun isChinese(cp: Int): Boolean =
        cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0x20000..0x2A6DF ||
            cp in 0x2A700..0x2B73F || cp in 0x2B740..0x2B81F || cp in 0x2B820..0x2CEAF ||
            cp in 0xF900..0xFAFF || cp in 0x2F800..0x2FA1F

    companion object {
        private const val MAX_INPUT_CHARS_PER_WORD = 200
        private val WHITESPACE = Regex("\\s+")

        /** Loads a BERT `vocab.txt` (one token per line; line index == id) into a vocab map. */
        fun loadVocab(stream: InputStream): Map<String, Int> {
            val map = LinkedHashMap<String, Int>()
            stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                var i = 0
                for (line in lines) {
                    // vocab.txt tokens never contain a trailing newline; do not trim interior chars.
                    map[line.removeSuffix("\r")] = i
                    i++
                }
            }
            return map
        }
    }
}
