# SIDR Design System v1

## 1. Назначение

SIDR Design System — визуальная и продуктовая система для Android-лаунчера, который развивается в сторону agentic OS.

Она должна поддерживать интерфейс, в котором пользователь взаимодействует не только с приложениями, но и с:

* командами;
* намерениями;
* агентами;
* системными действиями;
* памятью;
* контекстом;
* автоматизациями;
* локальными и облачными AI-моделями;
* разрешениями и подтверждениями;
* журналом выполнения.

Главная задача дизайн-системы:

> Сделать сложную агентную систему понятной, спокойной, прозрачной и управляемой.

SIDR не должен выглядеть как обычный Android-лаунчер, чат-бот, игровой интерфейс или декоративный cyberpunk-концепт.

Он должен восприниматься как самостоятельная интеллектуальная системная среда.

---

# 2. Основная концепция

## 2.1. Формула визуального языка

**Geometric restraint + terminal precision + agentic clarity**

Геометрическая сдержанность выражается через:

* строгую композицию;
* ясную иерархию;
* равномерные интервалы;
* спокойные пропорции;
* отсутствие визуального шума;
* функциональное использование цвета.

Терминальная точность выражается через:

* моноширинные системные подписи;
* четкие статусы;
* структурированные блоки;
* команды;
* логи;
* последовательности выполнения;
* технические индикаторы.

Agentic clarity выражается через:

* видимость намерения;
* объяснение плана;
* отображение выполняемых действий;
* прозрачность разрешений;
* понятное разделение предложения и фактического выполнения;
* возможность отмены и контроля.

---

# 3. Продуктовые принципы

## 3.1. Intent first

Главная сущность интерфейса — не приложение, а намерение пользователя.

SIDR должен показывать путь:

```text
USER INTENT
↓
INTERPRETATION
↓
PLAN
↓
PERMISSION
↓
EXECUTION
↓
RESULT
```

Запуск приложения является одним из возможных действий, а не основой всей системы.

---

## 3.2. Calm by default

В состоянии покоя интерфейс должен быть минимальным.

Система не должна постоянно показывать:

* лишние карточки;
* анимированные индикаторы;
* рекламные предложения;
* бесполезную аналитику;
* декоративные графики;
* множество активных цветов.

Интерфейс становится насыщеннее только во время выполнения реальной задачи.

---

## 3.3. Every element has a function

Каждый визуальный элемент должен отвечать хотя бы на один вопрос:

* Что происходит?
* Почему это происходит?
* Что система собирается сделать?
* Что уже было сделано?
* Требуется ли действие пользователя?
* Можно ли отменить действие?
* Каков результат?

Если элемент не отвечает ни на один из этих вопросов, его следует удалить.

---

## 3.4. System truth over visual effect

Интерфейс никогда не должен создавать иллюзию активности.

Запрещены:

* фиктивные AI-индикаторы;
* случайные потоки данных;
* фальшивые графики;
* анимации обработки, когда обработка не выполняется;
* псевдотехнические показатели без реальных данных.

Любой системный статус должен соответствовать реальному состоянию.

---

## 3.5. Visible agency

Пользователь всегда должен понимать:

* действует ли система сейчас;
* только предлагает действие или уже выполняет его;
* какое разрешение используется;
* что можно отменить;
* что будет необратимо;
* что было сохранено в память.

---

## 3.6. Progressive disclosure

Сложность показывается постепенно.

По умолчанию пользователь видит:

* краткое намерение;
* текущий статус;
* основной результат;
* главное действие.

Подробности открываются по запросу:

* reasoning summary;
* источники;
* технический лог;
* разрешения;
* execution graph;
* системные метаданные;
* модель;
* время выполнения.

---

## 3.7. Local first, cloud visible

Если действие выполняется локально, это можно показывать как преимущество.

Если данные отправляются в облако, интерфейс должен явно это обозначать.

Пример:

```text
PROCESSING LOCALLY
No data leaves this device
```

или:

```text
CLOUD MODEL REQUIRED
The selected text will be sent for processing
```

---

# 4. Архитектура интерфейса

## 4.1. Основные системные поверхности

SIDR v1 включает пять главных разделов:

