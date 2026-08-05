package com.aiindexfinger.automation

import com.aiindexfinger.model.NodeSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class InspectedSelectorHandoff {
    private val mutableSelector = MutableStateFlow<NodeSelector?>(null)
    val selector = mutableSelector.asStateFlow()

    fun publish(selector: NodeSelector) {
        mutableSelector.value = selector
    }

    fun consume(): NodeSelector? = mutableSelector.value?.also {
        mutableSelector.value = null
    }

    fun clear() {
        mutableSelector.value = null
    }
}