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
case "$MODELS" in
  "" | -*) usage ;;
esac

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
./gradlew "-Porg.gradle.java.installations.paths=$JDK17" --console=plain --no-daemon --no-build-cache --rerun-tasks \
  :app:testDebugUnitTest --tests '*BridgeTransportMeasurement' \
  -Psidr.measureBridgeTransport "-Psidr.measureModels=$MODELS" ${EXTRA[@]+"${EXTRA[@]}"} -i > "$LOG" 2>&1
STATUS=$?
grep -h 'BRIDGE-MEASURE ::' "$LOG" || true
echo "exit=$STATUS log=$LOG report=app/build/bridge-measure/report.txt"
exit $STATUS
