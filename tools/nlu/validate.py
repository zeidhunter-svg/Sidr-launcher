#!/usr/bin/env python3
"""
Stage 1 — NLU intent dataset validation + split writer.

Runs the §6 gates from the generation spec:
  1. Label validity: every label ∈ LABELS (mirrors NluLabel.kt argmax order)
  2. Lang validity: every lang ∈ {en, ar, tr, ru}
  3. Exact + near-dedup (NFC-normalised; flags cross-split duplicates)
  4. Token-length ≤ max_len (48) wordpieces
     - With --vocab: exact check using the provided vocab.txt (mBERT-family)
     - Without --vocab: word-count proxy (word_count + 2 ≤ 48; conservative lower bound)
  5. Per-language [UNK] ratio (requires --vocab; skipped otherwise)
  6. Per-(class × language) balance — asserts no cell below minimum, no class >85% one language
  7. No paraphrase-family leakage across train/test (family_key not shared)

Also writes train.jsonl / val.jsonl / test.jsonl when --write-splits is passed.

Split strategy (family-aware, deterministic):
  - Seed examples always go to train (hand-authored, highest quality, small count).
  - Generated examples: grouped by family_key within each (label, lang) cell.
  - Families sorted deterministically by family_key; last N_TEST families → test,
    next N_VAL families → val, rest → train.
  - Quota per cell: ≥ MIN_TEST_PER_CELL in test, ≥ MIN_VAL_PER_CELL in val.

Usage:
  # Validate only:
  python3 tools/nlu/validate.py

  # Validate + write splits:
  python3 tools/nlu/validate.py --write-splits

  # With exact tokenization (needs vocab.txt from the real/pruned mBERT):
  python3 tools/nlu/validate.py --vocab tools/nlu/vocab.txt --write-splits

  # Check a specific file:
  python3 tools/nlu/validate.py --input tools/nlu/data/intents.jsonl
"""

import argparse
import json
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

# ── Paths ─────────────────────────────────────────────────────────────────────────
HERE = Path(__file__).parent
DATA_DIR = HERE / "data"
INTENTS_FILE = DATA_DIR / "intents.jsonl"

# ── Contract (must match NluLabel.kt enum order) ──────────────────────────────────
LABELS = ["LAUNCH_APP", "SEARCH", "OPEN_SETTINGS", "SHOW_APPS", "HELP", "OPEN_ASSISTANT", "UNKNOWN"]
LABEL_SET = set(LABELS)
LANGS = ["en", "ar", "tr", "ru"]
LANG_SET = set(LANGS)

MAX_LEN = 48               # wordpiece tokens (incl. [CLS] + [SEP])
MAX_WORD_PROXY = 46        # word-count proxy: word_count + 2 ≤ 48 → word_count ≤ 46
MAX_UNK_RATIO = 0.15       # flag if >15% of tokens are [UNK]

MIN_PER_CELL = 30          # minimum examples per (label, lang)
MIN_UNKNOWN_PER_LANG = 20  # UNKNOWN needs extra diversity per language
MAX_LANG_DOMINANCE = 0.85  # no one language > 85% of any class

MIN_TEST_PER_CELL = 10
MIN_VAL_PER_CELL = 10


# ── Tokenizer helpers ─────────────────────────────────────────────────────────────

def _word_count_proxy(text: str) -> int:
    """Lower-bound proxy for subword token count: word count + 2 (CLS + SEP)."""
    return len(text.split()) + 2


def _load_vocab(vocab_path: str) -> dict[str, int]:
    vocab: dict[str, int] = {}
    with open(vocab_path, encoding="utf-8") as f:
        for i, line in enumerate(f):
            vocab[line.rstrip("\n")] = i
    return vocab


def _basic_tokenize(text: str) -> list[str]:
    """Mirrors HF BasicTokenizer(do_lower_case=True): NFD + lower + whitespace/punct split."""
    # NFD normalization then lowercase
    text = unicodedata.normalize("NFD", text).lower()
    tokens = []
    current = []
    for ch in text:
        cat = unicodedata.category(ch)
        if cat == "Mn":                        # strip combining marks
            continue
        if ch.isspace():
            if current:
                tokens.append("".join(current))
                current = []
        elif cat.startswith("P") or cat.startswith("S"):
            if current:
                tokens.append("".join(current))
                current = []
            tokens.append(ch)
        else:
            current.append(ch)
    if current:
        tokens.append("".join(current))
    return tokens


