# Skydule: context for AI coding agents

Android calendar app (Kotlin, Jetpack Compose, Material 3). Single module `:app`. Store package `com.kodalabs.skydule`
(`applicationId`, can't change after the first upload); code package is `com.kodalabs.skydule` too (renamed from the old working name in build 53; `data/LegacyPrefs.kt` copies the old `gradule_*` preference files once).
Named "Skydule" (sky + schedule).

## Hard rules
- Calendar data goes through `CalendarContract` (Calendar Provider) only, wrapped by `data/CalendarRepository.kt`.
  Do NOT add Google Calendar API / OAuth. Google-account calendars sync into the provider on-device.
- Never put provider queries in composables. Flow: Repository -> `MainViewModel` (StateFlow) -> UI.
- Provider calls run on `Dispatchers.IO` and catch provider failures (`SecurityException` and others; log, never crash, rethrow `CancellationException`). `saveEvent` writes event + reminders in one `applyBatch` and returns null if an update changed nothing.
- All-day events are UTC midnight-to-midnight in the provider. Derive their calendar dates in UTC, timed events in
  the device zone (see `CalendarRepository.events`). Do not "fix" this by reading all-day events in local time.
- minSdk 26: `java.time` is available, no desugaring needed.
- Editing never moves an event: `saveEvent` writes `CALENDAR_ID` only when creating (the columns written are decided in `data/EventValues.kt` `eventValueMap`, which is unit tested in `EventValuesTest`; moving an event between calendars would need delete + insert and is deliberately not offered). The editor shows an existing event's calendar locked, and the detail page hides Edit and Delete (with "Read-only calendar") when the event's calendar isn't writable (`MainScreen` `canModify`). On an edit, a blank location or description is written as null so it really clears.
- Saving and deleting report success (`MainViewModel.saveEvent/deleteEvent` return Boolean); the editor closes only on success and otherwise Toasts. `NewEvent.timeZoneId` keeps a timed event's `EVENT_TIMEZONE` on edit. `goToToday` shrinks an oversized loaded window (`shouldResetWindow`). The sky glow is static on purpose (no infinite animation). The dynamic icon was reviewed and is deliberately unchanged.
- `ReminderScheduler.reschedule` runs under a process-wide mutex (callers overlap). An edit is ONE `applyBatch` (event update with `withExpectedCount(1)`, reminder delete, inserts) so it rolls back whole. A reminder tap for an event that can't be found once its days are loaded clears the request with a toast. CI (`build.yml`) runs `testDebugUnitTest lintDebug assembleDebug`.
- Reminder planning is `planReminders` (pure, tested): arm future alerts, keep (never re-arm) due-but-pending ones for 1 h because Doze may hold them, cancel only keys no longer planned; it runs on IO under the mutex with a catch-all to `Diag`. Declined/cancelled events don't ring (`shouldRing`). Edits write back stored access level/availability when the user didn't change them, and replace only ALERT/DEFAULT reminder rows. Existing series can't be switched to "Does not repeat" (it would collapse onto the first date; per-occurrence editing is not built). The settings DataStore is excluded from backups (it holds local calendar ids). `MainScreen` `overlays` is a movableContentOf: call close functions through `rememberUpdatedState`, never directly.
- Calendar choices (hidden, colours) are stored by `calendarKey` (account type|name|sync id, or name for on-device calendars), never by local id, and resolved to this phone's ids in `SettingsStore.calendarPrefs` (re-resolved when calendars change). That is what makes the settings DataStore safe to include in Google/Samsung backups (it is NOT excluded). Pre-47 id-based values migrate on first read.
- UI tests: `app/src/androidTest` (Compose UI tests, `SkyduleJourneyTest`) run on an emulator: `./gradlew connectedDebugAndroidTest`, or the `ui-tests.yml` workflow (KVM + reactivecircus/android-emulator-runner, API 34). Each test seeds a throwaway on-device calendar and removes it. They have only been compile-checked off-emulator so far: expect first-run fixes. Detox is React Native only and is not used. `ProviderIntegrationTest` (same folder, no UI) runs the repository, settings and `ReminderScheduler` against the REAL Calendar Provider and AlarmManager (save/edit/delete, email reminders kept, expected-count rollback, durations, all-day dates, alert times, declined/hidden, calendar-key settings). Test ideas still open: snapshot tests (Paparazzi or Roborazzi, need state-hoisted screens) and a phone + foldable emulator matrix in `ui-tests.yml`.
- Reminder notifications: private on the lock screen (`setPublicVersion` shows only "Event reminder" + headline). Snoozed ones use `timedHeadline(snoozed = true)`. Snooze/dismiss/forget run under `ReminderScheduler.exclusive`. Calendar-default reminders (-1, METHOD_DEFAULT) stay -1 in the editor ("Calendar default") and are written back as the default; `CUSTOM_REMINDER` is `Int.MIN_VALUE` so it can't clash. The delete dialog asks `isModifiedOccurrence` (ORIGINAL_ID/ORIGINAL_SYNC_ID) before saying the whole series goes.
- Deleting a changed single occurrence (`isModifiedOccurrence`) sets STATUS_CANCELED instead of deleting the row (the original would come back); `events()` skips cancelled instances. Optional Instances columns (STATUS, SELF_ATTENDEE_STATUS) go through `queryInstances`, which retries without them if a provider refuses, so they can never blank the calendar. Calendars without a sync id are keyed by name (two same-named on-device calendars share a choice; renaming loses it) - accepted. Pre-47 id-based settings are migrated only on an updated install, never on a fresh one (`legacyIdsAreTrusted`).
- Debug builds install `debug/CrashReportActivity` (registered only in `src/debug/AndroidManifest.xml`, own process, plain views, installed first in `SkyduleApp` behind `BuildConfig.DEBUG`): an uncaught crash shows its stack trace with Copy/Share, for reporting from a phone. Keep the Gradient Weather `<queries>` entry in that same debug manifest.
- NEVER touch the Calendar Provider before READ_CALENDAR is granted, not even `registerContentObserver` (Android 16 throws SecurityException; this crashed fresh installs in builds 47-50). `CalendarRepository.changes()` tolerates it, `SettingsStore.calendarKeysFlow` and the VM's `prefsFlow` check permission first, and `LaunchWithoutPermissionTest` runs first-launch in CI on a fresh install.
- `SkyBackground` must keep ONE composition structure for every theme (no early `return` for AMOLED): a different shape moved the whole app's content in the tree and wiped its state (Settings closed on choosing AMOLED). `changingTheThemeKeepsSettingsOpen` covers it.
- Provider DURATION strings come in many shapes ("P3600S", "PT1H", "P1W", "P1DT2H"); `parseDurationMillis` reads them all. Don't reintroduce a seconds-and-days-only parser: an unreadable duration used to turn an edited recurring event into a 60-second one.
- Reminder minutes count back from the event start; for all-day events from LOCAL MIDNIGHT of the event's date (`reminderAnchorMillis`), as Android and Google Calendar store them ("1 day before at 9:00 AM" = 900). Only non-negative offsets are used (same-day-after-midnight can't be synced). The editor offers all-day presets and a days-before-plus-time custom dialog when All-day is on, and converts the notifications when the switch flips (`remindersForAllDay`/`remindersForTimed`). All-day notifications read "Today"/"Tomorrow"/"In N days" (`allDayHeadline`).

