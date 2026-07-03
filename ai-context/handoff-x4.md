# Хендофф: Phase UX · Block X4 — Search ⇄ Command (live-фильтрация приложений)

> Передаточный бриф для агента в новой сессии. Block X3 (App Drawer) закрыт 2026-07-03; ты делаешь
> **Block X4**. Читай самодостаточно, но подними исходники (§0) перед кодом.

## 0. Как поднять контекст (первым делом, в этом порядке)
1. `CLAUDE.md` — hard rules + сессионный дайджест.
2. `ai-context/phase-ux-plan.md` — активный план фазы; X1 + X2 + **X3 = ✅ DONE**, ты делаешь **X4** (§5, «Block X4 — Search ⇄ command unification»).
3. `ai-context/current-status.md` — раздел «Phase UX» (X3 done, X4 next).
4. `ai-context/decisions.md` — последние ADR «2026-07-03 — Phase UX Block X2/X3 complete» (что уже на home и в drawer).
5. Через `codegraph_explore` подними: `LauncherScreen`, `LauncherViewModel`, `AppDrawerScreen`,
   `AppDrawerViewModel`, `AppDrawerUiState`/`groupIntoSections`, `SidrSearchField`, `HandleUserCommandUseCase`,
   `CommandNormalizer`, `RuleBasedIntentMatcher`, `InstalledAppsRepository`, `Routes`.

## 1. Что уже готово (X1–X3) — переиспользуй, не переписывай
- **X1 `core/ui`:** `SidrSearchField(value, onValueChange, onSubmit, showMic, onMic, placeholder)` — **уже
  спроектирован под U6**: выглядит как поиск, `onSubmit` дергает командный пайплайн, `onValueChange` —
  решение вызывающего (фильтр vs. диспатч). **Есть встроенный Clear-афорданс** (крестик при непустом value,
  иначе мик) — идеален для фильтрации. Компонент чист — presentation-only.
- **X2 home:** `LauncherScreen` на `SidrScaffold`; топ-бар Settings/Assistant; `SidrSearchField` наверху
  (`value=commandInput`, `onValueChange=onCommandChanged`, `onSubmit=onCommandSubmitted`, `showMic`,
  `onMic`); ряд Favorites; кнопка All apps → `Routes.AppDrawer.ROUTE`. Грида на home нет. `commandInput`
  бэкается `SavedStateHandle` (H3).
- **X3 App Drawer:** `AppDrawerScreen` (в `feature/launcher`) — `SidrScaffold` + Back `TopBarIcon` +
  `LazyColumn` со `stickyHeader` `SectionHeader`'ами + компактные строки «иконка + label».
  `AppDrawerViewModel` (`@HiltViewModel`, **без** `HandleUserCommandUseCase`/`IntentMatcher`) отдаёт
  `UiState<AppDrawerUiState>` из **чистой** `groupIntoSections(apps)` (сорт по label case-insensitive,
  буквенные секции + хвостовой `#`). launch/usage — дословная копия `LauncherViewModel`. **В drawer
  ПОКА НЕТ поля поиска** — это часть твоей работы (см. §3).
- Иконки: `internal rememberAppIcon(packageName)` + `Drawable.toImageBitmap()` в `AppIcon.kt` (шарятся
  home + drawer). Переиспользуй.

## 2. Форки, уже решённые (НЕ переоткрывать)
- **U6 (§4 плана):** одно поле. Печать **фильтрует список приложений live**, Enter по-прежнему **сабмитит в
  командный пайплайн байт-в-байт**. Обе роли одного поля — без регрессии команд.
- **Топология:** всё в `feature/launcher`, никакого `feature→feature`, единый `NavHost` в `:app`, VM emit
  `NavigationEvent` (не трогать `NavHostController`).
- **`HandleUserCommandUseCase`/`IntentMatcher`/`CommandNormalizer`/`RuleBasedIntentMatcher` — НЕ менять.**
  Фильтр — чисто UI/VM-деривация над уже загруженным `InstalledApp`-списком; матчинг команд не трогаем.

## 3. Что решить с владельцем ПЕРЕД кодом (форк X4-A: где живёт фильтр)
Это load-bearing развилка — вынеси её в план и дождись выбора:
- **(a) Фильтр в App Drawer (рекоменд. для MVP).** Добавить `SidrSearchField` в топ `AppDrawerScreen`;
  печать префикс-фильтрует секции live (чистая функция над installed-списком), Clear сбрасывает. Enter в
  drawer — **опционально** сабмитит команду (тогда drawer-VM нужен `HandleUserCommandUseCase` — взвесь;
  проще: Enter в drawer = no-op/скрыть клаву, а командный сабмит остаётся на home). Самый чистый срез:
  drawer уже владеет полным списком + группировкой.
- **(b) Фильтр на home.** Печать в home-поле показывает inline список найденных приложений (заменяя
  Favorites/Suggestions пока строка непуста), Enter — команда как сейчас. Требует нового результат-списка
  на home и решения по layout.
- **(c) Оба** — дороже; для MVP избыточно.

**Моя рекомендация:** **(a)** — минимальный риск, drawer уже держит список и `groupIntoSections`; home-поле
остаётся command-first (X2-поведение без регрессии). Но выбор за владельцем — если он хочет «печатаю на
home → сразу вижу приложения», это (b) и другой объём.

## 4. Задача X4 (вертикальный срез, при выборе (a))
- **`AppDrawerViewModel`:** добавь `queryFlow: MutableStateFlow<String>` + `onQueryChanged(text)`;
  `uiState` строй как `combine(_rawAppsResult, queryFlow)` → фильтруй installed-список **чистой** функцией
  `filterApps(apps, query)` (префикс/`contains` по `label`, case-insensitive; пустой query → полный
  список), затем `groupIntoSections`. Держи `filterApps` чистой (юнит-тест). Пустой результат фильтра →
  `UiState.Empty` с сообщением «Ничего не найдено».
