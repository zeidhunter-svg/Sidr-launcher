# SIDR Design & Architecture Audit

## Версия 1.0

**Объект аудита:** SIDR Launcher / AI Framework
**Текущая стадия:** переход от Stage 1 — AI Launcher к Stage 2 — AI Framework
**Целевая перспектива:** Stage 3 — Agentic OS
**Целевая дизайн-философия:**
**Islamic Order × Computational Precision × Agentic Intelligence**

---

# 1. Итоговый вердикт

SIDR уже имеет **сильную и необычно дисциплинированную программную архитектуру**, но его визуальная архитектура пока остаётся архитектурой продвинутого AI-лаунчера.

Между ними возник разрыв:

```text
ТЕХНИЧЕСКАЯ АРХИТЕКТУРА
уже готова развиваться в AI Framework

ВИЗУАЛЬНАЯ АРХИТЕКТУРА
ещё в основном выражает terminal-themed launcher
```

Проект успешно прошёл Stage 1: Universal Input, Action Registry, cloud Command Planner, rule-first fallback, подтверждение рискованных действий и реальное выполнение уже проверены на устройстве. Активная цель теперь — обобщение routing, context и memory в AI Framework.

Поэтому SIDR **не требуется ещё один косметический рескин**.

Требуется переход:

```text
от:
Cyberpunk Terminal Launcher

к:
Intent-Driven Islamic Agentic System
```

Текущий AIL-0 следует сохранить как технологический и стилистический фундамент, но перестать считать его конечным визуальным языком.

## Общая оценка

| Область                           | Оценка | Вывод                                   |
| --------------------------------- | -----: | --------------------------------------- |
| Clean Architecture                | 9.2/10 | Очень сильная основа                    |
| Безопасность действий             | 9.0/10 | Правильная модель контроля              |
| Privacy architecture              | 9.3/10 | Одна из сильнейших сторон               |
| Offline degradation               | 9.4/10 | Архитектурно образцово                  |
| Навигационная архитектура         | 8.4/10 | Хороша для текущего масштаба            |
| Текущий Home UX                   | 7.3/10 | Хороший launcher Home, не OS Home       |
| Design tokens                     | 6.8/10 | Рабочие, но ещё не системные            |
| Component system                  | 6.5/10 | Набор компонентов, не полная библиотека |
| Agentic-state visualization       | 4.5/10 | Главный будущий пробел                  |
| Исламская идентичность            | 3.0/10 | Пока почти отсутствует в системе        |
| Масштабируемость UI до Agentic OS | 5.5/10 | Нужна новая визуальная архитектура      |

---

# 2. Текущее положение SIDR

SIDR уже не является экспериментальным Android launcher skeleton.

В Stage 1 реализованы:

* launcher shell;
* offline app launch;
* Universal Input;
* rule-based intent matching;
* cloud LLM routing;
* Action Registry;
* URL, web и Play Store actions;
* SAFE/CONFIRM risk model;
* явное подтверждение действий;
* assistant streaming;
* voice input;
* contextual suggestions;
* app drawer;
* settings;
* Room и DataStore;
* secure BYOK storage;
* privacy guards;
* Learned Resolutions как первый Stage 2 memory slice.

Roadmap правильно определяет три самостоятельные стадии:

```text
Stage 1 — AI Launcher
Stage 2 — AI Framework
Stage 3 — Agentic OS
```

При этом через все стадии сохраняются offline core, local-first deterministic routing, risk gating, отсутствие `feature → feature` зависимостей, чистый domain и fail-closed privacy.

## Главный вывод

SIDR уже имеет не только набор функций, но и **собственную вычислительную философию**:

```text
deterministic first
AI when useful
fail closed
never silently execute risk
keep private context local
preserve user control
```

Это очень сильная основа для исламского духа системы.

Проблема только в том, что интерфейс пока не выражает эту философию полностью.

---

# 3. Аудит продуктовой архитектуры

## 3.1. Сильная сторона: разделение трёх AI-путей

SIDR правильно не смешивает:

```text
IntentMatcher
→ локальная классификация

GenerativeAiEngine
→ разговорный ответ

CommandPlanner
→ структурированное предложение действия
```

Это фундаментально верное решение.

Многие AI-продукты делают один универсальный LLM-слой, который одновременно:

* отвечает;
* классифицирует;
* выбирает инструменты;
* выполняет;
* объясняет.

Это создаёт неясные границы ответственности и усложняет безопасность.

В SIDR Command Planner является отдельным domain-port, а его результат строго парсится в зарегистрированное действие. Неизвестный action, неверные аргументы или свободный текст закрываются в `NoPlan`. Рискованные действия не запускаются автоматически.

### Design consequence

В UI также должны существовать разные визуальные состояния:

```text
MATCHED LOCALLY
ROUTED BY AI
ANSWERED BY ASSISTANT
PROPOSED ACTION
CONFIRMED ACTION
EXECUTED ACTION
```

Сейчас эти архитектурные различия выражены недостаточно системно.

Пользователь не должен разбираться в названиях внутренних движков, но обязан понимать разницу между:

* найденным приложением;
* предложением AI;
* ответом;
* планом;
* выполняемым действием;
* завершённым результатом.

---

## 3.2. Сильная сторона: rule-first и offline parity

Архитектура сохраняет точный rule-only outcome, когда:

* router выключен;
* нет сети;
* нет API key;
* LLM не отвечает;
* output невозможно безопасно разобрать.

Это сильнее обычного «graceful degradation».

Фактически SIDR построен как:

```text
reliable launcher
+
optional intelligence
```

а не:

```text
AI application
+
weak offline fallback
```

### Design consequence

Offline-режим не должен выглядеть как аварийный или неполноценный.

Не следует показывать:

```text
AI UNAVAILABLE
LIMITED MODE
```

при каждом offline-запросе.

Лучше:

```text
LOCAL ROUTING
```

или вообще ничего, если локальная система успешно выполнила запрос.

Cloud/local indicator следует показывать только когда это помогает пользователю понять privacy, задержку или доступность функции.

---

## 3.3. Сильная сторона: safety architecture

Action Registry и `ActionRiskLevel` формируют правильную основу:

```text
SAFE
CONFIRM
DANGEROUS
```

Модель поведения Stage 1 консервативна:

* AI предлагает;
* SAFE требует явного пользовательского запуска;
* CONFIRM показывает confirmation surface;
* рискованные действия не запускаются автоматически.

Это соответствует будущей Agentic OS лучше, чем привычная модель «AI делает всё сам».

### Design consequence

Risk UI должен стать отдельной полноценной подсистемой, а не только одной `ConfirmActionCard`.

Необходимы визуальные паттерны:

```text
PROPOSED
REQUIRES CONFIRMATION
PERMISSION REQUIRED
SENSITIVE DATA
EXTERNAL APPLICATION
IRREVERSIBLE
BLOCKED BY POLICY
```

Сейчас DF-4 Confirmation Card — хороший первый компонент, но не завершённая safety language.

---

## 3.4. Сильная сторона: privacy by construction

В outbound router request разрешены только:

* пользовательская команда;
* статическая Action Registry schema.

Не отправляются:

* calendar;
* location;
* usage history;
* clipboard;
* messages;
* device context.

API key хранится через Keystore-backed secret storage, а failures закрываются безопасно.

### Design consequence

Privacy не должна оставаться только архитектурным инвариантом.

Её следует сделать видимой частью продукта:

```text
LOCAL
Processed on this device

CLOUD
Command and action schema only

PRIVATE CONTEXT
Not shared

MEMORY
Stored on device
```

Важно не перегружать экран значками privacy, но давать эти сведения там, где принимается решение:

* confirmation;
* permission request;
* memory save;
* automation creation;
* cloud processing.

---

# 4. Аудит модульной архитектуры

## 4.1. Domain

`domain` правильно остаётся Android-free и зависит только от Kotlin/coroutines.

Здесь находятся:

* use cases;
* action contracts;
* AI ports;
* memory policies;
* results;
* repository interfaces;
* routing semantics.

### Вердикт

**Сохранять без изменений как главный инвариант.**

Design System не должна заставлять domain возвращать presentation-specific модели.

Плохо:

```kotlin
ActionRiskLevel.CONFIRM -> AmberConfirmationCard
```

Хорошо:

```kotlin
ActionRiskLevel.CONFIRM
```

а mapping в presentation:

```kotlin
ActionRiskLevel.CONFIRM -> SidrRiskUi.ConfirmationRequired
```

---

## 4.2. `core/ui`

`core/ui` был правильно создан как presentation-only библиотека.

Особенно удачны следующие решения:

* отсутствие зависимости от domain/data/feature;
* `AppTile` принимает icon slot;
* reusable status surfaces;
* общий app-wide `SidrTheme`;
* preview-based проверка;
* `ConfirmActionCard` остаётся dumb-компонентом;
* компоненты не знают бизнес-семантику.

### Проблема

Сейчас `core/ui` больше похож на:

```text
theme + reusable launcher components
```

чем на:

```text
complete operating-system design system
```

В нём есть фундамент, но недостаточно уровней абстракции.

Не хватает явного разделения:

```text
tokens/
primitives/
components/
patterns/
layouts/
motion/
semantics/
```

### Рекомендованная структура

