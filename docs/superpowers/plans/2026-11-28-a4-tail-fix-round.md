# Хвост A4′ — раунд исправлений после приёмки 2026-11-28 — план реализации

> **Для агентных исполнителей:** ОБЯЗАТЕЛЬНЫЙ ПОДСКИЛ — superpowers:subagent-driven-development (метод назначен владельцем
> для хвоста: «субагенты») или superpowers:executing-plans; шаги — чекбоксы (`- [ ]`). План — **гипотеза** по правилу §0
> «Механика SDD-блока»: адреса, имена тестов и предсказанные наборы `FAILED ::` сверяются с деревом при исполнении;
> расхождение — находка, план правится.

**Цель:** исправить то, что приёмка хвоста A4′ 2026-11-28 ([чеклист](2026-11-28-a4-tail-acceptance.md), отметки
владельца) нашла иначе, чем написано, — по решениям владельца §0.2 — и повторно принять на телефоне только изменённые
пункты.

**Архитектура:** четыре группы по месту в коде. **Имена приложений** — `:domain` (шаг 1a маршрута, текст инструкции
модели) и `:data:repository` (`AppTargetResolver.exactly`, `DeviceMentionBinder`). **Правило карточки** — `:domain`
(`Interpretation.Act`, `needsCard`, шаг 2c). **Запись использования** — один декоратор `ActionExecutor` вместо двух
копий во ViewModel. **Интерфейс** — `core/ui`, `:feature:launcher`, `:feature:settings`; чип будильника удаляется.

**Стек:** Kotlin 2.4.10, KMP `:domain` (`commonMain` / `jvmTest`), Jetpack Compose, Hilt, Room (схема 5 — **не
меняется**), DataStore, Roborazzi.

**Вход (спеки нет — раунд исправлений закрытого блока):** отметки владельца в чеклисте приёмки, разбор §0.1 и ответы
владельца §0.2 (разговор сессии 2026-11-28). Решения, которые меняют действующее правило, получают поправку в ADR
раунда: **D3, третья поправка** (задачи 10, 11), **решение 2026-11-17, поправка** (задача 9).

## Глобальные ограничения

- `:domain` — чистый Kotlin (`commonMain`), ничего JVM- или Android-специфичного; тесты — `jvmTest`.
- Текст для пользователя — только `sidrString(R.string.…)` в feature-слое; `en`/`ru`/`tr` — в том же коммите
  (`LocaleCompletenessGuardTest`); новые турецкие строки — на суд владельца в повторной приёмке.
- Ничего с телефона не уходит модели: список приложений, имена пакетов, алиасы остаются на устройстве (D1; план хвоста
  §0.2, «данные телефона в облако не уходят никогда»). Привязка имени — после ответа модели, на устройстве.
- `ToolExecutor` — единственный путь к миру; ни одна задача не добавляет второго места вызова
  (`ToolExecutorCallSiteGuardTest`, `ToolWorkerCallSiteGuardTest`).
- Карточка согласия движка для рискованного шага (`requiresConsent(risk)`, `DOC-ADL-1`) не снимается ни одной задачей.
- `core/ui` тронут ⇒ `:core:ui:verifyRoborazziDebug`; новый или сдвинутый снимок пересъёмка только своих сценариев,
  список изменённых PNG — в теле коммита.
- Гейт — `tools/gate.sh`; пол `BASELINE_TESTS` меняется только с разбивкой по модулям в ADR (задача 3 его **снижает**:
  удалённые тесты чипа).

## Фокус ревью

1. **Касание иконки после переноса записи считается один раз.** Сетка, ящик и движок пишут через один декоратор; старые
   `recordUsage` во ViewModel удалены — двойной счёт исказил бы избранное (задача 2,
   `a grid tap is recorded once`).
2. **Выученный `open_url` без карточки толкования всё равно останавливается на согласии движка** (задача 11,
   `a learned open_url replays without the card and still stops at consent`).
3. **Адрес от модели по-прежнему требует карточку**, хотя длительность и время — нет (задача 10,
   `a model-written url still needs the card`).
4. **Голая метка не перекрывает решённое FastPath**: «помощь», «настройки», голая «камера», которую FastPath решает,
   отвечаются как раньше (задача 6, `a bare label never shadows a decided FastPath outcome`).
