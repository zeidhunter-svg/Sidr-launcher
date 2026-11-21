# Ориентир по решениям — что в силе и где лежит полный текст

> **Читается целиком.** Это карта, а не журнал. Журнал — [`decisions.md`](decisions.md) (1,1 МБ, ~12 000 строк):
> **архив по адресу**, который не режется и не переписывается; новые ADR дописываются **в его конец**. Адрес ADR —
> его заголовок, дословно; ссылка «ADR «…»» в коде, KDoc и документах разрешается поиском этого заголовка:
> `grep -n '^##.*<кусок заголовка>' ai-context/decisions.md`, затем `sed -n '<строка>,<строка+N>p'`. Номера строк
> адресом не служат: они сдвигаются при каждой дописке, а тринадцать старых ссылок вида `decisions.md:2509` уже
> не попадают в цель. Порядок разделов в журнале **не хронологический** (он начинается с DS-11 от 2026-08-10), поэтому
> ищите по заголовку, а не листайте.
>
> **Как держится.** Решение владельца 2026-11-21 «5: a», ADR «2026-11-21 — Гигиена контекста: ориентир по решениям и
> два правила §0». Каждый заголовок `## ` журнала стоит здесь дословно, а размер файла ограничен потолком; оба
> свойства проверяет `DecisionsOrientationGuardTest` (`:app`). Закрывающая сессия дописывает строку своего ADR и
> переразмечает строки тех ADR, которые он заменил («Чеклист закрытия» §0 трек-плана). Красный гвард размера —
> повод сжать строки, а не поднять потолок; поднимать его решает владелец.
>
> **Метки.** **в силе** — решение связывает следующую сессию. **заменён → …** — читать новый адрес, старый оставлен
> для истории. **история** — запись закрытого блока: чтобы понять прошлое, а не чтобы решать сейчас. Долг закрытых
> блоков перечислен в `CLAUDE.md`, Known debt, со ссылкой на ADR.

## В силе сейчас — решения, которые связывают следующую сессию

- **Понимание — модели, исполнение — детерминированному слою**; FastPath служит ускорением, а не фильтром понимания;
  локальный путь задаёт `DOC-ADL-3` (цитировать по ID) — ADR 1/4.
- **ONNX закрыт**, локальный рантайм — LiteRT / LiteRT-LM; `AppFunctions` / `MCP` — источники первого класса — ADR 2/4;
  стек удалён, не воскрешать — «Этап 0.3».
- **Портируемое ядро**: `:domain` — KMP, семь значений `ActionIds` заморожены байт в байт — ADR 3/4; исходящий
  контракт — девятнадцать предлагаемых `ToolId`, значения заморожены, число нет — ADR ф3a «один модельный путь».
- **Ассистент ⊕ агент**: один цикл, две поверхности, план из 0 шагов и есть ответ — ADR 4/4.
- **`localOnlyMode`** вместо `llmRouterEnabled`, по умолчанию `false`, без миграции — «Этап 4.0».
- **Два потребителя с самого начала** (`:consumer:jvm`, развилка F6) — «Развилка агентного трека».
- **Расхождение `Failed`/`Completed` A0.5 §6.3 не унифицировать**: это находка, а не дефект — ADR «Этап 4.5 (A0.5)»;
  в `CLAUDE.md` оно стоит в разделе «Do not».
- **Словарь статусов** `CODE-GREEN` / `DEVICE-ACCEPTED` / `CLOSED` — «Этап 0.5»; **бюджеты по замеру** — «Этап 0.6».
- **Master Plan и матрица доктрины** (31 правило `DOC-*`, у каждого настоящий тест или честное `<нет>`) — «Этап 3»;
  строки `DOC-HMA-5`, `DOC-ILM-5` — «Две строки доктрины».
- **Два принципа**: набор команд не ограничен, набор гейтов конечен и один для всех; что может работать на
  устройстве, работает там — ADR «Два принципа агентной ОС».
- **Один модельный путь (мост)**: ничего не исполняется до «Выполнить»; бюджет и транспорт выбраны по замеру
  (дополнение «Бюджет и транспорт по умолчанию — по замеру», 2026-11-07, и его подраздел «Замер фазы 3b») — ADR ф3a «один модельный путь»; разрешённое исходящее содержимое
  `AUTHORED_TOOL_SCHEMA` — ADR ф3a `ACTION_CATALOG_SCHEMA` → `AUTHORED_TOOL_SCHEMA`.
