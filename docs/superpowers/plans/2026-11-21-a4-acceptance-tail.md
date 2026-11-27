# Хвост приёмки A4′ — план реализации

> **Для агентных исполнителей:** ОБЯЗАТЕЛЬНЫЙ ПОДСКИЛ — superpowers:subagent-driven-development (рекомендуется) или
> superpowers:executing-plans; шаги — чекбоксы (`- [ ]`). План — **гипотеза** по правилу §0 «Механика SDD-блока»
> (решение владельца 2026-09-26): заранее прогоном проверено только то, что решает объём (замер §0.1); адреса, тесты,
> посадки мутаций и предсказанные наборы `FAILED ::` сверяются при исполнении, расхождение — находка, план правится.

**Цель:** построить двенадцать решений владельца после приёмки A4′ (задачи 1–10 таблицы ADR блока, задача 13 и две
задачи, добавленные владельцем 2026-11-21, — 14 и 15) и принять их одной приёмкой на телефоне.

**Архитектура:** три пачки по месту в коде. **Пачка 1** — маршрут команды в `:domain` (`RouteCommandUseCase`,
`HandleUserCommandUseCase`, алиасы) и одно правило карточки толкования: в сети ответ FastPath, не достигший цели,
уступает модели, а свежее прочтение модели без выбора и без текста модели, уходящего наружу, стартует сразу через
движок (вторая поправка D3). **Пачка 2** — интерфейс (`:feature:launcher`, `:feature:settings`, `:feature:assistant`,
`core/ui`, `core/android`). **Пачка 3** — память: слот выученной формы по формам слова.

**Стек:** Kotlin 2.4.10, KMP `:domain` (`commonMain` / `jvmTest`), Jetpack Compose, Hilt, Room (схема 5 — **не
меняется**), DataStore, Roborazzi.

**Вход (спеки нет — это хвост закрытого блока):** ADR «2026-11-21 — Этап 6 (A4′) — рантайм полностью…»
(`ai-context/decisions.md`), разделы «Двенадцать решений владельца» и «Ответы владельца 2026-11-21»; решения этой
сессии — §0.2 ниже, дословно.

## Глобальные ограничения

- `:domain` — чистый Kotlin (`commonMain`): ничего JVM- или Android-специфичного; тесты — `jvmTest`.
- Новое значение закрытой суммы `commonMain` стоит правок во всех исчерпывающих `when` вне `:domain` — перечислено в
  задаче, которая его вводит.
- Текст для пользователя — только `sidrString(R.string.…)` в feature-слое; `en`/`ru`/`tr` — в том же коммите
  (`LocaleCompletenessGuardTest`); турецкие строки — на суд владельца до приёмки.
- Подписанные строки Class B (`strings_locked.xml`) агент не правит без подписи владельца; переподпись — до
  `:app:assembleRelease` (`checkOwnerReviewedLocaleStrings`).
- `ToolExecutor` — единственный путь к миру; согласие на рискованный шаг (`requiresConsent(risk)`, `DOC-ADL-1`)
  не пропускается ни одной задачей.
- `localOnlyMode` / без провайдера / без сети: модель не спрашивается и ничего не уходит с устройства (`DOC-ADL-3`).
- Гейт: `SIDR_JDK17=/usr/lib/jvm/java-17-openjdk tools/gate.sh` (граничный, пол **1918**); scoped —
  `tools/gate.sh --scoped :module:task …`; `core/ui` тронут → `:core:ui:verifyRoborazziDebug`; Class B тронут →
  `:app:assembleRelease`. Числа — из JUnit XML. Никогда `| tail`, никогда `--rerun`.
- Коммит легитимной правки — **до** мутации того же файла; восстановление мутации — `trap … EXIT` + `git checkout --`.

## Фокус ревью

1. **Состояние сети меняется между гейтом и ответом.** Команда ушла к модели (FastPath уступил), а модель недоступна
   (ключ, тайм-аут, сеть): пользователь получает то, что сделал бы FastPath (поиск / план магазина), а не пустоту и не
   «модель недоступна» на месте работающего поиска. Тесты — задачи 1 и 2.
2. **Рискованный шаг в прочтении модели без карточки толкования.** `uninstall_app`, предложенный моделью, обязан
   остановиться на карточке согласия движка — ни одна ветка «сразу через движок» не исполняет `CONFIRM`/`DESTRUCTIVE`
   без касания. Тест и мутация — задача 4.
3. **Алиас, совпадающий с командой FastPath или с меткой** («помощь», «камера»): простая команда FastPath («помощь»)
   алиасом не перехватывается (голый алиас — только при нерешённом исходе); метку и триггер 2b алиас **перехватывает**
   (K10 — явная память пользователя). Тесты — задача 3.
4. **Выученная форма, у которой «форма слова» совпала дважды** («поищи погоду и погода»): не учится, а не учится
   неверно. Тест — задача 13.
5. **Будильник, поставленный не часами** (Find My Mobile на этом телефоне ставит `setAlarmClock`): чипа нет. Тест —
   задача 8.

---

## §0. Перед планом

### §0.1. Замер по правилу 2 (2026-11-21, до плана)

**Корпус `B15`.** Файлы замера (`data/repository/src/test/resources/b15/{corpus.txt,shortcuts.jsonl,apps.jsonl}`) на
этой машине **отсутствуют** (git-ignore; клон свежий). **16 из 25** строк корпуса цитируются дословно в закоммиченных
документах ([B15](2026-09-20-b15-selection-decline.md) §7.1–7.2, [фаза 1](2026-09-26-a4-phase1-fastpath-overflow.md)
таблица 13 строк); остальные 9 — `DECLINE_NO_MATCH`, FastPath их не заявлял, поэтому сужение рукавов FastPath их не
меняет. Прогон настоящего `RuleBasedIntentMatcher` на дереве `91ce5447` (одноразовый тест, удалён):

| Строка | Сейчас | Слов в запросе | Что должно быть |
|---|---|---:|---|
| найди в интернете лекцию по rust (корпус) | `SEARCH` 0.90 | 5 | уйти дальше |
| найди мне лекцию по rust в интернете (приёмка §A1) | `SEARCH` 0.90 | 6 | уйти дальше |
| найди классный фильм на youtube (корпус) | `SEARCH` 0.90 | 4 | уйти дальше |
| найди мне работу (корпус) | `SEARCH` 0.90 | 2 | уйти дальше |
| найди лучший рецепт шашлыка (корпус) | `SEARCH` 0.90 | 3 | верный поиск — **результат тот же** |
| найди рецепт хлеба и соли (закреплена тестом) | `SEARCH` 0.90 | 4 | верный поиск — результат тот же |
| search how to download and install python (закреплена) | `SEARCH` 0.90 | 6 | верный поиск — результат тот же |
| найди … и сделай бронь / и скачай ее (корпус) | `SEARCH` 0.30 | 6–9 | уже уходит (фаза 1) |
| открой новости в телеграмм / открой онлайн-банк (корпус) | `LAUNCH` → `NoAppFound` → магазин | — | уйти к модели (задача 14) |

**Вывод замера:** правило по длине остатка не отделяет «лекцию по rust» (5) от python (6) — формулировка задачи 13
«длинный остаток» правилом стать не может. Владелец выбрал **Б**: в сети уступает **любой** поиск FastPath (§0.2).

**`intent_match` с телефона** (`tools/device/pull-agent-db.sh`, агентом с разрешения владельца): 69 строк, все
2026-09-20…2026-10-05 — `UNKNOWN` 0.10 ×45, `LAUNCH_APP` 0.90 ×12, `SEARCH` 0.90 ×12 (текст — заглушка). Ни одного
`SEARCH` ниже 0.90: каждое «найди …» владельца FastPath брал уверенно. С включения записи 2026-11-21 строк нет (команд
не набирали; `app_usage` в тот же день писался — флаг включён). Проверка предсказаний — перед приёмкой (задача 14 пачки
«закрытие»).

**`app_usage` с телефона** (для задачи 15): 30 приложений; первые два — 215 и 200 запусков; **восьмое место — 14**;
избранное = топ-8 по числу запусков за всё время (`LauncherAppList.deriveFavorites`, `AppUsageDao`
`ORDER BY launch_count DESC, last_used_epoch_ms DESC`), считаются только запуски через Sidr при включённой истории.

### §0.2. Решения владельца 2026-11-21 (эта сессия) — дословно по смыслу, адрес ответа — разговор сессии

- **13: Б** — когда модель доступна (не local-only, провайдер есть, сеть есть), поиск FastPath не выполняется, а
  уходит к модели; без модели FastPath ищет как сейчас.
- **13 / D3: «да» поправке** — свежее прочтение модели стартует сразу через движок, если `needsCard()` ложен (нет
  выбора, нет текста модели, уходящего наружу); рискованный шаг — карточка согласия всегда. Это **вторая поправка
  D3** (первая — «выученная команда», 2026-11-20).
- **2: б** — строка отказа: **«Понял команду, но Sidr так пока не умеет.»** (`en`/`tr` — §Задача 5).
- **5: а** — чип будильника в строке даты, **в начале, у самого левого края**; дата остаётся по центру.
- **Нарезка — тремя пачками**, одна приёмка в конце.
- **14: «да»** (новая задача) — в сети «приложение не найдено» сначала спрашивает модель («открой клод» → Claude,
  «открой cloude» → Claude), магазин — только если и модель не нашла.
