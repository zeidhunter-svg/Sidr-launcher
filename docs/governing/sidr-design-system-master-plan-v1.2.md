# SIDR Design System Master Plan v1.2

## Islamic Order × Computational Precision × Agentic Intelligence

**Статус:** Governing Master Plan дизайн-трека
**Назначение:** единый управляющий план развития интерфейса SIDR
**Область:** SIDR Launcher → SIDR AI Framework → SIDR Agentic OS
**Текущая точка (2026-08-19):** DS-0…DS-11 + I18N-1 закрыты; дизайн-трек **между блоками** —
следующий DS-блок не назначен. Активен агентный трек:
[`sidr-agentic-master-plan-v1.0.md`](sidr-agentic-master-plan-v1.0.md). Точные статусы каждого блока
(`CODE-GREEN` / `DEVICE-ACCEPTED` / `CLOSED`, словарь Этапа 0.5) — в `CLAUDE.md` и
`ai-context/current-status.md`, а не здесь.
**Путь файла:** `docs/governing/sidr-design-system-master-plan-v1.2.md` — живой управляющий документ.
Переехал сюда 2026-08-19 (Этап 3) из `docs/design/`, объявленной ADR DS-0 архивом входных
материалов; см. [`README.md`](README.md).
**Доктрина:** проверяемая часть — §5/§5.1/§5.2 и §20.1 — извлечена в
[`sidr-doctrine-matrix-v1.0.md`](sidr-doctrine-matrix-v1.0.md). Здесь остались принципы прозой (§4).

---

# 0. Статус документа

Этот документ является **главной картой дизайн-трека SIDR**.

Он определяет:

* какие источники управляют дизайном;
* как трактовать визуальный артефакт с макетами;
* какие поверхности являются текущими, условными и будущими;
* порядок DS-блоков;
* связь DS-блоков с A1–A6;
* обязательные исламские дизайн-принципы;
* архитектурные prerequisites;
* границы `core/ui`, feature, domain и data;
* visual acceptance gates;
* screenshot-test matrix;
* запреты и stop conditions;
* процесс утверждения отклонений.

Этот документ **не является implementation plan одного большого задания**.

Каждый DS-блок всё равно проходит отдельный цикл:

```text
audit
→ design spec
→ implementation plan
→ implementation
→ architecture review
→ design review
→ tests
→ device acceptance
→ ADR
→ stop
```

Ни один агент не получает право реализовать весь Master Plan одним заданием.

---

# 1. Текущая точка проекта

## 1.1. Product state

SIDR прошёл Stage 1 — AI Launcher.

Уже существуют:

* надёжный offline launcher core;
* Universal Input;
* rule-first intent routing;
* BYOK cloud `CommandPlanner`;
* Action Registry;
* URL, web и Play Store routing;
* SAFE/CONFIRM gating;
* явное подтверждение рискованных действий;
* Assistant;
* voice input;
* App Drawer;
* Settings;
* Learned Resolutions;
* local persistence;
* privacy allow-list;
* secure secret storage.

Stage 2 развивает эти элементы в AI Framework:

```text
Tool / Capability layer
Context Engine
User Memory
```

Stage 3 добавляет:

```text
Agent Runtime
Execution Trace
Activity
Capability Grants
Automation
```

---

## 1.2. Design state

### DS-0 — DONE

Зафиксированы:

* provenance archive;
* living specs;
* конфликты старых дизайн-документов;
* soft-classic-grey identity;
* отказ от фальшивых agentic surfaces;
* authoritative DS sequence.

### DS-1 — DONE

Реализованы:

* `SidrColors`;
* dark/light soft-grey palettes;
* accent/status separation;
* mono/sans/serif typography roles;
* softened shapes;
* `SidrTheme.colors`;
* `SidrTheme.textStyles`;
* removal of global CRT scanlines;
* Roborazzi screenshot harness;
* initial dark/light goldens.

### Следующая точка (обновлено 2026-08-19)

Последовательность, которую этот раздел назначал — DS-2 → DS-3 → DS-4 → DS-6A → DS-6B → DS-7 —
**пройдена целиком**, плюс DS-8…DS-11 и I18N-1.

```text
Следующий DS-блок не назначен.
```

Дизайн-трек находится между блоками по решению владельца: активен агентный трек
([`sidr-agentic-master-plan-v1.0.md`](sidr-agentic-master-plan-v1.0.md)). Новый DS-блок заводится по
потребности — §12 (conditional future blocks) и §10 остаются в силе: **feature surface не может
заранее изображать несуществующий engine**, поэтому агентные поверхности разблокирует A4′/A5, а не
этот документ.

Что осталось незакрытым внутри пройденных блоков (не новый блок, а долг): DS-5 и I18N-1 —
`CODE-GREEN`, приёмка на устройстве владельцем не проводилась. Список ведётся в `CLAUDE.md`
§ Known debt.

---

# 2. Governing source hierarchy

Когда документы или артефакты расходятся, используется следующий порядок.

## 2.1. Уровень 1 — Owner decisions и ADR

Наивысший приоритет:

* прямое решение владельца;
* ADR в `ai-context/decisions.md`;
* явно зафиксированное supersession.

ADR не переписывается задним числом.

Новое решение добавляется новым ADR.

---

## 2.2. Уровень 2 — Living specifications

Текущую реализацию определяют:

* soft-classic-grey visual identity;
* этот Master Plan;
* Agentic OS Target Architecture A1–A6;
* отдельная утверждённая спецификация текущего DS-блока;
* отдельные capability specs.

