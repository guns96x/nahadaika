#!/usr/bin/env bash
# Випуск з ПК: тести, підписана збірка, push у main і GitHub Release (APK + патч + update.json).
# Ключ і пароль — поза репозиторієм (він публічний): ~/.nahadaika/nahadaika-release.jks і ~/.nahadaika/password.
# CI після push лише проганяє тести: реліз уже існує, тож tools/release.sh там пропускається.
set -euo pipefail
cd "$(dirname "$0")/.."

KEYDIR="${NAHADAIKA_KEYDIR:-$HOME/.nahadaika}"
export NAHADAIKA_KEYSTORE="$KEYDIR/nahadaika-release.jks"
NAHADAIKA_KEYSTORE_PASSWORD="$(tr -d '\r\n' < "$KEYDIR/password")"
export NAHADAIKA_KEYSTORE_PASSWORD
# Python на Windows інакше пише файли й читає аргументи в cp1251 — «що нового» ламається.
export PYTHONUTF8=1
export ANDROID_HOME="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="$(ls -d "/c/Program Files/Eclipse Adoptium/jdk-17"* 2>/dev/null | tail -1)"
  export JAVA_HOME
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Є незакомічені зміни — спершу закомітьте." >&2
  exit 1
fi

./gradlew testDebugUnitTest assembleRelease
git push origin HEAD:main
GITHUB_SHA="$(git rev-parse HEAD)" bash tools/release.sh
