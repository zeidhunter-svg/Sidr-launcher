# Sidr Launcher

**AI-first лаунчер для Android (9+ / API 28+), где ИИ — не отдельный чат-бот, а центральный
интерфейс всей системы.** Пользователь вместо привычной сетки иконок взаимодействует с телефоном
через единое поле ввода — текстом, голосом или контекстными подсказками:

> «Открой Telegram» · «Найди рецепт плова» · «github.com» · «установи WhatsApp» ·
> «что у меня сегодня по расписанию?»

ИИ *понимает* естественную речь и *маршрутизирует* её в безопасное, зарегистрированное действие
(запуск приложения, веб-поиск, открытие сайта, Play Store, настройки, ассистент) — под ним всегда
работает быстрый офлайн-путь на правилах. Пользователь взаимодействует не с приложениями, а с
намерениями.

Автор: **SidrOS Projects** · Стек: Kotlin · Jetpack Compose · Clean Architecture (MVVM, Hilt,
Coroutines/Flow) · многомодульный.

---

## Ключевые идеи

1. **Intent-first, а не «ИИ рулит телефоном».** LLM никогда не выполняет код и не управляет
   устройством напрямую. Он лишь переводит человеческую речь в структурированное намерение:

   ```
   Пользователь → LLM (маршрутизатор) → структурированное действие (LauncherAction)
                → подтверждение при риске → Intent Executor → Android API
   ```

   Это безопаснее и предсказуемее, чем позволять модели выполнять произвольные операции.

2. **Локальное детерминированное сопоставление выполняется ДО любого обращения к облаку/LLM.**
   Быстрый матчер на правилах (`< 10мс`) отрабатывает первым; облачный LLM подключается только при
   низкой уверенности правил или явно естественно-языковом запросе. Его сбой/офлайн деградирует к
   результату правил — **офлайн-поведение байт-в-байт идентично режиму «только правила»**.

3. **Каждое действие ИИ ограничено тремя воротами: уверенность → разрешения → подтверждение риска.**
   ИИ никогда молча не выполняет рискованное или разрушительное действие.

4. **Приватность как граница исходящего трафика.** Приватный контекст (заголовки календаря,
   координаты, сообщения, буфер обмена) по умолчанию **никогда** не уходит в облако. Исходящее
   содержимое ограничено fail-closed allow-list'ом (`OutboundContextPolicy`).

5. **BYOK — Bring Your Own Key.** Пользователь подключает свой ключ к любому OpenAI-совместимому
   провайдеру. Ключ хранится зашифрованным в Android Keystore (AES-256-GCM), никогда не попадает в
   логи, крэш-репорты или исходники. Архитектура построена вокруг портов (`GenerativeAiEngine`,
   `CommandPlanner`), поэтому провайдера/модель можно менять без переписывания приложения.

6. **Ядро лаунчера полностью работает офлайн.** Домашний экран, сетка приложений, запуск — без ИИ,
   сети, микрофона и опциональных разрешений. Ни одна ИИ-функция не блокирует запуск.

---

## Три стадии продукта

Один продуктовый замысел, реализуемый в три эволюционные стадии — каждая самодостаточна и поставляема
отдельно. Полная дорожная карта: [docs/roadmap.md](docs/roadmap.md).

```
Стадия 1 — AI Launcher (MVP)   →   Стадия 2 — AI Framework   →   Стадия 3 — Agentic OS
   ✅ ЗАКРЫТА, принята на              🔄 В РАБОТЕ                     🔮 БУДУЩЕЕ
      устройстве                       (S2-1 закрыта)                 (после стадий 1–2)
```

### Стадия 1 — AI Launcher (MVP) · ✅ ЗАКРЫТА

Настоящий AI-first лаунчер: одно универсальное поле ввода, которое *понимает* естественный язык и
*маршрутизирует* его в безопасные действия, с быстрым офлайн-путём на правилах под ним. **Это то, что
поставляется первым.**