5. **Второй потребитель не начинает переименовывать файлы**: у `:consumer:jvm` упоминание файла остаётся «словами
   пользователя» (задача 7, `a file mention keeps the user's words rule`).

---

## §0. Перед планом

### §0.1. Что нашла приёмка и почему (разбор 2026-11-28, база телефона снята агентом только на чтение)

Сборка на телефоне — `0.1.0+0a7084b` (установлена 2026-11-28 04:15). Ответ модели на телефоне не хранится (сессия
удаляется вместе со следом на терминальном состоянии), поэтому причины у Ф1 — **код + совпадение симптомов**, не запись
ответа.

| # | Пункт чеклиста | Увидено | Причина |
|---|---|---|---|
| Ф1 | A1, A6, D | «открой плюс», «открой cloude» → магазин; «открой клод и поставь таймер…» → «клод не найдено»; «карты» → «Maps не найдено»; «plus» → вопрос модели по-английски | Модели велено повторять **слова пользователя** (`AuthoredToolDescriptions.THE_APP` = "the app, in the user's own words"; `BridgeRendering.INSTRUCTION` — исправлять только явную опечатку), и она их повторяет; слово модели привязывается только к точной метке (`AppTargetResolver.exactly`) — «Maps» ≠ метка «Карты» (ru-сплит `com.google.android.apps.maps`); голая метка («карты», «plus») не открывается на устройстве — шаг 1a знает только алиас; язык вопроса модели не задан |
| Ф2 | A3 | «Вы имели в виду Claude?», затем карточка согласия | Решение 2026-11-17: необратимый инструмент + имя, написанное моделью ⇒ `forIrreversible` → `AppCandidates.Confirm` (`DeviceMentionBinder.bindMention`) |
| Ф3 | A6 | «открой claude и поставь таймер на 5 минут» → карточка «Понял так» | Длительность модель пишет сама («5 minutes»), `set_timer` — `EXTERNAL`, `modelTextGoingOut` считает её текстом наружу; на деле наружу уходит число (`Tier0IntentToolWorker`: `DurationSort.secondsOf` → `EXTRA_LENGTH`; время — `EXTRA_HOUR`/`EXTRA_MINUTES`) |
| Ф4 | B3 | чипа нет никогда | `com.masarat.salati` ставит ~10 `setAlarmClock` в день (06:00, 07:00, 07:32 …, `ContinuousNotificationReceiver` — не звонит, обновляет своё уведомление); `getNextAlarmClock()` отдаёт только самый ранний, фильтр «только часы» его верно отвергает — будильник часов не виден. Открытого чтения будильников часов Samsung нет (`dumpsys package providers`) |
| Ф5 | B6 | открытое командой не попадает в избранное | Запись использования — только в `LauncherAppLaunch.recordUsage` и `AppDrawerViewModel.recordUsage` (касание); запуск через движок не пишет (`LauncherAppLaunch.kt:85`, признано в комментарии). `app_usage` — последняя запись 2026-11-22 |
| Ф6 | C2, C3 | «поищи погоду в казани» выучена (streak 3), но каждый повтор — карточка | Запрос — константа, написанная моделью ⇒ `needsCard()` на шаге 2c |
| Ф7 | B2, B5 | «Отмена»/«Выполнить» разной ширины; вкладки алиасов разного размера | `equalWidth` включён только на карточке законченного плана (`AgentSessionSurface.kt:286`); вкладки — две `SidrFilterChip` по ширине текста |
| Ф8 | D | «есть небольшая задержка» у попаданий FastPath | не измерено — записывается, в раунд не входит |

Прошли как написано: A1 (клод, local-only, онлайн-банк), A2, A4, A5, B1, B4 (зазор приемлем), B5 (остаться на
«Добавить» — удобно, вопрос 2 пачки 2), B6 (окно, два закрепления, число мест 8 — вопрос 3 пачки 2), E (все `tr`,
включая Class B). C1 не прогонялся.

### §0.2. Решения владельца 2026-11-28 — дословно по смыслу

- **1: б** — поиск по-прежнему уступает модели (13: Б в силе).
- **2: а** — удаление по имени, написанному моделью, — одна карточка: карточка согласия называет приложение и пакет,
  «Вы имели в виду» для имени от модели убрать.
- **3: б** — чип будильника отложить до блока «уведомления как источник агента» (A6), сейчас убрать.
- **4: а** — (i) голая команда, точно равная метке установленного приложения, открывает его на устройстве без модели;
  (ii) модели велено писать обычное (официальное) имя приложения; (iii) имя от модели связывается ещё и с последней
  частью имени пакета, точно; вопрос модели — на языке запроса.
- **5: а** — длительность и время, прочитанные устройством, — не текст модели; карточка остаётся для свободного текста
  (поиск) и адреса.
- **6: а** — долгое нажатие на иконку в избранном → «Убрать с главного», в этом раунде.
- **Выученный поиск: «Без карточки»** — повтор выученной команды (тот же текст, подтверждённый трижды) карточку не
  показывает; карточка — только если нужен выбор.
- **Class B `tr` (`settings_local_only_description`)** — принят (`[х]` в чеклисте) ⇒ подпись `OWNER-REVIEWED`.
- **Без решения, как дефекты:** запись использования при запуске командой; равная ширина кнопок на всех карточках;
  равные вкладки алиасов.
- **Замечание владельца 2026-11-28:** «в будущем планируется локальная модель, которая будет иметь доступ к системе на
  глубоком уровне» — учтено в §0.3.

### §0.3. Учёт будущей локальной модели (замечание владельца)

Каждая задача проверена вопросом «не придётся ли её ломать, когда планировщиком станет локальная модель с глубоким
доступом»:
- **Имена приложений (задачи 6–9).** Облачной модели список приложений не отдаётся (правило в силе); локальной — можно,
  данные не покидают телефон. Поэтому всё «знание телефона» остаётся за портом `MentionBinder` и в шаге 1a, а не
  вшивается в текст инструкции: локальный планировщик получит список приложений через свою собственную реализацию
  `ModelPlanner`, а привязка (метка, алиас, хвост пакета) останется проверкой его ответа. Текст инструкции задачи 7 — для
  модели, **не видящей** телефона; локальной будет своя.
- **Запись использования (задача 2)** — на исполнителе действия, а не на пути команды: любой будущий планировщик,
  открывающий приложение через движок, записывается тем же декоратором.
- **Правило карточки (задачи 10, 11)** — по эффекту и происхождению значения, не по тому, какая модель его написала:
  локальная модель с доступом к системе проходит те же ворота (`DOC-ADL-1`, `ToolExecutor`).
- **Чип будильника (задача 3)** — его честная версия требует чтения уведомлений часов, то есть доступа уровня A6;
  удаляемый код адресуется в ADR (sha), A6 строит заново поверх своего доступа.

---

## Группа 1 — без решений и малое

### Задача 1: подпись `OWNER-REVIEWED` для `tr` Class B

**Файлы:** тот `values-tr/strings_locked.xml`, который держит `settings_local_only_description` (найти:
`grep -rln settings_local_only_description --include=strings_locked.xml */*/src/main/res/values-tr`).

- [ ] **Шаг 1:** `./gradlew :app:checkOwnerReviewedLocaleStrings` (JDK 17) — красный, печатает `sign it with:
  sha256:<16 hex>` для этого файла.
- [ ] **Шаг 2:** вписать в его комментарий-подпись `OWNER-REVIEWED 2026-11-28 sha256:<напечатанное>` (по образцу
  `feature/permission_education/src/main/res/values-tr/strings_locked.xml:4`); повтор задачи — зелёный.
- [ ] **Шаг 3: коммит** `chore(a4-tail/fix): подпись владельца для tr Class B (приёмка 2026-11-28)`.

### Задача 2: запуск записывается в одном месте — декоратор `ActionExecutor` (Ф5)

**Файлы:**
- Создать: `data/repository/src/main/java/com/sidr/launcher/data/repository/intent/UsageRecordingActionExecutor.kt` —
  `class UsageRecordingActionExecutor(delegate: ActionExecutor, usage: UsageHistoryRepository, flags:
  FeatureFlagRepository, nowMs: () -> Long) : ActionExecutor`.
