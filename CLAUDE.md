# Нагадайка — нотатки для розробки

Android (Kotlin 2.0, Compose, Room, CameraX, WorkManager, Vosk). Мова інтерфейсу, коментарів і тексту комітів у коді — українська; коментарі стислі, пояснюють «навіщо».

## Команди

- `./gradlew testDebugUnitTest` — усі тести (JVM + Robolectric). Знімки екранів пишуться в `app/build/screenshots` — переглядайте їх після змін UI.
- `./gradlew lintDebug` — lint (тести не аналізуються, `ignoreTestSources`).
- `./gradlew assembleRelease` — з `NAHADAIKA_KEYSTORE` і `NAHADAIKA_KEYSTORE_PASSWORD`, інакше debug-підпис.
- Maven Central інколи відповідає 429 — просто повторіть.

## Правила

- Ключ підпису (`*.jks`, пароль) ніколи не комітити — репозиторій публічний.
- Будь-яка зміна `VoiceParser` — з тестами у `VoiceParserTest` (фіксований «зараз»: середа 30.09.2026 10:00, Europe/Kyiv).
- Зміна схеми Room — підняти версію в `AppDatabase` і додати міграцію (є тест міграції в `AlarmTest`).
- Robolectric не вміє влучати в елементи з асиметричними заокругленнями без `@GraphicsMode(NATIVE)`.
- Реліз: підняти `versionCode`/`versionName`, оновити `whatsnew.txt`, закомітити й `bash tools/release-local.sh` (ключ у `~/.nahadaika/`). Без ключа на ПК — просто push у `main`, CI створить реліз і патч.