1. Home
2. Agents
3. Memory
4. Activity
5. System

---

## 4.2. Home

Главная поверхность для:

* универсального ввода;
* голосового ввода;
* запуска приложений;
* команд;
* вопросов;
* контекстных действий;
* текущих задач;
* системных предложений.

Home не является панелью виджетов.

Это стартовая точка взаимодействия с системой.

---

## 4.3. Agents

Раздел для:

* доступных агентов;
* активных агентов;
* возможностей;
* текущих задач;
* разрешений;
* истории выполнения;
* ограничений;
* состояния локальных и облачных инструментов.

---

## 4.4. Memory

Раздел для:

* сохраненных предпочтений;
* выученных разрешений неоднозначностей;
* пользовательских фактов;
* контекстных правил;
* истории изменений;
* удаления записей;
* временной памяти;
* постоянной памяти.

---

## 4.5. Activity

Прозрачный журнал:

* команд;
* действий;
* открытых приложений;
* вызванных агентов;
* использованных инструментов;
* выданных подтверждений;
* отмененных операций;
* ошибок;
* автоматизаций.

---

## 4.6. System

Раздел для:

* AI-моделей;
* privacy;
* permissions;
* execution policy;
* automation policy;
* connectivity;
* battery behavior;
* developer options;
* accessibility;
* appearance;
* backup and export.

---

# 5. Навигационная модель

## 5.1. Нижняя навигация

Основной вариант для телефона:

```text
HOME   AGENTS   MEMORY   ACTIVITY   SYSTEM
```

На небольших экранах допускается четыре постоянных пункта:

```text
HOME   AGENTS   MEMORY   ACTIVITY
```

System открывается через отдельную кнопку в верхней области Activity или профиля системы.

Предпочтительный вариант SIDR v1 — пять пунктов, если подписи помещаются без нарушения читаемости.

---

## 5.2. Правила навигации

* Home всегда возвращает пользователя к основному вводу.
* Повторное нажатие на Home прокручивает экран вверх.
* Активная задача не должна исчезать при переходе между разделами.
* Текущий execution stream должен быть доступен через компактную системную панель.
* Назад закрывает локальный слой, а не отменяет задачу.
* Отмена задачи выполняется только явным действием Cancel.

---

## 5.3. Global Invocation

Universal Input должен вызываться:

* с Home;
* долгим нажатием системной кнопки;
* жестом;
* голосовой командой;
* плавающей системной панелью;
* аппаратной кнопкой, если устройство позволяет.

Он не должен отображаться как постоянная поисковая строка на каждом экране.

---

# 6. Сетка и пространственная система

## 6.1. Базовая единица

Базовый шаг:

```text
4 dp
```

Все интервалы строятся кратно 4.

---

## 6.2. Основные интервалы

```text
2 dp   — микроинтервал
4 dp   — плотный внутренний интервал
8 dp   — малый интервал
12 dp  — связанный контент
16 dp  — стандартный интервал
20 dp  — усиленное разделение
24 dp  — секция
32 dp  — крупная секция
40 dp  — экранная пауза
48 dp  — минимальная интерактивная высота
64 dp  — крупный системный блок
```

---

## 6.3. Горизонтальные поля

Телефон:

```text
16 dp — минимальное поле
20 dp — стандарт SIDR
24 dp — крупный экран
```

Предпочтительное значение:

```text
20 dp
```

---

## 6.4. Вертикальный ритм

Экран строится блоками:

```text
SYSTEM HEADER
24 dp
PRIMARY CONTENT
32 dp
SECONDARY CONTENT
24 dp
SYSTEM NAVIGATION
```

Интерфейс должен сохранять ощущение свободного пространства.

---

# 7. Цветовая система

## 7.1. Dark theme

Dark theme является основной темой SIDR.

### Background

```text
Background Primary       #090A0A
Background Secondary     #0D0F0E
Surface                  #111311
Surface Raised           #161916
Surface Interactive      #1A1E1A
Surface Selected         #22271F
```

### Borders

```text
Border Subtle            #252925
Border Default           #343934
Border Strong            #4B514A
```

### Text

```text
Text Primary             #E8E6DF
Text Secondary           #A3A69F
Text Tertiary            #727770
Text Disabled            #4F544F
Text Inverse             #10110F
```