- Изменить: `app/src/main/java/com/sidr/launcher/di/IntentBindsModule.kt` (биндинг `ActionExecutor` → декоратор поверх
  `AndroidActionExecutor`; `@Binds` → `@Provides`, если так проще).
- Изменить: `feature/launcher/.../LauncherAppLaunch.kt` (удалить `recordUsage` и его вызов, комментарий `:85`),
  `feature/launcher/.../AppDrawerViewModel.kt` (удалить `recordUsage`), их конструкторы — без
  `usageHistoryRepository`, если он больше ни для чего не нужен.
- Тесты: новый `data/repository/src/test/.../intent/UsageRecordingActionExecutorTest.kt`; тесты ViewModel, которые
  проверяли запись (`grep -rn "recordLaunch\|recordedPackages" feature/launcher/src/test`), переносятся сюда, не
  удаляются молча.

**Правило:** после `delegate.execute(action)` — если `action is ExecutableAction.LaunchAppAction` и результат
`ActionExecutionResult.Success` и `flags.getFlags().first().usageHistoryEnabled` — `usage.recordLaunch(action.packageName,
nowMs())`; ошибка записи и любое исключение кроме `CancellationException` проглатываются; возвращается результат делегата
без изменений.

- [ ] **Шаг 1:** `a successful launch is recorded`; `a failed launch is not recorded`; `with usage history off nothing is
  recorded`; `a non-launch action is not recorded`; `a failing record never changes the launch result`; в
  `LauncherViewModelTest` — `a grid tap is recorded once` (фейк-исполнитель + декоратор, один `recordLaunch`; Фокус
  ревью 1); `a launch through the engine is recorded` — сессия `launch_app` через продакшн-цепочку
  `SystemIntentToolWorker → ExecuteActionUseCase → … → декоратор` (если цепочка в тесте не собирается без Android —
  тест в `:app` на продакшн-графе, как `AliasBindingGuardTest`).
- [ ] **Шаг 2: RED → реализовать → GREEN** (`--scoped :data:repository:testDebugUnitTest :feature:launcher:testDebugUnitTest :app:testDebugUnitTest`).
- [ ] **Шаг 3: коммит** `fix(a4-tail/fix): запуск записывается одним декоратором — и командой, и касанием`.

### Задача 3: чип будильника удалён (решение 3: б, Ф4)

**Файлы (удалить):** `domain/.../alarm/NextAlarm.kt`, `core/android/.../alarm/AndroidNextAlarmSource.kt` + его тест,
`core/testing/.../FakeNextAlarmSource.kt`, `app/.../di/NextAlarmModule.kt`, `core/ui/.../component/SidrNextAlarmChip.kt` +
`SidrNextAlarmChipTest.kt` + `SidrNextAlarmChipScreenshotTest.kt` + `core/ui/src/test/screenshots/next_alarm_chip.png`,
`feature/launcher/src/test/.../LauncherScreenAlarmChipTest.kt`. **Изменить:** `LauncherViewModel.kt` (`nextAlarm`,
`onAlarmChipTapped`, параметр `nextAlarmSource`), `LauncherViewModelTest.kt` (три теста `:4282`, `:4305`, `:4329`),
`LauncherScreen.kt` (`HomeDateLine` — дата по центру, как до задачи 8), строки `launcher_home_next_alarm` /
`launcher_home_next_alarm_cd` в трёх локалях; `core/android/build.gradle.kts` — Robolectric и coroutines-test оставить,
только если их читает другой тест модуля (`grep -ln robolectric core/android/src/test -r`).
**Не трогать:** `<queries>` `SHOW_ALARMS` в манифесте — его читает инструмент `show_alarms`.

- [ ] **Шаг 1:** удалить; `grep -rn "NextAlarm\|next_alarm\|AlarmChip" --include=*.kt --include=*.xml . | grep -v /build/`
  — пусто.
- [ ] **Шаг 2:** предсказание: −15 тестов (`:core:android` −6, `:core:ui` −3, `:feature:launcher` −6); `--scoped` по
  трём модулям зелёный; `:core:ui:verifyRoborazziDebug` зелёный (36 снимков, ни один, кроме удалённого, не сдвинут).