- **Сорт аргумента — отдельная ось**, F6 закрыт — ADR ф3b «сорт».
- **Выученная команда** (форма со слотами + шаблон, схема 5, шаг 2c) — ADR ф4; исключение из правила «ничего до
  «Выполнить»» — «D3, поправка: выученная команда».
- **Потолок `CLAUDE.md`** (`ClaudeMdSizeGuardTest`) и дословное приложение ушедшего текста — «Сжатие CLAUDE.md».
- **Двенадцать задач владельца после приёмки A4′, задача 13 («найди …») и ответы `2: a · 6: a · ADL3: b · 5: a`** —
  ADR блока «Этап 6 (A4′)», его разделы «Двенадцать решений владельца» и «Ответы владельца 2026-11-21»; порядок
  работ — `§HANDOFF` трек-плана.
- Процессные правила (как вести блок, как закрывать) — **не ADR**, а §0 трек-плана.

## Реестр — каждый заголовок журнала, по эпохам

### Основание (июнь 2026) — `##`-разделы без даты и раздел ADR фаз 3–7

- «Accepted architecture» — **в силе**: многомодульная Clean Architecture, домен без Android.
- «AI decisions» — **заменён → ADR 1/4** (порядок «сначала локальное сопоставление») и **ADR 2/4** (ONNX).
- «Android decisions» — **в силе**: API 28+, Accessibility не обязательна, ядро лаунчера работает без неё.
- «Implementation decision» — **история** (сначала только скелет).
- «Navigation decisions» — **в силе**: `Routes` в `:core:common`, единственный `NavHost` в `:app`, `NavigationEvent`.
- «Structure and sequencing decisions» — раздел-контейнер; внутри ADR уровня `###`:
  - «`OperationResult` ownership moves to `domain`» — **в силе** (жёсткое правило в `CLAUDE.md`);
  - блоки **B → H** (фаза 4) и «Acceptance blockers follow-up» — **история**;
  - блоки **I → N** + «Phase 5 close summary» — **история**; в силе их наследие: Keystore `SecureSecretStore` (J),
    `OutboundContextPolicy` (L);
  - «Phase 6 … forks decided», блоки **O → R**, «OQ#1 amended» — **заменены → ADR 2/4** и «Этап 0.3»;
  - «Phase 2 skipped / reordered» — **история**;
  - «Phase 7 … forks decided», блоки **S → W**, три приёмки на устройстве (2026-07-01 / 07-02 ×2) — **история**.

### UX и укрепление (2026-07-03 … 07-04) — всё **история**

- «ADR 2026-07-03 — Phase UX Block X1 complete (design-system foundation in `core/ui`)»
- «ADR 2026-07-03 — Phase UX Block X2 complete (home redesign / declutter)»
- «ADR 2026-07-03 — Phase UX Block X3 complete (App Drawer — all apps, on demand)»
- «ADR 2026-07-03 — Phase UX Block X4 complete (search ⇄ command unification — live app filtering)»
- «ADR 2026-07-03 — Phase UX Block X5 complete (real Settings surface — `:feature:settings`)»
- «ADR 2026-07-03 — Phase UX Block X6 complete (polish, a11y, first-run nudge, deferred settings; Phase UX CLOSED)»
- «ADR 2026-07-04 — Phase UX device acceptance (SM-A325F / Android 13) + set-as-default bug fix»
- «ADR 2026-07-04 — Startup optimization + release build (SM-A325F)» — релизная сборка и Baseline Profile живут до сих пор.
- «ADR 2026-07-04 — Contextual suggestions correctness (SM-A325F)»
- «ADR 2026-07-04 — Phase 9 Block Y4 test-coverage hardening»
- «ADR 2026-07-04 — Phase 9 Block Y5 privacy/logging/error handling»
- «ADR 2026-07-04 — Phase 9 Block Y6 multi-version + LOW_END validation»
- «ADR 2026-07-04 — Phase 9 Block Y7 residual cosmetic cleanup»

### Этап 1 — AI Launcher (2026-07-05 … 07-10)

- «ADR 2026-07-05 — Project reframed into three stages; Stage-1 AI-Launcher completion track» — **заменён → ADR 3/4**
  (портируемое ядро; «Stage 2» больше не этап расписания).
- «ADR 2026-07-05 — AIL-0 complete (design tokens & visual identity: cyberpunk terminal in `core/ui`)» — **заменён →**
  «Agentic OS target architecture … (soft classic grey)» и DS-1.
