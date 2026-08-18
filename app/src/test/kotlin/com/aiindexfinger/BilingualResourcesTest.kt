package com.aiindexfinger

import com.aiindexfinger.model.WorkflowStarterTemplates
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class BilingualResourcesTest {
    @Test
    fun `manifest advertises exactly the supported product locales`() {
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val manifest = document(File("src/main/AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0) as Element

        assertEquals("@xml/locales_config", application.getAttributeNS(androidNamespace, "localeConfig"))

        val localeConfig = document(File("src/main/res/xml/locales_config.xml"))
        val locales = localeConfig.getElementsByTagName("locale")
        val names = buildSet {
            repeat(locales.length) { index ->
                add((locales.item(index) as Element).getAttributeNS(androidNamespace, "name"))
            }
        }
        assertEquals(setOf("en", "zh-CN"), names)
    }

    @Test
    fun `english and simplified chinese string keys match`() {
        val defaultStrings = strings(File("src/main/res/values/strings.xml"))
        val chineseStrings = strings(File("src/main/res/values-zh-rCN/strings.xml"))

        assertEquals(defaultStrings.keys, chineseStrings.keys)
        defaultStrings.forEach { (key, value) ->
            assertEquals("Placeholder mismatch for $key", placeholders(value), placeholders(chineseStrings.getValue(key)))
        }
    }

    @Test
    fun `english and simplified chinese plurals are compatible`() {
        val defaultPlurals = plurals(File("src/main/res/values/strings.xml"))
        val chinesePlurals = plurals(File("src/main/res/values-zh-rCN/strings.xml"))

        assertEquals(defaultPlurals.keys, chinesePlurals.keys)
        defaultPlurals.forEach { (key, defaultQuantities) ->
            val chineseQuantities = chinesePlurals.getValue(key)
            val defaultOther = defaultQuantities.getValue("other")
            val chineseOther = chineseQuantities.getValue("other")
            assertEquals(
                "Placeholder mismatch for plural $key",
                placeholders(defaultOther),
                placeholders(chineseOther),
            )
            defaultQuantities.forEach { (quantity, value) ->
                assertEquals(
                    "Default placeholder mismatch for plural $key/$quantity",
                    placeholders(defaultOther),
                    placeholders(value),
                )
            }
            chineseQuantities.forEach { (quantity, value) ->
                assertEquals(
                    "Chinese placeholder mismatch for plural $key/$quantity",
                    placeholders(chineseOther),
                    placeholders(value),
                )
            }
        }
    }

    @Test
    fun `workflow example catalog is fully localized`() {
        val defaultStrings = strings(File("src/main/res/values/strings.xml"))
        val chineseStrings = strings(File("src/main/res/values-zh-rCN/strings.xml"))
        val catalogKeys = WorkflowStarterTemplates.catalog.flatMap { example ->
            listOf(example.titleResourceKey, example.descriptionResourceKey)
        }
        val requiredUiKeys = setOf(
            "workflow_example_catalog_title",
            "workflow_example_search",
            "workflow_example_category_all",
            "workflow_example_no_results",
            "workflow_example_clear_filters",
            "workflow_example_details_title",
            "workflow_example_requires_configuration",
            "workflow_example_ready_to_edit",
            "workflow_example_capabilities",
            "workflow_example_capability_separator",
            "workflow_example_compatibility",
            "workflow_example_use",
        )
        val categoryKeys = setOf(
            "fundamentals", "navigation", "repetition", "variables", "decisions",
            "timing", "resilience", "gestures", "app_observation", "text_reading",
        ).mapTo(mutableSetOf()) { "workflow_example_category_$it" }
        val capabilityKeys = setOf(
            "delay", "global_navigation", "variables", "conditions", "loops",
            "gestures", "app_selectors", "node_reading",
        ).mapTo(mutableSetOf()) { "workflow_example_capability_$it" }

        assertEquals(100, WorkflowStarterTemplates.catalog.size)
        (catalogKeys + requiredUiKeys + categoryKeys + capabilityKeys).forEach { key ->
            assertTrue("Missing default resource: $key", defaultStrings.containsKey(key))
            assertTrue("Missing Simplified Chinese resource: $key", chineseStrings.containsKey(key))
        }
        assertTrue(
            "Missing default plural: workflow_example_result_count",
            plurals(File("src/main/res/values/strings.xml"))
                .containsKey("workflow_example_result_count"),
        )
        assertTrue(
            "Missing Simplified Chinese plural: workflow_example_result_count",
            plurals(File("src/main/res/values-zh-rCN/strings.xml"))
                .containsKey("workflow_example_result_count"),
        )

        val englishTitles = WorkflowStarterTemplates.catalog.map { defaultStrings.getValue(it.titleResourceKey).trim() }
        val chineseTitles = WorkflowStarterTemplates.catalog.map { chineseStrings.getValue(it.titleResourceKey).trim() }
        assertTrue(englishTitles.all(String::isNotBlank))
        assertTrue(chineseTitles.all(String::isNotBlank))
        assertEquals("English workflow example titles must be unique", 100, englishTitles.distinct().size)
        assertEquals("Chinese workflow example titles must be unique", 100, chineseTitles.distinct().size)

        val englishDescriptions = WorkflowStarterTemplates.catalog.map {
            defaultStrings.getValue(it.descriptionResourceKey).trim()
        }
        val chineseDescriptions = WorkflowStarterTemplates.catalog.map {
            chineseStrings.getValue(it.descriptionResourceKey).trim()
        }
        assertTrue(englishDescriptions.all(String::isNotBlank))
        assertTrue(chineseDescriptions.all(String::isNotBlank))
        assertEquals("English workflow example descriptions must be unique", 100, englishDescriptions.distinct().size)
        assertEquals("Chinese workflow example descriptions must be unique", 100, chineseDescriptions.distinct().size)
    }

    @Test
    fun `self test workflow is fully localized`() {
        val defaultStrings = strings(File("src/main/res/values/strings.xml"))
        val chineseStrings = strings(File("src/main/res/values-zh-rCN/strings.xml"))

        setOf(
            "self_test_folder_name",
            "self_test_workflow_verify_home",
            "self_test_workflow_verify_observation_runtime",
        ).forEach { key ->
            assertTrue("Missing default resource: $key", defaultStrings.getValue(key).isNotBlank())
            assertTrue("Missing Simplified Chinese resource: $key", chineseStrings.getValue(key).isNotBlank())
        }
    }

    @Test
    fun `privacy disclosure names the absent internet permission precisely`() {
        val defaultDisclosure = strings(File("src/main/res/values/strings.xml"))
            .getValue("accessibility_disclosure_privacy")
        val chineseDisclosure = strings(File("src/main/res/values-zh-rCN/strings.xml"))
            .getValue("accessibility_disclosure_privacy")

        assertTrue(defaultDisclosure.contains("INTERNET"))
        assertTrue(chineseDisclosure.contains("INTERNET"))
        assertFalse(defaultDisclosure.contains("no network permission", ignoreCase = true))
        assertFalse(chineseDisclosure.contains("没有网络权限"))
    }

    private fun strings(file: File): Map<String, String> {
        val document = document(file)
        val strings = document.getElementsByTagName("string")
        return buildMap {
            repeat(strings.length) { index ->
                val element = strings.item(index) as Element
                put(element.getAttribute("name"), element.textContent)
            }
        }
    }

    private fun plurals(file: File): Map<String, Map<String, String>> {
        val document = document(file)
        val plurals = document.getElementsByTagName("plurals")
        return buildMap {
            repeat(plurals.length) { index ->
                val plural = plurals.item(index) as Element
                val items = plural.getElementsByTagName("item")
                put(
                    plural.getAttribute("name"),
                    buildMap {
                        repeat(items.length) { itemIndex ->
                            val item = items.item(itemIndex) as Element
                            put(item.getAttribute("quantity"), item.textContent)
                        }
                    },
                )
            }
        }
    }

    private fun document(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(file)

    private fun placeholders(value: String): List<String> =
        Regex("%(?:\\d+\\$)?[a-zA-Z]").findAll(value).map { it.value }.toList()
}