- [ ] **Шаг 3: коммит** `revert(a4-tail/fix): чип будильника убран до A6 — слот следующего будильника занят чужими
  setAlarmClock` (в теле — sha коммита, который его ввёл, для A6).

### Задача 4: равные кнопки и равные вкладки (Ф7)

**Файлы:** `core/ui/.../component/SidrActionSafety.kt` (`SidrSurfaceActions`: обе кнопки делят ширину всегда, когда
есть обе; параметр `equalWidth` убрать вместе с его протяжкой через `SidrResultSurface` и местом
`AgentSessionSurface.kt:286`), `SidrActionSafetyTest.kt` (тест K5 `:192` — без параметра),
`SidrResultSurfaceScreenshotTest.kt` (KDoc `:22`); `feature/settings/.../AliasesScreen.kt` (`AliasTabs`: `Row`
шириной `IntrinsicSize.Max`, каждая `SidrFilterChip` — `Modifier.weight(1f)`: обе по ширине большей, а не по ширине
экрана), тест в `AliasesScreenTest.kt`.

- [ ] **Шаг 1:** `every two-button surface gives both buttons the same width` (`SidrActionSafetyTest`, бывший K5 — для
  карточки согласия, толкования и результата); `the alias tabs are the same width` (`AliasesScreenTest`, ширины узлов
  равны).
- [ ] **Шаг 2: RED → реализовать → GREEN**; `:core:ui:recordRoborazziDebug` только для снимков с двумя кнопками,
  затем `verifyRoborazziDebug` GREEN; изменённые PNG — списком в теле коммита.
- [ ] **Шаг 3: коммит** `fix(a4-tail/fix): две кнопки карточки — одной ширины везде; вкладки алиасов — одной ширины`.

> Толкование «по примеру «Добавить»» — обе вкладки одной ширины (по большей). Если владелец имел в виду иное (обе
> короче, по «Добавить»), — правка на ревью плана.

### Задача 5: долгое нажатие на иконку избранного → «Убрать с главного» (решение 6: а)

**Файлы:** `core/ui/.../component/AppTile.kt` (`onLongClick: (() -> Unit)? = null`, `onLongClickLabel: String? =
null` → `combinedClickable`; без них — как сейчас), новый `core/ui/.../component/SidrContextMenu.kt`
(`SidrContextMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<SidrMenuItem>)`, `data class SidrMenuItem(label:
String, onClick: () -> Unit)`; Material3 `DropdownMenu` в цветах темы) + Roborazzi-сценарий `context_menu`;
`LauncherScreen.kt` (`FavoritesGrid`: долгое нажатие открывает меню из одного пункта `launcher_favorites_hide` →
`onHideFavorite(pkg)`; строка уже есть во всех трёх локалях), тест `LauncherScreenFavoritesTest.kt`.

- [ ] **Шаг 1:** `a long press on a favourite offers to remove it from Home` (`performTouchInput { longClick() }` →
  пункт виден → касание → `hideFavorite(pkg)` один раз); `a tap still launches` (без регрессии);
  `AppTile` без `onLongClick` — снимки сетки не меняются.
- [ ] **Шаг 2: RED → реализовать → GREEN**; `recordRoborazziDebug` только `context_menu`; `verifyRoborazziDebug` GREEN.
- [ ] **Шаг 3: коммит** `feat(a4-tail/fix): долгое нажатие на иконку избранного — «Убрать с главного»`.

---

## Группа 2 — имена приложений (решение 4: а, Ф1)

### Задача 6: голая метка установленного приложения открывает его на устройстве (4: а, i)

**Файлы:** `domain/.../ai/router/RouteCommandUseCase.kt` (шаг 1a: после алиаса — метка), параметр конструктора
`folding: TextFolding = PlainTextFolding`; `app/.../di/BridgeProvidesModule.kt` — передать продакшн-`TextFolding`
(то, что уже получает `DeviceMentionBinder`); тесты — `RouteCommandUseCaseTest` (`:domain:jvmTest`).

