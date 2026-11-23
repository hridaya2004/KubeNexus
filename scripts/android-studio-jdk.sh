#!/usr/bin/env bash
# Print the path to Android Studio's bundled JDK (JBR), when one is installed.
#
# The Makefile uses this so command-line builds run on the same JVM as the IDE.
# Set JAVA_HOME (or ANDROID_STUDIO_JDK) to override.
set -euo pipefail

shopt -s nullglob

candidates=(
  "${ANDROID_STUDIO_JDK:-}"
  "${JAVA_HOME:-}"
  "$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr"
  "$HOME"/.local/share/JetBrains/Toolbox/apps/android-studio*/jbr
  "$HOME"/.local/share/JetBrains/Toolbox/apps/AndroidStudio*/ch-*/*/jbr
  "$HOME/Library/Application Support/JetBrains/Toolbox/apps/android-studio/jbr/Contents/Home"
  "$HOME"/Library/Application\ Support/JetBrains/Toolbox/apps/android-studio*/jbr/Contents/Home
  "$HOME"/android-studio*/jbr
  "$HOME"/Android/android-studio*/jbr
  "$HOME/Android Studio"*/jbr
  /opt/android-studio*/jbr
  /snap/android-studio/current/jbr
  "$HOME/Applications/Android Studio"*.app/Contents/jbr/Contents/Home
  "/Applications/Android Studio"*.app/Contents/jbr/Contents/Home
  "${LOCALAPPDATA:-}/Programs/Android Studio"*/jbr
  "${LOCALAPPDATA:-}/JetBrains/Toolbox/apps/android-studio"*/jbr
)

for jdk in "${candidates[@]}"; do
  [[ -n "$jdk" ]] || continue
  if [[ -x "$jdk/bin/javac" || -x "$jdk/bin/javac.exe" ]]; then
    printf '%s\n' "$jdk"
    exit 0
  fi
done