- **15: а** (новая задача, вторая пачка) — избранное: закреплённые пользователем + остальные по использованию со
  свежестью; окно выбора и удаления по касанию заголовка «Избранное»; **число мест — до 12**.
**Ответы владельца 2026-11-22 — на находки ревью плана** (`.superpowers/sdd/2026-11-21-a4-acceptance-tail/plan-review.md`,
git-ignored; суть — здесь):
- **C1 → поправка D1: «Исправление — сразу запуск».** Мост отклонял имя приложения не словами пользователя
  (`BridgeError.UNDERIVABLE_MENTION`, `BridgeValidator.kt:117`; `launch_app.app` — `mention = true`), а свёртки-транслита
  нет (`TextFolding.kt:14`) — поэтому «открой клод» не связалось бы ни при каком ответе модели. Теперь: упоминание
  приложения, которого нет в словах команды, **допускается** как `ValueOrigin.ModelGenerated` и связывается на
  устройстве **только с точной меткой установленного приложения или алиасом** (никаких форм слова от слова модели);
  одно совпадение — запуск сразу (эффект `LOCAL`, `needsCard()` ложен); ничего — K3 (магазин по словам пользователя);
  несколько — выбор. Это поправка D1 / спеки A4′ §7.12.1 (механизм 1, B-17) — ADR в задаче 2.
- **C4 → строка local-only переподписана (`ru`)**, текст дословно: «Выключено: при подключении к сети команды, которые
  лаунчер не выполнил сам, а также поиск и запуск приложения, которого нет на телефоне, отправляются вашему ИИ-провайдеру
  для понимания. Включено: вашему ИИ-провайдеру ничего не отправляется, команды понимаются только на этом устройстве.
  В обоих случаях рискованный шаг подтверждается всегда, а понятую команду вы видите на карточке до выполнения, если
  нужно выбрать или если наружу уйдёт текст, написанный моделью. Поиск и ссылки, о которых вы просите, открываются в
  браузере.» (заменяет черновик задачи 4 шага 0; `en`/`tr` — по нему, турецкий — на суд).
- **I12 → «простая — закрывается сама»:** сессия из **одного** `SAFE`-шага, которой нечего отменять, по завершении
  убирает свою карточку с Home; многошаговая, с рискованным шагом или с «Отменить» — остаётся, как сейчас. Строится в
  пачке 1 (задача 4), не в задаче 8.
- **I5 → «не засчитывать»:** сессия, стартовавшая без карточки толкования, **не учит** — `learnable` не передаётся и не
  записывается; учится только прочтение, подтверждённое «Выполнить» на карточке (как B-8 первой поправки). Иначе три
  «найди …» закрепили бы форму «найди <слот>» и кэш отменил бы 13: Б. Подписанная `launcher_forget_learned_command_consequence`
  («…подтвердите её ещё три раза») остаётся истинной.

- Пожелание владельца «значки уведомлений на иконках Home» — **не в этом плане** (владелец 2026-11-21: «согласен, отдельным блоком»):
  **Разделено владельцем 2026-11-21 («а»):** (1) **значки на иконках** — малый блок сразу после хвоста: функция
  лаунчера, из уведомления — только пакет и число, текст не нужен; (2) **«уведомления как источник агента»** — в
  **A6** (гранты и автоматизация, рядом с `B8` «события как источник целей» и типом `Grant`), после локальной модели
  `B16`; к части (2) относится рамка ниже. **Рамка уточнена владельцем** («в чём проблема читать все уведомления — Sidr должен стать
  агентной ОС»): блок — **«уведомления как источник агента»**, а не «только число». `NotificationListenerService` по
  гранту; чтение **целиком, на устройстве** (значки, поиск по уведомлениям, «что пришло от …» без модели); текст —
  **внешнее содержимое**: данные, никогда не инструкция (prompt injection — главный риск), не дольше жизни уведомления,
  вне Auto Backup; грант не снимает гейтов (`DOC-HMA-5`). **В облако — никогда** (владелец 2026-11-21: «в облако вообще
  не должны уходить данные с телефона — сообщения, банки и др.; для этого предусмотрена локальная модель»): понимание
  текста уведомлений — только локальной моделью (ADR 2/4, LiteRT / LiteRT-LM; адрес `B16`, не построен), до неё — только
  детерминированное (значки, поиск по словам, показ); сентинел-гвард на текст уведомления в теле моста — обязателен.
  Без гранта — как сейчас. В коде сегодня нет ничего, что читает уведомления.
- Предложение владельца «окно агента / лёгкий редактор / заметки» — **не в этом плане**: записывается в `§HANDOFF`
  входом в спеку A2/A3 рядом с задачами 11–12 (видение агента — там же).

### §0.3. Решения контроллера плана — владелец перерешает на ревью плана

| # | Решение | Почему | Где |
|---|---|---|---|
| K1 | Уступивший FastPath при недоступной модели (`Declined`, `Unavailable`) **исполняет то, что сделал бы** (поиск; план магазина) | иначе Б отнимает работающий поиск при сломанном ключе / тайм-ауте (40 с ожидания и ничего) | задачи 1, 2 |
| K2 | Задача 14 распространяется и на шаг **2b**: `Unresolved` приложения **без кандидатов** в сети идёт к модели («удали приложение клод») | тот же класс; без этого «удали клод» остаётся «не найдено». **Частично обращает развилку 3b-E** (R14-39: «никогда не шаг 7») — только для сети и только для пустого `Unresolved` приложения; local-only как было | задача 2 |
| K3 | Если модель назвала приложение, которого нет (`Binding.NotFound`), — план магазина A0 **по словам пользователя**, не по имени модели | слова модели в магазин не уходят (D1, egress) | задача 2 |
| K4 | Сессия, стартовавшая из свежего прочтения без карточки, несёт метку «Предложено моделью» на карточке сессии (поле `proposedByModel` у `AgentSessionStarted`, не новое значение суммы) | `DOC-NYH-1` «показывает, как поняла» — после, а не до; прецедент — метка «Выученная команда» | задача 4 |
| K5 | Равная ширина кнопок — параметр `equalWidth` у `SidrSurfaceActions`, включён только на карточке законченного плана | шесть других мест вызова не меняются (всего семь, ревью M8; седьмое — сам `SidrResultSurface`, `:198`), золотые меняются только у этой карточки | задача 9 |
| K6 | Выученные алиасы — две вкладки на экране алиасов («Мои алиасы» / «Добавить»), на `SidrFilterChip`; новых компонентов `core/ui` нет | наименьшая правка; отдельный экран — новый маршрут | задача 11 |
| K7 | Касание чипа будильника — сессия с одним шагом `show_alarms` через движок (как 2b), не прямой `PendingIntent` | `ToolExecutor` — единственный путь к миру; `OneModelPathGuardTest` держит единственное прямое исполнение — чип WEB | задача 8 |
| K8 | Избранное: закреплённые — упорядоченный список пакетов в DataStore; скрытые — множество; свежесть **14 дней** по `last_used_epoch_ms`; места по умолчанию — **12**, варианты 4/6/8/10/12 | без схемы Room; 14 дней — предложено в разговоре, владелец не возразил | задача 12 |
| K9 | Задача 6 — сигнал «модель спрошена» **портом** `ModelAskedSignal` (`domain/ai/router`), который `RouteCommandUseCase` зовёт перед `modelPlanner.plan`, а лаунчер наблюдает; индикатор сразу при вызове, без таймера 300 мс. **Не** параметром `route(…)`: `CommandRouteStep` / `ResolvedCommandStep` — `fun interface`, их одиночный член не берёт значения по умолчанию, и смена арности тронула бы 31 + 8 SAM-лямбд (ревью плана I8) | точное значение «только пока спрошена модель», малый радиус | задача 6 |
| K10 | **Алиас — раньше метки, везде** (ревью I7): `AppTargetResolver` уже так делает (`:87-91`, тест `an alias wins over a label and is matched on its normalized phrase`) — FastPath (`IntentActionResolver`) и мост берут тот же порядок; голый алиас (шаг «1a») стоит **выше** 2b и затеняет триггер инструмента («камера» как алиас → его пакет, не `open_camera`) | алиас — явная память пользователя (владелец: «алиасы не спрашивать»); один порядок вместо двух | задача 3 |
| K11 | Шаг 2b, ушедший к модели по K2, при `Declined` / `Unavailable` / `Binding.NotFound` отвечает **на устройстве**, как сегодня (`answerOnDevice(target)` — «нет такого приложения «клод»») | иначе K2 отнимает честный ответ (ревью I2) | задача 2 |
| K12 | `:consumer:jvm` получает ту же вторую поправку D3 (`ConsoleHarness.interpret` стартует сразу при `!needsCard()`, как `replayOrInterpret` → `startAtOnce` первой поправки) | паритет потребителей (ревью I6) | задача 4 |
| K13 | Тестовая инфраструктура (ревью I9): Robolectric + compose ui-test в `core/android`, `feature/settings`, `feature/assistant` — шаг той задачи, что первой их требует; чип будильника — компонент `core/ui` (`SidrNextAlarmChip`) со своим Roborazzi-сценарием; размещение на Home — compose-семантический тест в `:feature:launcher` (Robolectric там есть); **золотых Home нет и не заводятся** | наименьшая инфраструктура; Roborazzi только в `core/ui` | задачи 8, 10, 11, 12 |
| K14 | Состояния «local-only / провайдер / сеть» читаются **один раз** в начале `route` и переиспользуются шагами 3/5/6 — ветки «путь закрылся между гейтом и ответом» не существует (ревью M7) | нет непроверяемой гонки | задача 1 |