**Правило:** в том же месте, что `aliasLaunch` (только `ruleOutcome.isUndecided()` и `fastPath.yielded == null`), если
алиас не ответил: `AppNameMatcher.matchWhole(CommandNormalizer.normalize(raw), installed, folding)` — `NameMatch.Exact`
ровно с **одним** приложением ⇒ тот же план, что у `aliasLaunch` (один `launch_app`, `query` = слова пользователя,
`app` = пакет), `startPlanned`; `Edge`, `None`, несколько приложений, список не прочитан, старт не удался ⇒ `null`,
цепочка дальше без изменений. KDoc шага 1a — «алиас или точная метка»; довод `DOC-ADL-3` тот же (ничего не уходит,
модели нет, действие — через движок).

- [ ] **Шаг 1:** `a bare installed label opens its app on the device and the model is never asked` («карты» → метка
  «Карты»); `a bare label differing only in case opens it` («plus» → «Plus»); `two apps with the same label - the chain
  goes on`; `a word form of a label is not this step's` («карт» → дальше); `a bare label never shadows a decided
  FastPath outcome` (Фокус ревью 4); `in local-only mode a bare label still opens` (как алиас); `the words of a bare
  label never reach the model`.
- [ ] **Шаг 2: RED → реализовать → GREEN** (`--scoped :domain:jvmTest`, затем `:app:testDebugUnitTest` — граф).
- [ ] **Шаг 3: коммит** `feat(a4-tail/fix): голое имя установленного приложения открывает его без модели`.

### Задача 7: инструкция модели — обычное имя приложения и язык вопроса (4: а, ii)

**Файлы:** `domain/.../ai/bridge/BridgeRendering.kt` (`INSTRUCTION`; метка аргумента `:235`),
`data/repository/.../agent/AuthoredToolDescriptions.kt` (`THE_APP`), `consumer/jvm/.../bridge/SandboxToolDescriptions.kt`
(проверить, не править текст), тесты — `BridgeRenderingTest`, `ToolSchemaWeightTest` (число символов), тест
`:consumer:jvm` на текст промпта.

**Правило:** метка упоминания разводится по сорту: упоминание сорта `ArgSorts.APP` — `"app"`, любое другое упоминание
(файл `:consumer:jvm`) — `"words"`. Предложение инструкции о приложениях заменяется двумя (текст — точно):

> `An argument marked "app" names an app: write the app's usual name as it is published, correcting the user's
> spelling, transliteration or translation; if you know no such app, repeat the user's words. An argument marked
> "words" must repeat the user's own words as they typed them.`

и после строки `"clarify"` — `Write any question in the language of the request.` `THE_APP` = `"the app's usual name"`.
`BridgeValidator` не меняется: имя, которого нет в словах пользователя, по-прежнему `ValueOrigin.ModelGenerated` (D1).

- [ ] **Шаг 1:** `an app mention is marked app and a file mention is marked words`; `a file mention keeps the user's
  words rule` (`:consumer:jvm`, Фокус ревью 5); `the instruction asks for the question in the request's language`;
  `ToolSchemaWeightTest` — новое число (вычисляется прогоном, вписывается с разбивкой «инструкция N → M», как `:45`).
- [ ] **Шаг 2: RED → реализовать → GREEN** (`--scoped :domain:jvmTest :data:repository:testDebugUnitTest :consumer:jvm:test`).
- [ ] **Шаг 3: коммит** `feat(a4-tail/fix): модели велено писать обычное имя приложения и спрашивать на языке запроса`.

### Задача 8: имя от модели находит приложение и по хвосту пакета (4: а, iii)

**Файлы:** `data/repository/.../agent/AppTargetResolver.kt` (`exactly`), тест `AppTargetResolverTest.kt`,
`DeviceMentionBinderTest.kt`.

**Правило:** `exactly(name)`: как сейчас (алиас или точная метка); если ответ `None` — установленные приложения, у которых
**последний сегмент** имени пакета (после последней `.`) равен `folding.fold(name)` с удалёнными пробелами и `-`/`_`.
Одно — `AppCandidates.One(app, foldedOnly = true)`; несколько — `AppCandidates.Several`; ни одного — `None`. Только для
`exactly` (имя модели): `candidates` (слова пользователя) хвост пакета не читает. Пакет модели не уходит и модели не
показывается — сравнение на устройстве, после ответа.