---

## 2.3. Уровень 3 — Implementation plan блока

Implementation plan определяет:

* точные файлы;
* задачи;
* commit boundaries;
* тесты;
* verification commands.

Он не имеет права переопределять Master Plan или living spec без нового ADR.

---

## 2.4. Уровень 4 — Visual North-Star Artifact

Артефакт с макетами является:

> **Visual North-Star and composition reference, not an executable implementation plan.**

Он отвечает на вопросы:

* какое ощущение должен создавать SIDR;
* как выглядит композиция;
* как распределяются визуальные приоритеты;
* какие поверхности предполагаются в будущем.

Он не может самостоятельно разрешить:

* новую навигацию;
* новый engine;
* persistence;
* новый domain model;
* новый permission;
* fake Activity;
* fake Agent Runtime;
* fake Execution Stream.

---

## 2.5. Уровень 5 — Imported provenance archive

Семь исходных PDF сохраняют:

* историю;
* первоначальное видение;
* component ideas;
* acceptance ideas.

Они не переопределяют living specs и ADR.

---

## 2.6. Уровень 6 — Repository

Код является источником истины о том:

* что существует сейчас;
* какие API фактически используются;
* какие architecture boundaries фактически действуют;
* какие тесты реально доступны.

Если дизайн-документ ошибается относительно существующего класса или файла, агент адаптирует план к репозиторию и документирует расхождение.

Он не ломает репозиторий ради буквального соответствия старому документу.

---

# 3. Product design constitution

SIDR строится вокруг трёх равноправных оснований:

```text
Islamic Order
+
Computational Precision
+
Agentic Intelligence
```

---

## 3.1. Islamic Order

Исламский дух выражается через:

* ясность намерения;
* ответственность;
* доверие;
* правдивость состояния;
* умеренность;
* отсутствие манипуляции;
* контроль человека;
* уважение к данным;
* объяснимость;
* спокойствие;
* корректность религиозных функций.

Он не выражается автоматически через:

* зелёный цвет;
* золотые рамки;
* арки;
* орнаменты;
* полумесяцы;
* изображения мечетей;
* декоративную каллиграфию;
* стилизацию каждого экрана под исламское искусство.

---

## 3.2. Computational Precision

SIDR показывает разницу между:

```text
requested
matched
proposed
awaiting confirmation
executing
completed
partially completed
failed
cancelled
blocked
learned
```

SIDR не изображает процесс, которого нет.

SIDR не маскирует неопределённость.

SIDR не показывает завершение раньше реального результата.

---

## 3.3. Agentic Intelligence

Agentic UI появляется только поверх настоящих:

* tools;
* context;
* memory;
* plans;
* runtime states;
* traces;
* consent gates.

Никакой экран не становится «agentic» только благодаря словам:

```text
AGENT
PLANNING
THINKING
EXECUTING
```

Состояние должно существовать в engine и отображаться в UI один к одному.

---

# 4. Исламские дизайн-принципы

Названия в этом разделе используются как **product-design constraints**, а не как заявления по фикху или замена религиозного заключения.

---

## 4.1. Niyyah — ясность намерения

Система должна ясно показывать:

* что попросил пользователь;
* как SIDR это понял;
* где есть неоднозначность;
* что будет сделано.

Принцип не требует отдельной карточки для каждой команды.

Очевидное действие выполняется без лишнего UI.

Сложное или неоднозначное действие раскрывает interpretation.

---

## 4.2. Amanah — доверие и ответственность

Пользовательские данные, память, разрешения и контекст считаются доверенными системе.

Обязательные следствия:

* local-first;
* минимизация данных;
* outbound allow-list;
* отсутствие скрытого cloud handoff;
* память видима и удаляема;
* permission объясняется до запроса;
* отказ не ломает базовые функции;
* точная локация не используется вне заявленной функции.

---

## 4.3. Mizan — баланс

SIDR сохраняет баланс между:

* простотой и контролем;
* automation и consent;
* прозрачностью и перегрузкой;
* technical truth и понятностью;
* sacred content и utility UI.

Progressive disclosure обязателен.

Информация раскрывается по мере необходимости.

---

## 4.4. Ilm — знание и объяснимость

Пользователь может понять:

* почему выбран этот target;
* откуда взяты данные;
* какой метод использован;
* было ли действие local/cloud;
* что изменилось;
* что можно исправить или отменить.

Главное дизайн-выражение принципа:

```text
SidrProvenanceLine
```

---

## 4.5. Adl — последовательность и одинаковые правила

Одинаковый риск получает одинаковый UI независимо от того, кем предложено действие:

* rule engine;
* local model;
* cloud model;
* agent;
* automation;
* direct UI.

SAFE/CONFIRM/DANGEROUS нельзя стилизовать произвольно на разных экранах.

Risk semantics не зависят от accent или feature.

---

## 4.6. Haya — сдержанность

Запрещаются:

* dark patterns;
* агрессивные CTA;
* ложная срочность;
* манипулятивные формулировки;
* бесконечная AI-анимация;
* визуальное давление;
* навязчивое накопление истории;
* ненужное раскрытие личной информации.

---

## 4.7. Sukun — спокойствие

Home в состоянии покоя остаётся спокойным.

Он не заполняется:

* случайными suggestions;
* Activity dashboard;
* agent status;
* системными графиками;
* постоянными badges;
* рекламными блоками;
* псевдотехническими данными.

Активность появляется только при реальной активности.

---

## 4.8. Human authority

