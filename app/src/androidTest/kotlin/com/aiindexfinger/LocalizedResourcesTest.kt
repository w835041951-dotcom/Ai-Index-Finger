package com.aiindexfinger

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizedResourcesTest {
    @Test
    fun formattedNotificationTextIsAvailableInBothProductLanguages() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals(
            "Open AI Index Finger to run Demo",
            context.forLocale(Locale.US).getString(R.string.schedule_notification_text, "Demo"),
        )
        assertEquals(
            "打开 AI Index Finger 运行 Demo",
            context.forLocale(Locale.SIMPLIFIED_CHINESE)
                .getString(R.string.schedule_notification_text, "Demo"),
        )
    }

    @Test
    fun recoveryTextUsesTheRequestedLocale() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals(
            "The workflow file was damaged. The previous valid backup has been restored.",
            context.forLocale(Locale.US).getString(R.string.workflows_recovered_from_backup),
        )
        assertEquals(
            "工作流文件已损坏，已恢复上一个有效备份。",
            context.forLocale(Locale.SIMPLIFIED_CHINESE)
                .getString(R.string.workflows_recovered_from_backup),
        )
    }

    @Test
    fun recurrenceLabelsAreAvailableInBothProductLanguages() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals(
            listOf("Once", "Daily", "Weekly"),
            listOf(
                R.string.schedule_recurrence_once,
                R.string.schedule_recurrence_daily,
                R.string.schedule_recurrence_weekly,
            ).map(context.forLocale(Locale.US)::getString),
        )
        assertEquals(
            listOf("仅一次", "每天", "每周"),
            listOf(
                R.string.schedule_recurrence_once,
                R.string.schedule_recurrence_daily,
                R.string.schedule_recurrence_weekly,
            ).map(context.forLocale(Locale.SIMPLIFIED_CHINESE)::getString),
        )
    }

    private fun Context.forLocale(locale: Locale): Context {
        val configuration = Configuration(resources.configuration).apply { setLocales(LocaleList(locale)) }
        return createConfigurationContext(configuration)
    }
}