#!/usr/bin/env bash
#
# Read the agent tables of the launcher's Room database off a connected device.
#
# WHY THIS EXISTS. Room opens `sidr_history.db` in WAL mode (`PRAGMA journal_mode` -> `wal`, measured
# on the SM-A325F). In WAL mode a committed write is durable in `sidr_history.db-wal` and reaches the
# main `.db` file only at a *checkpoint*. So the obvious command
#
#     adb exec-out run-as com.sidr.launcher cat databases/sidr_history.db > /tmp/sidr.db
#
# does NOT pull the database - it pulls the database as of the last checkpoint, which on 2026-09-10
# was three hours and four state transitions stale. It lies in both directions: it reported a session
# the engine had correctly deleted as still present (the false RED that stopped that owner run), and
# it can equally report all three `agent_*` tables empty while a row holding `agent_session.goal_text`
# - the user's raw command text - is on disk. Pull the `.db` and the `-wal` together, always.
#
# `-shm` is not pulled: it is a rebuildable index over the `-wal`, and SQLite regenerates it when it is
# absent or stale (verified 2026-09-10 by reading the same pulled pair with and without it).
#
# NOT ATOMIC. The two files are copied one after the other while the app may be writing. A torn read
# shows up as a `database disk image is malformed` or an integrity_check failure, not as quiet wrong
# data - the script runs the check and says so. Re-run if it fires.
#
# Usage:  tools/device/pull-agent-db.sh [outdir] [sql]
#   outdir  where to keep the pulled pair (default: a fresh mktemp dir; the path is printed)
#   sql     a query to run instead of the default agent-table dump
set -euo pipefail

PKG=com.sidr.launcher
DB=sidr_history.db
OUT="${1:-$(mktemp -d)}"
SQL="${2:-}"

SQLITE="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}/platform-tools/sqlite3"
[ -x "$SQLITE" ] || SQLITE="$(command -v sqlite3 || true)"
[ -n "$SQLITE" ] || { echo "no sqlite3: install one, or point ANDROID_HOME at the SDK" >&2; exit 2; }

mkdir -p "$OUT"
for f in "$DB" "$DB-wal"; do
  adb exec-out run-as "$PKG" cat "databases/$f" > "$OUT/$f"
done
echo "pulled $DB + $DB-wal -> $OUT" >&2

INTEGRITY="$("$SQLITE" "$OUT/$DB" 'pragma integrity_check;')"
[ "$INTEGRITY" = "ok" ] || {
  echo "integrity_check: $INTEGRITY - the pair was copied mid-write; re-run" >&2; exit 3;
}

if [ -n "$SQL" ]; then
  "$SQLITE" -header -column "$OUT/$DB" "$SQL"
  exit 0
fi

"$SQLITE" -header -column "$OUT/$DB" \
  "SELECT id, state, cursor, goal_text FROM agent_session;"
"$SQLITE" -header -column "$OUT/$DB" \
  "SELECT step_index, tool_id, risk, observation_type, consent FROM agent_plan_step ORDER BY step_index;"
"$SQLITE" -header -column "$OUT/$DB" \
  "SELECT seq, type, step_index, detail FROM agent_trace_event ORDER BY seq;"
"$SQLITE" "$OUT/$DB" \
  "SELECT 'at rest: agent_session=' || (SELECT COUNT(*) FROM agent_session) ||
          ' agent_plan_step=' || (SELECT COUNT(*) FROM agent_plan_step) ||
          ' agent_trace_event=' || (SELECT COUNT(*) FROM agent_trace_event);"
