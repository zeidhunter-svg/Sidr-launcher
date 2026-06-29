#!/usr/bin/env python3
"""
Block P / P0 — reference WordPiece golden-vector generator (stdlib only).

This is an *independent* reference implementation of the BERT (uncased) tokenizer
pipeline — HuggingFace `BertTokenizer`'s BasicTokenizer + WordpieceTokenizer — written
with the Python standard library only (no `transformers`, no network). It exists so the
Kotlin `WordPieceTokenizer` in :data:ai-local can be tested for *exact equality* against a
reference produced by a different codebase (the #1 silent-failure risk of Block P: a
"deterministic but wrong" tokenizer feeds the model garbage and still returns a result).

Two independent implementations of the same documented algorithm agreeing == validation.

The WordPiece algorithm is **language-independent and vocab-size-agnostic** (OQ#1 multilingual
amendment, 2026-06-29): the uncased base (lower-case + NFD accent-strip) matches the multilingual
`bert-base-multilingual-uncased` teacher's preprocessing, so the same reference serves en/ar/tr/ru.

IMPORTANT (device-pending, like Block J):
  These golden vectors are generated against `vocab.mini.txt` — a tiny curated vocab that
  exercises whole-word, greedy `##` subword continuation, [UNK], accent-strip, punctuation
  split, and truncation paths. When the REAL model + the pruned multilingual `vocab.txt`
  (~20-30k, en/ar/tr/ru, uncased) land (Open Question #1 artifact), regenerate the golden set
  from the *actual* HuggingFace `BertTokenizer` over that vocab and re-run the Kotlin test
  against it. The algorithm here matches HF transformers; only the vocab differs.

Run:
  python3 tools/nlu/gen_golden_vectors.py
Writes:
  tools/nlu/golden_tokenization.json                          (canonical)
  data/ai-local/src/test/resources/nlu/vocab.mini.txt         (test classpath copy)
  data/ai-local/src/test/resources/nlu/golden_tokenization.json (test classpath copy)
"""
import json
import os
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
TEST_RES = os.path.join(REPO, "data", "ai-local", "src", "test", "resources", "nlu")

# Pinned contract for the golden set. The golden uses MAX_LEN=12 so a long input forces the
# truncation path with short sentences; production DEFAULT max_len is 48 (OnnxModelSpec, OQ#1
# multilingual amendment). This golden MAX_LEN is independent of production and stays 12.
MAX_LEN = 12
PAD, UNK, CLS, SEP = "[PAD]", "[UNK]", "[CLS]", "[SEP]"
MAX_INPUT_CHARS_PER_WORD = 200


def load_vocab(path):
    vocab = {}
    with open(path, encoding="utf-8") as f:
        for i, line in enumerate(f):
            tok = line.rstrip("\n")
            vocab[tok] = i
    return vocab


# ---- BasicTokenizer (do_lower_case=True), mirroring HF transformers ----

def _is_control(ch):
    if ch in ("\t", "\n", "\r"):
        return False
    return unicodedata.category(ch).startswith("C")


def _is_whitespace(ch):
    if ch in (" ", "\t", "\n", "\r"):
        return True
    return unicodedata.category(ch) == "Zs"


def _is_punctuation(ch):
    cp = ord(ch)
    if (33 <= cp <= 47) or (58 <= cp <= 64) or (91 <= cp <= 96) or (123 <= cp <= 126):
        return True
    return unicodedata.category(ch).startswith("P")


def _is_chinese_char(cp):
    return (
        0x4E00 <= cp <= 0x9FFF or 0x3400 <= cp <= 0x4DBF or 0x20000 <= cp <= 0x2A6DF
        or 0x2A700 <= cp <= 0x2B73F or 0x2B740 <= cp <= 0x2B81F or 0x2B820 <= cp <= 0x2CEAF
        or 0xF900 <= cp <= 0xFAFF or 0x2F800 <= cp <= 0x2FA1F
    )


def _clean_text(text):
    out = []
    for ch in text:
        cp = ord(ch)
        if cp == 0 or cp == 0xFFFD or _is_control(ch):
            continue
        out.append(" " if _is_whitespace(ch) else ch)
    return "".join(out)


def _tokenize_chinese_chars(text):
    out = []
    for ch in text:
        if _is_chinese_char(ord(ch)):
            out.append(" "); out.append(ch); out.append(" ")
        else:
            out.append(ch)
    return "".join(out)


def _strip_accents(token):
    token = unicodedata.normalize("NFD", token)
    return "".join(c for c in token if unicodedata.category(c) != "Mn")


