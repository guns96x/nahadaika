#!/usr/bin/env bash
# Створює GitHub Release з APK, патчем від попередньої версії та update.json —
# звідти застосунок бере оновлення. Запускається в CI з теки reminder-app.
set -euo pipefail

APK=app/build/outputs/apk/release/app-release.apk
AAPT="$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -1)aapt"
BADGING="$("$AAPT" dump badging "$APK")"
CODE="$(sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" <<<"$BADGING" | head -1)"
NAME="$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$BADGING" | head -1)"
TAG="v$NAME"

if gh release view "$TAG" >/dev/null 2>&1; then
  echo "Реліз $TAG уже існує — пропускаю."
  exit 0
fi

# Що нового — з whatsnew.txt (українською), інакше з опису коміту.
if [ -s whatsnew.txt ]; then
  NOTES="$(cat whatsnew.txt)"
else
  NOTES="$(git log -1 --pretty=%B | grep -v -E '^(Co-Authored-By|Claude-Session):' | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}')"
fi
rm -rf out prev && mkdir -p out prev
cp "$APK" "out/Nahadaika-$NAME.apk"

PREV_TAG="$(gh release list --limit 1 --exclude-drafts --exclude-pre-releases --json tagName -q '.[0].tagName' || true)"
if [ -n "$PREV_TAG" ]; then
  gh release download "$PREV_TAG" -p 'update.json' -p 'Nahadaika-*.apk' -D prev || true
fi

python3 tools/make_update.py --apk "out/Nahadaika-$NAME.apk" --code "$CODE" --name "$NAME" \
  --notes "$NOTES" --prev-dir prev --out out

gh release create "$TAG" out/* --title "Нагадайка $NAME" --notes "$NOTES" --target "$GITHUB_SHA"
