# Bridge transport measurement — inputs (git-ignored)

A4′ phase 3a, spec §7.12.2: which transport form becomes the default, and what the budget numbers are.
Paid and networked — it never runs in `tools/gate.sh`. The owner runs it:

    SIDR_MEASURE_API_KEY=... tools/measure-bridge-transport.sh <model>[,<model>...]
    SIDR_MEASURE_API_KEY=... tools/measure-bridge-transport.sh <model> --dry-run   # no network

- `corpus.tsv` — one command per line: `<label><TAB><command>`; `#` starts a comment. Labels:
  `act` (should become a plan), `question` (should go to the assistant), `nav` (a Sidr screen),
  `other`. The B15 corpus is a good start; the labels are the owner's.
- The base URL defaults to `https://openrouter.ai/api/v1`; override with `SIDR_MEASURE_BASE_URL`.
- The key is read from `SIDR_MEASURE_API_KEY` and is never printed, logged or written.

Output: `BRIDGE-MEASURE ::` lines in `build/bridge-measure.log`, and the full per-line report in
`app/build/bridge-measure/report.txt` — for the owner to judge each reading by hand.
