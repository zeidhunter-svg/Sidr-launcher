# SIDR Component Library v1.1

## Islamic Order × Computational Precision × Agentic Intelligence

**Статус:** Proposed Component Specification
**Зависит от:** SIDR Design Doctrine & Foundation v1.1
**Область действия:** SIDR Launcher → SIDR AI Framework → SIDR Agentic OS
**Технологическая основа:** Jetpack Compose + Material 3 primitives
**Архитектурное размещение:** `:core:ui`

---

# 1. Назначение

SIDR Component Library v1.1 превращает дизайн-доктрину SIDR в систему:

* визуальных примитивов;
* интерактивных компонентов;
* agentic patterns;
* layout-контрактов;
* состояний;
* Compose API;
* accessibility-правил;
* preview matrix;
* screenshot tests;
* правил миграции существующего AIL-0 UI.

Библиотека не должна содержать бизнес-логику.

Она предоставляет presentation primitives, которые feature-модули связывают с:

* domain state;
* ViewModel;
* navigation events;
* action execution;
* memory;
* permissions;
* context;
* AI routing.

---

# 2. Архитектура библиотеки

```text
core/ui/
├── theme/
│   ├── SidrTheme.kt
│   ├── SidrColorScheme.kt
│   ├── SidrTypography.kt
│   ├── SidrShapes.kt
│   ├── SidrMotion.kt
│   └── SidrCompositionLocals.kt
│
├── token/
│   ├── SidrSpacing.kt
│   ├── SidrSizes.kt
│   ├── SidrStrokes.kt
│   ├── SidrElevation.kt
│   ├── SidrAlpha.kt
│   └── SidrDurations.kt
│
├── primitive/
│   ├── SidrSurface.kt
│   ├── SidrText.kt
│   ├── SidrDivider.kt
│   ├── SidrStatusMarker.kt
│   ├── SidrFocusRing.kt
│   ├── SidrIcon.kt
│   └── SidrProgress.kt
│
├── component/
│   ├── button/
│   ├── input/
│   ├── chip/
│   ├── row/
│   ├── navigation/
│   ├── feedback/
│   └── app/
│
├── pattern/
│   ├── sacred/
│   ├── intent/
│   ├── action/
│   ├── execution/
│   ├── memory/
│   ├── permission/
│   └── privacy/
│
├── layout/
│   ├── SidrScaffold.kt
│   ├── SidrSection.kt
│   ├── SidrContentColumn.kt
│   └── SidrResponsivePane.kt
│
├── semantics/
│   ├── SidrSemanticDescriptions.kt
│   └── SidrLiveRegionPolicy.kt
│
└── preview/
    ├── SidrPreview.kt
    ├── PreviewData.kt
    └── PreviewMatrix.kt
```

---

# 3. Уровни компонентов

## 3.1. Token

Не является composable.

Примеры:

* spacing;
* radius;
* stroke width;
* duration;
* semantic color.

---

## 3.2. Primitive

Минимальный визуальный строительный блок.

Примеры:

* surface;
* divider;
* status marker;
* text style;
* progress indicator.

Primitive не выражает продуктовую сущность.

---

## 3.3. Component

Самостоятельный интерактивный элемент.

Примеры:

* button;
* input;
* chip;
* settings row;
* app tile.

---

## 3.4. Pattern

Композиция компонентов, выражающая продуктовую семантику.

Примеры:

* Action Gate;
* Intent Surface;
* Execution Step;
* Memory Disclosure;
* Privacy Notice.

---

## 3.5. Feature composition

Остаётся внутри feature-модуля.

Примеры:

* Launcher Home;
* Learned Choices Screen;
* Assistant conversation;
* App Drawer;
* Settings screen.

`core/ui` не должен содержать готовый `LauncherScreen`.

---

# 4. Общие API-правила

Каждый публичный composable должен:

* начинаться с `Sidr`;
* принимать `modifier: Modifier = Modifier`;
* не принимать ViewModel;
* не принимать NavController;
* не импортировать domain models;
* не выполнять side effects без явного callback;
* поддерживать accessibility semantics;
* поддерживать font scale 2.0;
* поддерживать RTL;
* иметь preview;
* иметь стабильную модель состояния.

Пример:

```kotlin
@Composable
fun SidrPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable (() -> Unit))? = null,
)
```

Не допускается:

```kotlin
@Composable
fun SidrExecuteActionButton(
    action: LauncherAction,
    executor: ActionExecutor,
)
```

---

# 5. Theme API

## 5.1. SidrTheme

```kotlin
@Composable
fun SidrTheme(
    darkTheme: Boolean,
    accent: SidrAccent,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
)
```

## 5.2. Accent

```kotlin
enum class SidrAccent {
    Green,
    Amber,
}
```

## 5.3. Theme access

