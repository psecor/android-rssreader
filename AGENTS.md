---
project: android-rssreader
status: in-progress
status_description: "V1 daily-use scope feature-complete in debug builds; no release pipeline yet."
last_updated: 2026-06-15
last_updated_by: []
wiki_schema_version: 1
---

# AGENTS.md — android-rssreader

## What This Is

Native Android client for the self-hosted [rssreader](https://github.com/psecor/rssreader) RSS service. Pairs with the existing Node/Express + Postgres + React web app deployed at the same backend. Two motivations: replace daily mobile use of the responsive web app with something that caches aggressively and responds instantly, and learn what building an Android app is actually like.

## Status

- ✅ Google sign-in via Credential Manager → backend exchange for bearer token
- ✅ Feed list with categories, "All items" view, per-feed item list
- ✅ Article reading (HTML render in WebView), mark-on-open
- ✅ Mark read/unread: article toggle, row swipe, bulk "mark all read"
- ✅ Local search across title/description/author
- ✅ Pull-to-refresh; periodic WorkManager sync; post-sign-in sync
- ✅ Delta sync (`since=` cursors), offline write queue with retry
- ❌ Feed CRUD, category management, history view, reading stats (out of v1)
- ❌ Release build hardening (signing config, R8 minification, real migration testing)
- ❌ Google Play distribution

## Repository Layout

```
app/src/main/java/net/secorp/rssreader/
├── MainActivity.kt
├── RssApp.kt                       Application + Hilt entry, WorkManager bootstrap
├── auth/
│   ├── GoogleSignInClient.kt       Credential Manager wrapper
│   ├── AuthRepository.kt           Token exchange + sign-out
│   ├── AuthInterceptor.kt          OkHttp bearer-token injection
│   └── TokenStore.kt               EncryptedSharedPreferences-backed
├── data/
│   ├── api/                        Retrofit interfaces + DTOs + mappers
│   ├── db/                         Room entities, DAOs, migrations
│   ├── repo/RssRepository.kt       Sole source of truth surfaced to the UI
│   └── sync/                       SyncScheduler + SyncWorker + WriteSyncWorker
├── di/                             Hilt modules
└── ui/                             One package per screen (login, feeds, items, article)

app/schemas/                        Room schema JSON, one file per DB version
local.properties.example            Required config keys, gitignored real file
```

## Architecture

```
Compose Screen ─── ViewModel ─── RssRepository ─┬─ Room  (single source of truth for UI)
                                                ├─ Retrofit (RssApi, AuthApi)
                                                └─ SyncScheduler ─── WorkManager
                                                                     ├─ SyncWorker       (read pull)
                                                                     └─ WriteSyncWorker  (mark-read push)
```

The UI reads from Room via Flow — never from the network directly. Network changes land in Room and propagate through existing observers. User writes (mark read/unread) update Room immediately, queue a `PendingActionEntity`, and enqueue a write-push worker.

**Trade-off:** Room as single source of truth means the UI can briefly show stale data after a remote change / vs. always-fresh server queries → vastly snappier UI on a flaky cellular link. Was the explicit motivation for building a native client at all.

**Trade-off:** Local-first writes + idempotent `PendingActionEntity` (primary key is `itemId`, so toggle-toggle-toggle collapses to the latest intent) / vs. strict ordering → simpler offline behavior and fewer round trips, at the cost of an intermediate toggle never reaching the server.

## Data & Schema

| Entity | Purpose | Notable fields |
|---|---|---|
| `CategoryEntity` | Feed grouping | `id`, `name` |
| `FeedEntity` | RSS feed | `id`, `title`, `url`, `categoryId` |
| `FeedItemEntity` | Single article | `id`, `feedId`, `title`, `link`, `description`, `isRead`, `readAt`, `pubDate`, `thumbnail` |
| `PendingActionEntity` | Queued mark-read mutation | `itemId` (PK), `isRead`, `queuedAt` |

Schema JSON snapshots live in `app/schemas/<package>.RssDatabase/<version>.json`, committed and one file per DB version. Migrations land in `data/db/Migrations.kt`. The DB builder currently uses `fallbackToDestructiveMigration()` while the schema is still moving; see Gotchas before any real release.

## Configuration

All from `local.properties` (gitignored). See `local.properties.example` for the schema:

| Key | Purpose |
|---|---|
| `sdk.dir` | Android SDK path. Android Studio writes this on first sync. |
| `backendBaseUrl` | rssreader backend root, trailing slash required. |
| `googleWebClientId` | Google Cloud OAuth *web* client ID. The Android ID token's `aud` claim must match this; the backend verifies against it. |

Both `backendBaseUrl` and `googleWebClientId` are exposed to source via `BuildConfig`. Empty values build successfully and fail at app init with a `check()` message — see `NetworkModule.provideRetrofit` and `GoogleSignInClient.requestIdToken`.

## Build, Run, Deploy

```bash
cp local.properties.example local.properties
# fill in the three values
./gradlew installDebug
```

For local backend dev, set `backendBaseUrl=http://10.0.2.2:3001/` (Android emulator → Mac host) or `backendBaseUrl=http://localhost:3001/` paired with `adb reverse tcp:3001 tcp:3001` on a USB-connected device.

No release pipeline yet. `release` build type currently has `isMinifyEnabled = false` and no signing config wired up.

## Observability & Maintenance

- HTTP request/response logged at `BODY` level in debug via OkHttp `HttpLoggingInterceptor`. Filter logcat by tag `OkHttp`.
- WorkManager state via `adb shell dumpsys jobscheduler | grep rssreader` or Android Studio's Background Task Inspector.
- Last delta-sync watermark: `adb shell run-as net.secorp.rssreader cat shared_prefs/sync_state.xml`.
- Pending write queue depth: query the `pending_actions` table via Database Inspector, or call `PendingActionDao.count()` from a test scope.

## Integration Surfaces

The app consumes the rssreader backend HTTP API. Selected endpoints (canonical schema in the backend repo):

| Endpoint | Used for |
|---|---|
| `POST /auth/google/mobile` | Exchange Google ID token for bearer |
| `GET /api/categories` | Full list, replace-all locally |
| `GET /api/feeds` | Full list, replace-all locally |
| `GET /api/feed-items?since=&limit=&offset=` | Initial + delta item sync, paged |
| `GET /api/read-status?since=` | Delta read-status sync |
| `POST /api/read-status` | Push mark-read/unread |

Sync cursor is captured *before* the network calls so anything modified mid-sync gets caught on the next pass. Small over-fetch over the risk of missing updates.

## Gotchas

1. **`fallbackToDestructiveMigration()` is still on.** `DatabaseModule.provideDatabase` falls back to wiping the DB on any unrecognized schema version. Migrations exist (`data/db/Migrations.kt`) but the fallback masks bugs in them. Tighten before any real release; verify each migration with a test.

2. **Compose `TextField` value must not be bound to a VM `StateFlow`.** Binding `value = state.query` (where `state` is collected from a StateFlow) round-trips every keystroke through the VM and back a frame later, racing subsequent keystrokes — characters get dropped, reordered, or stuck. `ItemListScreen` keeps the search field's text in `rememberSaveable { mutableStateOf("") }` and pushes to the VM via `onValueChange`. Repeat the pattern for any new input control.

3. **Material icons set is core-only.** `material-icons-extended` is intentionally not on the classpath (~25 MB). Stick to icons that ship with core (`Check`, `Close`, `Search`, `Refresh`, `MoreVert`, `ArrowBack`, etc.) or inline a custom `ImageVector`. When `DoneAll` was wanted for "mark all read", the answer was a text button instead.

4. **Empty list inside `PullToRefreshBox`.** Material3 `PullToRefreshBox` needs a scrollable child to attach its nested-scroll listener. The feed list's empty-state composable is therefore wrapped in a one-item `LazyColumn` so pull-to-refresh still triggers from a clean install.

5. **App signing for Google sign-in is keystore-tied.** The Android OAuth client at Google Cloud is registered against the app's package name + signing SHA-1. Debug builds use the local debug keystore; a release build signed with a different key will fail to obtain ID tokens until that key's SHA-1 is added in Google Cloud Console.

6. **`refreshAll` ordering matters.** Categories → Feeds → Items → ReadStatuses. Items have FK to Feeds, Feeds to Categories. Reversing the order either violates FK constraints during replace-all or briefly leaves the UI pointed at half-resolved data.

## Related

**Other projects:**
- [psecor/rssreader](https://github.com/psecor/rssreader) — backend + web client this app authenticates against and syncs with.

**Topics:**
- _none yet_

<!-- agent-wiki:backlinks-start -->
_No incoming links yet._
<!-- agent-wiki:backlinks-end -->