### §0.4. Пачки, порядок, места

| Пачка | Задачи плана (решение владельца) | Ревьюер | Гейт пачки |
|---|---|---|---|
| **1 — маршрут и карточка** | 1 (13), 2 (14), 3 (3), 4 (1 + D3), 5 (2), 6 (6) | **`reviewer-deep`** у 1–4 (согласие, локальный путь, egress); `reviewer` у 5–6 | граничный + `:app:assembleRelease` (Class B) |
| **2 — интерфейс** | 7 (4), 8 (5), 9 (7), 10 (9), 11 (10), 12 (15) | `reviewer`; 8 — `reviewer-deep` (новый источник в Home, путь к миру) | граничный + `:core:ui:verifyRoborazziDebug` |
| **3 — память** | 13 (8) | `reviewer-deep` (форма учится → повтор без модели) | граничный |
| **Закрытие** | 14 — замер после, чеклист приёмки, приёмка владельца, чеклист закрытия §0 | — | граничный финальный |

Исполнитель: `implementer-critical` у задач 1–4, 6, 8, 13; `implementer` у остальных. Новые гварды — к `mutation-prover`
(у 1, 2, 4 — `mutation-prover-deep`: согласие и egress).

**Цепочка счёта** — не предсказывается числом заранее (ревью M5: прикидки плана расходились с перечнем тестов): каждая
задача в отчёте пишет дельту по модулю **из JUnit XML** (новые + перевёрнутые, переименований нет); разбивка по модулям
выводится **до** каждого граничного прогона и сверяется с суммой дельт задач пачки.

---

## Пачка 1 — маршрут команды и карточка

### Задача 1: FastPath уступает поиск модели, когда она доступна (решение 13: Б)

**Файлы:**
- Изменить: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/HandleUserCommandUseCase.kt`
- Создать: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/FastPathAnswer.kt`
- Изменить: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/router/RouteCommandUseCase.kt` (шаги 1, 3–7)
- Тесты: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/intent/HandleUserCommandUseCaseTest.kt`,
  `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/ai/router/RouteCommandUseCaseTest.kt`

**Интерфейсы:**
- Производит:
  ```kotlin
  // FastPathAnswer.kt
  sealed interface FastPathYield {
      data class Search(val intent: LauncherIntent.SearchIntent) : FastPathYield
      data class MissingApp(val message: CommandMessage.NoAppFound) : FastPathYield   // задача 2
  }
  data class FastPathAnswer(val outcome: CommandOutcome, val yielded: FastPathYield? = null)
  // HandleUserCommandUseCase
  suspend fun answer(rawInput: String, yieldToModel: Boolean): FastPathAnswer
  suspend fun handle(rawInput: String): CommandOutcome = answer(rawInput, yieldToModel = false).outcome
  suspend fun runYielded(yielded: FastPathYield.Search): CommandOutcome   // resolve → execute, без записи intent_match
  // RouteCommandUseCase
  // три состояния читаются один раз в начале route (K14): localOnly, provider, online; open = !localOnly && provider != null && online
  ```
- `FastPathYield` — закрытая сумма, но её `when` живут только в `RouteCommandUseCase` (`:domain`); вне `:domain`
  её никто не видит — правило «закрытая сумма через модули» не срабатывает.

- [ ] **Шаг 1: падающие тесты `HandleUserCommandUseCaseTest`**
  - `a confident search yields to the model when asked to - nothing executes`: `answer("найди лучший рецепт шашлыка",
    yieldToModel = true)` → `outcome == CommandOutcome.LowConfidence`, `yielded == FastPathYield.Search(SearchIntent(
    "лучший рецепт шашлыка", WEB))`, фейк-исполнитель вызван **0** раз.
  - `a confident search is executed as before when the model is not reachable`: то же с `yieldToModel = false` →
    `CommandOutcome.Executed`, исполнитель вызван 1 раз, `yielded == null`.
  - `a yielded search records the matcher's confidence`: запись `intent_match` — `SEARCH`, `0.90` (решение плана:
    уступка — маршрут, а не уверенность; ряды сопоставимы с 12 прежними).
  - `runYielded executes the search and records nothing`: исполнитель 1 раз, история 0 записей.
  - `a launch, a setting and a simple command never yield`: «открой калькулятор» (установлен), «настройки», «помощь»
    с `yieldToModel = true` — исход как при `false`.
- [ ] **Шаг 2: прогнать — RED**
  `tools/gate.sh --scoped :domain:jvmTest` → `FAILED ::` ровно пять новых (нет `answer` — ошибка компиляции считается
  RED всего модуля; тогда сначала заглушка `answer = TODO()`, и RED — пять).
- [ ] **Шаг 3: реализовать `answer` / `runYielded`.** Уступка — только `LauncherIntent.SearchIntent` после гейта
  уверенности (`shouldAutoExecute`), до `resolver.resolve`; `handle` делегирует в `answer(…, false)`.
- [ ] **Шаг 4: падающие тесты `RouteCommandUseCaseTest`**
  - `online with a provider a FastPath search goes to the model, not the browser`: фейк-модель отвечает `Proposed`
    `web_search` → исход `Interpreted` (до задачи 4) / `AgentSessionStarted` (после), исполнитель FastPath 0 вызовов,
    модель спрошена 1 раз.
  - `local-only a search runs on FastPath and the model is never asked` (паритет `DOC-ADL-3`), `no provider …`,
    `offline …` — `Executed`, модель 0 вызовов, транспорт 0 тел.
  - `a yielded search whose model is unavailable runs the FastPath search` (K1): `ModelPlanning.Unavailable(TIMED_OUT)`
    → `Executed`, `runYielded` 1 раз.
  - `a yielded search the model declines runs the FastPath search` (K1).
  - `a yielded search the model sends to the assistant opens the assistant` — `ToAssistant` → `OpenAssistant`.
  - Существующий `all three causes at once - only the outermost is reported` — **не меняется** (проверка, не правка).
  - **Переворачиваются (ревью I1), с новым ассертом:** `:data:repository` `FastPathOverflowSeamTest > a search whose
    conjunction joins two nouns is still run by FastPath and the model is not asked` — фикстура онлайн с провайдером →
    теперь модель спрошена 1 раз; **не переименовывать** (тест — якорь `DOC-NYH-4` в матрице): тело проверяет новое
    поведение для сети, а паритетный близнец `… in local-only …` добавляется рядом и держит «FastPath ищет, модель не
    спрошена»; ячейка `DOC-NYH-4` матрицы называет оба.
- [ ] **Шаг 5: прогнать — RED** (`:domain` и `:data:repository`, по вызову на модуль).
- [ ] **Шаг 6: реализовать в роутере.** Три состояния читаются **один раз** в начале `route` (K14): `localOnly`,
  `provider`, `online`; `open = !localOnly && provider != null && online`; `handleUserCommand.answer(raw, open)`; шаги
  3/5/6 используют те же значения (ветки «путь закрылся» нет); шаг 7 — `Declined` и `Unavailable` при `yielded is
  Search` → `handleUserCommand.runYielded(it)`. `isUndecided` не меняется. KDoc класса: новый пункт «1′ — уступка»,
  ссылка на это решение; абзац про паритет — «local-only: уступки нет, FastPath ищет байт в байт».
- [ ] **Шаг 7: GREEN** — scoped `:domain:jvmTest` и `:data:repository:testDebugUnitTest`; дельта по модулям — из XML.
- [ ] **Шаг 8: коммит** `feat(a4-tail/13): в сети FastPath уступает поиск модели, без модели ищет сам`.
- [ ] **Шаг 9: мутации (к `mutation-prover-deep`, после коммита):**

| Посадка | Предсказанный `FAILED ::` |
|---|---|
| M1-1 `answer`: `yieldToModel` игнорируется (всегда `false`) | `a confident search yields…`, `online with a provider a FastPath search goes to the model…` |
| M1-2 `open` вычисляется без `localOnly` | `local-only a search runs on FastPath…` |
| M1-3 шаг 7: `Unavailable` → сообщение как раньше (без `runYielded`) | `a yielded search whose model is unavailable…` |
| M1-4 `runYielded` записывает `intent_match` | `runYielded executes the search and records nothing` |

  Чистый перепрогон из очищенного дерева — до коммита записи мутаций в леджер.

### Задача 2: «приложение не найдено» в сети спрашивает модель, и модель может исправить имя (решение 14 + поправка D1)