### Primary accent

```text
Accent Primary           #D8A84E
Accent Strong            #E5B75C
Accent Dim               #8E6D32
Accent Surface           #2B2418
Accent Border            #5A4725
```

### Semantic colors

```text
Success                  #75A982
Success Surface          #15231A

Warning                  #D8A84E
Warning Surface          #2B2418

Error                    #C8756F
Error Surface            #2A1817

Info                     #7E9EAD
Info Surface             #162127

Processing               #D7D2C4
Processing Surface       #20211E
```

---

## 7.2. Light theme

Light theme поддерживается, но не определяет идентичность SIDR.

```text
Background Primary       #F4F2EC
Background Secondary     #ECE9E1
Surface                  #FAF8F3
Surface Raised           #FFFFFF
Surface Interactive      #E8E4DA

Text Primary             #171815
Text Secondary           #565A54
Text Tertiary            #777C74

Accent Primary           #A97724
Accent Strong            #8B611D
Accent Surface           #F2E4C7
```

---

## 7.3. Правила использования цвета

Цвет означает состояние, а не украшение.

Акцентный цвет используется для:

* активного действия;
* текущего выбранного состояния;
* подтверждения;
* важного системного фокуса;
* progress state;
* ключевого системного элемента.

Акцентный цвет не используется:

* для всех заголовков;
* для каждой иконки;
* для декоративных линий;
* для больших фоновых областей;
* для нескольких конкурирующих CTA.

---

## 7.4. Цвет и риск

```text
SAFE            neutral / primary
CONFIRM         amber
SENSITIVE       warm warning
DANGEROUS       muted red
BLOCKED         gray-red
```

Опасное действие никогда не обозначается только цветом.

Обязательно используются:

* текст;
* иконка;
* формулировка последствий;
* отдельное подтверждение.

---

# 8. Типографика

## 8.1. Шрифтовая модель

SIDR использует два типа шрифтов.

### Interface Sans

Для:

* заголовков;
* описаний;
* кнопок;
* длинного текста;
* ответов ассистента;
* настроек.

Рекомендуемый класс:

* Inter;
* Geist;
* IBM Plex Sans;
* Manrope;
* системный sans-serif.

### System Mono

Для:

* команд;
* системных статусов;
* логов;
* идентификаторов;
* параметров;
* времени;
* execution stream;
* технических подписей.

Рекомендуемый класс:

* JetBrains Mono;
* IBM Plex Mono;
* Roboto Mono.

---

## 8.2. Типографическая шкала

```text
Display Large
32 sp / 38 sp / Medium

Display
28 sp / 34 sp / Medium

Title Large
24 sp / 30 sp / Medium

Title
20 sp / 26 sp / Medium

Title Small
17 sp / 22 sp / Medium

Body Large
16 sp / 24 sp / Regular

Body
14 sp / 21 sp / Regular

Body Small
13 sp / 18 sp / Regular

Label
12 sp / 16 sp / Medium

Label Small
11 sp / 14 sp / Medium

System
13 sp / 19 sp / Mono Regular

System Small
11 sp / 16 sp / Mono Medium
```

---

## 8.3. Регистр

Uppercase используется только для:

* системных статусов;
* коротких названий секций;
* warning labels;
* execution states;
* технических меток.

Пример:

```text
ACTIVE PROCESS
REQUIRES CONFIRMATION
LOCAL MODEL
MEMORY UPDATED
```

Не следует писать весь интерфейс заглавными буквами.

---

# 9. Иконографика

## 9.1. Стиль

Иконки:

* линейные;
* геометрические;
* толщиной 1.5–2 dp;
* без декоративной детализации;
* без псевдообъема;
* без цветных иллюстраций.

---

## 9.2. Размеры

```text
16 dp — микроиконки
20 dp — стандарт
24 dp — навигация
28 dp — крупное действие
32 dp — системное состояние
```

---

## 9.3. Ключевые системные символы

```text
>     command
●     active
○     queued
✓     completed
!     warning
×     failed
↻     retry
□     permission required
```

Текстовые символы могут использоваться в mono-блоках, но не должны полностью заменять доступные иконки.

