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
3. **Алиас, совпадающий с командой FastPath** («настройки», «камера»): решённое и достигнутое FastPath не
   перехватывается алиасом. Тест — задача 3.
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
- Предложение владельца «окно агента / лёгкий редактор / заметки» — **не в этом плане**: записывается в `§HANDOFF`
  входом в спеку A2/A3 рядом с задачами 11–12 (видение агента — там же).

### §0.3. Решения контроллера плана — владелец перерешает на ревью плана

| # | Решение | Почему | Где |
|---|---|---|---|
| K1 | Уступивший FastPath при недоступной модели (`Declined`, `Unavailable`) **исполняет то, что сделал бы** (поиск; план магазина) | иначе Б отнимает работающий поиск при сломанном ключе / тайм-ауте (40 с ожидания и ничего) | задачи 1, 2 |
| K2 | Задача 14 распространяется и на шаг **2b**: `Unresolved` приложения **без кандидатов** в сети идёт к модели («удали приложение клод») | тот же класс; без этого «удали клод» остаётся «не найдено». **Частично обращает развилку 3b-E** (R14-39: «никогда не шаг 7») — только для сети и только для пустого `Unresolved` приложения; local-only как было | задача 2 |
| K3 | Если модель назвала приложение, которого нет (`Binding.NotFound`), — план магазина A0 **по словам пользователя**, не по имени модели | слова модели в магазин не уходят (D1, egress) | задача 2 |
| K4 | Сессия, стартовавшая из свежего прочтения без карточки, несёт метку «Предложено моделью» на карточке сессии (поле `proposedByModel` у `AgentSessionStarted`, не новое значение суммы) | `DOC-NYH-1` «показывает, как поняла» — после, а не до; прецедент — метка «Выученная команда» | задача 4 |
| K5 | Равная ширина кнопок — параметр `equalWidth` у `SidrSurfaceActions`, включён только на карточке законченного плана | восемь других мест не меняются, золотые меняются только у этой карточки | задача 9 |
| K6 | Выученные алиасы — две вкладки на экране алиасов («Мои алиасы» / «Добавить»), на `SidrFilterChip`; новых компонентов `core/ui` нет | наименьшая правка; отдельный экран — новый маршрут | задача 11 |
| K7 | Касание чипа будильника — сессия с одним шагом `show_alarms` через движок (как 2b), не прямой `PendingIntent` | `ToolExecutor` — единственный путь к миру; `OneModelPathGuardTest` держит единственное прямое исполнение — чип WEB | задача 8 |
| K8 | Избранное: закреплённые — упорядоченный список пакетов в DataStore; скрытые — множество; свежесть **14 дней** по `last_used_epoch_ms`; места по умолчанию — **12**, варианты 4/6/8/10/12 | без схемы Room; 14 дней — предложено в разговоре, владелец не возразил | задача 12 |
| K9 | Задача 6 — сигнал «модель спрошена» колбэком `onModelAsked` в `RouteCommandUseCase.route`, индикатор сразу при вызове, без таймера 300 мс | точное значение «только пока спрошена модель» | задача 6 |

### §0.4. Пачки, порядок, места

| Пачка | Задачи плана (решение владельца) | Ревьюер | Гейт пачки |
|---|---|---|---|
| **1 — маршрут и карточка** | 1 (13), 2 (14), 3 (3), 4 (1 + D3), 5 (2), 6 (6) | **`reviewer-deep`** у 1–4 (согласие, локальный путь, egress); `reviewer` у 5–6 | граничный + `:app:assembleRelease` (Class B) |
| **2 — интерфейс** | 7 (4), 8 (5), 9 (7), 10 (9), 11 (10), 12 (15) | `reviewer`; 8 — `reviewer-deep` (новый источник в Home, путь к миру) | граничный + `:core:ui:verifyRoborazziDebug` |
| **3 — память** | 13 (8) | `reviewer-deep` (форма учится → повтор без модели) | граничный |
| **Закрытие** | 14 — замер после, чеклист приёмки, приёмка владельца, чеклист закрытия §0 | — | граничный финальный |

Исполнитель: `implementer-critical` у задач 1–4, 13; `implementer` у остальных. Новые гварды — к `mutation-prover`
(у 1, 4 — `mutation-prover-deep`: согласие и egress).

