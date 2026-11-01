package com.example.gradule

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.gradule.widgets.EXTRA_OPEN_DAY
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.gradule.ui.SplashScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.gradule.icons.DayIcon
import com.example.gradule.reminders.EXTRA_EVENT_BEGIN
import com.example.gradule.reminders.EXTRA_EVENT_ID
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gradule.ui.GraduleTheme
import com.example.gradule.ui.LandingScreen
import com.example.gradule.ui.MainScreen
import com.example.gradule.ui.SkyBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ordinary phones stay in portrait: landscape is too short for the side card and looks cramped. Foldables and
        // tablets rotate freely.
        val roomy = packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle") ||
            resources.configuration.smallestScreenWidthDp >= 600
        if (!roomy) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        // Plan the next icon change. The switch itself waits until the app is out of sight (see onStop): changing
        // the launcher alias the app was started from closes it.
        DayIcon.scheduleNext(this)
        // Our backgrounds are always dark-ish, so keep system bar icons light.
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(transparent),
            navigationBarStyle = SystemBarStyle.dark(transparent),
        )
        handleIntent(intent)
        // Someone tapping a reminder wants the event now, not a branded pause.
        val skipSplash = intent?.hasExtra(EXTRA_EVENT_ID) == true || intent?.hasExtra(EXTRA_OPEN_DAY) == true
        setContent { LaunchGate(skipSplash) }
    }

    override fun onStart() {
        super.onStart()
        DayIcon.appVisible = true
    }

    override fun onStop() {
        super.onStop()
        DayIcon.appVisible = false
        // Now it's safe: bring the launcher icon up to date with the choice, date and time of day.
        DayIcon.apply(this)
        DayIcon.scheduleNext(this)
        // Widgets pick up anything changed while the app was open (week start, calendars shown).
        com.example.gradule.widgets.Widgets.refresh(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val id = i?.getLongExtra(EXTRA_EVENT_ID, -1L) ?: -1L
        if (id != -1L) openEventRequests.value = OpenEventRequest(id, i?.getLongExtra(EXTRA_EVENT_BEGIN, -1L) ?: -1L)
        // A tap on a home-screen widget: open Schedule on that day.
        val day = i?.getLongExtra(EXTRA_OPEN_DAY, Long.MIN_VALUE) ?: Long.MIN_VALUE
        if (day != Long.MIN_VALUE) openDayRequests.value = day
    }

    companion object {
        /** Set when a reminder notification is tapped; MainScreen opens that event and clears it. */
        val openEventRequests = MutableStateFlow<OpenEventRequest?>(null)

        /** Set when a widget is tapped (a LocalDate epoch day); MainScreen shows Schedule on that day and clears it. */
        val openDayRequests = MutableStateFlow<Long?>(null)
    }
}

private val calendarPermissions = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

private fun hasCalendarPermission(context: Context) = calendarPermissions.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

/** How long the splash stays up after the first frame, so the launch reads as deliberate rather than a glitch. */
private const val SPLASH_MIN_MS = 1600L

/** Extra time the splash may wait for the first events before showing the app anyway. */
private const val SPLASH_MAX_EXTRA_MS = 2500L

/** Holds the Skydule splash for a moment, then fades into the app. */
@Composable
private fun LaunchGate(skipSplash: Boolean, vm: MainViewModel = viewModel()) {
    // Saveable, so rotating or a process restore doesn't replay the splash.
    var splashDone by rememberSaveable { mutableStateOf(skipSplash) }
    // Warm-up: subscribing here starts reading the calendar while the splash is up, so the first screen has its
    // events already. (Without a subscriber the loading wouldn't begin until the app screen appeared.)
    vm.loadedEvents.collectAsStateWithLifecycle()
    vm.calendars.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (!splashDone) {
            delay(SPLASH_MIN_MS)
            // Hold a little longer if the events aren't in yet, but never more than a few seconds.
            withTimeoutOrNull(SPLASH_MAX_EXTRA_MS) { vm.eventsLoaded.first { it } }
            splashDone = true
        }
    }
    Crossfade(targetState = splashDone, animationSpec = tween(450), label = "launch") { done ->
        if (done) GraduleApp() else SplashScreen()
    }
}

@Composable
private fun GraduleApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val mode by vm.themeMode.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val granted by vm.hasPermission.collectAsStateWithLifecycle()
    var denied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val ok = result.values.all { it }
        vm.setPermission(ok)
        denied = !ok
    }

    // Reminders need notification permission on Android 13+. Ask once calendar access is granted.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(granted) {
        if (granted && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Re-check on every resume so returning from system settings works.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.setPermission(hasCalendarPermission(context))
        vm.refreshWeather(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    GraduleTheme(mode) {
        SkyBackground(mode) {
            if (granted) {
                MainScreen(vm)
            } else {
                LandingScreen(
                    denied = denied,
                    onRequest = { launcher.launch(calendarPermissions) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                )
            }
        }
    }
}

/** A tapped reminder: which event, and which occurrence of it (its start in millis; -1 if unknown). */
data class OpenEventRequest(val id: Long, val begin: Long)
