# Releasing Skydule to Google Play

## One-time setup
1. **Upload key.** `skydule-upload.jks` (made once, kept safe outside the repo). Keep a backup. With Play App Signing,
   Google holds the real signing key, so a lost upload key can be reset through Play support.
2. **GitHub secrets** (repo Settings > Secrets and variables > Actions > New repository secret):
   - `UPLOAD_KEYSTORE_BASE64`: the keystore file, base64 encoded (one line)
   - `UPLOAD_STORE_PASSWORD`
   - `UPLOAD_KEY_ALIAS` (`upload`)
   - `UPLOAD_KEY_PASSWORD`
3. **Play Console:** create the app, then on the first upload choose "Use Play App Signing".

## Each release
1. Raise `versionName` in `app/build.gradle.kts` if the user-visible version changes. `versionCode` is set by CI from
   the run number plus 100, so it always goes up. A manual build falls back to the default in `app/build.gradle.kts`
   (currently 21, the last code uploaded by hand): pass `-PversionCode=<higher>` or raise that default. Play never lets a
   code be reused, even from a discarded draft. Add an entry to `CHANGELOG.md`.
2. GitHub > Actions > "Release bundle" > Run workflow (or push a tag such as `v1.0.0`).
3. Download `skydule-release-aab` from the finished run, unzip it, and upload `app-release.aab` in Play Console:
   Testing > Internal testing > Create new release.
4. Add testers (email addresses) under the Testers tab and install from the opt-in link.

## Checks
- Never upload a build made without the upload key: locally, a missing `keystore.properties` falls back to the debug key.
- Keep `keystore.properties`, `*.jks` and `*.keystore` out of git (they are ignored).

## Google Places key (location suggestions)
- The key is never committed. For store builds, add it in GitHub > Settings > Secrets and variables > Actions as `PLACES_API_KEY`; `release.yml` passes it to the build. For a local build, put `PLACES_API_KEY=...` in a `.env` file in the project root (git-ignored; template in `.env.example`).
- `build.yml` (the public rolling debug APK) deliberately has no key, so that build uses OpenStreetMap suggestions only.
- The key is still compiled into the app, so keep it restricted in Google Cloud: Places API (New) only, package `com.kodalabs.skydule` plus the Play app-signing SHA-1, a daily quota and a budget alert.

## Crash reporting (Firebase Crashlytics)
- In the Firebase console create a project, add an Android app with package `com.kodalabs.skydule`, download `google-services.json`, and open the Crashlytics page once to enable it.
- Store it as the GitHub secret `GOOGLE_SERVICES_JSON_BASE64` (`base64 -w0 google-services.json`). `release.yml` writes it to `app/google-services.json` for the build and deletes it afterwards. For a local build put the file at `app/google-services.json` (git-ignored).
- Without the secret the release still builds, just without crash reporting. Never commit the file.