```kotlin
object SidrTheme {
    val colors: SidrSemanticColors
    val typography: SidrTypography
    val spacing: SidrSpacing
    val shapes: SidrShapes
    val motion: SidrMotion
    val sizes: SidrSizes
}
```

---

# 6. Primitive: SidrSurface

## Назначение

Базовая поверхность для всех SIDR-компонентов.

## API

```kotlin
enum class SidrSurfaceRole {
    Base,
    Raised,
    Interactive,
    Selected,
    System,
    Risk,
    Memory,
    Privacy,
    Overlay,
}

@Composable
fun SidrSurface(
    modifier: Modifier = Modifier,
    role: SidrSurfaceRole = SidrSurfaceRole.Base,
    shape: Shape = SidrTheme.shapes.standard,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
)
```

## Правила

* `onClick == null` означает неинтерактивную поверхность;
* interactive surface обязана иметь semantics;
* border не включается автоматически;
* elevation определяется ролью;
* ripple/indication должен соответствовать SIDR, а не default Material appearance.

---

# 7. Primitive: SidrText

## Назначение

Единая точка доступа к типографическим ролям.

```kotlin
enum class SidrTextRole {
    SacredArabic,
    SacredTranslation,
    Display,
    Title,
    Body,
    BodySecondary,
    Label,
    System,
    SystemStrong,
    Command,
    Metadata,
}
```

```kotlin
@Composable
fun SidrText(
    text: String,
    role: SidrTextRole,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
)
```

## Правила

* Arabic Sacred text получает отдельный `TextStyle`;
* `Metadata` использует System Mono;
* long-form content не использует Mono;
* truncation запрещён для confirmation consequences и Shahada;
* для технических ID truncation допускается при наличии expand/copy.

---

# 8. Primitive: SidrDivider

```kotlin
enum class SidrDividerRole {
    Subtle,
    Standard,
    Strong,
    Sacred,
}
```

```kotlin
@Composable
fun SidrDivider(
    modifier: Modifier = Modifier,
    role: SidrDividerRole = SidrDividerRole.Subtle,
)
```

## Правила

* `Sacred` не означает декоративный орнамент;
* divider не должен использоваться между каждым row;
* предпочтительно разделять группы spacing, а не линией.

---

# 9. Primitive: SidrStatusMarker

## Состояния

```kotlin
enum class SidrStatus {
    Idle,
    Queued,
    Running,
    Completed,
    Warning,
    Failed,
    Blocked,
    Cancelled,
    Local,
    Cloud,
}
```

## API

```kotlin
@Composable
fun SidrStatusMarker(
    status: SidrStatus,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    contentDescription: String? = null,
)
```

## Визуальные формы

```text
Idle       ○
Queued     ○
Running    ●
Completed  ✓
Warning    !
Failed     ×
Blocked    □
Cancelled  —
```

Форма и текст должны дополнять цвет.

---

# 10. Primitive: SidrProgress

## Варианты

```kotlin
sealed interface SidrProgressState {
    data object Indeterminate : SidrProgressState
    data class Determinate(val fraction: Float) : SidrProgressState
}
```

## Правила

* progress отображается только при реальной работе;
* indeterminate не должен работать бесконечно без timeout/error state;
* agentic execution по возможности использует step states вместо generic spinner;
* full-screen spinner на Home запрещён.

---

# 11. Buttons

## 11.1. SidrPrimaryButton

Главное действие.

Примеры:

* Continue;
* Execute;
* Save;
* Allow.

```kotlin
@Composable
fun SidrPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable (() -> Unit))? = null,
)
```

Высота:

```text
48 dp minimum
```

Shape:

```text
8 dp
```

---

## 11.2. SidrSecondaryButton

Для альтернативного действия.

```kotlin
@Composable
fun SidrSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable (() -> Unit))? = null,
)
```

---

## 11.3. SidrTertiaryButton

Текстовое действие.

Примеры:

* Cancel;
* Skip;
* Not now;
* View details.

---

## 11.4. SidrDestructiveButton

Для:

* Forget;
* Delete;
* Revoke;
* Stop automation.

Не должен использовать яркую красную заливку по умолчанию.

Предпочтительно:

* muted risk border;
* destructive text;
* отдельное confirmation состояние.

---

## 11.5. SidrTerminalAction

Наследник AIL-0 terminal action.

```kotlin
@Composable
fun SidrTerminalAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
)
```

Вид:

```text
[ CONFIRM ]
[ CANCEL ]
[ RETRY ]
```

Использование ограничено:

* confirmation;
* execution;
* developer tools;
* compact system surfaces.

Не использовать в обычных Settings.

---

# 12. Chips

## 12.1. SidrRouteChip

Показывает направление обработки Universal Input.

Примеры:

```text
APP
WEB
SITE
ASK
```