Пользователь владеет циклом действия.

Для agentic runtime это означает:

* Cancel;
* Pause;
* Edit;
* Confirm;
* Deny;
* Retry;
* Undo, когда реально возможно;
* Review aftermath.

AI не расширяет полномочия самостоятельно.

---

# 5. Islamic Principles Traceability Matrix

> **Извлечено 2026-08-19 (Этап 3.2 агентного трека) →
> [`sidr-doctrine-matrix-v1.0.md`](sidr-doctrine-matrix-v1.0.md).**
>
> Матрица переехала целиком, потому что у неё появился второй потребитель: половина её строк
> указывает на инженерные слои (Niyyah → A4, Ilm → A4–A5, Adl → A1/A4/A6, Human authority → A4/A6),
> а агент, работающий над рантаймом, не имеет причин открывать документ дизайн-трека.
>
> Что изменилось при переезде, кроме адреса: у каждого правила появился **стабильный ID** (`DOC-*`),
> тип проверки проставлен **из закрытого словаря** (раньше в этой колонке была проза), и добавлена
> колонка **«чем обеспечено»** — имя реального теста либо честное `<нет>` с адресатом долга. Это
> превращает документ из декларации в список долгов и делает его проверяемым: `DoctrineMatrixGuardTest`
> краснеет, если правило заявляет тест, которого в репозитории нет.
>
> **Здесь остались принципы прозой — §4.** Смысл принципа ищется там, проверка — в матрице.

## 5.1. Приоритет принципов (разрешение конфликтов)

> Переехало → [`sidr-doctrine-matrix-v1.0.md`](sidr-doctrine-matrix-v1.0.md) §2. Порядок разрешения
> (Human-authority + Amanah → Adl → Ilm → Mizan → Haya + Sukun) и эвристика одной строкой не
> изменились; пересказывать их здесь значило бы завести второй источник правды.

## 5.2. Verification vocabulary для «Обязательной проверки»

> Переехало → [`sidr-doctrine-matrix-v1.0.md`](sidr-doctrine-matrix-v1.0.md) §1. Словарь остался
> закрытым и по составу прежним (`screenshot` / `semantics` / `arch-guard` / `unit` / `device` /
> `manual`), но теперь **применён** — тип стоит в самой таблице, а не в прозе, и расширение словаря
> ловится guard-тестом. Правило может быть только `manual` — тогда оно так и помечается;
> аспирационное «should be tested» по-прежнему запрещено.

---


# 6. Visual identity contract

## 6.1. Soft-classic-grey

Основной характер:

```text
calm
mature
precise
restrained
timeless
systemic
```

Grey identity не означает отсутствие статуса.

Статусные цвета остаются фиксированными:

```text
success
attention
caution
danger
info
```

Pewter accent и semantic status — разные системы.

---

## 6.2. Tri-font model

> **Амендмент DS-11 B (2026-08-10, owner decision).** Территория mono сужена. Раньше mono владел всей
> interface shell — чипы, лейблы табов, статусы, section labels, settings labels. Именно это давало
> «технологичное» прочтение интерфейса при том, что заявленный характер §6.1 — `calm / mature /
> restrained`. Теперь mono оставлен только там, где моноширинность несёт смысл, а вся оболочка —
> sans. Правило закреплено тестом `TypographyRoleTest`, не только этим документом.

### Mono

JetBrains Mono используется **только** для terminal signature (§6.3):

* command line (`>` prompt, input);
* provenance line (`LOCAL · 14 MS`).

Обе роли требуют выравнивания по колонкам — это и есть обоснование моноширинности.

### Sans

**Bundled IBM Plex Sans** (OFL, `core/ui/OFL-IBMPlexSans.txt`) — вся остальная типографика:

* настоящая проза: assistant answer, permission explanation, длинное описание, error explanation,
  result summary, memory explanation;
* **interface shell (перенесено из Mono в DS-11 B):** labels, chips, tab labels, statuses, section
  labels, settings labels, metadata, timestamps, execution state.

Гарнитура **bundled**, а не `FontFamily.SansSerif`: оболочка не должна наследовать шрифт вендора, и
Latin/Cyrillic/Greek/Turkish обязаны приходить из одного известного файла — это предусловие блока i18n.

Tracking, унаследованный от mono (1.4–1.6 sp), при переносе на пропорциональную гарнитуру снижен до
0.6–0.8 sp: широкий трекинг компенсировал плотный фиксированный шаг mono, а на sans читается как
искусственно разрежённый текст.

**Известное ограничение.** Ни один sans-кандидат (IBM Plex Sans, Inter, Nunito Sans) не содержит
арабского — проверено по cmap реальных TTF. Арабский остаётся отдельной гарнитурой по необходимости,
см. Sacred ниже. Также в IBM Plex Sans отсутствует `◦` (U+25E6); маркеры превью используют `•`
(U+2022), присутствующий и в sans, и в mono, чтобы не зависеть от системного font fallback.

### Sacred

Sacred typography является отдельной ролью.

Текущий `FontFamily.Serif` — временная foundation implementation.

Окончательная Arabic-capable typography утверждается в DS-6A после:

* shaping test;
* RTL test;
* diacritics test;
* device test;
* license review;
* owner visual approval.

---

## 6.3. Terminal signature

Сохраняются:

* `>` prompt;
* block caret;
* press-invert interaction;
* mono-shell hierarchy;
* concise system labels;
* provenance line.

Не возвращаются глобально:

* CRT scanlines;
* phosphor glow;
* neon;
* fake terminal noise;
* constant flicker.

---

## 6.4. Geometric restraint

Основные формы:

```text
4dp compact
7dp chips
10dp standard
12dp modal/gate
```

Преобладают:

* spacing;
* tonal hierarchy;
* thin borders;
* restrained surfaces.

Не допускается card-around-everything.

---

# 7. Interaction law: ordinary action generates less UI

Это обязательный принцип всей Design System.

## Уровень 0 — direct ordinary action

Пример:

```text
tap Telegram
```

Результат:

* приложение открывается;
* нет toast;
* нет Result Card;
* нет Activity disclosure;
* нет AI animation.

---

## Уровень 1 — simple routed action

Пример:

```text
open Telegram
```

При уверенном safe match действие выполняется согласно текущей policy.

UI появляется только если это необходимо текущему контракту.

---

## Уровень 2 — ambiguity

Пример:

```text
open bank
```

Показывается выбор.

Не показывается drama, error или agent plan.

---

## Уровень 3 — consequential proposal

Пример:

```text
open external URL
```

Показывается Action Gate:

* target;
* reason;
* consequence;
* provenance;
* Cancel;
* Continue.

---

## Уровень 4 — multi-step agentic task

Показывается Execution UI только после появления A4 Runtime.

---

# 8. Information architecture

## 8.1. Current production surfaces

Утверждённые текущие поверхности:

```text
Home
App Drawer
Assistant
Settings
```

Они соответствуют реальному продукту.

---

## 8.2. Current nested surfaces

Внутри Settings или существующей навигации могут жить:

* Learned Choices;
* Permission Education;
* Assistant provider configuration;
* theme/system controls.

---

## 8.3. Conditional future surfaces

### Memory

Разрешается как самостоятельная поверхность после того, как A3 User Memory становится шире одной Settings-подстраницы.

### Activity

Разрешается после реальных:

* `ExecutionTrace`;
* redaction policy;
* ephemeral default;
* opt-in persistence;
* retention controls.

### Execution

Разрешается после A4 Agent Runtime.

### Agents / Automation

Разрешается после A4/A6, когда существуют:

* real runtime;
* grants;
* tool limits;
* automation policies;
* revocation;
* audit.

---

## 8.4. Navigation rule

Нельзя принимать постоянную 5-tab или 4-tab bottom navigation только по макету.

Перед изменением постоянной navigation IA требуется:

1. реальная новая primary surface;
2. usage rationale;
3. architecture prerequisite;
4. accessibility review;
5. owner decision;
6. отдельный ADR.

---

# 9. Visual Artifact Governance

## 9.1. Статус артефакта

Артефакт является:

```text
Visual North-Star
Composition Reference
State Reference
Mood Reference
```

Он не является:

```text
Architecture Plan
Navigation Authorization
Persistence Contract
Engine Specification
Implementation Order
```

---

## 9.2. Обязательная маркировка каждого frame

Каждый экран артефакта должен быть внесён в реестр и получить один статус:

```text
CURRENT TARGET
NEAR-TERM TARGET
CONDITIONAL FUTURE
CONCEPT ONLY
SUPERSEDED
REJECTED
```

---

## 9.3. Обязательные поля реестра

| Поле                | Содержание                   |
| ------------------- | ---------------------------- |
| Frame ID            | стабильное имя или номер     |
| Surface             | Home, Drawer, Memory и т. д. |
| Visual role         | что показывает макет         |
| Status              | current/future/rejected      |
| Required engine     | none, A2, A3, A4, A5, A6     |
| DS block            | где реализуется              |
| Existing backend    | какой state уже существует   |
| Missing backend     | чего пока нет                |
| Accepted elements   | что использовать             |
| Superseded elements | что не переносить            |
| Acceptance tests    | обязательные проверки        |

---

## 9.4. Первичная карта известных поверхностей

| Макет                   | Статус                | Prerequisite                | Реализация          |
| ----------------------- | --------------------- | --------------------------- | ------------------- |
| Home idle               | NEAR-TERM TARGET      | existing launcher state     | DS-4                |
| Home typing/search      | NEAR-TERM TARGET      | existing Universal Input    | DS-4                |
| Ambiguous app selection | NEAR-TERM TARGET      | existing ambiguity model    | DS-3/DS-4           |
| SAFE proposal           | NEAR-TERM TARGET      | existing routed proposal    | DS-3                |
| CONFIRM action          | NEAR-TERM TARGET      | existing confirmation state | DS-3                |
| Settings                | NEAR-TERM TARGET      | existing Settings           | DS-3                |
| App Drawer              | NEAR-TERM TARGET      | existing drawer             | DS-3/DS-4 follow-up |
| Sacred Header           | NEAR-TERM TARGET      | typography contract         | DS-6A               |
| Prayer summary          | CONDITIONAL           | DS-6B engine                | DS-6B integration   |
| Learned choices         | NEAR-TERM TARGET      | S2-1                        | DS-7                |
| Aliases UI              | CONDITIONAL NEAR-TERM | S2-2                        | DS-7                |
| Memory main surface     | CONDITIONAL FUTURE    | A3                          | after A3            |
| Execution Plan/Stream   | CONDITIONAL FUTURE    | A4                          | after A4            |
| Activity                | CONDITIONAL FUTURE    | A4/A5                       | after A5            |
| Agents                  | CONDITIONAL FUTURE    | A4/A6                       | after A6            |
| Automation              | CONDITIONAL FUTURE    | A6                          | after A6            |

