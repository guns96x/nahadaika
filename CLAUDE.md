# Нагадайка — нотатки для розробки

Android (Kotlin 2.0, Compose, Room, CameraX, WorkManager, Vosk). Мова інтерфейсу, коментарів і тексту комітів у коді — українська; коментарі стислі, пояснюють «навіщо».

## Команди

- `./gradlew testDebugUnitTest` — усі тести обох варіантів (JVM + Robolectric); спільні лежать у `src/test`, лише для github — у `src/testGithub`. Знімки екранів пишуться в `app/build/screenshots` — переглядайте їх після змін UI.
- `./gradlew lintDebug` — lint обох варіантів (тести не аналізуються, `ignoreTestSources`).
- Два варіанти збірки: `github` (самооновлення з GitHub Releases, `src/github`) і `play` (без оновлень і без `INTERNET`, `src/play`). Код оновлень — лише в `src/github`, у main — фасад `SelfUpdate`, `UpdateBanner`, `UpdateSection` (у play порожні).
- `./gradlew assembleGithubRelease` / `assemblePlayRelease` — з `NAHADAIKA_KEYSTORE` і `NAHADAIKA_KEYSTORE_PASSWORD`, інакше debug-підпис.
- Maven Central інколи відповідає 429 — просто повторіть.

## Правила

- Усі видимі рядки — у ресурсах: англійська в `res/values/strings_*.xml` (базова), українська в `values-uk`; github-рядки — у `src/github/res`. У Compose — `stringResource`, поза ним — `Res.s`/`Res.plural`. Нова мова: папка `values-xx`, `resourceConfigurations` у `app/build.gradle.kts` і `res/xml/locales_config.xml`. Тести біжать з локаллю `uk` (`robolectric.properties`; у `@Config(qualifiers=...)` писати `uk-…`), англійську перевіряє `LocalizationTest`.

- Ключ підпису (`*.jks`, пароль) ніколи не комітити — репозиторій публічний.
- Будь-яка зміна `VoiceParser` — з тестами у `VoiceParserTest` (фіксований «зараз»: середа 30.09.2026 10:00, Europe/Kyiv).
- Зміна схеми Room — підняти версію в `AppDatabase` і додати міграцію (є тест міграції в `AlarmTest`).
- Robolectric не вміє влучати в елементи з асиметричними заокругленнями без `@GraphicsMode(NATIVE)`.
- Реліз: підняти `versionCode`/`versionName`, оновити `whatsnew.txt`, закомітити й `bash tools/release-local.sh` (ключ у `~/.nahadaika/`). Без ключа на ПК — просто push у `main`, CI створить реліз і патч.