```text
core/ui/
├── theme/
│   ├── SidrTheme
│   ├── SidrColorScheme
│   ├── SidrTypography
│   ├── SidrShapes
│   └── SidrMotion
│
├── token/
│   ├── SidrSpacing
│   ├── SidrSize
│   ├── SidrStroke
│   ├── SidrElevation
│   └── SidrAlpha
│
├── primitive/
│   ├── SidrSurface
│   ├── SidrDivider
│   ├── SidrIcon
│   ├── SidrText
│   ├── SidrStatusMarker
│   └── SidrFocusRing
│
├── component/
│   ├── button/
│   ├── input/
│   ├── chip/
│   ├── navigation/
│   ├── state/
│   └── selection/
│
├── pattern/
│   ├── action/
│   ├── execution/
│   ├── permission/
│   ├── memory/
│   └── agent/
│
└── preview/
```

---

## 4.3. Feature-модули

Разделение feature-модулей в целом правильное:

```text
feature/launcher
feature/assistant
feature/settings
feature/permission_education
feature/suggestions
```

Отсутствие `feature → feature` зависимости — очень полезное ограничение.

### Текущий риск

По мере появления:

* Context Engine;
* User Memory;
* Activity;
* agents;
* automations;
* execution history;

может начаться разрастание `feature/settings` или `feature/launcher`.

`LearnedChoicesScreen` внутри settings логичен для первой небольшой memory-функции, но будущая User Memory уже не должна оставаться подразделом Settings.

### Рекомендация

Не создавать новые модули заранее, но зафиксировать триггеры:

```text
feature/memory
создаётся, когда память становится самостоятельной пользовательской поверхностью

feature/activity
создаётся, когда execution history становится больше диагностического списка

feature/agents
создаётся, когда существует управляемая сущность Agent

feature/automation
создаётся перед Stage 3, когда появляются фоновые планы и политики
```

---

## 4.4. `app` и навигация

Single `NavHost` в `:app` — правильное решение.

ViewModel не получает `NavController`; события проходят:

```text
ViewModel
→ NavigationEvent
→ AppNavHost
```

Безопасный fallback и payload-free logging также являются сильной частью архитектуры. `AppNavHost` уже содержит Launcher, Assistant, App Drawer, Settings, Learned Choices и Permission Education. Это показывает, что навигационная модель выросла из раннего shell, но пока остаётся управляемой.

### Риск масштабирования

Строковые route-константы и общий `NavigationEvent.NavigateTo(route: String)` начнут становиться менее безопасными, когда появятся:

* вложенные agent destinations;
* memory details;
* execution detail;
* automation editors;
* context inspectors;
* deep links;
* multi-pane navigation.

### Рекомендация

Пока не переписывать навигацию.

Но зафиксировать архитектурный триггер:

> При появлении второй самостоятельной Stage 2 feature-поверхности после Memory извлечь `core/navigation` и перейти к typed destination contracts.

Это не требуется для текущего редизайна.

---

# 5. Аудит текущего Home

## 5.1. Что сделано правильно

Phase UX убрал полный app grid с Home и перенёс его в отдельный App Drawer.

Текущий Home включает:

* Settings;
* Assistant;
* Universal Input;
* command feedback;
* contextual suggestions;
* Favorites;
* All apps.

Это значительно лучше обычного launcher home с сеткой иконок.

Universal Input объединил:

* app search;
* command;
* web;
* site;
* assistant;
* voice.

Это уже правильная основа intent-first UI.

---

## 5.2. Главная проблема Home

Home всё ещё построен вокруг модели:

```text
search field
+ suggestions
+ favorite applications
+ all apps
```

То есть он остаётся **улучшенным launcher home**.

Для будущего SIDR нужен Home, построенный вокруг:

```text
spiritual anchor
+ universal intent
+ current context
+ active process
+ relevant continuation
```

Favorites и All Apps должны оставаться доступными, но не определять идентичность Home.

---

## 5.3. Рекомендованная информационная архитектура Home

```text
1. Sacred Header
   Shahada
   Prayer context

2. Universal Intent
   Text / voice / command

3. Active State
   Current task or pending confirmation

4. Relevant Context
   Next event, learned continuation, useful suggestion

5. Launcher Access
   Favorites / All Apps

6. Global Navigation
```

В состоянии покоя Home должен быть спокойным.

В состоянии выполнения он должен адаптироваться:

```text
IDLE
→ духовный и контекстный Home

TYPING
→ input становится главным элементом

AMBIGUOUS
→ выбор кандидата

PROPOSED
→ действие и риск

RUNNING
→ execution stream

COMPLETED
→ результат и следующий шаг
```

---

# 6. Аудит текущего визуального языка AIL-0

## 6.1. Что содержит AIL-0

AIL-0 заменил нейтральный Material You на:

* green default accent;
* amber alternative;
* dark/light schemes;
* JetBrains Mono;
* brutalist 0/2/4/8 dp shapes;
* terminal prompt;
* block caret;
* chip press-invert;
* `>_` brand mark;
* scanline/CRT elements;
* terminal confirmation card.

