# Gradule

A calendar app for Android that reads and writes your **Google and on-device calendars**, with a UI that shifts
with the sky, in the spirit of [Gradient Weather](https://gradientweather.app/). The name is a play on
gradient + schedule.

Kotlin · Jetpack Compose · Material 3 · no accounts, no API keys.

## Views
- **Schedule** (default): a big hero for the selected day, then a continuous list of the days that have events.
- **Week**: swipeable hour grid with overlapping events side by side and a "now" line.
- **Month**: swipeable grid with event chips. Tap a day to jump to it in Schedule.

Tapping an event expands its card into a full-screen detail page (and shrinks back on close or back), the way
Google Calendar does.

Settings (theme: Sky, Material You, AMOLED black) are one tap away from every tab.

## How calendar access works
Gradule uses Android's built-in Calendar Provider (`CalendarContract`). Any Google account on your phone syncs its
calendars into that provider, so Gradule sees them automatically, and edits sync back through Android's own sync
adapter. It can also create a calendar that exists only on your phone.

## Get it running

### 1. Push to GitHub
```bash
git init && git add . && git commit -m "Gradule starter"
git branch -M main
git remote add origin https://github.com/<you>/gradule.git
git push -u origin main
```
The **Build APK** workflow runs automatically.

### 2. Install on your phone
After the workflow finishes, open the repo's **Releases** page on your phone, open **Latest debug build**, and
download `app-debug.apk`. Allow "install unknown apps" for your browser when asked. Every push to `main`
updates that release, and new builds install over the old one.

### 3. Vibe code in Codespaces
Create a Codespace from the repo. `.devcontainer/setup.sh` installs the Android SDK (takes a few minutes the first
time). Then:
```bash
./gradlew assembleDebug      # check it compiles
git commit -am "..." && git push   # CI builds your APK
```
Codespaces can't run an emulator, so the loop is: prompt, compile, push, install on your phone.
`AGENTS.md` gives Copilot/Claude Code the project rules and roadmap.

## Before you ship
- Change `applicationId`/`namespace` from `com.example.gradule`.
- Replace the debug keystore for release builds.
