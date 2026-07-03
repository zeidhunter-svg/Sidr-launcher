# Хендофф: Phase UX · Block X6 — Polish, a11y, first-run, deferred settings + device acceptance

> Передаточный бриф для агента в новой сессии. Block X5 (Real Settings surface `:feature:settings`)
> закрыт 2026-07-03; ты делаешь **Block X6 — финальный блок Phase UX**. Читай самодостаточно, но
> **сначала подними контекст через codegraph** (§0) — репозиторий проиндексирован (`.codegraph/` в корне),
> это дешевле и точнее, чем grep/Read.

## 0. Как поднять контекст (первым делом, в этом порядке)
1. `CLAUDE.md` — hard rules + сессионный дайджест (модульные границы, «Contract → Owner module»).
2. `ai-context/phase-ux-plan.md` — активный план фазы; X1 + X2 + X3 + X4 + **X5 = ✅ DONE**, ты делаешь
   **X6** (§5, «Block X6 — Polish, a11y hygiene, first-run, device acceptance»).
3. `ai-context/current-status.md` — раздел «Phase UX» (X5 done, X6 next) + «Open questions».
4. `ai-context/decisions.md` — последние ADR «2026-07-03 — Phase UX Block X4 / X5 complete».
5. **Через `codegraph_explore` подними (одним-двумя вызовами, не Read-циклом):**
   `LauncherViewModel deriveFavorites FAVORITES_COUNT isVoiceInputAvailable startVoiceInput LauncherScreen
   LauncherUiState AppDrawerScreen SettingsViewModel SettingsScreen SettingsUiState UserPreferences
   UserPreferencesRepository FeatureFlags FeatureFlagRepository PreferencesKeys PreferencesMapper
   PrivacyInventoryGuardTest AssistantViewModel AssistantScreen AppNavHost Routes EmptyState ErrorState
   SidrScaffold SidrSearchField AppTile TopBarIcon SectionHeader`. Codegraph отдаёт verbatim-исходники +
   blast-radius — используй его ПЕРЕД правкой. **ВАЖНО:** у `PreferencesKeys`/`FeatureFlags` десятки
   коллеров, а `PrivacyInventoryGuardTest` сверяет `ALL_KEY_NAMES` + **denylist** — не сломай guard.

## 1. Что уже есть (X1–X5) — переиспользуй, не переписывай
- **X1 `core/ui` design-system:** `SidrTheme(darkTheme, dynamicColor, content)` (X5 уже прокидывает
  user-theme из `LauncherActivity`, см. ниже). Компоненты: `SidrScaffold`, `SidrSearchField` (со встроенным
  Clear + `onSubmit` + `showMic`), `AppTile`, `SectionHeader`, `TopBarIcon` (48dp target / 24dp glyph,
  `contentDescription` обязателен), **`EmptyState`, `ErrorState`** — используй их для loading/empty/error
  вместо самопальных `Text`.
- **X2 home (decluttered):** `LauncherScreen` больше НЕ рендерит грид-стену; показывает Favorites (top-N)
  + Suggestions + командное поле + топ-бар иконки **Settings** (`Routes.Settings.ROUTE`) и **Assistant**
  (`Routes.Assistant.ROUTE`). Favorites считаются в `LauncherViewModel.deriveFavorites(...)` и **захардкожены
  `FAVORITES_COUNT = 8`** (`LauncherViewModel.kt:502`) — это твой X6-объём (см. §3 X6-A).
- **X3 App Drawer:** `Routes.AppDrawer.ROUTE`, весь список приложений по требованию, алфавитные секции.
- **X4 search ⇄ command:** в drawer'е `SidrSearchField` фильтрует приложения live (pure `filterApps`),
  Enter запускает верхнее совпадение; home остаётся command-first. **«Ask assistant» affordance был отложен
  в X6** (см. §3 X6-C) — prefill-навигация к ассистенту без потери набранного вопроса.
- **X5 Settings (`:feature:settings`):** реальный экран `SettingsScreen`/`SettingsViewModel`/`SettingsUiState`
  (Compose + Hilt kapt). Сейчас: **theme** (system/light/dark, радио), **AI suggestions** (тумблер +
  ре-синк WorkManager через доменный порт `SuggestionScheduling`), **Assistant provider** (nav на форму),
  **Set as default launcher** (интент из экрана через `LocalContext`: `RoleManager.ROLE_HOME` на API 29+,
  иначе `Settings.ACTION_HOME_SETTINGS`). VM emit `NavigationEvent` (Channel), собран на `SidrScaffold` +
  back `TopBarIcon` + `SectionHeader`. **`LauncherActivity` наблюдает `UserPreferencesRepository` → мапит
  `themeName` → `SidrTheme(darkTheme=…)`** (X5-D; `dynamicColor` остаётся вкл).
