# Хендофф: Phase UX · Block X2 — Home redesign (declutter)

> Передаточный бриф для агента в новой сессии. Block X1 (design-system foundation) закрыт
> 2026-07-03; ты делаешь **Block X2**. Читай самодостаточно, но подними исходники (§0) перед кодом.

## 0. Как поднять контекст (первым делом, в этом порядке)
1. `CLAUDE.md` — hard rules + сессионный дайджест.
2. `ai-context/phase-ux-plan.md` — активный план фазы; **Block X1 = ✅ DONE**, ты делаешь **X2** (§5 плана).
3. `ai-context/current-status.md` — раздел «Phase UX» (X1 done, X2 next).
4. `ai-context/decisions.md` — последний ADR «2026-07-03 — Phase UX Block X1 complete» (что уже есть в `core/ui`).
5. Через `codegraph_explore` подними: `LauncherScreen`, `LauncherViewModel`, `LauncherUiState`,
   `AppNavHost`, и компоненты `core/ui`: `SidrScaffold`/`SidrSearchField`/`AppTile`/`TopBarIcon`/`SectionHeader`.

## 1. Что уже готово (X1) — переиспользуй, не переписывай
`core/ui` наполнен и применён app-wide (`LauncherActivity` уже под `SidrTheme`):
- Тема: `SidrTheme(darkTheme, dynamicColor=true)`, палитра, `SidrTypography`, `SidrShapes`.
- Токены: `object Spacing` (xs..xxl), `object Sizes` (`minTouchTarget`/`appIcon`/`appTile`/`icon`/`stateGlyph`).
- Компоненты (`core/ui/.../component/`): `SidrScaffold`, `SidrSearchField` (единый поиск/команда + мик,
  trailing precedence Clear-когда-набрано → Mic-когда-доступно), `AppTile` (**иконка слотом**
  `icon: @Composable () -> Unit` — так `core/ui` не получает domain/data edge), `SectionHeader`
  (a11y `heading()`), `TopBarIcon(icon: ImageVector, contentDescription, onClick)` (48dp target),
  `EmptyState`, `ErrorState(onRetry?)`.
- Иконки = core-набор `androidx.compose.material.icons` (транзитивно через material3) + бандл-вектор
  `res/drawable/ic_mic_24.xml`. **0 новых Gradle-зависимостей** — держи это правило.

## 2. Форки, уже решённые (НЕ переоткрывать)
- **U1 = минимальный home + App Drawer** — грид всех приложений УХОДИТ с home (в X2 просто убираем;
  сам drawer — это X3).
- **U2** = кнопка «All apps» обязательна (свайп-жест опционален, можно в X3).
- **U3 = favorites = авто топ-N из `UsageHistoryRepository`** (без ручного пина; пин — позже).
- **U6** = поле поиск+команда одно, НО live-фильтрация приложений — это **X4**. В X2 поле пока только
  сабмитит команды byte-for-byte как сейчас.

## 3. Задача X2 (вертикальный срез) — перестроить home в `feature/launcher/LauncherScreen.kt`
- **Топ-бар** (через `SidrScaffold.topBar`): справа `TopBarIcon` Settings →
  `viewModel.navigateTo(Routes.Settings.ROUTE)` и Assistant → `navigateTo(Routes.Assistant.ROUTE)`.
  Слева — опциональные часы (можно отложить в X6; если делаешь — простой `Text`, без тикающего таймера).
- **`SidrSearchField`** вместо приватного `CommandInputBar`: `value=commandInput`,
  `onValueChange=onCommandChanged`, `onSubmit=onCommandSubmitted`, `showMic=isVoiceInputAvailable`,
  `onMic=onMicTap`. Логику `onMicTap` из текущего `LauncherScreen` сохрани дословно (она корректна:
  `checkSelfPermission(RECORD_AUDIO)` → `startVoiceInput()`, иначе route в permission-education).
- **Suggestions** — без изменений (`suggestionsContent` прокидывается из `AppNavHost`; single-owner
  `LauncherUiState.suggestions`).
- **Favorites** (новое): `SectionHeader("Favorites")` + ряд `AppTile` (top-N). Иконку в слот — через
  существующий `rememberAppIcon(packageName)` / `Drawable.toImageBitmap()` из `LauncherScreen`
  (оставь/перенеси helper в feature-модуль; в `core/ui` его тащить нельзя).
- **All apps** аффорданс (U2): кнопка → nav-событие к drawer. Drawer-destination появится в X3 →
  рекомендация: завести `Routes.AppDrawer.ROUTE` в `core/common` уже в X2, а `composable(...)` для него —
  в X3. До X3 `AppNavHost` safe-fallback вернёт на home (это ОК). Альтернатива — кнопка disabled с TODO(X3).