Стадия закрыта (треки `AIL-0…6`) и **принята на устройстве SM-A325F / Android 13** (2026-07-06):
NL-запрос → карточка подтверждения (CONFIRM) / one-tap (SAFE) → выполнение; ничего не запускается
автоматически; офлайн / router-off ⇒ паритет «только правила»; 0 утечек ключа.

### Стадия 2 — AI Framework · 🔄 В РАБОТЕ

Обобщение механики лаунчера (маршрутизация / действия / контекст / память) в переиспользуемый,
тестируемый **on-device AI-фреймворк**, который может использовать любая поверхность. Строится
**feature-first** — растим лишь минимальные абстракции, которые нужны каждой узкой возможности.

- **S2-1 «Learned Resolutions» · ✅ ЗАКРЫТА (принята на устройстве 2026-07-11).** On-device обучение
  тому, какое приложение имел в виду пользователь при неоднозначной команде запуска (rank-first →
  авто-разрешение по порогу при `streak ≥ 3`, полностью офлайн, без LLM/облака), всегда исправимо
  через Settings → Learned Choices. Паритет сохранён; исходящий allow-list расширен на ноль.
- **S2-2 «Explicit Aliases» · ⏸ приостановлена.** Домен/данные/DI (фазы A–C) уже на ветке
  `launcher-4`; ждёт своего grey-UI в блоке DS-7.

**Инженерная цель стадий 2→3 — шестислойная агентная архитектура A1–A6**
([docs/agentic-os-architecture.md](docs/agentic-os-architecture.md)). Следующий естественный
архитектурный срез — **A1 (слой Tool / Capability)**.

### Стадия 3 — Agentic OS · 🔮 БУДУЩЕЕ

ИИ-операционный слой над Android: многошаговое планирование, безопасная автоматизация с согласием
пользователя, единая intent-driven оболочка. Не замена внутренностей Android. Начинается только после
стабильной поставки стадий 1–2.

Ключевое отличие от текущего состояния: сегодняшний пайплайн **одношаговый**
(`команда → одно действие → подтверждение → выполнение`) — это «умный маршрутизатор», а не агент.
Агентность — это **ограниченный цикл plan-execute** с согласием, вплетённым в цикл, где пользователь
владеет им (пауза/отмена на лету):

```
цель → ПЛАН (много шагов) → на шаг: ворота → вызов инструмента → наблюдение → (пере-план) → … → РЕЗУЛЬТАТ → трейс
```

Поглощает прежний трек «Phase 8 / accessibility»: тир доступности — строго opt-in, за воротами
согласия, никогда не обязателен для обычного использования.

---

## Что уже сделано в рамках MVP

Оболочка лаунчера, офлайн-ядро, персистентность, безопасность и UX построены и приняты на устройстве
SM-A325F / Android 13. Сжатая история (детали — в `CLAUDE.md` и пофазовых планах в `ai-context/`):

| Фаза | Что построено | Статус |
|---|---|---|
| **0–2** | Скелет + оболочка: многомодульная Clean Architecture, единый `NavHost`, репозиторий установленных приложений, ввод команд, офлайн home/grid/launch | ✅ |
| **3** | Intent-система: `RuleBasedIntentMatcher`, `ActionExecutor`, уверенностные ворота, MVP-цикл команд. Живёт на устройстве | ✅ |
| **4** | Персистентность/состояние/hardening: DataStore (настройки/флаги), Room (история использования/ранжирования/интентов с редакцией SEARCH/UNKNOWN), модуль permission-education, восстанавливаемый `UiState.Error` | ✅ |
| **5** | Облачный ИИ (BYOK): OpenAI-совместимый SSE-движок (`Flow<AiChunk>`), `SecureSecretStore` (Keystore AES-256-GCM), приватностные исходящие guard'ы, `DefaultGenerativeRouter` + статический fallback, стриминговый UI ассистента. **Проверено на устройстве** (openrouter / `gpt-4o-mini` стримил end-to-end) | ✅ |
| **6** | Локальный NLU (**только код, ИНЕРТЕН**): `OnnxIntentClassifier` (self-gating), rule-first `LayeredIntentMatcher`, провижининг модели (`ModelStore` / SHA-256 / WorkManager). **Модель не поставляется → всегда деградирует к правилам.** Ждёт OQ#1/#2 — отдельный трек, не блокер MVP | ✅ (код) |
| **7** | Голос + контекстные подсказки: `AndroidSpeechInputSource` + поток `RECORD_AUDIO`, офлайн + opt-in провайдеры подсказок, single-owner `LauncherUiState.suggestions`, периодический precompute/cleanup | ✅ |
| **UX** | Редизайн home + дизайн-система: минимальный home + App Drawer, находимые иконки Settings/Assistant, дизайн-система `core/ui`, реальный `:feature:settings` | ✅ |
| **9** | Hardening: стартовая производительность (warm ~102мс, cold ~766мс, без спиннера на первом кадре), R8 + Baseline Profile, корректность подсказок, тест/приватность/логирование | ✅ |

