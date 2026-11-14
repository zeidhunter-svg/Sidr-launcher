#!/usr/bin/env bash
# The transport measurement of A4′ phase 3a (spec §7.12.2; plan §0.2 B-7, Task 6). PAID and NETWORKED:
# it never runs inside tools/gate.sh. The owner runs it with the key in SIDR_MEASURE_API_KEY:
#   tools/measure-bridge-transport.sh <model>[,<model>...] [--dry-run]
# Neither this script nor the harness echoes, logs or writes the key.
set -uo pipefail
cd "$(dirname "$0")/.."

# Arguments are parsed STRICTLY and BEFORE anything else (Task 6 fix round 1). The dry run is the only
# thing standing between a typo and a paid, networked run, so a misplaced or misspelt flag must never
# fall through to a live run: `--dry-run m1` used to run LIVE with the model id "--dry-run",
# `m1 m2 --dry-run` a live run of m1, `m1 --dryrun` a live run too. Exactly one positional argument
# (the models, ONE comma-separated word, not starting with "-"), optionally followed by exactly
# `--dry-run`; anything else is refused here, before the key is even looked at and before Gradle starts.
usage() {
  echo "usage: tools/measure-bridge-transport.sh <model>[,<model>...] [--dry-run]" >&2
  exit 2
}
DRY_RUN=no
case "$#" in
  1) ;;
  2) [ "$2" = "--dry-run" ] || usage; DRY_RUN=yes ;;
  *) usage ;;
esac
MODELS="$1"
# The harness splits MODELS on commas and trims each segment (Task 6 fix round 2), so ONE quoted word can
# still smuggle a flag or a blank id past the count check above: `"m1 --dry-run"` or `m1,--dry-run`
# would start a LIVE run with a bogus id. Refused: whitespace anywhere, an empty segment (leading,
# trailing or doubled comma), a segment starting with "-".
case "$MODELS" in
  "" | -* | *[[:space:]]* | ,* | *, | *,,* | *,-*) usage ;;
esac
# And printable ASCII only: Kotlin's trim() also strips Unicode spaces such as U+00A0, which glibc's
# [[:space:]] does not match (measured under ru_RU.UTF-8), so `m1,<U+00A0>--dry-run` would pass the line
# above and reach the harness as the id "--dry-run". C locale, scoped to the function, for byte ranges.
printable_ascii() {
  local LC_ALL=C
  case "$1" in *[!\!-~]*) return 1 ;; esac
  return 0
}
printable_ascii "$MODELS" || usage

if [ -z "${SIDR_MEASURE_API_KEY:-}" ]; then echo "FATAL: SIDR_MEASURE_API_KEY is not set"; exit 2; fi
CORPUS=app/src/test/resources/bridge-measure/corpus.tsv
if [ ! -f "$CORPUS" ]; then echo "FATAL: $CORPUS is missing — see app/src/test/resources/bridge-measure/README.md"; exit 2; fi
JDK17="${SIDR_JDK17:-/home/Suleiman/jdks/jdk-17.0.19+10}"
EXTRA=()
if [ "$DRY_RUN" = yes ]; then EXTRA+=("-Psidr.measureDryRun"); fi
mkdir -p build
LOG=build/bridge-measure.log
# `--no-daemon` (Task 6 fix round 1): a daemon this run STARTED would inherit the key in its environment
# and keep it for up to 3 h of idleness. This is a one-off run, so the daemon's startup saving is
# irrelevant. With `--no-daemon` Gradle may still fork a single-use build process (to honour
# org.gradle.jvmargs), and that process exits with the build.
# In-process Kotlin compilation (fix round 2): a Kotlin compile daemon started by this run inherits the key
# in its environment (measured in fix round 1), so none is started.
./gradlew "-Porg.gradle.java.installations.paths=$JDK17" --console=plain --no-daemon --no-build-cache --rerun-tasks \
  -Pkotlin.compiler.execution.strategy=in-process \
  :app:testDebugUnitTest --tests '*BridgeTransportMeasurement' \
  -Psidr.measureBridgeTransport "-Psidr.measureModels=$MODELS" ${EXTRA[@]+"${EXTRA[@]}"} -i > "$LOG" 2>&1
STATUS=$?
grep -h 'BRIDGE-MEASURE ::' "$LOG" || true
echo "exit=$STATUS log=$LOG report=app/build/bridge-measure/report.txt"
exit $STATUS
