#!/usr/bin/env bash
# Проверка стража guard-bash.sh в обе стороны: что блокирует И что пропускает.
#
# Почему это файл, а не строка в терминале: страж живой, и команда с текстом
# `git push` внутри блокируется им же. Набор случаев лежит данными рядом
# (guard-bash-cases.json), поэтому прогон — это одна безобидная строка
# `tools/claude/guard-bash-test.sh`, которую страж пропускает.
#
# Случаи "pass" важнее случаев "block": страж, который блокирует всё, тоже
# зелёный на одних только запретах. Каждый pass-случай — попытка опровергнуть
# конкретную часть регулярки (регистр, приклеенное к `=` слово, одиночный `&`).

set -uo pipefail

dir="$(cd "$(dirname "$0")" && pwd)"
guard="$dir/guard-bash.sh"
cases="$dir/guard-bash-cases.json"

[ -x "$guard" ] || { echo "страж не исполняемый: $guard" >&2; exit 1; }
[ -r "$cases" ] || { echo "нет набора случаев: $cases" >&2; exit 1; }

total=0
failed=0

while IFS= read -r row; do
  expect="$(printf '%s' "$row" | jq -r '.expect')"
  why="$(printf '%s' "$row" | jq -r '.why')"
  cmd="$(printf '%s' "$row" | jq -r '.cmd')"

  payload="$(jq -Rn --arg c "$cmd" '{tool_name:"Bash",tool_input:{command:$c}}')"
  printf '%s' "$payload" | "$guard" >/dev/null 2>&1
  rc=$?
  got="pass"
  [ "$rc" -eq 2 ] && got="block"

  total=$((total + 1))
  first_line="$(printf '%s' "$cmd" | sed -n '1p')"
  if [ "$got" = "$expect" ]; then
    printf '  ok     %-5s  %s\n' "$got" "$first_line"
  else
    failed=$((failed + 1))
    printf '  ПРОВАЛ ожидал %s, получил %s\n         команда: %s\n         случай:  %s\n' \
      "$expect" "$got" "$first_line" "$why"
  fi
done < <(jq -c '.[]' "$cases")

printf '\n%d случаев, %d провалов\n' "$total" "$failed"
[ "$failed" -eq 0 ]