### AI-Launcher completion track (AIL-0…6) — то, что делает лаунчер *ИИ*-лаунчером

- **AIL-0** — токены дизайна и визуальная идентичность (`core/ui`, presentation-only).
- **AIL-1** — Action Registry (`domain`): `LauncherAction` / `ActionDescriptor` / `ActionRiskLevel`
  {SAFE, CONFIRM, DANGEROUS} / `ActionId` — единый тестируемый, permission-aware словарь целей.
- **AIL-2** — Web / URL / Play-Store маршрутизация (без ИИ): URL-подобный ввод → `ACTION_VIEW` после
  безопасной нормализации; «установи X» → Play Store (`market://`); настраиваемый веб-провайдер.
  Полностью офлайн.
- **AIL-3** — Universal Input: одно поле = фильтр приложений + команда + web/site + вход в ассистент
  + голос. Аддитивный `UniversalInputRouter` + sealed `InputIntent`.
- **AIL-4** — **LLM Action Router (ядро ИИ, BYOK cloud).** Новый **третий порт** `CommandPlanner` —
  отличный от `IntentMatcher` (классификация) и `GenerateReplyUseCase` (беседа ассистента).
  Подключается только при низкой уверенности правил / NL-вводе: отправляет команду + схему Action
  Registry в облачный движок, парсит **структурированное** предложение действия (никогда не свободный
  текст). Офлайн/сбой → результат правил. Приватность: только схема + команда.
- **AIL-5** — Confirmation & safety gating: `ActionRiskLevel` вплетён в выполнение — рискованные/
  неоднозначные действия требуют явного подтверждения (`ConfirmActionCard`); permission-gated; ничего
  не авто-выполняется.
- **AIL-6** — polish + приёмка на устройстве SM-A325F с реальным BYOK-провайдером.

**Definition of Done стадии 1 (выполнено):** пользователь набирает или произносит естественный запрос
в одно поле → лаунчер корректно маршрутизирует его в app / web / site / Play Store / settings /
assistant; рискованные действия спрашивают первыми; набранные команды не изменились; офлайн-поведение
идентично «только правила»; принято на устройстве.

### Дизайн-трек (параллельный, presentation-only)

Миграция на утверждённую идентичность **«soft classic grey»** по `docs/design/`. **DS-1…DS-4 —
DONE:** серый токен-слой + три-шрифтовая система + смягчённые формы + `SidrTheme`; аддитивные
примитивы `core/ui/primitive/`; SIDR-контролы (кнопки/чипы/строки/top-bar/`SidrActionGate`); Home
мигрирован на intent-first раскладку (`SidrUniversalInput`). Без изменений production
nav/VM/persistence/domain — пайплайн команд / роутинг / голос / офлайн-паритет полностью целы.

---

## Шестислойная агентная архитектура (цель A1–A6)

North-star инженерное направление эволюции из умного маршрутизатора в local-first AI agentic OS
([детали](docs/agentic-os-architecture.md)). Каждый слой становится реальным срезом (spec → plan →
build) в стиле проекта: feature-first, порты, rule-first, fail-closed, приватностно-ограниченно.
**Золотое правило: UI поверхности строится только когда её движок реален.**