- «ADR 2026-07-05 — AIL-1 complete (Action Registry contracts in `domain`)» — **в силе**: `ActionCatalog`, `ActionIds`
  (заморожены ADR 3/4).
- «ADR 2026-07-05 — AIL-2 complete (Web / URL / Play-Store routing, no AI)» — **история**; `web_search`/`open_url`
  стали инструментами в ф3a.
- «ADR 2026-07-05 — AIL-3 complete (Universal Input: additive router over one home field)» — **история**.
- «ADR 2026-07-05 — AIL-4: LLM Action Router (`CommandPlanner`) — structured routing via BYOK LLM» — **заменён → ADR ф3a
  «один модельный путь»** (роутер удалён).
- «ADR 2026-07-06 — AIL-4 complete (LLM Action Router: `CommandPlanner` + `RouteCommandUseCase`)» — **заменён** — то же.
- «ADR 2026-07-06 — AIL-5 complete (Confirmation & safety gating: risk-gated confirm card + execution)» — **история**;
  предикат гейта сегодня — `requiresConsent(risk)`, `DOC-ADL-1` (A1′).
- «ADR 2026-07-06 — AIL-6 complete (Polish + SM-A325F device acceptance → Stage-1 AI-Launcher MVP CLOSED)» — **история**.
- «ADR 2026-07-06 — Stage 2 kickoff: "Learned Resolutions" design spec (block S2-1, DESIGN-ONLY)» — **история**.
- «ADR 2026-07-10 — S2-1 "Learned Resolutions" complete (code-closed; device-pending)» — **история**.
- «ADR 2026-07-11 — S2-1 Learned Resolutions device accepted + closed» — **история**; продукт описан в Shipped surface,
  раздел «Memory».
- «ADR 2026-07-10 — Agentic OS target architecture (A1–A6) + visual identity (soft classic grey)» — **в силе как
  направление**; блоки ведёт Master Plan («Этап 3»).

### Трек дизайна и локализации (2026-07-11 … 08-19)

- «ADR 2026-07-11 — DS-1 complete (soft-classic-grey token layer + Roborazzi screenshot harness)» — **в силе**: токены и
  золотые Roborazzi; свод правил — `docs/governing/sidr-design-system-master-plan-v1.2.md`.
- «ADR 2026-07-11 — DS-0 (design provenance backfill: imported docs committed + conflict/deviation record)» — **история**.
- «ADR 2026-07-11 — DS-2 primitives complete (presentation-only)» — **история**.
- «ADR 2026-07-11 — DS-3 controls complete (presentation-only)» — **история**.
- «ADR 2026-07-11 — DS-4 complete (Home shell + Universal Input; device-accepted)» — **история**.
- «ADR 2026-07-11 — Vision MVP preview + all screens to artifact» — **история**; PREVIEW-поверхности остаются по
  решению 2026-08-18, оно записано в трек-плане.
- «ADR 2026-07-13 — DS-5 + auto-hide nav + accent reactivation closed (code) + stabilization pass» — **история**; долг:
  чеклист DS-5 так и не прогнан.
- «ADR 2026-08-08 — DS-6B Prayer Correctness (COMPLETE — device-accepted by owner)» — **история**; прецедент «`CLOSED` не
  значит ноль долга».
- «ADR 2026-08-10 — DS-7 Memory Surfaces + S2-2 Explicit Aliases complete (device-accepted)» — **история**.
- «ADR 2026-08-10 — DS-11 pre-gate UI refinement (Home chrome, route lane, typography)» — **история**; прецедент
  «инверсия ключа вместо смены значения по умолчанию» (`autoHideNavBar`; цитирует ADR 1/4).
- «2026-08-10 — DS-10 Assistant Migration complete (device-accepted)» — **история**.
- «2026-08-16 — I18N-1 Multilingual UI complete» — **в силе**: `en`/`ru`/`tr` через `sidrString`, барьеры-гварды.
- «2026-08-19 — I18N-2 residual localization + barrier 4» — **в силе**: четвёртый барьер.

### Агентный перезапуск — Этапы 1–3 (2026-08-19)