**Файлы:**
- Изменить: `HandleUserCommandUseCase.kt` (уступка `NoAppFound`), `RouteCommandUseCase.kt` (шаги 2, 2b, 7)
- Изменить: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/bridge/BridgeValidator.kt` (`UNDERIVABLE_MENTION`
  сужается: упоминание **приложения** не из слов пользователя допускается с `ValueOrigin.ModelGenerated`), биндер
  `data/repository/.../agent/DeviceMentionBinder.kt` (+ `consumer/jvm/.../SandboxMentionBinder.kt`, если правило живёт в
  общем коде — проверить): `ModelGenerated`-упоминание связывается **только** с точной меткой (после `TextFolding`) или
  точным алиасом, формы слова от слова модели — нет
- Изменить: `domain/src/jvmTest/.../agent/AgentEgressSentinelGuardTest.kt` (ревью C2), матрица
  `docs/governing/sidr-doctrine-matrix-v1.0.md` (`DOC-ADL-3`, `DOC-ILM-2`/D1-ячейки — найти по `UNDERIVABLE`) + журнал §6
- Тесты: `HandleUserCommandUseCaseTest.kt`, `RouteCommandUseCaseTest.kt`, `BridgeValidatorTest.kt`,
  `DeviceMentionBinderTest.kt`, шов через **настоящие** `BridgeModelPlanner` + `BridgeValidator` (фейк-транспорт) —
  в `:data:repository` рядом с `FastPathOverflowSeamTest`

**Интерфейсы:**
- Потребляет: `FastPathYield.MissingApp`, `answer`, три состояния (задача 1).
- Производит: `private suspend fun storePlan(raw: String, query: String): CommandOutcome` — бывший шаг 2: сессия
  `AppNotInstalled(query)` или, если план не стартовал, `Message(NoAppFound(query))` (байт в байт как сегодня).
  Шаг 2 зовёт его при `!open`; при `open` — фолбэк (K1, K3). Шаг 2 читает **`yielded is MissingApp`**, а не
  `ruleOutcome` (ревью M4: уступивший исход — `LowConfidence`).

- [ ] **Шаг 1: падающие тесты `HandleUserCommandUseCaseTest`:** `a launch of a name that is not installed yields when
  asked to` → `LowConfidence`, `yielded == MissingApp(NoAppFound("клод"))`; без уступки — `Message(NoAppFound("клод"))`
  как сейчас; `a launch whose name carries a second goal stays LowConfidence with nothing yielded` (фаза 1 не меняется).
- [ ] **Шаг 2: падающие тесты валидатора и биндера (поправка D1):**
  - `BridgeValidatorTest > an app the user did not name is admitted as the model's` — «открой клод», ответ `launch_app
    app = "Claude"` → `Valid`, origin `ModelGenerated`; `… a non-app mention the user did not type is still refused` —
    аргумент другого сорта с `mention = true` → `UNDERIVABLE_MENTION` как сейчас.
  - `DeviceMentionBinderTest > a model-written app name binds only to an exact label` — «Claude» ↔ метка «Claude» →
    `Bound`; «Claud» (край) → `NotFound` (формы слова только для слов пользователя); `… binds to an exact alias`;
    `… two exact matches are a choice`.
- [ ] **Шаг 3: падающие тесты `RouteCommandUseCaseTest`:**
  - `online a missing app goes to the model and binds on the device` — план магазина **не** стартовал.
  - `online the model names an app that is not here - the store plan, in the user's words` (K3) → сессия
    `AppNotInstalled("клод")`.
  - `online a missing app the model declines - the store plan` и `… model unavailable - the store plan` (K1).
  - `online an unresolved 2b app with no candidates goes to the model` (K2) — «удали приложение клод».
  - `online an unresolved 2b app the model declines / cannot answer / names something absent - no such app in the user's
    words` (K11) → `Message(NoAppFound("клод"))`.
  - `online an unresolved 2b target WITH candidates is still a pick on the device` — модель 0 вызовов.
  - `an unresolved 2b target is answered on the device and the model is never asked` (онлайн-фикстура; цитируется
    ячейкой `DOC-ADL-3` и KDoc `RouteCommandUseCase.kt:129-131`) — его свойство **обращено** решением K2, поэтому он
    **удаляется, не переименовывается** (ревью I3: переименование краснит `DoctrineMatrixGuardTest`, а local-only близнец
    `in local-only mode an unresolved 2b target is still answered on the device`, `:861`, уже есть). В том же коммите
    ячейка матрицы, журнал §6 и KDoc переводятся на близнеца `:861` и на новый онлайн-тест K2.
  - **Переворачиваются (ревью I1):** `RouteCommandUseCaseTest > the deterministic paths never get an interpretation card`
    («открой убер» теперь спрашивает модель, `:725` — убрать строку «открой убер» из детерминированного набора или
    поставить её в local-only), `> a text FastPath or 2b decided never consults the learned plans` (`:1248-1254`, K2);
    `:data:repository` `FastPathOverflowSeamTest > a launch naming nothing installed still becomes the A0
    launch-then-store plan, byte for byte` (онлайн → модель спрошена; тот же приём, что в задаче 1: тело — сеть,
    близнец local-only — байт в байт), `AgentActingSeamTest > R14-39 closed - …` (онлайн K2; близнец local-only);
    `:feature:launcher` `LauncherViewModelTest > a 2b target that is not found reads no such app with no wait`
    (`claimingRoute` — проверить, идёт ли через настоящий роутер; если да — local-only).
- [ ] **Шаг 4: егресс-гвард (ревью C2).** `AgentEgressSentinelGuardTest > local-only off and online - the whole
  session runs and nothing leaves the device` — **переписывается намеренно**, не ослабляется: онлайн с провайдером модель
  спрошена **ровно один раз** с командой (новый, принятый владельцем исход), **сессия, что идёт следом, не отправляет
  больше ни одного тела** (счёт тел за всю сессию = 1); четыре локальных состояния — 0, как были. Имя теста меняется на
  `… asks the model once and the session sends nothing more` — ячейка `DOC-ADL-3` («ни в одном из пяти состояний» →
  «в четырёх локальных; в сети — один вопрос модели, дальше ничего») + строка журнала §6.
- [ ] **Шаг 5: шов (ревью C1 — без него задача вакуумна).** `:data:repository` `MissingAppToModelSeamTest > open klod
  binds Claude through the real bridge`: настоящие `BridgeModelPlanner` + `BridgeValidator` + `DeviceMentionBinder`,
  фейк-транспорт отвечает `launch_app app = "Claude"`, установлено «Claude» → `AgentSessionStarted` (после задачи 4) /
  `Interpreted` (до); `… cloude binds Claude` — то же; `… the model's own word never reaches the store` — фейк отвечает
  «Claudex» (не установлен) → сессия `AppNotInstalled("клод")`.
