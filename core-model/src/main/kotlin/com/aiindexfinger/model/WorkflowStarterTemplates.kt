package com.aiindexfinger.model

enum class WorkflowStarterTemplate { PauseThenHome, RepeatWithPause, VariableDecision }

enum class WorkflowExampleCategory {
    Fundamentals, Navigation, Repetition, Variables, Decisions,
    Timing, Resilience, Gestures, AppObservation, TextReading,
}

enum class WorkflowExampleCapability {
    Delay, GlobalNavigation, Variables, Conditions, Loops, Gestures, AppSelectors, NodeReading,
}

data class WorkflowExampleCompatibility(
    val minimumSchemaVersion: Int,
    val requiredCapabilities: Set<WorkflowExampleCapability>,
    val requiresConfiguration: Boolean,
    val autoRunAllowed: Boolean = false,
)

data class WorkflowExample(
    val id: String,
    val category: WorkflowExampleCategory,
    val learningPurposeId: String,
    val titleResourceKey: String,
    val descriptionResourceKey: String,
    val searchKeywordsEnglish: List<String>,
    val searchKeywordsSimplifiedChinese: List<String>,
    val compatibility: WorkflowExampleCompatibility,
    private val buildSteps: (() -> String) -> List<Step>,
) {
    fun create(name: String = titleResourceKey, newId: () -> String): Workflow = Workflow(
        id = newId(), name = name, state = WorkflowState.Draft, steps = buildSteps(newId),
    )
}

data class SearchableWorkflowExample(
    val example: WorkflowExample,
    val localizedTitle: String,
    val localizedDescription: String,
    val localizedCategory: String,
)

fun filterWorkflowExamples(
    examples: List<SearchableWorkflowExample>,
    query: String,
    category: WorkflowExampleCategory?,
): List<SearchableWorkflowExample> {
    val normalizedQuery = query.trim().lowercase()
    return examples.filter { searchable ->
        (category == null || searchable.example.category == category) &&
            (normalizedQuery.isEmpty() || listOf(
                searchable.localizedTitle,
                searchable.localizedDescription,
                searchable.localizedCategory,
                *searchable.example.searchKeywordsEnglish.toTypedArray(),
                *searchable.example.searchKeywordsSimplifiedChinese.toTypedArray(),
            ).any { value -> value.lowercase().contains(normalizedQuery) })
    }
}

object WorkflowStarterTemplates {
    const val PLACEHOLDER_PACKAGE = "com.example.configure_me"
    const val PLACEHOLDER_VIEW_ID = "com.example.configure_me:id/target"

    val catalog: List<WorkflowExample> by lazy { registry.map(ExampleSpec::toExample) }

    fun create(
        template: WorkflowStarterTemplate,
        name: String = template.defaultName,
        newId: () -> String,
    ): Workflow = requireNotNull(catalogById[template.catalogId]).create(name, newId)

    private val catalogById by lazy { catalog.associateBy(WorkflowExample::id) }

    private data class ExampleSpec(
        val id: String,
        val category: WorkflowExampleCategory,
        val purposeSlug: String,
        val chineseTerms: List<String>,
        val shape: Shape,
        val requiresConfiguration: Boolean = false,
    ) {
        fun toExample(): WorkflowExample {
            val key = "workflow_example_${category.resourceName}_$purposeSlug"
            return WorkflowExample(
                id = id,
                category = category,
                learningPurposeId = purposeSlug,
                titleResourceKey = "${key}_title",
                descriptionResourceKey = "${key}_description",
                searchKeywordsEnglish = listOf(purposeSlug.replace('_', ' '), category.resourceName.replace('_', ' ')),
                searchKeywordsSimplifiedChinese = chineseTerms,
                compatibility = WorkflowExampleCompatibility(
                    minimumSchemaVersion = Workflow.CURRENT_SCHEMA_VERSION,
                    requiredCapabilities = shape.capabilities,
                    requiresConfiguration = requiresConfiguration,
                ),
                buildSteps = shape::build,
            )
        }
    }

