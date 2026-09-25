#!/usr/bin/env bash
# The gate, as a machine instead of a memory (spec §9.2; §4 proposal 4+9).
#
# It replaces three of the seven rules of §9.1 — each paid for by a concrete false green — with
# something that cannot forget them:
#   * `build/test-results` is cleared before the run            (R14-44: stale XML lies both ways)
#   * `--rerun-tasks` is passed on a boundary run, `--rerun` never (R9: a false green inside A1′)
#   * counts come from the JUnit XML, never from the console     (2026-07-13: `tail` hid a red gate)
#
# It does NOT replace the other four: predicting the decomposition before the run, proving a new
# guard by mutation, fixture substrings, and re-running from a cleared tree after a mutation are
# judgements, and a script that pretended to make them would be one more instance of "evidence that
# does not describe the thing it is believed to describe".
#
# TWO MODES, AND THEIR LABELS DIFFER ON PURPOSE.
#   boundary (default) — the run that closes a task. Clears every module's results, passes
#                        `--rerun-tasks`, compares the total against the 1440 baseline, and says
#                        `GATE GREEN` / `GATE RED`.
#   --scoped           — a run inside a task: a TDD step or a mutation. Clears, COUNTS and reports
#                        the named modules only, passes `--no-build-cache --no-watch-fs` instead of
#                        `--rerun-tasks`, prints NO baseline comparison, and says
#                        `SCOPED GREEN … — NOT a boundary gate`.
# The phase runs a dozen mutations; as one mode they were a dozen full `--rerun-tasks` sweeps. But
# the saving is not why the labels differ: a cheap run that printed `GATE GREEN` would be a
# false-green generator of exactly the kind this file exists to remove, and a scoped table compared
# against 1440 would be a number describing five missing modules.
#
# THREE THINGS A SCOPED RUN MUST NOT DO, each found by the second plan review (§0.6) and each
# demonstrated by running the first version of this file rather than by reading it:
#   * count another module's leftover XML. The first version cleared the named modules and then
#     summed `**/test-results` across the whole tree — so a red XML left by the previous mutation in
#     module A turned a scoped run of module B red with `gradle=0`, and B's mutation "proved" itself.
#   * let a named test task be served instead of run. `org.gradle.caching=true` is on in this repo
#     and the daemon keeps a watched file-system state between builds; together they can restore a
#     test task's pre-mutation green XML FROM-CACHE. The first version tried to catch that with
#     `grep ': 0 executed'`, a line Gradle 9.5.0 never prints: its statistics reporter omits every
#     zero count (`TaskExecutionStatisticsReporter.formatDetail`, read with `javap`).
#   * leave "RED on <named test>" unverifiable. A mutation is proved by WHICH test went red, so every
#     failing testcase is printed as a `FAILED ::` line.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

JDK17="${SIDR_JDK17:-/home/Suleiman/jdks/jdk-17.0.19+10}"
[ -d "$JDK17" ] || { echo "FATAL: JDK 17 not found at $JDK17 (set SIDR_JDK17)" >&2; exit 2; }

MODE=boundary
case "${1:-}" in
  --boundary) MODE=boundary; shift ;;
  --scoped)   MODE=scoped;   shift ;;
  -h|--help)  echo "usage: tools/gate.sh [--boundary|--scoped] [gradle tasks…]"; exit 0 ;;
esac

DEFAULT_TASKS=(:domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test)
TASKS=("$@")
[ "${#TASKS[@]}" -eq 0 ] && TASKS=("${DEFAULT_TASKS[@]}")

# A scoped run must name modules, because "which results may I clear", "which results may I count"
# and "what may I leave up-to-date" are all answered from the task path. An unqualified task
# (`testDebugUnitTest`) runs in every module, which IS a boundary run — so it is refused here rather
# than silently mislabelled.
if [ "$MODE" = scoped ]; then
  [ "$#" -gt 0 ] || { echo "FATAL: --scoped needs at least one :module:task" >&2; exit 2; }
  for t in "${TASKS[@]}"; do
    case "$t" in
      :*:*) ;;
      *) echo "FATAL: --scoped takes only fully-qualified :module:task (got '$t'). An unqualified task runs every module — that is a boundary run." >&2; exit 2 ;;
    esac
  done
