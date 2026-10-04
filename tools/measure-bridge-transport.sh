#!/usr/bin/env bash
# The transport measurement of A4′ phase 3a (spec §7.12.2; plan §0.2 B-7, Task 6). PAID and NETWORKED:
# it never runs inside tools/gate.sh. The owner runs it with the key in SIDR_MEASURE_API_KEY:
#   tools/measure-bridge-transport.sh <model>[,<model>...] [--dry-run]
# Neither this script nor the harness echoes, logs or writes the key.
set -uo pipefail
cd "$(dirname "$0")/.."
if [ -z "${SIDR_MEASURE_API_KEY:-}" ]; then echo "FATAL: SIDR_MEASURE_API_KEY is not set"; exit 2; fi
MODELS="${1:-}"
if [ -z "$MODELS" ]; then echo "usage: tools/measure-bridge-transport.sh <model>[,<model>...] [--dry-run]"; exit 2; fi
CORPUS=app/src/test/resources/bridge-measure/corpus.tsv
if [ ! -f "$CORPUS" ]; then echo "FATAL: $CORPUS is missing — see app/src/test/resources/bridge-measure/README.md"; exit 2; fi
JDK17="${SIDR_JDK17:-/home/Suleiman/jdks/jdk-17.0.19+10}"
EXTRA=()
if [ "${2:-}" = "--dry-run" ]; then EXTRA+=("-Psidr.measureDryRun"); fi
mkdir -p build
LOG=build/bridge-measure.log
./gradlew "-Porg.gradle.java.installations.paths=$JDK17" --console=plain --no-build-cache --rerun-tasks \
  :app:testDebugUnitTest --tests '*BridgeTransportMeasurement' \
  -Psidr.measureBridgeTransport "-Psidr.measureModels=$MODELS" ${EXTRA[@]+"${EXTRA[@]}"} -i > "$LOG" 2>&1
STATUS=$?
grep -h 'BRIDGE-MEASURE ::' "$LOG" || true
echo "exit=$STATUS log=$LOG report=app/build/bridge-measure/report.txt"
exit $STATUS