    private enum class Shape(val capabilities: Set<WorkflowExampleCapability>) {
        Delay(setOf(WorkflowExampleCapability.Delay)),
        DelaySequence(setOf(WorkflowExampleCapability.Delay)),
        SetThenDelay(setOf(WorkflowExampleCapability.Variables, WorkflowExampleCapability.Delay)),
        DelayThenSet(setOf(WorkflowExampleCapability.Delay, WorkflowExampleCapability.Variables)),
        RepeatDelay(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.Delay)),
        RepeatSequence(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.Delay)),
        ConditionalDelay(setOf(WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Delay)),
        AlternativeDelays(setOf(WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Delay)),
        ContinueDelay(setOf(WorkflowExampleCapability.Delay)),
        RetryDelay(setOf(WorkflowExampleCapability.Delay)),
        PauseThenHome(setOf(WorkflowExampleCapability.Delay, WorkflowExampleCapability.GlobalNavigation)),
        Back(setOf(WorkflowExampleCapability.GlobalNavigation)),
        Home(setOf(WorkflowExampleCapability.GlobalNavigation)),
        Recents(setOf(WorkflowExampleCapability.GlobalNavigation)),
        PauseThenBack(setOf(WorkflowExampleCapability.Delay, WorkflowExampleCapability.GlobalNavigation)),
        PauseThenRecents(setOf(WorkflowExampleCapability.Delay, WorkflowExampleCapability.GlobalNavigation)),
        HomeThenPause(setOf(WorkflowExampleCapability.GlobalNavigation, WorkflowExampleCapability.Delay)),
        BackThenPause(setOf(WorkflowExampleCapability.GlobalNavigation, WorkflowExampleCapability.Delay)),
        RecentsThenPause(setOf(WorkflowExampleCapability.GlobalNavigation, WorkflowExampleCapability.Delay)),
        RepeatBack(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.GlobalNavigation)),
        LegacyRepeat(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.Delay)),
        RepeatThenDelay(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.Delay)),
        DelayThenRepeat(setOf(WorkflowExampleCapability.Delay, WorkflowExampleCapability.Loops)),
        NestedRepeat(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.Delay)),
        RepeatSet(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.Variables)),
        RepeatConditional(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Delay)),
        TwoRepeats(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.Delay)),
        RepeatSetThenDelay(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.Variables, WorkflowExampleCapability.Delay)),
        RepeatContinue(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.Delay)),
        SetLiteral(setOf(WorkflowExampleCapability.Variables)),
        SetTwo(setOf(WorkflowExampleCapability.Variables)),
        SetTemplate(setOf(WorkflowExampleCapability.Variables)),
        CopyVariable(setOf(WorkflowExampleCapability.Variables)),
        BranchSet(setOf(WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Variables)),
        BranchSetBoth(setOf(WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Variables)),
        SetDelayCopy(setOf(WorkflowExampleCapability.Variables, WorkflowExampleCapability.Delay)),
        LegacyDecision(setOf(WorkflowExampleCapability.Variables, WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Delay)),
        NotEqualBranch(setOf(WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Delay)),
        ContainsBranch(setOf(WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Delay)),
        NotContainsBranch(setOf(WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Delay)),
        TrueSequence(setOf(WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Delay)),
        FalseSequence(setOf(WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Delay)),
        NestedDecision(setOf(WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Delay)),
        SetThenDecision(setOf(WorkflowExampleCapability.Variables, WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Delay)),
        DecisionThenSet(setOf(WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Delay, WorkflowExampleCapability.Variables)),
        TwoDecisions(setOf(WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Delay)),
        ThreeDelays(setOf(WorkflowExampleCapability.Delay)),
        RepeatThenCooldown(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.Delay)),
        ContinueSequence(setOf(WorkflowExampleCapability.Delay)),
        RetrySequence(setOf(WorkflowExampleCapability.Delay)),
        StopDelay(setOf(WorkflowExampleCapability.Delay)),
        RetryInLoop(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.Delay)),
        ContinueInLoop(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.Delay)),
        RetryBranch(setOf(WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Delay)),
        FallbackBranch(setOf(WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Delay)),
        RetryThenSet(setOf(WorkflowExampleCapability.Delay, WorkflowExampleCapability.Variables)),
        Tap(setOf(WorkflowExampleCapability.Gestures)),
        Swipe(setOf(WorkflowExampleCapability.Gestures)),
        TwoTaps(setOf(WorkflowExampleCapability.Gestures)),
        TapThenSwipe(setOf(WorkflowExampleCapability.Gestures)),
        SwipeThenTap(setOf(WorkflowExampleCapability.Gestures)),
        RepeatTap(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.Gestures)),
        RepeatSwipe(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.Gestures)),
        DelayThenTap(setOf(WorkflowExampleCapability.Delay, WorkflowExampleCapability.Gestures)),
        TapThenDelay(setOf(WorkflowExampleCapability.Gestures, WorkflowExampleCapability.Delay)),
        GestureBranch(setOf(WorkflowExampleCapability.Conditions, WorkflowExampleCapability.Gestures)),
        WaitPresent(setOf(WorkflowExampleCapability.AppSelectors)),
        WaitAbsent(setOf(WorkflowExampleCapability.AppSelectors)),
        WaitThenDelay(setOf(WorkflowExampleCapability.AppSelectors, WorkflowExampleCapability.Delay)),
        DelayThenWait(setOf(WorkflowExampleCapability.Delay, WorkflowExampleCapability.AppSelectors)),
        RepeatWait(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.AppSelectors)),
        PresenceBranch(setOf(WorkflowExampleCapability.Conditions, WorkflowExampleCapability.AppSelectors)),
        ScrollForward(setOf(WorkflowExampleCapability.AppSelectors)),
        ScrollBackward(setOf(WorkflowExampleCapability.AppSelectors)),
        WaitThenScroll(setOf(WorkflowExampleCapability.AppSelectors)),
        WaitTwice(setOf(WorkflowExampleCapability.AppSelectors)),
        ReadTextOrDescription(setOf(WorkflowExampleCapability.AppSelectors, WorkflowExampleCapability.NodeReading)),
        ReadText(setOf(WorkflowExampleCapability.AppSelectors, WorkflowExampleCapability.NodeReading)),
        ReadDescription(setOf(WorkflowExampleCapability.AppSelectors, WorkflowExampleCapability.NodeReading)),
        ReadViewId(setOf(WorkflowExampleCapability.AppSelectors, WorkflowExampleCapability.NodeReading)),
        ReadClass(setOf(WorkflowExampleCapability.AppSelectors, WorkflowExampleCapability.NodeReading)),
        ReadThenDelay(setOf(WorkflowExampleCapability.AppSelectors, WorkflowExampleCapability.NodeReading, WorkflowExampleCapability.Delay)),
        DelayThenRead(setOf(WorkflowExampleCapability.Delay, WorkflowExampleCapability.AppSelectors, WorkflowExampleCapability.NodeReading)),
        RepeatRead(setOf(WorkflowExampleCapability.Loops, WorkflowExampleCapability.AppSelectors, WorkflowExampleCapability.NodeReading)),
        ReadThenBranch(setOf(WorkflowExampleCapability.AppSelectors, WorkflowExampleCapability.NodeReading, WorkflowExampleCapability.Conditions)),
        ReadTwice(setOf(WorkflowExampleCapability.AppSelectors, WorkflowExampleCapability.NodeReading));

        fun build(id: () -> String): List<Step> = when (this) {
            Delay -> listOf(delay(id)); DelaySequence -> listOf(delay(id), delay(id))
            SetThenDelay -> listOf(set(id), delay(id)); DelayThenSet -> listOf(delay(id), set(id))
            RepeatDelay -> listOf(repeat(id, delay(id))); RepeatSequence -> listOf(repeat(id, delay(id), delay(id)))
            ConditionalDelay -> listOf(branch(id, listOf(delay(id)))); AlternativeDelays -> listOf(branch(id, listOf(delay(id)), listOf(delay(id))))
            ContinueDelay -> listOf(delay(id, FailurePolicy.Continue)); RetryDelay -> listOf(delay(id, FailurePolicy.Retry(2)))
            PauseThenHome -> listOf(Step.Delay(id(), 5_000), action(id, SystemAction.Home))
            Back -> listOf(action(id, SystemAction.Back)); Home -> listOf(action(id, SystemAction.Home)); Recents -> listOf(action(id, SystemAction.Recents))
            PauseThenBack -> listOf(delay(id), action(id, SystemAction.Back)); PauseThenRecents -> listOf(delay(id), action(id, SystemAction.Recents))
            HomeThenPause -> listOf(action(id, SystemAction.Home), delay(id)); BackThenPause -> listOf(action(id, SystemAction.Back), delay(id))
            RecentsThenPause -> listOf(action(id, SystemAction.Recents), delay(id)); RepeatBack -> listOf(repeat(id, action(id, SystemAction.Back)))
            LegacyRepeat -> listOf(Step.Repeat(id(), 3, listOf(Step.Delay(id(), 1_000))))
            RepeatThenDelay -> listOf(repeat(id, delay(id)), delay(id)); DelayThenRepeat -> listOf(delay(id), repeat(id, delay(id)))
            NestedRepeat -> listOf(repeat(id, repeat(id, delay(id)))); RepeatSet -> listOf(repeat(id, set(id)))
            RepeatConditional -> listOf(repeat(id, branch(id, listOf(delay(id))))); TwoRepeats -> listOf(repeat(id, delay(id)), repeat(id, delay(id)))
            RepeatSetThenDelay -> listOf(repeat(id, set(id), delay(id))); RepeatContinue -> listOf(repeat(id, delay(id, FailurePolicy.Continue)))
            SetLiteral -> listOf(set(id)); SetTwo -> listOf(set(id), set(id, "second", Value.Literal("beta")))
            SetTemplate -> listOf(set(id, value = Value.Template("Hello {{name}}"))); CopyVariable -> listOf(set(id), set(id, "copy", Value.Variable("sample")))
            BranchSet -> listOf(branch(id, listOf(set(id)))); BranchSetBoth -> listOf(branch(id, listOf(set(id)), listOf(set(id))))
            SetDelayCopy -> listOf(set(id), delay(id), set(id, "copy", Value.Variable("sample")))
            LegacyDecision -> listOf(set(id, "mode", Value.Literal("demo")), Step.IfElse(id(), Condition.Equals(Value.Variable("mode"), Value.Literal("demo")), listOf(Step.Delay(id(), 500)), listOf(Step.Delay(id(), 1_000))))
            NotEqualBranch -> listOf(branch(id, listOf(delay(id)), ComparisonOperator.NotEquals)); ContainsBranch -> listOf(branch(id, listOf(delay(id)), ComparisonOperator.Contains))
            NotContainsBranch -> listOf(branch(id, listOf(delay(id)), ComparisonOperator.NotContains)); TrueSequence -> listOf(branch(id, listOf(delay(id), delay(id))))
            FalseSequence -> listOf(branch(id, listOf(delay(id)), listOf(delay(id), delay(id)))); NestedDecision -> listOf(branch(id, listOf(branch(id, listOf(delay(id))))))
            SetThenDecision -> listOf(set(id), branch(id, listOf(delay(id)))); DecisionThenSet -> listOf(branch(id, listOf(delay(id))), set(id))
            TwoDecisions -> listOf(branch(id, listOf(delay(id))), branch(id, listOf(delay(id)), ComparisonOperator.Contains))
            ThreeDelays -> listOf(delay(id), delay(id), delay(id)); RepeatThenCooldown -> listOf(repeat(id, delay(id)), delay(id), delay(id))
            ContinueSequence -> listOf(delay(id, FailurePolicy.Continue), delay(id)); RetrySequence -> listOf(delay(id, FailurePolicy.Retry(2)), delay(id))
            StopDelay -> listOf(delay(id, FailurePolicy.Stop)); RetryInLoop -> listOf(repeat(id, delay(id, FailurePolicy.Retry(2))))
            ContinueInLoop -> listOf(repeat(id, delay(id, FailurePolicy.Continue))); RetryBranch -> listOf(branch(id, listOf(delay(id, FailurePolicy.Retry(2)))))
            FallbackBranch -> listOf(branch(id, listOf(delay(id)), listOf(delay(id, FailurePolicy.Continue)))); RetryThenSet -> listOf(delay(id, FailurePolicy.Retry(2)), set(id))
            Tap -> listOf(tap(id)); Swipe -> listOf(swipe(id)); TwoTaps -> listOf(tap(id), tap(id)); TapThenSwipe -> listOf(tap(id), swipe(id)); SwipeThenTap -> listOf(swipe(id), tap(id))
            RepeatTap -> listOf(repeat(id, tap(id))); RepeatSwipe -> listOf(repeat(id, swipe(id))); DelayThenTap -> listOf(delay(id), tap(id)); TapThenDelay -> listOf(tap(id), delay(id))
            GestureBranch -> listOf(branch(id, listOf(tap(id)), listOf(swipe(id))))
            WaitPresent -> listOf(wait(id)); WaitAbsent -> listOf(wait(id, false)); WaitThenDelay -> listOf(wait(id), delay(id)); DelayThenWait -> listOf(delay(id), wait(id))
            RepeatWait -> listOf(repeat(id, wait(id))); PresenceBranch -> listOf(Step.IfElse(id(), Condition.NodeExists(selector()), listOf(delay(id))))
            ScrollForward -> listOf(scroll(id, ScrollDirection.Forward)); ScrollBackward -> listOf(scroll(id, ScrollDirection.Backward)); WaitThenScroll -> listOf(wait(id), scroll(id, ScrollDirection.Forward)); WaitTwice -> listOf(wait(id), wait(id))
            ReadTextOrDescription -> listOf(read(id, NodeAttribute.TextOrDescription)); ReadText -> listOf(read(id, NodeAttribute.Text)); ReadDescription -> listOf(read(id, NodeAttribute.ContentDescription))
            ReadViewId -> listOf(read(id, NodeAttribute.ViewId)); ReadClass -> listOf(read(id, NodeAttribute.ClassName)); ReadThenDelay -> listOf(read(id, NodeAttribute.Text), delay(id))
            DelayThenRead -> listOf(delay(id), read(id, NodeAttribute.Text)); RepeatRead -> listOf(repeat(id, read(id, NodeAttribute.Text)))
            ReadThenBranch -> listOf(read(id, NodeAttribute.Text), branch(id, listOf(delay(id)), ComparisonOperator.Contains)); ReadTwice -> listOf(read(id, NodeAttribute.Text), read(id, NodeAttribute.ContentDescription))
        }
    }

    private fun specs(category: WorkflowExampleCategory, vararg rows: Triple<String, List<String>, Shape>, configured: Boolean = false): List<ExampleSpec> =
        rows.map { (slug, terms, shape) -> ExampleSpec("${category.resourceName}-$slug", category, slug, terms, shape, configured) }

    private val registry = buildList {
        addAll(specs(WorkflowExampleCategory.Fundamentals,
            row("single_pause", "单次暂停", Shape.Delay), row("pause_sequence", "连续暂停", Shape.DelaySequence), row("remember_then_pause", "保存后等待", Shape.SetThenDelay), row("pause_then_remember", "等待后保存", Shape.DelayThenSet), row("finite_pause_block", "有限等待循环", Shape.RepeatDelay), row("pause_block_sequence", "循环步骤序列", Shape.RepeatSequence), row("optional_pause", "可选等待", Shape.ConditionalDelay), row("alternative_pauses", "替代等待", Shape.AlternativeDelays), row("continue_after_pause_issue", "失败后继续", Shape.ContinueDelay), row("retry_pause_issue", "失败后重试", Shape.RetryDelay)))
        addAll(specs(WorkflowExampleCategory.Navigation,
            row("pause_then_home", "暂停后主页", Shape.PauseThenHome), row("go_back", "返回上一页", Shape.Back), row("go_home", "回到主页", Shape.Home), row("open_recents", "打开最近任务", Shape.Recents), row("pause_then_back", "暂停后返回", Shape.PauseThenBack), row("pause_then_recents", "暂停后最近任务", Shape.PauseThenRecents), row("home_then_pause", "主页后等待", Shape.HomeThenPause), row("back_then_pause", "返回后等待", Shape.BackThenPause), row("recents_then_pause", "最近任务后等待", Shape.RecentsThenPause), row("repeat_back_navigation", "重复返回", Shape.RepeatBack)).mapIndexed { index, spec -> if (index == 0) spec.copy(id = "navigation-01") else spec })
        addAll(specs(WorkflowExampleCategory.Repetition,
            row("repeat_with_pause", "重复暂停", Shape.LegacyRepeat), row("repeat_step_sequence", "重复步骤序列", Shape.RepeatSequence), row("prepare_then_repeat", "准备后循环", Shape.DelayThenRepeat), row("repeat_then_finish", "循环后收尾", Shape.RepeatThenDelay), row("nested_repetition", "嵌套循环", Shape.NestedRepeat), row("repeat_variable_assignment", "循环赋值", Shape.RepeatSet), row("repeat_conditional_pause", "循环内判断", Shape.RepeatConditional), row("two_repeat_blocks", "两个循环块", Shape.TwoRepeats), row("repeat_with_setup", "循环内准备", Shape.RepeatSetThenDelay), row("repeat_with_recovery", "循环内恢复", Shape.RepeatContinue)).mapIndexed { index, spec -> if (index == 0) spec.copy(id = "repetition-01") else spec })
        addAll(specs(WorkflowExampleCategory.Variables,
            row("store_literal_value", "保存固定值", Shape.SetLiteral), row("store_two_values", "保存两个值", Shape.SetTwo), row("store_then_pause", "保存后等待", Shape.SetThenDelay), row("pause_then_store", "等待后保存", Shape.DelayThenSet), row("store_template_value", "保存模板值", Shape.SetTemplate), row("copy_variable_value", "复制变量值", Shape.CopyVariable), row("repeat_variable_update", "循环更新变量", Shape.RepeatSet), row("branch_on_variable", "按变量分支", Shape.BranchSet), row("assign_in_both_branches", "分支分别赋值", Shape.BranchSetBoth), row("store_pause_and_copy", "保存等待再复制", Shape.SetDelayCopy)))
        addAll(specs(WorkflowExampleCategory.Decisions,
            row("variable_decision", "变量判断", Shape.LegacyDecision), row("not_equal_branch", "不相等分支", Shape.NotEqualBranch), row("contains_text_branch", "包含文本分支", Shape.ContainsBranch), row("excludes_text_branch", "不包含文本分支", Shape.NotContainsBranch), row("true_branch_sequence", "真分支序列", Shape.TrueSequence), row("false_branch_sequence", "假分支序列", Shape.FalseSequence), row("nested_decision", "嵌套判断", Shape.NestedDecision), row("decision_after_setup", "准备后判断", Shape.SetThenDecision), row("decision_then_finalize", "判断后收尾", Shape.DecisionThenSet), row("two_stage_decision", "两阶段判断", Shape.TwoDecisions)).mapIndexed { index, spec -> if (index == 0) spec.copy(id = "decisions-01") else spec })
        addAll(specs(WorkflowExampleCategory.Timing,
            row("single_wait", "单次等待", Shape.Delay), row("two_stage_wait", "两段等待", Shape.DelaySequence), row("three_stage_wait", "三段等待", Shape.ThreeDelays), row("wait_block", "等待循环块", Shape.RepeatDelay), row("wait_block_then_cooldown", "循环后冷却", Shape.RepeatThenCooldown), row("warmup_then_wait_block", "预热后循环", Shape.DelayThenRepeat), row("optional_wait", "可选等待", Shape.ConditionalDelay), row("alternative_waits", "替代等待", Shape.AlternativeDelays), row("continue_timed_sequence", "继续定时序列", Shape.ContinueSequence), row("retry_timed_sequence", "重试定时序列", Shape.RetrySequence)))
        addAll(specs(WorkflowExampleCategory.Resilience,
            row("retry_failed_step", "失败后重试", Shape.RetryDelay), row("continue_past_failure", "失败后继续", Shape.ContinueDelay), row("stop_on_failure", "失败时停止", Shape.StopDelay), row("retry_then_checkpoint", "重试后检查点", Shape.RetrySequence), row("continue_then_checkpoint", "继续后检查点", Shape.ContinueSequence), row("retry_inside_loop", "循环内重试", Shape.RetryInLoop), row("continue_inside_loop", "循环内继续", Shape.ContinueInLoop), row("retry_true_branch", "真分支重试", Shape.RetryBranch), row("fallback_branch", "备用恢复分支", Shape.FallbackBranch), row("mark_recovery_complete", "标记恢复完成", Shape.RetryThenSet)))
        addAll(specs(WorkflowExampleCategory.Gestures,
            row("single_tap", "单次点击", Shape.Tap), row("single_swipe", "单次滑动", Shape.Swipe), row("double_tap_sequence", "连续两次点击", Shape.TwoTaps), row("tap_then_swipe", "点击后滑动", Shape.TapThenSwipe), row("swipe_then_tap", "滑动后点击", Shape.SwipeThenTap), row("repeat_tap", "重复点击", Shape.RepeatTap), row("repeat_swipe", "重复滑动", Shape.RepeatSwipe), row("pause_before_tap", "点击前等待", Shape.DelayThenTap), row("pause_after_tap", "点击后等待", Shape.TapThenDelay), row("conditional_gesture", "条件手势", Shape.GestureBranch)))
        addAll(specs(WorkflowExampleCategory.AppObservation,
            row("wait_for_target", "等待目标出现", Shape.WaitPresent), row("wait_for_target_to_disappear", "等待目标消失", Shape.WaitAbsent), row("wait_then_stabilize", "出现后稳定等待", Shape.WaitThenDelay), row("settle_then_observe", "稳定后观察", Shape.DelayThenWait), row("repeat_presence_check", "重复检查存在", Shape.RepeatWait), row("branch_on_presence", "按存在状态分支", Shape.PresenceBranch), row("prepare_forward_scroll", "准备向前滚动", Shape.ScrollForward), row("prepare_backward_scroll", "准备向后滚动", Shape.ScrollBackward), row("wait_then_scroll", "等待后滚动", Shape.WaitThenScroll), row("confirm_presence_twice", "两次确认存在", Shape.WaitTwice), configured = true))
        addAll(specs(WorkflowExampleCategory.TextReading,
            row("read_visible_label", "读取可见标签", Shape.ReadTextOrDescription), row("read_text_attribute", "读取文本属性", Shape.ReadText), row("read_accessibility_description", "读取无障碍描述", Shape.ReadDescription), row("read_view_identifier", "读取控件标识", Shape.ReadViewId), row("read_widget_class", "读取控件类型", Shape.ReadClass), row("read_then_pause", "读取后等待", Shape.ReadThenDelay), row("pause_then_read", "等待后读取", Shape.DelayThenRead), row("repeat_text_read", "重复读取文本", Shape.RepeatRead), row("branch_after_read", "读取后判断", Shape.ReadThenBranch), row("compare_two_readings", "比较两次读取", Shape.ReadTwice), configured = true))
    }

    private fun row(slug: String, chinese: String, shape: Shape) = Triple(slug, listOf(chinese), shape)
    private fun delay(id: () -> String, policy: FailurePolicy = FailurePolicy.Stop) = Step.Delay(id(), 500, failurePolicy = policy)
    private fun set(id: () -> String, name: String = "sample", value: Value = Value.Literal("ready")) = Step.SetVariable(id(), name, value)
    private fun repeat(id: () -> String, vararg steps: Step) = Step.Repeat(id(), 2, steps.toList())
    private fun branch(id: () -> String, yes: List<Step>, no: List<Step> = emptyList(), operator: ComparisonOperator = ComparisonOperator.Equals) = Step.IfElse(id(), Condition.Equals(Value.Literal("sample"), Value.Literal("ready"), operator), yes, no)
    private fun branch(id: () -> String, yes: List<Step>, operator: ComparisonOperator) = branch(id, yes, emptyList(), operator)
    private fun action(id: () -> String, action: SystemAction) = Step.GlobalAction(id(), action)
    private fun tap(id: () -> String) = Step.Tap(id(), 500, 500)
    private fun swipe(id: () -> String) = Step.Swipe(id(), 500, 700, 500, 300)
    private fun selector() = NodeSelector(PLACEHOLDER_PACKAGE, viewId = PLACEHOLDER_VIEW_ID)
    private fun wait(id: () -> String, exists: Boolean = true) = Step.WaitForNode(id(), selector(), exists)
    private fun scroll(id: () -> String, direction: ScrollDirection) = Step.Scroll(id(), selector(), direction)
    private fun read(id: () -> String, attribute: NodeAttribute) = Step.ReadNodeText(id(), selector(), "observed_text", attribute)
    private val WorkflowExampleCategory.resourceName: String get() = name.replace(Regex("(?<!^)([A-Z])"), "_$1").lowercase()
}

private val WorkflowStarterTemplate.catalogId: String get() = when (this) {
    WorkflowStarterTemplate.PauseThenHome -> "navigation-01"
    WorkflowStarterTemplate.RepeatWithPause -> "repetition-01"
    WorkflowStarterTemplate.VariableDecision -> "decisions-01"
}

private val WorkflowStarterTemplate.defaultName: String get() = when (this) {
    WorkflowStarterTemplate.PauseThenHome -> "Pause, then go Home"
    WorkflowStarterTemplate.RepeatWithPause -> "Repeated pause"
    WorkflowStarterTemplate.VariableDecision -> "Variable decision"
}
