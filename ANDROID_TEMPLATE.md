# Android App Template

A field guide for spinning up a new native Android client in the same shape as `android-rssreader`. Intended for an agent (and the human alongside them) bootstrapping a new project — start here, then read this repo's `AGENTS.md` and source for concrete examples.

This is a **pattern reference**, not a library or a copy-paste skeleton. Take the patterns; leave the RSS specifics.

## What's portable, what's not

**Portable (copy the pattern):**
- Tech stack and version pins
- Project / package structure
- Compose + MVVM + Repository + Room-as-source-of-truth architecture
- OkHttp bearer interceptor + token refresh
- WorkManager periodic + on-foreground sync
- Encrypted token storage via EncryptedSharedPreferences
- Sticky UI prefs via plain SharedPreferences (`data/prefs/`)
- Local-first writes + idempotent pending-action queue
- Remote build workflow (Linux builder + Mac USB bridge over reverse-tunnel adb)
- Gotchas list at the bottom

**Not portable (don't copy):**
- The mobile auth endpoint (`POST /auth/google/mobile` — tied to a specific custom backend)
- `ALLOWED_EMAILS` gating (single-tenant pattern)
- Room schema and DAOs (RSS domain)
- WebView article rendering (RSS-specific)
- The `since=` delta cursor + `PendingActionEntity` mark-read flow (works for "mark items read", will need rethinking for other domains)

## Tech stack (pinned versions from `gradle/libs.versions.toml`)

| Layer | Choice | Version |
|---|---|---|
| Build | Android Gradle Plugin | 8.7.3 |
| Build | Gradle | 8.11.x |
| Build | KSP | 2.0.21-1.0.27 |
| Language | Kotlin | 2.0.21 |
| JDK | OpenJDK 17 | required by AGP 8.x |
| Min/target SDK | 26 / 35 | Android 8.0 → Android 15 |
| UI | Jetpack Compose + Material 3 | BOM 2024.10.01 |
| Nav | Navigation Compose | 2.8.4 |
| DI | Hilt (+ hilt-work, hilt-navigation-compose) | 2.52 |
| Networking | Retrofit + OkHttp + kotlinx.serialization | 2.11.0 / 4.12.0 / 1.7.3 |
| Persistence | Room | 2.6.1 |
| Background | WorkManager | 2.9.1 |
| Images | Coil (Compose) | 2.7.0 |
| Sign-in | Credential Manager + google-id | 1.3.0 / 1.1.1 |
| Secret storage | androidx.security.crypto (EncryptedSharedPreferences) | 1.1.0-alpha06 |

Use a [Gradle version catalog](https://docs.gradle.org/current/userguide/platforms.html) (`gradle/libs.versions.toml`) so versions live in one place. Copy this repo's `libs.versions.toml` verbatim as a starting point; trim unused entries once the project's shape is clear.

**Why these:** all idiomatic for 2026 native Android. Compose + Hilt + Coroutines is what every real codebase looks like; the alternatives (XML views, manual DI, RxJava) all create friction with newer Jetpack libraries and Android Studio tooling.

## Project structure

```
app/src/main/java/<your-package>/
├── MainActivity.kt                 single-activity host
├── <App>.kt                        @HiltAndroidApp + Configuration.Provider for WorkManager
├── auth/                           sign-in, token storage, OkHttp interceptor
├── data/
│   ├── api/                        Retrofit interfaces, DTOs, mappers
│   ├── db/                         Room entities, DAOs, migrations, Database
│   ├── prefs/                      SharedPreferences-backed sticky UI toggles
│   ├── repo/                       single repository per domain — sole API surface for the UI
│   └── sync/                       WorkManager workers + the scheduler that enqueues them
├── di/                             Hilt modules (one per concern: Network, Database, Auth, …)
└── ui/                             one package per screen (each owns its Screen.kt + ViewModel.kt)
    └── theme/                      Material 3 colors/typography
```

**One activity, many composables, many ViewModels.** Don't create new Activities per screen.

**One Repository per top-level domain.** Repository methods return `Flow` (for observation) or `suspend` (for writes/one-shots). The UI never calls Retrofit or Room directly.

**Hilt is everywhere.** Activities and ViewModels are entry points; everything else is constructor-injected. WorkManager workers use `@HiltWorker` + `HiltWorkerFactory`.

## Architecture

```
Compose Screen ── ViewModel ── Repository ─┬─ Room  (single source of truth for UI)
                                           ├─ Retrofit + OkHttp (API)
                                           └─ SyncScheduler ── WorkManager
                                                                ├─ ReadSyncWorker
                                                                └─ WriteSyncWorker
```

**Core rule: the UI reads from Room, never from the network.** Network responses land in Room; the UI's Flow re-emits automatically. User writes update Room *first*, then enqueue a pending-action row for a worker to push to the server with retry.

**Trade-off:** Room as the SoT means the UI can show briefly stale data after a remote change. This is worth it because instant UI is the whole point of building a native client over using the web app on mobile.

**Trade-off:** Idempotent pending-action rows (primary key on the action's target ID) means toggle-toggle-toggle collapses to "latest intent" with one server round-trip — cheaper and simpler than ordered queues, at the cost of intermediate states never reaching the server.

If the domain doesn't fit a "many items, mostly-readonly, occasional writes" model, the local-first + write-queue pattern may not pay off. (E.g. if every interaction is a server RPC with no useful local cache, just call the API directly.)

## Auth pattern (generalized)

This repo does Google sign-in, but the general shape applies to any modern Android auth flow:

1. **Native sign-in UI.** Use Credential Manager (`androidx.credentials`) for Google. For OAuth-with-custom-IdP, use Chrome Custom Tabs with a deep-link redirect.
2. **Token exchange with your backend.** Send the IdP token to a backend endpoint; receive an access token (and ideally a refresh token).
3. **Encrypted storage.** Persist tokens via `EncryptedSharedPreferences` — see `auth/TokenStore.kt`. Don't use plain SharedPreferences for secrets.
4. **OkHttp injection.** An `Interceptor` adds `Authorization: Bearer <access>` to every request. A separate `Authenticator` handles 401 → refresh → retry **under a mutex** so concurrent requests don't trigger N parallel refreshes.
5. **Sign-out.** Clear `TokenStore`, cancel WorkManager jobs, wipe Room. Don't just clear the token and leave stale data on disk.

**For LaunchDarkly specifically:** LD's mobile SDKs handle their own auth via mobile SDK keys. If you're wrapping the LD SDK, you don't need the OAuth dance — but you'll still want an SDK-key-loading pattern and probably an encrypted store for any user-identity tokens you use to evaluate flags. If you're talking to LD's REST API instead (admin/reporting use case), it's API-key auth and a much simpler `Authorization: api-key <key>` interceptor.

## Sync pattern (generalized)

Two workers, scheduled by a `SyncScheduler`:

- **Read sync** — pulls from the server, writes to Room. Runs periodically (~15 min via WorkManager `PeriodicWorkRequest`) and one-shot on app foreground / post-sign-in. Use a `?since=<cursor>` query if the API supports it; fall back to full-pull if it doesn't.
- **Write sync** — drains a `PendingActionEntity` table to the server with exponential backoff and idempotent batching.

**Cursor invariant:** capture the cursor *before* the network calls, not after. If a row is modified mid-sync, you want it caught on the next pass, not silently skipped. Small over-fetch beats lost updates.

**Refresh ordering matters** when entities have FKs (categories → feeds → items in RSS). Replace-all in dependency order, or you'll either violate constraints or briefly point the UI at half-resolved data.

**For LaunchDarkly:** flag evaluations are typically streamed via the SDK (SSE or polling). If you use the LD SDK directly, this whole worker pattern is unnecessary — the SDK has its own background sync. The pattern is only relevant if you're building something that reads admin-API data (flag definitions, audit logs) into a local store.

## Build, run, deploy

```bash
cp local.properties.example local.properties
# fill in the keys (sdk.dir, backend base URL, OAuth client ID, etc.)
./gradlew installDebug
```

That's the whole loop. Versioned values that vary per environment live in `local.properties` (gitignored), get read by `app/build.gradle.kts` into `BuildConfig.*` fields, and the app reads them from `BuildConfig` at runtime. Always check in a `local.properties.example` showing every required key with comments — agents and humans will both forget which keys exist otherwise.

**Empty config values should fail loud at app init**, not silently produce broken requests. Use `check(BuildConfig.X.isNotEmpty()) { "X must be set in local.properties" }` in the Hilt module that reads them.

**No release pipeline in v1.** Leave `release` build type with `isMinifyEnabled = false` and no signing config until you actually need to ship outside debug. R8/proguard rules for Hilt, Retrofit, kotlinx.serialization, and Compose can be added at release time; doing them up front is wasted work for a personal-use app.

## Remote dev environment (Linux builder + Mac USB bridge)

The Mac is a dumb USB bridge for `adb`; everything else (build, IDE, Claude Code) runs on `secorp.net`. Established and battle-tested in this project.

**On secorp.net (one-time):**
- JDK 17 from apt (`/usr/lib/jvm/java-17-openjdk-amd64`)
- Android SDK at `~/Android/Sdk` via `sdkmanager` — install `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`
- `~/.bashrc` sets `JAVA_HOME`, `ANDROID_HOME`, and prepends both to `PATH`
- Project cloned under `~/termag/projects/<name>`

**On the Mac (one-time):**
- `~/.ssh/config` for the build host:
  ```
  Host secorp.net
      RemoteForward 127.0.0.1:5037 127.0.0.1:5037
      ExitOnForwardFailure yes
  ```
  `ExitOnForwardFailure yes` is critical: it makes the SSH connection refuse to open if anything else is squatting on port 5037 (typically a stray local `adb` daemon), instead of silently failing the tunnel. When this fires, kill the local daemon (`adb kill-server`) and reconnect.
- `~/.gradle/gradle.properties` pins `org.gradle.java.home=/Applications/Android Studio.app/Contents/jbr/Contents/Home` so Mac-side Gradle finds JDK 17 even if system `java` is older. Only needed if you build *on* the Mac too.
- `brew install --cask android-platform-tools` for `adb`. **adb version must match exactly between Mac and the build host** or the tunnel resets connections mid-install with cryptic errors.
- Copy the debug keystore (`~/.android/debug.keystore`) from Mac to the build host so APKs from both sign with the same SHA-1; otherwise Google sign-in will accept APKs from one host and reject the other (the SHA-1 in Google Cloud only matches one keystore).

**Day-to-day flow:**
1. Phone plugged into Mac via USB; accept "Allow USB debugging" prompt once
2. From Mac: `ssh secorp.net` (the RemoteForward attaches automatically)
3. On secorp.net: `cd ~/termag/projects/<name> && ./gradlew installDebug`

**If the install hangs**, the tunnel has died. Symptoms: `adb devices` says `daemon not running; starting now at tcp:5037` (it's starting a *local* daemon instead of going through the tunnel). Recovery:
```bash
# On secorp.net:
adb kill-server                 # remove the stray local daemon
ss -tlnp | grep :5037           # confirm 5037 is free
# On the Mac, drop and re-open the ssh session — the tunnel reattaches.
```

Wrap install commands with `timeout 60 ./gradlew installDebug` so a dead tunnel fails in a minute instead of hanging the build for 16+ minutes (adb itself has no internal timeout).

**Skip Android Studio and the emulator for v1.** Both are painful over SSH/VNC. `./gradlew installDebug` from a terminal covers build+deploy; `adb logcat -s <YourTag>:*` covers debug output; layout inspector and profiler only matter much later. Real-phone-via-tunnel beats remote emulator on every axis except CI.

## Configuration pattern

```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        val props = Properties().apply {
            rootProject.file("local.properties").inputStream().use { load(it) }
        }
        buildConfigField("String", "BACKEND_BASE_URL", "\"${props["backendBaseUrl"] ?: ""}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${props["googleWebClientId"] ?: ""}\"")
    }
    buildFeatures { buildConfig = true }
}
```

```kotlin
// di/NetworkModule.kt
@Provides @Singleton
fun provideRetrofit(): Retrofit {
    check(BuildConfig.BACKEND_BASE_URL.isNotEmpty()) {
        "backendBaseUrl must be set in local.properties"
    }
    return Retrofit.Builder().baseUrl(BuildConfig.BACKEND_BASE_URL)…
}
```

## Persistent UI state pattern

ViewModels die on process death, navigation away, and reinstalls. Anything the user expects to persist (filter toggles, sort order, last-viewed feed) needs to live outside the ViewModel.

For non-secret booleans/strings, follow the pattern in `data/prefs/UiPreferencesStore.kt`:

```kotlin
@Singleton
class UiPreferencesStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
    var someToggle: Boolean
        get() = prefs.getBoolean(KEY, false)
        set(v) { prefs.edit { putBoolean(KEY, v) } }
}
```

Inject into the VM, seed `MutableStateFlow(prefs.someToggle)`, write back on every change. Plain SharedPreferences is fine here — DataStore is overkill for a handful of toggles. Secrets go in `EncryptedSharedPreferences` instead.

## Gotchas (hard-won; copy these too)

1. **ADB version must match exactly** between Mac and the build host. Mismatch silently kills both daemons or resets connections mid-install. `adb version` on both; install the same `platform-tools` distribution.

2. **OAuth client ID is per-keystore SHA-1.** Google Cloud associates the Android client with package name + signing-key SHA-1. Each keystore (debug-on-Mac, debug-on-Linux-host, release) has a different SHA-1. Register every keystore's SHA-1 against the same client ID, or share one keystore across hosts (this repo shares `~/.android/debug.keystore` from Mac to the build host).

3. **Compose `TextField` value must NOT be bound directly to a VM `StateFlow`.** Round-tripping every keystroke through `state.query` → VM → re-emit → recompose drops/reorders characters under fast typing. Keep the field's text in `rememberSaveable { mutableStateOf("") }` and push to the VM via `onValueChange`. See `ui/items/ItemListScreen.kt` for the canonical example.

4. **`material-icons-extended` is intentionally not on the classpath** (~25 MB). Stick to icons in core Material (`Check`, `Close`, `Search`, `Refresh`, `MoreVert`, `ArrowBack`, etc.) or inline a small `ImageVector`. When `DoneAll` was needed for "mark all read", a text button was the answer.

5. **`PullToRefreshBox` needs a scrollable child** to attach its nested-scroll listener. Empty-state composables must therefore be wrapped in a single-item `LazyColumn` (or use `LazyItemScope.fillParentMaxSize()` inside an existing `LazyColumn`'s `item { }` block) so pull-to-refresh still works from an empty view.

6. **OkHttp `Authenticator` for token refresh must be reentrant under a mutex.** N concurrent 401s otherwise trigger N parallel refreshes and the server typically only accepts one.

7. **JWT/bearer-token interceptors must skip the refresh endpoint itself**, or you'll loop trying to authenticate the refresh call with an expired token.

8. **`fallbackToDestructiveMigration()` masks migration bugs.** Useful during early schema churn (this project still has it on), but absolutely must be removed before any real release. Write actual `Migration(from, to)` callbacks and verify each with an integration test.

9. **WorkManager + Hilt requires `Configuration.Provider`** on your `@HiltAndroidApp` Application + a `HiltWorkerFactory` injection. Forgetting this manifests as `IllegalStateException: WorkManager not initialized` or `@HiltWorker` workers silently never running.

10. **Replace-all sync ordering matters for FK relationships.** Always parents → children. Reversing the order violates constraints or shows half-resolved data.

11. **Compose previews break with `@HiltViewModel`.** Use a sealed `UiState` parameter on a "stateless" composable and pass real states from previews; keep the ViewModel injection at the top-level entry composable only.

## Setup recipe (from zero to running on a phone)

1. **Create the project.** Easiest: clone this repo as a scratch reference, then `git init` a fresh empty repo for your new project and copy in `gradle/`, `gradlew`, `gradlew.bat`, `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`. Rename `namespace` and `applicationId`. Replace `app/src/main/java/<old>/` with your package.
2. **Pick the package name** (`com.example.foo`). Used everywhere — Hilt's generated code, OAuth client registration, etc. Hard to change later.
3. **Write `local.properties.example`** before any code. Lists every key the app needs. Commit it.
4. **Stand up `MainActivity` + `App.kt` + a single Compose screen** that displays "hello". Verify it builds + installs on the phone before adding more.
5. **Add the auth layer next.** If using Google sign-in, register an **Android** OAuth client in Google Cloud Console with the package name + debug keystore SHA-1 first; the app can't get an ID token otherwise.
6. **Build out Room + Repository + the first read-only screen** before any writes. Verify the Flow → ViewModel → Compose pipe works end-to-end with stub data.
7. **Add WorkManager + sync** once you have at least one screen that benefits from refreshed data.
8. **Add writes + pending-action queue** last. By then you understand the data model well enough to make the right idempotency choices.

Skip Android Studio entirely until you hit something `./gradlew` + `adb logcat` genuinely can't diagnose — usually that's Layout Inspector or Profiler, and they don't matter until v2.

## See also in this repo

- `AGENTS.md` — what THIS project is, current status, repository layout, gotchas list
- `gradle/libs.versions.toml` — version catalog (copy as starting point)
- `app/build.gradle.kts` — module-level config including BuildConfig wiring
- `auth/TokenStore.kt` — EncryptedSharedPreferences pattern
- `auth/AuthInterceptor.kt` — OkHttp bearer injection
- `data/prefs/UiPreferencesStore.kt` — sticky UI toggles pattern
- `data/sync/` — WorkManager workers + scheduler
- `data/repo/RssRepository.kt` — single-repo-per-domain pattern
- `ui/items/ItemListScreen.kt` — Compose TextField gotcha, empty-state inside `LazyColumn`, swipe-to-dismiss
- `di/` — Hilt module shapes

When in doubt, read this project's source. It's the working reference behind this doc.
