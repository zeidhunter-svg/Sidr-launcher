# Хендофф: Phase UX · Block X5 — Real Settings surface (`:feature:settings`)

> Передаточный бриф для агента в новой сессии. Block X4 (Search ⇄ Command) закрыт 2026-07-03; ты делаешь
> **Block X5**. Читай самодостаточно, но **сначала подними контекст через codegraph** (§0) — репозиторий
> проиндексирован (`.codegraph/` в корне), это дешевле и точнее, чем grep/Read.

## 0. Как поднять контекст (первым делом, в этом порядке)
1. `CLAUDE.md` — hard rules + сессионный дайджест (модульные границы, «Contract → Owner module»).
2. `ai-context/phase-ux-plan.md` — активный план фазы; X1 + X2 + X3 + **X4 = ✅ DONE**, ты делаешь **X5**
   (§5, «Block X5 — Real Settings surface (`:feature:settings`)»). Форки U5 (module) и U7 (default-launcher)
   уже РЕШЕНЫ owner'ом в пользу (b); см. §4 таблицу форков.
3. `ai-context/current-status.md` — раздел «Phase UX» (X4 done, X5 next) + «Open questions».
4. `ai-context/decisions.md` — последние ADR «2026-07-03 — Phase UX Block X1/X2/X3/X4 complete».
5. **Через `codegraph_explore` подними (одним-двумя вызовами, не Read-циклом):**
   `LauncherSettingsScreen LauncherSettingsViewModel LauncherSettingsUiState SuggestionsWorkScheduler
   FeatureFlags FeatureFlagRepository UserPreferences UserPreferencesRepository PreferencesMapper
   PreferencesKeys PrivacyInventoryGuardTest AppNavHost Routes SidrTheme LauncherActivity
   AssistantScreen deriveFavorites`. Codegraph отдаёт verbatim-исходники + blast-radius — используй его
   ПЕРЕД правкой (кто зовёт `FeatureFlags`/`PreferencesKeys` — там десятки коллеров, не сломай privacy-guard).

## 1. Что уже есть (X1–X4) — переиспользуй, не переписывай
- **X1 `core/ui`:** `SidrTheme(darkTheme, dynamicColor, content)` (нейтральный M3 + dynamic color на API 31+;
  `darkTheme` по умолчанию `isSystemInDarkTheme()`, но **KDoc уже предусматривает** передачу user-theme из
  «later block» — это твой блок). Компоненты `SidrScaffold`, `SidrSearchField`, `AppTile`, `SectionHeader`,
  `TopBarIcon`, `EmptyState`, `ErrorState`. `LauncherActivity` уже оборачивает контент в `SidrTheme`.
- **X2 home:** топ-бар `TopBarIcon` **Settings** (`Icons.Filled.Settings`) → `navigateTo(Routes.Settings.ROUTE)`
  и **Assistant** → `Routes.Assistant.ROUTE`. Иконка Settings уже дискаверабельна — тебе НЕ нужно её добавлять,
  только сделать так, чтобы за ней открывался настоящий экран настроек.
- **Текущий Settings (заглушка, в `:app`):** `com.sidr.launcher.settings.LauncherSettingsScreen` +
  `LauncherSettingsViewModel` + `LauncherSettingsUiState` + `LauncherSettingsViewModelTest`. **Один тумблер**
  `aiSuggestionsEnabled` (пишет `FeatureFlagRepository.updateFlags(...)`, затем дёргает
  `SuggestionsWorkScheduler.ensureScheduled()` — re-sync WorkManager при переключении) + строка ошибки.
  `AppNavHost` уже держит `composable(Routes.Settings.ROUTE)` с `hiltViewModel<LauncherSettingsViewModel>()`.
- **Persistence уже готова (порты в `:domain`, impl в `:data:repository` над DataStore `sidr_preferences`):**
  - `UserPreferences(themeName: String = "system" /* system|light|dark */, commandInputEnabled: Boolean = true)`
    + `UserPreferencesRepository { getPreferences(): Flow; updatePreferences(): OperationResult<Unit> }`.
    **`themeName` поле УЖЕ существует** — тему не надо добавлять в persistence, только применить + дать UI.
  - `FeatureFlags(aiSuggestionsEnabled, usageHistoryEnabled, permissionEducationDismissed)` +
    `FeatureFlagRepository { getFlags(): Flow; updateFlags(): OperationResult<Unit> }`.
  - `PreferencesKeys` (`:data:repository`) + `PreferencesMapper` — маппинг доменных моделей ↔ DataStore.
    **`PrivacyInventoryGuardTest` сверяет `ALL_KEY_NAMES` (+denylist) с реальными ключами** — любой НОВЫЙ
    pref-ключ надо добавить в инвентарь, иначе guard красный.
