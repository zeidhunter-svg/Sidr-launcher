# Dataset Card — Sidr Launcher NLU Intent Dataset

> **Stage 1 deliverable** (Phase 6 / OQ#1). This dataset fine-tunes the 7-class intent classifier
> that backs the `OnnxIntentClassifier` (`LayeredIntentMatcher` NLU secondary). No training,
> quantization, or ONNX export happens here — those are Stage 2.

---

## Purpose and role in the pipeline

The Sidr Launcher uses a **rule-first** intent matcher (`LayeredIntentMatcher`). The rule matcher
handles common, explicit commands at high confidence (≥ 0.90) — `"open X"`, `"search X"`,
`"settings"`, `"help"`, etc. — and the NLU model is **only consulted when rule confidence is below
0.50** (the `DefaultIntentConfidencePolicy.suggestThreshold`).

This means the NLU model earns its place on **ambiguous, conversational, or non-English inputs**:
bare app names, colloquial paraphrases, non-English commands, typos, bare topics. The dataset is
constructed to concentrate value in that ambiguous neighbourhood, deliberately under-representing
rule-canonical forms.

---

## Label contract

**Argmax-index order (MUST match `NluLabel.kt` enum + `train_export.py` LABELS)**

| Index | Label | Maps to |
|-------|-------|---------|
| 0 | `LAUNCH_APP` | `LaunchAppIntent(displayNameQuery = <heuristic slot>)` |
| 1 | `SEARCH` | `SearchIntent(query = <heuristic slot>, target = WEB)` |
| 2 | `OPEN_SETTINGS` | `OpenSettingsIntent()` |
| 3 | `SHOW_APPS` | `SimpleCommandIntent(SHOW_APPS)` |
| 4 | `HELP` | `SimpleCommandIntent(HELP)` |
| 5 | `OPEN_ASSISTANT` | `SimpleCommandIntent(OPEN_ASSISTANT)` |
| 6 | `UNKNOWN` | `UnknownIntent` → confidence-escape, falls back to rule |

`CLEAR` is intentionally absent — it is rule-only (fixed phrase in `SIMPLE_COMMANDS`).
`labels.json` encodes this mapping; `validate.py` asserts it equals the Kotlin mapper's order.

---

## Class definitions and boundaries

### LAUNCH_APP (index 0)
**In:** "Open a specific installed app" — bare app names (`"telegram"`, `"واتساب"`), polite
requests, directional phrasings, typos, conversational forms without rule verbs.
**Near confusable:** SEARCH (web search, not an app launch); OPEN_ASSISTANT (the built-in AI
assistant, a specific destination). `"open X"` is handled by the rule at 0.90 — NLU sees only
conversational variants where the rule falls below 0.50.

### SEARCH (index 1)
**In:** "Search the web for something" — question form (`"what is the weather"`), bare topic
(`"pizza near me"`, `"رحلات رخيصة"`), need-phrasing (`"how do I fix"`), any web-lookup intent
without the rule verbs `search/find/google`.
**Near confusable:** LAUNCH_APP (app names vs. search topics); UNKNOWN (world-knowledge questions
the launcher can't actually answer — the distinction is whether the launcher *routes* the query to
a search engine).

### OPEN_SETTINGS (index 2)
**In:** "Open the phone's system settings" — the word for "settings" in any of the four languages,
contextual phrasings (`"I want to change my WiFi"`), specific setting categories (Bluetooth,
brightness, sound, notifications, display, WiFi).
**Near confusable:** LAUNCH_APP (`"settings app"` could parse as launch; the rule already nails
bare `"settings"` at 0.95, so NLU sees contextual/non-English phrasings).

### SHOW_APPS (index 3)
**In:** "Show all installed apps / app drawer" — bare `"apps"` in any language, `"app drawer"`,
`"show me everything"`, requests to list or find an app.
**Near confusable:** LAUNCH_APP (finding one specific app vs. listing all apps); UNKNOWN (generic
"show me something" without app context).

### HELP (index 4)
**In:** "Get help using the launcher" — expressions of confusion, questions about what commands
work, requests for a guide, `"what can you do"`, equivalents in all four languages.
**Near confusable:** OPEN_ASSISTANT (asking the AI assistant for general help vs. asking the
launcher itself for usage instructions). The rule nails bare `"help"` at 0.95; NLU sees
variations.

### OPEN_ASSISTANT (index 5)
**In:** "Switch to AI chat / open the built-in assistant" — bare `"assistant"` / `"AI"` /
`"chatbot"` / `"ذكاء اصطناعي"` / `"yapay zeka"` / `"ИИ"`, requests to chat with AI, switch to
AI mode.
**Near confusable:** HELP (AI help vs. launcher usage help); LAUNCH_APP (`"open assistant"` →
rule routes as `LaunchAppIntent(query="assistant")` at 0.90 — NLU sees bare references and
conversational phrasings).

### UNKNOWN (index 6)
**In:** Anything NOT a launcher command — alarms (`"set alarm for 7am"`), phone calls
(`"call mom"`), math (`"2+2"`), world-knowledge trivia (`"capital of France"`), translations
(`"translate hello"`), music without a named app (`"play music"`), social greetings
(`"good morning"`), gibberish. This class must be **diverse and substantial in every language** —
an under-represented UNKNOWN causes the model to force-fit everything into real intents, defeating
the confidence-escape mechanism.
**Near confusable:** all real intents (many UNKNOWN examples look superficially similar to search
queries or app launches — the boundary is whether the launcher can meaningfully route the command).

---

## Language scope

| Language | Script | Notes |
|----------|--------|-------|
| **en** — English | Latin | Baseline |
| **ar** — Arabic | RTL Arabic | Spoken dialect (Egyptian/Gulf/Levantine), NOT formal MSA; rich clitic morphology |
| **tr** — Turkish | Latin | Agglutinative — suffix stacks drive max_len; Turkish-I problem (ı/i, İ/I) addressed by using the `bert-base-multilingual-uncased` base (lossy lowercasing) at Stage 2 |
| **ru** — Russian | Cyrillic | Solid mBERT-family coverage; informal colloquial included |

European languages deferred (addable later with zero contract change).

**Raw casing is preserved** — no pre-lowercasing in the dataset. The tokenizer applies NFD +
lowercase at train/inference. Keeping raw casing preserves the Stage-2 option of switching to the
**cased mBERT base** (recommended by Google for Arabic + Turkish accuracy) without re-authoring
any examples.

Arabic and Turkish validation subsets are sized to measure cased-vs-uncased accuracy at Stage 2
(`≥ 10` examples per (class × language) in the held-out test split).

### Turkish-I gotcha (critical for Stage 2)
`bert-base-multilingual-uncased` applies locale-naive lowercasing: `"İSTANBUL"` → `"i̇stanbul"`
(wrong) rather than `"istanbul"`. If Stage-2 accuracy on Turkish is low, evaluate
`bert-base-multilingual-cased` — the dataset preserves raw casing, so no re-authoring is needed.

### Arabic dialect note
Real users of an Android launcher type in spoken dialect, not MSA. The seed set and generation
prompts target Gulf/Khaleeji, Egyptian, and Levantine dialect. Examples with MSA phrasing are
marked but not excluded; a native-speaker review pass per language is the quality signal (see
Review status below).

---

## Dataset statistics (2026-06-29)

### Counts per (class × language)

|              | en  | ar  | tr  | ru  | total |
|--------------|-----|-----|-----|-----|-------|
| LAUNCH_APP   | 127 | 161 | 165 | 152 | 605   |
| SEARCH       | 147 | 182 | 171 | 158 | 658   |
| OPEN_SETTINGS| 105 | 141 | 150 | 137 | 533   |
| SHOW_APPS    | 134 | 161 |  82 |  78 | 455   |
| HELP         |  91 |  97 |  92 |  92 | 372   |
| OPEN_ASSISTANT|  86 |  90 |  84 |  85 | 345   |
| UNKNOWN      |  94 |  95 |  94 |  80 | 363   |
| **TOTAL**    |**784**|**927**|**838**|**782**|**3331**|

### Splits

| Split | Count | Notes |
|-------|-------|-------|
| train | 1783 | Seeds + most generated examples |
| val   | 804  | ≥ 10 per (class × language); no family leakage from train |
| test  | 744  | ≥ 10 per (class × language); frozen; family-key disjoint from train |
| **total** | **3331** | |

---

## Generation method

### Seed set (source=`seed`, reviewed=`true`)
- **288 examples** hand-authored natively in each language (en/ar/tr/ru).
- Focus: ambiguous neighborhood — bare app names, conversational paraphrases, non-English,
  realistic typos. Rule-canonical forms deliberately under-represented.
- All seeds assigned to train.

### Expanded examples (source=`generated`, reviewed=`false`)
- Generated via **OpenRouter** (`https://openrouter.ai/api/v1`), model per language:

| Language | Primary model | Secondary model |
|----------|--------------|-----------------|
| ar | `qwen/qwen3-235b-a22b` | `google/gemini-2.5-flash` |
| tr | `qwen/qwen3-235b-a22b` | `google/gemini-2.5-flash` |
| en | `google/gemini-2.5-flash-lite` | `google/gemma-3-27b-it` |
| ru | `google/gemini-2.5-flash-lite` | `google/gemma-3-27b-it` |

- 4 API calls per (class × language) cell, alternating primary/secondary for diversity.
- Prompted with language-native examples + explicit instruction to produce idiomatic, non-translated
  output; Arabic prompts specifically requested spoken dialect.
- `qwen/qwen3-30b-a3b` was tested for en/ru but returned `content: null` (hidden thinking mode on
  the OpenRouter provider route) and was replaced.
- **Model-generated text is NOT ground truth** — all generated examples carry `reviewed: false`.

### Review status

| Language | Seed reviewed | Generated reviewed | Native reviewer |
|----------|--------------|-------------------|-----------------|
| en | ✓ (author) | ✗ | Not assigned |
| ar | ✓ (author) | ✗ | **Strongly recommended** (dialect authenticity, MSA vs. dialect boundary) |
| tr | ✓ (author) | ✗ | **Strongly recommended** (suffix correctness, i/ı boundary) |
| ru | ✓ (author) | ✗ | Recommended |

A human review pass should flip `reviewed: true` and remove or correct misclassified examples
(especially UNKNOWN boundary cases and non-English naturalness). The `family_key` field enables
batch review by generation call.

---

## Split strategy

- **Seed examples** always in train (highest quality, hand-reviewed, small count).
- **Generated examples** grouped by `family_key` (one value per API call batch, ~20 examples/group).
- Within each (class × language) cell, family groups sorted by `family_key` and assigned
  greedily: last groups → test, next → val, rest → train.
- This ensures **no paraphrase family leaks across train and test** — semantically related examples
  from the same generation call always land in the same split.
- **Cross-split dedup**: any text appearing in test/val is removed from train (NFC-normalized).

`validate.py` asserts no `family_key` overlap between train and test.

---

## Validation gates (all pass on 2026-06-29 build)

| Gate | Status | Notes |
|------|--------|-------|
| Label validity (every label ∈ 7 from `IntentLabelMapper`) | ✅ PASS | |
| Lang validity (every lang ∈ `{en,ar,tr,ru}`) | ✅ PASS | |
| Dedup (NFC-normalised, cross-split) | ✅ PASS (1 warning) | `"telegram"` appears in both en and tr seeds (bare app name is same text across languages — OK, dedup retains one; second occurrence is a generated example) |
| Token length ≤ 48 wordpieces | ✅ PASS | Word-count proxy used (no vocab); exact check pending real `vocab.txt` |
| Per-language [UNK] ratio | ⏭ SKIPPED | Requires `--vocab`; run after Stage-2 pruned vocab is produced |
| Balance per (class × language) ≥ 30 (≥ 20 for UNKNOWN) | ✅ PASS | Min cell: SHOW_APPS/ru = 78 |
| No language dominance > 85% in any class | ✅ PASS | |
| Family-key leakage: no train/test overlap | ✅ PASS | |
| Per-cell quota in test/val ≥ 10 | ✅ PASS | |
| `labels.json` index order = `NluLabel.kt` enum = `train_export.py` LABELS | ✅ PASS | |

Run `validate.py --vocab <path/to/vocab.txt>` once the real pruned multilingual vocab is available
(Stage 2) to complete Gates 4 (exact) and 5 (UNK ratio).

---

## Stage-2 accuracy gate (go/no-go for shipping)

The trained + ONNX-exported model must clear these thresholds before `assert_onnx_contract` is
passed and the artifact is shipped:

| Metric | Threshold | Rationale |
|--------|-----------|-----------|
| Overall top-1 accuracy on `test.jsonl` | ≥ **88%** | Matches `train_export.py` `MIN_ACCURACY=0.90` minus 2pp margin for distribution shift |
| Per-language top-1 on test | ≥ **80%** each | No single language can be weak and hide behind a strong average |
| UNKNOWN recall on test | ≥ **85%** | Escape mechanism health — UNKNOWN must fire reliably |
| UNKNOWN precision on test | ≥ **80%** | Must not flag real commands as unknown |
| Ambiguous subset (examples where rule confidence < 0.50) | ≥ **75%** | NLU earns its place exactly here |
| Latency on SM-A325F (CPU, no NNAPI) | **< 150 ms** | Hard Stage-2 go/no-go (Phase 6 spec) |

If the < 150 ms gate is missed after prune+distill+int8, fall back to a non-transformer classifier
(char/byte-CNN or fastText-style subword) as documented in `train_export.py`.

Per-language gate failures trigger:
- ar < 80%: suspect MSA vs. dialect distribution — add native-reviewed dialect examples.
- tr < 80%: suspect Turkish-I casing — evaluate `bert-base-multilingual-cased` (dataset preserves
  raw casing, so no re-authoring needed).

---

## Files in `tools/nlu/data/`

| File | Description |
|------|-------------|
| `intents.jsonl` | Full dataset (3331 examples), one JSON object per line; `split` field set |
| `train.jsonl` | Train split (1783 examples) |
| `val.jsonl` | Validation split (804 examples) |
| `test.jsonl` | Held-out test split (744 examples) — do NOT use for training decisions |
| `labels.json` | Label → index contract; asserted by `validate.py` |
| `DATASET_CARD.md` | This file |

JSONL format per example:
```json
{
  "text": "افتحلي واتساب",
  "label": "LAUNCH_APP",
  "lang": "ar",
  "split": "train",
  "source": "seed",
  "reviewed": true,
  "family_key": "seed_LAUNCH_APP_ar_0013",
  "model": null
}
```

---

## Next steps (Stage 2 / OQ#1 + OQ#2)

1. **Native review pass** (ar, tr, ru especially) — flip `reviewed: true`, fix misclassified examples.
2. **Run `validate.py --vocab vocab.txt`** once the pruned multilingual vocab exists — completes
   token-length (exact) and UNK-ratio gates.
3. **Fine-tune** `bert-base-multilingual-uncased` teacher on `train.jsonl` (see `train_export.py`).
4. **Prune → distill → int8** per the OQ#1 compression spec; assess ONNX latency on SM-A325F.
5. **Run `assert_onnx_contract`** — hard-fails on any I/O shape mismatch.
6. **Evaluate on `test.jsonl`** — must clear all Stage-2 thresholds above before shipping.
7. **OQ#2** — fill `ModelDownloadConfig.INTENT_NLU_PENDING` with the real artifact URL + SHA-256.
