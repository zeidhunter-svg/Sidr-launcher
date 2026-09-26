# Stage 2 — execution prompt: compress → quantize → export the NLU model (Phase 6, offline track)

> Produces the **shipped** artifact: a small multilingual intent classifier (`intent.onnx`, int8) + its **pruned**
> `vocab.txt` + regenerated golden vectors, from the Stage-1 dataset and the OQ#1-amendment decisions. This is the
> ML-heavy step. Runs on a machine with **Python + torch + transformers + onnx + onnxruntime + network** (NOT the Kotlin
> container, NOT the phone). It's a reproducible pipeline under `tools/nlu/`.
>
> **Model:** the correctness-critical parts — **logits-vs-probs output**, **tokenizer-normalization parity**, the
> **vocab↔embedding↔`vocab.txt`-line invariant**, and **no double softmax** — silently ship a broken model if wrong; do
> them on **Opus 4.8**. Script-filling/orchestration may run on **Sonnet 4.6**. Review on Opus.
> **Source of truth:** `IntentLabelMapper`, `WordPieceTokenizer`, `OnnxModelSpec`, `assert_onnx_contract`, the Stage-1
> dataset + its `DATASET_CARD.md`, and the OQ#1-amendment + Block P/Q ADRs. Where this prompt and the source/contract
> disagree, the contract wins.
> **Hard boundary:** this stage does NOT host the model or pin `ModelDownloadConfig` (Stage 5), does NOT run on the device
> (Stage 6), and makes **no Kotlin change beyond reporting the final `OnnxModelSpec` values** (and optionally landing that
> one-line constant). It produces artifacts + a model card + a device-benchmark harness.

---

## 0. Preconditions (verify both; do not assume)
1. **Stage 1 dataset exists + is frozen** — multilingual `en/ar/tr/ru`, per (class × language), leakage-free train/val/test
   splits, validated, with a `DATASET_CARD.md` that states the **accuracy gate** (overall X%, per-language floor Z%,
   ambiguous subset Y%). If the splits aren't frozen or the gate isn't written, **stop**.
2. **OQ#1 amendment landed** — multilingual WordPiece **teacher** chosen, `max_len = 48`, `vocabSize` is a data-driven
   `TODO(Stage2)` placeholder, prune+distill+int8 mandated, `<150ms` is a hard gate, non-transformer is a documented
   fallback. If the contract still says 30522/en/max_len-32, the amendment didn't land — **stop and escalate**.

## 0.5 Execution environment (this is a small task — size expectations accordingly)
This is a **tiny model on a tiny dataset** (7 classes, ~7–15k short examples, a 2–4-layer student) — minutes of GPU, not days.
- **Default to a free Colab T4 (16GB VRAM)** — the whole pipeline (teacher pass + distill + quantize + export + goldens) fits
  in one session. Pin deps + seed so it reproduces from a clean runtime.
- **A 6GB local GPU is enough for the student** (4-layer, hidden 256–384, `max_len 48`, batch 8–16 fp16 ≈ 1–2.5 GB). What is
  **tight** on 6GB is a **full fine-tune of the ~168M mBERT teacher** (optimizer states + activations). So on constrained
  local hardware, prefer **distilling from the off-the-shelf / lightly-adapted teacher** over a heavy teacher fine-tune; if a
  teacher fine-tune is needed, use batch 4–8 + gradient accumulation + fp16 + gradient checkpointing — or just run that step
  on Colab. The student step is never the memory problem.
- Quantization + ONNX export + vocab-pruning are CPU-minutes; no GPU needed.