| Слой | Назначение | Растёт из |
|---|---|---|
| **A1 — Tool / Capability** | руки агента; граница безопасности | `ActionCatalog` / `ActionDescriptor` / `ActionRiskLevel` / `ActionExecutor` |
| **A2 — Context Engine v2** | чувства агента; приватные `ContextSnapshot` | разрозненные провайдеры подсказок, `DeviceProfileProvider`, `ConnectivityChecker` |
| **A3 — User Memory** | преемственность; редактируемая память | `ResolutionPreferenceStore` (S2-1) + `AliasStore` (S2-2) |
| **A4 — Agent Runtime** | недостающее ядро: ограниченный цикл plan-execute с согласием | `CommandPlanner` + `RouteCommandUseCase` + `ExecuteActionUseCase` |
| **A5 — Activity / Trace** | честная история выполнения (opt-in персистентность) | `ExecutionTrace` + `IntentMatchHistoryRepository` |
| **A6 — Grants + Automation** | согласие на возможности + автоматизация (Стадия 3) | A1-тиры + A4-согласие + permission-education |

---

## Архитектура

### Модули

```text
app/                          Android-приложение, единый NavHost, композиционный корень, Hilt-граф
core/ui/                      Дизайн-система, Compose-компоненты, тема
core/common/                  Контракты представления/состояния: UiState, диспетчеры, навигация
core/android/                 DeviceProfile-детекция, PackageManager, SpeechInputSource (Android impl)
core/testing/                 Общие fake'и и тестовые фикстуры
domain/                       Чистый Kotlin: модели, порты, use case'ы, OperationResult
data/repository/              Репозитории лаунчера, rule-based matcher, Android ActionExecutor, Room + DataStore
data/ai-cloud/                Ktor-клиент облачного ИИ + стриминг (+ LlmCommandPlanner)
data/ai-local/                ONNX NLU, эмбеддинги, локальный инференс (ONNX изолирован)
feature/launcher/             Home, сетка приложений, ввод команд, взаимодействия
feature/assistant/            Текст/голос UI ассистента
feature/suggestions/          Stateless строка контекстных подсказок (UI-only)
feature/settings/             Настройки
feature/permission_education/ UI объяснения разрешений (education ≠ request)
baselineprofile/              Baseline Profile для стартовой производительности
```

### Жёсткие правила (инварианты во всех стадиях)

- `domain` = чистый Kotlin (stdlib + coroutines). Без Android, без `core/*`.
- Интерфейсы в `domain`; реализации в `data/*`. UI не содержит бизнес-логики.
- Нет `feature → feature` зависимостей. Единый `NavHost` в `app`. ViewModel'и эмитят
  `NavigationEvent`, никогда не трогают `NavHostController`.
- Операции репозиториев/use case'ов возвращают `OperationResult<T>`; никогда не бросают исключения в UI.
- `IntentMatcher` (→ `IntentMatchResult`) — **другой порт**, чем `GenerativeAiEngine`
  (→ `Flow<AiChunk>`). Сопоставление ≠ генерация.
- `CommandPlanner` — санкционированный **третий** пайплайн (structured routing-via-LLM). Подключается
  только при низкой уверенности правил / NL-вводе. LLM-предложения никогда не авто-выполняют
  рискованное действие; router-off ⇒ байт-в-байт паритет «только правила».
- Ядро лаунчера полностью работает офлайн; опциональные разрешения никогда не блокируют старт.

### Пайплайн выполнения

Два независимых пайплайна, не разделяющих код: **сопоставление интентов** (офлайн, быстрый) и
**генеративный ИИ** (экран ассистента). Плюс третий — **LLM-маршрутизатор**.