```kotlin
@Composable
fun SidrRouteChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable (() -> Unit))? = null,
)
```

---

## 12.2. SidrSuggestionChip

Предложение, не являющееся выбранным route.

Примеры:

* Open Maps;
* Continue task;
* Search web.

---

## 12.3. SidrActionChip

Выполняемое действие.

Примеры:

* Open;
* Retry;
* Change;
* Forget.

Action Chip должен ясно выглядеть интерактивным.

---

## 12.4. SidrStatusChip

Неинтерактивный статус.

Примеры:

```text
LOCAL
CLOUD
RUNNING
LEARNED
```

По умолчанию не имеет `onClick`.

---

## 12.5. SidrRiskChip

Статус риска.

```kotlin
enum class SidrRiskLevel {
    Safe,
    Confirm,
    Sensitive,
    Dangerous,
    Blocked,
    External,
}
```

Это presentation enum, не domain dependency.

Feature mapping выполняется с domain risk model.

---

## 12.6. SidrFilterChip

Используется в:

* Activity;
* Memory;
* Agents;
* App Drawer filters.

---

# 13. Universal Input

## 13.1. Роль

Главная точка ввода намерения.

Он объединяет:

* app filtering;
* launcher commands;
* web search;
* URL opening;
* assistant request;
* voice input;
* future agentic commands.

---

## 13.2. Состояния

```kotlin
enum class SidrUniversalInputState {
    Idle,
    Focused,
    Typing,
    Listening,
    Interpreting,
    Ambiguous,
    Proposed,
    Executing,
    Error,
    Disabled,
}
```

---

## 13.3. API

```kotlin
@Composable
fun SidrUniversalInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    state: SidrUniversalInputState = SidrUniversalInputState.Idle,
    placeholder: String,
    enabled: Boolean = true,
    voiceAvailable: Boolean = false,
    onVoiceClick: (() -> Unit)? = null,
    onClearClick: (() -> Unit)? = null,
    routeContent: (@Composable (() -> Unit))? = null,
    supportingText: String? = null,
)
```

---

## 13.4. Anatomy

```text
┌──────────────────────────────────┐
│ >  Input text               MIC  │
│    Supporting state             │
└──────────────────────────────────┘
```

Высота:

```text
56 dp minimum
64 dp preferred with supporting text
```

Padding:

```text
horizontal 16 dp
vertical 12 dp
```

---

## 13.5. Prompt marker

`>` остаётся постоянным идентификатором Universal Input.

Он:

* не озвучивается TalkBack как `greater than`;
* получает semantic description через container;
* меняет цвет при focus;
* не используется как spinner.

---

## 13.6. Block caret

Используется только при реальном focus.

При reduce motion допускается системный caret без кастомного мигания.

---

## 13.7. Listening state

Должен показывать:

* что микрофон активен;
* возможность остановить;
* live transcription, если доступна;
* permission error;
* no fake waveform.

---

# 14. Command Surface

## Назначение

Показывает исходный запрос пользователя в agentic flow.

```kotlin
@Composable
fun SidrCommandSurface(
    command: String,
    modifier: Modifier = Modifier,
    timestamp: String? = null,
    sourceLabel: String? = null,
)
```

Вид:

```text
COMMAND

> Open my banking app
```

Не требуется для каждого обычного launcher action.

Полезен в:

* Activity detail;
* execution detail;
* confirmation;
* agentic task.

---

# 15. Intent Surface

## Назначение

Показывает, как SIDR понял сложный запрос.

```kotlin
@Composable
fun SidrIntentSurface(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    confidenceLabel: String? = null,
    sourceLabel: String? = null,
    onEdit: (() -> Unit)? = null,
    onViewDetails: (() -> Unit)? = null,
)
```

Пример:

```text
INTENT

Open the official GitHub website

ROUTED BY AI
[ EDIT ]
```

## Правила

* не показывать для очевидных действий;
* confidence не показывать как псевдоточный процент обычному пользователю;
* допустимы human labels:

  * clear match;
  * likely match;
  * needs clarification.

---

# 16. Clarification Surface

## Назначение

Отдельное состояние, а не error.

```kotlin
data class SidrChoiceItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val leadingContent: (@Composable (() -> Unit))? = null,
)
```

```kotlin
@Composable
fun SidrClarificationSurface(
    question: String,
    choices: List<SidrChoiceItem>,
    onChoiceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
)
```

Правила:

* максимум 5 visible choices до scroll;
* каждый выбор имеет touch target 48 dp;
* selection не исполняется автоматически, если действие требует отдельного confirmation;
* uncertainty формулируется спокойно.

---

# 17. Action Proposal

## Назначение

Показывает AI-предложение, которое ещё не выполнено.

