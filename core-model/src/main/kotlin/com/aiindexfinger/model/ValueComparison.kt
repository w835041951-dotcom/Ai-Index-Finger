package com.aiindexfinger.model

fun ComparisonOperator.evaluate(left: String, right: String): Boolean = when (this) {
    ComparisonOperator.Equals -> left == right
    ComparisonOperator.NotEquals -> left != right
    ComparisonOperator.Contains -> left.contains(right)
    ComparisonOperator.NotContains -> !left.contains(right)
}