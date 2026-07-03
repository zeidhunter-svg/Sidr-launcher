# Хендофф: Phase UX · Block X3 — App Drawer (все приложения, по запросу)

> Передаточный бриф для агента в новой сессии. Block X2 (home declutter) закрыт 2026-07-03; ты
> делаешь **Block X3**. Читай самодостаточно, но подними исходники (§0) перед кодом.

## 0. Как поднять контекст (первым делом, в этом порядке)
1. `CLAUDE.md` — hard rules + сессионный дайджест.
2. `ai-context/phase-ux-plan.md` — активный план фазы; X1 + **X2 = ✅ DONE**, ты делаешь **X3** (§5, блок «Block X3 — App Drawer»).
3. `ai-context/current-status.md` — раздел «Phase UX» (X2 done, X3 next).
4. `ai-context/decisions.md` — последний ADR «2026-07-03 — Phase UX Block X2 complete» (что уже сделано на home + `Routes.AppDrawer`).
5. Через `codegraph_explore` подними: `LauncherScreen`, `LauncherViewModel`, `LauncherUiState`,
   `AppNavHost`, `Routes`, и компоненты `core/ui`: `AppTile`/`SectionHeader`/`SidrScaffold`/`TopBarIcon`/
   `SidrSearchField`/`EmptyState`/`ErrorState`; плюс `InstalledAppsRepository`, `ActionExecutor`,
   `UsageHistoryRepository`.

## 1. Что уже готово (X1 + X2) — переиспользуй, не переписывай
- `core/ui` наполнен (X1) и применён app-wide. Компоненты: `SidrScaffold`, `SidrSearchField`,
  `AppTile` (**иконка слотом** `icon: @Composable () -> Unit` — так `core/ui` не получает domain/data edge),
  `SectionHeader` (a11y `heading()` — идеален для буквенных секций A–Z), `TopBarIcon` (`ImageVector` **и**
  `Painter` перегрузки, 48dp target), `EmptyState`, `ErrorState(onRetry?)`. Токены `Spacing`/`Sizes`.
- **X2 уже сделал:** home без грида; топ-бар Settings/Assistant; `SidrSearchField` вместо `CommandInputBar`;
  ряд **Favorites** (top-N); кнопка **All apps** уже вызывает `viewModel.navigateTo(Routes.AppDrawer.ROUTE)`.
- **`Routes.AppDrawer.ROUTE = "app_drawer"` уже заведён** в `core/common` (X2). Пока в `AppNavHost` нет
  `composable(app_drawer)`, тап по «All apps» падает в safe-fallback → home. **Твоя работа X3 — зарегистрировать destination.**
- Иконки приложений грузятся хелперами `rememberAppIcon(packageName)` + `Drawable.toImageBitmap()` —
  сейчас они **`private` внутри `LauncherScreen.kt`** (строки ~366–394). Для drawer их надо переиспользовать
  (см. §3).

## 2. Форки, уже решённые (НЕ переоткрывать)
- **U1** — минимальный home + App Drawer: полный список приложений живёт **здесь**, в drawer.
- **U2** — кнопка «All apps» обязательна (уже есть в X2). **Свайп-жест опционален** — можно отложить/сделать в конце X3.
- **U6** — единое поле поиск+команда, НО live-фильтрация приложений — это **X4** (не X3). В X3 drawer
  показывает полный список без фильтра.
- **Топология (fork U-topology, §6 плана):** App Drawer — это **экран внутри `feature/launcher`**, НЕ новый
  модуль (рекоменд.). `feature/launcher` уже имеет Hilt + Compose + `core:ui` + `domain` + `core:testing`.
  Никакого `feature→feature` edge; единственный `NavHost` в `:app`. *(Settings уходит в свой модуль в X5 —
  drawer не обязан.)*

## 3. Задача X3 (вертикальный срез) — новый экран `AppDrawerScreen` в `feature/launcher`
- **Регистрация в NavHost.** В `AppNavHost` добавь `composable(Routes.AppDrawer.ROUTE) { … }` (образец —
  destination `Routes.Settings.ROUTE`: `hiltViewModel()` + `onBack = { handleNavigationEvent(navController,
  NavigationEvent.NavigateBack) }`). Back с drawer → возврат на home (safe-fallback уже есть).
- **`AppDrawerScreen`** (новый файл в `feature/launcher`): `SidrScaffold` с топ-баром (кнопка Back —
  `TopBarIcon(Icons.Filled.ArrowBack…)` или системный back; заголовок «All apps» опц.). Контент —
  `LazyColumn`:
  - **Алфавитный список ВСЕХ установленных приложений** (не usage-сортировка — это home). Сортировка по
    `label` (case-insensitive), группировка по первой букве; не-буквы → секция `#`.
  - **`SectionHeader(letter)`** как заголовок каждой буквенной группы (sticky по возможности —
    `stickyHeader`), строки — `AppTile` (иконка в слот через переиспользуемый `rememberAppIcon`) **или**
    компактная строка «иконка + label» (реши по вкусу; grid vs list — list удобнее для A–Z + fast-scroll).
  - **Fast-scroll**: боковой A–Z рельс (буквы → `LazyListState.scrollToItem` к индексу секции) ИЛИ
    минимально — обычный скролл в X3 и рельс отдельным штрихом. Цель acceptance — «плавный скролл»; рельс
    приветствуется, но не блокирует.
  - Пустой список → `EmptyState`; ошибка загрузки → `ErrorState(onRetry)`.
- **Тап по приложению → запуск + запись usage.** Переиспользуй существующий путь: `ActionExecutor.execute(
  ExecutableAction.LaunchAppAction(packageName, activityName))` + запись в `UsageHistoryRepository`
  (гейт `FeatureFlags.usageHistoryEnabled`, `CancellationException` re-throw, ошибки глушить) — **дословно
  как `LauncherViewModel.launchApp`/`recordUsage`**. Запуск из drawer обязан кормить Favorites/Suggestions.
- **Хелперы иконок.** Вынеси `rememberAppIcon` + `Drawable.toImageBitmap` из `LauncherScreen.kt` в
  **`internal` файл `feature/launcher`** (напр. `AppIcon.kt`), чтобы и home, и drawer их звали. В `core/ui`
  их тащить нельзя (PackageManager/Drawable — feature-концерн; `AppTile` держит иконку слотом ровно поэтому).

## 4. VM / состояние
- Заведи **`AppDrawerViewModel` (`@HiltViewModel`) в `feature/launcher`**, инжектит те же domain-порты, что и
  `LauncherViewModel`: `InstalledAppsRepository`, `ActionExecutor`, `UsageHistoryRepository`,
  `FeatureFlagRepository`, `@IoDispatcher`. `HandleUserCommandUseCase`/`IntentMatcher` **не нужны** (drawer не
  парсит команды). Эмити `NavigationEvent` через тот же `Channel`-паттерн (для Back).
- Состояние: `UiState<AppDrawerUiState>` (`Loading/Empty/Error(retryable)/Success`), где
  `AppDrawerUiState` несёт уже **сгруппированный/отсортированный** результат (напр. `sections: List<DrawerSection(letter, apps)>`
  или плоский `List<InstalledApp>` + чистая деривация секций в composable). Держи группировку **чистой**
  функцией (легко юнит-тестить).
- **Дублирование launch/recordUsage:** самый простой путь — скопировать логику в `AppDrawerViewModel`
  (7–10 строк). Если хочешь без копипасты — вынеси общий `internal` хелпер/юзкейс в `feature/launcher`
  (напр. `LaunchAndRecordUseCase`), но это опционально; **не** тяни в `:domain` без нужды. Реши и запиши в ADR.
- `HandleUserCommandUseCase`, `IntentMatcher`, роутинг команд, `LauncherViewModel` — **не трогать** (кроме
  выноса иконочных хелперов в §3).

## 5. Мелочь для `core/ui`, разрешённая в X3
- В core-наборе `material-icons` есть `Icons.Filled.ArrowBack`/`Icons.AutoMirrored.Filled.ArrowBack` —
  используй для Back (0 новых deps). Если понадобится глиф, которого нет в core-наборе → **бандл-вектор**
  в `core/ui/.../res/drawable/` (образец `ic_mic_24.xml`/`ic_assistant_24.xml`, без `?attr/colorControlNormal`),
  и при нужде — уже существующая `TopBarIcon(painter=…)` перегрузка. **Новых Gradle-deps не добавлять.**
- Если делаешь общий «список-строка» тайл (иконка+label слева-направо), можно добавить его в `core/ui`
  как новый компонент (иконка слотом!), либо оставить private в `feature/launcher`. По вкусу; граница §6 та же.

## 6. Границы (hard rules — строго)
- `core/ui` зависит только от `core/common`; **никаких** domain/data/feature edges (иконки — слот/painter).
- Нет `feature→feature`. Единый `NavHost` в `:app`; VM emit `NavigationEvent`, `NavHostController` не трогает.
- `domain` чист; репозитории/use-case → `OperationResult`, не бросать в UI. Launcher core работает офлайн
  (drawer — оффлайн полностью: PackageManager + локальный запуск).
- Не трогать OQ#1/2/3; `:data:ai-local` остаётся inert. Round-3 finding'и (keySet-гонка, `retray`→`retry`)
  — НЕ здесь (закреплены за X5/X6). Live-фильтрация поля — X4, не X3.

## 7. Acceptance
- Тап «All apps» на home открывает drawer (destination зарегистрирован, safe-fallback больше не срабатывает).
- В drawer достижимо **каждое** установленное приложение; список алфавитный с буквенными `SectionHeader`;
  скролл плавный (A–Z рельс — плюс).
- Тап по приложению в drawer **запускает** его и **пишет usage** (при `usageHistoryEnabled=true`), что затем
  влияет на Favorites/Suggestions на home. Back → home.
- Офлайн ок; типизированные команды на home по-прежнему byte-for-byte (drawer их не касается).
- Новые JVM-тесты (в `feature/launcher`): группировка/сортировка A–Z (чистая функция), деривация секций,
  Empty/Error, launch-через-executor + запись usage (+ гейт флага), Back-nav событие. Существующие suites зелёные.
- `./gradlew assembleDebug testDebugUnitTest` — зелёные.
- В конце: ADR в `decisions.md` («2026-07-03 — Phase UX Block X3 complete»), синк `current-status.md`
  + пометка X3 ✅ в `phase-ux-plan.md`.

## 8. Device pass (SM-A325F, `RF8R705H38F`) — agent-directed / human-hands, одна инструкция за раз
- «All apps» открывает drawer со всеми приложениями; список алфавитный, скролл плавный; тап запускает
  приложение; возврат Back на home; после запуска из drawer приложение поднимается в Favorites на home.

## 9. Процесс
Один вертикальный срез за раз. **Сначала предложи владельцу конкретный план X3 (файлы/компоненты/VM/навигация,
и явно — решение по дублированию launch/recordUsage и по fast-scroll) → дождись подтверждения → потом код.**
JVM + `assembleDebug` зелёные после блока; ADR + синк статусов в конце.

---
### Опорные факты из кода (проверены на 2026-07-03)
- `Routes.AppDrawer.ROUTE = "app_drawer"` — заведён в X2, `composable(...)` ещё НЕ зарегистрирован.
- `AppNavHost.handleNavigationEvent` даёт safe-fallback на неизвестный route → home; образец destination с
  Back — блок `composable(Routes.Settings.ROUTE)` (`onBack = handleNavigationEvent(…, NavigateBack)`).
- `LauncherScreen`: кнопка «All apps» → `viewModel.navigateTo(Routes.AppDrawer.ROUTE)` (уже есть).
- `rememberAppIcon(packageName)` + `Drawable.toImageBitmap()` — `private` в `LauncherScreen.kt` (~366–394),
  вынести в `internal` для переиспользования.
- `LauncherViewModel.launchApp(pkg, activity)` + `recordUsage(pkg)` — образец пути запуска+usage (гейт
  `usageHistoryEnabled`, `CancellationException` re-throw). `onAppClicked` дергает `launchApp`.
- `InstalledAppsRepository.getInstalledApps(): OperationResult<List<InstalledApp>>`;
  `InstalledApp(packageName, label, activityName?)`.
- `UsageHistoryRepository.recordLaunch(packageName, epochMs)`; `getUsageRecords(): Flow<List<AppUsageRecord>>`.
- `feature/launcher/build.gradle.kts`: уже есть Hilt (kapt) + Compose + `core:ui` + `domain` + `core:testing` —
  добавлять зависимости под drawer НЕ нужно.
- `core/ui`: `AppTile(label, onClick, icon={})`, `SectionHeader(text)`, `SidrScaffold(topBar, content)`,
  `TopBarIcon(icon|painter, contentDescription, onClick)`, `EmptyState`, `ErrorState(onRetry?)`, токены
  `Spacing`/`Sizes`.
