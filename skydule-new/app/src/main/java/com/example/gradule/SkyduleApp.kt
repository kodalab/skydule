package com.example.gradule

import android.app.Application
import com.example.gradule.data.Diag

class SkyduleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diag.init(this)
        // Re-arm the background reminder jobs on every process start (boot, update, a calendar-change wake-up).
        com.example.gradule.reminders.ReminderJobs.ensure(this)
    }
}