- [ ] **Шаг 1:** `a model name matching no label binds by the package's last segment` («Maps» →
  `com.google.android.apps.maps` при метке «Карты»); `two packages with the same last segment are a pick`
  (`com.a.app`, `com.b.app` ← «App»); `a middle segment does not match` («Telegram» ↛ `org.telegram.messenger`); `the
  user's own words never match by package`; `DeviceMentionBinderTest > «карты» read as Maps binds Google Maps`.
- [ ] **Шаг 2: RED → реализовать → GREEN** (`--scoped :data:repository:testDebugUnitTest`).
- [ ] **Шаг 3: коммит** `feat(a4-tail/fix): имя приложения от модели находится и по хвосту имени пакета`.

### Задача 9: удаление по имени от модели — одна карточка согласия (решение 2: а, Ф2)

**Файлы:** `data/repository/.../agent/DeviceMentionBinder.kt` (`bindMention`), KDoc `AppTargetResolver.forIrreversible`
и класса `DeviceMentionBinder` («For an irreversible tool even an exact answer is a pick»), тест
`DeviceMentionBinderTest.kt`.

**Правило:** для `modelWritten` результат `appTargets.exactly(mention)` **не** проходит `forIrreversible`: одно
приложение связывается (`One`), карточка согласия движка называет метку и пакет
(`launcher_agent_step_uninstall`, `AgentSessionPresentation.kt:350`). Для слов пользователя `forIrreversible` — как
было (решение 2026-11-17 для набранного имени в силе). Несколько — выбор, как было.

- [ ] **Шаг 1:** `uninstall by a name the model corrected binds once and leaves the asking to consent`;
  `uninstall by a name the user typed exact only after folding is still confirmed` (существующий — не меняется);
  `RouteCommandUseCaseTest > online «удали приложение клод» read as Claude starts one session that stops at consent`
  (фейк-биндер с `One` → `AgentSessionStarted`, движок — `AwaitingConsent`).
- [ ] **Шаг 2: RED → реализовать → GREEN.**
- [ ] **Шаг 3: мутация (`mutation-prover`)** — вернуть `forIrreversible` для `modelWritten` → RED
  `uninstall by a name the model corrected…`; убрать его и для слов пользователя → RED существующего теста 2026-11-17.
- [ ] **Шаг 4: коммит** `fix(a4-tail/fix): удаление по имени от модели — одна карточка согласия`.

---

## Группа 3 — правило карточки (D3, третья поправка)

### Задача 10: длительность и время, прочитанные устройством, — не текст модели (решение 5: а, Ф3)

**Файлы:** `domain/.../sort/ArgSort.kt` (`ArgSorts.READ_ON_DEVICE = setOf(DURATION, CLOCK_TIME)` — сорта, чьи слова
устройство превращает в число, а наружу уходит число; `URL` — **нет**: адрес уходит как есть),
`domain/.../ai/bridge/Interpretation.kt` (`Act.readOnDevice: Map<Int, Set<String>> = emptyMap()`; `modelTextGoingOut`
исключает эти имена, как `appWords`), `data/repository/.../agent/DeviceMentionBinder.kt` (`valuesOf` возвращает и имена
аргументов сортов `READ_ON_DEVICE`, которые прочитал; `act()` их кладёт), `consumer/jvm` — `SandboxMentionBinder`
(сорта `FILE`; поле пустое — проверить, что компилируется); тесты — `InterpretationTest`/`NeedsCardTest`
(`grep -rln "needsCard" domain/src/jvmTest`), `DeviceMentionBinderTest`, `RouteCommandUseCaseTest`.

- [ ] **Шаг 1:** `a duration the device read is not model text going out`; `a clock time the device read is not model
  text going out`; `a model-written url still needs the card` (Фокус ревью 3); `a model-written search query still
  needs the card`; `RouteCommandUseCaseTest > «открой claude и поставь таймер на 5 минут» read with «5 minutes» starts
  without the card` (`proposedByModel = true`); карточка, если всё же показана (выбор), значения длительности в
  «Модель написала» не перечисляет (`ModelValues` читает ту же функцию).
