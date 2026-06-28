#!/usr/bin/env python3
"""
Block P / P0 — real model: fine-tune -> dynamic-int8 -> ONNX export, with a quality gate.

Authors a small labeled launcher-command dataset across the 7 classes (augmented with
verb x app-slot templates), fine-tunes a BERT-Mini / TinyBERT-4L encoder for 7-class sequence
classification, holds out an eval split, GATES on a minimum accuracy threshold + a printed
confusion matrix, dynamic-int8 quantizes, and exports ONNX matching the pinned contract
(tools/nlu/README.md): 3x int64 [1,32] inputs, float [1,7] output, opset <= 22, label order
LAUNCH_APP, SEARCH, OPEN_SETTINGS, SHOW_APPS, HELP, OPEN_ASSISTANT, UNKNOWN.

Also re-emits the golden tokenizer vectors from the REAL HuggingFace BertTokenizer over the real
vocab.txt (replacing the mini-vocab placeholder golden set) so the Kotlin tokenizer test asserts
against authoritative references.

Requires GPU/network:
  pip install torch transformers onnx onnxruntime datasets evaluate
  python3 tools/nlu/train_export.py --out tools/nlu/

This environment has no torch/transformers/network, so running this is device-pending (Block J
precedent). The script + README pin the contract; the runtime code is built against it.
"""
import argparse

BASE_MODEL = "google/bert_uncased_L-4_H-256"  # BERT-Mini class
MAX_LEN = 32
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


def assert_onnx_contract(path):
    """Hard-fail the export unless the .onnx matches the contract the Kotlin side builds against.

    The placeholder model matching the contract does NOT prove the real export matches — verify the
    REAL artifact here so a mismatch fails at export time, not silently on-device (a wrong/missing
    input or output shape makes session.run throw → permanent rule-fallback).
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
    print(f"contract OK: {path} matches pinned I/O (inputs={list(inputs)}, output={only.name}{out_shape})")


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
    #   tok = AutoTokenizer.from_pretrained(BASE_MODEL)              # uncased WordPiece, 30522
    #   model = AutoModelForSequenceClassification.from_pretrained(
    #       BASE_MODEL, num_labels=len(LABELS), id2label=dict(enumerate(LABELS)))
    #   ds = build_dataset(); split into train/eval (stratified)
    #   Trainer(...).train()
    #   acc, confusion = evaluate(model, eval_ds)
    #   assert acc >= MIN_ACCURACY, f"accuracy {acc} < gate {MIN_ACCURACY}; do not export"
    #   print(confusion)  # review by hand before shipping
    #   torch.onnx.export(model, dummy([1,MAX_LEN] int64 x3), f"{out}/intent.fp32.onnx",
    #       input_names=["input_ids","attention_mask","token_type_ids"],
    #       output_names=["logits"], opset_version=OPSET,
    #       dynamic_axes=None)  # fixed [1,32] per the pinned contract
    #   from onnxruntime.quantization import quantize_dynamic, QuantType
    #   quantize_dynamic(f"{out}/intent.fp32.onnx", f"{out}/intent.onnx", weight_type=QuantType.QInt8)
    #   assert_onnx_contract(f"{out}/intent.onnx")   # <-- HARD-FAIL if the export drifts from the contract
    #   tok.save_vocabulary(out)  # -> vocab.txt (30522)
    #   regenerate golden vectors from `tok` (real BertTokenizer) over the real vocab.txt
    raise SystemExit(
        "train_export.py is a device-pending pipeline: needs torch/transformers/onnx + GPU/network. "
        "See the pseudocode in this file (incl. the mandatory assert_onnx_contract step) and "
        "tools/nlu/README.md for the pinned contract."
    )


if __name__ == "__main__":
    main()