## 1. Pre-flight — contract truth (codegraph + reads, BEFORE building)
Even though the work is Python, the **shipped artifact must satisfy the Kotlin runtime contract** — read it from source:
- [ ] **`IntentLabelMapper`** — the **7 labels + exact index order** (output `[1,7]` argmax index → label), AND the single
      most important question: **does it expect raw logits or probabilities?** Block P applies softmax→argmax→floor itself,
      so the model must output **raw logits `[1,7]`** with **no softmax/log-softmax in the ONNX graph** — exporting a
      softmax layer causes a **double softmax** that distorts confidences and breaks the `confidenceFloor`. Confirm this.
      Also read `OnnxModelSpec.confidenceFloor` (the model's calibration must make it meaningful — §8 calibration).
- [ ] **`WordPieceTokenizer`** — the exact **normalization** (lowercasing, accent/diacritic handling, the **Turkish i/ı,
      İ/I** rule, `##` continuation, `[UNK]`, `max_len` pad/truncate). The HF tokenizer used at train/export time MUST
      normalize **identically**, or golden vectors diverge and on-device tokenization is wrong (§7).
      - **Cased-vs-uncased is a Stage-2 decision to MEASURE, not inherit (OQ#1 carry-forward).** The amendment shipped
        `bert-base-multilingual-uncased` for blast-radius (matches the current tokenizer), but Google recommends the **cased**
        mBERT for non-Latin (Arabic) + Turkish, and uncased lowercasing is **lossy for Turkish İ/ı**. So: train/evaluate
        **both** and pick on the **Arabic + Turkish val subsets** (§8). If cased wins, the cost is bounded — flip
        `do_lower_case=false`, add the matching `WordPieceTokenizer` toggle, and **regenerate goldens** (§7). Record which was
        chosen + the per-language numbers that decided it. Do **not** default to uncased by inertia.
- [ ] **`OnnxModelSpec`** — input names (`input_ids`, `attention_mask`, `token_type_ids`?), dtypes (**int64**), shapes
      (`[1,48]`), output shape `[1,7]`, **opset**, the `vocabSize` placeholder to fill. Prefer a **2-input** model
      (`input_ids`+`attention_mask`); only emit `token_type_ids` if the student genuinely uses it (Block P supplies it
      only when `session.inputNames` declares it).
- [ ] **`assert_onnx_contract`** — exactly what it checks (data-driven vocab size, names/dtypes/shapes). The export must pass it.
- [ ] **ORT compatibility** — confirm the export **opset is supported by the on-device ONNX Runtime 1.20.0** (Block P's
      pinned AAR). Confirm the quantization scheme loads in ORT 1.20 mobile (docs/context7 — don't assume).
- [ ] **`tools/nlu/`** — the Block-P0 train/export script placeholders + golden generator + the Stage-1 dataset path/format
      and `DATASET_CARD` gate values.

## 2. Pipeline shape (reproducible, in `tools/nlu/`)
Deliver **scripts, not a notebook**: `prune_vocab.py`, `train_student.py` (distillation), `quantize_export.py`, `eval.py`,
`gen_goldens.py`, plus a `run.sh`/`Makefile` orchestrating them end-to-end, **pinned dependency versions**, and a **fixed
random seed**. The accuracy numbers + goldens must be reproducible from a clean checkout.

## 3. Step A — vocabulary pruning
- [ ] Tokenize the **entire training corpus** (all 4 languages + domain) with the teacher tokenizer; collect the set of used
      token IDs. New vocab = used tokens **+ all special tokens** (`[PAD] [UNK] [CLS] [SEP] [MASK]`) **+ full
      single-character coverage for Latin/Cyrillic/Arabic** (so any unseen word degrades to characters, not `[UNK]`) **+ a
      frequency-floor margin**. Don't over-prune — aggressive pruning inflates `[UNK]` on *unseen* user commands.
- [ ] Remap the embedding table: `new_embedding[i] = teacher_embedding[old_id_of_token_i]`. Write the pruned `vocab.txt`
      with **line order == new token IDs**.
- [ ] **Invariant to enforce + document:** `vocab.txt` line N ↔ embedding row N ↔ the ID the tokenizer emits for that token.
      A mismatch here is a silent, total failure. Add an assertion.
- [ ] Validate `[UNK]` rate on a **held-out** set (not train) per language; if high, relax the prune. Record the final
      vocab size — this becomes `OnnxModelSpec.vocabSize`.

## 4. Step B — distillation / student training
- [ ] Establish a teacher: fine-tune the **pruned-vocab teacher** on the dataset → accuracy **ceiling** + KD teacher. **On a
      6GB local GPU this is the one tight step** (§0.5) — run it on Colab, or mitigate (batch 4–8 + grad-accum + fp16 +
      checkpointing), or, if a directly-trained small model already nears the gate, skip the heavy teacher fine-tune and
      distill from the lightly-adapted teacher.
- [ ] Distill into a **small student** (~2–4 layers, hidden 256–384): KD loss = soft teacher logits (temperature) + hard
      dataset labels; optionally TinyBERT-style intermediate matching. Single-segment classification → student should not
      need `token_type_ids`.
- [ ] **Run this whole step for both cased + uncased teachers if undecided (§1)** — the cased/uncased pick is made on the
      per-language val numbers in §8, not assumed.
- [ ] If a directly-fine-tuned small pruned model already meets the gate, distillation is the lever to push further — use it
      if the student underperforms. Pin seed; log per-language val accuracy each epoch.

## 5. Step C — int8 quantization
- [ ] Quantize the student to **int8** (`onnxruntime.quantization`). Try **dynamic** int8 first (no calibration set); if
      latency still misses, use **static** int8 with a calibration subset (faster on-device).
- [ ] **Re-measure the accuracy gate on the QUANTIZED model** — the shipped artifact is the quantized one; quantization can
      drop accuracy. The gate is evaluated post-quant, not on the float model.

## 6. Step D — ONNX export + contract assertion
- [ ] Export the quantized student to ONNX at the confirmed **opset (ORT-1.20-compatible)**; inputs `input_ids`(+`attention_mask`)
      **int64 `[1,48]`** fixed; output **raw logits `[1,7]`** — **no softmax in the graph** (§1). Do not emit `token_type_ids`
      unless used.
- [ ] Run **`assert_onnx_contract(intent.onnx, vocab.txt)`** against the **final exported pruned artifact** (not the teacher /
      a partially-pruned model). It hard-fails on `embedding_rows == len(vocab.txt)` + names/dtypes/shapes/`[1,7]`.
      **Two recorded carry-forwards (decisions.md items 7 + 8) — honor them here:**
      - The `== OnnxModelSpec.vocabSize` term is **not** auto-enforced — `vocabSize` is a **manual Kotlin copy guarded by a
        `print`**. Implement/use the **`--expected-vocab-size` CLI guard**: pass the Kotlin `vocabSize` and have the assert
        hard-fail on mismatch (skips on the `VOCAB_SIZE_PENDING` sentinel), closing the manual-copy gap without parsing
        `OnnxModelSpec.kt`.
      - **Sanity-bound the pruned size to ~20–30k** and reject a ~110k-row table — `rows == len(vocab)` alone goes **falsely
        green** if run against the un-pruned teacher (both agree there). Fail loudly if the size looks un-pruned.
- [ ] Set the real pruned line count as `OnnxModelSpec.vocabSize` (the manual copy) and re-run the guard to confirm
      model ↔ vocab ↔ Kotlin-constant all agree.

## 7. Step E — tokenizer parity + golden vectors (the on-device bridge)
- [ ] Regenerate goldens (`gen_goldens.py`) with the **pruned vocab + the exact normalization config** matching
      `WordPieceTokenizer`, over a fixed phrase set covering **all 4 languages** + edge cases: `##` continuations, `[UNK]`,
      **Turkish i/ı + İ/I**, Arabic (RTL/clitics), accented Latin, and a phrase that **truncates at 48**.
- [ ] Confirm the Kotlin `WordPieceTokenizer` reproduces the goldens **byte-exact** (the Block-P0 golden test, now fed the
      real pruned vocab). **Any divergence = a normalization mismatch between HF and Kotlin → fix it (or escalate to a P
      tokenizer fix); do NOT paper over it.** This is what proves training-time tokenization == on-device tokenization.

## 8. Step F — gates (go/no-go)
- [ ] **Accuracy gate (on the quantized model):** overall top-1 ≥ X, **each language ≥ Z** (a weak language must not hide
      behind a strong average), ambiguous subset ≥ Y — per the `DATASET_CARD`.
- [ ] **Calibration (tie to `confidenceFloor` + Block R):** a distilled model is often overconfident, which makes the 0.60
      floor and R's suggest-band meaningless. Do a quick **temperature scaling** on val so softmax confidences are
      reasonable; record the temperature (fold into the model or document it for the Kotlin side). Confirm the floor still
      separates real answers from escapes.
- [ ] **Latency:** the hard `<150ms` CPU gate is ultimately a **device measurement (Stage 6)**. Now: produce a **proxy
      estimate** (ORT CPU on a comparable ARM env / emulator / desktop, clearly labelled as a proxy) for early go/no-go, and
      **deliver an on-device benchmark harness** (adb-runnable or a tiny androidTest) so Stage 6 just runs it. If the proxy
      is already far over (e.g. ≫150ms), iterate before going to device.

## 9. If the gates fail — bounded iteration, then escalate (do NOT ship a contract-break)
- [ ] Iterate within the WordPiece path: vocab size, student depth/width, distill epochs/temperature, dynamic↔static quant.
- [ ] **If accuracy AND/OR latency still can't be met:** stop and write a **failure report** (measured numbers + what was
      tried). The non-transformer fallback (char/byte-CNN, fastText-style) is **NOT a Stage-2 drop-in** — it abandons
      WordPiece, so it **reworks P's tokenizer/export** and is an **OQ#1 re-decision**. Escalate it; do **not** silently
      produce a non-WordPiece artifact the Kotlin `WordPieceTokenizer` can't feed.

## 10. Deliverables + feedback
- `intent.onnx` (int8), the pruned `vocab.txt`, the regenerated goldens (consumed by the P0 golden test).
- `prune_vocab.py` / `train_student.py` / `quantize_export.py` / `eval.py` / `gen_goldens.py` + `run.sh`/`Makefile` + pinned
  deps + seed.
- On-device latency **benchmark harness** for Stage 6.
- `MODEL_CARD.md`: teacher (`bert-base-multilingual-uncased` ~168M / ~110k vocab, **or** cased if §8 chose it), the
  **cased-vs-uncased decision + the ar/tr val numbers that decided it**, prune stats (vocab ~110k→N, held-out `[UNK]` rate),
  student config, distill + quant settings, **per-language accuracy** on the quantized model, calibration temperature, proxy
  latency, and the final **`vocabSize` + `maxLen`** to write into `OnnxModelSpec`.
- Report the `OnnxModelSpec` values back (and optionally land that one-line constant edit, flagged) so the placeholder is
  resolved. Confirm `assert_onnx_contract` passes.

## 11. Acceptance
- Pruned `vocab.txt` produced; the **vocab↔embedding↔line-index invariant** holds (asserted).
- Output is **raw logits `[1,7]`** (no baked softmax), matching `IntentLabelMapper`; `assert_onnx_contract` passes; opset
  loads in ORT 1.20.
- Goldens regenerated; Kotlin `WordPieceTokenizer` reproduces them **byte-exact**, incl. the Turkish-i / Arabic / accent /
  truncation cases.
- Accuracy gate met **on the quantized model** (overall + per-language floors + ambiguous); calibration done.
- **Cased-vs-uncased decided on the ar/tr val numbers** (not inherited); if cased chosen, the tokenizer toggle + golden regen
  are done. The `--expected-vocab-size` guard + the ~20–30k sanity bound both pass against the final pruned artifact.
- Proxy latency recorded + device-benchmark harness delivered; the hard `<150ms` gate is device-pending (Stage 6).
- Reproducible pipeline (scripts + pinned deps + seed); `MODEL_CARD`; `OnnxModelSpec` values reported.
- On gate failure: a failure report + escalation, **not** a silently-shipped contract-breaking artifact.

## 12. Non-goals
No hosting / SHA-256 pin / `ModelDownloadConfig` (Stage 5); no committing the vocab asset into the Android module (Stage 4
uses the produced file); no device run (Stage 6); no Kotlin change beyond the reported `OnnxModelSpec` constant; no
non-WordPiece fallback artifact without an explicit OQ#1 re-decision (§9).