```kotlin
@Composable
fun SidrActionProposal(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    risk: SidrRiskLevel = SidrRiskLevel.Safe,
    targetLabel: String? = null,
    onExecute: () -> Unit,
    onCancel: (() -> Unit)? = null,
    onDetails: (() -> Unit)? = null,
)
```

Обязательное различие:

```text
PROPOSED
не равно
EXECUTED
```

---

# 18. SidrActionGate

## 18.1. Роль

Общая family для:

* confirmation;
* permission;
* sensitive data;
* external handoff;
* destructive action.

---

## 18.2. Типы

```kotlin
enum class SidrActionGateType {
    Confirmation,
    Permission,
    SensitiveData,
    ExternalHandoff,
    Destructive,
}
```

---

## 18.3. API

```kotlin
@Composable
fun SidrActionGate(
    type: SidrActionGateType,
    title: String,
    consequence: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    target: String? = null,
    reason: String? = null,
    privacyText: String? = null,
    details: (@Composable (() -> Unit))? = null,
    confirming: Boolean = false,
)
```

---

## 18.4. Anatomy

```text
REQUIRES CONFIRMATION

Open:
Türkiye Finans

Reason:
You asked to open your banking app.

Consequence:
This action leaves SIDR and opens
another application.

[ CANCEL ]        [ CONTINUE ]
```

---

## 18.5. Правила

* Cancel всегда видим;
* destructive confirm не ставится в позицию обычного primary action без различия;
* кнопки переходят в вертикальный layout при font scale;
* consequence нельзя скрывать под expand;
* progress после confirm не должен позволять повторный tap;
* dialog dismiss не должен считаться confirmation.

---

# 19. Permission Notice

## Назначение

Объясняет permission до Android system dialog.

```kotlin
@Composable
fun SidrPermissionNotice(
    permissionName: String,
    benefit: String,
    unavailableWithoutPermission: String,
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
    privacyNote: String? = null,
    settingsPath: String? = null,
)
```

Не должна изображать системный permission dialog.

---

# 20. Privacy Notice

```kotlin
enum class SidrProcessingLocation {
    Local,
    Cloud,
    Mixed,
}
```

```kotlin
@Composable
fun SidrPrivacyNotice(
    location: SidrProcessingLocation,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onDetails: (() -> Unit)? = null,
)
```

Примеры:

```text
LOCAL
Processed on this device.
```

```text
CLOUD
Your command and action schema will be sent
to the configured provider.
```

---

# 21. Execution Plan

## Назначение

Отображает план будущих шагов.

План ещё не означает execution.

```kotlin
data class SidrPlanStepUi(
    val id: String,
    val number: Int,
    val title: String,
    val description: String? = null,
    val risk: SidrRiskLevel? = null,
)
```

```kotlin
@Composable
fun SidrExecutionPlan(
    steps: List<SidrPlanStepUi>,
    modifier: Modifier = Modifier,
    title: String = "PLAN",
    onEdit: (() -> Unit)? = null,
)
```

Использовать только после появления реальной multi-step planning model.

---

# 22. Execution Step

## 22.1. Состояния

```kotlin
enum class SidrExecutionStepState {
    Queued,
    Running,
    AwaitingConfirmation,
    Completed,
    PartiallyCompleted,
    Failed,
    Cancelled,
    Skipped,
    Blocked,
}
```

---

## 22.2. UI model

```kotlin
data class SidrExecutionStepUi(
    val id: String,
    val title: String,
    val state: SidrExecutionStepState,
    val detail: String? = null,
    val toolLabel: String? = null,
    val durationLabel: String? = null,
    val errorMessage: String? = null,
)
```

---

## 22.3. API

```kotlin
@Composable
fun SidrExecutionStep(
    step: SidrExecutionStepUi,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpandChange: ((Boolean) -> Unit)? = null,
    trailingAction: (@Composable (() -> Unit))? = null,
)
```

---

# 23. Execution Stream

## Назначение

Основной agentic pattern будущего SIDR.

```kotlin
@Composable
fun SidrExecutionStream(
    steps: List<SidrExecutionStepUi>,
    modifier: Modifier = Modifier,
    title: String = "EXECUTION",
    activeStepId: String? = null,
    onCancel: (() -> Unit)? = null,
    onRetryStep: ((String) -> Unit)? = null,
)
```

Anatomy:

```text
EXECUTION

✓ Calendar checked
  LOCAL · 18 MS

● Calculating route
  Navigation agent

○ Preparing reminder
  Waiting
```

## Правила

* список отражает реальные состояния;
* шаг не становится Completed до реального результата;
* анимация status marker минимальна;
* текущий шаг имеет live region, но не объявляется при каждом незначительном update;
* Cancel относится ко всей задаче только при явном label.

---

# 24. Result Surface

## Состояния

```kotlin
enum class SidrResultType {
    Completed,
    PartiallyCompleted,
    Failed,
    Cancelled,
    Blocked,
    NoChange,
}
```

