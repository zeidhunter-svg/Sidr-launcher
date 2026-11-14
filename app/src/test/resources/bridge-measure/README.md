# Bridge transport measurement — inputs (git-ignored)

A4′ phase 3a, spec §7.12.2: which transport form becomes the default, and what the budget numbers are.
Paid and networked — it never runs in `tools/gate.sh`. The owner runs it:

    read -rs SIDR_MEASURE_API_KEY && export SIDR_MEASURE_API_KEY
    tools/measure-bridge-transport.sh <model>[,<model>...]
    tools/measure-bridge-transport.sh <model> --dry-run   # no network

`read -rs` takes the key without echoing it and keeps it off the command line: a typed
`SIDR_MEASURE_API_KEY=... tools/…` line is saved verbatim in the shell's history file.
The script accepts exactly `<models>` (one comma-separated word) and an optional trailing `--dry-run`;
anything else is refused before Gradle starts.

- `corpus.tsv` — one command per line: `<label><TAB><command>`; `#` starts a comment. Labels:
  `act` (should become a plan), `question` (should go to the assistant), `nav` (a Sidr screen),
  `multi` (asks for more than one act — the summary's `multi_steps_ge2=x/y` counts how many came back
  as a plan of two steps or more; phase 3b), `other`. The B15 corpus is a good start; the labels are
  the owner's. A plan's line reads `kind=plan:<n>`, `n` its number of steps.
- The base URL defaults to `https://openrouter.ai/api/v1`; override with `SIDR_MEASURE_BASE_URL`.
- Two switches, environment variables validated by the harness before any request (phase 3b; the
  script's arguments are unchanged): `SIDR_MEASURE_MODES` — a comma list of `PROMPT_ONLY`,
  `RESPONSE_FORMAT`, `FORCED_TOOL` (default: all three); `SIDR_MEASURE_REASONING` — `off`, `on` or
  `off,on` (default `off`). `on` sends the reasoning cap `BridgeBudget.Default.maxOutputTokens / 2`,
  which goes to `openrouter.ai` only, so `on` with another base URL is refused. An unknown or repeated
  value is refused. The dry run prints both; every `line` and `summary` carries `reasoning=off|on`.
- The key is read from `SIDR_MEASURE_API_KEY` and is never printed, logged or written.

Output: `BRIDGE-MEASURE ::` lines in `build/bridge-measure.log`, and the full per-line report in
`app/build/bridge-measure/report.txt` — for the owner to judge each reading by hand.