**Предсказанная цепочка счёта** (сверяется при исполнении, расхождение — находка): пол 1918 → пачка 1 ≈ +30
(`:domain` +22, `:feature:launcher` +6, `:app` +2) → пачка 2 ≈ +28 (`:feature:launcher` +12, `:feature:settings`
+8, `:core:android` +4, `:feature:assistant` +1, `:core:ui` +3) → пачка 3 ≈ +6 (`:domain`) → ≈ **1982**. Разбивка по
модулям выводится **до** каждого граничного прогона.

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
  private suspend fun modelPathOpen(): Boolean   // !localOnlyMode && activeConfig != null && isOnline()
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
- [ ] **Шаг 5: прогнать — RED** (шесть новых `FAILED ::`).
- [ ] **Шаг 6: реализовать в роутере.** `val open = modelPathOpen()` до шага 1; `handleUserCommand.answer(raw, open)`;
  шаги 3/5/6 — если `yielded != null` и путь вдруг закрыт — `fallback(yielded)` вместо сообщения; шаг 7 — `Declined`
  и `Unavailable` при `yielded is Search` → `handleUserCommand.runYielded(it)`. `isUndecided` не меняется. KDoc
  класса: новый пункт «1′ — уступка», ссылка на это решение; абзац про паритет — «local-only: уступки нет, FastPath
  ищет байт в байт».
- [ ] **Шаг 7: GREEN** — `tools/gate.sh --scoped :domain:jvmTest` → `SCOPED GREEN`, `:domain` 617 → **≈628**.
- [ ] **Шаг 8: коммит** `feat(a4-tail/13): в сети FastPath уступает поиск модели, без модели ищет сам`.
- [ ] **Шаг 9: мутации (к `mutation-prover-deep`, после коммита):**

| Посадка | Предсказанный `FAILED ::` |
|---|---|
| M1-1 `answer`: `yieldToModel` игнорируется (всегда `false`) | `a confident search yields…`, `online with a provider a FastPath search goes to the model…` |
| M1-2 `modelPathOpen` возвращает `true` без проверки `localOnlyMode` | `local-only a search runs on FastPath…` |
| M1-3 шаг 7: `Unavailable` → сообщение как раньше (без `runYielded`) | `a yielded search whose model is unavailable…` |
| M1-4 `runYielded` записывает `intent_match` | `runYielded executes the search and records nothing` |

  Чистый перепрогон из очищенного дерева — до коммита записи мутаций в леджер.

### Задача 2: «приложение не найдено» в сети спрашивает модель (решение 14)

**Файлы:**
- Изменить: `HandleUserCommandUseCase.kt` (уступка `NoAppFound`), `RouteCommandUseCase.kt` (шаги 2, 2b, 7)
- Тесты: `HandleUserCommandUseCaseTest.kt`, `RouteCommandUseCaseTest.kt`

**Интерфейсы:**
- Потребляет: `FastPathYield.MissingApp`, `answer`, `modelPathOpen` (задача 1).
- Производит: `private suspend fun storePlan(raw: String, query: String): CommandOutcome?` — бывший шаг 2 вынесен; шаг 2
  зовёт его при `!open`, фолбэк — при `open`.

- [ ] **Шаг 1: падающие тесты `HandleUserCommandUseCaseTest`:** `a launch of a name that is not installed yields when
  asked to` → `LowConfidence`, `yielded == MissingApp(NoAppFound("клод"))`; без уступки — `Message(NoAppFound("клод"))`
  как сейчас; `a launch whose name carries a second goal stays LowConfidence with nothing yielded` (фаза 1 не меняется).
- [ ] **Шаг 2: падающие тесты `RouteCommandUseCaseTest`:**
  - `online a missing app goes to the model and binds on the device`: «открой клод», фейк-модель → `launch_app`
    `app = "Claude"`, фейк-биндер связывает «Claude» → `Interpreted`/`AgentSessionStarted`; план магазина **не**
    стартовал (стор 0 записей).
  - `online the model names an app that is not here - the store plan, in the user's words` (K3): биндер `NotFound
    ("Claude")` → сессия `AppNotInstalled("клод")`.
  - `online a missing app the model declines - the store plan` и `… model unavailable - the store plan` (K1).
  - `local-only a missing app gets the store plan and the model is never asked` (паритет; существующий тест A0 —
    проверить, что он и есть этот, не дублировать).
  - `online an unresolved 2b app with no candidates goes to the model` (K2): «удали приложение клод» → модель спрошена;
    `local-only … is answered on the device` — существующий `an unresolved 2b target is answered on the device and the
    model is never asked` **переименовывается** в `… in local-only …` и остаётся зелёным; `online an unresolved 2b
    target WITH candidates is still a pick on the device` — модель 0 вызовов.