---

# 10. Форма и геометрия

## 10.1. Радиусы

SIDR не использует чрезмерно округлые элементы.

```text
0 dp   — терминальные блоки
4 dp   — статусные элементы
8 dp   — стандартные карточки
12 dp  — крупные интерактивные панели
16 dp  — модальные поверхности
24 dp  — только специальные плавающие элементы
```

Предпочтительный стандарт:

```text
8 dp
```

---

## 10.2. Контуры

Основной визуальный язык строится на:

* тонких границах;
* разделителях;
* небольшом различии поверхностей;
* контрасте текста;
* минимальных тенях.

Тени должны использоваться только для:

* модальных окон;
* floating surfaces;
* временных системных слоев;
* drag states.

---

# 11. Основные компоненты

## 11.1. System Header

Назначение:

* название текущей поверхности;
* системное состояние;
* контекст;
* дополнительное действие.

Структура:

```text
SECTION LABEL
Primary title
Secondary status
```

Не должен дублировать Android status bar.

---

## 11.2. Universal Input

Главный компонент SIDR.

Состояния:

* idle;
* focused;
* typing;
* listening;
* interpreting;
* ambiguous;
* ready;
* executing;
* error.

Структура:

```text
> Ask, search, open or automate
```

или при вводе:

```text
> Open my banking app
```

Элементы:

* prompt marker;
* текст;
* voice action;
* context indicator;
* submit;
* clear.

Universal Input не должен выглядеть как обычная поисковая строка.

Рекомендуемая высота:

```text
56–64 dp
```

---

## 11.3. Command Block

Используется для отображения команды пользователя.

```text
USER COMMAND

> Find the latest Ktor documentation
  and save the main changes
```

Command Block всегда визуально отделяется от ответа системы.

---

## 11.4. Intent Card

Показывает, как система поняла запрос.

```text
INTENT DETECTED

Research official Ktor documentation
and save a structured summary
```

Дополнительные действия:

```text
EDIT
VIEW DETAILS
```

---

## 11.5. Execution Plan

Показывает будущие действия до их выполнения.

```text
EXECUTION PLAN

01  Search official documentation
02  Compare current changes
03  Summarize relevant findings
04  Save result to memory
```

Статусы шагов:

```text
QUEUED
READY
RUNNING
COMPLETED
FAILED
SKIPPED
BLOCKED
```

---

## 11.6. Execution Stream

Центральный компонент agentic-интерфейса.

```text
EXECUTION

● SEARCH
  Reading official documentation

✓ CONTEXT
  4 relevant sources found

○ MEMORY
  Waiting for final result
```

Структура шага:

* status marker;
* agent/tool name;
* action;
* secondary detail;
* duration;
* optional expand action.

---

## 11.7. Agent Card

```text
RESEARCH AGENT
Active

Searches official sources and
creates structured summaries.

TOOLS
Web Search
Document Reader

PERMISSIONS
Network
Temporary context

[ OPEN ]  [ PAUSE ]
```

В карточке не должно быть декоративного аватара агента, если он не несет функции.

---

## 11.8. Memory Item

```text
LEARNED PREFERENCE

“Open bank” means:
Türkiye Finans

Learned from 4 confirmed choices

LAST USED
8 July 2026

[ EDIT ]  [ FORGET ]
```

Обязательно показывать:

* что сохранено;
* почему сохранено;
* когда использовалось;
* как удалить.

---

## 11.9. Activity Item

```text
OPENED APPLICATION

Türkiye Finans

Triggered by:
“Open bank”

Today, 14:32
12 ms
```

Activity item может иметь уровни детализации:

* compact;
* expanded;
* technical.

---

## 11.10. Permission Gate

```text
REQUIRES CONFIRMATION

SIDR wants to open:
Türkiye Finans

Reason:
You asked to open your banking app.

RISK
This action leaves SIDR and opens
another application.

[ CANCEL ]        [ CONTINUE ]
```

Правила:

* одно главное действие;
* ясное описание;
* отсутствие манипулятивного текста;
* отмена всегда доступна;
* опасное действие не подтверждается случайным свайпом.

---

## 11.11. Result Card