- **Убрать `AppGrid` с home** — ключевая цель блока. **НО `LauncherUiState.apps` НЕ удалять:**
  `onSuggestionClicked` резолвит `suggestion.actionId` против `uiState…apps` (LauncherViewModel:187-203).
  Приложения продолжают грузиться в state (нужны X3-drawer'у и резолву suggestion), просто не рендерятся гридом.

## 4. Изменения VM (`LauncherViewModel` / `LauncherUiState`)
- `LauncherUiState`: добавить `favorites: List<InstalledApp> = emptyList()`.
- `UsageHistoryRepository` **уже инжектится** в VM (используется в `recordUsage`). Favorites =
  `getUsageRecords()` (Flow, most-used first) ∩ загруженные `apps`, смаппить в `InstalledApp` по
  `packageName`, взять первые N (предлагаю `FAVORITES_COUNT = 8`). Пустая история (свежая установка) →
  пустой favorites (в X6 можно fallback на алфавит; сейчас не усложнять).
- `uiState` теперь combine источников `_rawAppsResult` + `usageRecords` (+ suggestions уже в потоке).
  Сохрани контракт `UiState.Loading/Empty/Error/Success` и 3-flow дизайн.
- `HandleUserCommandUseCase`, `IntentMatcher`, роутинг команд — **не трогать**. Типизированные команды
  `settings`/`assistant`/… работают byte-for-byte.

## 5. Мелочь для `core/ui`, разрешённая в X2 (Assistant-иконка)
В core-наборе нет ассистент-глифа. Добавь бандл-вектор `core/ui/.../res/drawable/ic_assistant_24.xml`
(sparkle/auto-awesome) **без** `?attr/colorControlNormal` (Compose-only тема — `Icon` тонирует сам;
образец — `ic_mic_24.xml`) и **перегрузку** `TopBarIcon(painter: Painter, …)` рядом с `ImageVector`-вариантом.
Settings-иконка = `Icons.Filled.Settings` (core-набор). Новых Gradle-deps не добавлять.

## 6. Границы (hard rules — строго)
- `core/ui` зависит только от `core/common`; **никаких** domain/data/feature edges (иконки — слот/painter).
- Нет `feature→feature`. Единый `NavHost` в `:app`; VM emit `NavigationEvent`, `NavHostController` не трогает.
- `domain` чист; репозитории/use-case → `OperationResult`, не бросать в UI. Launcher core работает офлайн.
- Не трогать OQ#1/2/3; `:data:ai-local` остаётся inert. Round-3 finding'и (keySet-гонка, `retray`→`retry`)
  — НЕ здесь (закреплены за X5/X6).

## 7. Acceptance
- Home без полного грида; Settings/Assistant достижимы по иконке; favorites = top-N used; typed-команды
  byte-for-byte; офлайн ок.
- Новые JVM-тесты в `LauncherViewModelTest`: favorites-деривация (top-N, порядок, пересечение с installed,
  пустая история → empty) + nav-события settings/assistant. Существующие suites зелёные.
- `./gradlew assembleDebug testDebugUnitTest` — зелёные.
- В конце: ADR в `decisions.md` («2026-07-03 — Phase UX Block X2 complete»), синк `current-status.md`
  + пометка X2 ✅ в `phase-ux-plan.md`.

## 8. Device pass (SM-A325F, `RF8R705H38F`) — agent-directed / human-hands, одна инструкция за раз
- Home открывается без стены иконок; обе топ-бар иконки открывают Settings/Assistant; favorites видны;
  `open telegram` (typed) работает; мик-аффорданс на месте.

## 9. Процесс
Один вертикальный срез за раз. **Сначала предложи владельцу конкретный план X2 (файлы/компоненты/VM-изменения)
→ дождись подтверждения → потом код.** JVM + `assembleDebug` зелёные после блока; ADR + синк статусов в конце.

---
### Опорные факты из кода (проверены через codegraph на 2026-07-03)
- `LauncherUiState` = `data class(apps, suggestions)` — добавляешь `favorites`.
- `LauncherViewModel`: `navigateTo(route)` эмитит `NavigationEvent.NavigateTo`; `onCommandSubmitted` →
  `applyOutcome(handleUserCommand.handle(text))`; `onSuggestionClicked` резолвит по `apps` → иначе route →
  иначе `launchApp`. `UsageHistoryRepository` уже в конструкторе.
- `UsageHistoryRepository.getUsageRecords(): Flow<List<AppUsageRecord>>` (most-used first);
  `AppUsageRecord(packageName, lastUsedEpochMs, launchCount)`.
- `InstalledAppsRepository.getInstalledApps(): OperationResult<List<InstalledApp>>`.
- `Routes`: `Launcher.ROUTE="launcher"`, `Assistant.ROUTE="assistant"`, `Settings.ROUTE="settings"`,
  `PermissionEducation` (arg-based). `AppDrawer` — заводишь ты (X2).
- `AppNavHost`: `handleNavigationEvent` уже даёт safe-fallback на неизвестный route → home.