- [ ] **Шаг 3: RED** — `--scoped :domain:jvmTest`, восемь новых `FAILED ::`.
- [ ] **Шаг 4: реализовать.** `answer`: при `yieldToModel` `Message(NoAppFound)` без второй цели → `LowConfidence` +
  `MissingApp`. Роутер: шаг 2 — только при `!open`; 2b `Unresolved` с `sort == APP`, без кандидатов, читаемый — при
  `open` проваливается дальше (не `answerOnDevice`); шаг 7: `Proposed` → `Binding.NotFound` при `yielded is
  MissingApp` → `storePlan(raw, yielded.message.query)`; `Declined`/`Unavailable` → тоже `storePlan`. KDoc шагов 2 и 2b —
  «в сети уступает модели (решение 14, K2)»; абзац «Step 2 sits above step 3» — дописать, что уступка ключуется на
  состоянии системы и поэтому стоит **ниже** гейтов 3/5/6, а детерминированный план магазина — выше, как был.
- [ ] **Шаг 5: GREEN**, `:domain` ≈ **636**.
- [ ] **Шаг 6: коммит** `feat(a4-tail/14): в сети «нет такого приложения» сначала спрашивает модель`.
- [ ] **Шаг 7: мутации:** M2-1 шаг 2 без проверки `open` (план магазина всегда) → `online a missing app goes to the
  model…`; M2-2 `NotFound` → `NoAppFound` модели вместо `storePlan` → `… the store plan, in the user's words`; M2-3 2b
  уступает и с кандидатами → `… WITH candidates is still a pick…`.

### Задача 3: алиас — до модели и в слоте приложения (решение 3)

