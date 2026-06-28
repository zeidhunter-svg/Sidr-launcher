# Bundled local-NLU assets

`vocab.txt` (BERT uncased WordPiece, 30522 tokens) is bundled here as a static asset (Block Q, §5.D):
small (~200 KB), version-locked to `WordPieceTokenizer`, so it needs no download, no second SHA-256,
and cannot drift from the tokenizer. `ModelStore.vocabStream(...)` resolves it via `assets/nlu/vocab.txt`.

**Device/training-pending (OQ#1/OQ#2):** the real `vocab.txt` lands together with the trained
`intent.onnx` model. Until then this directory is intentionally empty — `vocabStream(...)` returns
`null` for a missing asset and `OnnxIntentClassifier` degrades to the rule path (no crash).

The model file itself is **not** bundled — it is downloaded, SHA-256-verified, and promoted into
app-internal `noBackupFilesDir/models/` by the WorkManager download worker (gated on OQ#2).