```text
TASK COMPLETED

Ktor documentation reviewed

4 important changes found
Summary saved to Memory

[ VIEW RESULT ]  [ OPEN MEMORY ]
```

---

## 11.12. Error Block

```text
ACTION FAILED

The requested application
is not installed.

[ OPEN PLAY STORE ]
[ CHOOSE ANOTHER APP ]
```

Ошибка должна содержать:

* что не произошло;
* почему;
* что можно сделать дальше.

---

## 11.13. System Notice

Для системных сообщений:

```text
LOCAL MODEL ACTIVE
No data leaves this device
```

или:

```text
NETWORK UNAVAILABLE
Cloud actions are paused
```

---

# 12. Кнопки

## 12.1. Primary Button

Для главного действия.

```text
CONTINUE
EXECUTE
SAVE
ALLOW
```

Высота:

```text
48 dp
```

---

## 12.2. Secondary Button

Для альтернативного действия.

```text
EDIT
VIEW DETAILS
OPEN LOG
```

---

## 12.3. Tertiary Button

Текстовая кнопка без заливки.

```text
CANCEL
SKIP
NOT NOW
```

---

## 12.4. Destructive Button

```text
DELETE
FORGET
REVOKE ACCESS
STOP AUTOMATION
```

Требует ясного контекста.

---

## 12.5. Terminal Action

Для execution-поверхностей:

```text
[ CONFIRM ]
[ CANCEL ]
[ RETRY ]
```

Используется ограниченно.

---

# 13. Карточки

## 13.1. Карточки не являются контейнером по умолчанию

Контент не должен автоматически помещаться в карточку.

Использовать карточку следует только когда нужно обозначить:

* отдельную сущность;
* отдельное состояние;
* действие;
* границу ответственности;
* раскрываемый блок.

---

## 13.2. Типы карточек

```text
Entity Card
Status Card
Action Card
Permission Card
Result Card
Memory Card
Agent Card
```

---

# 14. Состояния компонентов

Каждый интерактивный компонент должен иметь:

```text
Default
Focused
Pressed
Selected
Disabled
Loading
Error
Success
```

Для агентных компонентов также:

```text
Queued
Planning
Awaiting confirmation
Running
Paused
Cancelled
Completed
Failed
Partially completed
```

---

# 15. Motion System

## 15.1. Принцип

Анимация объясняет изменение состояния.

Она не используется как постоянное украшение.

---

## 15.2. Длительности

```text
100 ms — micro feedback
160 ms — selection
220 ms — surface transition
280 ms — expand/collapse
360 ms — large state change
```

---

## 15.3. Разрешенные анимации

* появление результата;
* изменение статуса шага;
* раскрытие execution details;
* переход queued → running;
* progress;
* подтверждение сохранения;
* изменение navigation state;
* голосовой уровень сигнала.

---

## 15.4. Запрещенные анимации

* постоянное свечение;
* бесконечные декоративные линии;
* бессмысленное мерцание;
* вращающиеся AI-символы без действия;
* фальшивые waveform;
* параллакс ради эффекта;
* сложные 3D-переходы.

---

## 15.5. Reduce Motion

При включенной системной настройке Reduce Motion:

* переходы сокращаются;
* scale-анимации отключаются;
* progress остается функциональным;
* состояние меняется через fade или мгновенно.

---

# 16. Звуки и тактильная обратная связь

## 16.1. Haptics

Используется для:

* принятой команды;
* опасного подтверждения;
* завершения задачи;
* ошибки;
* отмены;
* перехода к записи голоса.

Не использовать вибрацию на каждое касание.

---

## 16.2. System sound

Звуки должны быть:

* короткими;
* спокойными;
* нейтральными;
* без футуристических клише.

Пользователь должен иметь возможность полностью отключить их.

---

# 17. Accessibility

## 17.1. Контраст

Минимум:

```text
4.5:1 — обычный текст
3:1   — крупный текст
3:1   — интерактивные границы
```

---

## 17.2. Размер касания

Минимальный touch target:

```text
48 × 48 dp
```

---

## 17.3. Масштабирование текста

Интерфейс должен поддерживать системное увеличение шрифта без:

* обрезания статусов;
* наложения кнопок;
* исчезновения действий;
* нарушения порядка чтения.