**Файлы:**
- Изменить: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/IntentActionResolver.kt` (FastPath-рукав
  запуска читает алиас), `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/AppTargetResolver.kt`
  (слот 2b читает алиас), `RouteCommandUseCase.kt` (голый алиас — шаг «1a»), `app/src/main/java/com/sidr/launcher/di/*`
  (проводка `AliasStore`).
- Изменить: `ResolveCommandWithAliasUseCase.kt` — его ветка `Unknown` становится недостижимой для голого алиаса;
  **не удалять** в этой задаче, KDoc: «с задачи 3 хвоста — запасной путь; кандидат на удаление решает владелец».
- Тесты: `IntentActionResolverTest.kt`, `AppTargetResolverTest.kt`, `RouteCommandUseCaseTest.kt`,
  `ResolveCommandWithAliasUseCaseTest.kt`.

**Интерфейсы:**
- Потребляет: `AliasStore.find(phrase): OperationResult<AppAlias?>` (`domain/memory/alias`), `appPackageOrNull()`.
- Производит: `IntentActionResolver(…, aliases: AliasStore? = null)`; `AppTargetResolver(…, aliases: AliasStore)`;
  в роутере `private suspend fun aliasLaunch(raw: String): CommandOutcome?` — однoшаговый план `launch_app` с пакетом
  алиаса через `startAgentSession.startPlanned` (путь 2b), `null` — нет алиаса / не установлен / старт не удался.

- [ ] **Шаг 1: падающие тесты:**
  - `IntentActionResolverTest > open with an alias launches the alias target`: алиас «tg» → `org.telegram.plus`,
    «открой tg» → `LaunchAppAction(org.telegram.plus)`; `… an alias whose app is gone is not found` → `NoAppFound`.
  - `IntentActionResolverTest > a word form of an alias launches it` (владелец 2026-11-21, телефон: «открой телегу»
    при алиасе «телега» ушло в магазин; решение владельца «алиасы не спрашивать — лишний слой»):
    `AppNameMatcher.wordsMatch(typed, aliasPhrase) == false` (край) → `LaunchAppAction` пакета алиаса, **без**
    «Вы имели в виду». Отличие от края метки (B-2 — только предложение) — намеренное: алиас — память пользователя.
  - `IntentActionResolverTest > a word form that fits an alias and another app's label is a choice` — край алиаса и
    край/точная метка **другого** пакета одновременно → `AmbiguousAppAction` с обоими, ничего не запущено.
  - `IntentActionResolverTest > an installed label beats an alias of the same words` — приложение «Камера» и алиас
    «камера» → чужой пакет: запускается установленная «Камера» (Фокус ревью 3).
  - `AppTargetResolverTest > the app slot reads an alias` — «удали приложение tg» → один пакет, без выбора.
  - `RouteCommandUseCaseTest > a bare alias starts its launch on the device and the model is never asked`: «tg» →
    `AgentSessionStarted`, модель 0 вызовов, в сети и в local-only (владелец принял алиасы в local-only).
  - `RouteCommandUseCaseTest > a bare alias never shadows a decided FastPath outcome` — алиас «помощь» → исход `Help`.
  - `RouteCommandUseCaseTest > the words of an alias never reach the model` — транспорт 0 тел на «tg» (принцип 2 ADR
    «Два принципа»).
- [ ] **Шаг 2: RED** — `--scoped :domain:jvmTest`, затем `--scoped :data:repository:testDebugUnitTest`.
- [ ] **Шаг 3: реализовать.** Порядок в `IntentActionResolver`: точная метка → алиас (точное совпадение фразы после
  `CommandNormalizer`) → формы слова: край алиаса — **запуск**, край метки — предложение (B-2), край алиаса вместе с
  меткой другого пакета — выбор (`AppNameMatcher`) → `NoAppFound`. То же правило — в слоте 2b (`AppTargetResolver`).
  Замер телефона 2026-11-21 (`intent_match`): «открой телега», «открой tg», «открой телегу» — `LAUNCH_APP` 0.90 →
  магазин; голые «tg» / «телега» — `UNKNOWN`; алиасы `tg`, `телега` → `org.telegram.plus` на месте. Роутер: шаг «1a» — после FastPath, только при
  `isUndecided()`, **выше** шага 2b; стоит выше шага 3 — это `DOC-ADL-3` применённый (детерминированная память меняет
  исход, который FastPath не решил; модель не спрошена, ничего не ушло) — абзац в KDoc класса, назвать в ADR задачи.
- [ ] **Шаг 4: GREEN** по обоим модулям; `:domain` ≈ **640**, `:data:repository` ≈ **478**.
- [ ] **Шаг 5: коммит** `feat(a4-tail/3): алиас — до модели, в запуске FastPath и в слоте 2b`.
- [ ] **Шаг 6: мутации:** M3-1 шаг «1a» ниже шага 7 → `a bare alias … the model is never asked`; M3-2 алиас до точной
  метки → `an installed label beats an alias…`.

### Задача 4: одна карточка Sidr — прочтение модели без выбора стартует сразу (решения 1 и D3, вторая поправка)

**Файлы:**
- Изменить: `RouteCommandUseCase.kt` (шаг 7, ветка `Proposed`), `domain/.../intent/CommandOutcome.kt`
  (`AgentSessionStarted.proposedByModel: Boolean = false`)
- Изменить: `feature/launcher/.../LauncherCommandSession.kt` (запись выучиваемого при старте без «Выполнить»),
  `feature/launcher/.../agent/AgentSessionSurface.kt` (метка «Предложено моделью»)
- Изменить: `feature/settings/src/main/res/values{,-ru,-tr}/strings_locked.xml` — `settings_local_only_description`
  (**подпись владельца**), строки метки — `feature/launcher/src/main/res/values{,-ru,-tr}/strings.xml`
- Изменить: `docs/governing/sidr-doctrine-matrix-v1.0.md` — третий столбец `DOC-NYH-1` (новый тест); текст правила не
  меняется
- Тесты: `RouteCommandUseCaseTest.kt`, `LauncherViewModelTest.kt`, `app/src/test/.../agent/OneModelPathGuardTest.kt`,
  `BridgePayloadGuardTest.kt` (проверить, не править)

**Интерфейсы:**
- Потребляет: `Interpretation.Act.needsCard()` (`domain/memory/plan/LearnedPlans.kt`) — **то же правило**, что у
  выученной команды; `Interpretation.Act.plan()`.
- Производит: `CommandOutcome.AgentSessionStarted(id, learned: CommandForm? = null, proposedByModel: Boolean = false)`.

- [ ] **Шаг 0 (правило §0 ф4, до кода): сверка обещания с Class B.** `grep -rn "подтвержд\|Выполнить\|confirm"
  feature/*/src/main/res/values*/strings_locked.xml` — ожидается одна строка `settings_local_only_description` в трёх
  локалях; иное — находка. **Черновик для подписи (`ru`)**: «Выключено: команды, которые лаунчер не распознал локально,
  отправляются вашему ИИ-провайдеру для понимания. Включено: вашему ИИ-провайдеру ничего не отправляется, команды
  понимаются только на этом устройстве. В обоих случаях рискованный шаг подтверждается всегда, а понятую команду вы
  видите на карточке до выполнения, если нужно выбрать или если наружу уйдёт текст, написанный моделью. Поиск и ссылки,
  о которых вы просите, открываются в браузере.» `en`/`tr` — по тексту `ru` после подписи. **Стоп до подписи.**
- [ ] **Шаг 1: падающие тесты `RouteCommandUseCaseTest`:**
  - `a fresh reading with no pick and no model text going out starts at once, marked as the model's` → `AgentSessionStarted(
    proposedByModel = true)`, `learnable` передан (см. шаг 4).
  - `a fresh reading that proposes a risky step starts and stops at the engine's consent` — `uninstall_app` →
    `AgentSessionStarted`; фейк-исполнитель `ToolExecutor` **0** вызовов; сессия `AwaitingConsent` (Фокус ревью 2).
  - `a fresh reading that owes a pick gets the card` — `Interpreted`.
  - `a fresh reading that sends the model's text off the phone gets the card` — `web_search` с `ModelGenerated` →
    `Interpreted`; с `SaidBy(USER)` → старт.
  - Переворачиваются (имя и ассерт): существующие тесты «толкование модели — карточка, сессия не стартует» (третий
    столбец `DOC-NYH-1`) — найти по `grep -n "Interpreted" RouteCommandUseCaseTest.kt`, список — в отчёт задачи.