- **Voice (Block T):** `LauncherViewModel.isVoiceInputAvailable` (`= speechInputSource.isAvailable()`,
  `:146`) — микрофон в UI показывается ТОЛЬКО когда true; `startVoiceInput(...)` (`:224`) гейтится
  доступностью распознавателя, НЕ user-флагом. Прав `RECORD_AUDIO` — routed-education (Block T).
- **Persistence (порты `:domain`, impl `:data:repository` над DataStore `sidr_preferences`):**
  - `UserPreferences(themeName="system", commandInputEnabled=true)` + `UserPreferencesRepository`
    (`getPreferences`/`updatePreferences`). **Сюда добавляются X6-поля** (favoritesCount / voice toggle).
  - `FeatureFlags(aiSuggestionsEnabled, usageHistoryEnabled, permissionEducationDismissed)` +
    `FeatureFlagRepository`.
  - `PreferencesKeys` + `PreferencesMapper` (`:data:repository`); `FakeUserPreferencesRepository` /
    `FakeFeatureFlagRepository` в `:core:testing`.

## 2. Уже решённые форки (НЕ переоткрывать)
- **Топология:** единый `NavHost` в `:app`; VM emit `NavigationEvent`, `NavHostController` не трогать; нет
  `feature→feature` и **нет `feature→:app`** (WorkManager-ре-синк уже через доменный порт
  `SuggestionScheduling`, X5-A). `core/ui` зависит только от `core/common`.
- **Ключ ассистента никогда не в saved state / не логируется** (`AssistantViewModel` намеренно без
  `SavedStateHandle`). Prefill к ассистенту (X6-C) — опциональный nav-arg, потребляемый ОДИН раз,
  **никогда** не в saved state.
- **Settings-модуль = `:feature:settings`** (создан в X5). Расширяй его, не пересоздавай.

## 3. Что решить с owner'ом ПЕРЕД кодом (форки X6-* — вынеси в план, дождись выбора)

- **Форк X6-A (главный): куда кладём `favoritesCount` и `voiceInputEnabled` + как назвать ключи.**
  Оба требуют НОВЫХ pref-полей + правку `PreferencesKeys` + `PreferencesMapper` + **`ALL_KEY_NAMES`** +
  проводку потребителя. Развилки:
  - `favoritesCount` → **`UserPreferences.favoritesCount: Int`** (дефолт 8), читается в
    `LauncherViewModel.deriveFavorites` вместо `const FAVORITES_COUNT`. Ключ `user_favorites_count`
    (denylist-clean).
  - voice on/off → **`UserPreferences` (напр. `commandMicEnabled`)** или **`FeatureFlags`**? Рекомендую
    `UserPreferences` (это UI-предпочтение, не capability-флаг). **⚠️ КРИТИЧНО: слово `voice` — forbidden
    term в `PrivacyInventoryGuardTest` denylist** (как `calendar`/`location`; см. коммент в `PreferencesKeys`
    у `PERM_DISMISSED_*`). Ключ, буквально названный `*_voice_*`, **завалит guard**. Нужно denylist-clean
    имя, напр. `user_mic_command_enabled` / `user_speech_input_enabled`. Гейт микрофона в
    `LauncherViewModel` станет `isVoiceInputAvailable && prefs.<flag>` (и в `startVoiceInput` — ранний
    выход, если выключено).
  - **Реши:** имена ключей + куда поле (моя реком.: оба в `UserPreferences`; `user_favorites_count` +
    `user_mic_command_enabled`).
- **Форк X6-B: объём X6 (что шипаем сейчас vs дробим).** План перечисляет: (1) deferred settings
  (favorites-count + voice toggle), (2) первый-запуск nudge (set-as-default), (3) a11y-гигиена
  (contentDescription, ≥48dp targets, focus order), (4) loading/empty/error через `core/ui`, (5) лёгкая
  motion (drawer open / list reveal), (6) device-acceptance + **cold-start re-measure**. Реши: везём всё
  одним блоком или MVP-срез (1–4) сейчас, motion (5) — опционально. Моя реком.: **1→4 + first-run,
  motion лёгкая по возможности**, cold-start — только re-measure на девайсе (fix — отдельный owed item, §5).
- **Форк X6-C: «Ask assistant» prefill (перенесён из X4).** Опциональный nav-arg маршрута ассистента
  (напр. `Routes.Assistant.routeFor(prompt)` c `NavType.StringType`, nullable, defaultValue null),
  потребляемый ОДИН раз при входе, **никогда не в `SavedStateHandle`** (ключ-инвариант + prompt транзиентен).
  Источник — «Ask assistant» в drawer'е и/или неизвестная команда на home. Реши: делаем в X6 или ещё
  откладываем. Моя реком.: **делаем минимально** (nav-arg + one-shot consume в `AssistantViewModel.init`),
  без сохранения; если сложно — оставляем как есть и явно фиксируем перенос.