fi

# :data:repository:testDebugUnitTest -> data/repository ; :consumer:jvm:test -> consumer/jvm
module_of() { local m="${1#:}"; m="${m%:*}"; printf '%s' "${m//://}"; }

MODULES=()
if [ "$MODE" = boundary ]; then
  echo "== boundary: clearing build/test-results EVERYWHERE (R14-44) =="
  find . -path ./build -prune -o -type d -name test-results -print0 2>/dev/null \
    | xargs -0 -r rm -rf
else
  echo "== scoped: clearing build/test-results for the named modules only =="
  for t in "${TASKS[@]}"; do
    m="$(module_of "$t")"
    [ -d "$m" ] || { echo "FATAL: no module directory '$m' for task '$t'" >&2; exit 2; }
    rm -rf "$m/build/test-results"
    MODULES+=("$m")
  done
fi

# The daemon is ON in both modes (owner decision, 2026-09-25: the former --no-daemon rule is no
# longer current). Gradle uses it by default and `gradle.properties` sets no `org.gradle.daemon`
# line, so nothing is passed and the first invocation's JVM startup is amortised over the rest.
#
# Why that is safe in each mode, said rather than assumed. A boundary run passes `--rerun-tasks`, so
# every task executes and nothing is taken from a remembered up-to-date verdict or from the cache. A
# scoped run keeps up-to-date checks for compilation (that is its whole saving) but passes
# `--no-watch-fs`, so no file-system state survives from the previous build and every input is
# re-read, and `--no-build-cache`, so a test task whose results were just cleared has to EXECUTE —
# it cannot be restored from the cache. The named-task check below then says so from Gradle's own
# log, per task, and a named test task that did not execute fails the run as `SCOPED NOT RUN`.
GRADLE_ARGS=("-Porg.gradle.java.installations.paths=$JDK17" --console=plain)
if [ "$MODE" = boundary ]; then
  GRADLE_ARGS+=(--rerun-tasks)
else
  GRADLE_ARGS+=(--no-build-cache --no-watch-fs)
fi

LOG="$(mktemp -t sidr-gate-XXXXXX.log)"
echo "== log: $LOG =="
echo "== $MODE: ${TASKS[*]} ${GRADLE_ARGS[*]} =="
# `set +e` around BOTH long-running steps. Without it `set -e` aborts the script the moment the
# counter exits non-zero — which is exactly the run this script exists to report — and the RED
# summary, the census line and the log path are never printed.
set +e
./gradlew "${GRADLE_ARGS[@]}" "${TASKS[@]}" > "$LOG" 2>&1
GRADLE_EXIT=$?
set -e
# Never piped through `tail`: the whole log is kept and the exit code is the exit code.
ACTIONABLE="$(grep -E "actionable tasks?:" "$LOG" || true)"
echo "${ACTIONABLE:-WARNING: no 'actionable tasks' line — was anything executed?}"

NOT_RUN=0
if [ "$MODE" = scoped ]; then
  echo "== named tasks, as Gradle's plain console reported them =="
  for t in "${TASKS[@]}"; do
    line="$(grep -E "^> Task ${t}( |$)" "$LOG" | tail -n 1 || true)"
    case "$line" in
      "> Task $t")        status="executed" ;;
      "> Task $t FAILED") status="executed, FAILED" ;;
      "")                 status="ABSENT from the log" ;;
      *)                  status="${line#"> Task $t "}" ;;
    esac
    echo "  $t: $status"
    # A test task in a scoped run must EXECUTE: its results were cleared and the cache is off, so
    # UP-TO-DATE, FROM-CACHE, NO-SOURCE or absence means this run proved nothing about it.
    case "$t" in
      *[Tt]est) case "$status" in executed*) ;; *) NOT_RUN=1 ;; esac ;;
    esac
  done
fi