Эта система успешно прошла device acceptance.

---

## 6.2. Сильные стороны

### Узнаваемость

SIDR уже не выглядит как стандартный Material 3 launcher.

### Точность

Mono-типографика хорошо подходит для:

* commands;
* statuses;
* logs;
* execution steps;
* identifiers.

### Строгость

Небольшие радиусы и чёткие границы хорошо передают:

* системность;
* контроль;
* функциональность.

### Отличимый Universal Input

Символ `>` и block caret создают понятную идентичность.

### Сильный confirmation pattern

DF-4 визуально ясно отделяет предложение от выполнения.

---

## 6.3. Ограничения

### 1. Mono используется слишком широко

Полностью моноширинный интерфейс:

* хуже читается в длинных ответах;
* увеличивает визуальную плотность;
* быстро утомляет;
* делает Settings похожими на консоль;
* снижает эмоциональную зрелость продукта.

### 2. Cyberpunk становится целью вместо инструмента

Scanlines, phosphor green и terminal styling могут создать неверное ожидание:

```text
SIDR = ретро-компьютерная эстетика
```

вместо:

```text
SIDR = новая модель взаимодействия с вычислительной системой
```

### 3. Green/amber пока воспринимаются как тема терминала

Цвета должны постепенно получить более глубокое значение:

* green — life, balance, local/safe state;
* amber/gold — attention, knowledge, primary agency;
* muted red — real risk;
* neutral white — active intelligence.

Но нельзя превращать это в прямую религиозную символику или декоративную цветовую схему.

### 4. Brutalism недостаточен для длинных системных процессов

Жёсткие блоки хороши для:

* prompt;
* confirmation;
* status.

Они хуже подходят для:

* explanations;
* memory details;
* context;
* agent plans;
* multi-step tasks.

### 5. Нет визуального различия между уровнями системы

Нужны отдельные языки для:

```text
human content
system metadata
AI proposal
execution
memory
risk
spiritual context
```

Сейчас большую часть интерфейса объединяет terminal treatment.

---

# 7. Сохранить, эволюционировать, удалить

## 7.1. Сохранить

### `>` как символ намерения

Оставить как основной знак Universal Input.

Но это не должен быть общий логотип всей системы в каждом месте.

### Block caret

Оставить для состояния активного ввода.

Не использовать как постоянную декоративную анимацию.

### Green и amber accents

Оставить пользовательский выбор.

Сделать оттенки более зрелыми и менее «неоновыми».

### JetBrains Mono

Оставить для:

* commands;
* statuses;
* timestamps;
* technical metadata;
* execution stream;
* action arguments.

### Малые радиусы

Оставить как часть идентичности.

### Press-invert

Сохранить для terminal actions и selected command chips.

### Confirmation Card

Сохранить модель, но преобразовать в общую Action Gate family.

### Строгие границы

Оставить там, где граница имеет смысл:

* permission;
* execution step;
* risk;
* selected entity.

---

## 7.2. Эволюционировать

### SidrTheme

Из четырёх Material ColorScheme преобразовать в:

```text
Material ColorScheme
+
Sidr semantic colors
+
Sidr status colors
+
Sidr content roles
```

### Typography

Перейти к dual-font model:

```text
Interface Sans
для содержания

System Mono
для вычислительного состояния
```

### Route Chips

Разделить на:

```text
RouteChip
SuggestionChip
ActionChip
FilterChip
StatusChip
RiskChip
```

Не делать один chip универсальным для всех сущностей.

### SidrScaffold

Добавить варианты:

```text
Standard
Home
Focused
Immersive
Detail
```

Но не создавать несколько независимых scaffold-реализаций.

### SectionHeader

Отказаться от uppercase везде.

Использовать uppercase только для коротких системных секций.

### ConfirmActionCard

Преобразовать в:

```text
SidrActionGate
├── Confirmation
├── Permission
├── SensitiveData
├── ExternalHandoff
└── Destructive
```

### Empty/Error states

Добавить:

```text
Blocked
Offline
PartialSuccess
Cancelled
NoPermission
NoMemory
```

---

## 7.3. Удалить или сильно ограничить

### Ultra-cyberpunk как формальное название идентичности

Заменить на:

> SIDR — Islamic Agentic System

### CRT scanlines

Не использовать как постоянный app-wide overlay.

Возможен крайне тонкий эффект только в:

* boot surface;
* developer console;
* optional visual theme.

### Phosphor glow

Убрать с основного текста и обычных компонентов.

### Mono для длинного текста

Убрать полностью.

### Рамка вокруг каждого блока

Убрать.

### Terminal syntax как декор

Терминальные маркеры должны сообщать реальные состояния.