- **Форк X6-D: first-run nudge — детект «я не дефолтный лончер» + где хранить «показано».** Нужен
  one-shot флаг (напр. `FeatureFlags`/`UserPreferences`, denylist-clean ключ) + проверка роли
  (`RoleManager.isRoleHeld(ROLE_HOME)` API 29+, иначе эвристика `resolveActivity` HOME-интента). Реши: где
  живёт детект (UI-действие в `:app`/screen, как X5-C set-default) и где флаг «nudge dismissed».
- **Форк X6-E: cold-start — fix или только re-measure в X6?** Round-3/плана perf-budget `<400ms` — открыт
  (root-cause: PackageManager enum + Hilt + Room + DataStore + `ensureModel()`). X2 уже убрал грид с первого
  кадра. Реши: X6 = **только измерить на SM-A325F** (ожидаемое улучшение), а профилированный fix — отдельная
  задача вне Phase UX; ИЛИ включаем базовый fix в X6. Моя реком.: **только re-measure**, fix отдельно.

## 4. Задача X6 (при рекомендованных выборах)
- **`:feature:settings`:** добавь в `SettingsScreen`/`SettingsViewModel`/`SettingsUiState`:
  - **Favorites count** — селектор (напр. 4/6/8/10) → `UserPreferencesRepository.updatePreferences(favoritesCount=…)`.
  - **Voice input** — тумблер → `UserPreferences.<mic flag>`; персист переживает рестарт.
  - Собери на существующих `SectionHeader`/M3-компонентах, единый вид с X5.
- **`:domain`/persistence:** добавь поля в `UserPreferences`; ключи в `PreferencesKeys` (**denylist-clean
  имена!**) + `PreferencesMapper` (read с дефолтом + write); **обнови `ALL_KEY_NAMES`** и убедись, что
  `PrivacyInventoryGuardTest` зелёный.
- **`feature/launcher`:** `deriveFavorites` читает `prefs.favoritesCount` (убрать `const FAVORITES_COUNT`
  или сделать дефолтом); мик-гейт (`isVoiceInputAvailable && prefs.<mic flag>`) в показе микрофона +
  ранний выход в `startVoiceInput`. `LauncherUiState`/поток — прокинь новые prefs через существующий
  `combine`.
- **a11y-гигиена:** пройди home/drawer/settings/assistant — `contentDescription` на всех иконках/тапах,
  ≥48dp targets, `heading()`/focus order; loading/empty/error через `core/ui` `EmptyState`/`ErrorState`.
- **first-run nudge (X6-D):** one-shot «set as default» подсказка + одна строка «type or search» hint.
- **«Ask assistant» prefill (X6-C):** nav-arg + one-shot consume (если owner подтвердил).
- **`:app`:** только composition-root проводка (nav-arg маршрута ассистента, если X6-C).

## 5. Границы (hard rules — строго)
- `domain` чист (stdlib + coroutines). Интерфейсы в `domain`, impl в `data/*`/`app`. Репозитории/use-case
  → `OperationResult`, не бросать в UI. `core/ui` ← только `core/common`.
- Нет `feature→feature`, **нет `feature→:app`**. Единый `NavHost` в `:app`; VM emit `NavigationEvent`.
- Launcher core офлайн; опциональные пермишены не блокируют старт.
- **Ключ ассистента никогда не в saved state / не логируется.** Prefill — one-shot, не персистить.
- **Не менять** `HandleUserCommandUseCase`/`IntentMatcher`/`CommandNormalizer`/`RuleBasedIntentMatcher` и
  suggestion/WorkManager-контракт (gate-before-enqueue). Не трогать OQ#1/2/3; `:data:ai-local` inert.
  Round-3 finding'и (keySet-гонка, `retray`→`retry`) — вне Phase UX.
- **`voice`/`calendar`/`location` — forbidden terms в `PrivacyInventoryGuardTest`**: любой новый pref-ключ
  должен быть denylist-clean (см. X6-A).
- **Cold-start perf-fix** (`<400ms`) — НЕ в X6 (только re-measure, если owner так решил); не переписывай
  Hilt/Room/DataStore ради этого здесь.

## 6. Acceptance
- Favorites-count и Voice-toggle **персистят через рестарт**; favorites-row реально меняет число иконок;
  voice-тумблер OFF прячет микрофон и `startVoiceInput` — no-op.