---

## 17.4. Screen reader

Каждый execution status должен читаться как полноценная фраза.

Плохо:

```text
Circle. Search. Running.
```

Хорошо:

```text
Search agent is currently reading official documentation.
```

---

## 17.5. Цвет не является единственным носителем смысла

Статус всегда обозначается сочетанием:

* текста;
* формы;
* символа;
* цвета.

---

# 18. Content Design

## 18.1. Голос системы

SIDR говорит:

* спокойно;
* точно;
* без лишнего восторга;
* без антропоморфизации;
* без преувеличений;
* без давления.

---

## 18.2. Правильные формулировки

Хорошо:

```text
I found two matching applications.
Choose which one you want to open.
```

Плохо:

```text
I’m confused! Which app did you mean?
```

Хорошо:

```text
This action requires confirmation.
```

Плохо:

```text
Are you absolutely sure you want me to do this?
```

---

## 18.3. Статусы

Использовать конкретные глаголы:

```text
Searching
Reading
Comparing
Opening
Saving
Waiting
Checking
Executing
Completed
Cancelled
```

Избегать:

```text
Thinking
Working magic
Doing something
AI processing
```

---

## 18.4. Объяснение решений

Система должна объяснять выбор коротко:

```text
Selected because you chose this app
the last four times.
```

---

# 19. Главный экран SIDR

## 19.1. Состояние покоя

```text
SHAHADA

Prayer times

Primary greeting or context

Universal Input

Current context
Active task, if one exists

Bottom navigation
```

---

## 19.2. Правило для шахады

На главном экране используется абсолютная формулировка:

```text
لا إله إلا الله محمد رسول الله

THERE IS NO DEITY EXCEPT ALLAH
MUHAMMAD IS THE MESSENGER OF ALLAH
```

Она должна быть:

* визуально уважительной;
* без декоративных эффектов;
* без помещения в интерактивную карточку;
* без соседства с рекламой;
* без анимации;
* без использования как декоративного логотипа.

---

## 19.3. Prayer Times

Показываются автоматически на основе местоположения.

Используется локально принятый метод расчета или официальный источник.

Пример:

```text
FAJR  04:12
DHUHR 13:08
ASR   17:03
MAGHRIB 20:41
ISHA  22:22
```

В компактном состоянии допускается:

```text
FAJR 04:12              ISHA 22:22
```

---

# 20. Экран Agents

Структура:

```text
AGENTS

ACTIVE
2 processes

Active agent cards

AVAILABLE
Research
Calendar
Navigation
Communication
Memory

PERMISSIONS
Review access
```

Каждый агент показывает:

* статус;
* задачу;
* инструменты;
* разрешения;
* последнюю активность;
* возможность остановки.

---

# 21. Экран Memory

Структура:

```text
MEMORY

Overview
Learned resolutions
Preferences
Facts
Temporary context
Retention settings
Export
Delete
```

Ключевое правило:

> Пользователь должен иметь возможность увидеть и удалить все, что система хранит о нем.

---

# 22. Экран Activity

Структура:

```text
ACTIVITY

Today
Yesterday
This week

Command
Intent
Actions
Permissions
Result
Duration
```

Фильтры:

```text
ALL
ACTIONS
AGENTS
AUTOMATIONS
ERRORS
PRIVACY
```

---

# 23. Экран System

Разделы:

```text
AI
Models
Local processing
Cloud providers

EXECUTION
Confirmation policy
Dangerous actions
Background actions

PRIVACY
Permissions
Memory retention
Data export

AUTOMATION
Active automations
Limits
Quiet hours

INTERFACE
Theme
Typography
Motion
Haptics

DEVICE
Battery
Network
Storage
Accessibility

ABOUT
Version
Architecture
Open-source licenses
```

---

# 24. Responsive behavior

## 24.1. Small phones

* один столбец;
* 16–20 dp поля;
* bottom navigation;
* compact execution stream;
* минимум параллельных блоков.

---

## 24.2. Large phones and foldables

* master-detail;
* execution stream может находиться справа;
* navigation rail;
* agents и activity открываются без полной смены контекста.

---

## 24.3. Tablets

