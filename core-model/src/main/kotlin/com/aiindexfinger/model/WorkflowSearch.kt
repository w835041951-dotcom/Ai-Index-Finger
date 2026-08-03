package com.aiindexfinger.model

fun Workflow.matchesSearch(query: String): Boolean {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return true
    val searchableText = buildList {
        add(name)
        steps.forEach { step -> step.addSearchTerms(this) }
    }.joinToString("\n").lowercase()
    return terms.all(searchableText::contains)
}

private fun Step.addSearchTerms(terms: MutableList<String>) {
    terms += when (this) {
        is Step.Click -> "click element"
        is Step.RecordedClick -> "recorded click element coordinate ${control.packageName}"
        is Step.ImageClick -> "click image screenshot $packageName"
        is Step.Delay -> "wait delay"
        is Step.GlobalAction -> "global ${action.name}"
        is Step.IfElse -> "if condition"
        is Step.InputText -> variableName?.let { "input text variable $it" } ?: "input text"
        is Step.LaunchApp -> "launch app $packageName ${intentAction.orEmpty()}"
        is Step.LongClick -> "long click element"
        is Step.ReadNodeText -> "read node attribute ${attribute.name} variable $variableName"
        is Step.Repeat -> "repeat loop"
        is Step.Scroll -> "scroll ${direction.name}"
        is Step.SetVariable -> "set variable $name ${value.searchableVariableNames()}"
        is Step.Swipe -> "swipe gesture"
        is Step.Tap -> "tap coordinate"
        is Step.WaitForNode -> if (mustExist) "wait element appear" else "wait element disappear"
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
        is Step.InputText -> selector.addSearchTerms(terms)
        is Step.LongClick -> selector.addSearchTerms(terms)
        is Step.ReadNodeText -> selector.addSearchTerms(terms)
        is Step.Repeat -> steps.forEach { it.addSearchTerms(terms) }
        is Step.Scroll -> selector.addSearchTerms(terms)
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
        is Condition.Equals -> {
            listOf(left, right).filterIsInstance<Value.Variable>().forEach { terms += it.name }
        }
        is Condition.NodeExists -> selector.addSearchTerms(terms)
    }
}

private fun NodeSelector.addSearchTerms(terms: MutableList<String>) {
    terms += packageName
    listOf(viewId, text, contentDescription, className).filterNotNullTo(terms)
}