## API

```kotlin
@Composable
fun SidrResultSurface(
    type: SidrResultType,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    metadata: List<String> = emptyList(),
    primaryAction: SidrActionSpec? = null,
    secondaryAction: SidrActionSpec? = null,
    details: (@Composable (() -> Unit))? = null,
)
```

```kotlin
data class SidrActionSpec(
    val label: String,
    val onClick: () -> Unit,
)
```

---

# 25. Error Surface

```kotlin
enum class SidrErrorType {
    Recoverable,
    Permission,
    Offline,
    Unsupported,
    NotFound,
    Provider,
    Unknown,
}
```

```kotlin
@Composable
fun SidrErrorSurface(
    type: SidrErrorType,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onAlternative: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
)
```

Ошибка должна сообщать:

* что не произошло;
* безопасную причину;
* следующий шаг.

---

# 26. Empty State

```kotlin
@Composable
fun SidrEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: (@Composable (() -> Unit))? = null,
)
```

Запрещены:

* мультяшные иллюстрации без необходимости;
* чрезмерно эмоциональный текст;
* fake positivity.

---

# 27. Memory Components

## 27.1. Memory types

```kotlin
enum class SidrMemoryType {
    TemporaryContext,
    LearnedPreference,
    UserProvidedFact,
    SystemPolicy,
    AutomationState,
}
```

---

## 27.2. Memory status

```kotlin
enum class SidrMemoryStatus {
    Active,
    NeedsConfirmation,
    NeedsReconfirmation,
    Inactive,
    Expired,
    Deleted,
}
```

---

## 27.3. SidrMemoryItem

```kotlin
@Composable
fun SidrMemoryItem(
    title: String,
    value: String,
    type: SidrMemoryType,
    status: SidrMemoryStatus,
    modifier: Modifier = Modifier,
    evidence: String? = null,
    lastUsed: String? = null,
    localOnly: Boolean = true,
    onOpen: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onForget: (() -> Unit)? = null,
)
```

Anatomy:

```text
LEARNED PREFERENCE

“Open bank”
Türkiye Finans

Based on 3 confirmed choices
Last used 8 July 2026

LOCAL
[ EDIT ] [ FORGET ]
```

---

## 27.4. Memory Disclosure

Используется после meaningful learning event.

```kotlin
@Composable
fun SidrMemoryDisclosure(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    evidence: String? = null,
    onView: (() -> Unit)? = null,
    onForget: (() -> Unit)? = null,
)
```

Не показывать после каждого незначительного обновления.

---

## 27.5. Learned Resolution Item

Специализированная composition может жить в `feature/settings`, используя `SidrMemoryItem`.

`core/ui` не должен знать:

* target package;
* candidate fingerprint;
* confidence threshold;
* domain resolution policy.

---

# 28. Activity Components

## 28.1. Activity type

```kotlin
enum class SidrActivityType {
    Command,
    Proposal,
    Confirmation,
    Execution,
    Result,
    Memory,
    Permission,
    Privacy,
    Automation,
    Error,
}
```

---

## 28.2. SidrActivityItem

```kotlin
@Composable
fun SidrActivityItem(
    type: SidrActivityType,
    title: String,
    timestamp: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    status: SidrStatus? = null,
    metadata: List<String> = emptyList(),
    onClick: (() -> Unit)? = null,
)
```

---

## 28.3. SidrActivityTimeline

```kotlin
@Composable
fun SidrActivityTimeline(
    items: List<SidrActivityTimelineItem>,
    modifier: Modifier = Modifier,
    onItemClick: ((String) -> Unit)? = null,
)
```

Timeline marker должен отражать статус, а не быть декоративным.

---

# 29. Sacred Components

## 29.1. SidrShahadaHeader

```kotlin
@Composable
fun SidrShahadaHeader(
    arabicText: String,
    translationLineOne: String,
    translationLineTwo: String,
    modifier: Modifier = Modifier,
)
```

## Требования

* Arabic и translation — разные `Text`;
* Arabic использует RTL;
* translation использует LTR;
* никаких onClick;
* никаких badges;
* никаких animation;
* min horizontal padding 20 dp;
* min vertical breathing space 24 dp;
* max width для Arabic на tablet;
* translation может скрываться только по явной пользовательской настройке, не из-за нехватки высоты.

---

## 29.2. SidrPrayerSummary

```kotlin
data class SidrPrayerTimeUi(
    val name: String,
    val time: String,
    val isNext: Boolean = false,
)
```

```kotlin
@Composable
fun SidrPrayerSummary(
    prayers: List<SidrPrayerTimeUi>,
    modifier: Modifier = Modifier,
    locationLabel: String? = null,
    statusLabel: String? = null,
    onOpenDetails: (() -> Unit)? = null,
)
```

