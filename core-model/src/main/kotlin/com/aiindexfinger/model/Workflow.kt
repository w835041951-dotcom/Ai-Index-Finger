package com.aiindexfinger.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

object ImageTemplateConstraints {
    const val MIN_EDGE_PX = 12
    const val MAX_EDGE_PX = 1_024
    const val MAX_PNG_BYTES = 192 * 1_024
    const val MAX_BASE64_LENGTH = 256 * 1_024
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class Workflow(
    @EncodeDefault
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val steps: List<Step>,
    val defaultStepTimeoutMillis: Long = 10_000,
    val state: WorkflowState? = null,
) {
    init {
        require(schemaVersion > 0) { "Schema version must be positive" }
        require(id.isNotBlank()) { "Workflow id must not be blank" }
        require(name.isNotBlank()) { "Workflow name must not be blank" }
        require(defaultStepTimeoutMillis > 0) { "Default step timeout must be positive" }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 23
    }
}

/** Upgrades data from a supported schema without changing its recorded action configuration. */
fun Workflow.normalizedForCurrentSchema(): Workflow {
    require(schemaVersion <= Workflow.CURRENT_SCHEMA_VERSION) { "Workflow schema is not supported" }
    if (schemaVersion == Workflow.CURRENT_SCHEMA_VERSION) return this
    return copy(
        schemaVersion = Workflow.CURRENT_SCHEMA_VERSION,
        steps = steps.map(Step::normalizedForCurrentSchema),
    )
}

private fun Step.normalizedForCurrentSchema(): Step = when (this) {
    is Step.IfElse -> copy(
        whenTrue = whenTrue.map(Step::normalizedForCurrentSchema),
        whenFalse = whenFalse.map(Step::normalizedForCurrentSchema),
    )
    is Step.Repeat -> copy(steps = steps.map(Step::normalizedForCurrentSchema))
    else -> this
}

@Serializable
enum class WorkflowState {
    Draft,
    Ready,
}

@Serializable
sealed interface Step {
    val id: String
    val timeoutMillis: Long?
    val failurePolicy: FailurePolicy