- **Assistant provider form** уже живёт в `feature/assistant` (`AssistantScreen` → `ProviderSettingsForm` →
  `saveProvider(baseUrl, model, apiKey)`; **ключ никогда не в saved state / не логируется**). Для «Assistant
  provider entry» из настроек — это просто `navigateTo(Routes.Assistant.ROUTE)`, НЕ дублируй форму.

## 2. Форки, уже решённые (НЕ переоткрывать)
- **U5 (§4 плана, owner 2026-07-02):** Settings → **отдельный модуль `:feature:settings`** (по образцу
  `:feature:permission_education`: Compose + Hilt kapt). `:app` остаётся тонким composition-root.
- **U7 (§4 плана, owner 2026-07-02):** launcher обязан уметь «Set as default» — нужен in-Settings хелпер
  (`RoleManager` / `ACTION_HOME_SETTINGS`). Первый-запуск-nudge — это **X6**, не X5.
- **Топология:** единый `NavHost` в `:app`; VM emit `NavigationEvent`, `NavHostController` не трогать; нет
  `feature→feature`. `Routes.Settings.ROUTE = "settings"` уже есть — переиспользуй.

## 3. Что решить с owner'ом ПЕРЕД кодом (форки X5-* — вынеси в план, дождись выбора)
Это load-bearing развилки, специфичные для X5:

- **Форк X5-A (главный): как feature-модуль ре-синкает WorkManager при переключении `aiSuggestionsEnabled`.**
  Сейчас VM зависит от `SuggestionsWorkScheduler`, который лежит в **`:app`** (`com.sidr.launcher.work`) и тянет
  `androidx.work`. Из `:feature:settings` на него сослаться нельзя (`feature→:app` запрещён). Варианты:
  - **(a) [рекоменд.]** объявить порт в `:domain` (напр. `SuggestionScheduling { suspend fun ensureScheduled() }`),
    реализовать его в `:app` поверх существующего `SuggestionsWorkScheduler`, забиндить в Hilt и инжектить в
    feature-VM. Тумблер остаётся синхронным (write flag → `ensureScheduled()`), как сейчас. Чисто, «impl в
    `:app`, контракт в `:domain`».
  - **(b)** feature только пишет флаг; ре-планирование переносится в наблюдателя флага в `SidrLauncherApp`
    (startup/DataStore-collect → `ensureScheduled()`). Развязывает, но добавляет app-side наблюдатель и делает
    ре-синк асинхронным относительно тапа.
  - Моя рекомендация: **(a)** — минимальная деривация от текущего поведения, WorkManager-контракт (gate-before-
    enqueue) не меняется.

- **Форк X5-B: объём настроек в X5 (что шипаем сейчас vs откладываем).** План перечисляет: **theme**,
  **AI suggestions**, **voice input on/off**, **favorites count**, **Assistant provider entry**,
  **Set-as-default helper**. Дешёвые (без новой persistence): theme (`themeName` уже есть), AI suggestions
  (флаг есть), Assistant entry (только nav), default-launcher helper (Android-интент). Дорогие (нужны НОВЫЕ
  pref-поля + правка `PreferencesKeys`/`ALL_KEY_NAMES`/`PreferencesMapper` + guard + проводка потребителя):
  - **favorites count** — сейчас `FAVORITES_COUNT` захардкожен в `deriveFavorites` (`feature/launcher`); нужен
    `UserPreferences.favoritesCount` (или flag) + чтение в `LauncherViewModel`.
  - **voice input on/off** — сегодня голос гейтится доступностью распознавателя (`isVoiceInputAvailable`), а не
    user-флагом; тумблер потребует нового поля + гейта показа микрофона.
  Реши: везём ли favorites-count и voice-toggle в X5, или **MVP-срез = theme + AI suggestions + Assistant entry
  + set-as-default**, а count/voice → X6. Моя рекомендация: **MVP-срез сейчас** (4 пункта), count/voice отдельным
  тонким довеском в X6 — так X5 не разрастается на persistence-миграции.

- **Форк X5-C: где живёт default-launcher helper (`RoleManager`/`ACTION_HOME_SETTINGS`).** Это Android-интент,
  не бизнес-логика. Варианты: **(a) [рекоменд.]** пускать интент прямо из экрана настроек (как
  `:feature:permission_education` пускает системные интенты — через `LocalContext`), без нового порта; **(b)**
  инжектируемый хелпер в `:core:android`. Рекомендация: **(a)** — UI-действие, не тянет новый контракт.