- [ ] **Шаг 2: RED.**
- [ ] **Шаг 3: реализовать.** Ветка `Proposed`: связанное прочтение → если `act.plan() != null && !act.needsCard()` —
  `startAgentSession.startPlanned(act.goal, plan)` → `AgentSessionStarted(id, proposedByModel = true)`; неудачный
  старт → `Interpreted` (карточка как сейчас — отказ безопаснее). `learnable` — передать в лаунчер: поле
  `AgentSessionStarted.learnable: LearnedPlanShape? = null`, лаунчер записывает его, как сейчас после «Выполнить».
- [ ] **Шаг 4: лаунчер.** `LauncherViewModelTest > a model session started without a card records what it teaches
  once` и `… shows the model mark on its card`; строки метки — **переиспользовать** `launcher_interpretation_model_value`
  нельзя (у неё аргумент) → новая `launcher_agent_model_mark`: `ru` «Предложено моделью», `en` "Suggested by the
  model", `tr` "Model tarafından önerildi" (на суд).
- [ ] **Шаг 5: GREEN** — scoped `:domain:jvmTest`, `:feature:launcher:testDebugUnitTest`, `:app:testDebugUnitTest`
  (`OneModelPathGuardTest`, `BridgePayloadGuardTest` — зелёные **без правки**; правка — находка, стоп).
- [ ] **Шаг 6: ADR поправки** в конец `ai-context/decisions.md`: «2026-11-2x — D3, вторая поправка: прочтение без выбора
  и без текста модели» + строка в `ai-context/decisions-orientation.md` (поправка 2026-11-20 — «в силе», рядом новая);
  матрица `DOC-NYH-1` — новый тест в третьем столбце (`DoctrineMatrixGuardTest`).
- [ ] **Шаг 7: коммит** `feat(a4-tail/1+D3): одна карточка Sidr — прочтение модели без выбора стартует через движок`.
- [ ] **Шаг 8: мутации (`mutation-prover-deep`):** M4-1 `needsCard()` → `false` → `… sends the model's text … gets the
  card`, `… owes a pick gets the card`; M4-2 ветка стартует и при `needsCard()` → те же; M4-3 `startPlanned` с
  `risk` шага, пониженным до `SAFE` (посадка в планировщике, **проверить** пол `maxOf` в `AgentExecutor` — правило
  §0 ф4 про вакуумные посадки через порт) → `… stops at the engine's consent`.

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
- [ ] **Шаг 2: RED → реализовать → GREEN.** Строка `launcher_model_declined`: `ru` «Понял команду, но Sidr так пока не
  умеет.», `en` "Understood, but Sidr can't do that yet.", `tr` "Anladım, ama Sidr bunu henüz yapamıyor." (на суд).
  ADR 1/4 правило 3: «неизвестная команда» снимается и для понятой команды — строкой в ADR пачки.
- [ ] **Шаг 3: коммит** `feat(a4-tail/2): отказ модели — «понял, но Sidr так пока не умеет»`.

### Задача 6: ожидание — только пока спрошена модель (решение 6)