    @Serializable
    @SerialName("launch_app")
    data class LaunchApp(
        override val id: String,
        val packageName: String,
        val intentAction: String? = null,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step {
        init {
            require(packageName.isNotBlank()) { "Package name must not be blank" }
            require(intentAction == null || intentAction.isNotBlank()) { "Intent action must not be blank" }
        }
    }

    @Serializable
    @SerialName("click")
    data class Click(
        override val id: String,
        val selector: NodeSelector,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step

    @Serializable
    @SerialName("recorded_click")
    data class RecordedClick(
        override val id: String,
        val x: Int,
        val y: Int,
        val selector: NodeSelector? = null,
        val control: RecordedControl,
        val targetMode: RecordedClickTargetMode = if (selector != null) {
            RecordedClickTargetMode.Control
        } else {
            RecordedClickTargetMode.Coordinates
        },
        val fallbackCause: RecordedClickFallbackCause? = null,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step {
        init {
            require(x >= 0 && y >= 0) { "Recorded click coordinates must not be negative" }
            require(targetMode != RecordedClickTargetMode.Control || selector != null) {
                "Control target mode requires a node selector"
            }
        }
    }

    @Serializable
    @SerialName("image_click")
    data class ImageClick(
        override val id: String,
        val packageName: String,
        val templatePngBase64: String,
        val templateWidth: Int,
        val templateHeight: Int,
        val minimumScorePermille: Int = 920,
        val ambiguityMarginPermille: Int = 25,
        val scaleTolerancePermille: Int = 0,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
        val templateClickX: Int? = null,
        val templateClickY: Int? = null,
        val selectionMode: ImageClickSelectionMode = ImageClickSelectionMode.BestMatch,
        val maxClicks: Int = 20,
        val clickIntervalMillis: Long = 200,
    ) : Step {
        init {
            require(packageName.isNotBlank()) { "Package name must not be blank" }
            require(templateWidth in ImageTemplateConstraints.MIN_EDGE_PX..ImageTemplateConstraints.MAX_EDGE_PX) {
                "Template width is out of range"
            }
            require(templateHeight in ImageTemplateConstraints.MIN_EDGE_PX..ImageTemplateConstraints.MAX_EDGE_PX) {
                "Template height is out of range"
            }
            require(
                templatePngBase64.isNotBlank() &&
                    templatePngBase64.length <= ImageTemplateConstraints.MAX_BASE64_LENGTH,
            ) {
                "Template image is missing or too large"
            }
            require(minimumScorePermille in 0..1_000) { "Image match score must be between 0 and 1000" }
            require(ambiguityMarginPermille in 0..1_000) { "Image ambiguity margin must be between 0 and 1000" }
            require(scaleTolerancePermille in SUPPORTED_SCALE_TOLERANCES) {
                "Image scale tolerance must be 0, 50, or 100"
            }
            require(maxClicks in 1..100) { "Image click count must be between 1 and 100" }
            require(clickIntervalMillis in 0..10_000) {
                "Image click interval must be between 0 and 10000 ms"
            }
            require((templateClickX == null) == (templateClickY == null)) {
                "Image click coordinates must both be set or both be absent"
            }
            templateClickX?.let {
                require(it in 0 until templateWidth) { "Image click X coordinate is outside the template" }
            }
            templateClickY?.let {
                require(it in 0 until templateHeight) { "Image click Y coordinate is outside the template" }
            }
        }

        companion object {
            const val MIN_TEMPLATE_SIZE = ImageTemplateConstraints.MIN_EDGE_PX
            const val MAX_TEMPLATE_SIZE = ImageTemplateConstraints.MAX_EDGE_PX
            const val MAX_TEMPLATE_BASE64_LENGTH = ImageTemplateConstraints.MAX_BASE64_LENGTH
            val SUPPORTED_SCALE_TOLERANCES = setOf(0, 50, 100)
        }
    }

    @Serializable
    @SerialName("long_click")
    data class LongClick(
        override val id: String,
        val selector: NodeSelector,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step

    @Serializable
    @SerialName("input_text")
    data class InputText(
        override val id: String,
        val selector: NodeSelector,
        val text: String,
        val variableName: String? = null,
        val inputMethod: TextInputMethod = TextInputMethod.SetText,
        val value: Value? = null,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step {
        init {
            require(variableName == null || variableName.isNotBlank()) {
                "Input variable name must not be blank"
            }
        }
    }

    @Serializable
    @SerialName("read_node_text")
    data class ReadNodeText(
        override val id: String,
        val selector: NodeSelector,
        val variableName: String,
        val attribute: NodeAttribute = NodeAttribute.TextOrDescription,
        val postProcess: ReadNodeTextPostProcess = ReadNodeTextPostProcess(),
        val defaultValue: String? = null,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step {
        init {
            require(variableName.isNotBlank()) { "Output variable name must not be blank" }
        }
    }

    @Serializable
    @SerialName("swipe")
    data class Swipe(
        override val id: String,
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int,
        val durationMillis: Long = 400,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step {
        init {
            require(startX >= 0 && startY >= 0 && endX >= 0 && endY >= 0) {
                "Swipe coordinates must not be negative"
            }
            require(durationMillis in 1..10_000) { "Swipe duration must be between 1 and 10000 ms" }
        }
    }

    @Serializable
    @SerialName("scroll")
    data class Scroll(
        override val id: String,
        val selector: NodeSelector,
        val direction: ScrollDirection,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step

    @Serializable
    @SerialName("scroll_until")
    data class ScrollUntil(
        override val id: String,
        val selector: NodeSelector,
        val direction: ScrollDirection,
        val stopCondition: ScrollUntilStopCondition,
        val maxScrolls: Int? = null,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step {
        init {
            require(maxScrolls == null || maxScrolls > 0) { "Maximum scroll count must be positive" }
            require(stopCondition !is ScrollUntilStopCondition.MaxScrolls || maxScrolls != null) {
                "Maximum scroll stop condition requires a maximum scroll count"
            }
        }
    }

    @Serializable
    @SerialName("tap")
    data class Tap(
        override val id: String,
        val x: Int,
        val y: Int,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step {
        init {
            require(x >= 0 && y >= 0) { "Tap coordinates must not be negative" }
        }
    }

    @Serializable
    @SerialName("global_action")
    data class GlobalAction(
        override val id: String,
        val action: SystemAction,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step

    @Serializable
    @SerialName("wait_for_node")
    data class WaitForNode(
        override val id: String,
        val selector: NodeSelector,
        val mustExist: Boolean = true,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step

    @Serializable
    @SerialName("delay")
    data class Delay(
        override val id: String,
        val durationMillis: Long,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step

    @Serializable
    @SerialName("set_variable")
    data class SetVariable(
        override val id: String,
        val name: String,
        val value: Value,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step

    @Serializable
    @SerialName("if_else")
    data class IfElse(
        override val id: String,
        val condition: Condition,
        val whenTrue: List<Step>,
        val whenFalse: List<Step> = emptyList(),
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step

    @Serializable
    @SerialName("label")
    data class Label(
        override val id: String,
        val name: String,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step {
        init {
            require(name.isNotBlank()) { "Label name must not be blank" }
        }
    }

    @Serializable
    @SerialName("jump_if")
    data class JumpIf(
        override val id: String,
        val targetLabel: String,
        val condition: Condition? = null,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step {
        init {
            require(targetLabel.isNotBlank()) { "Jump target label must not be blank" }
        }
    }

    @Serializable
    @SerialName("repeat")
    data class Repeat(
        override val id: String,
        val times: Int,
        val steps: List<Step>,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step {
        init {
            require(times > 0) { "Repeat count must be positive" }
        }
    }
}

@Serializable
enum class ImageClickSelectionMode {
    BestMatch,
    AllMatches,
}

@Serializable
enum class TextInputMethod {
    SetText,
    Paste,
}

@Serializable
enum class RecordedClickTargetMode {
    Control,
    Coordinates,
}

@Serializable
enum class RecordedClickFallbackCause {
    SourceUnavailable,
    SourceInvalid,
    HierarchyUnavailable,
    HierarchyChanged,
    HierarchyIncomplete,
    SourceNotUnique,
    SelectorNotUnique,
}

@Serializable
data class RecordedBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right > left && bottom > top) { "Recorded control bounds must have a positive size" }
    }
}

@Serializable
data class RecordedControl(
    val packageName: String,
    val viewId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val bounds: RecordedBounds,
    val clickable: Boolean,
    val enabled: Boolean,
    val longClickable: Boolean,
    val scrollable: Boolean,
) {
    init {
        require(packageName.isNotBlank()) { "Recorded control package name must not be blank" }
    }
}

@Serializable
data class AncestorSelector(
    val viewId: String? = null,
    val text: String? = null,
    val textMatchMode: TextMatchMode = TextMatchMode.Exact,
    val contentDescription: String? = null,
    val contentDescriptionMatchMode: TextMatchMode = TextMatchMode.Exact,
    val className: String? = null,
) {
    init {
        require(listOf(viewId, text, contentDescription, className).any { !it.isNullOrBlank() }) {
            "An ancestor selector requires at least one node attribute"
        }
    }
}

@Serializable
data class NodeSelector(
    val packageName: String,
    val viewId: String? = null,
    val text: String? = null,
    val textMatchMode: TextMatchMode = TextMatchMode.Exact,
    val contentDescription: String? = null,
    val contentDescriptionMatchMode: TextMatchMode = TextMatchMode.Exact,
    val className: String? = null,
    val matchIndex: Int = 0,
    val ancestor: AncestorSelector? = null,
) {
    init {
        require(listOf(viewId, text, contentDescription, className).any { !it.isNullOrBlank() }) {
            "A node selector requires at least one node attribute"
        }
        require(matchIndex >= 0) {
            "Match index must not be negative"
        }
    }
}

@Serializable
enum class TextMatchMode {
    Exact,
    Contains,
}

@Serializable
sealed interface Value {
    @Serializable
    @SerialName("literal")
    data class Literal(val value: String) : Value

    @Serializable
    @SerialName("variable")
    data class Variable(val name: String) : Value

    @Serializable
    @SerialName("template")
    data class Template(val template: String) : Value
}

@Serializable
sealed interface Condition {
    @Serializable
    @SerialName("equals")
    data class Equals(
        val left: Value,
        val right: Value,
        val operator: ComparisonOperator = ComparisonOperator.Equals,
    ) : Condition

    @Serializable
    @SerialName("node_exists")
    data class NodeExists(val selector: NodeSelector) : Condition
}

@Serializable
enum class ComparisonOperator {
    Equals,
    NotEquals,
    Contains,
    NotContains,
}

@Serializable
sealed interface FailurePolicy {
    @Serializable
    @SerialName("stop")
    data object Stop : FailurePolicy

    @Serializable
    @SerialName("continue")
    data object Continue : FailurePolicy

    @Serializable
    @SerialName("retry")
    data class Retry(val attempts: Int, val delayMillis: Long = 500) : FailurePolicy {
        init {
            require(attempts in 1..10) { "Retry attempts must be between 1 and 10" }
            require(delayMillis >= 0) { "Retry delay must not be negative" }
        }
    }
}

@Serializable
enum class SystemAction {
    Back,
    Home,
    Recents,
    Notifications,
    QuickSettings,
    PowerDialog,
    LockScreen,
}

@Serializable
enum class ScrollDirection {
    Forward,
    Backward,
}

@Serializable
sealed interface ScrollUntilStopCondition {
    @Serializable
    @SerialName("node_appears")
    data class NodeAppears(val selector: NodeSelector) : ScrollUntilStopCondition

    @Serializable
    @SerialName("node_disappears")
    data class NodeDisappears(val selector: NodeSelector) : ScrollUntilStopCondition

    @Serializable
    @SerialName("condition")
    data class ConditionMet(val condition: Condition) : ScrollUntilStopCondition

    @Serializable
    @SerialName("no_progress")
    data object NoProgress : ScrollUntilStopCondition

    @Serializable
    @SerialName("max_scrolls")
    data object MaxScrolls : ScrollUntilStopCondition
}

@Serializable
enum class NodeAttribute {
    TextOrDescription,
    Text,
    ContentDescription,
    ViewId,
    ClassName,
}

@Serializable
data class ReadNodeTextPostProcess(
    val trim: Boolean = false,
    val caseTransform: ReadNodeTextCaseTransform = ReadNodeTextCaseTransform.None,
    val regex: String? = null,
    val regexGroup: Int = 0,
    val splitDelimiter: String? = null,
    val splitIndex: Int = 0,
) {
    init {
        require(regex == null || regex.isNotBlank()) { "Read regex must not be blank" }
        require(regexGroup >= 0) { "Read regex group must not be negative" }
        require(splitDelimiter == null || splitDelimiter.isNotEmpty()) { "Split delimiter must not be empty" }
        require(splitIndex >= 0) { "Split index must not be negative" }
        regex?.let { Regex(it) }
    }
}

@Serializable
enum class ReadNodeTextCaseTransform {
    None,
    Lowercase,
    Uppercase,
}

fun ReadNodeTextPostProcess.applyTo(value: String): String? {
    var processed = if (trim) value.trim() else value
    regex?.let { pattern ->
        val match = Regex(pattern).find(processed) ?: return null
        if (regexGroup > match.groupValues.lastIndex) return null
        processed = match.groups[regexGroup]?.value ?: return null
    }
    splitDelimiter?.let { delimiter ->
        processed = processed.split(delimiter).getOrNull(splitIndex) ?: return null
    }
    return when (caseTransform) {
        ReadNodeTextCaseTransform.None -> processed
        ReadNodeTextCaseTransform.Lowercase -> processed.lowercase()
        ReadNodeTextCaseTransform.Uppercase -> processed.uppercase()
    }
}