---

## 29.3. Layout variants

```kotlin
enum class SidrPrayerSummaryVariant {
    Compact,
    Standard,
    Expanded,
}
```

Compact:

```text
FAJR 04:12             ISHA 22:22
```

Standard:

```text
FAJR 04:12
DHUHR 13:08
ASR 17:03
MAGHRIB 20:41
ISHA 22:22
```

Предпочтительный Home вариант должен зависеть от доступного пространства, но все молитвы должны быть доступны через detail surface.

---

## 29.4. Prayer status

Необходимы labels:

```text
UPDATED
CACHED
MANUAL LOCATION
LOCATION UNAVAILABLE
METHOD REQUIRED
```

Не использовать alarming error styling для cached times.

---

# 30. App Components

## 30.1. SidrAppTile

Сохраняет существующий slot-based подход.

```kotlin
@Composable
fun SidrAppTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    selected: Boolean = false,
    badge: (@Composable (() -> Unit))? = null,
    onLongClick: (() -> Unit)? = null,
)
```

---

## 30.2. SidrAppRow

Для clarification, search и memory.

```kotlin
@Composable
fun SidrAppRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    packageLabel: String? = null,
    statusLabel: String? = null,
    trailingContent: (@Composable (() -> Unit))? = null,
)
```

---

## 30.3. SidrAlphabetHeader

Для App Drawer sticky headers.

```kotlin
@Composable
fun SidrAlphabetHeader(
    letter: String,
    modifier: Modifier = Modifier,
)
```

---

# 31. Rows

## 31.1. SidrNavigationRow

Для перехода на другой экран.

```kotlin
@Composable
fun SidrNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    leadingIcon: (@Composable (() -> Unit))? = null,
    status: String? = null,
)
```

---

## 31.2. SidrToggleRow

```kotlin
@Composable
fun SidrToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
)
```

Row click и switch click не должны вызывать двойное переключение.

---

## 31.3. SidrChoiceRow

Для radio/selection.

---

## 31.4. SidrStatusRow

Неинтерактивное текущее состояние.

Пример:

```text
LOCAL MODEL
Not installed
```

---

## 31.5. SidrDestructiveRow

Для:

* Clear memory;
* Revoke key;
* Delete history.

Не использовать рядом с обычными navigation rows без visual separation.

---

# 32. Section Components

## 32.1. SidrSectionHeader

```kotlin
@Composable
fun SidrSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    systemStyle: Boolean = false,
)
```

`systemStyle = true` включает короткий uppercase mono label.

Не использовать для всех заголовков.

---

## 32.2. SidrSection

```kotlin
@Composable
fun SidrSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
)
```

Spacing:

```text
header → content: 12 dp
section → next section: 24–32 dp
```

---

# 33. Navigation Components

## 33.1. SidrTopBar

```kotlin
@Composable
fun SidrTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
)
```

Не дублирует Android status bar.

---

## 33.2. SidrBottomNavigation

Не внедрять на Home до утверждения постоянной information architecture.

API проектируется заранее:

```kotlin
data class SidrNavigationItem(
    val id: String,
    val label: String,
    val icon: @Composable () -> Unit,
)
```

```kotlin
@Composable
fun SidrBottomNavigation(
    items: List<SidrNavigationItem>,
    selectedItemId: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```

---

## 33.3. SidrNavigationRail

Для tablet/foldable.

---

# 34. SidrScaffold

## Варианты

```kotlin
enum class SidrScaffoldVariant {
    Standard,
    Home,
    Focused,
    Detail,
    Immersive,
}
```

## API

```kotlin
@Composable
fun SidrScaffold(
    modifier: Modifier = Modifier,
    variant: SidrScaffoldVariant = SidrScaffoldVariant.Standard,
    topBar: (@Composable (() -> Unit))? = null,
    bottomBar: (@Composable (() -> Unit))? = null,
    floatingContent: (@Composable (() -> Unit))? = null,
    content: @Composable (PaddingValues) -> Unit,
)
```

---

## 34.1. Home

* edge-to-edge;
* Sacred Layer сверху;
* stable first frame;
* no forced top bar;
* ime-aware Universal Input;
* active process может стать sticky.

---

## 34.2. Focused

Для:

* assistant;
* confirmation;
* execution;
* voice interaction.

---

## 34.3. Detail

Для:

* memory detail;
* activity detail;
* agent detail;
* settings subsection.

---

# 35. Responsive Layout

## 35.1. SidrContentColumn

```kotlin
@Composable
fun SidrContentColumn(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 720.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    content: @Composable ColumnScope.() -> Unit,
)
```

На tablet текст не растягивается бесконечно.

---

## 35.2. SidrResponsivePane