### `>_` как постоянный brand mark на каждом экране

Оставить для boot/technical context, но основной SIDR identity должна быть спокойнее.

---

# 8. Исламский дух: текущий разрыв

## 8.1. Что уже соответствует исламскому духу

Даже без визуальной исламской темы SIDR уже содержит важные качества:

* пользователь контролирует выполнение;
* рискованные действия не скрываются;
* приватные данные не отправляются по умолчанию;
* память можно удалить;
* система не притворяется уверенной;
* невалидное AI-предложение закрывается;
* локальные возможности сохраняются без облака;
* интерфейс стремится к ясности.

Это серьёзнее, чем простое добавление исламского орнамента.

---

## 8.2. Чего пока нет

Нет оформленной системы принципов, которая связывает:

* визуальный дизайн;
* AI behavior;
* privacy;
* memory;
* automation;
* spiritual context.

Шахада и prayer times пока являются будущими элементами, а не частью утверждённой product architecture.

---

## 8.3. Рекомендованные исламские принципы SIDR

Названия ниже являются дизайн-принципами, а не попыткой выносить религиозные постановления.

### Niyyah — ясность намерения

Система должна ясно показывать, что она поняла.

```text
YOUR INTENT
Open the official GitHub website
```

### Amanah — ответственность и доверие

Данные и выданные разрешения рассматриваются как доверенные пользователем.

```text
Stored on this device
Not shared with cloud processing
```

### Mizan — баланс

Интерфейс не должен ни скрывать сложность, ни обрушивать её на пользователя.

### Ilm — знание и объяснимость

SIDR показывает источник, причину выбора и основание действия.

```text
Selected because you confirmed this app three times.
```

### Adl — последовательность правил

Одинаковые риски должны получать одинаковый UI независимо от того, кто предложил действие:

* rule engine;
* local model;
* cloud model;
* automation;
* agent.

### Haya — сдержанность

Никаких агрессивных уведомлений, манипулятивных CTA и демонстративной визуальной роскоши.

### Sukun — спокойствие

Покой является нормальным состоянием Home.

Система активна визуально только во время реальной активности.

---

# 9. Shahada и Home

Шахада должна присутствовать на Home в абсолютной форме:

```text
لا إله إلا الله محمد رسول الله

THERE IS NO DEITY EXCEPT ALLAH
MUHAMMAD IS THE MESSENGER OF ALLAH
```

## Правила

Она не должна:

* быть кнопкой;
* быть логотипом бренда;
* мигать;
* реагировать на scroll;
* соседствовать с рекламой;
* использоваться как splash slogan;
* становиться декоративным паттерном;
* обрезаться;
* быть частью случайно закрываемой карточки.

Она должна находиться в спокойной, устойчивой верхней области Home.

## Рекомендованная иерархия

```text
Arabic
основной сакральный уровень

English
небольшой перевод

Prayer context
функциональный временной слой

Universal Input
главное взаимодействие
```

## Важное ограничение

Для арабского текста нельзя использовать JetBrains Mono.

Нужен качественный арабский шрифт с корректной формой букв и уважительной композицией. Однако шрифт не должен имитировать сложную декоративную каллиграфию, если интерфейс не способен гарантировать корректное отображение.

---

# 10. Prayer Times

Prayer Times должны быть реальной системной функцией, а не статическим виджетом.

Необходимы:

* определение locality;
* локально принятый или официальный источник;
* выбранный calculation authority;
* timezone awareness;
* offline cache;
* last update;
* manual location override;
* privacy-safe location handling.

## UI-состояния

```text
Resolved
Updating
Using cached times
Location unavailable
Manual location
Method needs selection
```

## Не следует

* показывать только Fajr и Isha без возможности увидеть остальные;
* скрывать метод расчёта;
* автоматически отправлять precise location в cloud AI;
* смешивать prayer times с обычными suggestions;
* использовать молитвенные времена как декоративную метрику.

---

# 11. Новая визуальная архитектура SIDR

## 11.1. Четыре слоя интерфейса

### Sacred Layer

* Shahada;
* prayer times;
* spiritual calm;
* Home anchor.

Этот слой не интерактивен, кроме перехода к полному prayer view.

### Human Layer

* пользовательский текст;
* приложения;
* ответы;
* настройки;
* память;
* понятные объяснения.

Использует Interface Sans.

### System Layer

* statuses;
* commands;
* timestamps;
* tool names;
* execution metadata.

Использует System Mono.

### Agency Layer

* intent;
* plan;
* proposal;
* permission;
* execution;
* result.

Использует специализированные agentic patterns.

---

## 11.2. Главный цикл

```text
INTENT
↓
INTERPRETATION
↓
PROPOSAL OR PLAN
↓
USER CONTROL
↓
EXECUTION
↓
RESULT
↓
MEMORY
```

