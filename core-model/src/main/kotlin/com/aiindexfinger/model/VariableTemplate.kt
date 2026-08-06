package com.aiindexfinger.model

private val variablePlaceholder = Regex("\\$\\{([^{}]+)\\}")

fun String.templateVariables(): Set<String> = variablePlaceholder.findAll(this)
    .map { it.groupValues[1] }
    .toSet()

fun String.renderTemplate(resolve: (String) -> String): String =
    variablePlaceholder.replace(this) { match -> resolve(match.groupValues[1]) }