```kotlin
@Composable
fun SidrResponsivePane(
    primary: @Composable () -> Unit,
    secondary: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
)
```

Использование:

* Home + execution;
* Memory list + detail;
* Activity list + detail.

---

# 36. Home Composition Contract

Home остаётся в `feature/launcher`.

Рекомендуемая композиция:

```text
SidrScaffold(Home)
└── SidrContentColumn
    ├── SidrShahadaHeader
    ├── SidrPrayerSummary
    ├── SidrUniversalInput
    ├── Input route chips / results
    ├── Active action or execution surface
    ├── Relevant context
    ├── Favorites
    └── All apps access
```

---

## 36.1. Home priorities

В состоянии покоя:

```text
Shahada
Prayer context
Universal Input
Relevant continuation
Launcher access
```

При вводе:

```text
Universal Input
Search/results
Route choices
```

При confirmation:

```text
Universal Input compact
SidrActionGate dominant
```

При execution:

```text
Universal Input compact
Execution Stream dominant
```

---

## 36.2. Не допускается

* одновременно показывать большое количество suggestions;
* держать Favorites выше active task;
* показывать app grid как основную часть Home;
* размещать random AI card;
* использовать молитвенные времена как фон.

---

# 37. Settings Composition Contract

Settings остаётся в `feature/settings`.

Рекомендуемая структура:

```text
INTELLIGENCE
Actions & confirmations
Memory
Privacy
Permissions
Personalization
Appearance
Device & performance
About
```

Использовать:

* `SidrSectionHeader`;
* `SidrToggleRow`;
* `SidrNavigationRow`;
* `SidrChoiceRow`;
* `SidrStatusRow`;
* `SidrDestructiveRow`.

Не создавать отдельную card для каждого row.

---

# 38. Preview Matrix

Каждый публичный компонент должен иметь минимум:

```text
Dark Green
Dark Amber
Light Green
Light Amber
```

Для сложных компонентов дополнительно:

```text
Default
Disabled
Selected
Loading
Error
Large font
RTL
Long content
```

---

## 38.1. Preview annotations

Рекомендуется создать:

```kotlin
@Preview(name = "Dark Green")
@Preview(name = "Dark Amber")
@Preview(name = "Light Green")
@Preview(name = "Light Amber")
annotation class SidrThemePreviews
```

Отдельно:

```kotlin
@Preview(fontScale = 1.5f)
@Preview(fontScale = 2.0f)
annotation class SidrFontScalePreviews
```

---

# 39. Screenshot Tests

Обязательные golden tests для:

* Universal Input;
* Action Gate;
* Clarification Surface;
* Result Surface;
* Memory Item;
* Shahada Header;
* Prayer Summary;
* Execution Step;
* Home composition.

Matrix не должна взрывать количество snapshot-файлов.

Минимальный обязательный набор:

```text
Dark Green default
Dark Amber default
Dark Green 2.0 font
Light Green default
RTL where relevant
```

---

# 40. Accessibility Tests

Проверять:

* touch target;
* role;
* state description;
* click labels;
* heading semantics;
* focus order;
* duplicate announcements;
* Arabic reading order;
* live region usage;
* disabled controls;
* color-independent meaning.

---

# 41. State Ownership

Компоненты не должны хранить business state.

Допустимо локальное состояние:

* pressed;
* expanded;
* focus;
* animation progress.

Недопустимо:

* pending action;
* execution state;
* selected application;
* learned preference;
* permission result;
* router state.

Эти состояния принадлежат feature/ViewModel.

---

# 42. Motion Implementation

Компоненты получают duration из `SidrTheme.motion`.

Не использовать hardcoded:

```kotlin
tween(300)
```

Использовать:

```kotlin
tween(SidrTheme.motion.standardMillis)
```

При reduce motion:

* duration сокращается;
* scale отключается;
* caret animation упрощается;
* execution state остаётся читаемым.

---

# 43. Content Rules

Component API не должен сам создавать пользовательские тексты.

Плохо:

```kotlin
SidrActionGate(action = action)
```

и внутри:

```text
Are you sure?
```

Хорошо:

```kotlin
SidrActionGate(
    title = uiState.title,
    consequence = uiState.consequence,
    ...
)
```

Feature или resources определяют текст.

---

# 44. Localization

Все production strings:

* находятся в resources feature-модуля или общего UI-ресурсного слоя;
* не hardcode внутри composable;
* поддерживают plural;
* поддерживают RTL;
* не строятся конкатенацией частей предложения.

System labels также локализуются, кроме внутренних developer surfaces.

---

# 45. Migration Map from AIL-0

## 45.1. `SidrCommandPrompt`

Преобразовать в:

```text
SidrUniversalInput
```

Сохранить:

* `>`;
* block caret;
* route integration;
* terminal precision.

Добавить:

* formal state model;
* supporting text;
* listening state;
* accessibility;
* dual typography.

