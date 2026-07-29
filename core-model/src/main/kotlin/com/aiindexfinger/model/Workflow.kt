package com.aiindexfinger.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Workflow(
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
        const val CURRENT_SCHEMA_VERSION = 13
    }
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
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step {
        init {
            require(packageName.isNotBlank()) { "Package name must not be blank" }
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
    @SerialName("repeat")
    data class Repeat(
        override val id: String,
        val times: Int,
        val steps: List<Step>,
        override val timeoutMillis: Long? = null,
        override val failurePolicy: FailurePolicy = FailurePolicy.Stop,
    ) : Step {
        init {
            require(times in 1..MAX_REPEAT_COUNT) { "Repeat count must be between 1 and $MAX_REPEAT_COUNT" }
        }

        companion object {
            const val MAX_REPEAT_COUNT = 10_000
        }
    }
}

@Serializable
enum class TextInputMethod {
    SetText,
    Paste,
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
) {
    init {
        require(packageName.isNotBlank()) { "Package name must not be blank" }
        require(listOf(viewId, text, contentDescription, className).any { !it.isNullOrBlank() }) {
            "A node selector requires at least one node attribute"
        }
        require(matchIndex in 0 until MAX_MATCH_COUNT) {
            "Match index must be between 0 and ${MAX_MATCH_COUNT - 1}"
        }
    }

    companion object {
        const val MAX_MATCH_COUNT = 1_000
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
}

@Serializable
enum class ScrollDirection {
    Forward,
    Backward,
}

@Serializable
enum class NodeAttribute {
    TextOrDescription,
    Text,
    ContentDescription,
    ViewId,
    ClassName,
}