## App structure
- Three tabs in a floating glass bar (`ui/MainScreen.kt`): **Schedule** (default), **Week**, **Month**.
- One shared `selectedDate` in `MainViewModel`. Month/Week pagers follow it and update it on swipe; tapping a day in
  Week (tap a day header) opens the same kind of day panel as Month under the grid (`DayPanel`, shared; `weekPanelOpen` in `MainScreen`; tap the day again to close; tapping an event opens it). Month is a plain full-height grid: with the panel away each cell shows up to two titled chips and "+N"; as the panel opens (`reveal` 0 to 1 drives cell height and the swap) they fade into one dot per event. Tapping a day selects it and slides its events down in a panel under the grid (`DayPanel` in `ui/MonthTab.kt`); tapping the same day again closes the panel, another day swaps its contents, and tapping an event opens it. While the panel is open the floating + hides (`monthPanelOpen` in `MainScreen`) and a + in the panel header adds an event on that day.
- Schedule: days are told apart by the date column with a 22dp gap (no divider lines). A day with nothing on (today, or the selected day) shows `EmptyDayCard`: same shape as an event card, a sun outline instead of the colour bar, "Clear day / Tap to add an event"; tapping it opens the editor on that day. The hero says nothing when today is empty.
- Calendar settings (`ui/CalendarSettingsScreen.kt`, opened from the top row of Settings; the row is title and arrow only, and the explanation sits at the top of the screen so narrow screens such as a foldable's outer display keep the Settings row to one line): every calendar grouped by account with a show/hide switch and a colour (24-swatch palette, or a custom HSV/hex picker with recent colours). The choices are `CalendarPrefs` in `SettingsStore` (hidden ids, `id:argb` colour overrides, recent colours) and are applied inside `CalendarRepository.events`/`upcomingAlerts`, so every view and every reminder agrees. Hidden calendars drop their events and reminders; an event's own colour still beats the calendar override. Nothing is written to the Calendar Provider, so other apps are untouched. The new-event editor lists only visible calendars (`MainViewModel.editorCalendars`, all of them if everything is hidden).
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
  `reminders/Reminders.kt` plans one exact AlarmManager alarm per upcoming notification (14-day horizon, 200 max;
  event instances are fetched past the horizon by the longest reminder lead, `alertQueryEnd`, so a 3-week reminder
  isn't missed), replans on calendar changes (debounced in `MainViewModel`), after boot, and after each fired alarm.
  While the app is closed, `reminders/ReminderJobs.kt` keeps it right: a content-triggered JobScheduler job on
  `CalendarContract.CONTENT_URI` (alternating ids 7101/7103 because each run arms the other; content jobs can't be
  persisted) wakes `ReminderJobService` when a sync changes the calendar, and a persisted 6-hourly job (7102)
  re-plans the window and re-arms the content job. `ReminderJobs.ensure` runs on every process start
  (`SkyduleApp`), after boot/update (`ReminderReceiver`) and when calendar access is granted. A force-stop clears
  jobs until the app is opened again; that is an Android rule. Notifications
  go to the "event_reminders" channel; tapping one (launcher intent, like widgets, carrying `EXTRA_EVENT_BEGIN`) sets `MainActivity.openEventRequests` (`OpenEventRequest(id, begin)`), and `MainScreen` opens the matching occurrence, loading its days first if needed.
  The app does NOT rely on the stock Calendar/Google Calendar app to show alerts.
  Notifications have two buttons: **Snooze** re-shows the same notification after `SettingsStore.snoozeLength`
  (5/10/15/30 min, set under Settings → Reminders) via a separate alarm keyed `snooze:<key>`, which `reschedule`
  leaves alone. **Dismiss** cancels the notification and any pending snooze alarm for it.
- The Schedule hero is always TODAY (not the selected day) with a weather pill beside the date (`ui/WeatherViews.kt`,
  `data/Weather*.kt`, `data/LocationProvider.kt`; Open-Meteo, coarse location, requested when the pill is tapped).
  With permission granted but the phone's Location switch off, `refreshWeather` sets `WeatherState.LocationOff`; the pill reads "Turn on location" and opens system Location settings, and `ON_RESUME` reloads. 
  A "Go to today" pill is on every tab; `MainViewModel.todayTick` makes Schedule animate-scroll to today.

- Static icon (Play listing, Settings, fallback): "Skydule" in IBM Plex Serif Bold with a small sun on the sunrise gradient, as an adaptive icon built from vector outlines. Generated by `tools/generate_default_icon.py` (font in `tools/fonts/`, licence in `third_party/IBMPlex-OFL.txt`), which also writes `store/icon-{512,1024,2048}.png` for the Play listing.
- App icon choice (Settings > Appearance > App icon, `ui/IconPickerScreen.kt`, with previews): **Default** (the static icon above) or **Dynamic**
  (`icons/DayIcon.kt`, month and day in IBM Plex Serif Bold like the static icon; month tinted 80% toward white from the sky's lighter end, day pure white; one foreground per period): today's short month and day over a sky for the time of day: dawn 05-08, day 08-17, dusk 17-20,
  night 20-05 (`IconPeriod`; sky colours from `ui/SkyBackground.kt` at fixed hours). Launchers can't draw icons live, so
  `tools/generate_day_icons.py` pre-makes 366 dates x 4 periods = 1,464 small adaptive vector icons
  (`res/mipmap-anydpi/ic_dyn_<period>_MM_DD.xml`, 366 shared foregrounds, 4 shared backgrounds) and one `activity-alias`
  each (`A<period>M<mm>D<dd>`, plus `DefaultIcon`) between the markers in `AndroidManifest.xml`. Exactly one alias is
  enabled. `DayIcon.apply` enables the new alias before disabling the old one (`DONT_KILL_APP`), but only runs while the app is out of sight (`MainActivity.onStop`; `DayIcon.appVisible` makes the receiver skip it while the app is open), because disabling the alias the app was launched from closes the running app; `DayIconReceiver` runs at
  each period boundary/midnight (inexact alarm, only while Dynamic is chosen) and `MainActivity.onStop` runs it too.
  The choice is kept in SharedPreferences (`skydule_day_icon`), not DataStore, because the receiver needs it synchronously.
  Re-run the generator after changing the sky palette or design. The main activity has no launcher filter; aliases provide it.

- Launch: a system splash on Android 12+ (`values-v31/themes.xml`: plain night colour, empty icon, since the system
  splash can only take a static colour). Then `MainActivity.LaunchGate` shows `ui/SplashScreen.kt` for `SPLASH_MIN_MS`
  (1.6 s) and crossfades into the app. The in-app splash uses the same time-of-day sky as the background
  (`skyColors`/`currentHour` in `ui/SkyBackground.kt`) and the same IBM Plex Serif Bold wordmark and little sun as the app icon (`res/font/ibmplexserif_bold.ttf`, OFL). Until calendar access is granted, `SkyduleScreen` shows the landing page (`ui/LandingScreen.kt`)
  with **Get started**, which requests calendar access. If access was denied, it also offers the app settings.

- Loaded events: `MainViewModel.loadedEvents` carries the events AND the window they were loaded for in one emission (`LoadedEvents`); it is the only source for "is this day loaded" (`eventsByDay` is gone, so "loaded" and "the events" can't disagree). Tapped reminders are resolved by the pure `resolveOpenRequest` (`ui/OpenRequest.kt`, `OpenRequestTest`).
- Startup: `MainViewModel` knows calendar permission from the first instant, and `LaunchGate` subscribes to `eventsByDay`/`calendars` during the splash and holds it (up to 2.5 s extra) until `eventsLoaded`, so the first screen already has its events. The first load is not debounced; later change bursts are (250 ms). No separate cache: the Calendar Provider is already on-device, and a copy would only add staleness and a second place holding calendar data.
- Privacy policy: in-app text in `ui/PrivacyScreen.kt` (opened from Settings > About) and `docs/PRIVACY.md`. Keep them in step. The same text is the public page `docs/index.html` (GitHub Pages, served from /docs). Contact: getskydule@gmail.com.

## Design language (inspired by Gradient Weather)
- UI "shifts with the sky": full-screen gradient driven by time of day (`ui/SkyBackground.kt`),
  later by real weather (Open-Meteo, no API key).
- Always white text on mid-to-dark gradients. Glass cards: `Glass` (white 13%), 28dp corners.
  Dense grids use the darker `GridGlass` so text stays legible on bright afternoon skies.
- Big light-weight display type, generous spacing, subtle motion. Theme modes: Sky (default), Material You, AMOLED.
- There is no weather-tinted "Dynamic sky" theme (removed on purpose, with its code). A stored `LIVE_SKY` value from an old build falls back to Sky.

- Repeat rules the presets can't express are kept as `Repeat.CUSTOM` + `NewEvent.customRule` when editing. Reminders of -1 (calendar default) are treated as 10 min.
- Snoozes are saved in prefs (`skydule_reminders`/`snoozes`) and re-armed by `ReminderScheduler.reschedule`; after boot it also shows reminders missed in the last 6 h. Unit tests: `./gradlew testDebugUnitTest`.

## Store release
- Sign with an upload key via untracked `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword). Without it release falls back to the debug key: never upload that. R8 is on for release. Backup rules exclude alarm/icon prefs.
- compileSdk/targetSdk are 36 (AGP 8.9.3, Gradle 8.11.1, build-tools 36.0.0), as Play requires. Still to do before upload: privacy policy + Data safety form.

## Build
- `./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`
- CI builds on every push and publishes the APK to the rolling `latest` GitHub release.
- Debug builds are signed with the committed `app/debug.keystore` so updates install over each other.

## Roadmap (pick one at a time)
1. Event edit screen (reuse EventEditor) and recurring-event delete options (this one / this and following / all)
2. (done) Calendar visibility and colour in Settings, stored in Skydule only. Next: per-event colours.
3. Weather-tinted gradients (Open-Meteo) + weather emoji per day in the day headers
4. Long-press a time slot in Week to create an event there
5. (done: Next up, Today's agenda, Week strip; see Widgets below) More widgets (month, schedule list), custom colour themes, default reminder per calendar, reminders on the detail page
6. Idea, not committed: Material 3 Expressive as an optional style (a shapes/type/motion change, not just colour, so a bigger job than the colour themes). Wait for tester demand and check how stable the Compose Expressive APIs are first. Meanwhile, borrow its motion and shape ideas for everyone: Settings and FAB transitions need rounded corners, no diagonal drift, and edge-to-edge at the bottom (tester feedback from a UX motion designer).

## Releasing
See `docs/RELEASING.md`. `.github/workflows/build.yml` makes the debug APK on every push; `.github/workflows/release.yml` makes the signed
`.aab` for Play from the upload key stored in GitHub secrets (`UPLOAD_KEYSTORE_BASE64`, `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_ALIAS`,
`UPLOAD_KEY_PASSWORD`). `versionCode` comes from `-PversionCode` (CI: run number + 100; default 1). Never commit keystore files.

- Background parity: `BottomBlur(meltContent)` draws the same bottom darkening on every tab; only Schedule melts its content into the blur (Week/Month keep content sharp above the bar). Schedule's look is the source of truth for the background.
- Orientation: `MainActivity.onCreate` locks ordinary phones (no hinge sensor, smallest width under 600dp) to portrait. Foldables and tablets rotate freely. Test the two-pane layout on a foldable or tablet emulator.

- Narrow screens (a foldable's cover display, about 360dp): the Week title drops the repeated month ("25 – 31 Oct"; "28 Oct – 3 Nov" across months); `CompactHeader` (Week/Month) keeps the title on one line and shrinks it to fit, and "Go to today" becomes "Today" under 400dp wide.
- Event chips (Month cells, Week all-day strip): use `chipTextStyle` (line = font size, centred, no font padding) and `chipHeight` (grows with the system font size). Plain `Text` in a fixed 16dp box sat low and was clipped on Samsung phones.
- Full-page screens (Settings, Calendar settings) run edge to edge at the bottom: no `navigationBarsPadding` on the page, `NavBarSpacer()` at the end of the scroll so the last row clears the nav bar, and `BottomEdgeFade` so content fades under the gesture handle. The Settings container transform keeps its corners rounded until the last quarter (`TransformSheet(cornerHold)`).
- Settings is a full-screen page that slides in from the right and back out (`settingsProgress`, `Motion.pageOpen()/pageClose()`, with a light scrim); it no longer grows from the gear.
- `TransformSheet` (every grow-from-a-button page: event page, editor): corners stay rounded for the first 70% (`cornerHold`), width eases out ahead of height so the shape doesn't turn into a tall thin slab, and content fades in only after 55% so it isn't seen cropped. Judge changes with the animation scales at 5x in Developer options.

## Place suggestions
- Event Location field (`ui/EventEditor.kt`) -> `data/PlaceSearch.kt`. Google Places (New) over plain HTTPS (no SDK) when `BuildConfig.PLACES_API_KEY` is set; Photon (OpenStreetMap, no key) otherwise, and as the fallback when Google fails, returns nothing or a limit is hit. Results are biased to the phone's coarse location when allowed. Reason: OpenStreetMap lacked home addresses and many businesses (tester report).
- Key: never committed. It comes from the `PLACES_API_KEY` env var (GitHub Actions secret, passed to `release.yml` only; `build.yml` deliberately does not get it because it publishes a downloadable debug APK) or a git-ignored `.env` file (`PLACES_API_KEY=...`, template `.env.example`). It is still compiled into the app, so the Google Cloud restrictions below are what protect it. Restrict it in Google Cloud to package `com.kodalabs.skydule` plus the Play app-signing SHA-1 (and the upload key's, for sideloads), enable only Places API (New), and set a daily quota and budget alert. The app sends `X-Android-Package` and `X-Android-Cert` (computed from the installed signature) with each request, which restricted keys require.
- Cost control: one session token per typing session, closed by a Place Details call that asks for the place id only when a suggestion is picked (`PlaceSearch.finish`); in-memory cache; max 25 Google requests per session and 300 per day per phone, 450 ms typing pause before a request (`skydule_places` prefs), then Photon. The text "Google Maps" (exact wording, 12sp, solid white, bottom left under the list; Google's attribution policy for Places results shown without a map) shows under Google suggestions; don't reword or fade it.
- Privacy text, Data safety notes and the Settings "Data sources" row mention Google; keep them in step.

## Crash reports and feedback
- Firebase Crashlytics, optional per build: the Gradle plugins and `google-services` apply only if `app/google-services.json` exists (git-ignored; `release.yml` writes it from the `GOOGLE_SERVICES_JSON_BASE64` secret; `build.yml` does not, so the public debug APK has no reporting). Without the file everything still builds and `Diag` does nothing.
- `data/Diag.kt` + `SkyduleApp`: collection is off in the manifest and switched on at start-up from the user's setting (Settings > About > Send crash reports, SharedPreferences `skydule_diag`, on by default). No user id, no custom keys, no analytics SDK. `Diag.record` logs handled errors (provider failures, place search, widgets) as non-fatals with the class name and stack only, never the message (could contain calendar or place text). Add it next to any new quiet `catch`.
- Settings > About > Send feedback opens the email app with a draft to getskydule@gmail.com and the app version, Android version and phone model pre-filled. No logs attached.
- Privacy text (in-app, `docs/PRIVACY.md`, `docs/index.html`), `docs/PLAY_DATA_SAFETY.md` and `docs/PLAY_LISTING.md` describe the crash reports; keep them in step.

## Widgets
- Three home-screen widgets in `widgets/Widgets.kt` (plain `RemoteViews`, no Glance, no new dependencies): **Next up** (2x1: square on a tall home-screen grid, resizable), **Today's agenda** (4x2, stretches taller to show up to 3 events) and **Week strip** (4x2). One fixed look: the dusk gradient (`res/drawable/widget_bg.xml`), white text, glass cards. No light/dark choice and no + button by decision; a tap only opens Skydule's Schedule on a day (`EXTRA_OPEN_DAY` -> `MainActivity.openDayRequests` -> `MainScreen`). Week strip day cells open that day; everything else opens today.
- Data comes from `CalendarRepository.events` with the same `CalendarPrefs` as the app, so hidden calendars stay hidden. No weather on the widgets (it would need background location).
- Refresh: `Widgets.refresh` runs on the system's 30-minute update, on time/date/timezone changes, after calendar changes (`MainViewModel`), whenever the app goes to the background (`MainActivity.onStop`), and from one inexact alarm set to the next event start/end or midnight. Without calendar access the widgets say "Open Skydule".
- Widget sizing: phone home-screen grids differ (on a Samsung S26 a 2x2 is a narrow tall tile, about 135x280dp, and a 4x2 is nearly square), so every widget reads its real height (`OPTION_APPWIDGET_MAX_HEIGHT`) and fills it: Next up gets bigger type, up to 4 title lines and a "Then" line when tall (190dp+); Today's agenda shows up to 6 events; Week strip lists up to 5 upcoming events under the days. Widget taps launch through the launcher intent (`getLaunchIntentForPackage`, i.e. the enabled icon alias) so Android reuses the running instance; launching `MainActivity` directly did not match an instance started from an alias.
- Widget layouts may only use classes RemoteViews allows (FrameLayout, LinearLayout, RelativeLayout, GridLayout, TextView, ImageView, Button, ImageButton, ProgressBar, Chronometer...). A plain `<View>` made Android refuse the whole widget ("Couldn't add widget"); use an empty `<FrameLayout>` as a spacer.

## Motion
- Small things move on springs defined in `ui/Motion.kt` (Material 3 Expressive style: spatial springs for position/size, effects springs for fades, each fast/default/slow, plus `panel()` for layout-driving sizes): the tab pill, the + button popping in and the Month day panel. Full-screen pages (Settings, event page, editor, the widescreen side card, `Motion.pageOpen()/pageClose()`) stay on timed curves, about 340 ms out and 260 ms back, because a stiff spring finished in about two frames in a screen recording, so the page no longer visibly grew; the sub-screen slides use Compose's default. Don't add ad hoc `tween`s for UI motion; pick a `Motion` spec so the app keeps one feel. Only ambient effects (sky colour fade, the splash crossfade) are timed on their own. The Compose Material `MotionScheme` is still experimental, so we copy its token values instead of depending on it.