def _wordpiece_tokenize(word: str, vocab: dict[str, int], max_chars: int = 200) -> list[str]:
    if len(word) > max_chars:
        return ["[UNK]"]
    chars = list(word)
    tokens = []
    start = 0
    while start < len(chars):
        end = len(chars)
        found = None
        while start < end:
            substr = "".join(chars[start:end])
            candidate = substr if start == 0 else f"##{substr}"
            if candidate in vocab:
                found = candidate
                break
            end -= 1
        if found is None:
            return ["[UNK]"]
        tokens.append(found)
        start = end
    return tokens


def _tokenize(text: str, vocab: dict[str, int]) -> list[str]:
    """Full tokenization: [CLS] + wordpiece tokens + [SEP]."""
    tokens = ["[CLS]"]
    for word in _basic_tokenize(text):
        tokens.extend(_wordpiece_tokenize(word, vocab))
    tokens.append("[SEP]")
    return tokens


# ── Validation ────────────────────────────────────────────────────────────────────

def _normalize(text: str) -> str:
    return unicodedata.normalize("NFC", text).strip()


def validate(
    examples: list[dict[str, Any]],
    vocab: dict[str, int] | None,
    verbose: bool = True,
) -> tuple[list[str], list[str]]:
    """
    Run all §6 gates. Returns (errors, warnings).
    errors = hard failures that must block split writing.
    warnings = issues to review but not necessarily blocking.
    """
    errors: list[str] = []
    warnings: list[str] = []

    def err(msg: str) -> None:
        errors.append(msg)
        if verbose:
            print(f"  ERROR: {msg}")

    def warn(msg: str) -> None:
        warnings.append(msg)
        if verbose:
            print(f"  WARN:  {msg}")

    # ── Gate 1: label validity ────────────────────────────────────────────────────
    if verbose:
        print("\n[Gate 1] Label validity")
    bad_labels = {e["label"] for e in examples if e.get("label") not in LABEL_SET}
    for bl in sorted(bad_labels):
        err(f"Unknown label '{bl}'")

    # ── Gate 2: lang validity ─────────────────────────────────────────────────────
    if verbose:
        print("[Gate 2] Lang validity")
    bad_langs = {e["lang"] for e in examples if e.get("lang") not in LANG_SET}
    for bl in sorted(bad_langs):
        err(f"Unknown lang '{bl}'")

    # ── Gate 3: exact + near dedup (NFC-normalised) ───────────────────────────────
    if verbose:
        print("[Gate 3] Dedup")
    seen_norm: dict[str, int] = {}   # norm_text → first index
    dup_count = 0
    for i, e in enumerate(examples):
        norm = _normalize(e.get("text", ""))
        if not norm:
            err(f"Row {i}: empty text")
            continue
        if norm in seen_norm:
            dup_count += 1
            if dup_count <= 10:
                warn(f"Duplicate (rows {seen_norm[norm]} and {i}): '{norm[:60]}'")
        else:
            seen_norm[norm] = i
    if dup_count > 10:
        warn(f"... and {dup_count - 10} more duplicates (total {dup_count})")

    # ── Gate 4: token length ─────────────────────────────────────────────────────
    if verbose:
        mode = "exact (vocab provided)" if vocab else "word-count proxy (no vocab)"
        print(f"[Gate 4] Token length ≤ {MAX_LEN} [{mode}]")
    overflow_count = 0
    for i, e in enumerate(examples):
        text = e.get("text", "")
        if vocab is not None:
            toks = _tokenize(text, vocab)
            length = len(toks)
        else:
            length = _word_count_proxy(text)
        if length > MAX_LEN:
            overflow_count += 1
            if overflow_count <= 5:
                warn(f"Row {i} overflow ({length} tokens): '{text[:60]}'")
    if overflow_count > 5:
        warn(f"... and {overflow_count - 5} more overflows (total {overflow_count})")
    if overflow_count:
        err(f"{overflow_count} examples exceed max_len={MAX_LEN} — trim or remove them")

    # ── Gate 5: per-language [UNK] ratio (exact vocab only) ──────────────────────
    if vocab is not None:
        if verbose:
            print("[Gate 5] Per-language [UNK] ratio")
        unk_stats: dict[str, list[float]] = defaultdict(list)
        for e in examples:
            toks = _tokenize(e.get("text", ""), vocab)
            content = [t for t in toks if t not in ("[CLS]", "[SEP]", "[PAD]")]
            if not content:
                continue
            unk_ratio = content.count("[UNK]") / len(content)
            unk_stats[e["lang"]].append(unk_ratio)
        for lang in LANGS:
            ratios = unk_stats.get(lang, [])
            if not ratios:
                continue
            avg = sum(ratios) / len(ratios)
            high = [r for r in ratios if r > MAX_UNK_RATIO]
            if verbose:
                print(f"  {lang}: avg_unk={avg:.3f}, high_unk_count={len(high)}/{len(ratios)}")
            if avg > MAX_UNK_RATIO * 0.5:
                warn(f"{lang}: average [UNK] ratio {avg:.3f} is elevated — "
                     f"check normalization or switch to the cased mBERT base")
    else:
        if verbose:
            print("[Gate 5] [UNK] ratio — SKIPPED (no --vocab provided)")

    # ── Gate 6: per-(class × lang) balance ────────────────────────────────────────
    if verbose:
        print("[Gate 6] Balance per (label, lang)")
    cell_counts: Counter = Counter()
    class_by_lang: dict[str, Counter] = defaultdict(Counter)
    for e in examples:
        cell_counts[(e["label"], e["lang"])] += 1
        class_by_lang[e["label"]][e["lang"]] += 1

    # Minimum count check
    for label in LABELS:
        for lang in LANGS:
            count = cell_counts[(label, lang)]
            minimum = MIN_UNKNOWN_PER_LANG if label == "UNKNOWN" else MIN_PER_CELL
            if count < minimum:
                err(f"({label}, {lang}) has only {count} examples (minimum {minimum})")
            elif verbose:
                print(f"  ({label:16s}, {lang}) = {count}")

    # Language dominance check
    for label in LABELS:
        total = sum(class_by_lang[label].values())
        if total == 0:
            continue
        for lang in LANGS:
            frac = class_by_lang[label].get(lang, 0) / total
            if frac > MAX_LANG_DOMINANCE:
                err(f"Class {label}: lang '{lang}' dominates at {frac:.0%} (max {MAX_LANG_DOMINANCE:.0%})")

    return errors, warnings


