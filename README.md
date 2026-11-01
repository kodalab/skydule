# Skydule

A calendar for Android with a sky-coloured interface. It reads and writes the calendars already on your phone, including Google calendars synced to the device, and needs no account, no sign-in and no API keys. Free, with no ads.

Built with Kotlin, Jetpack Compose and Material 3.

## Features

- **Schedule**: a large header for the selected day, then a continuous list of the days that have events.
- **Week**: a swipeable hour grid, with overlapping events side by side and a line showing the current time.
- **Month**: a swipeable full-month grid with titled event chips. Tapping a day slides up a panel listing that day's events, with a + button to add one. Tap the day again to close it.
- **Event details and editing**: tap an event to see its details, then edit it in place. Create events with the + button. Reminders are supported.
- **Themes**: Sky (changes with the time of day), Material You, and AMOLED black.
- **App icon**: the default icon, or a dynamic one that shows today's date.
- **Foldables and tablets**: on wide screens the event card slides in from the left or right, as you choose in Settings, and the list narrows to make room. Phones stay in portrait with the card opening full screen.

## How calendar access works

Skydule uses Android's built-in Calendar Provider (`CalendarContract`). Any account whose calendars are synced to the phone appears in the app automatically, and edits sync back through Android's own sync adapter. Skydule can also create a calendar that exists only on the device.

The app makes no network requests for your calendar data. There is no Google Calendar API, OAuth or backend. See the [privacy policy](https://kodalab.github.io/skydule/) for details.

## Project layout

| Path | Contents |
| --- | --- |
| `app/src/main/java/com/kodalabs/skydule/` | App source (the package name predates the Skydule name) |
| `app/src/main/java/com/kodalabs/skydule/ui/` | Compose screens and components |
| `app/src/main/java/com/kodalabs/skydule/data/` | Calendar access and settings storage |
| `tools/` | Scripts that generate the launcher icons |
| `docs/` | Privacy policy, Play Store listing, data safety answers, release guide |
| `store/` | Play Store graphics |
| `.github/workflows/` | CI: debug build on every push, signed release bundle on demand |

`AGENTS.md` describes the architecture and the project rules for contributors and coding assistants. `CLAUDE.md` points Claude Code at it.

## Building

Requirements: JDK 17 and the Android SDK (compileSdk 36).

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
./gradlew bundleRelease        # release bundle (needs signing config, see below)
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Minimum Android version is 8.0 (API 26).

## Continuous integration

- **Build** runs on every push to `main`, compiles the app, and publishes the debug APK to the rolling `latest` GitHub release (it is also kept as a build artifact).
- **Release** builds a signed Android App Bundle for Google Play. It runs when you start it by hand, or when you push a tag that starts with `v`. It needs four repository secrets: `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_ALIAS` and `UPLOAD_KEY_PASSWORD`.

The full release procedure is in [`docs/RELEASING.md`](docs/RELEASING.md).

## Signing

Release bundles are signed with an upload key. Keep the keystore and `keystore.properties` out of version control; both are ignored by git. For local release builds, create `keystore.properties` next to the project with the keystore path and credentials, as described in `docs/RELEASING.md`.

## Contact

getskydule@gmail.com