- [ ] **Шаг 2: RED → реализовать → GREEN** (`--scoped :domain:jvmTest :data:repository:testDebugUnitTest :feature:launcher:testDebugUnitTest :consumer:jvm:test`).
- [ ] **Шаг 3: мутация (`mutation-prover`)** — `URL` в `READ_ON_DEVICE` → RED `a model-written url still needs the card`;
  пустое `readOnDevice` в `act()` → RED двух первых тестов.
- [ ] **Шаг 4: коммит** `fix(a4-tail/fix): прочитанные устройством длительность и время — не текст модели (D3, третья поправка)`.

### Задача 11: повтор выученной команды — без карточки, кроме выбора (решение «Без карточки», Ф6)

**Файлы:** `domain/.../ai/router/RouteCommandUseCase.kt` (`replayLearned`: карточка только при `replay.act.plan() ==
null`, то есть при долге выбора; KDoc шага 2c и `needsCard` «One rule for two callers» → два правила),
`domain/.../memory/plan/LearnedPlans.kt` (KDoc `needsCard`), тесты `RouteCommandUseCaseTest`, `LearnedPlansTest`.

**Обоснование для KDoc и ADR:** текст модели в выученной команде — константа шаблона, ровно тот текст, который
пользователь подтвердил на карточке три раза; слот заполняется словами пользователя. Нового текста модели повтор не
несёт. Согласие движка для рискованного шага — без изменений.

- [ ] **Шаг 1:** `a learned search replays without the card` («поищи погоду в казани», шаблон `web_search query =
  "погода в казани"` константа → `AgentSessionStarted(learned = …)`); `a learned command owing a pick still shows the
  card`; `a learned open_url replays without the card and still stops at consent` (Фокус ревью 2: сессия в
  `AwaitingConsent`); `a fresh model reading with model text going out still needs the card` (шаг 7 не меняется).
- [ ] **Шаг 2: RED → реализовать → GREEN** (`--scoped :domain:jvmTest`).
- [ ] **Шаг 3: мутация (`mutation-prover`)** — вернуть `needsCard()` в `replayLearned` → RED `a learned search replays
  without the card`; снять проверку выбора → RED `…owing a pick still shows the card`.
- [ ] **Шаг 4: коммит** `fix(a4-tail/fix): выученная команда повторяется без карточки, если не нужен выбор (D3, третья поправка)`.

---

## Закрытие

### Задача 12: граничный гейт, ревью, ADR, повторная приёмка

- [ ] `gradlew --stop`; разбивка по модулям **до** прогона (пол 2078 − 15 задачи 3 + добавленные — посчитать по
  задачам); `tools/gate.sh` → `GATE GREEN`; `:core:ui:verifyRoborazziDebug`; `:app:assembleRelease` (подпись задачи 1
  снимает красный `checkOwnerReviewedLocaleStrings`). Новый пол — в `tools/gate.sh` с разбивкой в ADR.
- [ ] Ревью ветки раунда — `reviewer-deep` (согласие, правило карточки, доктрина).
- [ ] ADR «2026-11-28 — Раунд исправлений хвоста A4′ после приёмки»: §0.1 (находки и причины), §0.2 (решения),
  **D3, третья поправка** (задачи 10, 11), **решение 2026-11-17, поправка** (задача 9), чип — sha для A6, Ф8 (задержка) —
  остаток; строка в `ai-context/decisions-orientation.md`; `CLAUDE.md` (статус, Known debt) и
  `ai-context/current-status.md` — голова и три последних раздела.
- [ ] Чеклист повторной приёмки — дописать в конец [чеклиста 2026-11-28](2026-11-28-a4-tail-acceptance.md) разделом
  «Повторная приёмка» только по изменённым пунктам: «открой плюс», «открой cloude», «карты», «plus», «открой клод и
  поставь таймер на 5 минут», «удали приложение клод» (одна карточка, **Отмена** в системном диалоге), «поищи погоду в
  казани» после трёх подтверждений, открытое командой — в избранном, долгое нажатие на иконку, кнопки и вкладки, чипа нет;
  новые строки `tr` (если появились) — на суд. Сборка — `adb install -r`.
- [ ] Коммит и `git push origin launcher--7`.
