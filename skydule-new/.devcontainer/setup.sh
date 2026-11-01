#!/usr/bin/env bash
# Installs the Android SDK command-line tools + the packages this project needs.
set -e
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
mkdir -p "$SDK/cmdline-tools"

if [ ! -d "$SDK/cmdline-tools/latest" ]; then
  curl -fsSL -o /tmp/cmdtools.zip \
    https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q /tmp/cmdtools.zip -d /tmp/cmdtools
  mv /tmp/cmdtools/cmdline-tools "$SDK/cmdline-tools/latest"
  rm -rf /tmp/cmdtools /tmp/cmdtools.zip
fi

yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-35" "build-tools;35.0.0"

echo "sdk.dir=$SDK" > local.properties
chmod +x gradlew
echo "Android SDK ready. Try: ./gradlew assembleDebug"
