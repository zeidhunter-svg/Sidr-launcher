#!/usr/bin/env bash
# Проверка фильтра codegraph-prompt-hook.sh в обе стороны: что пропускает к
# настоящему хуку и что гасит. Настоящий хук подменяется заглушкой через PATH,
# чтобы тест не зависел от индекса и не тратил время на него.
#
# Случаи "skip" — попытка опровергнуть конкретную ветку фильтра (машинный
# user-turn, вставка, вопрос без идентификатора); случаи "run" — что фильтр
# не слишком строг для настоящего вопроса о коде.

set -uo pipefail

dir="$(cd "$(dirname "$0")" && pwd)"
hook="$dir/codegraph-prompt-hook.sh"
cases="$dir/codegraph-prompt-hook-cases.json"

[ -x "$hook" ] || { echo "фильтр не исполняемый: $hook" >&2; exit 1; }
[ -r "$cases" ] || { echo "нет набора случаев: $cases" >&2; exit 1; }

stub="$(mktemp -d)"
trap 'rm -rf "$stub"' EXIT
printf '#!/usr/bin/env bash\necho STUB-RAN\n' > "$stub/codegraph"
chmod +x "$stub/codegraph"

total=0
failed=0
while IFS= read -r row; do
  expect="$(printf '%s' "$row" | jq -r '.expect')"
  why="$(printf '%s' "$row" | jq -r '.why')"
  prompt="$(printf '%s' "$row" | jq -r '.prompt')"
  payload="$(jq -n --arg p "$prompt" '{prompt:$p,cwd:"/x"}')"
  out="$(printf '%s' "$payload" | PATH="$stub:$PATH" "$hook")"
  rc=$?
  total=$((total+1))
  if [ "$rc" -ne 0 ]; then
    echo "FAIL (rc=$rc) — $why"; failed=$((failed+1)); continue
  fi
  case "$expect" in
    run)  [ "$out" = "STUB-RAN" ] || { echo "FAIL run  — $why (вывод: '$out')"; failed=$((failed+1)); } ;;
    skip) [ -z "$out" ]          || { echo "FAIL skip — $why (вывод: '$out')"; failed=$((failed+1)); } ;;
  esac
done < <(jq -c '.[]' "$cases")

echo "codegraph-prompt-hook: $total случаев, $failed провалов"
[ "$failed" -eq 0 ]
