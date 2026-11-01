package com.kodalabs.skydule

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.kodalabs.skydule.data.CalendarRepository
import com.kodalabs.skydule.data.EventVisibility
import com.kodalabs.skydule.data.NewEvent
import com.kodalabs.skydule.data.Repeat
import com.kodalabs.skydule.reminders.EXTRA_EVENT_BEGIN
import com.kodalabs.skydule.reminders.EXTRA_EVENT_ID
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The main user journeys, run on an emulator against the real Calendar Provider.
 *
 * Each test starts from a clean slate: a fresh on-device calendar named "UI test calendar" is created before the app
 * launches and removed afterwards, so the tests never touch the calendars already on the phone.
 * Run: ./gradlew connectedDebugAndroidTest (emulator running), or the "UI tests" GitHub workflow.
 */
@OptIn(ExperimentalTestApi::class)
class SkyduleJourneyTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        *buildList {
            add(Manifest.permission.READ_CALENDAR)
            add(Manifest.permission.WRITE_CALENDAR)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray(),
    )

    // An empty rule: the activity is launched by hand AFTER the calendar has been seeded.
    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo by lazy { CalendarRepository(context.contentResolver) }
    private var calendarId = -1L
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun seedCalendar() {
        MainActivity.openEventRequests.value = null
        MainActivity.openDayRequests.value = null
        calendarId = runBlocking { repo.createLocalCalendar(CALENDAR_NAME, 0xFF3B82F6.toInt()) } ?: error("could not create the test calendar")
    }

    @After fun cleanUp() {
        scenario?.close()
        // Removing the calendar removes its events with it.
        val uri = android.provider.CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(android.provider.CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(android.provider.CalendarContract.Calendars.ACCOUNT_NAME, "Skydule")
            .appendQueryParameter(android.provider.CalendarContract.Calendars.ACCOUNT_TYPE, android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        context.contentResolver.delete(uri, "${android.provider.CalendarContract.Calendars._ID} = ?", arrayOf(calendarId.toString()))
    }

    private fun launch(intent: Intent? = null) {
        val i = intent ?: Intent(context, MainActivity::class.java)
        i.setClassName(context.packageName, MainActivity::class.java.name)
        scenario = ActivityScenario.launch(i)
    }

    private fun waitForText(text: String, timeoutMs: Long = 20_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForDescription(text: String, timeoutMs: Long = 20_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitUntilGone(text: String, timeoutMs: Long = 20_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    private fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun seedEvent(title: String, day: LocalDate = LocalDate.now(), repeat: Repeat = Repeat.NONE, startDay: LocalDate = day): Long {
        val start = at(startDay, 9)
        val event = NewEvent(
            calendarId = calendarId, title = title, allDay = false, startMillis = start, endMillis = start + 3_600_000L,
            startDay = startDay, endDay = startDay, repeat = repeat, location = null, description = null,
            reminders = listOf(10), busy = true, visibility = EventVisibility.DEFAULT,
        )
        return runBlocking { repo.saveEvent(event) } ?: error("could not seed $title")
    }

    // --- Journeys ---

    @Test fun launchShowsScheduleWithAnEmptyToday() {
        launch()
        waitForText("Clear day")
        compose.onNodeWithText("Schedule").assertExists()
    }

    @Test fun createAnEventFromThePlusButton() {
        launch()
        waitForDescription("New event")
        compose.onNodeWithContentDescription("New event").performClick()
        waitForText("Add title")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Dentist")
        compose.onNodeWithText("Save").performClick()
        waitForText("Dentist")
    }

    @Test fun openEditAndDeleteAnEvent() {
        seedEvent("Team lunch")
        launch()
        waitForText("Team lunch")
        compose.onNodeWithText("Team lunch").performClick()

        // Edit the title.
        waitForDescription("Edit")
        compose.onNodeWithContentDescription("Edit").performClick()
        waitForText("Save")
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Team dinner")
        compose.onNodeWithText("Save").performClick()
        waitForText("Team dinner")

        // Delete it, confirming the dialog.
        compose.onNodeWithText("Team dinner").performClick()
        waitForDescription("Delete")
        compose.onNodeWithContentDescription("Delete").performClick()
        waitForText("Delete event?")
        compose.onNodeWithText("Delete").performClick()
        waitUntilGone("Team dinner")
    }

    @Test fun tabsSwitchAndComeBack() {
        seedEvent("Standup")
        launch()
        waitForText("Standup")
        compose.onNodeWithText("Week").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Month").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Schedule").performClick()
        waitForText("Standup")
    }

    @Test fun settingsOpensAndCloses() {
        launch()
        waitForDescription("Settings")
        compose.onNodeWithContentDescription("Settings").performClick()
        waitForText("Settings")
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForDescription("Settings")
    }

    @Test fun changingTheThemeKeepsSettingsOpen() {
        launch()
        waitForDescription("Settings")
        compose.onNodeWithContentDescription("Settings").performClick()
        waitForText("Settings")
        // Every theme, AMOLED included (it used to close Settings), then back to the default.
        for (choice in listOf("Material You (Android 12+)", "AMOLED black", "Sky (follows the time of day)")) {
            compose.onNodeWithText("Theme").performClick()
            waitForText(choice)
            compose.onNodeWithText(choice).performClick()
            compose.waitForIdle()
            waitForText("Theme")
            compose.onNodeWithText("Settings").assertExists()
        }
    }

    @Test fun tappingAReminderOpensTheEventWhenStartedFromCold() {
        // A weekly event that began three weeks ago; the reminder is for next week's occurrence.
        val id = seedEvent("Weekly review", repeat = Repeat.WEEKLY, startDay = LocalDate.now().minusWeeks(3))
        val nextWeek = at(LocalDate.now().plusWeeks(1), 9)
        launch(Intent().putExtra(EXTRA_EVENT_ID, id).putExtra(EXTRA_EVENT_BEGIN, nextWeek))
        // The detail page opens (its Edit button appears) and the request is consumed, with no "gone" toast path.
        waitForDescription("Edit")
        compose.waitUntil(10_000) { MainActivity.openEventRequests.value == null }
    }

    @Test fun tappingAReminderForADeletedEventDoesNotOpenAnything() {
        launch(Intent().putExtra(EXTRA_EVENT_ID, 987_654_321L).putExtra(EXTRA_EVENT_BEGIN, at(LocalDate.now(), 9)))
        waitForText("Clear day")
        // The request is dropped once the days are loaded, so it can't pop open later.
        compose.waitUntil(15_000) { MainActivity.openEventRequests.value == null }
        assertNull(MainActivity.openEventRequests.value)
        assertNotNull(scenario)
        compose.onAllNodesWithContentDescription("Edit").fetchSemanticsNodes().let { assert(it.isEmpty()) }
    }

    private companion object {
        const val CALENDAR_NAME = "UI test calendar"
    }
}
