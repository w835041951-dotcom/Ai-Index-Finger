package com.aiindexfinger.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchableAppCatalogTest {
    @Test
    fun appChooserUsesCaseInsensitiveLabelsWithDeterministicTieBreakers() {
        val stored = listOf(
            LaunchableApp("Zulu", "com.example.zulu"),
            LaunchableApp("alpha", "com.example.alpha.lower"),
            LaunchableApp("Alpha", "com.example.alpha.second"),
            LaunchableApp("Alpha", "com.example.alpha.first"),
        )

        assertEquals(
            listOf(
                LaunchableApp("Alpha", "com.example.alpha.first"),
                LaunchableApp("Alpha", "com.example.alpha.second"),
                LaunchableApp("alpha", "com.example.alpha.lower"),
                LaunchableApp("Zulu", "com.example.zulu"),
            ),
            sortLaunchableApps(stored),
        )
        assertEquals("com.example.zulu", stored.first().packageName)
    }

    @Test
    fun appChooserSearchesEveryAppByLabelOrPackageName() {
        val apps = (1..31).map { index ->
            LaunchableApp("Application $index", "com.example.application$index")
        } + LaunchableApp("Camera", "org.example.photos")

        assertEquals(apps, filterLaunchableApps(apps, "  "))
        assertEquals(
            listOf(LaunchableApp("Application 31", "com.example.application31")),
            filterLaunchableApps(apps, "APPLICATION 31"),
        )
        assertEquals(
            listOf(LaunchableApp("Camera", "org.example.photos")),
            filterLaunchableApps(apps, "example.photos"),
        )
        assertEquals(emptyList<LaunchableApp>(), filterLaunchableApps(apps, "missing"))
    }
}