- [ ] **Шаг 6: RED** по `:domain`, `:data:repository`, `:feature:launcher` (по вызову на модуль).
- [ ] **Шаг 7: реализовать.** `answer`: при `yieldToModel` `Message(NoAppFound)` без второй цели → `LowConfidence` +
  `MissingApp`. Роутер: шаг 2 — `!open` и FastPath `NoAppFound` → `storePlan`; 2b `Unresolved` (`sort == APP`, без
  кандидатов, читаемый) при `open` — дальше, с запомненным `target`; шаг 7: для `MissingApp` — `NotFound` / `Declined` /
  `Unavailable` → `storePlan(raw, query)`; для 2b-`target` — `answerOnDevice(target)` (K11). KDoc шагов 2 и 2b. ADR
  поправки D1 в конец `decisions.md` («2026-11-2x — D1, поправка: имя приложения, исправленное моделью») + строка в
  ориентире: что поправка делает ложным (`Derivability` KDoc «exact after folding … relating a typed form to a label is
  the binder's job», спека §7.12.1 механизм 1, B-17) и где исправлено; расширение исходящего названо: «в сети
  распознанный запуск отсутствующего приложения уходит модели».
- [ ] **Шаг 8: GREEN** по трём модулям; `:app:testDebugUnitTest` (`DoctrineMatrixGuardTest`, `BridgePayloadGuardTest`).
- [ ] **Шаг 9: коммит** `feat(a4-tail/14): в сети «нет такого приложения» спрашивает модель; модель может исправить имя`.
- [ ] **Шаг 10: мутации (`mutation-prover-deep`):** M2-1 шаг 2 стартует план магазина и при `yielded is MissingApp`
  (уступка игнорируется) → `online a missing app goes to the model…`; M2-2 `NotFound` → `NoAppFound` слова модели вместо
  `storePlan` → `… the store plan, in the user's words`, `… the model's own word never reaches the store`; M2-3 2b
  уступает и с кандидатами → `… WITH candidates is still a pick…`; M2-4 биндер связывает `ModelGenerated` по краю →
  `a model-written app name binds only to an exact label`; M2-5 K11 снят (2b-цель → сообщение модели) →
  `… no such app in the user's words`.

### Задача 3: алиас — до модели, раньше метки, и его формы слова (решение 3; K10)

**Файлы:**
- Изменить: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/IntentActionResolver.kt` (рукав запуска FastPath
  читает алиас **раньше** метки, K10), `RouteCommandUseCase.kt` (голый алиас — шаг «1a», конструктор
  `aliases: AliasStore = AliasStore.NONE`-подобный умолчательный — прецедент B-12 `learnedPlans = LearnedPlans.NONE`;
  если `NONE` нет — завести в `domain/memory/alias`), проводка `app/src/main/java/com/sidr/launcher/di/IntentProvidesModule.kt`
  (`provideIntentActionResolver`) и `BridgeProvidesModule.provideRouteCommandUseCase` — **параметры с умолчанием**, иначе
  краснеют компиляцией `TextFoldingBindingGuardTest:84` и `BridgePayloadGuardTest:285` (ревью M3, C3)
- **Не меняется:** `AppTargetResolver` — уже читает алиас первым (`:87-91,153-160`) и держится тестом `an alias wins over
  a label and is matched on its normalized phrase` (ревью I7); формы слова алиаса в слоте 2b — **проверить** по
  `AppTargetResolverTest`; если их нет — добавить тест и правку туда же (тогда файл меняется).
- `ResolveCommandWithAliasUseCase.kt` — его ветка `Unknown` после задачи 5 мертва в сети для **любого** ввода (ревью M15),
  офлайн — для голого алиаса; не удалять, KDoc: «с хвоста A4′ — запасной путь; кандидат на удаление решает владелец»
  (вместе с `CommandFeedback.UnknownCommand`, мёртвым в продакшене онлайн).
- Тесты: `IntentActionResolverTest.kt`, `AppTargetResolverTest.kt` (регрессионная фиксация), `RouteCommandUseCaseTest.kt`.

**Интерфейсы:**
- Потребляет: `AliasStore.find(phrase): OperationResult<Alias?>` (`domain/memory/alias/AliasStore.kt:7`; тип — `Alias`,
  ревью M1), `appPackageOrNull()`, `AppNameMatcher.wordsMatch`.
- Производит: `IntentActionResolver(repository, folding, aliases: AliasStore? = null)`; в роутере
  `private suspend fun aliasLaunch(raw: String): CommandOutcome?` — однoшаговый план `launch_app` с пакетом алиаса через
  `startAgentSession.startPlanned` (путь 2b), `null` — нет алиаса / не установлен / старт не удался.

- [ ] **Шаг 1: падающие тесты:**
  - `IntentActionResolverTest > open with an alias launches the alias target` — «открой tg» → `LaunchAppAction(
    org.telegram.plus)`; `… an alias whose app is gone is not found` → `NoAppFound`.
  - `IntentActionResolverTest > an alias beats an installed label of the same words` (K10; та же сторона, что
    `AppTargetResolver`) — алиас «камера» → пакет X и приложение «Камера» → запускается X.
  - `IntentActionResolverTest > a word form of an alias launches it` (владелец: «алиасы не спрашивать — лишний слой»;
    телефон 2026-11-21: «открой телегу» при алиасе «телега» ушло в магазин) — край `AppNameMatcher.wordsMatch(typed,
    aliasPhrase) == false` → `LaunchAppAction`, без «Вы имели в виду».
  - `IntentActionResolverTest > a word form that fits an alias and another app's label is a choice` — край алиаса и
    край/точная метка **другого** пакета → `AmbiguousAppAction` с обоими, ничего не запущено.
  - `RouteCommandUseCaseTest > a bare alias starts its launch on the device and the model is never asked` — «tg» →
    `AgentSessionStarted`, модель 0 вызовов, в сети и в local-only (владелец принял алиасы в local-only).
  - `RouteCommandUseCaseTest > a bare alias never shadows a decided FastPath outcome` — алиас «помощь» → исход `Help`
    (шаг «1a» — только при `isUndecided()`).
  - `RouteCommandUseCaseTest > a bare alias shadows a 2b tool trigger` (K10) — алиас «камера» → его пакет, `open_camera`
    не спланирован.
  - `RouteCommandUseCaseTest > the words of an alias never reach the model` — транспорт 0 тел на «tg» (принцип 2).
- [ ] **Шаг 2: RED** — `--scoped :domain:jvmTest`, затем `:data:repository:testDebugUnitTest`.
- [ ] **Шаг 3: реализовать.** Порядок в `IntentActionResolver`: точный алиас → точная метка → край алиаса (запуск) /
  край метки (предложение, B-2) / край алиаса + метка другого пакета (выбор) → `NoAppFound`. Замер телефона 2026-11-21
  (`intent_match`): «открой телега», «открой tg», «открой телегу» — `LAUNCH_APP` 0.90 → магазин; голые «tg» / «телега» —
  `UNKNOWN`; алиасы `tg`, `телега` → `org.telegram.plus` на месте. Роутер: шаг «1a» — после FastPath, только при
  `isUndecided()`, **выше** 2b; стоит выше шага 3 — это `DOC-ADL-3` применённый (детерминированная память меняет исход,
  который FastPath не решил; модель не спрошена, ничего не ушло) — абзац в KDoc класса, назвать в ADR пачки.
- [ ] **Шаг 4: GREEN** по `:domain`, `:data:repository`, `:app` (компиляция гвардов).
- [ ] **Шаг 5: коммит** `feat(a4-tail/3): алиас — до модели и раньше метки, формы слова алиаса запускают`.
- [ ] **Шаг 6: мутации:** M3-1 шаг «1a» ниже шага 7 → `a bare alias … the model is never asked`, `the words of an alias
  never reach the model`; M3-2 метка до алиаса → `an alias beats an installed label…`; M3-3 край алиаса как предложение →
  `a word form of an alias launches it`.

### Задача 4: одна карточка Sidr — прочтение модели без выбора стартует сразу (решения 1, D3 вторая поправка, I5, I12)

**Файлы:**
- Изменить: `RouteCommandUseCase.kt` (шаг 7, ветка `Proposed`), `domain/.../intent/CommandOutcome.kt`
  (`AgentSessionStarted.proposedByModel: Boolean = false`)
- Изменить: `feature/launcher/.../LauncherCommandSession.kt`, `feature/launcher/.../agent/LauncherAgentSession.kt`
  (`attach(id, learned)` `:140` несёт метку; **автозакрытие** простой сессии, I12), `feature/launcher/.../agent/
  AgentSessionSurface.kt` (метка «Предложено моделью»), `LauncherViewModel.kt` / `LauncherScreen.kt` (параметры
  поверхности) — ревью M2
- Изменить: `consumer/jvm/src/main/.../ConsoleHarness.kt` (`interpret()` → `startAtOnce` при `!needsCard()`, K12) +
  `ConsoleHarnessTest`
- Изменить: `feature/settings/src/main/res/values{,-ru,-tr}/strings_locked.xml` — `settings_local_only_description`
  (**подписано владельцем 2026-11-22**, текст — §0.2 дословно) + сигнатура `OWNER-REVIEWED` по прецеденту Части D
  (`01f61d29`); строка метки — `feature/launcher/src/main/res/values{,-ru,-tr}/strings.xml`
- Изменить: матрица `DOC-NYH-1` — третий столбец (новые тесты; скобка «сессия не стартует» переписывается), текст правила
  не меняется; KDoc, которые поправка делает ложными (ревью I5): `Interpretation.kt`, `RouteCommandUseCase.kt` шаг 7
  («Nothing here executes»), `CommandOutcome.Interpreted`; жёсткое правило `CLAUDE.md` («Nothing the model proposes runs
  before … «Выполнить» … except a learned command») — второе исключение
- Тесты: `RouteCommandUseCaseTest.kt`, `LauncherViewModelTest.kt`, `app/src/test/.../agent/BridgePayloadGuardTest.kt`
  (**правится**, ревью C3), `:data:repository` `LearnedPlanSeamTest` (ревью I1), `ConsoleHarnessTest`

**Интерфейсы:**
- Потребляет: `Interpretation.Act.needsCard()` (`domain/memory/plan/LearnedPlans.kt:117`) — **то же правило**, что у
  выученной команды; `Interpretation.Act.plan()` (`ai/bridge/Interpretation.kt:85`).
- Производит: `CommandOutcome.AgentSessionStarted(id, learned: CommandForm? = null, proposedByModel: Boolean = false)`.
  **`learnable` не добавляется** (I5 «не засчитывать»): сессия без карточки ничего не учит.

- [ ] **Шаг 0 (правило §0 ф4, до кода): сверка обещания с Class B.** `grep -rn -i "подтвер\|Выполнить\|confirm\|onay"
  feature/*/src/main/res/values*/strings_locked.xml` — ожидаемые попадания (ревью I5): `settings_local_only_description`
  (×3 локали; `tr` — по «onay»), `launcher_forget_learned_command_consequence` (×3 — **остаётся истинной** при I5 «не
  засчитывать»), `settings_learned_choices_empty_message` (прочитать — истинна ли); каждое иное попадание — находка.
  Затем вписать подписанный `ru`, подготовить `en` / `tr` по нему (турецкий — на суд владельца до приёмки).
- [ ] **Шаг 1: падающие тесты `RouteCommandUseCaseTest`:**
  - `a fresh reading with no pick and no model text going out starts at once, marked as the model's` →
    `AgentSessionStarted(proposedByModel = true)`.
  - `a fresh reading that owes a pick gets the card` — `Interpreted`.
  - `a fresh reading that sends the model's text off the phone gets the card` — `web_search` с `ModelGenerated` →
    `Interpreted`; с `SaidBy(USER)` → старт.
  - `a session started without a card teaches nothing` (I5) — `learnedPlans.learnable` не вызван / запись 0.
  - `a fresh reading whose start fails keeps the card` — `startPlanned` отказал → `Interpreted`.
  - **Переворачиваются (ревью I1):** `> a fresh model reading carries what it teaches…` (каст `:1212` — теперь только для
    прочтения с карточкой); остальные «толкование модели — карточка, сессия не стартует» — найти `grep -n "Interpreted"
    RouteCommandUseCaseTest.kt`, список с новым ассертом — в отчёт задачи.
- [ ] **Шаг 2: рискованный шаг — тестом движка, не роутера (ревью I4).** Новый тест в `:domain` рядом с
  `AgentEgressSentinelGuardTest` (та же сборка): маршрут → сохранённая сессия → `RunAgentSessionUseCase(AgentExecutor(
  registry, FakeToolExecutor(...)))` с `CONFIRM`-инструментом (`play_store_search` / `open_url` из `withA0Tools`, или
  тест-локальный `removeRegistry` с `uninstall_app`): `a model reading with a risky step starts and stops at the
  engine's consent` → `AwaitingConsent`, `FakeToolExecutor` 0 вызовов (Фокус ревью 2).
- [ ] **Шаг 3: RED** (`:domain`).
- [ ] **Шаг 4: реализовать роутер.** Ветка `Proposed`: связанное прочтение → `act.plan() != null && !act.needsCard()` →
  `startAgentSession.startPlanned(act.goal, plan)` → `AgentSessionStarted(id, proposedByModel = true)`; неудачный старт →
  `Interpreted`. `learnable` — только у `Interpreted`, как сейчас.
- [ ] **Шаг 5: лаунчер.** `LauncherViewModelTest > a model session started without a card shows the model mark`;
  `> a simple finished session closes itself` (I12: один `SAFE`-шаг, нечего отменять → поверхность сессии снята по
  `Completed`), `> a finished session with an undo stays`, `> a finished plan of two steps stays`. Строка
  `launcher_agent_model_mark`: `ru` «Предложено моделью», `en` "Suggested by the model", `tr` "Model tarafından önerildi"
  (на суд). Автозакрытие относится ко **всем** стартам без карточки (задачи 1+4, 2+4, 3, 8) и к выученной команде.
- [ ] **Шаг 6: `BridgePayloadGuardTest` — предсказанная правка (ревью C3).** `the controls - the device read every
  planted source, and the command and offering are in the body` (`:554`) кастует каждый исход к `Interpreted`; команды
  0–2 («давай lumora», «давай кварцелот», «давай tessar») теперь стартуют сессии. Правка: связанные пакеты 0–2 читаются
  из стартовавших сессий (`FakeAgentSessionStore.saved`), 3-я (выбор) — из карточки; **ни одно утверждение о теле
  (тела, слот пользователя, allow-list) не меняется** — это записать в отчёт. Иная правка этого гварда — стоп.
- [ ] **Шаг 7: `LearnedPlanSeamTest` (ревью I1).** `confirmEraseThreeTimes()` (`:269` ждёт `Interpreted`): «сотри lumora»
  — `uninstall_app` (`CONFIRM`, слот — слова пользователя) → `needsCard()` ложен → теперь стартует без карточки и **не
  учит** (I5). Значит, форму этот шов больше не выучит «Выполнить»-ом через роутер: фикстура переводится на прочтение с
  выбором (две метки под одно слово) **или** обучение пишется напрямую через `RecordLearnedPlanUseCase`; выбор — в
  отчёт, свойство шва (повтор через тот же валидатор/биндер) не меняется.
- [ ] **Шаг 8: JVM-паритет (K12).** `ConsoleHarnessTest > a fresh reading with nothing to show starts at once` — как
  на Android; `… with a pick shows the card`.
- [ ] **Шаг 9: GREEN** — scoped `:domain:jvmTest`, `:data:repository:testDebugUnitTest`,
  `:feature:launcher:testDebugUnitTest`, `:app:testDebugUnitTest`, `:consumer:jvm:test`.
- [ ] **Шаг 10: ADR поправки** в конец `ai-context/decisions.md`: «2026-11-2x — D3, вторая поправка: прочтение без
  выбора и без текста модели» — раздел «Что поправка делает ложным — и где исправлено» по образцу первой поправки (KDoc,
  `CLAUDE.md`, матрица `DOC-NYH-1`, Class B); названные остатки: **M13** сирота `Running` (A4′ 3a (1)) доходит до шага 7 —
  сессия пишется до того, как лаунчер применил исход; **M14** `goal_text` пишется при маршруте для каждого прочтения без
  карточки (A1′ (10) шире); **M16** решение 1 выполнено только для рискованного прочтения **без выбора**:
  `uninstall_app` с именем «только после свёртки» — выбор (`AppTargetResolver.forIrreversible`, `:47-48`) → карточка +
  согласие = два касания. Строка в ориентире (поправка 2026-11-20 — «в силе», рядом новая).
- [ ] **Шаг 11: коммит** `feat(a4-tail/1+D3): одна карточка Sidr — прочтение модели без выбора стартует через движок`.
- [ ] **Шаг 12: мутации (`mutation-prover-deep`):** M4-1 `needsCard()` → `false` → `… sends the model's text … gets the
  card`, `… owes a pick gets the card`; M4-2 ветка стартует и при `needsCard()` → те же; M4-3 `AgentExecutor`
  пропускает чекпойнт согласия для шага с `StepRationale` модели (посадка, которая **доходит** до мира) → `… starts and
  stops at the engine's consent`; M4-3′ шаг-риск, пониженный до `SAFE` в планировщике, — **ожидаемо ВЫЖИВАЕТ** (пол
  `maxOf(step.risk, registry risk)`, `AgentExecutor.kt:358-359`) — записать как свидетельство пола, не как провал;
  M4-4 сессия без карточки учит → `a session started without a card teaches nothing`.

### Задача 5: честная строка отказа модели (решение 2: б)

**Файлы:**
- Изменить: `domain/.../intent/CommandMessage.kt` (+ `data object UnderstoodNoAction : CommandMessage`),
  `RouteCommandUseCase.kt` (`Declined` без уступки → новое сообщение), `feature/launcher/.../LauncherPresentation.kt`
  (единственный исчерпывающий `when` вне `:domain` — проверено `grep -rln "is CommandMessage.Verbatim"`),
  `feature/launcher/src/main/res/values{,-ru,-tr}/strings.xml`
- Тесты: `RouteCommandUseCaseTest.kt` (переворот `the model declines - keeps the FastPath outcome` →
  `the model declines - says it understood and Sidr cannot do it yet`), `LauncherPresentationTest`/ближайший тест
  маппинга (найти `grep -rln "launcher_understanding_needs_network" feature/launcher/src/test`).

- [ ] **Шаг 1:** переворот теста + `a yielded command the model declines is not this line` (K1 — уступка имеет приоритет).
  **Переворачиваются ещё (ревью I1):** `RouteCommandUseCaseTest > a learned command that does not bind goes on down the
  chain` (`:1187` ждёт `Unknown` → теперь строка отказа, если модель отказала); `LauncherViewModelTest > submitting an
  unknown command shows a message and does not execute` (`:846` — фейк-маршрут: проверить, `Unknown` ли он даёт сам;
  если через настоящий роутер — новая строка).
- [ ] **Шаг 2: RED → реализовать → GREEN.** Строка `launcher_model_declined`: `ru` «Понял команду, но Sidr так пока не
  умеет.», `en` "Understood, but Sidr can't do that yet.", `tr` "Anladım, ama Sidr bunu henüz yapamıyor." (на суд).
  ADR 1/4 правило 3: «неизвестная команда» снимается и для понятой команды — строкой в ADR пачки.
- [ ] **Шаг 3: коммит** `feat(a4-tail/2): отказ модели — «понял, но Sidr так пока не умеет»`.

### Задача 6: ожидание — только пока спрошена модель (решение 6; K9)

**Файлы:**
- Создать: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/router/ModelAskedSignal.kt` —
  `fun interface ModelAskedSignal { fun modelAsked() }` + `ModelAskedSignal.NONE`
- Изменить: `RouteCommandUseCase.kt` (конструктор `modelAsked: ModelAskedSignal = ModelAskedSignal.NONE`; вызов
  непосредственно перед `modelPlanner.plan`), `BridgeProvidesModule.provideRouteCommandUseCase` (параметр с умолчанием —
  `BridgePayloadGuardTest:285` компилируется), `feature/launcher/.../LauncherCommandSession.kt` (`submit`: таймер
  `THINKING_DELAY_MS` удалён; реализация сигнала — синглтон в `:feature:launcher` или `:app` с
  `MutableSharedFlow<Unit>`, сессия выставляет `_thinking.value = true` на событие, пока её `pending` жив)
- **Не меняются:** `CommandRouteStep`, `ResolvedCommandStep` и их 39 SAM-лямбд (ревью I8 — поэтому порт, а не параметр).
- Тесты: `RouteCommandUseCaseTest.kt`, `LauncherViewModelTest.kt`, `LauncherScreenInterpretationTest.kt`

- [ ] **Шаг 1:** `RouteCommandUseCaseTest > the model-asked signal fires once, right before the model, and never on a local
  path` (FastPath, 1a, 2b, 2c, local-only, без провайдера, офлайн — 0; шаг 7 — 1). `LauncherViewModelTest > a slow local
  path shows no wait` (фейк-маршрут с задержкой 1 с виртуального времени, сигнала нет → `thinking` ни разу не `true`),
  `> the wait shows the moment the model is asked`.
- [ ] **Шаг 2: переворачиваются (ревью I8)** — тесты ожидания, чьи медленные фейки сигнала не подают: `LauncherViewModelTest
  > the wait shows once the model is asked and cancelling it applies nothing` (до фикса ревью задачи 6 —
  `> the wait shows after a moment and cancelling it applies nothing`), `> a newer command supersedes a slow answer still on
  its way`, `> editing the command during the wait abandons it`, `> a dictated Final with no Partial dismisses the standing
  interpretation before its answer lands`, `> the web chip supersedes…`, `> a tap on an app supersedes…`, `> a suggestion
  that opens a screen supersedes…`, `> the ASK chip abandons a command still waiting on the model`;
  `LauncherScreenInterpretationTest > while the model is thinking the wait is drawn, and Cancel takes it down`. Правка
  каждого — медленный фейк подаёт сигнал перед задержкой (то, что делает настоящий шаг 7); утверждения об отмене /
  вытеснении **не меняются**. Тесты хардкодят 299/301 мс (`THINKING_DELAY_MS` в тестах нет) — задержки убрать.
- [ ] **Шаг 3: RED → реализовать → GREEN** (`:domain`, `:feature:launcher`, `:app`).
- [ ] **Шаг 4: коммит** `feat(a4-tail/6): «Разбираю команду…» — только пока спрошена модель`.

### Граница пачки 1

- [ ] `gradlew --stop`; разбивка по модулям выписана; `tools/gate.sh` → `GATE GREEN` ≥ пола; `:app:assembleRelease`
  (переподпись `settings_local_only_description` — до этого прогона).
- [ ] Финальное ревью пачки — `reviewer-deep` по диффу пачки; пол `BASELINE_TESTS` поднимается на итог.

---

## Пачка 2 — интерфейс

### Задача 7: нумерация шагов (решение 4)

**Файлы:** `feature/launcher/.../agent/AgentSessionSurface.kt` (`AgentPlanSteps`, `:380-398` — строка + слово состояния),
`feature/launcher/.../InterpretationSurface.kt` (`:89-91` — строки шагов карточки толкования, без слова состояния,
ревью I11), строки `en`/`ru`/`tr`; тесты — `AgentSessionPresentationTest.kt`, `LauncherScreenInterpretationTest.kt`.

- [x] **Шаг 1:** `a plan of two or more steps numbers its lines from one` (карточка сессии, `"1. %s — %s"`); `the
  interpretation card numbers its steps the same way` (`"1. %s"`); `a one-step plan is not numbered` (обе карточки);
  `«← из шага N» names the number the interpretation card shows`.
- [x] **Шаг 2: RED → реализовать → GREEN.** Две новые строки: `launcher_agent_step_line_numbered` `%1$d. %2$s — %3$s`
  (сессия) и `launcher_interpretation_step_numbered` `%1$d. %2$s` (толкование), во всех трёх локалях (турецкий порядок —
  на суд); однoшаговый план — прежние строки.
- [x] **Шаг 3: коммит** `feat(a4-tail/4): шаги обеих карточек пронумерованы`.

### Задача 8: чип будильника в строке даты (решение 5: а, у левого края)

**Файлы:**
- Создать: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/alarm/NextAlarm.kt` (`data class NextAlarm(val
  triggerEpochMs: Long)`, `interface NextAlarmSource { fun observe(): Flow<NextAlarm?> }`)
- Создать: `core/android/src/main/java/com/sidr/launcher/core/android/alarm/AndroidNextAlarmSource.kt` — `callbackFlow`
  над `AlarmManager.getNextAlarmClock()` и `ACTION_NEXT_ALARM_CLOCK_CHANGED`; фильтр: `showIntent?.creatorPackage` ==
  пакет, разрешающий `AlarmClock.ACTION_SHOW_ALARMS` (`PackageManager.resolveActivity`), иначе `null`
- Изменить: `core/android/build.gradle.kts` — `testImplementation` Robolectric + coroutines-test (K13, ревью I9)
- Создать: `core/ui/.../component/SidrNextAlarmChip.kt` (текст + `contentDescription`, касание) + Roborazzi-сценарий
  `next_alarm_chip` (K13)
- Изменить: `LauncherScreen.kt` (`HomeDateLine` — `Box`: дата по центру, чип `Alignment.CenterStart`; дата получает
  отступ по ширине чипа, чтобы длинная строка хиджры · григорианской на 720 px не легла под чип — ревью M9),
  `LauncherViewModel.kt` (`nextAlarm: StateFlow<NextAlarm?>`, `onAlarmChipTapped()` → K7), DI-модуль `:app`
- Тесты: `core/android/src/test/.../AndroidNextAlarmSourceTest.kt` (Robolectric `ShadowAlarmManager`),
  `LauncherViewModelTest.kt`, compose-семантический тест размещения в `:feature:launcher` (`LauncherScreenAlarmChipTest`)

- [x] **Шаг 1:** `AndroidNextAlarmSourceTest`: `the clock's own alarm is shown`, `an alarm set by another app is not`
  (creator `com.samsung.android.fmm` → `null`; Фокус ревью 5), `no alarm - null`, `a change broadcast re-reads`.
  `LauncherViewModelTest > tapping the alarm chip starts show_alarms through the engine` — `startPlanned` с одним шагом
  **`TIER0_SHOW_ALARMS`** (`AgentSessionPresentation.kt:109`, не литерал; ревью M9) и реестром, где `show_alarms` есть
  (`withA0Tools()` его не содержит — тест-регистр добавляет); сессия закрывается сама (I12, задача 4).
  `LauncherScreenAlarmChipTest > the chip sits at the start of the date line and hides while typing`.
- [x] **Шаг 2: RED → реализовать → GREEN.** Время — `DateFormat.getTimeFormat(context)`, форматируется в feature-слое.
  Строки: `launcher_home_next_alarm` `⏰ %1$s` (`en`/`ru`/`tr` одинаково), `launcher_home_next_alarm_cd` `ru`
  «Следующий будильник: %1$s», `en` "Next alarm: %1$s", `tr` "Sonraki alarm: %1$s".
- [x] **Шаг 3: золотые** — `:core:ui:recordRoborazziDebug` только для `next_alarm_chip`, затем
  `:core:ui:verifyRoborazziDebug` GREEN; изменение чужих снимков — стоп.
- [x] **Шаг 4: коммит** `feat(a4-tail/5): чип будильника часов в строке даты`.

### Задача 9: «Отменить» и «Закрыть» одного размера (решение 7)

**Файлы:** `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrActionSafety.kt` (`SidrSurfaceActions(…,
equalWidth: Boolean = false)` — в ветке `Row` обе кнопки `Modifier.weight(1f)`), параметр протянуть через
`SidrResultSurface`; включить на карточке законченного плана (`AgentSessionSurface.kt`); золотые `core/ui`.

- [x] **Шаг 1:** Roborazzi-сценарий `result_surface_equal_actions` (обе кнопки, равная ширина) — новый снимок;
  существующие 36 снимков **не меняются** (`verifyRoborazziDebug` зелёный на них).
- [x] **Шаг 2: реализовать → `recordRoborazziDebug` нового → `verifyRoborazziDebug` GREEN.**
- [x] **Шаг 3: коммит** `feat(a4-tail/7): кнопки законченного плана — равной ширины`.

### Задача 10: поле ключа над клавиатурой (решение 9)

**Файлы:** `feature/assistant/src/main/java/com/sidr/launcher/feature/assistant/AssistantScreen.kt`
(`AssistantProviderScreen` — `Modifier.imePadding()` на прокручиваемой колонке; поле ключа при фокусе зовёт
`BringIntoViewRequester.bringIntoView()`); `feature/assistant/build.gradle.kts` — Robolectric + compose ui-test +
`testOptions.unitTests.isIncludeAndroidResources = true` (K13).

- [x] **Шаг 1:** Compose-тест (Robolectric) `focusing the key field asks to be brought into view` — проверяется через
  **инжектируемый колбэк фокуса** (`onKeyFieldFocused: () -> Unit` с умолчанием, вызывающим `bringIntoView`), не через
  фейк-реквестер: `BringIntoViewRequester` в Compose — `sealed` (ревью M10; сверить с foundation 1.12.0). Подъём над
  настоящей IME тестом не доказуем — **строка чеклиста приёмки**.
- [x] **Шаг 2: RED → реализовать → GREEN** (`:feature:assistant:testDebugUnitTest`).
- [x] **Шаг 3: коммит** `fix(a4-tail/9): поле ключа провайдера не прячется под клавиатурой`.

### Задача 11: выученные алиасы — своей вкладкой (решение 10)

**Файлы:** `feature/settings/.../AliasesScreen.kt` (две `SidrFilterChip` вверху: «Мои алиасы» / «Добавить»; по
умолчанию «Мои алиасы», если они есть, иначе «Добавить»), строки `settings_aliases_tab_mine` / `settings_aliases_tab_add`
(`ru` «Мои алиасы» / «Добавить», `en` "My aliases" / "Add", `tr` "Takma adlarım" / "Ekle" — на суд);
`feature/settings/build.gradle.kts` — Robolectric + compose ui-test + `isIncludeAndroidResources` (K13); тест — новый
`feature/settings/src/test/.../AliasesScreenTest.kt`.

- [x] **Шаг 1:** `with aliases the screen opens on them, not on the app list`; `«Забыть» still goes through the forget
  gate` (`SidrForgetGate`, `DOC-AML-5` — как сейчас); `with none it opens on Add`.
- [x] **Шаг 2: RED → реализовать → GREEN** (`:feature:settings`).
- [x] **Шаг 3: коммит** `feat(a4-tail/10): выученные алиасы — отдельной вкладкой`.

### Задача 12: избранное — закреплённые, свежесть, окно выбора, до 12 мест (решение 15)

**Файлы:**
- Изменить: `domain/.../preferences/UserPreferences.kt` (`favoritesCount = 12`, `pinnedFavorites: List<String> =
  emptyList()`, `hiddenFavorites: Set<String> = emptySet()`), `data/repository/.../preferences/PreferencesKeys.kt` —
  **два** новых ключа (`USER_FAVORITES_COUNT` уже есть, `:49`; ревью I10): `user_favorites_pinned` (строка — пакеты через
  `\n`, порядок = порядок закрепления; пакет не содержит перевода строки) и `user_favorites_hidden` (`stringSetPreferences`);
  оба — в `PreferencesKeys.ALL_KEY_NAMES` (`:147`; инвентарь ручной, `PrivacyInventoryGuardTest` незаписанный ключ не
  увидит) и чисты против запрет-листа; `PreferencesMapper.kt`
- Изменить: `feature/launcher/.../LauncherAppList.kt` (`deriveFavorites(records, installed, prefs, nowMs)`),
  `LauncherScreen.kt` (касание заголовка «Избранное» → окно выбора; **заголовок рисуется и при пустом избранном** —
  сегодня он только при непустом, `:559-560`, и на свежей установке закрепить было бы нечем), `LauncherViewModel.kt`
  (`pin`/`unpin`/`hide`)
- Изменить: `feature/settings/.../SettingsUiState.kt` (`FAVORITES_COUNT_OPTIONS = listOf(4, 6, 8, 10, 12)`)
- Тесты: новый `feature/launcher/src/test/.../LauncherAppListFavoritesTest.kt`, `SettingsViewModelTest.kt`,
  тест маппера (`grep -rln "favoritesCount" data/repository/src/test`), compose-тест окна выбора в `:feature:launcher`

**Правило `deriveFavorites`** (K8): сначала закреплённые в своём порядке (только установленные), затем записи
использования по прежнему порядку, **кроме** скрытых, закреплённых и тех, чей `lastUsedEpochMs < nowMs − 14 дней`;
всё — до `favoritesCount`. Пустая история и нет закреплённых — пусто (заголовок есть, сетки нет).

- [x] **Шаг 1:** `pinned apps come first and are never pushed out`; `an app not opened for 14 days leaves the automatic
  places`; `a new app enters once it outranks the last fresh one` (по таблице §0.1); `a hidden app is not shown and its
  history stays`; `pinning a hidden app unhides it`; `an uninstalled pinned app is skipped`; `the count defaults to 12 and
  offers 12` (`SettingsViewModelTest`); маппер — круговой тест двух новых ключей, включая порядок закреплённых;
  `the header is drawn with no favourites and opens the picker`.
- [x] **Шаг 2: RED → реализовать → GREEN.** Окно выбора — `SidrSurface` с `LazyColumn` строк `SidrToggleRow`
  (закреплено / нет) над всеми установленными приложениями, закреплённые сверху; у автоматически попавших — действие
  «Убрать» (`hide`). Строки: `launcher_favorites_edit_title` «Избранное», `launcher_favorites_pin` «Закрепить»,
  `launcher_favorites_hide` «Убрать с главного», `launcher_favorites_edit_hint` «Закреплённые стоят первыми, остальные
  места заполняются тем, что вы открываете чаще.» (`en`/`tr` — на суд).
- [x] **Шаг 3: правила резервной копии.** Новые ключи DataStore — тот же файл, что исключён из Auto Backup
  (`BackupRulesGuardTest` зелёный без правки; правка — находка).
- [x] **Шаг 4: коммит** `feat(a4-tail/15): избранное — закреплённые, свежесть 14 дней, окно выбора, до 12 мест`.

### Граница пачки 2

- [x] `gradlew --stop`; разбивка; `tools/gate.sh` → `GATE GREEN`; `:core:ui:verifyRoborazziDebug` GREEN; ревью пачки.

---

## Пачка 3 — память

### Задача 13: слот выученной формы по формам слова (решение 8)

**Файлы:** `domain/src/commonMain/kotlin/com/sidr/launcher/domain/memory/plan/LearnedForms.kt`
(`CommandReading.occurrencesOfForms(wanted)`), тест `LearnedFormsTest.kt`; гвард свёртки
`TextFoldingBindingGuardTest` — проверить, не править.

**Правило:** аргумент с `ValueOrigin.ModelGenerated`, чьи слова (после `folding.words`) встают на подряд идущие слова
команды так, что каждая пара — точное совпадение или край (`AppNameMatcher.wordsMatch(typed, model) != null`), и
**хотя бы одна пара — край**, ровно в **одном** месте команды, на границах токенов, — становится слотом; значение слота
при повторе — **слова пользователя** (как у `SaidBy`). Два места — не учится (Фокус ревью 4). Иначе — как сейчас
(константа). `Derivability` (а с ним `needsCard`) **не меняется** — слово модели «погода» по-прежнему её текст.

- [x] **Шаг 1:** `a value the model declined is learned as the user's slot` («поищи погоду в казани» + `web_search
  query = "погода в казани"` → форма `поищи <0>`, повтор «поищи погоду в москве» → `"погоду в москве"`); `two places of
  the same form - not learned`; `a model value with no word of the user stays a constant`; `an exact value is learned as
  before` (существующий — не меняется).
- [x] **Шаг 2: RED → реализовать → GREEN** (`:domain`).
- [x] **Шаг 3: коммит** `feat(a4-tail/8): слот выученной формы находится и по формам слова`.
- [x] **Шаг 4: мутация** M13-1 «край» не требуется (только точные пары) → `a value the model declined…`; M13-2 два места
  допускаются (берётся первое) → `two places … not learned`.

> **Решение владельца 2026-11-27 («Не для шагов наружу»):** слот по формам слова — для всех инструментов, кроме
> `web_search` и `open_url`; у них значение модели остаётся константой, как до задачи 13 (иначе «поищи …» закрепился бы и
> шаг 2c отменил бы 13: Б, а I5 построен, чтобы этого не было). Пример шага 1 про «поищи погоду в казани» поэтому
> **не исполняется**: поиск константа, слот — у запуска («открой лумору»). Ход, мутации M13-1…M13-5 — ADR пачки 3.

### Граница пачки 3 — граничный гейт, ревью `reviewer-deep`.

- [x] Граничный гейт `GATE GREEN` 2078 (`:domain` 688); ревью `reviewer-deep` → два раунда исправлений → APPROVE.

---

## Закрытие

### Задача 14: замер после, чеклист приёмки, приёмка, закрытие

- [ ] **Замер после:** тот же одноразовый прогон §0.1 на дереве после пачки 1 — таблица «до / после» в ADR блока
  (предсказание: в сети **все семь** уверенных `SEARCH` §0.1 уходят к модели — строки 5–7 возвращаются `web_search` словами
  пользователя и стартуют без карточки — и два «открой …» тоже; local-only — как до; ревью M6). В ADR назвать побочки
  обучения (ревью M12): при I5 «не засчитывать» запуски без карточки форм не закрепляют; выученный до хвоста «открой …»
  офлайн не читается, потому что шаг 2 (план магазина) отвечает на решённый `NoAppFound` раньше 2c.
- [ ] **`intent_match` с телефона** (агент, `pull-agent-db.sh`) — доля `SEARCH`/`LAUNCH_APP`, сколько уверенных: проверка
  предсказаний, строкой в ADR.
- [ ] **Чеклист приёмки** `docs/superpowers/plans/2026-11-2x-a4-tail-acceptance.md`: по строке на задачу, каждая строка
  сверена с записями исполнения **всех** пачек (правило §0); «увиденное» — гипотеза до прогона. Обязательные строки:
  «открой клод» / «открой cloude» в сети; то же в local-only; «найди лучший рецепт шашлыка» в сети (без карточки, ~3 с) и
  офлайн (сразу); «удали приложение …» от модели — одна карточка согласия; «tg» голым и «открой tg» — сразу, ни одного
  ожидания; поле ключа над клавиатурой; чип будильника (поставить будильник в часах — появился; Find My Mobile — нет);
  избранное: закрепить, убрать, новое приложение; турецкие строки — суд владельца.
- [ ] **Приёмка владельца** на SM-A325F; дефекты раунда — по прецеденту A1′ (чинятся в раунде, подписывается
  исправленная сборка).
- [ ] **Чеклист закрытия §0** целиком, включая: ADR хвоста в конец `decisions.md` + строка в ориентире; **разбор одного
  гварда** (правило 6) — предложение плана: `ToolPermissionManifestGuardTest` (его половину по разрешениям держит
  `ToolRegistryPermissionGuardTest`, Known debt A1′ (9)) — `git log -S`, леджеры, мутации; итог — строкой в ADR;
  `CLAUDE.md` + `current-status.md` (хвост файла — три последних раздела); `§HANDOFF` — следующая сессия: спека A2/A3 с
  задачами 11–12 и предложением владельца об окне агента; коммит и пуш `git push origin launcher--7`.