**Файлы:**
- Изменить: `RouteCommandUseCase.kt` (`route(rawInput, onModelAsked: () -> Unit = {})` — зовётся непосредственно
  перед `modelPlanner.plan`), обёртки над ним (`ResolveCommandWithPreferenceUseCase`, `ResolveCommandWithAliasUseCase`,
  `ResolvedCommandStep`) — тот же параметр насквозь, `feature/launcher/.../LauncherCommandSession.kt` (`submit`: таймер
  `THINKING_DELAY_MS` удалён, `_thinking.value = true` из колбэка)
- Тесты: `RouteCommandUseCaseTest.kt`, `LauncherViewModelTest.kt`

- [ ] **Шаг 1:** `RouteCommandUseCaseTest > the model-asked signal fires once, right before the model, and never on a
  local path` (FastPath, 2b, 2c, local-only — 0; шаг 7 — 1); `LauncherViewModelTest > a slow local path shows no wait`
  (фейк 2b с задержкой 1 с виртуального времени → `thinking` ни разу не `true`) и `the wait shows when the model is
  asked` (переписанный существующий тест B-10 — найти по `THINKING_DELAY_MS`).
- [ ] **Шаг 2: RED → реализовать → GREEN** (`:domain`, `:feature:launcher`).
- [ ] **Шаг 3: коммит** `feat(a4-tail/6): «Разбираю команду…» — только пока спрошена модель`.

### Граница пачки 1

- [ ] `gradlew --stop`; разбивка по модулям выписана; `tools/gate.sh` → `GATE GREEN` ≥ пола; `:app:assembleRelease`
  (переподпись `settings_local_only_description` — до этого прогона).
- [ ] Финальное ревью пачки — `reviewer-deep` по диффу пачки; пол `BASELINE_TESTS` поднимается на итог.

---

## Пачка 2 — интерфейс

### Задача 7: нумерация шагов (решение 4)

**Файлы:** `feature/launcher/.../agent/AgentSessionSurface.kt` (`AgentPlanSteps`), карточка толкования
(`AgentSessionPresentation.kt` — строки шагов и `launcher_interpretation_from_step`), строки `en`/`ru`/`tr`;
тест — `AgentSessionPresentationTest.kt`.

- [ ] **Шаг 1:** `a plan of two or more steps numbers its lines from one` — строка шага `"1. %s — %s"`; `a one-step plan
  is not numbered`; `«← из шага N» names the number the card shows` (номер шага-источника совпадает с номером строки).
- [ ] **Шаг 2: RED → реализовать → GREEN.** Новая строка `launcher_agent_step_line_numbered`: `%1$d. %2$s — %3$s` во
  всех трёх локалях (турецкий порядок — на суд); однoшаговый план — прежняя `launcher_agent_step_line`.
- [ ] **Шаг 3: коммит** `feat(a4-tail/4): шаги карточки пронумерованы`.

### Задача 8: чип будильника в строке даты (решение 5: а, у левого края)

**Файлы:**
- Создать: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/alarm/NextAlarm.kt` (`data class NextAlarm(val
  triggerEpochMs: Long)`, `interface NextAlarmSource { fun observe(): Flow<NextAlarm?> }`)
- Создать: `core/android/src/main/java/com/sidr/launcher/core/android/alarm/AndroidNextAlarmSource.kt` — `callbackFlow`
  над `AlarmManager.getNextAlarmClock()` и `ACTION_NEXT_ALARM_CLOCK_CHANGED`; фильтр: `showIntent?.creatorPackage` ==
  пакет, разрешающий `AlarmClock.ACTION_SHOW_ALARMS` (`PackageManager.resolveActivity`), иначе `null`
- Изменить: `LauncherScreen.kt` (`HomeDateLine` — `Box` с датой по центру и чипом `Alignment.CenterStart`),
  `LauncherViewModel.kt` (`nextAlarm: StateFlow<NextAlarm?>`, `onAlarmChipTapped()` → K7), DI-модуль `:app`
- Тесты: `core/android/src/test/.../AndroidNextAlarmSourceTest.kt` (Robolectric `ShadowAlarmManager`),
  `LauncherViewModelTest.kt`, Roborazzi-сценарий Home с чипом (`feature/launcher` или `core/ui` — где живут золотые
  Home; найти `grep -rln "HomeDateLine\|launcher_home" */src/test`)

- [ ] **Шаг 1:** `AndroidNextAlarmSourceTest`: `the clock's own alarm is shown` (creator = пакет часов → `NextAlarm`),
  `an alarm set by another app is not` (creator = `com.samsung.android.fmm` → `null`; Фокус ревью 5), `no alarm - null`,
  `a change broadcast re-reads`. `LauncherViewModelTest > tapping the alarm chip starts show_alarms through the engine`
  (`startPlanned` с одним шагом `show_alarms`, `ToolExecutor` — через движок).