- **Форк X5-D: применение темы.** `UserPreferences.themeName` есть, но `SidrTheme` берёт `isSystemInDarkTheme()`
  по умолчанию, а `LauncherActivity` тему из pref не читает. Нужно: `LauncherActivity` (или composition-root)
  наблюдает `UserPreferencesRepository.getPreferences()` → маппит `themeName` → `darkTheme: Boolean` и передаёт
  в `SidrTheme`. Реши, трогаем ли `dynamicColor` тумблером (рекоменд.: **нет** в X5 — dynamic остаётся вкл,
  тема только system/light/dark). Держи это в `:app`/`core:ui`, не тяни в feature.

## 4. Задача X5 (вертикальный срез, при рекомендованных выборах)
- **Новый модуль `:feature:settings`** (Compose + Hilt kapt; build.gradle по образцу
  `feature/permission_education/build.gradle.kts` — зависимости: `core:ui`, `core:common`, `domain`,
  `core:testing`; **новых Gradle-deps не добавлять**). Зарегистрируй в `settings.gradle.kts`
  (`include(":feature:settings")`) и в `:app` build.gradle (`implementation(project(":feature:settings"))`).
  Пакет `com.sidr.launcher.feature.settings`.
- **Перенеси** `LauncherSettingsScreen`/`LauncherSettingsViewModel`/`LauncherSettingsUiState` (+тест) из
  `:app` (`com.sidr.launcher.settings`) в новый модуль, переименовав по вкусу (`SettingsScreen`/`SettingsViewModel`).
  Замени зависимость на `SuggestionsWorkScheduler` доменным портом (форк X5-A). `:app` теряет старые файлы;
  `AppNavHost` импортирует новый экран/VM в `composable(Routes.Settings.ROUTE)` (шаблон уже стоит — поменяй
  импорты + типы). VM emit `NavigationEvent`; `onBack` через общий `handleNavigationEvent`.
- **Настройки (MVP-срез, форк X5-B):**
  - **Theme** — сегмент/радио system|light|dark → `UserPreferencesRepository.updatePreferences(themeName=…)`;
    применяется через форк X5-D. Персист переживает рестарт.
  - **AI suggestions** — существующий тумблер `aiSuggestionsEnabled` + ре-синк WorkManager (форк X5-A).
  - **Assistant provider** — строка/кнопка → `navigateTo(Routes.Assistant.ROUTE)` (форму НЕ дублируй).
  - **Set as default launcher** — кнопка → системный экран (форк X5-C; `RoleManager.createRequestRoleIntent(
    ROLE_HOME)` на API 29+, иначе `Settings.ACTION_HOME_SETTINGS`). Только запуск интента.
  - Собери на `SidrScaffold` + `TopBarIcon` back + `SectionHeader`/`core-ui` компонентах (единый вид с drawer/home).
- **`domain`/persistence:** для MVP-среза НОВЫХ pref-полей не требуется (theme + флаг уже есть). Если owner
  включил favorites-count/voice (форк X5-B), тогда: добавь поле в `UserPreferences`/`FeatureFlags`, ключ в
  `PreferencesKeys` + `PreferencesMapper`, **обнови `ALL_KEY_NAMES` (privacy-guard)**, и проведи потребителя
  (`deriveFavorites` / mic-гейт).

## 5. Границы (hard rules — строго)
- `core/ui` зависит только от `core/common`; никаких domain/data/feature edges. `domain` чист (stdlib +
  coroutines). Интерфейсы в `domain`, impl в `data/*`/`app`. Репозитории/use-case → `OperationResult`, не бросать в UI.
- Нет `feature→feature` и **нет `feature→:app`** (отсюда форк X5-A — порт в `domain`, impl в `:app`).
- Единый `NavHost` в `:app`; VM emit `NavigationEvent`; `NavHostController` не трогать. Launcher core офлайн;
  опциональные пермишены не блокируют старт.
- **Ключ ассистента никогда не в saved state / не логируется** — из настроек только nav на существующую форму.
- **Не менять** `HandleUserCommandUseCase`/`IntentMatcher`/`CommandNormalizer`/`RuleBasedIntentMatcher` и
  suggestion/WorkManager-контракт (gate-before-enqueue). Не трогать OQ#1/2/3; `:data:ai-local` inert.
  Round-3 finding'и (keySet-гонка, `retray`→`retry`) — не в X5.