echo "== counts, from the JUnit XML =="
set +e
python3 - "$REPO_ROOT" "$MODE" "${MODULES[@]}" <<'PY'
import sys, glob, os
from xml.etree import ElementTree as ET

root, mode = sys.argv[1], sys.argv[2]
named = list(dict.fromkeys(sys.argv[3:]))
per_module, failed = {}, []
for path in glob.glob(os.path.join(root, "**", "build", "test-results", "**", "*.xml"),
                      recursive=True):
    rel = os.path.relpath(path, root)
    module = rel.split(os.sep + "build" + os.sep)[0]
    if mode == "scoped" and module not in named:
        continue  # another module's leftover XML is not this run's evidence
    try:
        suite = ET.parse(path).getroot()
    except ET.ParseError:
        print(f"UNPARSEABLE :: {rel}")
        continue
    if suite.tag != "testsuite":
        continue
    acc = per_module.setdefault(module, [0, 0, 0, 0])
    acc[0] += int(suite.get("tests", 0))
    acc[1] += int(suite.get("failures", 0))
    acc[2] += int(suite.get("errors", 0))
    acc[3] += int(suite.get("skipped", 0))
    for case in suite.iter("testcase"):
        if case.find("failure") is not None or case.find("error") is not None:
            failed.append(f"{module} :: {case.get('classname')} > {case.get('name')}")

total = [0, 0, 0, 0]
for module in sorted(per_module):
    t, f, e, s = per_module[module]
    total = [a + b for a, b in zip(total, [t, f, e, s])]
    print(f"{module:<28} tests={t:<5} failures={f:<3} errors={e:<3} skipped={s}")
for module in named:
    if module not in per_module:
        print(f"{module:<28} NO RESULTS — this named module wrote no XML in this run")
print("-" * 64)
print(f"{'TOTAL':<28} tests={total[0]:<5} failures={total[1]:<3} errors={total[2]:<3} "
      f"skipped={total[3]}")
for line in failed:
    print(f"FAILED :: {line}")
# The baseline belongs to a boundary run ONLY. Printing it under a scoped run would invite comparing
# one module's count with a whole-tree number — a table describing five absent modules.
if mode == "boundary":
    print("BASELINE at 2026-09-22 (074e4ce): tests=1440 failures=0 errors=0")
else:
    print("SCOPED: this table counts the named modules only — NOT comparable with 1440")
sys.exit(1 if (total[1] or total[2]) else 0)
PY
COUNT_EXIT=$?
set -e

echo "== registry census, from the federation =="
CENSUS_IN_RUN=no
if [ "$MODE" = boundary ]; then
  CENSUS_IN_RUN=yes
else
  for t in "${TASKS[@]}"; do case "$t" in :app:*) CENSUS_IN_RUN=yes ;; esac; done
fi
if [ "$CENSUS_IN_RUN" = yes ]; then
  CENSUS="$(grep -rhoE --include='*.xml' 'REGISTRY :: .*' app/build/test-results/ 2>/dev/null || true)"
  if [ -n "$CENSUS" ]; then
    printf '%s\n' "${CENSUS%%$'\n'*}"
  else
    echo "REGISTRY :: not captured — :app ran but printed no census (Task 1 step 2)"
  fi
else
  echo "REGISTRY :: not in this run (no :app task named)"
fi

echo "== log kept at: $LOG =="
if [ "$GRADLE_EXIT" -ne 0 ] || [ "$COUNT_EXIT" -ne 0 ]; then
  if [ "$MODE" = boundary ]; then
    echo "GATE RED (gradle=$GRADLE_EXIT counts=$COUNT_EXIT)"
  else
    echo "SCOPED RED (${TASKS[*]}) (gradle=$GRADLE_EXIT counts=$COUNT_EXIT)"
  fi
  exit 1
fi
if [ "$NOT_RUN" -ne 0 ]; then
  echo "SCOPED NOT RUN (${TASKS[*]}) — a named test task did not execute; this run proved nothing about it"
  exit 1
fi
if [ "$MODE" = boundary ]; then
  echo "GATE GREEN"
else
  echo "SCOPED GREEN (${TASKS[*]}) — NOT a boundary gate"
fi
