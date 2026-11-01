package com.kodalabs.skydule.debug

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.PrintWriter
import java.io.StringWriter

/**
 * DEBUG BUILDS ONLY (registered in src/debug/AndroidManifest.xml, installed from SkyduleApp behind BuildConfig.DEBUG).
 * When the app crashes it shows the stack trace on screen, with Copy and Share buttons, so a crash on a phone
 * without a laptop can be reported exactly. Runs in its own process so it survives the crash, and uses plain views
 * so nothing in the app can stop it from appearing.
 */
class CrashReportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra(EXTRA_TRACE).orEmpty()
        val pad = (16 * resources.displayMetrics.density).toInt()
        val text = TextView(this).apply {
            this.text = trace
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
        }
        val copy = Button(this).apply {
            this.text = "Copy"
            setOnClickListener {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Skydule crash", trace))
                Toast.makeText(this@CrashReportActivity, "Copied", Toast.LENGTH_SHORT).show()
            }
        }
        val share = Button(this).apply {
            this.text = "Share"
            setOnClickListener {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, trace),
                        "Share crash",
                    ),
                )
            }
        }
        val title = TextView(this).apply { this.text = "Skydule crashed (debug build)"; textSize = 18f }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(copy); addView(share) }
        val scroll = ScrollView(this).apply { addView(text) }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad * 2, pad, pad)
                addView(title)
                addView(buttons)
                addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            },
        )
    }

    companion object {
        const val EXTRA_TRACE = "trace"

        /** Shows any uncaught crash in this screen, then lets Android end the process as usual. */
        fun install(app: Application) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                try {
                    val sw = StringWriter()
                    error.printStackTrace(PrintWriter(sw))
                    app.startActivity(
                        Intent(app, CrashReportActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            .putExtra(EXTRA_TRACE, "Thread: ${thread.name}\n\n$sw".take(50_000)),
                    )
                    Thread.sleep(1_500) // give the new screen a moment to start before this process goes
                } catch (_: Throwable) {
                }
                previous?.uncaughtException(thread, error)
            }
        }
    }
}