Каждая стадия должна иметь своё визуальное состояние.

---

# 12. Недостающая Agentic Component Architecture

## 12.1. Intent Surface

Показывает интерпретацию запроса:

```text
INTENT

Open the official GitHub website
```

Не показывается для очевидных локальных действий, если это создаёт лишний шаг.

---

## 12.2. Clarification Surface

```text
I found two matching applications.

Türkiye Finans
Ziraat Mobile
```

Это отдельный компонент, а не generic error.

---

## 12.3. Execution Plan

Нужен не сейчас для Stage 1, но компонент следует спроектировать заранее:

```text
PLAN

01  Check calendar
02  Find route
03  Prepare departure reminder
```

План не означает выполнение.

---

## 12.4. Execution Stream

Центральный паттерн будущего SIDR:

```text
EXECUTION

✓ Calendar checked
● Calculating route
○ Reminder waiting
```

Он должен стать главным отличием Agentic OS от chat UI.

---

## 12.5. Result Surface

Разделять:

```text
Completed
Partially completed
Failed
Cancelled
Blocked
```

---

## 12.6. Memory Disclosure

Когда система сохраняет learned resolution:

```text
LEARNED LOCALLY

“Open bank” now prefers Türkiye Finans.

Why:
You selected it 3 times.

[ VIEW ] [ FORGET ]
```

Но такой disclosure не должен появляться после каждого незначительного обновления.

---

# 13. Learned Resolutions: дизайн-аудит

Архитектурно Learned Resolutions — очень хороший первый Stage 2 slice.

Модель строится не как примитивное:

```text
command → app
```

а как:

```text
capability / intent
+ candidate set
+ resolution context
→ preferred target
+ evidence
```

Это правильно подготавливает путь к Context Engine и User Memory.

Auto-resolve допускается только для SAFE, CONFIDENT и совпадающего candidate fingerprint. Данные остаются локальными, а outbound allow-list не расширяется.

## Сильные стороны

* deterministic policy;
* raw evidence отдельно от confidence;
* no cloud;
* explicit successful choice required;
* delete-to-relearn;
* honest display status;
* privacy classification;
* bounded storage.

## UX-проблема

Управление только через Settings → Learned Choices достаточно для v1, но не решает проблему обнаружения:

* пользователь может не знать, что выбор был выучен;
* пользователь может не понимать, почему открывается конкретное приложение;
* correction после auto-resolve требует отдельного похода в Settings.

## Рекомендация

Сохранить Settings management как authoritative surface.

Добавить в Activity или result disclosure:

```text
Opened Türkiye Finans

Based on your learned preference
[ CHANGE ]
```

Кнопка `CHANGE` должна вести к соответствующей записи или удалять её только после отдельного подтверждения.

---

# 14. Settings audit

Текущий Settings решает реальные задачи:

* theme;
* accent;
* AI suggestions;
* usage personalization;
* voice;
* favorites count;
* router;
* assistant provider;
* learned choices;
* permissions;
* default launcher.

## Проблема

Settings постепенно превращается в список технических переключателей.

Для Agentic OS нужна новая IA:

```text
SYSTEM
├── Intelligence
├── Actions & confirmation
├── Memory
├── Privacy
├── Permissions
├── Personalization
├── Appearance
├── Device & performance
└── About
```

## Рекомендация

Не внедрять эту структуру немедленно.

Но Component Library должна поддерживать:

* settings section;
* navigation row;
* toggle row;
* choice row;
* status row;
* destructive row;
* inline explanation;
* policy summary.

---

# 15. Assistant audit

Assistant остаётся отдельной conversational surface.

Это архитектурно правильно: разговор не должен автоматически становиться execution authority.

## Риск

В будущем Universal Input и Assistant могут начать конкурировать:

```text
Home input:
понимает и выполняет

Assistant:
отвечает и обсуждает
```

Пользователь может не понимать, куда вводить сложный запрос.

## Рекомендация

Не объединять ViewModel и pipelines.

Объединить **entry semantics**:

```text
Home Universal Input
→ action intent
→ answer intent
→ clarification
→ assistant continuation
```

Assistant становится глубокой разговорной поверхностью, но Home остаётся универсальной точкой входа.

---

# 16. App Drawer audit

Текущий App Drawer:

* находится внутри launcher feature;
* имеет отдельный ViewModel;
* поддерживает поиск;
* группирует приложения;
* использует sticky headers;
* запускает приложение через существующий action path.

Это хороший прагматичный дизайн.

## Рекомендации

Сохранить App Drawer как утилитарную поверхность.

Не переносить туда:

* agent commands;
* contextual suggestions;
* memory;
* assistant responses.

Добавить позднее:

* favorites marker;
* learned alias indicator;
* optional A–Z rail;
* context actions по long press.

