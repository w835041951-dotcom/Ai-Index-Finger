package com.aiindexfinger

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class BilingualResourcesTest {
    @Test
    fun `english and simplified chinese string keys match`() {
        val defaultKeys = stringKeys(File("src/main/res/values/strings.xml"))
        val chineseKeys = stringKeys(File("src/main/res/values-zh-rCN/strings.xml"))

        assertEquals(defaultKeys, chineseKeys)
    }

    private fun stringKeys(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = document.getElementsByTagName("string")
        return buildSet {
            repeat(strings.length) { index ->
                add(strings.item(index).attributes.getNamedItem("name").nodeValue)
            }
        }
    }
}