- «2026-08-19 — ADR 1/4 (agentic restart) — deterministic-first redefined: understanding belongs to the model, execution to the deterministic layer» — **в силе**.
- «2026-08-19 — ADR 2/4 (agentic restart) — platform re-baseline 2026: ONNX NLU closed, LiteRT/LiteRT-LM designated, tools come from the OS» — **в силе**.
- «2026-08-19 — ADR 3/4 (agentic restart) — portable core boundary: what "Framework" is, and the `ActionIds` byte-for-byte constraint» — **в силе**.
- «2026-08-19 — ADR 4/4 (agentic restart) — Assistant ⊕ Agent: one conversational loop, two surfaces» — **в силе**.
- «2026-08-19 — Этап 0.2 complete — FastPath localized to ru/tr» — **история**.
- «2026-08-19 — Этап 0.3 complete — ONNX stack removed» — **в силе** как запрет (раздел «Do not» в `CLAUDE.md`).
- «2026-08-19 — Этап 0.4 complete — `CLAUDE.md` compressed 101 KB → 15 KB» — **история**; прецедент, продолжен «Сжатием
  CLAUDE.md» 2026-11-11.
- «2026-08-19 — Этап 0.5 complete — honest statuses (`CODE-GREEN` / `DEVICE-ACCEPTED` / `CLOSED`)» — **в силе**.
- «2026-08-19 — Этап 0.6 complete — performance budgets rewritten on measured numbers» — **в силе**.
- «2026-08-19 — Этап 0.7 complete — I18N-2 residue reviewed, both items pre-resolved» — **история**.
- «2026-08-19 — Этап 2 complete — toolchain bumped to Aug-2026-current, `:domain` on Kotlin Multiplatform» — **в силе**.
- «2026-08-19 — Этап 3 complete — agentic Master Plan + doctrine matrix extracted, `docs/governing/` created» — **в силе**.

### Агентный трек — блоки A0 … A4′ (2026-08-20 … 11-21)

- «2026-08-20 — Этап 4.0 (agentic track) — the understanding flag is inverted: `llmRouterEnabled` → `localOnlyMode`» — **в силе**.
- «2026-08-21 — Развилка агентного трека: портируемое ядро с двумя потребителями с самого начала» — **в силе**.
- «2026-08-22 — Этап 4 (A0) — Thin Agentic Spike: the engine is proved in code, and accepted on device» — **история +
  долг** (Known debt «A0»).
- «2026-08-23 — Сквозное ревью блока A0 и раунд исправлений: девять находок, восемь исправлены, одна снята как недостижимая» — **история + долг** (повторная проверка на устройстве).
- «2026-08-26 — Этап 4.5 (A0.5) — второй потребитель портируемого ядра: машинерия переносится, словарь — нет» — **в силе**
  (§6.3) **+ долг**.
- «2026-09-03 — Этап 5 (A1′) — федерированный ToolRegistry: N источников за одной границей, provenance на поверхности, один risk→gate предикат» — **в силе**; приёмка 2026-09-10 — раздел внутри.
- «2026-09-19 — Этап 5.5 (A1″) — масса инструментов и отбор: пять действующих инструментов, четвёртый адаптер, и паттерн «свидетельство, описывающее не то, что от него думают»» — **история + долг**; методический долг (a)/(b) **связывает**; фаза 3b и приёмка — разделы внутри.
- «2026-09-20 — Две строки доктрины: грант не вычитает гейт, и три класса содержимого» — **в силе**.
- «2026-10-05 — A4′ ф3a: `ACTION_CATALOG_SCHEMA` → `AUTHORED_TOOL_SCHEMA`» — **в силе**.
- «2026-10-05 — A4′ ф3a: один модельный путь; исходящий контракт — девятнадцать `ToolId`» — **в силе** (с дополнением
  о бюджете и подразделом «Замер фазы 3b»).
- «2026-11-07 — Два принципа агентной ОС: защищённая открытость и локальное по умолчанию» — **в силе**.
- «2026-11-07 — A4′ ф3b: сорт — отдельная ось; F6 закрыт» — **в силе**.
- «2026-11-11 — Сжатие CLAUDE.md (гигиена контекста)» — **в силе** (потолок; дословное приложение).
- «2026-11-20 — A4′ ф4: кэш выученных планов — форма со слотами, шаблон, схема 5» — **в силе**.
- «2026-11-20 — D3, поправка: выученная команда» — **в силе**.
- «2026-11-21 — Этап 6 (A4′) — рантайм полностью: фазы 0–4, одна приёмка на устройстве, блок `CLOSED`» — **в силе**:
  двенадцать задач, ответы владельца, остаточные ограничения.
- «2026-11-21 — Гигиена контекста: ориентир по решениям и два правила §0» — **в силе**: этот файл и его гвард, журнал —
  архив по адресу, правила 2 и 6 §0; в приложении — дословно History map, ушедший из `CLAUDE.md`.
