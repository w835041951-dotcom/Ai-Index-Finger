package com.aiindexfinger.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkflowSerializationTest {
    private val json = Json { prettyPrint = true }

    @Test
    fun `current schema version is always encoded`() {
        val encoded = json.encodeToString(
            Workflow.serializer(),
            Workflow(id = "schema", name = "Schema", steps = emptyList()),
        )

        assertTrue(encoded.contains("\"schemaVersion\": ${Workflow.CURRENT_SCHEMA_VERSION}"))
    }

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
                    Step.InputText(
                        "input",
                        selector,
                        "hello",
                        inputMethod = TextInputMethod.Paste,
                        value = Value.Template("Hello ${'$'}{mode}"),
                    ),
                Step.Click("click", selector),
                Step.RecordedClick(
                    id = "recorded-click",
                    x = 220,
                    y = 440,
                    selector = selector,
                    control = RecordedControl(
                        packageName = "com.example.target",
                        viewId = "com.example.target:id/search",
                        text = "订单",
                        contentDescription = "Search orders",
                        className = "android.widget.EditText",
                        bounds = RecordedBounds(120, 340, 320, 540),
                        clickable = true,
                        enabled = true,
                        longClickable = false,
                        scrollable = false,
                    ),
                    targetMode = RecordedClickTargetMode.Coordinates,
                ),
                Step.Tap("tap", 120, 340),
                Step.Scroll("scroll", selector, ScrollDirection.Backward),
                Step.ScrollUntil(
                    id = "scroll-until",
                    selector = selector,
                    direction = ScrollDirection.Forward,
                    stopCondition = ScrollUntilStopCondition.NodeAppears(selector),
                    maxScrolls = 12,
                ),
                Step.Swipe("swipe", 500, 1600, 500, 400, 350),
                Step.GlobalAction("back", SystemAction.Back),
                Step.Delay("wait", 1_000),
                Step.SetVariable("set", "mode", Value.Literal("ready")),
                Step.SetVariable("set-template", "message", Value.Template("Status: ${'$'}{mode}")),
                Step.ReadNodeText(
                    "read",
                    selector,
                    "captured",
                    NodeAttribute.ClassName,
                    postProcess = ReadNodeTextPostProcess(
                        trim = true,
                        caseTransform = ReadNodeTextCaseTransform.Lowercase,
                        regex = "(\\w+)",
                        regexGroup = 1,
                        splitDelimiter = "-",
                        splitIndex = 0,
                    ),
                    defaultValue = "fallback",
                ),
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
                Step.Label("label", "finish"),
                Step.JumpIf(
                    id = "jump",
                    targetLabel = "finish",
                    condition = Condition.Equals(Value.Variable("mode"), Value.Literal("ready")),
                ),
            ),
        )

        val encoded = json.encodeToString(Workflow.serializer(), workflow)
        val decoded = json.decodeFromString(Workflow.serializer(), encoded)

        assertEquals(workflow, decoded)
    }

    @Test
    fun `defaults recorded click target mode from selector availability`() {
        val control = RecordedControl(
            packageName = "com.example.target",
            bounds = RecordedBounds(0, 0, 20, 40),
            clickable = true,
            enabled = true,
            longClickable = false,
            scrollable = false,
        )
        val selector = NodeSelector("com.example.target", className = "android.widget.Button")

        assertEquals(
            RecordedClickTargetMode.Control,
            Step.RecordedClick("control", 10, 20, selector, control).targetMode,
        )
        assertEquals(
            RecordedClickTargetMode.Coordinates,
            Step.RecordedClick("coordinates", 10, 20, control = control).targetMode,
        )
        assertFailsWith<IllegalArgumentException> {
            Step.RecordedClick(
                "invalid",
                10,
                20,
                control = control,
                targetMode = RecordedClickTargetMode.Control,
            )
        }
    }

    @Test
    fun `older recorded click defaults fallback cause to null`() {
        val encoded = """{"id":"workflow","name":"Legacy recording","steps":[{"type":"recorded_click","id":"click","x":10,"y":20,"control":{"packageName":"com.example.target","bounds":{"left":0,"top":0,"right":20,"bottom":40},"clickable":true,"enabled":true,"longClickable":false,"scrollable":false}}]}"""

        val decoded = json.decodeFromString(Workflow.serializer(), encoded)
        val recordedClick = decoded.steps.single() as Step.RecordedClick

        assertEquals(RecordedClickTargetMode.Coordinates, recordedClick.targetMode)
        assertEquals(null, recordedClick.selector)
        assertEquals(10, recordedClick.x)
        assertEquals(20, recordedClick.y)
        assertEquals(null, recordedClick.fallbackCause)
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
    fun `round trips image click and defaults legacy matching and click settings`() {
        val imageClick = Step.ImageClick(
            id = "image",
            packageName = "com.example.target",
            templatePngBase64 = "aGVsbG8=",
            templateWidth = 24,
            templateHeight = 18,
            minimumScorePermille = 940,
            ambiguityMarginPermille = 30,
            scaleTolerancePermille = 100,
            selectionMode = ImageClickSelectionMode.AllMatches,
            maxClicks = 75,
            clickIntervalMillis = 1_500,
            timeoutMillis = 4_000,
            failurePolicy = FailurePolicy.Continue,
            templateClickX = 7,
            templateClickY = 11,
        )
        val workflow = Workflow(id = "image-workflow", name = "Image", steps = listOf(imageClick))
        val legacyJson = """{"schemaVersion":17,"id":"legacy","name":"Legacy","steps":[{"type":"image_click","id":"image","packageName":"com.example","templatePngBase64":"aGVsbG8=","templateWidth":24,"templateHeight":24,"minimumScorePermille":920,"ambiguityMarginPermille":25,"timeoutMillis":null,"failurePolicy":{"type":"stop"}}]}"""

        assertEquals(
            workflow,
            json.decodeFromString(Workflow.serializer(), json.encodeToString(Workflow.serializer(), workflow)),
        )
        val legacy = json.decodeFromString(Workflow.serializer(), legacyJson)
        assertEquals(17, legacy.schemaVersion)
        val legacyImageClick = legacy.steps.single() as Step.ImageClick
        assertEquals(0, legacyImageClick.scaleTolerancePermille)
        assertEquals(null, legacyImageClick.templateClickX)
        assertEquals(null, legacyImageClick.templateClickY)
    }

    @Test
    fun `normalizes schema nineteen image clicks to current defaults`() {
        val legacyJson = """{"schemaVersion":19,"id":"legacy","name":"Legacy","steps":[{"type":"image_click","id":"image","packageName":"com.example","templatePngBase64":"aGVsbG8=","templateWidth":24,"templateHeight":24}]}"""

        val normalized = json.decodeFromString(Workflow.serializer(), legacyJson).normalizedForCurrentSchema()
        val imageClick = normalized.steps.single() as Step.ImageClick

        assertEquals(Workflow.CURRENT_SCHEMA_VERSION, normalized.schemaVersion)
        assertEquals(ImageClickSelectionMode.BestMatch, imageClick.selectionMode)
        assertEquals(20, imageClick.maxClicks)
        assertEquals(200, imageClick.clickIntervalMillis)
    }

    @Test
    fun `rejects invalid image template metadata`() {
        assertFailsWith<IllegalArgumentException> {
            Step.ImageClick("image", "com.example", "aGVsbG8=", 11, 24)
        }
        assertEquals(
            1_024,
            Step.ImageClick("image", "com.example", "aGVsbG8=", 1_024, 12).templateWidth,
        )
        assertEquals(
            ImageTemplateConstraints.MAX_BASE64_LENGTH,
            Step.ImageClick(
                "image",
                "com.example",
                "x".repeat(ImageTemplateConstraints.MAX_BASE64_LENGTH),
                12,
                12,
            ).templatePngBase64.length,
        )
        assertFailsWith<IllegalArgumentException> {
            Step.ImageClick("image", "com.example", "x".repeat(Step.ImageClick.MAX_TEMPLATE_BASE64_LENGTH + 1), 24, 24)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.ImageClick("image", "com.example", "aGVsbG8=", 1_025, 24)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.ImageClick("image", "com.example", "aGVsbG8=", 24, 24, scaleTolerancePermille = 25)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.ImageClick("image", "com.example", "aGVsbG8=", 24, 24, maxClicks = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.ImageClick("image", "com.example", "aGVsbG8=", 24, 24, maxClicks = 101)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.ImageClick("image", "com.example", "aGVsbG8=", 24, 24, clickIntervalMillis = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.ImageClick("image", "com.example", "aGVsbG8=", 24, 24, clickIntervalMillis = 10_001)
        }
        assertFailsWith<IllegalArgumentException> {
            Step.ImageClick(
                "image",
                "com.example",
                "aGVsbG8=",
                24,
                24,
                templateClickX = 8,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Step.ImageClick(
                "image",
                "com.example",
                "aGVsbG8=",
                24,
                24,
                templateClickX = 24,
                templateClickY = 8,
            )
        }
    }

    @Test
    fun `rejects invalid scroll until limits`() {
        val selector = NodeSelector("com.example", text = "List")

        assertFailsWith<IllegalArgumentException> {
            Step.ScrollUntil(
                "scroll-until",
                selector,
                ScrollDirection.Forward,
                ScrollUntilStopCondition.MaxScrolls,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Step.ScrollUntil(
                "scroll-until",
                selector,
                ScrollDirection.Forward,
                ScrollUntilStopCondition.NoProgress,
                maxScrolls = 0,
            )
        }
    }
}