---

# 10. Design-track and engine-track relationship

```text
DESIGN TRACK                     ENGINE TRACK

DS-0 provenance                 Stage 1 complete
DS-1 foundation                S2-1 Learned Resolutions
DS-2 primitives                S2-2 Aliases / A1 preparation
DS-3 controls                  A1 Tool layer
DS-4 Home                      A2 Context slices
DS-6A Sacred Header            A3 Memory growth
DS-6B Prayer capability        A4 Runtime later
DS-7 Memory UI                 A5 Activity later
Future execution UI            A6 Automation later
```

Это параллельные треки, но не независимые.

Правило:

> Design System может заранее создать generic primitive.
> Feature surface не может заранее изображать несуществующий engine.

---

# 11. DS-block master sequence

# DS-0 — Provenance and reconciliation

**Статус:** DONE

Результат:

* imported docs archived;
* governing hierarchy recorded;
* conflicts resolved;
* DS sequence established.

---

# DS-1 — Foundation tokens and visual harness

**Статус:** DONE

Результат:

* grey identity;
* semantic status;
* tri-font;
* shapes;
* theme accessors;
* global CRT removed;
* Roborazzi running.

---

# DS-2 — SIDR primitives

**Статус:** DONE (2026-07-11) — `core/ui/primitive/` (8 primitives) + `Strokes` token + preview gallery
goldens (dark/light/font-scale/RTL); keystone `SidrProvenanceLine` semantic + TalkBack; presentation-only,
additive. ADR: decisions.md "2026-07-11 — DS-2 primitives complete".

## Цель

Создать минимальные строительные элементы, выражающие identity и system truth.

## Обязательные primitives

```text
SidrSurface
SidrText
SidrDivider
SidrStatusMarker
SidrProvenanceLine
SidrFocusRing
SidrProgress
SidrSystemLabel
```

---

## Главный элемент DS-2 — SidrProvenanceLine

### Назначение

Единый visual primitive для origin и truth.

Примеры:

```text
LOCAL · 14 MS
ROUTED BY AI · OPENROUTER
LEARNED · 3 CONFIRMED CHOICES
DIYANET · UPDATED 2H AGO
CACHED · ISTANBUL · UTC+3
SYSTEM INTENT · EXTERNAL
```

### Правила

* faint mono;
* compact;
* не заменяет основное объяснение;
* не содержит сырой sensitive data;
* не используется как украшение;
* скрывается для ordinary actions;
* может переноситься;
* screen reader получает полноценное описание.

### API должен быть semantic

Предпочтительно:

```kotlin
SidrProvenanceLine(
    source = ...,
    details = ...,
)
```

а не передача произвольного форматированного набора декоративных token.

---

## DS-2 не должен

* менять Home;
* менять navigation;
* мигрировать Settings;
* менять ViewModel;
* создавать agent UI;
* добавлять молитвенные данные;
* добавлять новый persistence;
* импортировать domain в `core/ui`.

---

## DS-2 proof surface

Создать preview-only primitive gallery.

Не использовать production screen как экспериментальную площадку.

---

## DS-2 acceptance

* dark/light goldens;
* font scale 1.0, 1.5, 2.0;
* narrow 360dp;
* RTL для text primitives;
* status не зависит от accent;
* provenance доступен TalkBack;
* no feature dependency;
* full build gate green.

---

# DS-3 — Controls and semantic interaction patterns

## Цель

Создать рабочий control language SIDR.

## Компоненты

```text
SidrPrimaryButton
SidrSecondaryButton
SidrTertiaryButton
SidrDestructiveButton
SidrTerminalAction
SidrIconButton

SidrRouteChip
SidrSuggestionChip
SidrActionChip
SidrStatusChip
SidrRiskChip
SidrFilterChip

SidrNavigationRow
SidrToggleRow
SidrChoiceRow
SidrStatusRow
SidrDestructiveRow

SidrSectionHeader
SidrAlphabetHeader
SidrTopBar
SidrActionGate
```

---

## Press-invert contract

Press-invert является отличительной функциональной подписью.

### Должно быть

* мгновенная визуальная обратная связь;
* high-contrast selected/pressed state;
* корректная light/dark реализация;
* reduce-motion-safe;
* не зависит только от цвета;
* не создаёт layout shift.

### Не должно быть

* glow;
* scale bounce;
* prolonged animation;
* hidden focus;
* fake terminal effect.

---

## Action Gate contract

Одинаковый gate используется для:

* confirmation;
* permission boundary;
* external handoff;
* sensitive data;
* destructive action.

Различаются:

* title;
* consequence;
* provenance;
* status marker;
* confirm label.

Одинаковый risk не получает новую форму в каждой feature.

---

## DS-3 production proof surface

Рекомендуемый первый proof:

```text
Settings
```

Причина:

* реальный stable backend;
* много controls;
* низкий routing risk;
* можно доказать visual system без переделки Home.

Второй proof:

```text
existing ConfirmActionCard → SidrActionGate
```

Только при полном parity.

---

## DS-3 acceptance

* row не переключается дважды;
* Cancel не вызывает action;
* confirm вызывается один раз;
* consequence всегда видим;
* buttons stack при 2.0 font scale;
* risk label + marker + text;
* focus visible;
* screenshot matrix green;
* existing feature tests unchanged.

---

# DS-4 — Home shell and Universal Input composition

## Цель

Перевести Home в утверждённый intent-first layout без изменения routing semantics.

## Scope