```text
Navigation rail
Primary workspace
Context / execution panel
```

---

# 25. Compose implementation model

## 25.1. Token structure

```text
SidrColors
SidrTypography
SidrSpacing
SidrShapes
SidrElevation
SidrMotion
SidrIcons
```

---

## 25.2. Component packages

```text
core/ui/theme
core/ui/tokens
core/ui/components
core/ui/components/command
core/ui/components/execution
core/ui/components/agent
core/ui/components/memory
core/ui/components/permission
core/ui/components/system
```

---

## 25.3. Минимальный набор Compose-компонентов

```text
SidrScaffold
SidrTopBar
SidrBottomNavigation
SidrSectionHeader
SidrUniversalInput
SidrCommandBlock
SidrIntentCard
SidrExecutionPlan
SidrExecutionStep
SidrExecutionStream
SidrAgentCard
SidrMemoryItem
SidrActivityItem
SidrPermissionGate
SidrResultCard
SidrErrorBlock
SidrSystemNotice
SidrPrimaryButton
SidrSecondaryButton
SidrTerminalAction
SidrDivider
SidrStatusChip
```

---

# 26. Правила MVP

В SIDR Launcher MVP обязательно реализовать:

* dark theme;
* typography system;
* spacing system;
* Home;
* Universal Input;
* bottom navigation;
* execution states;
* confirmation UI;
* result UI;
* error UI;
* activity log;
* memory management surface;
* accessibility basics.

Можно отложить:

* сложные motion transitions;
* light theme polish;
* tablet layout;
* sound system;
* customizable themes;
* agent marketplace;
* advanced data visualization.

---

# 27. Запрещенные визуальные паттерны

SIDR v1 не должен использовать:

* glassmorphism;
* neon cyberpunk;
* постоянные градиенты;
* крупные glowing-кнопки;
* декоративные AI-сферы;
* humanoid assistant avatars;
* excessive blur;
* огромные rounded cards;
* colorful dashboard layout;
* фальшивые system graphs;
* sci-fi radar;
* holographic effects;
* search bar on every screen;
* Material 3 без собственной адаптации;
* интерфейс, похожий на обычный чат.

---

# 28. Критерий готовности компонента

Компонент считается готовым, если:

1. Он имеет ясную функцию.
2. Поддерживает все основные состояния.
3. Имеет accessibility labels.
4. Работает с увеличенным текстом.
5. Не зависит только от цвета.
6. Поддерживает dark theme.
7. Имеет Compose preview.
8. Имеет screenshot test.
9. Не нарушает touch target.
10. Соответствует реальному системному состоянию.

---

# 29. Design Review Checklist

Перед принятием каждого экрана проверить:

```text
[ ] Понятно ли, что происходит?
[ ] Видно ли, действует система или только предлагает?
[ ] Есть ли понятная отмена?
[ ] Видно ли, что будет сохранено?
[ ] Есть ли лишние декоративные элементы?
[ ] Не дублируется ли системный Android UI?
[ ] Нужна ли каждая карточка?
[ ] Используется ли цвет функционально?
[ ] Читается ли экран без цвета?
[ ] Работает ли экран с крупным шрифтом?
[ ] Показан ли риск действия?
[ ] Не скрыта ли важная информация?
[ ] Соответствует ли статус реальному состоянию?
```

---

# 30. SIDR Design System v1 — итоговая идентичность

SIDR должен выглядеть как:

* спокойная системная среда;
* точный интеллектуальный инструмент;
* прозрачная agentic OS;
* интерфейс, ориентированный на намерения;
* система, которая уважает контроль пользователя;
* продукт с собственной визуальной идентичностью.

SIDR не должен выглядеть как:

* чат-бот;
* оболочка поверх Android;
* набор AI-виджетов;
* игровой HUD;
* концепт без реальной функциональности;
* копия существующего лаунчера.

Главный принцип:

> SIDR показывает не иллюзию интеллекта, а структуру реального действия.

Главный пользовательский цикл:

```text
INTENT
↓
UNDERSTANDING
↓
PLAN
↓
CONTROL
↓
EXECUTION
↓
RESULT
↓
MEMORY
```

Это является основой SIDR Launcher и будущей SIDR Agentic OS.
