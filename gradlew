#!/usr/bin/env sh
# Small wrapper bootstrapper for environments where the Gradle wrapper JAR is not bundled.
set -eu
GRADLE_VERSION=9.5.1
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/modelanchor-bootstrap"
DIST="$BASE/gradle-$GRADLE_VERSION"
ZIP="$BASE/gradle-$GRADLE_VERSION-bin.zip"
if [ ! -x "$DIST/bin/gradle" ]; then
  mkdir -p "$BASE"
  if [ ! -f "$ZIP" ]; then
    URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    echo "Downloading Gradle $GRADLE_VERSION..."
    if command -v curl >/dev/null 2>&1; then curl -fL "$URL" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then wget -O "$ZIP" "$URL"
    else echo "Install curl or wget first." >&2; exit 1
    fi
  fi
  if ! command -v unzip >/dev/null 2>&1; then echo "Install unzip first." >&2; exit 1; fi
  unzip -q -o "$ZIP" -d "$BASE"
fi
exec "$DIST/bin/gradle" "$@"
