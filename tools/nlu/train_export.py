#!/usr/bin/env python3
"""
Block P / P0 — real model: compress a multilingual teacher -> dynamic-int8 -> ONNX export, gated.

OQ#1 amended (multilingual, 2026-06-29): target languages en/ar/tr/ru. The shipped model is a
COMPRESSED multilingual WordPiece teacher, not a naive mBERT (which won't hit <150ms on SM-A325F):
  1. vocab-PRUNE the teacher embedding table to the tokens occurring in en/ar/tr/ru + the launcher
     command domain (~110k -> ~20-30k rows) — the dominant size lever; tokenizer algorithm unchanged;
  2. layer-DISTILL to a small (~2-4 layer) student — the latency lever;
  3. dynamic-INT8 -> final footprint.
<150ms on the SM-A325F is a HARD Stage-2 go/no-go gate: if prune+distill+int8 of the WordPiece path
can't meet it, fall back to a non-transformer classifier (char/byte-CNN or fastText-style subword)
rather than ship a too-slow model. SentencePiece/XLM-R is rejected (would rewrite WordPieceTokenizer).

Authors a small labeled launcher-command dataset across the 7 (language-independent) classes,
holds out an eval split, GATES on a minimum accuracy threshold + a printed confusion matrix, and
exports ONNX matching the pinned contract (tools/nlu/README.md): 3x int64 [1,MAX_LEN] inputs,
float [1,7] output, opset <= 22, label order
LAUNCH_APP, SEARCH, OPEN_SETTINGS, SHOW_APPS, HELP, OPEN_ASSISTANT, UNKNOWN.

Also re-emits the golden tokenizer vectors from the REAL HuggingFace BertTokenizer over the pruned
multilingual vocab.txt (replacing the mini-vocab placeholder golden set) so the Kotlin tokenizer
test asserts against authoritative references.

Requires GPU/network:
  pip install torch transformers onnx onnxruntime datasets evaluate
  python3 tools/nlu/train_export.py --out tools/nlu/

This environment has no torch/transformers/network, so running this is device-pending (Block J
precedent). The script + README pin the contract; the runtime code is built against it.
"""
import argparse

# Multilingual WordPiece TEACHER (uncased — matches WordPieceTokenizer's lower+NFD normalization,
# so no tokenizer change). The SHIPPED model is this teacher pruned+distilled+int8 (see module doc),
# NOT this checkpoint as-is.
BASE_MODEL = "google-bert/bert-base-multilingual-uncased"  # ~110k WordPiece vocab; teacher only
MAX_LEN = 48  # provisional (en/ar/tr/ru fragment more per word than en); finalize at dataset time
OPSET = 17  # <= 22, supported by ORT 1.20.0
MIN_ACCURACY = 0.90  # quality gate; do NOT export below this
LABELS = ["LAUNCH_APP", "SEARCH", "OPEN_SETTINGS", "SHOW_APPS", "HELP", "OPEN_ASSISTANT", "UNKNOWN"]

# Seed templates; expand with real verb x app-slot augmentation before training.
TEMPLATES = {
    "LAUNCH_APP": ["open {app}", "launch {app}", "fire up {app}", "go to {app}", "{app}"],
    "SEARCH": ["search {q}", "google {q}", "look up {q}", "find {q}", "search for {q}"],
    "OPEN_SETTINGS": ["settings", "open settings", "device settings", "system settings"],
    "SHOW_APPS": ["show apps", "all apps", "app drawer", "list apps"],
    "HELP": ["help", "what can you do", "how do i use this"],
    "OPEN_ASSISTANT": ["assistant", "open assistant", "talk to assistant", "ai chat"],
    "UNKNOWN": ["asdfgh", "blah blah", "the quick brown fox", "12345"],
}
APPS = ["camera", "telegram", "maps", "spotify", "chrome", "whatsapp", "calculator", "clock"]
QUERIES = ["weather", "pizza near me", "python tutorials", "cat videos", "news"]


# The runtime-pinned I/O contract (must equal OnnxModelSpec.DEFAULT + NluLabel order in Kotlin).
EXPECTED_INPUTS = {  # name -> elem_type (TensorProto.INT64 == 7), dims
    "input_ids": (7, [1, MAX_LEN]),
    "attention_mask": (7, [1, MAX_LEN]),
    "token_type_ids": (7, [1, MAX_LEN]),
}
EXPECTED_OUTPUT = ("logits", 1, [1, len(LABELS)])  # FLOAT == 1


def _embedding_rows(model):
    """The vocab dimension of the token-embedding table = first axis of its 2-D initializer.

    Prefer the initializer whose name carries `word_embeddings`; otherwise the widest 2-D table
    (the embedding dominates a small model). Returns None if no 2-D initializer is found.
    """
    best = None
    for init in model.graph.initializer:
        if len(init.dims) != 2:
            continue
        if "word_embeddings" in init.name.lower():
            return init.dims[0]
        if best is None or init.dims[0] > best:
            best = init.dims[0]
    return best