- **«Ask assistant» prefill** — отложен в X6 (решение X4). Не добавляй nav-arg prefill здесь.

## 6. Acceptance
- Settings открывается по иконке из home (не только по команде `settings`); экран живёт в `:feature:settings`.
- Каждый тумблер/выбор **персистит через рестарт** (theme, AI suggestions). Тема реально применяется
  (system/light/dark меняет схему).
- AI-suggestions тумблер по-прежнему ре-синкает WorkManager (gate ON/OFF сохранён) — через доменный порт, без
  `feature→:app`.
- «Assistant provider» роутит на существующую форму; ключ-инвариант не нарушен.
- «Set as default launcher» открывает корректный системный экран (RoleManager / HOME settings).
- **`PrivacyInventoryGuardTest` зелёный** (если добавлял ключи — инвентарь обновлён).
- Новые/перенесённые JVM-тесты: settings-VM (переключение флага + ре-синк через фейковый порт, ошибка →
  errorMessage, theme-выбор пишет pref); существующие suites зелёные.
- `./gradlew assembleDebug testDebugUnitTest` — зелёные.
- В конце: ADR в `decisions.md` («2026-07-03 — Phase UX Block X5 complete»), синк `current-status.md` +
  пометка X5 ✅ в `phase-ux-plan.md`.

## 7. Device pass (SM-A325F, `RF8R705H38F`) — копится, прогоним ПОСЛЕ всей работы (owner так решил)
- Тап по иконке Settings → настоящий экран настроек; смена темы применяется; тумблеры переживают рестарт;
  «Set as default» открывает системный выбор launcher'а; «Assistant provider» ведёт на форму.
- **Накопленный device-долг Phase UX (X2/X3/X4):** home без грид-стены; топ-бар иконки → Settings/Assistant;
  Favorites видны; «All apps» открывает алфавитный drawer; drawer-запуск поднимает app в Favorites; **в drawer
  печать фильтрует приложения live, Clear сбрасывает, Enter запускает верхнее совпадение**; на home
  типизированная команда (`open telegram`, `settings`) работает.

## 8. Процесс
Один вертикальный срез за раз. **Сначала предложи owner'у конкретный план X5 — явно реши форки X5-A
(WorkManager-порт), X5-B (объём: MVP-срез vs +count/voice), X5-C (default-launcher helper), X5-D (применение
темы) — дождись подтверждения → потом код.** JVM + `assembleDebug` зелёные после блока; ADR + синк статусов
в конце. Device-прогон — позже, всё разом.

---
### Опорные факты из кода (проверены через codegraph 2026-07-03)
- `LauncherSettingsViewModel(FeatureFlagRepository, SuggestionsWorkScheduler, @IoDispatcher)` — тумблер пишет
  `updateFlags(aiSuggestionsEnabled=…)` затем `suggestionsWorkScheduler.ensureScheduled()`; ошибка → `SAVE_ERROR`.
  `SuggestionsWorkScheduler` **в `:app`** (`com.sidr.launcher.work`, тянет `androidx.work`) → отсюда форк X5-A.
- `UserPreferences(themeName="system"|"light"|"dark", commandInputEnabled=true)`; `UserPreferencesRepository`
  (`getPreferences`/`updatePreferences`) + `FakeUserPreferencesRepository` в `:core:testing`. Тема-поле уже есть.
- `SidrTheme(darkTheme=isSystemInDarkTheme(), dynamicColor=true, content)` — KDoc прямо ждёт user-theme override.
  `LauncherActivity` оборачивает контент в `SidrTheme`, но pref не читает (форк X5-D).
- `AppNavHost.composable(Routes.Settings.ROUTE)` уже есть (`hiltViewModel<LauncherSettingsViewModel>()` +
  `onBack` через `handleNavigationEvent`). `Routes.Settings.ROUTE = "settings"`.
- `PreferencesKeys`/`PreferencesMapper` (`:data:repository`) + `PrivacyInventoryGuardTest`/`ALL_KEY_NAMES` —
  новый ключ ⇒ обнови инвентарь. `FeatureFlags`/`PreferencesKeys` имеют десятки коллеров (codegraph blast-radius).
- `AssistantScreen`/`saveProvider` (`feature/assistant`) — существующая provider-форма; из настроек только
  `navigateTo(Routes.Assistant.ROUTE)`.
- Модуль-образец: `feature/permission_education` (Compose + Hilt kapt). `settings.gradle.kts` includes —
  добавь `:feature:settings`.