```text
Ввод пользователя
      │
      ▼
RouteCommandUseCase ──(1)──► HandleUserCommandUseCase   (правила первыми, офлайн, < 10мс)
      │                            │  LayeredIntentMatcher: rule-first → NLU (self-gating, инертен без модели)
      │                            ▼
      │                       CommandOutcome  (уверенный результат правил возвращается как есть)
      │
      ├─(2) router off ⇒ паритет «только правила»
      ├─(3) только Unknown / LowConfidence достигают LLM
      ├─(4) офлайн ⇒ результат правил
      └─(5) CommandPlanner.plan(команда, ActionCatalog)
                 │
                 ├─ RoutedAction → CommandOutcome.RoutedAction (SAFE = one-tap · CONFIRM = карточка)
                 ├─ Clarify      → Message
                 └─ NoPlan       → результат правил
                          │
                          ▼ (подтверждение при риске)
                 ExecuteActionUseCase → IntentActionResolver → ActionExecutor → Android API
```

Порядок fallback генеративного пути (экран ассистента):
`rule matcher (всегда) → ONNX (зарезервировано) → cloud AI (если online + config + key) → статический fallback (всегда)`.

### Производительность (жёсткие бюджеты)

Cold start (лаунчер виден) `< 400мс` · первый кадр `< 200мс` · rule-матч `< 10мс` · ONNX-инференс на
MID_RANGE `< 150мс` · первый стриминговый токен `< 2000мс`. Потолки heap: LOW_END `< 80МБ` ·
MID_RANGE `< 150МБ` · HIGH_END `< 250МБ`. Функция, не укладывающаяся в бюджет, деградирует или
отключается на данном `DeviceProfile`.

### Разрешения

Обязательное: `QUERY_ALL_PACKAGES` (обнаружение приложений). Опциональные (запрашиваются только по
триггеру фичи, отказ отключает только эту фичу): `SET_WALLPAPER`, `RECORD_AUDIO`, `READ_CALENDAR`,
`ACCESS_FINE_LOCATION`, `RECEIVE_BOOT_COMPLETED`. `BIND_ACCESSIBILITY_SERVICE` — ни запрашивается, ни
объясняется (отложено до Стадии 3, только opt-in).

---

## Стек

Kotlin · Jetpack Compose · Material 3 · MVVM + Clean Architecture · Hilt · Coroutines/Flow · Ktor
(облачный ИИ + стриминг) · Kotlin Serialization · ONNX Runtime Mobile (с NNAPI где доступно) ·
Navigation Compose · DataStore · Room · WorkManager.

## Сборка

Многомодульный Gradle-проект. **Собирать под JDK 17** (системный JDK 25 ломает Gradle 8.10.2).
Основной gate-набор:

```bash
./gradlew --no-daemon :domain:test testDebugUnitTest assembleDebug
```

---

## Что дальше

- **Стадия 2 (AI Framework).** Следующий архитектурный срез — **A1 (слой Tool / Capability)**: он же
  превращает `alias → tool-call` (разблокирует приостановленную S2-2 «Explicit Aliases») и открывает
  всё, что ниже по стеку (A2 контекст → A3 память → A4 runtime → A5 activity → A6 grants).
- **Дизайн-трек.** Следующий блок — **DS-5 (Action & Safety)**; далее DS-6A/6B (sacred header +
  корректность молитвенных данных), DS-7 (grey-UI для S2-2 aliases / memory-поверхности).
- **Отдельный model-трек (вне ship-gate MVP).** OQ#1/#2 — реальная NLU-модель (`intent.onnx` +
  pruned multilingual `vocab.txt`) + host + SHA-256; OQ#3 — модель эмбеддингов (семантический
  re-rank, блок V); OQ#4 — on-device STT по матрице устройств; валидация Android 9/11/14 + реального
  LOW_END-железа; boot-warmup после перезагрузки.

## Источники истины

- Дорожная карта: [docs/roadmap.md](docs/roadmap.md)
- Архитектура: [docs/architecture.md](docs/architecture.md)
- Агентная цель A1–A6: [docs/agentic-os-architecture.md](docs/agentic-os-architecture.md)
- Лог решений (ADR): [ai-context/decisions.md](ai-context/decisions.md)
- Текущий статус: [ai-context/current-status.md](ai-context/current-status.md)
- Дизайн-система: [docs/design/](docs/design/)
- Оперативный дайджест и жёсткие правила: [CLAUDE.md](CLAUDE.md)

---

*Автор: Али Булатов SidrOS Project.*