* Home composition;
* Universal Input presentation;
* search-over-takes behavior;
* relevant continuation slot;
* favorites;
* All Apps access;
* active state slot;
* calm idle hierarchy.

---

## DS-4 Home hierarchy

До DS-6A:

```text
Quiet top space
Universal Input
Active or pending state
Relevant continuation
Favorites
All Apps
```

После DS-6A:

```text
Sacred Header
Universal Input
Active or pending state
Relevant continuation
Favorites
All Apps
```

После DS-6B:

```text
Sacred Header
Prayer provenance/context
Universal Input
Active or pending state
Relevant continuation
Favorites
All Apps
```

---

## Universal Input сохраняет

* `>` prompt;
* block caret;
* live app filtering;
* app/web/site/assistant routing;
* voice final transcript path;
* IME submit;
* clear behavior;
* offline path;
* router-off parity;
* current ViewModel contract.

---

## Home states

### Idle

* calm;
* no dashboard;
* no fake status;
* no persistent Activity;
* no random agent card.

### Typing

* input/results dominate;
* unrelated content recedes;
* no layout instability.

### Ambiguous

* clear selection;
* no error language;
* no auto-launch.

### SAFE proposal

* deliberate user action;
* no auto-run.

### CONFIRM

* Action Gate dominant;
* consequence visible.

### Completed ordinary action

* no Home result card.

---

## DS-4 exclusions

* no prayer calculation;
* no location access;
* no Memory main surface;
* no Activity;
* no Agents;
* no Execution Stream;
* no new bottom navigation;
* no new engine states.

---

## DS-4 acceptance

* all existing routing/VM tests pass unchanged;
* device acceptance for rule path, AI route, SAFE, CONFIRM, Cancel, offline;
* 360×800 golden;
* 2.0 font scale;
* light/dark;
* IME;
* rotation;
* no first-frame spinner;
* no material startup regression.

---

# DS-6A — Sacred Header

## Цель

Добавить духовный якорь Home уважительно и технически корректно.

## Утверждённый текст

Arabic:

```text
لا إله إلا الله محمد رسول الله
```

English:

```text
THERE IS NO DEITY EXCEPT ALLAH
MUHAMMAD IS THE MESSENGER OF ALLAH
```

Формулировка не переводится в личную форму.

---

## Правила Shahada

* не является brand slogan;
* не является кнопкой;
* не является dismissible card;
* не анимируется;
* не мигает;
* не используется как loading decoration;
* не обрезается;
* не соседствует с рекламой;
* не закрывается agentic surface без уважительной layout-логики;
* Arabic и English являются отдельными text nodes;
* correct RTL;
* no monospace Arabic;
* не используется decorative mosque arch по умолчанию.

---

## Arabic typography gate

До production integration обязательны:

* Arabic shaping screenshots;
* diacritics/ligature check;
* Samsung device smoke;
* emulator/API check;
* RTL reading order;
* TalkBack review;
* 1.0/1.5/2.0 font scale;
* landscape;
* owner visual approval.

Системный serif не считается автоматически окончательным решением.

---

## DS-6A не включает

* prayer times;
* calculation method;
* location;
* notifications;
* prayer alarms;
* adhan;
* Qibla;
* religious content feed.

---

# DS-6B — Prayer correctness capability

## Статус

Отдельный capability track.

Это не обычная UI-задача и не presentation-only DS-блок.

Перед кодом обязательны:

```text
brainstorm
→ religious/correctness requirements
→ architecture spec
→ privacy review
→ implementation plan
```

---

## Hard invariants

* время не показывается без provenance;
* пользователь видит authority/method;
* timezone и DST корректны;
* offline cache имеет freshness;
* stale state обозначается;
* failure visible;
* plausible-but-wrong time недопустимо;
* precise location остаётся local;
* prayer location не входит в `AiRequest`;
* permission denial не ломает launcher;
* manual location доступна;
* authority/method user-selectable;
* first frame не ждёт сеть.

---

## Required domain concepts

Не фиксируя преждевременно точные классы, capability должен различать:

```text
PrayerAuthority
CalculationMethod
PrayerLocation
TimeZoneState
PrayerSchedule
PrayerScheduleProvenance
Freshness
UnavailableReason
```

---

## Required UI states

```text
Verified current
Cached fresh
Cached stale
Manual location
Location unavailable
Method required
Authority unavailable
Timezone conflict
Calculation failed
```

---

## Acceptance

* provenance visible;
* offline behavior;
* timezone transition tests;
* DST tests;
* date boundary tests;
* location permission denied;
* manual override;
* cache expiry;
* device timezone change;
* no cloud AI egress;
* owner acceptance.

---

# DS-7 — Memory surfaces

## Цель

Перевести существующую memory-функциональность в единый visual language.

## Реальный backend

Первыми production consumers являются:

* Learned Resolutions;
* Aliases после S2-2.

---

## Components

```text
SidrMemoryItem
SidrMemoryEvidence
SidrMemoryStatus
SidrMemoryDisclosure
SidrForgetGate
```

---

## Memory rules

Каждый item должен отвечать:

* что сохранено;
* какой тип;
* откуда появилось;
* почему используется;
* local/cloud;
* когда использовалось;
* как изменить;
* как забыть.

Не показываются пользователю:

* internal fingerprint;
* raw database key;
* implementation confidence;
* технический schema ID без необходимости.

---

## Memory disclosure threshold

Disclosure показывается только при meaningful event:

* новая устойчиво выученная preference;
* изменённая preference;
* новый explicit alias;
* re-confirmation required.