- **`AppDrawerScreen`:** `SidrSearchField` под топ-баром (или вместо заголовка), `value=query`,
  `onValueChange=vm::onQueryChanged`, `showMic=false` (голос — на home), Clear встроен. Список ниже
  реагирует live. `stickyHeader`-секции остаются.
- **Команды не регрессируют:** home `onCommandSubmitted` не трогаем; `HandleUserCommandUseCase` байт-в-байт.
- **(опц., дёшево)** «Ask assistant» афорданс: когда query похож на вопрос/длинную фразу — тонкая кнопка
  «Спросить ассистента» → `navigateTo(Routes.Assistant.ROUTE)`. **ВНИМАНИЕ:** `AssistantViewModel`
  **намеренно без `SavedStateHandle`** и prefill промпта сейчас НЕТ — предзаполнение потребует новый
  nav-arg/механизм. Для X4 честнее: либо роутить **без** prefill, либо отложить афорданс в X6. Реши и
  запиши в ADR; **не** ломай «ключ никогда не в saved state».

## 5. Мелочь для `core/ui`, разрешённая в X4
- `SidrSearchField` уже готов (Clear + placeholder). Новых Gradle-deps не добавлять. Если нужен глиф,
  которого нет в core-наборе Material — бандл-вектор в `core/ui/.../res/drawable/` (образец
  `ic_assistant_24.xml`) + `TopBarIcon(painter=…)`.

## 6. Границы (hard rules — строго)
- `core/ui` зависит только от `core/common`; никаких domain/data/feature edges.
- Нет `feature→feature`. Единый `NavHost` в `:app`; VM emit `NavigationEvent`, `NavHostController` не трогать.
- `domain` чист; репозитории/use-case → `OperationResult`, не бросать в UI. Фильтр офлайн (над уже
  загруженным списком). `HandleUserCommandUseCase`/`IntentMatcher`/`CommandNormalizer` **не менять**.
- Не трогать OQ#1/2/3; `:data:ai-local` inert. Round-3 finding'и (keySet-гонка, `retray`→`retry`) — за X5/X6.
- Реальные настройки (`:feature:settings`, U5/U7) — это X5, не X4.

## 7. Acceptance
- Печать в поле фильтрует список приложений live; Clear сбрасывает к полному списку; секции/скролл целы.
- Enter/сабмит команды работает **байт-в-байт** как сейчас (типизированные команды не сломаны).
- Пустой результат фильтра → корректный Empty; офлайн ок.
- (если сделан) «Ask assistant» роутит на ассистента без нарушения saved-state-инварианта.
- Новые JVM-тесты (`feature/launcher`): `filterApps` (чистая функция — префикс/contains, регистр, пустой
  query, пустой результат), VM-деривация фильтр→секции, отсутствие регрессии команд. Существующие suites зелёные.
- `./gradlew assembleDebug testDebugUnitTest` — зелёные.
- В конце: ADR в `decisions.md` («2026-07-03 — Phase UX Block X4 complete»), синк `current-status.md` +
  пометка X4 ✅ в `phase-ux-plan.md`.

## 8. Device pass (SM-A325F, `RF8R705H38F`) — копится, прогоним ПОСЛЕ всей работы (владелец так решил)
- В drawer: печать фильтрует приложения; Clear сбрасывает; тап по найденному запускает; Back → home.
- На home: типизированная команда (`open telegram`, `settings`) по-прежнему работает.
- **Накопленный device-долг Phase UX (X2/X3):** home без грид-стены, топ-бар иконки → Settings/Assistant,
  Favorites видны, «All apps» открывает полный алфавитный drawer, drawer-запуск поднимает app в Favorites.

## 9. Процесс
Один вертикальный срез за раз. **Сначала предложи владельцу конкретный план X4 — и явно реши форк X4-A
(§3: фильтр в drawer vs. на home) + судьбу «Ask assistant»-prefill — дождись подтверждения → потом код.**
JVM + `assembleDebug` зелёные после блока; ADR + синк статусов в конце.

---
### Опорные факты из кода (проверены на 2026-07-03)
- `SidrSearchField(value, onValueChange, onSubmit, modifier, placeholder="Search or type a command…",
  showMic=false, onMic={})` — Clear-афорданс встроен (крестик при непустом value, иначе мик). Presentation-only.
- Home: `LauncherViewModel.commandInput` (SavedStateHandle), `onCommandChanged`, `onCommandSubmitted` — не трогать.
- Drawer: `AppDrawerViewModel._rawAppsResult` (raw `OperationResult<List<InstalledApp>>?`) → `uiState` через
  `map { … groupIntoSections(…) }`; **сейчас без query**. `groupIntoSections(apps): List<DrawerSection>` —
  чистая, в `AppDrawerUiState.kt`. Добавляй `filterApps` рядом (тоже чистой).
- `InstalledApp(packageName, label, activityName?)`; `InstalledAppsRepository.getInstalledApps():
  OperationResult<List<InstalledApp>>`.
- `AssistantViewModel` — **намеренно без `SavedStateHandle`** (ключ провайдера не должен попасть в saved
  state); prefill промпта не реализован → любой «Ask assistant» с предзаполнением требует нового механизма.
- `HandleUserCommandUseCase`, `RuleBasedIntentMatcher`, `CommandNormalizer` в `:domain`/`:data:repository` —
  командный матчинг; X4 их НЕ касается.
- `feature/launcher/build.gradle.kts`: Hilt (kapt) + Compose + `core:ui` + `domain` + `core:testing` — добавлять зависимости не нужно.