App Drawer не должен стать вторым Home.

---

# 17. Навигационная модель будущего SIDR

Пять постоянных вкладок сейчас добавлять рано.

На текущем этапе лучше:

```text
Home
├── App Drawer
├── Assistant
├── Activity
├── Memory
└── System
```

При переходе к полноценной Stage 3 возможна нижняя навигация:

```text
HOME
AGENTS
MEMORY
ACTIVITY
SYSTEM
```

Но `AGENTS` нельзя добавлять до появления реальной управляемой модели агента.

Иначе UI будет обещать функциональность, которой ещё нет.

---

# 18. Accessibility audit

Текущая архитектура уже учитывает:

* touch target 48 dp;
* non-null content descriptions;
* heading semantics;
* error/retry states;
* optional permission education;
* voice availability;
* reduced functionality without permissions.

Это хорошая база.

## Недостающие требования

### Large font testing

Agentic cards могут быстро разрушаться при 1.5–2.0 font scale.

### RTL

Из-за Arabic Shahada необходимо проверить:

* корректное направление арабского текста;
* независимость направления системной навигации;
* отсутствие смешения Arabic и English baseline;
* TalkBack reading order.

### Dynamic status announcements

Execution transitions должны объявляться screen reader только когда они значимы.

### Motion reduction

Block caret и scanline не должны игнорировать system reduce-motion preference.

### Color blindness

Green/amber нельзя использовать как единственное различие.

---

# 19. Performance audit применительно к дизайну

Проект имеет жёсткие performance budgets, а реальный cold start остаётся около 766 ms на проверенном устройстве.

Поэтому новая система не должна добавлять на первый кадр:

* blur;
* shaders;
* animated backgrounds;
* vector-heavy ornament;
* runtime-generated Islamic patterns;
* сложные gradients;
* image decoding;
* multiple infinite transitions;
* remote prayer data blocking startup.

## Home first-frame policy

Первый кадр должен использовать:

* cached prayer times;
* static text;
* local theme;
* no loading spinner;
* no AI initialization dependency;
* no network dependency.

Обновления приходят после первого кадра.

---

# 20. Документационные расхождения

Архитектурный документ местами отстаёт от реального проекта.

Например, он всё ещё может описывать `feature/settings` как planned placeholder, хотя фактически модуль уже существует и `AppNavHost` использует реальные `SettingsScreen` и `LearnedChoicesScreen`.

## Риск

Агент может принять устаревший architecture document за абсолютную истину и:

* создать дублирующий модуль;
* вернуть placeholder;
* неверно оценить текущую Stage 2 готовность.

## Рекомендация

Перед implementation plan синхронизировать:

```text
docs/architecture.md
CLAUDE.md
current-status.md
roadmap.md
AppNavHost.kt
```

`CLAUDE.md` сейчас является более надёжным snapshot, чем отдельные устаревшие места architecture.

---

# 21. Главные архитектурные риски

## Риск 1. `LauncherViewModel` станет управляющим центром всей ОС

Уже сейчас он отвечает за много потоков:

* apps;
* command input;
* routing;
* suggestions;
* favorites;
* voice;
* pending routed action;
* learned resolution flow;
* navigation.

### Мера

Не дробить его сейчас искусственно.

Но при появлении execution plan или context engine выделить presentation coordinators/use cases, а не добавлять очередную ответственность напрямую.

---

## Риск 2. `core/ui` превратится в склад компонентов

Без taxonomy туда начнут попадать:

* random cards;
* feature-specific wrappers;
* duplicated buttons;
* несколько похожих status chips.

### Мера

Ввести component governance:

```text
Primitive
Component
Pattern
Feature composition
```

Каждый новый элемент должен иметь один уровень.

---

## Риск 3. Settings станет контейнером всей Stage 2

Learned Choices допустим в Settings сейчас.

Но Context, Memory и Agents нельзя вечно прятать туда.

### Мера

Создавать самостоятельную surface только после появления реальной пользовательской задачи, не заранее.

---

## Риск 4. Design обещает больше autonomy, чем реализовано

Execution Plan и Agent screens нельзя показывать как активные функции до реализации multi-step planning.

### Мера

Design System может специфицировать компоненты, но roadmap должен пометить их:

```text
AVAILABLE NOW
FOUNDATION
FUTURE CONTRACT
```

---

## Риск 5. Исламская эстетика станет декоративной

Самый опасный вариант:

* орнаменты;
* золотые рамки;
* полумесяцы;
* мечети;
* зелёный цвет повсюду;
* арабские мотивы без функции.

### Мера

Исламская идентичность основывается прежде всего на:

```text
order
clarity
trust
moderation
knowledge
privacy
responsibility
```

Декоративная геометрия допускается крайне ограниченно:

