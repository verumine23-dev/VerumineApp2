#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.2"
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
GRADLE_DIR="$GRADLE_HOME/gradle-${GRADLE_VERSION}"
GRADLE_ZIP="$GRADLE_HOME/gradle-${GRADLE_VERSION}-bin.zip"

if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  mkdir -p "$GRADLE_HOME"
  if [ ! -f "$GRADLE_ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 -o "$GRADLE_ZIP" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$GRADLE_ZIP" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    else
      echo "Error: curl or wget is required to download Gradle." >&2
      exit 1
    fi
  fi
  rm -rf "$GRADLE_DIR"
  unzip -q "$GRADLE_ZIP" -d "$GRADLE_HOME"
fi

exec "$GRADLE_DIR/bin/gradle" "$@"
