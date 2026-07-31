package com.aiindexfinger.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkflowSerializationTest {
    private val json = Json { prettyPrint = true }

    @Test
    fun `round trips supported device actions`() {
        val selector = NodeSelector(
            packageName = "com.example.target",
            viewId = "com.example.target:id/search",
            text = "Order",
            textMatchMode = TextMatchMode.Contains,
            className = "android.widget.EditText",
            matchIndex = 2,
        )
        val workflow = Workflow(
            id = "workflow-actions",
            name = "Device actions",
            steps = listOf(
                Step.LaunchApp("launch", "com.example.target"),
                Step.InputText("input", selector, "hello", inputMethod = TextInputMethod.Paste),
                Step.Click("click", selector),
                Step.Tap("tap", 120, 340),
                Step.Scroll("scroll", selector, ScrollDirection.Backward),
                Step.Swipe("swipe", 500, 1600, 500, 400, 350),
                Step.GlobalAction("back", SystemAction.Back),
                Step.Delay("wait", 1_000),
                Step.SetVariable("set", "mode", Value.Literal("ready")),
                Step.SetVariable("set-template", "message", Value.Template("Status: ${'$'}{mode}")),
                Step.ReadNodeText("read", selector, "captured", NodeAttribute.ClassName),
                Step.InputText("input-variable", selector, text = "", variableName = "mode"),
                Step.IfElse(
                    id = "node-condition",
                    condition = Condition.NodeExists(selector),
                    whenTrue = listOf(Step.Click("conditional-click", selector)),
                ),
                Step.IfElse(
                    id = "condition",
                    condition = Condition.Equals(
                        Value.Variable("mode"),
                        Value.Literal("read"),
                        ComparisonOperator.Contains,
                    ),
                    whenTrue = listOf(
                        Step.Repeat(
                            id = "repeat",
                            times = 2,
                            steps = listOf(Step.WaitForNode("wait-node", selector, mustExist = false)),
                        ),
                    ),
                ),
            ),
        )

        val encoded = json.encodeToString(Workflow.serializer(), workflow)
        val decoded = json.decodeFromString(Workflow.serializer(), encoded)

        assertEquals(workflow, decoded)
    }

    @Test
    fun `round trips explicit draft and ready states`() {
        val draft = Workflow(
            id = "draft",
            name = "Draft",
            steps = emptyList(),
            state = WorkflowState.Draft,
        )
        val ready = Workflow(
            id = "ready",
            name = "Ready",
            steps = listOf(Step.Delay("delay", 1)),
            state = WorkflowState.Ready,
        )

        assertEquals(draft, json.decodeFromString(Workflow.serializer(), json.encodeToString(Workflow.serializer(), draft)))
        assertEquals(ready, json.decodeFromString(Workflow.serializer(), json.encodeToString(Workflow.serializer(), ready)))
    }

    @Test
    fun `round trips intent action and decodes legacy launch app`() {
        val actionWorkflow = Workflow(
            id = "direct-settings",
            name = "Direct Settings",
            steps = listOf(
                Step.LaunchApp(
                    "launch",
                    "com.android.settings",
                    intentAction = "android.settings.LOCATION_SOURCE_SETTINGS",
                ),
            ),
        )
        val legacyJson = """{"schemaVersion":14,"id":"legacy","name":"Legacy","steps":[{"type":"launch_app","id":"launch","packageName":"com.example","timeoutMillis":null,"failurePolicy":{"type":"stop"}}]}"""

        assertEquals(
            actionWorkflow,
            json.decodeFromString(Workflow.serializer(), json.encodeToString(Workflow.serializer(), actionWorkflow)),
        )
        val legacyLaunch = json.decodeFromString(Workflow.serializer(), legacyJson)
            .steps.single() as Step.LaunchApp
        assertEquals(null, legacyLaunch.intentAction)
    }

    @Test
    fun `round trips image click and decodes schema 13 workflows`() {
        val imageClick = Step.ImageClick(
            id = "image",
            packageName = "com.example.target",
            templatePngBase64 = "aGVsbG8=",
            templateWidth = 24,
            templateHeight = 18,
            minimumScorePermille = 940,
            ambiguityMarginPermille = 30,
            timeoutMillis = 4_000,
            failurePolicy = FailurePolicy.Continue,
        )
        val workflow = Workflow(id = "image-workflow", name = "Image", steps = listOf(imageClick))
        val legacyJson = """{"schemaVersion":13,"id":"legacy","name":"Legacy","steps":[]}"""

        assertEquals(
            workflow,
            json.decodeFromString(Workflow.serializer(), json.encodeToString(Workflow.serializer(), workflow)),
        )
        assertEquals(13, json.decodeFromString(Workflow.serializer(), legacyJson).schemaVersion)
    }

    @Test
    fun `rejects invalid image template metadata`() {
        assertFailsWith<IllegalArgumentException> {
            Step.ImageClick("image", "com.example", "aGVsbG8=", 11, 24)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.ImageClick("image", "com.example", "x".repeat(Step.ImageClick.MAX_TEMPLATE_BASE64_LENGTH + 1), 24, 24)
        }
    }
}
