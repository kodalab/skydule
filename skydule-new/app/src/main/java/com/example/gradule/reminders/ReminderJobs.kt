package com.example.gradule.reminders

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.gradule.data.Diag
import com.example.gradule.widgets.Widgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Keeps reminders right while Skydule is closed.
 *
 * Alarms are planned from the calendar, and the calendar changes without us: an event added on the web syncs in, or
 * the 14-day planning window simply moves on. Two kinds of job cover that:
 *  - a content-triggered job that wakes us when the Calendar Provider changes. Android runs a content-triggered job
 *    once, so each run arms the next one (alternating between two ids so a run never cancels itself);
 *  - a persisted periodic job (every 6 hours) as a safety net: it re-plans the window and re-arms the content job if
 *    Android dropped it.
 * Content-triggered jobs can't be persisted, so boot, app update and every app start re-arm them ([ensure]).
 */
object ReminderJobs {
    internal const val ID_CONTENT_A = 7101
    internal const val ID_CONTENT_B = 7103
    internal const val ID_PERIODIC = 7102
    private const val PERIOD_MS = 6 * 3_600_000L

    /** Makes sure both jobs exist. Cheap; safe to call often. Does nothing without calendar access. */
    fun ensure(context: Context) {
        val app = context.applicationContext
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return
        try {
            val js = app.getSystemService(JobScheduler::class.java) ?: return
            if (js.getPendingJob(ID_CONTENT_A) == null && js.getPendingJob(ID_CONTENT_B) == null) {
                armContentJob(app, js, ID_CONTENT_A)
            }
            if (js.getPendingJob(ID_PERIODIC) == null) {
                val info = JobInfo.Builder(ID_PERIODIC, component(app))
                    .setPeriodic(PERIOD_MS)
                    .setPersisted(true)
                    .build()
                js.schedule(info)
            }
        } catch (e: Exception) {
            Diag.record("ReminderJobs", e)
        }
    }

    internal fun armContentJob(context: Context, js: JobScheduler, id: Int) {
        val info = JobInfo.Builder(id, component(context))
            .addTriggerContentUri(
                JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS),
            )
            // A sync writes many rows in a burst; wait for it to settle, but never longer than 15 s.
            .setTriggerContentUpdateDelay(2_000)
            .setTriggerContentMaxDelay(15_000)
            .build()
        js.schedule(info)
    }

    /** Content jobs alternate between two ids, so the run that arms the next one never cancels itself. */
    internal fun otherContentJobId(id: Int): Int = if (id == ID_CONTENT_A) ID_CONTENT_B else ID_CONTENT_A

    private fun component(context: Context) = ComponentName(context, ReminderJobService::class.java)
}

class ReminderJobService : JobService() {
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val app = applicationContext
        try {
            val js = app.getSystemService(JobScheduler::class.java)
            when (params.jobId) {
                // Arm the other content job first, so a change made while we work still wakes us.
                ReminderJobs.ID_CONTENT_A, ReminderJobs.ID_CONTENT_B ->
                    js?.let { ReminderJobs.armContentJob(app, it, ReminderJobs.otherContentJobId(params.jobId)) }
                else -> ReminderJobs.ensure(app)
            }
        } catch (e: Exception) {
            Diag.record("ReminderJobService", e)
        }
        work = CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.reschedule(app)
                Widgets.refresh(app)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Diag.record("ReminderJobService", t)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }
}