def assert_onnx_contract(path, vocab_path):
    """Hard-fail the export unless the .onnx matches the contract the Kotlin side builds against.

    The placeholder model matching the contract does NOT prove the real export matches — verify the
    REAL artifact here so a mismatch fails at export time, not silently on-device (a wrong/missing
    input or output shape makes session.run throw → permanent rule-fallback).

    DATA-DRIVEN vocab check (OQ#1 multilingual amendment): the pruned vocab size is not a magic
    number — it is derived from the produced `vocab.txt` line count and asserted equal to the
    model's embedding rows. The Kotlin `OnnxModelSpec.vocabSize` MUST be hand-set to the SAME number
    (the Phase-4 `TABLE_NAMES` precedent); this print states the value to copy.
    """
    import onnx

    model = onnx.load(path)
    onnx.checker.check_model(model)
    graph = model.graph

    def dims(vi):
        return [d.dim_value for d in vi.type.tensor_type.shape.dim]

    inputs = {vi.name: vi for vi in graph.input}
    # Allow exactly the 3 expected inputs (token_type_ids may be omitted by some exports, but the
    # pinned contract ships all three; the Kotlin shell only sends token_type_ids if declared).
    for name, (etype, shape) in EXPECTED_INPUTS.items():
        if name == "token_type_ids" and name not in inputs:
            continue
        assert name in inputs, f"missing input '{name}'; got {list(inputs)}"
        vi = inputs[name]
        assert vi.type.tensor_type.elem_type == etype, f"{name} dtype != int64"
        assert dims(vi) == shape, f"{name} shape {dims(vi)} != {shape}"
    unexpected = set(inputs) - set(EXPECTED_INPUTS)
    assert not unexpected, f"unexpected inputs {unexpected} (Kotlin won't feed them)"

    out_name, out_type, out_shape = EXPECTED_OUTPUT
    outs = {vi.name: vi for vi in graph.output}
    # Output is read by index 0 in Kotlin (name-independent), but assert there is exactly one and it
    # has the right dtype/shape.
    assert len(outs) == 1, f"expected exactly one output, got {list(outs)}"
    only = next(iter(outs.values()))
    assert only.type.tensor_type.elem_type == out_type, "output dtype != float"
    assert dims(only) == out_shape, f"output shape {dims(only)} != {out_shape}"

    # Data-driven vocab/embedding cross-check (no hardcoded vocab size).
    vocab_size = sum(1 for _ in open(vocab_path, encoding="utf-8"))
    rows = _embedding_rows(model)
    assert rows is not None, "no 2-D embedding initializer found in the model"
    assert rows == vocab_size, (
        f"embedding rows {rows} != vocab.txt lines {vocab_size}; the tokenizer and model disagree"
    )
    print(
        f"contract OK: {path} matches pinned I/O (inputs={list(inputs)}, output={only.name}{out_shape}); "
        f"vocab_size={vocab_size} — set OnnxModelSpec.vocabSize to this exact value"
    )


def build_dataset():
    rows = []
    for label, tmpls in TEMPLATES.items():
        for t in tmpls:
            if "{app}" in t:
                rows += [(t.format(app=a), label) for a in APPS]
            elif "{q}" in t:
                rows += [(t.format(q=q), label) for q in QUERIES]
            else:
                rows.append((t, label))
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=".")
    ap.parse_args()

    # Pseudocode of the real pipeline (kept terse; fill in when deps/GPU are available):
    #   from transformers import AutoTokenizer, AutoModelForSequenceClassification, Trainer
    #   tok = AutoTokenizer.from_pretrained(BASE_MODEL)              # multilingual uncased WordPiece (~110k)
    #   model = AutoModelForSequenceClassification.from_pretrained(
    #       BASE_MODEL, num_labels=len(LABELS), id2label=dict(enumerate(LABELS)))
    #   --- OQ#1 multilingual compression (the model is NOT shipped as the raw teacher) ---
    #   # 1. PRUNE: restrict tok's vocab to tokens seen in en/ar/tr/ru + the command domain, then
    #   #    slice the embedding rows to match (~110k -> ~20-30k). Re-emit the pruned vocab.txt;
    #   #    its NEW line count is the contract's vocab size (data-driven, no magic number).
    #   # 2. DISTILL the (pruned) teacher into a ~2-4 layer student.
    #   ds = build_dataset(); split into train/eval (stratified)   # multilingual dataset = Stage 1
    #   Trainer(...).train()
    #   acc, confusion = evaluate(model, eval_ds)
    #   assert acc >= MIN_ACCURACY, f"accuracy {acc} < gate {MIN_ACCURACY}; do not export"
    #   print(confusion)  # review by hand before shipping
    #   torch.onnx.export(model, dummy([1,MAX_LEN] int64 x3), f"{out}/intent.fp32.onnx",
    #       input_names=["input_ids","attention_mask","token_type_ids"],
    #       output_names=["logits"], opset_version=OPSET,
    #       dynamic_axes=None)  # fixed [1,MAX_LEN] per the pinned contract
    #   from onnxruntime.quantization import quantize_dynamic, QuantType
    #   quantize_dynamic(f"{out}/intent.fp32.onnx", f"{out}/intent.onnx", weight_type=QuantType.QInt8)
    #   tok.save_vocabulary(out)  # -> pruned multilingual vocab.txt (~20-30k)
    #   assert_onnx_contract(f"{out}/intent.onnx", f"{out}/vocab.txt")  # <-- HARD-FAIL on contract drift
    #   # 3. MEASURE <150ms on SM-A325F (hard go/no-go); if missed -> non-transformer fallback.
    #   regenerate golden vectors from `tok` (real BertTokenizer) over the pruned vocab.txt
    raise SystemExit(
        "train_export.py is a device-pending pipeline: needs torch/transformers/onnx + GPU/network. "
        "See the pseudocode in this file (incl. the mandatory assert_onnx_contract step) and "
        "tools/nlu/README.md for the pinned contract."
    )


if __name__ == "__main__":
    main()