---

## 45.2. `RouteChipRow`

Разделить на:

* `SidrRouteChip`;
* `SidrSuggestionChip`;
* `SidrActionChip`;
* `SidrFilterChip`;
* `SidrStatusChip`.

Сам container может остаться feature-level `FlowRow`.

---

## 45.3. `ConfirmActionCard`

Преобразовать в:

```text
SidrActionGate
```

Сохранить DF-4 visual DNA.

Добавить:

* gate type;
* consequence;
* privacy;
* external handoff;
* destructive state;
* large-font layout.

---

## 45.4. `SectionHeader`

Переработать в новый `SidrSectionHeader`.

Убрать обязательный uppercase.

---

## 45.5. `AppTile`

Сохранить slot-based API.

Привести spacing, selected state и semantics к v1.1.

---

## 45.6. `EmptyState` / `ErrorState`

Разделить на:

* Empty;
* Error;
* Blocked;
* Offline;
* Partial result.

---

## 45.7. CRT и scanline

Удалить из глобального theme.

Допустить только:

* optional visual mode;
* developer console;
* boot experiment.

---

# 46. Implementation Phases

## CL-0 — Inventory

* собрать все публичные `core/ui` composables;
* найти дубли;
* определить usage sites;
* зафиксировать screenshot baseline;
* не менять UI.

---

## CL-1 — Theme Foundation

* semantic colors;
* typography;
* shapes;
* spacing;
* motion;
* composition locals.

Behavior change запрещён.

---

## CL-2 — Primitives

* Surface;
* Text;
* Divider;
* StatusMarker;
* Progress;
* FocusRing.

---

## CL-3 — Core Controls

* buttons;
* chips;
* rows;
* section headers;
* navigation components.

---

## CL-4 — Universal Input Migration

* заменить presentation;
* сохранить ViewModel contract;
* сохранить command pipeline;
* сохранить route behavior;
* проверить IME/voice/a11y.

---

## CL-5 — Action & Safety

* Action Proposal;
* Action Gate;
* Permission Notice;
* Privacy Notice;
* Result;
* Error.

---

## CL-6 — Sacred Home Foundation

* Shahada Header;
* Prayer Summary;
* Home spacing;
* cached first frame;
* RTL tests.

Prayer data architecture должна планироваться отдельно от UI library.

---

## CL-7 — Memory

* Memory Item;
* Memory Disclosure;
* migration Learned Choices UI.

---

## CL-8 — Activity & Execution

* Activity Item;
* Execution Step;
* Execution Stream.

Execution Stream production use включается только при наличии реальной execution model.

---

# 47. Architecture Guards

Добавить проверки:

```text
core/ui не зависит от domain
core/ui не зависит от feature
core/ui не зависит от data
core/ui не импортирует NavController
core/ui не импортирует ViewModel
```

По возможности закрепить через Gradle dependency checks или static architecture tests.

---

# 48. Definition of Done для компонента

Компонент готов, когда:

```text
[ ] Назначение однозначно
[ ] API не содержит business dependency
[ ] Поддержаны основные состояния
[ ] Поддержаны dark/light
[ ] Поддержаны green/amber
[ ] Touch target ≥ 48 dp
[ ] Работает font scale 2.0
[ ] Работает RTL
[ ] Цвет не является единственным сигналом
[ ] Есть accessibility semantics
[ ] Есть preview
[ ] Есть screenshot test для ключевого паттерна
[ ] Reduce Motion учтён
[ ] Нет hardcoded production strings
[ ] Нет декоративной AI-активности
[ ] Нет feature-specific логики
[ ] Поведение существующего use case не изменилось
```

---

# 49. Component Governance

Новый компонент добавляется в `core/ui`, только если:

1. Используется минимум двумя feature-композициями; или
2. Является фундаментальным SIDR pattern; или
3. Нужен для единообразного safety/privacy UX.

Feature-specific composition остаётся в feature.

Пример:

```text
SidrActionGate
→ core/ui

LauncherRoutedActionPanel
→ feature/launcher
```

---

# 50. Итог

SIDR Component Library v1.1 должна сохранить сильную основу AIL-0:

* точность;
* терминальную ясность;
* узнаваемость;
* green/amber identity;
* явное подтверждение;
* низкую визуальную стоимость.

Но перевести её в зрелую систему:

```text
от:
terminal-themed components

к:
semantic operating-system components
```

Главные семейства библиотеки:

```text
Foundation
Universal Input
Intent
Action & Safety
Execution
Memory
Activity
Sacred Home
System Navigation
```

Компоненты должны позволить SIDR пройти путь:

```text
AI Launcher
→ AI Framework
→ Agentic OS
```

без разрушения архитектуры, без смешения UI с domain и без ещё одного полного визуального перезапуска.