- **`PrivacyInventoryGuardTest` зелёный** (новые ключи в инвентаре, denylist-clean).
- a11y: иконки/тапы озвучены (TalkBack), targets ≥48dp; loading/empty/error через `core/ui`.
- first-run nudge показывается один раз и ведёт на системный выбор лончера; повторно не всплывает.
- (если X6-C) «Ask assistant» доносит набранный текст на форму ассистента **без** попадания в saved state.
- Новые/расширенные JVM-тесты: settings-VM (favorites-count + voice-toggle пишут pref, ошибка → error),
  `deriveFavorites` уважает `favoritesCount`, мик-гейт учитывает флаг; существующие suites зелёные.
- `./gradlew assembleDebug testDebugUnitTest` — зелёные.
- В конце: ADR в `decisions.md` («2026-07-03/04 — Phase UX Block X6 complete»), синк `current-status.md` +
  пометка X6 ✅ в `phase-ux-plan.md`. **Phase UX закрывается этим блоком.**

## 7. Device pass (SM-A325F, `RF8R705H38F`) — копится, прогоним ПОСЛЕ всей работы (owner так решил)
- **Накопленный device-долг Phase UX (X2/X3/X4/X5 + X6):** home без грид-стены; топ-бар иконки →
  Settings/Assistant; Favorites видны (и число = настройке); «All apps» открывает алфавитный drawer;
  в drawer печать фильтрует live, Clear сбрасывает, Enter запускает верхнее совпадение; на home
  типизированная команда (`open telegram`, `settings`) работает; **тап по иконке Settings → настоящий
  экран; смена темы применяется; тумблеры (AI suggestions / voice / favorites-count) переживают рестарт;
  «Set as default» открывает системный выбор; «Assistant provider» ведёт на форму**; first-run nudge
  показан один раз; **cold-start re-measure** (`am start -S -W` ×N, ожидаемое улучшение после X2).
- **Прочий накопленный device-долг (Phase 5/6/7, не Phase UX):** Block-N assistant real streaming/offline/
  retry/cancel (**PENDING-CONFIG**), Phase-6 real NLU (**PENDING-MODEL**, OQ#1/2), voice recognizer
  `Ready`/`Partial` (OQ#4), Block-U calendar/location reads, Block-V embedder (OQ#3). Их НЕ разблокировать в X6.

## 8. Процесс
Один вертикальный срез за раз. **Сначала предложи owner'у конкретный план X6 — явно реши форки X6-A
(имена/место pref-полей), X6-B (объём), X6-C (assistant prefill), X6-D (first-run детект+флаг), X6-E
(cold-start fix vs re-measure) — дождись подтверждения → потом код.** JVM + `assembleDebug` зелёные после
блока; ADR + синк статусов в конце. Device-прогон — позже, всё разом. **X6 — последний блок Phase UX.**

---
### Опорные факты из кода (проверены через codegraph 2026-07-03)
- `LauncherViewModel.FAVORITES_COUNT = 8` (`LauncherViewModel.kt:502`), используется только в
  `deriveFavorites(usageRecords, installed)` (`:399`) — `usageRecords.mapNotNull{…}.take(FAVORITES_COUNT)`.
- `isVoiceInputAvailable` (`:146`) = `speechInputSource.isAvailable()`; `startVoiceInput(languageTag)`
  (`:224`) гейтится только доступностью распознавателя — user-флага сейчас нет.
- `UserPreferences(themeName="system", commandInputEnabled=true)`; `PreferencesMapper.toUserPreferences`/
  `writeUserPreferences` мапят через `PreferencesKeys.USER_*`; `ALL_KEY_NAMES` — единственный инвентарь,
  который сверяет `PrivacyInventoryGuardTest` (+denylist с forbidden `voice/calendar/location/history/…`).
- `SettingsViewModel(FeatureFlagRepository, UserPreferencesRepository, SuggestionScheduling, @IoDispatcher)`;
  `uiState = combine(getFlags(), getPreferences(), saveError)`; методы `setAiSuggestionsEnabled`,
  `setThemeName`, `openAssistantProvider`, `navigateBack`. `SettingsUiState(aiSuggestionsEnabled, themeName,
  errorMessage)` + `ThemeOption`.
- `AssistantViewModel` — БЕЗ `SavedStateHandle` (намеренно); `Routes.Assistant.ROUTE = "assistant"`;
  `Routes.PermissionEducation` уже показывает паттерн опционального nav-arg (`routeFor`, `ROUTE_WITH_ARG`).
- Модуль-образец: `feature/settings` (создан в X5) + `feature/permission_education`. `core/ui` компоненты:
  `SidrScaffold`, `SidrSearchField`, `AppTile`, `SectionHeader`, `TopBarIcon`, `EmptyState`, `ErrorState`.