# ── Split logic ───────────────────────────────────────────────────────────────────

def write_splits(examples: list[dict[str, Any]], verbose: bool = True) -> None:
    """
    Assign train/val/test splits and write the three JSONL files.

    Strategy (family-aware, no paraphrase leakage):
      - Seed examples (source=="seed") always → train.
      - Generated examples: grouped by family_key within each (label, lang) cell.
        Sorted families assigned deterministically; last families → test, next → val, rest → train.
      - Cross-split dedup enforced: an NFC-normalised text appearing in test/val is
        removed from train.
    """
    # Group generated examples by (label, lang, family_key)
    from itertools import groupby

    seed_examples = [e for e in examples if e.get("source") == "seed"]
    gen_examples = [e for e in examples if e.get("source") != "seed"]

    # family key can be missing for legacy entries — treat each as its own family
    def fam_key(e: dict) -> tuple:
        return (e["label"], e["lang"], e.get("family_key") or f"_solo_{id(e)}")

    gen_sorted = sorted(gen_examples, key=fam_key)
    families: dict[tuple[str, str], list[tuple[str, list[dict]]]] = defaultdict(list)
    for k, group in groupby(gen_sorted, key=fam_key):
        label, lang, fk = k
        families[(label, lang)].append((fk, list(group)))

    train_set: list[dict[str, Any]] = list(seed_examples)
    val_set: list[dict[str, Any]] = []
    test_set: list[dict[str, Any]] = []

    for label in LABELS:
        for lang in LANGS:
            cell_families = sorted(families.get((label, lang), []), key=lambda x: x[0])
            test_pool: list[dict[str, Any]] = []
            val_pool: list[dict[str, Any]] = []
            train_pool: list[dict[str, Any]] = []

            # Greedily assign families: iterate from the end for test, then val, then train
            for fk, fam_examples in reversed(cell_families):
                if len(test_pool) < MIN_TEST_PER_CELL:
                    test_pool.extend(fam_examples)
                elif len(val_pool) < MIN_VAL_PER_CELL:
                    val_pool.extend(fam_examples)
                else:
                    train_pool.extend(fam_examples)

            test_set.extend(test_pool)
            val_set.extend(val_pool)
            train_set.extend(train_pool)

    # Cross-split dedup: remove from train any text that appears in test or val
    held_out_norms: set[str] = set()
    for e in test_set + val_set:
        held_out_norms.add(_normalize(e["text"]))
    train_deduped = [e for e in train_set if _normalize(e["text"]) not in held_out_norms]
    removed = len(train_set) - len(train_deduped)

    # Annotate split field
    for e in train_deduped:
        e["split"] = "train"
    for e in val_set:
        e["split"] = "val"
    for e in test_set:
        e["split"] = "test"

    # Write files
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    for split_name, split_examples in [("train", train_deduped), ("val", val_set), ("test", test_set)]:
        path = DATA_DIR / f"{split_name}.jsonl"
        with open(path, "w", encoding="utf-8") as f:
            for e in split_examples:
                f.write(json.dumps(e, ensure_ascii=False) + "\n")
        if verbose:
            print(f"  {split_name}.jsonl: {len(split_examples)} examples")

    # Also update split field in intents.jsonl
    split_map: dict[str, str] = {}
    for e in train_deduped + val_set + test_set:
        split_map[_normalize(e["text"])] = e["split"]

    updated: list[dict[str, Any]] = []
    for e in examples:
        e_copy = dict(e)
        norm = _normalize(e_copy.get("text", ""))
        e_copy["split"] = split_map.get(norm, "train")
        updated.append(e_copy)
    with open(INTENTS_FILE, "w", encoding="utf-8") as f:
        for e in updated:
            f.write(json.dumps(e, ensure_ascii=False) + "\n")

    if removed and verbose:
        print(f"  Removed {removed} train examples due to cross-split dedup")

    print(f"\nSplits: train={len(train_deduped)} val={len(val_set)} test={len(test_set)} "
          f"total={len(train_deduped)+len(val_set)+len(test_set)}")

    # Verify no family_key leakage between train and test
    train_fks = {e.get("family_key") for e in train_deduped if e.get("family_key")}
    test_fks  = {e.get("family_key") for e in test_set if e.get("family_key")}
    leaked = train_fks & test_fks
    if leaked:
        print(f"  WARN: {len(leaked)} family_keys appear in both train and test: {list(leaked)[:3]}")
    else:
        print("  Family-key leakage check: PASS (no train/test family overlap)")

    # Check minimum per (label, lang) in test + val
    test_counts: Counter = Counter((e["label"], e["lang"]) for e in test_set)
    val_counts:  Counter = Counter((e["label"], e["lang"]) for e in val_set)
    problems = []
    for label in LABELS:
        for lang in LANGS:
            if test_counts[(label, lang)] < MIN_TEST_PER_CELL:
                problems.append(f"({label},{lang}) test={test_counts[(label,lang)]} < {MIN_TEST_PER_CELL}")
            if val_counts[(label, lang)] < MIN_VAL_PER_CELL:
                problems.append(f"({label},{lang}) val={val_counts[(label,lang)]} < {MIN_VAL_PER_CELL}")
    if problems:
        print("  WARN: some cells below minimum quota in test/val splits:")
        for p in problems:
            print(f"    {p}")
        print("  → Run --expand to generate more examples for these cells.")
    else:
        print("  Per-cell quota check: PASS")


