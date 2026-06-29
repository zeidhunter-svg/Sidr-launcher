#!/usr/bin/env python3
"""
Block P / P0 — placeholder ONNX model with the EXACT runtime contract.

Builds a tiny but *valid* `intent.onnx` whose input/output **names, dtypes and axes** match the
real model exactly (see tools/nlu/README.md), so the :data:ai-local androidTest (P5) can load it,
run a session, and exercise the NNAPI->CPU path on-device BEFORE the real model is trained. The
math is meaningless (a fixed linear layer) — only the I/O contract matters here. Swapping in the
real model must not require any Kotlin change because the contract is identical.

Inputs : input_ids, attention_mask, token_type_ids  -> int64  [1, 48]  (OQ#1 multilingual: maxLen 48)
Output : logits                                      -> float  [1, 7]

Run:
  pip install onnx numpy
  python3 tools/nlu/make_placeholder_model.py
Writes:
  tools/nlu/intent.placeholder.onnx
  (copy into data/ai-local/src/androidTest/assets/nlu/intent.onnx for P5; device-pending)

This environment has no `onnx` package, so producing the binary here is device-pending (Block J
precedent). The script is the source of truth for the placeholder's shape.
"""
import os

MAX_LEN = 48  # OQ#1 multilingual amendment (was 32); matches OnnxModelSpec.maxLen
NUM_LABELS = 7
INPUTS = ["input_ids", "attention_mask", "token_type_ids"]


def main():
    import numpy as np
    import onnx
    from onnx import TensorProto, helper, numpy_helper

    # Graph: take input_ids, cast to float, matmul with a fixed [MAX_LEN, NUM_LABELS] weight,
    # producing logits [1, NUM_LABELS]. attention_mask / token_type_ids are declared inputs but
    # unused by the math (still part of the contract so the Kotlin side must feed them).
    ids = helper.make_tensor_value_info("input_ids", TensorProto.INT64, [1, MAX_LEN])
    mask = helper.make_tensor_value_info("attention_mask", TensorProto.INT64, [1, MAX_LEN])
    types = helper.make_tensor_value_info("token_type_ids", TensorProto.INT64, [1, MAX_LEN])
    logits = helper.make_tensor_value_info("logits", TensorProto.FLOAT, [1, NUM_LABELS])

    w = numpy_helper.from_array(
        (np.random.RandomState(0).randn(MAX_LEN, NUM_LABELS) * 0.01).astype(np.float32), name="W"
    )

    cast = helper.make_node("Cast", ["input_ids"], ["ids_f"], to=TensorProto.FLOAT)
    matmul = helper.make_node("MatMul", ["ids_f", "W"], ["logits"])

    graph = helper.make_graph([cast, matmul], "placeholder_intent", [ids, mask, types], [logits], [w])
    # opset 17 is comfortably <= 22 and supported by ORT 1.20.0
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 17)])
    model.ir_version = 9
    onnx.checker.check_model(model)

    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "intent.placeholder.onnx")
    onnx.save(model, out)
    print("wrote", out)


if __name__ == "__main__":
    main()