- [ ] **Шаг 2: RED → реализовать → GREEN.** Время — `DateFormat.getTimeFormat(context)` (24 ч по настройке телефона),
  форматируется в feature-слое. Строки: `launcher_home_next_alarm` `⏰ %1$s` (`en`/`ru`/`tr` одинаково),
  `launcher_home_next_alarm_cd` `ru` «Следующий будильник: %1$s», `en` "Next alarm: %1$s", `tr` "Sonraki alarm: %1$s".
- [ ] **Шаг 3: проверить по дереву, что оставляет на Home завершённая сессия из одного `SAFE`-шага** (как у «системные
  настройки» 2b). Если карточку «План выполнен» — она закрывается сама по возвращении на Home именно для сессии чипа;
  предсказание до прогона записать, расхождение — находка.
- [ ] **Шаг 4: золотые** — `:core:ui:verifyRoborazziDebug` (или модульный аналог): новые снимки «с будильником» /
  «без», `recordRoborazziDebug` только для них; изменение чужих снимков — стоп.
- [ ] **Шаг 5: коммит** `feat(a4-tail/5): чип будильника часов в строке даты`.

### Задача 9: «Отменить» и «Закрыть» одного размера (решение 7)

**Файлы:** `core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrActionSafety.kt` (`SidrSurfaceActions(…,
equalWidth: Boolean = false)` — в ветке `Row` обе кнопки `Modifier.weight(1f)`), параметр протянуть через
`SidrResultSurface`; включить на карточке законченного плана (`AgentSessionSurface.kt`); золотые `core/ui`.

- [ ] **Шаг 1:** Roborazzi-сценарий `result_surface_equal_actions` (обе кнопки, равная ширина) — новый снимок;
  существующие 36 снимков **не меняются** (`verifyRoborazziDebug` зелёный на них).
- [ ] **Шаг 2: реализовать → `recordRoborazziDebug` нового → `verifyRoborazziDebug` GREEN.**
- [ ] **Шаг 3: коммит** `feat(a4-tail/7): кнопки законченного плана — равной ширины`.

### Задача 10: поле ключа над клавиатурой (решение 9)

**Файлы:** `feature/assistant/src/main/java/com/sidr/launcher/feature/assistant/AssistantScreen.kt`
(`AssistantProviderScreen` — `Modifier.imePadding()` на прокручиваемой колонке; поле ключа — `BringIntoViewRequester`
при фокусе).

- [ ] **Шаг 1:** Compose-тест (Robolectric) `the key field asks to be brought into view on focus` — фокус на поле →
  вызван `bringIntoView` (фейк-реквестер через параметр по умолчанию). Подъём над настоящей IME тестом не доказуем —
  **строка чеклиста приёмки**, не утверждение плана.
- [ ] **Шаг 2: RED → реализовать → GREEN** (`:feature:assistant:testDebugUnitTest`).
- [ ] **Шаг 3: коммит** `fix(a4-tail/9): поле ключа провайдера не прячется под клавиатурой`.

### Задача 11: выученные алиасы — своей вкладкой (решение 10)

**Файлы:** `feature/settings/.../AliasesScreen.kt` (две `SidrFilterChip` вверху: «Мои алиасы» / «Добавить»; по
умолчанию «Мои алиасы», если они есть, иначе «Добавить»), строки `settings_aliases_tab_mine` / `settings_aliases_tab_add`
(`ru` «Мои алиасы» / «Добавить», `en` "My aliases" / "Add", `tr` "Takma adlarım" / "Ekle" — на суд); тест — новый
`feature/settings/src/test/.../AliasesScreenTest.kt` (Robolectric).

- [ ] **Шаг 1:** `with aliases the screen opens on them, not on the app list`; `«Забыть» still goes through the forget
  gate` (`SidrForgetGate`, `DOC-AML-5` — как сейчас); `with none it opens on Add`.
- [ ] **Шаг 2: RED → реализовать → GREEN** (`:feature:settings`).
- [ ] **Шаг 3: коммит** `feat(a4-tail/10): выученные алиасы — отдельной вкладкой`.

### Задача 12: избранное — закреплённые, свежесть, окно выбора, до 12 мест (решение 15)