def _assert_labels_json() -> None:
    """Assert labels.json is consistent with LABELS list (catches accidental edits)."""
    labels_path = DATA_DIR / "labels.json"
    if not labels_path.exists():
        print("  WARN: labels.json not found — run generate.py first")
        return
    with open(labels_path, encoding="utf-8") as f:
        data = json.load(f)
    index_to_label = data.get("index_to_label", [])
    if index_to_label != LABELS:
        print(f"  ERROR: labels.json index_to_label {index_to_label} != LABELS {LABELS}")
        sys.exit(1)
    label_to_index = data.get("label_to_index", {})
    for label, idx in label_to_index.items():
        if LABELS[idx] != label:
            print(f"  ERROR: labels.json label_to_index[{label}]={idx} but LABELS[{idx}]={LABELS[idx]}")
            sys.exit(1)
    print("  labels.json contract check: PASS")


# ── Main ──────────────────────────────────────────────────────────────────────────

def main() -> None:
    ap = argparse.ArgumentParser(description="Validate the Sidr NLU intent dataset (§6 gates).")
    ap.add_argument("--input", type=Path, default=INTENTS_FILE,
                    help="Path to intents.jsonl (default: tools/nlu/data/intents.jsonl).")
    ap.add_argument("--vocab", type=str, default=None,
                    help="Path to vocab.txt for exact tokenization + UNK-ratio gate. "
                         "Should be the pruned multilingual vocab from the mBERT teacher. "
                         "Without this, a word-count proxy is used.")
    ap.add_argument("--write-splits", action="store_true",
                    help="Write train.jsonl / val.jsonl / test.jsonl and update intents.jsonl.")
    ap.add_argument("--quiet", action="store_true",
                    help="Suppress per-row output; only show summary.")
    args = ap.parse_args()

    input_path: Path = args.input
    if not input_path.exists():
        print(f"ERROR: {input_path} not found. Run generate.py --seed-only first.", file=sys.stderr)
        sys.exit(1)

    print(f"Loading {input_path} ...")
    examples: list[dict[str, Any]] = []
    with open(input_path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                examples.append(json.loads(line))
    print(f"Loaded {len(examples)} examples.")

    # labels.json contract check
    print("\n[Pre-flight] labels.json")
    _assert_labels_json()

    # Load vocab if provided
    vocab: dict[str, int] | None = None
    if args.vocab:
        print(f"\n[Pre-flight] Loading vocab from {args.vocab} ...")
        vocab = _load_vocab(args.vocab)
        print(f"  Vocab size: {len(vocab)}")

    # Run validation
    print("\n── Validation ──────────────────────────────────────────────────────────")
    errors, warnings = validate(examples, vocab=vocab, verbose=not args.quiet)

    print(f"\n── Summary ─────────────────────────────────────────────────────────────")
    print(f"  Errors:   {len(errors)}")
    print(f"  Warnings: {len(warnings)}")

    if errors:
        print("\nFailed gates (must fix before training):")
        for e in errors:
            print(f"  ✗ {e}")
        if not args.write_splits:
            sys.exit(1)
        print("\nProceeding to write splits despite errors (fix errors before training).")

    # Print per-(class × lang) table
    counts: Counter = Counter((e["label"], e["lang"]) for e in examples)
    print("\nCounts per (label, lang):")
    header = "  " + " " * 16 + "".join(f"  {l:>5}" for l in LANGS) + "  total"
    print(header)
    for label in LABELS:
        row_counts = [counts[(label, lang)] for lang in LANGS]
        total = sum(row_counts)
        row = "  " + label.ljust(16) + "".join(f"  {c:>5}" for c in row_counts) + f"  {total:>5}"
        print(row)
    all_counts = [counts[(l, ln)] for l in LABELS for ln in LANGS]
    grand = sum(all_counts)
    print(f"  {'TOTAL'.ljust(16)}" + "".join(f"  {sum(counts[(l, ln)] for l in LABELS):>5}" for ln in LANGS) + f"  {grand:>5}")

    # Write splits
    if args.write_splits:
        print("\n── Writing splits ───────────────────────────────────────────────────────")
        write_splits(examples, verbose=not args.quiet)
    else:
        print("\nPass --write-splits to produce train.jsonl / val.jsonl / test.jsonl.")

    if not errors:
        print("\n✓ All gates passed.")
    else:
        sys.exit(1)


if __name__ == "__main__":
    main()
