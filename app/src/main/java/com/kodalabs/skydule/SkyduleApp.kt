package com.kodalabs.skydule

import android.app.Application
import com.kodalabs.skydule.data.Diag

class SkyduleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Debug builds show a crash on screen (so it can be reported from a phone). Installed first, before anything can fail.
        if (BuildConfig.DEBUG) com.kodalabs.skydule.debug.CrashReportActivity.install(this)
        com.kodalabs.skydule.data.LegacyPrefs.migrate(this)
        Diag.init(this)
        // Re-arm the background reminder jobs on every process start (boot, update, a calendar-change wake-up).
        com.kodalabs.skydule.reminders.ReminderJobs.ensure(this)
    }
}
