package com.aiindexfinger.model

fun TextMatchMode.matches(expected: String?, actual: String?): Boolean = when {
    expected == null -> true
    actual == null -> false
    this == TextMatchMode.Exact -> expected == actual
    else -> actual.contains(expected)
}