Не показывается:

* после каждого обычного запуска;
* после каждого increment evidence;
* как постоянный toast.

---

## DS-7 architecture gate

`core/ui` не импортирует:

* `ResolutionPreference`;
* `Alias`;
* `MemoryPolicy`;
* Room entity.

Feature mapping создаёт presentation model.

---

## DS-7 acceptance

* active;
* stale;
* needs reconfirmation;
* empty;
* delete;
* delete-to-relearn;
* long names;
* local-only provenance;
* 2.0 font scale;
* TalkBack;
* current persistence tests unchanged.

---

# 12. Conditional future design blocks

## Execution surfaces

Разрешаются после A4.

Компоненты могут быть preview-specification раньше, но не production integration.

Нужны реальные:

* `ExecutionPlan`;
* `PlanStep`;
* `AgentSession`;
* `ExecutionTrace`;
* pause/cancel;
* consent state.

---

## Activity surface

Разрешается после A4/A5.

Hard rules:

* real events only;
* ephemeral default;
* persistence opt-in;
* retention cap;
* redaction;
* clear all;
* no raw sensitive command by default.

---

## Agents / Automation

Разрешаются после A4/A6.

До этого макеты имеют статус:

```text
CONDITIONAL FUTURE
```

Они не добавляются в navigation.

---

# 13. Component ownership

## `core/ui`

Может содержать:

* tokens;
* primitives;
* controls;
* generic semantic patterns;
* preview models;
* accessibility helpers.

Не может содержать:

* domain model;
* repository;
* ViewModel;
* NavController;
* routing;
* prayer calculation;
* memory policy;
* tool execution.

---

## Feature modules

Отвечают за:

* state mapping;
* strings;
* screen composition;
* callbacks;
* presentation models;
* interaction with ViewModel.

---

## Domain

Отвечает за:

* actual semantics;
* policies;
* state machines;
* ports;
* results;
* risk;
* memory;
* runtime.

---

## Data

Отвечает за:

* persistence;
* Android implementations;
* network;
* Room/DataStore;
* calculation/data providers.

---

# 14. Screenshot and visual acceptance matrix

## Базовая матрица каждого primitive/control

```text
Dark
Light
Disabled
Focused
Pressed/selected
Long content
Font scale 1.5
Font scale 2.0
```

---

## Дополнительно для RTL/sacred

```text
RTL
Arabic shaping
Mixed Arabic/LTR composition
Narrow screen
Landscape
```

---

## Основные device classes

Минимальный обязательный baseline:

```text
360dp × 800dp
```

Дополнительно для major screens:

* compact phone;
* standard phone;
* landscape;
* large font;
* real SM-A325F smoke.

---

## Golden policy

Goldens:

* фиксируют approved visual contract;
* не перезаписываются автоматически после failed verification;
* обновляются только после reviewer inspection;
* commit сопровождается описанием причины изменений.

---

# 15. Accessibility gates

Каждый major component обязан пройти:

* 48dp touch target;
* visible focus;
* TalkBack order;
* state description;
* no duplicate announcement;
* color-independent state;
* 2.0 font scale;
* RTL where relevant;
* reduce motion;
* meaningful click label.

Execution/live updates не объявляются при каждом техническом tick.

---

# 16. Performance gates

Запрещается добавлять на Home first frame:

* network wait;
* prayer calculation wait;
* remote font;
* blur;
* shader background;
* infinite animation;
* image decoding dependency;
* loading spinner вместо content;
* runtime-generated ornament.

Обязательные свойства:

* local theme available immediately;
* cached data only;
* stable layout;
* no white flash;
* no measurable cold-start regression без отдельного решения.

---

# 17. Privacy gates

Любой новый UI, показывающий context, memory или activity, обязан ответить:

1. Где данные возникли?
2. Где они хранятся?
3. Покидают ли устройство?
4. Как удалить?
5. Как отключить?
6. Каков retention?
7. Что редактируется?
8. Что будет при отказе?

Если ответ отсутствует, surface не готова к production.

---

# 18. No-fake-agency gate

Компонент или экран не может использовать production label:

```text
Planning
Agent
Execution
Activity
Automation
```

если backend не предоставляет соответствующее реальное состояние.

Допустимы:

* preview;
* design artifact;
* future contract documentation.

Недопустима production simulation.

---

# 19. Review process

Каждый блок проходит два независимых review.

## Architecture review

Проверяет:

* dependency edges;
* state ownership;
* domain purity;
* routing parity;
* privacy;
* persistence;
* feature boundaries;
* no fake backend.

## Design review

Проверяет:

* visual identity;
* Islamic principles;
* hierarchy;
* spacing;
* typography;
* interaction;
* accessibility;
* artifact mapping;
* screenshot goldens.

Один reviewer не должен автоматически считать visual acceptance доказанной только потому, что тесты зелёные.

---

# 20. Islamic Design Review Checklist

Каждый production screen обязан пройти ручную проверку:

```text
[ ] Ясно ли намерение?
[ ] Правдиво ли состояние?
[ ] Сохраняется ли контроль пользователя?
[ ] Нет ли манипуляции?
[ ] Не собираются ли лишние данные?
[ ] Видна ли существенная provenance?
[ ] Одинаков ли UI одинакового риска?
[ ] Не перегружен ли покой?
[ ] Не используется ли религия как декоративный бренд?
[ ] Уважительно ли представлен sacred content?
[ ] Не скрыта ли ошибка религиозно значимой функции?
[ ] Можно ли исправить или удалить сохранённое?
```

## 20.1. Calm budgets (числовые прокси Sukun/Haya)

> **Извлечено 2026-08-19 (Этап 3.2) → [`sidr-doctrine-matrix-v1.0.md`](sidr-doctrine-matrix-v1.0.md).**
>
> Пять пунктов бюджета стали строками матрицы, а не переехали блоком: `DOC-SKN-1` (idle animation
> budget = 0), `DOC-SKN-2` (accent budget ≤ 2–3, точное N по-прежнему в планах DS-2/DS-3),
> `DOC-SKN-3` (sacred-density), `DOC-SKN-4` (motion 100–360 мс + reduce-motion). Пятый — «нет
> status-только-цветом» — при разборе оказался не Sukun, а **Adl**: это правило про одинаковость
> прочтения статуса, а не про покой, и стал частью `DOC-ADL-2`, где у него уже есть тест
> (`ThemeTokensTest#accent_and_status_are_distinct_tokens`).
>
> Причина переезда та же, что у §5: счётчик без имени теста — это декларация. В матрице у каждого
> из пяти стоит либо тест, либо честное `<нет>` с указанием, чей это долг.

---

# 21. Change-control rules

Новый ADR обязателен, если предлагается:

* изменить grey identity;
* изменить tri-font strategy;
* убрать terminal signature;
* изменить primary navigation;
* построить future surface раньше engine;
* изменить Activity persistence default;
* изменить prayer correctness rules;
* изменить privacy egress;
* добавить religious decorative language;
* изменить Shahada wording;
* объединить accent и status;
* нарушить ordinary-action-less-UI principle.

---

# 22. Definition of Done для DS-блока

Блок завершён только если:

```text
[ ] Scope соответствует Master Plan
[ ] Есть отдельная implementation plan
[ ] Нет неразрешённого deviation
[ ] Architecture review approved
[ ] Design review approved
[ ] Islamic principles traced
[ ] Accessibility gates green
[ ] Screenshot verification green
[ ] Relevant unit tests green
[ ] Full test gate green
[ ] assembleDebug green
[ ] Device acceptance выполнен, если затронут production screen
[ ] ADR добавлен
[ ] Status docs обновлены
[ ] Working tree clean
[ ] Следующий блок не начат автоматически
```

---

# 23. Master milestone map

## Milestone M1 — Foundation

```text
DS-0 ✅
DS-1 ✅
DS-2
DS-3
```

Exit:

* primitives stable;
* controls stable;
* provenance primitive exists;
* press-invert stable;
* risk gate stable;
* screenshot matrix established.

---

## Milestone M2 — Launcher visual migration

```text
DS-4
```

Exit:

* Home migrated;
* Universal Input migrated;
* behavior parity proven;
* Settings/control proof complete;
* App Drawer visually coherent.

---

## Milestone M3 — Islamic Home foundation

```text
DS-6A
DS-6B
```

Exit:

* Shahada respectful and accessible;
* prayer capability correctness-proven;
* provenance visible;
* offline/private behavior proven.

---

## Milestone M4 — Memory language

```text
DS-7
```

Exit:

* Learned Resolutions and Aliases use common memory semantics;
* edit/delete/provenance visible;
* no hidden profiling.

---

## Milestone M5 — Agentic surfaces

Разблокируется только после:

```text
A4 Runtime
A5 Activity
A6 Grants/Automation
```

Exit:

* execution UI 1:1 with runtime;
* Activity real and privacy-bounded;
* Agents/Automation based on real grants.

---

# 24. Immediate next action (обновлено 2026-08-19)

Исходный текст этого раздела назначал следующей задачей спеку DS-2. **DS-2 закрыт 2026-07-11**, как и
всё, что за ним следовало. Раздел был устаревшим ~10 блоков и переписан в Этапе 3 агентного трека.

**Для дизайн-трека следующего действия нет.** Новый DS-блок не назначен и заводится по потребности,
обычным циклом (audit → design spec → implementation plan → … → ADR). Форма цикла в §0 и §22
остаётся действующей.

**Активная работа проекта — агентный трек.** Его следующее действие определяет
[`sidr-agentic-master-plan-v1.0.md`](sidr-agentic-master-plan-v1.0.md) §8: Этап 4.0 (инверсия флага
понимания), затем спека A0.

Если DS-блок всё же заводится, он обязан:

* перечитать актуальный `core/ui` — не полагаться на описания в этом документе;
* назвать правила доктрины (`DOC-*`), которые обязан закрыть, и получить для них имена тестов
  в [`sidr-doctrine-matrix-v1.0.md`](sidr-doctrine-matrix-v1.0.md);
* определить Roborazzi matrix и не менять production screens без отдельного решения;
* закончиться review и отдельным owner approval.

---

# 25. Финальная формула

```text
VISUAL ARTIFACT
показывает направление

MASTER PLAN
управляет последовательностью

LIVING SPEC
задаёт контракт блока

ENGINE ARCHITECTURE
разрешает реальные поверхности

IMPLEMENTATION PLAN
определяет кодовые шаги

ADR
фиксирует решение

TESTS + DEVICE ACCEPTANCE
доказывают результат
```

SIDR не строит интерфейс поверх обещаний.

SIDR строит:

```text
real capability
→ honest state
→ restrained surface
→ clear provenance
→ human control
```

Именно это является общим дизайн-контрактом пути:

```text
SIDR Launcher
→ SIDR AI Framework
→ SIDR Agentic OS
```
