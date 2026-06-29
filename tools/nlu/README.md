# tools/nlu — Phase 6 / Block P offline model pipeline (P0)

> **NOT app code.** Lives outside the Gradle source sets. Produces the artifacts the runtime
> code in `:data:ai-local` (Block P) consumes and that Block Q will download + SHA-256-verify.

## What Block P needs from here

1. A **dynamic-int8**, ONNX-exported **7-class** sequence classifier (`intent.onnx`), produced by
   compressing a multilingual teacher (prune → distill → int8 — OQ#1 multilingual amendment).
2. The matching **BERT WordPiece uncased `vocab.txt`** — the **pruned multilingual** vocab
   (data-driven size, ~20–30k, en/ar/tr/ru), shipped as an asset beside the model.
3. **Golden tokenizer vectors** the JVM test asserts the Kotlin `WordPieceTokenizer` against, byte-exact.

## Pinned contract (P0 pins → P2 builds against; the #1 silent-break source)

| Item | Value | Notes |
|---|---|---|
| Teacher (OQ#1 multilingual) | `bert-base-multilingual-uncased` (WordPiece, uncased, ~110k vocab) | NOT shipped raw — compressed |
| Shipped model | teacher **vocab-pruned → layer-distilled → int8** (en/ar/tr/ru + launcher domain) | `<150ms` SM-A325F = hard Stage-2 gate; non-transformer fallback if missed |
| Tokenizer | BERT **WordPiece, uncased**, pruned multilingual `vocab.txt` (data-driven ~20–30k) | MUST equal the teacher's; Kotlin impl is vocab-size-agnostic |
| Special tokens | `[PAD] [UNK] [CLS] [SEP]` | resolved by **name** from `vocab.txt` (ids may shift after pruning), not hardcoded in Kotlin |
| Quantization | dynamic int8 | |
| Export opset | ≤ 22 (verify ORT 1.20.0 supports) | pin at export |
| Inputs | `input_ids`, `attention_mask`, `token_type_ids` — **all int64** | most BERT exports need all three; `token_type_ids` all-zeros for single-sequence |
| Sequence axis | **fixed**, `max_len = 48` | provisional (tr/ar fragment more per word); the Kotlin side always pads/truncates to exactly `[1,48]`, valid for both fixed and dynamic exports |
| Vocab size | **data-driven** = `vocab.txt` line count; `assert_onnx_contract` asserts `embedding_rows == len(vocab) == OnnxModelSpec.vocabSize` | no magic number; Kotlin `vocabSize` hand-synced to it |
| Output | `logits`, **float `[1, 7]`** | read by index 0 on the Kotlin side (name-independent) |
| Label order (argmax index → class) | `0 LAUNCH_APP, 1 SEARCH, 2 OPEN_SETTINGS, 3 SHOW_APPS, 4 HELP, 5 OPEN_ASSISTANT, 6 UNKNOWN` | **must match training**; mirrored in `NluLabel` (Kotlin) |

`CLEAR` is **not** a class (rule-only). Slots are **heuristic** (verb/filler strip in the pure
Kotlin mapper), not modeled — joint intent+slot is frozen-forward (Phase 7+).

## Label → LauncherIntent map (implemented in the pure Kotlin `IntentLabelMapper`)

```
LAUNCH_APP     -> LaunchAppIntent(displayNameQuery = <heuristic slot>)
SEARCH         -> SearchIntent(query = <heuristic slot>, target = WEB)
OPEN_SETTINGS  -> OpenSettingsIntent()
SHOW_APPS      -> SimpleCommandIntent(SHOW_APPS)
HELP           -> SimpleCommandIntent(HELP)
OPEN_ASSISTANT -> SimpleCommandIntent(OPEN_ASSISTANT)
UNKNOWN        -> UnknownIntent
```

## Scripts

| Script | Deps | Purpose |
|---|---|---|
| `gen_golden_vectors.py` | **stdlib only** | independent reference WordPiece (HF `BertTokenizer` algorithm) → `golden_tokenization.json`; the Kotlin tokenizer is asserted byte-exact against it. Runs now, offline. |
| `make_placeholder_model.py` | `pip install onnx numpy` | builds a tiny **valid** `intent.onnx` with the **exact** names/dtypes/axes above (3×int64 `[1,48]` in, float `[1,7]` out) for the `androidTest` to load on-device before the real model is trained. |
| `train_export.py` | `pip install torch transformers onnx onnxruntime datasets` | fine-tune → dynamic-int8 → ONNX export the real model + emit `vocab.txt`; gates on a min-accuracy threshold + a reviewed confusion matrix. Requires GPU/network. |

## Quality gate (do NOT ship "whatever trained")

NLU is consulted **only** on hard/ambiguous inputs (the easy cases are taken by the `<10ms` rule
path) — exactly where a weak model on a tiny dataset fails. `train_export.py` therefore:
augments the dataset with `verb × app-slot` templates, holds out an eval split, and **gates model
fitness on a minimum accuracy threshold + a reviewed confusion matrix** before emitting an artifact.

## Device-pending (like Block J)

This environment has no `torch`/`transformers`/`onnx`/network, so the **real** `intent.onnx` +
the pruned multilingual `vocab.txt` (~20–30k, en/ar/tr/ru) + HF-regenerated golden vectors are a
**device-pending acceptance item**.
The mini-vocab golden set (`vocab.mini.txt` + `golden_tokenization.json`) validates the Kotlin
tokenizer's algorithm now, offline. When the real artifact lands: regenerate the golden set from
the real HF `BertTokenizer` over the real `vocab.txt`, drop `intent.onnx`/`vocab.txt` into the
`androidTest` assets, and run P5 on the SM-A325F.
