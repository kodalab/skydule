package com.example.gradule.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Crash reporting for quality, not tracking. Uses Firebase Crashlytics when the build has a google-services.json
 * (see app/build.gradle.kts); otherwise every call here quietly does nothing.
 *
 * - Collection is off in the manifest and switched on here only when the user's setting allows it
 *   (Settings > Privacy > Send crash reports, on by default). The setting is kept in SharedPreferences so it can be
 *   read synchronously at start-up, before any crash can happen.
 * - No user id, no custom data, no analytics SDK. Errors the app handles itself are recorded as stripped-down
 *   non-fatals: the class name and the stack trace only, never the exception message, which could contain text from
 *   a calendar or a place search.
 */
object Diag {
    private const val PREFS = "gradule_diag"
    private const val KEY = "crash_reports"
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
        apply()
    }

    fun enabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        apply()
    }

    /** Records an error the app caught and handled, so quiet failures still show up. [tag] names where it happened. */
    fun record(tag: String, t: Throwable) {
        val ctx = app ?: return
        if (!enabled(ctx)) return
        crashlytics()?.let {
            try {
                it.recordException(RuntimeException("$tag: ${t.javaClass.name}").apply { stackTrace = t.stackTrace })
            } catch (_: Throwable) {
            }
        }
    }

    /** A problem with no exception (for example an HTTP status). */
    fun record(tag: String, detail: String) = record(tag, IllegalStateException(detail))

    private fun apply() {
        val ctx = app ?: return
        try {
            crashlytics()?.setCrashlyticsCollectionEnabled(enabled(ctx))
        } catch (_: Throwable) {
        }
    }

    private fun crashlytics(): FirebaseCrashlytics? {
        val ctx = app ?: return null
        return try {
            if (FirebaseApp.getApps(ctx).isEmpty()) null else FirebaseCrashlytics.getInstance()
        } catch (_: Throwable) {
            null
        }
    }
}
