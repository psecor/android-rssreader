# RSS Reader (Android)

Native Android client for [psecor/rssreader](https://github.com/psecor/rssreader), a self-hosted Google-OAuth-gated RSS service. Built as a daily-use companion to the web app and as a Kotlin/Compose learning exercise.

## Status

V1 daily-use parity is feature-complete in debug builds: Google sign-in, feed and category browsing, article reading, mark read/unread (tap, swipe, or bulk), pull-to-refresh, periodic background sync, and search across title/description/author. Local-first via Room with delta sync and an offline write queue. Min SDK 26, target SDK 35.

Out of v1 scope: feed CRUD, category management, history view, reading stats. No release pipeline yet (signing, R8, real migrations are still to do).

## Build

You need the Android SDK and JDK 17. Open in Android Studio or build from the command line.

```bash
cp local.properties.example local.properties
# fill in sdk.dir, backendBaseUrl, googleWebClientId
./gradlew installDebug
```

`local.properties.example` documents the three required keys. Sign-in additionally requires the app's debug signing SHA-1 to be registered as an Android OAuth client in the same Google Cloud project as `googleWebClientId`.

## Tech

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · WorkManager · Retrofit/OkHttp · Coil · Credential Manager (Sign in with Google).

## Repo conventions

`AGENTS.md` is the contributor / agent guide — architecture, data model, gotchas. Start there if you're picking the project up.

## License

MIT — see [LICENSE](LICENSE).