def _split_on_punc(token):
    chars = list(token)
    out, cur = [], []
    for ch in chars:
        if _is_punctuation(ch):
            if cur:
                out.append("".join(cur)); cur = []
            out.append(ch)
        else:
            cur.append(ch)
    if cur:
        out.append("".join(cur))
    return out


def basic_tokenize(text):
    text = _clean_text(text)
    text = _tokenize_chinese_chars(text)
    tokens = text.split()
    split_tokens = []
    for token in tokens:
        token = token.lower()
        token = _strip_accents(token)
        split_tokens.extend(_split_on_punc(token))
    return " ".join(split_tokens).split()


def wordpiece_tokenize(token, vocab):
    if len(token) > MAX_INPUT_CHARS_PER_WORD:
        return [UNK]
    sub_tokens, start = [], 0
    chars = list(token)
    while start < len(chars):
        end = len(chars)
        cur = None
        while start < end:
            substr = "".join(chars[start:end])
            if start > 0:
                substr = "##" + substr
            if substr in vocab:
                cur = substr
                break
            end -= 1
        if cur is None:
            return [UNK]
        sub_tokens.append(cur)
        start = end
    return sub_tokens


def tokenize(text, vocab):
    out = []
    for tok in basic_tokenize(text):
        out.extend(wordpiece_tokenize(tok, vocab))
    return out


def encode(text, vocab):
    pieces = tokenize(text, vocab)
    # [CLS] ... [SEP], truncate content to MAX_LEN-2
    content = pieces[: MAX_LEN - 2]
    seq = [CLS] + content + [SEP]
    ids = [vocab.get(t, vocab[UNK]) for t in seq]
    attn = [1] * len(ids)
    while len(ids) < MAX_LEN:
        ids.append(vocab[PAD]); attn.append(0)
    return {
        "text": text,
        "tokens": seq,
        "input_ids": ids,
        "attention_mask": attn,
        "token_type_ids": [0] * MAX_LEN,
    }


SENTENCES = [
    "open camera",          # whole words
    "settings",             # greedy ## subword: set ##ting ##s
    "Café",            # accent strip + lowercase -> cafe
    "open!",                # punctuation split -> open !
    "xyzzy",                # OOV -> [UNK]
    "  OPEN   Camera  ",    # whitespace collapse + lowercase
    "play music and play some music now please show apps open camera",  # 12 words -> truncation to MAX_LEN-2
]


def main():
    vocab = load_vocab(os.path.join(HERE, "vocab.mini.txt"))
    golden = {"maxLen": MAX_LEN, "vocab": "vocab.mini.txt", "cases": [encode(s, vocab) for s in SENTENCES]}
    payload = json.dumps(golden, ensure_ascii=False, indent=2)

    with open(os.path.join(HERE, "golden_tokenization.json"), "w", encoding="utf-8") as f:
        f.write(payload + "\n")

    # Also emit a dependency-free flat format the Kotlin JVM test parses without a JSON lib.
    # Line 0: "maxLen\t<n>". Each case: b64(text) \t tokens \t ids \t mask \t types (space-joined).
    import base64
    flat = ["maxLen\t%d" % MAX_LEN]
    for c in golden["cases"]:
        flat.append("\t".join([
            base64.b64encode(c["text"].encode("utf-8")).decode("ascii"),
            " ".join(c["tokens"]),
            " ".join(str(x) for x in c["input_ids"]),
            " ".join(str(x) for x in c["attention_mask"]),
            " ".join(str(x) for x in c["token_type_ids"]),
        ]))
    flat_payload = "\n".join(flat) + "\n"

    os.makedirs(TEST_RES, exist_ok=True)
    with open(os.path.join(HERE, "golden_tokenization.txt"), "w", encoding="utf-8") as f:
        f.write(flat_payload)
    with open(os.path.join(TEST_RES, "golden_tokenization.json"), "w", encoding="utf-8") as f:
        f.write(payload + "\n")
    with open(os.path.join(TEST_RES, "golden_tokenization.txt"), "w", encoding="utf-8") as f:
        f.write(flat_payload)
    # copy vocab too
    with open(os.path.join(HERE, "vocab.mini.txt"), encoding="utf-8") as src:
        data = src.read()
    with open(os.path.join(TEST_RES, "vocab.mini.txt"), "w", encoding="utf-8") as f:
        f.write(data)

    print("wrote golden vectors for", len(SENTENCES), "cases, maxLen", MAX_LEN)
    for c in golden["cases"]:
        print(" ", repr(c["text"]), "->", c["tokens"])


if __name__ == "__main__":
    main()