* boot screen;
* empty sacred space;
* subtle divider proportions;
* optional wallpaper.

---

# 22. Приоритеты редизайна

## P0 — Design doctrine

Зафиксировать:

```text
Islamic Order
Computational Precision
Agentic Intelligence
```

и продуктовые принципы.

## P1 — Token architecture

Переработать:

* colors;
* typography;
* spacing;
* shapes;
* strokes;
* motion;
* semantic roles.

## P2 — Universal Input

Сохранить текущую функциональность byte-for-byte, изменить только presentation contract.

## P3 — Action & Safety family

Объединить:

* proposal;
* confirmation;
* permission;
* external handoff;
* destructive warning.

## P4 — Home composition

Добавить:

* Shahada;
* prayer context;
* adaptive active-task area;
* более спокойную hierarchy.

## P5 — Memory language

Создать компоненты:

* learned preference;
* evidence;
* state;
* forget;
* local-only indicator.

## P6 — Activity language

Подготовить event timeline для:

* requested;
* proposed;
* confirmed;
* executed;
* failed;
* learned.

## P7 — Execution Stream

Спроектировать сейчас, реализовать тогда, когда появляется реальная multi-step execution model.

---

# 23. Рекомендуемый план миграции

## Фаза DS-0 — Documentation alignment

Без production UI изменений:

* обновить architecture;
* утвердить design doctrine;
* составить inventory текущих компонентов;
* классифицировать legacy AIL-0 elements.

## Фаза DS-1 — Foundation tokens

Presentation-only:

* dual typography;
* semantic colors;
* spacing;
* stroke;
* shape;
* motion;
* RTL-safe text styles.

## Фаза DS-2 — Primitive library

Создать или нормализовать:

* surface;
* text;
* divider;
* icon;
* status marker;
* button;
* chip;
* focus;
* loading.

## Фаза DS-3 — Existing component migration

Перевести без изменения поведения:

* SidrScaffold;
* SidrCommandPrompt;
* RouteChipRow;
* ConfirmActionCard;
* SectionHeader;
* AppTile;
* EmptyState;
* ErrorState.

## Фаза DS-4 — Home v1.1

Добавить исламский Home foundation:

* Shahada;
* prayer times;
* adaptive layout;
* active-state area.

Не менять router behavior.

## Фаза DS-5 — Memory & Activity patterns

Первое применение в Learned Choices.

## Фаза DS-6 — Agentic patterns

Только после появления реальных Stage 2 execution/context contracts.

---

# 24. Что нельзя делать агенту одним большим заданием

Не следует давать команду:

```text
Implement the entire SIDR Design System and redesign all screens.
```

Это почти гарантированно приведёт к:

* нарушению parity;
* скрытым behavior changes;
* появлению feature-specific logic в `core/ui`;
* огромному diff;
* смешению Material 3 и новой системы;
* дублированию компонентов;
* непроверяемым screenshot changes.

Правильная единица работы:

```text
один token layer
или
одна component family
или
одна screen migration
```

Каждая задача должна включать:

* scope;
* forbidden behavior changes;
* preview matrix;
* accessibility requirements;
* screenshot tests;
* full Gradle verification;
* device check для Home/confirmation/input.

---

# 25. Финальный вывод

## Архитектура

Архитектура SIDR уже достаточно сильна для продолжения к AI Framework.

Её не требуется фундаментально переписывать.

Особенно важно сохранить:

* pure domain;
* separate AI pipelines;
* rule-first routing;
* offline parity;
* explicit risk control;
* fail-closed privacy;
* single NavHost;
* no feature-to-feature dependencies;
* presentation-only `core/ui`.

## Дизайн

Текущий AIL-0 не следует выбрасывать.

Он содержит сильную ДНК:

* terminal precision;
* recognisable prompt;
* restrained geometry;
* green/amber identity;
* explicit confirmation;
* low visual dependency overhead.

Но его нужно вывести из состояния:

```text
ultra-cyberpunk terminal theme
```

в состояние:

```text
mature intent-driven operating system
```

## Исламский дух

Исламская основа SIDR должна определять не только Home, но и поведение всей системы:

```text
ясное намерение
ответственное действие
уважение к доверенным данным
умеренность
правдивое отображение состояния
контроль пользователя
спокойствие
знание и объяснимость
```

Шахада и prayer times станут явным духовным центром Home.

Но подлинная исламская идентичность SIDR будет проявляться в том, **как система обращается с человеком, его намерениями, данными, выбором и ответственностью**.

## Стратегическое решение

SIDR Design System v1.1 должен стать не редизайном Stage 1, а общим визуальным контрактом:

```text
AI Launcher
→ AI Framework
→ Agentic OS
```

без необходимости ещё одного полного визуального перезапуска на Stage 3.