**Файлы:**
- Изменить: `domain/.../preferences/UserPreferences.kt` (`favoritesCount = 12`, `pinnedFavorites: List<String> =
  emptyList()`, `hiddenFavorites: Set<String> = emptySet()`), `data/repository/.../preferences/PreferencesKeys.kt`,
  `PreferencesMapper.kt`
- Изменить: `feature/launcher/.../LauncherAppList.kt` (`deriveFavorites(records, installed, prefs, nowMs)`),
  `LauncherScreen.kt` (касание заголовка «Избранное» → окно выбора), `LauncherViewModel.kt` (`pin`/`unpin`/`hide`)
- Изменить: `feature/settings/.../SettingsUiState.kt` (`FAVORITES_COUNT_OPTIONS = listOf(4, 6, 8, 10, 12)`)
- Тесты: `LauncherViewModelTest.kt` (или новый `LauncherAppListFavoritesTest.kt`), `SettingsViewModelTest.kt`,
  `PreferencesMapper`-тест (найти `grep -rln "favoritesCount" data/repository/src/test`)

**Правило `deriveFavorites`** (K8): сначала закреплённые в своём порядке (только установленные), затем записи
использования по прежнему порядку, **кроме** скрытых, закреплённых и тех, чей `lastUsedEpochMs < nowMs − 14 дней`;
всё — до `favoritesCount`. Пустая история и нет закреплённых — пусто (как сейчас).

- [ ] **Шаг 1:** `pinned apps come first and are never pushed out`; `an app not opened for 14 days leaves the
  automatic places`; `a new app enters once it outranks the last fresh one` (по таблице §0.1: 14 запусков старого,
  но устаревшего → новое с 1 запуском входит); `a hidden app is not shown and its history stays`; `an uninstalled
  pinned app is skipped`; `the count defaults to 12 and offers 12` (`SettingsViewModelTest`); маппер — круговой тест
  трёх новых ключей.
- [ ] **Шаг 2: RED → реализовать → GREEN.** Окно выбора — `SidrSurface` с `LazyColumn` строк `SidrToggleRow`
  (закреплено / нет) над всеми установленными приложениями, закреплённые сверху; у автоматически попавших — действие
  «Убрать» (`hide`); закрепление скрытого снимает скрытие (тест `pinning a hidden app unhides it`). Строки: `launcher_favorites_edit_title` «Избранное», `launcher_favorites_pin` «Закрепить»,
  `launcher_favorites_hide` «Убрать с главного», `launcher_favorites_edit_hint` «Закреплённые стоят первыми, остальные
  места заполняются тем, что вы открываете чаще.» (`en`/`tr` — на суд).
- [ ] **Шаг 3: правила резервной копии.** Новые ключи DataStore — тот же файл, что исключён из Auto Backup
  (`BackupRulesGuardTest` зелёный без правки; правка — находка).
- [ ] **Шаг 4: золотые Home** — только снимки, где видно избранное и окно выбора; прочие не меняются.
- [ ] **Шаг 5: коммит** `feat(a4-tail/15): избранное — закреплённые, свежесть 14 дней, окно выбора, до 12 мест`.

### Граница пачки 2

- [ ] `gradlew --stop`; разбивка; `tools/gate.sh` → `GATE GREEN`; `:core:ui:verifyRoborazziDebug` GREEN; ревью пачки.

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

- [ ] **Шаг 1:** `a value the model declined is learned as the user's slot` («поищи погоду в казани» + `web_search
  query = "погода в казани"` → форма `поищи <0>`, повтор «поищи погоду в москве» → `"погоду в москве"`); `two places of
  the same form - not learned`; `a model value with no word of the user stays a constant`; `an exact value is learned as
  before` (существующий — не меняется).
- [ ] **Шаг 2: RED → реализовать → GREEN** (`:domain`).
- [ ] **Шаг 3: коммит** `feat(a4-tail/8): слот выученной формы находится и по формам слова`.
- [ ] **Шаг 4: мутация** M13-1 «край» не требуется (только точные пары) → `a value the model declined…`; M13-2 два места
  допускаются (берётся первое) → `two places … not learned`.

### Граница пачки 3 — граничный гейт, ревью `reviewer-deep`.

---

## Закрытие

### Задача 14: замер после, чеклист приёмки, приёмка, закрытие

- [ ] **Замер после:** тот же одноразовый прогон §0.1 на дереве после пачки 1 — таблица «до / после» в ADR блока
  (предсказание: четыре поиска и два «открой …» в сети уходят к модели; local-only — как до).
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
