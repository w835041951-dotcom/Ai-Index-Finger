package com.aiindexfinger.model

import java.text.Normalizer
import java.util.Locale

fun Workflow.matchesSearch(query: String): Boolean {
    val terms = query.normalizedSearchTokens()
    if (terms.isEmpty()) return true
    val searchableText = buildList {
        add(name)
        steps.forEach { step -> step.addSearchTerms(this) }
    }.joinToString("\n").normalizeSearchText()
    return terms.all(searchableText::contains)
}

private fun Step.addSearchTerms(terms: MutableList<String>) {
    terms += when (this) {
        is Step.Click -> "click element tap 点击 元素"
        is Step.RecordedClick -> "recorded click element coordinate 录制 点击 坐标 ${control.packageName}"
        is Step.ImageClick -> "click image screenshot 图片 点击 截图 $packageName"
        is Step.Delay -> "wait delay 等待 延迟"
        is Step.GlobalAction -> "global ${action.name} 全局 操作"
        is Step.IfElse -> "if condition 判断 条件"
        is Step.Label -> "label tag 标签 $name"
        is Step.JumpIf -> "goto jump label 跳转 标签 $targetLabel"
        is Step.InputText -> value?.let { "input text 输入 文本 ${it.searchableVariableNames()}" }
            ?: variableName?.let { "input text variable 输入 文本 变量 $it" }
            ?: "input text 输入 文本"
        is Step.LaunchApp -> "launch app open 打开 应用 $packageName ${intentAction.orEmpty()}"
        is Step.LongClick -> "long click press 长按 元素"
        is Step.ReadNodeText -> "read node attribute ${attribute.name} 读取 属性 变量 $variableName"
        is Step.Repeat -> "repeat loop 循环"
        is Step.Scroll -> "scroll ${direction.name} 滚动"
        is Step.ScrollUntil -> "scroll until ${direction.name} 滚动 直到"
        is Step.SetVariable -> "set variable define 变量 赋值 $name ${value.searchableVariableNames()}"
        is Step.Swipe -> "swipe gesture 滑动 手势"
        is Step.Tap -> "tap coordinate 点击 坐标"
        is Step.WaitForNode -> if (mustExist) {
            "wait element appear 等待 元素 出现"
        } else {
            "wait element disappear 等待 元素 消失"
        }
    }
    when (this) {
        is Step.Click -> selector.addSearchTerms(terms)
        is Step.RecordedClick -> {
            selector?.addSearchTerms(terms)
            listOf(control.viewId, control.text, control.contentDescription, control.className)
                .filterNotNull()
                .forEach(terms::add)
        }
        is Step.ImageClick -> Unit
        is Step.IfElse -> {
            condition.addSearchTerms(terms)
            (whenTrue + whenFalse).forEach { it.addSearchTerms(terms) }
        }
        is Step.JumpIf -> condition?.addSearchTerms(terms)
        is Step.InputText -> selector.addSearchTerms(terms)
        is Step.LongClick -> selector.addSearchTerms(terms)
        is Step.ReadNodeText -> selector.addSearchTerms(terms)
        is Step.Repeat -> steps.forEach { it.addSearchTerms(terms) }
        is Step.Scroll -> selector.addSearchTerms(terms)
        is Step.ScrollUntil -> {
            selector.addSearchTerms(terms)
            when (val stopCondition = stopCondition) {
                is ScrollUntilStopCondition.NodeAppears -> stopCondition.selector.addSearchTerms(terms)
                is ScrollUntilStopCondition.NodeDisappears -> stopCondition.selector.addSearchTerms(terms)
                is ScrollUntilStopCondition.ConditionMet -> stopCondition.condition.addSearchTerms(terms)
                else -> Unit
            }
        }
        is Step.WaitForNode -> selector.addSearchTerms(terms)
        else -> Unit
    }
}

private fun Value.searchableVariableNames(): String = when (this) {
    is Value.Literal -> ""
    is Value.Variable -> name
    is Value.Template -> template.templateVariables().joinToString(" ")
}

private fun Condition.addSearchTerms(terms: MutableList<String>) {
    when (this) {
        is Condition.Equals -> listOf(left, right)
            .map(Value::searchableVariableNames)
            .filter(String::isNotBlank)
            .forEach(terms::add)
        is Condition.NodeExists -> selector.addSearchTerms(terms)
    }
}

private fun NodeSelector.addSearchTerms(terms: MutableList<String>) {
    terms += packageName
    listOf(viewId, text, contentDescription, className).filterNotNullTo(terms)
    ancestor?.addSearchTerms(terms)
}

private fun AncestorSelector.addSearchTerms(terms: MutableList<String>) {
    listOf(viewId, text, contentDescription, className).filterNotNullTo(terms)
}

private fun String.normalizedSearchTokens(): List<String> = normalizeSearchText()
    .split(Regex("[\\s\\u3000]+"))
    .filter { it.isNotEmpty() }

private fun String.normalizeSearchText(): String = Normalizer
    .normalize(this, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT)