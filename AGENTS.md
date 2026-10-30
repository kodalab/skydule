# Skydule: context for AI coding agents

Android calendar app (Kotlin, Jetpack Compose, Material 3). Single module `:app`. Store package `com.gregjameshoward.skydule`
(`applicationId`, can't change after the first upload); code namespace still `com.example.gradule`.
Named "Skydule" (sky + schedule). It was called Gradule and moved off that name and off Gradient Weather's look.

## Hard rules
- Calendar data goes through `CalendarContract` (Calendar Provider) only, wrapped by `data/CalendarRepository.kt`.
  Do NOT add Google Calendar API / OAuth. Google-account calendars sync into the provider on-device.
- Never put provider queries in composables. Flow: Repository -> `MainViewModel` (StateFlow) -> UI.
- Provider calls run on `Dispatchers.IO` and catch provider failures (`SecurityException` and others; log, never crash, rethrow `CancellationException`). `saveEvent` writes event + reminders in one `applyBatch` and returns null if an update changed nothing.
- All-day events are UTC midnight-to-midnight in the provider. Derive their calendar dates in UTC, timed events in
  the device zone (see `CalendarRepository.events`). Do not "fix" this by reading all-day events in local time.
- minSdk 26: `java.time` is available, no desugaring needed.

## App structure
- Three tabs in a floating glass bar (`ui/MainScreen.kt`): **Schedule** (default), **Week**, **Month**.
- One shared `selectedDate` in `MainViewModel`. Month/Week pagers follow it and update it on swipe; tapping a day in
  Week or Month sets it and switches to Schedule, which scrolls to that day.
- Events are loaded for a `DateWindow` (`MainViewModel.window`) and bucketed by day (`eventsByDay`). Tabs call
  `ensureCovers` when paging, and Schedule calls `extendForward`/`extendBackward` at its list ends.
- Settings are a full page (`ui/SettingsScreen.kt`) that slides in from any tab's gear. Values live in
  `data/SettingsStore.kt` (DataStore) and are exposed as StateFlows on `MainViewModel`: theme, week start (the Week and
  Month tabs are keyed on it), show weather, default alert for new events, temperature unit.
- The tab bar (`ui/GlassTabBar.kt`) switches on tap or on a horizontal drag across it. The selection is a translucent pill that slides between tabs. (The earlier "liquid glass" stretch effect and its settings were removed on purpose: it wasn't smooth. Don't re-add.)
- Layout: `MainScreen` is a plain Box, not a Scaffold. Content sits below the status bar (`statusBarsPadding`; Scaffold used to do this) and the tab bar and plus button float over it. The bar's measured height is exposed as `LocalBarInset` (`ui/BottomBlur.kt`); Week and Month pad their cards by it, and Schedule scrolls underneath it. `BottomBlur` wraps Schedule only: along the bar's height (+28dp) the list crossfades into a blurred, slightly darkened copy of itself (graphicsLayer capture + RenderEffect blur, Android 12+; older phones get the darkened fade only). The list also fades softly under the header (22dp).
- Wide screens (600dp+ wide and 480dp+ tall, e.g. an unfolded foldable or tablet): `MainScreen` shows two panes. The list/tabs fill one, and a floating frosted card (`DetailPane`, on the side chosen by `SettingsStore.detailSide`, default right) holds the event page or editor. `PaneMode` (ui/Transform.kt): SLIDE on every tab (full-width content; the card slides in from the chosen side and the content narrows to its edge, then widens again on close), NONE on phones. In a card the page uses the same neutral glass as the empty state (`endColor` transparent, no scrim, `pageInsets()` skips the system-bar padding). `EventDetailOverlay`/`EventEditorOverlay` are `movableContentOf`, so a half-written event survives folding and unfolding. The activity handles `configChanges` itself (manifest), so fold/rotate does not recreate it. The side option in Settings shows only when `MainViewModel.supportsTwoPanes` (hinge sensor or 600dp+ shortest side). Phones and cover screens keep the full-screen container transform below.
- Tapping an event opens the detail page (`ui/EventDetail.kt`) as a container transform, like Google Calendar: the
  tapped card's root bounds (captured by `Modifier.clickableWithBounds` in `ui/Common.kt`) animate to full screen and
  back. `MainScreen` owns one `Animatable` progress (0 = card, 1 = full screen); the overlay reads it. Any new
  tappable event element should use `clickableWithBounds` and pass its rect to `EventOpener`.
- The detail page is tinted from the event's calendar colour and has close + delete (edit is on the roadmap).
- Editing: the detail page's pencil turns into the editor in place (`MainScreen` `onEdit`). The editor takes a
  `NewEvent` draft from `CalendarRepository.draftFor` and saves with `saveEvent`, which updates when `id` is set and
  replaces the reminder rows. Recurring events are edited as a whole series (no "this occurrence only" yet).
- The FAB is an icon-only "+" on every tab. It (and a tap on an empty Week time slot) opens the new-event editor (`ui/EventEditor.kt`) through the same
  `TransformSheet` (`ui/Transform.kt`) container transform. It saves via `MainViewModel.createEvent(NewEvent)`
  (repeat via RRULE+DURATION, reminders, availability, visibility). Not done: guests, time zone, Tasks/Birthdays.
- Reminders: the editor stores any number of minutes-before values as `Reminders` rows (presets + Custom…).
  `reminders/Reminders.kt` plans one exact AlarmManager alarm per upcoming notification (14-day horizon, 200 max),
  replans on calendar changes (debounced in `MainViewModel`), after boot, and after each fired alarm. Notifications
  go to the "event_reminders" channel; tapping one sets `MainActivity.openEventRequests`, which opens the event.
  The app does NOT rely on the stock Calendar/Google Calendar app to show alerts.
  Notifications have two buttons: **Snooze** re-shows the same notification after `SettingsStore.snoozeLength`
  (5/10/15/30 min, set under Settings → Reminders) via a separate alarm keyed `snooze:<key>`, which `reschedule`
  leaves alone. **Dismiss** cancels the notification and any pending snooze alarm for it.
- The Schedule hero is always TODAY (not the selected day) with a weather pill beside the date (`ui/WeatherViews.kt`,
  `data/Weather*.kt`, `data/LocationProvider.kt`; Open-Meteo, coarse location, requested when the pill is tapped).
  A "Go to today" pill is on every tab; `MainViewModel.todayTick` makes Schedule animate-scroll to today.

- Static icon (Play listing, Settings, fallback): "Skydule" in IBM Plex Serif Bold with a small sun on the sunrise gradient, as an adaptive icon built from vector outlines. Generated by `tools/generate_default_icon.py` (font in `tools/fonts/`, licence in `third_party/IBMPlex-OFL.txt`), which also writes `store/icon-{512,1024,2048}.png` for the Play listing.
- App icon choice (Settings > Appearance > App icon, `ui/IconPickerScreen.kt`, with previews): **Default** (the static icon above) or **Dynamic**
  (`icons/DayIcon.kt`, month and day in IBM Plex Serif Bold like the static icon; month tinted 80% toward white from the sky's lighter end, day pure white; one foreground per period): today's short month and day over a sky for the time of day: dawn 05-08, day 08-17, dusk 17-20,
  night 20-05 (`IconPeriod`; sky colours from `ui/SkyBackground.kt` at fixed hours). Launchers can't draw icons live, so
  `tools/generate_day_icons.py` pre-makes 366 dates x 4 periods = 1,464 small adaptive vector icons
  (`res/mipmap-anydpi/ic_dyn_<period>_MM_DD.xml`, 366 shared foregrounds, 4 shared backgrounds) and one `activity-alias`
  each (`A<period>M<mm>D<dd>`, plus `DefaultIcon`) between the markers in `AndroidManifest.xml`. Exactly one alias is
  enabled. `DayIcon.apply` enables the new alias before disabling the old one (`DONT_KILL_APP`); `DayIconReceiver` runs at
  each period boundary/midnight (inexact alarm, only while Dynamic is chosen) and `MainActivity.onCreate` runs it too.
  The choice is kept in SharedPreferences (`gradule_day_icon`), not DataStore, because the receiver needs it synchronously.
  Re-run the generator after changing the sky palette or design. The main activity has no launcher filter; aliases provide it.

- Launch: a system splash on Android 12+ (`values-v31/themes.xml`: plain night colour, empty icon, since the system
  splash can only take a static colour). Then `MainActivity.LaunchGate` shows `ui/SplashScreen.kt` for `SPLASH_MIN_MS`
  (1.6 s) and crossfades into the app. The in-app splash uses the same time-of-day sky as the background
  (`skyColors`/`currentHour` in `ui/SkyBackground.kt`) and the same IBM Plex Serif Bold wordmark and little sun as the app icon (`res/font/ibmplexserif_bold.ttf`, OFL). Until calendar access is granted, `GraduleApp` shows the landing page (`ui/LandingScreen.kt`)
  with **Get started**, which requests calendar access. If access was denied, it also offers the app settings.

- Startup: `MainViewModel` knows calendar permission from the first instant, and `LaunchGate` subscribes to `eventsByDay`/`calendars` during the splash and holds it (up to 2.5 s extra) until `eventsLoaded`, so the first screen already has its events. The first load is not debounced; later change bursts are (250 ms). No separate cache: the Calendar Provider is already on-device, and a copy would only add staleness and a second place holding calendar data.
- Privacy policy: in-app text in `ui/PrivacyScreen.kt` (opened from Settings > About) and `docs/PRIVACY.md`. Keep them in step. The same text is the public page `docs/index.html` (GitHub Pages, served from /docs). Contact: getskydule@gmail.com.

## Design language (inspired by Gradient Weather)
- UI "shifts with the sky": full-screen gradient driven by time of day (`ui/SkyBackground.kt`),
  later by real weather (Open-Meteo, no API key).
- Always white text on mid-to-dark gradients. Glass cards: `Glass` (white 13%), 28dp corners.
  Dense grids use the darker `GridGlass` so text stays legible on bright afternoon skies.
- Big light-weight display type, generous spacing, subtle motion. Theme modes: Sky (default), Dynamic sky, Material You, AMOLED.
- Dynamic sky (`ThemeMode.LIVE_SKY`): same time-of-day gradient, greyed by the current weather kind
  (`weatherLook` in `ui/SkyBackground.kt`) and with the glow dimmed. Falls back to plain Sky when weather is unknown.

- Repeat rules the presets can't express are kept as `Repeat.CUSTOM` + `NewEvent.customRule` when editing. Reminders of -1 (calendar default) are treated as 10 min.
- Snoozes are saved in prefs (`gradule_reminders`/`snoozes`) and re-armed by `ReminderScheduler.reschedule`; after boot it also shows reminders missed in the last 6 h. Unit tests: `./gradlew testDebugUnitTest`.

## Store release
- Sign with an upload key via untracked `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword). Without it release falls back to the debug key: never upload that. R8 is on for release. Backup rules exclude alarm/icon prefs.
- compileSdk/targetSdk are 36 (AGP 8.9.3, Gradle 8.11.1, build-tools 36.0.0), as Play requires. Still to do before upload: privacy policy + Data safety form.

## Build
- `./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`
- CI builds on every push and publishes the APK to the rolling `latest` GitHub release.
- Debug builds are signed with the committed `app/debug.keystore` so updates install over each other.

## Roadmap (pick one at a time)
1. Event edit screen (reuse EventEditor) and recurring-event delete options (this one / this and following / all)
2. Calendar visibility toggles (`Calendars.VISIBLE`) in Settings
3. Weather-tinted gradients (Open-Meteo) + weather emoji per day in the day headers
4. Long-press a time slot in Week to create an event there
5. Home-screen widget (Glance), custom colour themes, default reminder per calendar, reminders on the detail page

## Releasing
See `docs/RELEASING.md`. `.github/workflows/build.yml` makes the debug APK on every push; `.github/workflows/release.yml` makes the signed
`.aab` for Play from the upload key stored in GitHub secrets (`UPLOAD_KEYSTORE_BASE64`, `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_ALIAS`,
`UPLOAD_KEY_PASSWORD`). `versionCode` comes from `-PversionCode` (CI: run number + 100; default 1). Never commit keystore files.

- Background parity: `BottomBlur(meltContent)` draws the same bottom darkening on every tab; only Schedule melts its content into the blur (Week/Month keep content sharp above the bar). Schedule's look is the source of truth for the background.
- Orientation: `MainActivity.onCreate` locks ordinary phones (no hinge sensor, smallest width under 600dp) to portrait. Foldables and tablets rotate freely. Test the two-pane layout on a foldable or tablet emulator.
