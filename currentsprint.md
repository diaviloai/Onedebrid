# OneDebrid — Current Sprint

## Status

Implementation in progress. Architectural design phase complete.

This file is fully rewritten each session — it reflects actual current
code state, verified by pulling the repo and reading files directly, not
appended to informally.

Build verification: project compiles cleanly as of Session 25's close,
confirmed via GitHub Actions on commit `003f45c` ("update") — job "build"
succeeded in 4m 59s, per the direct run/job URL Dia provided
(`github.com/diaviloai/Onedebrid/actions/runs/32189536407/job/95880649912`),
fetched via web_fetch since the GitHub Actions API hit a shared-IP rate
limit this session (`API rate limit exceeded for 34.23.141.224`) before
that URL was available. The only annotations on the run are Gradle
cache-service infrastructure warnings ("Our services aren't available
right now" — GitHub's own cache backend, not this repo) and Node 20/
setup-java v4 deprecation notices; none relate to this session's code.
All 9 files touched this session were independently re-verified against
a fresh full-repo tarball pull (grep-checked for the specific markers/
wiring described below, not just presence) before this file was updated.

## Package Structure

com.onedebrid.app/
    ├── MainActivity.kt
    ├── OneDebridApplication.kt
    ├── coordinator/
    │   ├── PlaybackCoordinator.kt
    │   ├── SearchCoordinator.kt
    │   └── SessionCoordinator.kt
    ├── data/
    │   ├── local/
    │   │   ├── AppDatabase.kt
    │   │   ├── MediaCache.kt (Session 25 — wraps CacheEntryDao for
    │   │   │   Media/Episode-list JSON caching)
    │   │   ├── TypeConverters.kt
    │   │   ├── dao/
    │   │   │   ├── CacheEntryDao.kt
    │   │   │   ├── ContinueWatchingDao.kt
    │   │   │   ├── DownloadDao.kt
    │   │   │   ├── ProfileDao.kt
    │   │   │   ├── RecentlyPlayedDao.kt
    │   │   │   └── SearchHistoryDao.kt
    │   │   └── entity/
    │   │       ├── CacheEntryEntity.kt
    │   │       ├── ContinueWatchingEntity.kt
    │   │       ├── DownloadEntity.kt
    │   │       ├── ProfileEntity.kt
    │   │       ├── RecentlyPlayedEntity.kt
    │   │       └── SearchHistoryEntity.kt
    │   └── repository/
    │       ├── MediaRepository.kt / MediaRepositoryImpl.kt (Session 25:
    │       │   getMediaDetails()/getEpisodes() now cache-first via
    │       │   MediaCache, write-through on a successful fetch)
    │       ├── PlaybackRepository.kt / PlaybackRepositoryImpl.kt
    │       ├── ProfileRepository.kt / ProfileRepositoryImpl.kt
    │       ├── RepositoryResult.kt
    │       ├── SearchRepository.kt / SearchRepositoryImpl.kt
    │       ├── SessionRepository.kt / SessionRepositoryImpl.kt
    │       └── (Subtitle/Download repositories not yet built)
    ├── di/
    │   ├── CoroutineDispatchers.kt
    │   └── (Hilt modules — DatabaseModule, RepositoryModule; neither
    │       needed changes this session, see Session 25 notes below)
    ├── domain/
    │   ├── error/
    │   │   └── AppError.kt
    │   └── model/
    │       ├── Media.kt (Session 25: @Serializable added)
    │       ├── Episode.kt (Session 25: @Serializable added)
    │       ├── PlaybackRequest.kt
    │       ├── SearchResult.kt
    │       ├── SessionState.kt (SessionState, PlaybackSession,
    │       │   SearchSession, PlaybackState enum)
    │       ├── StreamSource.kt (VideoQuality enum)
    │       ├── SubtitleTrack.kt (SubtitleFormat enum)
    │       ├── UserProfile.kt (PlaybackPreferences, SubtitlePreferences,
    │       │   SearchPreferences, ThemePreferences)
    │       └── WatchedItem.kt
    ├── provider/
    │   └── (SearchProvider, StubSearchProvider, MetadataProvider,
    │       StubMetadataProvider, DebridProvider, others)
    ├── ui/
    │   ├── home/
    │   │   ├── HomeScreen.kt (Session 25: rows now tappable)
    │   │   └── HomeViewModel.kt (Session 25: onItemClick(), one-shot
    │   │       navigateToPlayer event)
    │   ├── navigation/
    │   │   ├── NavGraph.kt (Session 25: Home wired with
    │   │   │   onNavigateToPlayer)
    │   │   └── PendingPlaybackHolder.kt
    │   ├── player/
    │   │   ├── PlayerScreen.kt
    │   │   └── PlayerViewModel.kt
    │   ├── search/
    │   │   ├── SearchScreen.kt
    │   │   └── SearchViewModel.kt
    │   └── settings/
    │       ├── SettingsScreen.kt
    │       └── ProfileViewModel.kt
    └── usecase/
        ├── CreateProfileUseCase.kt
        ├── DeleteProfileUseCase.kt
        ├── EndPlaybackSessionUseCase.kt
        ├── GetContinueWatchingUseCase.kt
        ├── GetMediaByIdUseCase.kt (Session 25 — new, wraps
        │   MediaRepository.getMediaDetails(); not HomeViewModel-specific,
        │   reusable by any future caller needing mediaId → Media)
        ├── RemoveFromContinueWatchingUseCase.kt
        ├── SavePlaybackPositionUseCase.kt
        ├── SearchMediaUseCase.kt
        ├── SwitchProfileUseCase.kt
        ├── UpdateProfileUseCase.kt
        └── (others per earlier sessions)

(This tree reflects what's been directly read/touched across sessions,
not a guaranteed exhaustive listing — see the repo itself for ground
truth on files not mentioned in recent session notes.)

## App Navigation State (as of Session 25)

All four primary screens exist and are wired into a single `NavGraph.kt`:

- **Home** (`Route.Home`, start destination) → real `HomeScreen.kt`.
  Shows Continue Watching. As of Session 25, rows are tappable to resume
  playback (see "Session 25 — What Was Done" below) — this replaces the
  Session 22–24 "not tappable" limitation entirely; it is not carried
  forward below. Top bar has Search and Settings actions.
- **Search** (`Route.Search`) → real `SearchScreen.kt`. Reached via
  Home's Search button. Forwards to Player via
  `PendingPlaybackHolder` + `Route.Player.path`.
- **Player** (`Route.Player`) → real `PlayerScreen.kt`. Reads
  `PlaybackRequest` from `PendingPlaybackHolder`; routes back to Home
  (with `popUpTo` clearing itself off the stack) if nothing is pending.
  Now reachable from Home as well as Search, both via the same
  `PendingPlaybackHolder`-then-navigate pattern.
- **Settings** (`Route.Settings`) → real `SettingsScreen.kt`. Reached
  via Home's Settings button. Leaf destination (no further forward
  navigation from it). Profile list (switch/create/rename/delete) +
  full preference editor for all four `UserProfile` groups (playback,
  subtitles, search, theme).

No `Route` entries exist beyond these four. Adding an entry without a
real destination is deliberately avoided (would be dead code implying
more is wired than actually is).

## Session 25 — What Was Done

**Media cache/lookup layer built and wired end-to-end** (Next Steps #1
from Session 24, the top priority). Scope agreed with Dia up front:
cache wiring + tap-to-resume, explicitly deferring the
PendingPlaybackHolder-replacement/nav-arg groundwork stretch goal (see
Next Steps below).

Before writing code, read the actual current `MediaRepository.kt`,
`MediaRepositoryImpl.kt`, `SearchResult.kt`, `PlaybackRequest.kt`,
`WatchedItem.kt`, `HomeScreen.kt`, `HomeViewModel.kt`, `NavGraph.kt`,
`PendingPlaybackHolder.kt`, `CacheEntryEntity.kt`, `CacheEntryDao.kt`,
`DatabaseModule.kt`, `Media.kt`, `Episode.kt`, and `AppError.kt` per
standing practice, before proposing a design.

Key discovery: `CacheEntryEntity`/`CacheEntryDao` already existed,
already registered in `AppDatabase` (version 1, no migration needed),
but nothing consumed them — an unused, ready-to-wire generic cache
table. `CacheEntryEntity`'s own doc comment already specified the key
convention used (`"metadata:tt1234567"` etc.), and
`kotlinx.serialization.json` was already a project dependency, so no new
libraries were needed.

Files created:
- **`data/local/MediaCache.kt`** — wraps `CacheEntryDao`. `getMedia()`/
  `putMedia()`/`getEpisodes()`/`putEpisodes()`, JSON via
  `kotlinx.serialization`. Key convention: cacheType `"metadata"` /
  `"episodes"`, key `"<cacheType>:<mediaId>"`. TTL: flat 7 days for
  both, confirmed with Dia as a reasonable starting assumption (not
  derived from a specific requirement — revisit if staleness becomes a
  real complaint once real metadata providers exist). Cache read
  failures (malformed/undeserializable entry) are treated as a miss, not
  a thrown exception, since the caller falls through to the
  network/provider path either way.
- **`usecase/GetMediaByIdUseCase.kt`** — thin wrapper around
  `MediaRepository.getMediaDetails()`. Deliberately not
  `HomeViewModel`-specific; any future caller needing mediaId → Media
  (e.g. the Next Steps #2 Details screen) should use this.

Files modified:
- **`domain/model/Media.kt`, `domain/model/Episode.kt`** — added
  `@Serializable`. Both were already plain data classes with only
  primitive/String/enum fields, so no custom serializers were needed.
- **`data/repository/MediaRepositoryImpl.kt`** — `getMediaDetails()` and
  `getEpisodes()` are now cache-first: check `MediaCache`, return on
  hit; on miss, fall through to `metadataProvider` exactly as before,
  then write-through to `MediaCache` on success. Failure behavior is
  unchanged from before this session — the cache only skips redundant
  work on a hit, it doesn't alter what happens on a miss. No DI module
  changes were needed: `MediaCache` is `@Singleton @Inject constructor`
  taking only `CacheEntryDao` (already provided by `DatabaseModule`),
  and `MediaRepositoryImpl`'s binding in `RepositoryModule` is a plain
  `@Binds` interface binding that needed no changes for the new
  constructor param.
- **`ui/home/HomeViewModel.kt`** — new `onItemClick(item: WatchedItem)`.
  Resolves `item.mediaId` via `GetMediaByIdUseCase`; on success, builds a
  minimal `Episode` from `WatchedItem`'s own
  `episodeId`/`seasonNumber`/`episodeNumber` fields when all three are
  present (movies get `episode = null`), builds a `PlaybackRequest` with
  `resumePositionMs = item.positionMs`, calls
  `pendingPlaybackHolder.set(request, profileId)`, then emits a one-shot
  navigation event. On failure, stores the `AppError` in new
  `HomeUiState.resumeError` for the screen to surface. New
  `HomeUiState.resolvingMediaId` tracks which row (if any) is currently
  resolving; a second tap while one is in flight is a no-op (single
  concurrent resolution only — see file's doc comment for the
  reasoning). Navigation event is a `Channel<Unit>`-backed
  `navigateToPlayer: Flow<Unit>`, not a second `StateFlow` — this is the
  first ViewModel in the project needing a one-shot UI event (Search
  already has the full `Media` in hand synchronously at tap time, so it
  never needed one), and a `Channel` avoids the replay-on-recomposition
  risk a conflated `StateFlow` would have here.
- **`ui/home/HomeScreen.kt`** — rows are now `clickable` (disabled while
  that row is resolving); shows an inline "Loading…" label in place of
  the progress text while resolving, and an inline error message below
  the list when `resumeError` is set and nothing is currently resolving.
  Collects `viewModel.navigateToPlayer` via `LaunchedEffect` +
  `collectLatest`, calling the new `onNavigateToPlayer` param.
  `resumeError` is not row-scoped (`HomeUiState` only tracks the single
  most recent failure) — documented in `ContinueWatchingList`'s call
  site as an accepted simplification for single-concurrent-resolution;
  would need a per-row error map if concurrent resolution is ever
  supported.
- **`ui/navigation/NavGraph.kt`** — `HomeScreen` now receives
  `onNavigateToPlayer`, a plain forward `navigate(Route.Player.path)`
  identical in shape to Search's existing call. Doc comments for `Home`
  and `Player` updated to describe the new flow; `PendingPlaybackHolder`
  itself is unchanged and still does not survive process death (that's
  a separate, larger piece of work — see Next Steps).
- **`res/values/strings.xml`** — added `home_resolving_media`,
  `home_resume_error`.

**Important, expected, and deliberately-not-worked-around limitation:**
`MetadataProvider` is still `StubMetadataProvider`. Tapping a Continue
Watching row today will show the loading state, then the inline error
(`AppError.AllProvidersUnavailable`), unless that exact `mediaId` was
somehow already cache-hit. This is correct wiring-ahead-of-provider
behavior, not a bug — the cache and tap-to-resume flow are fully wired
and will work automatically once real metadata providers exist, without
needing to revisit this session's code.

No test files exist anywhere in the repo (checked this session) — no
existing tests were at risk of breaking from `MediaRepositoryImpl`'s
constructor gaining a new parameter.

## Known, Deliberate Limitations (documented in code, not silently
worked around)

- **SearchScreen TV_SHOW results are non-interactive** (no
  episode-picker yet) — from Session 22, still true.
- **SearchScreen playback always uses Smart Defaults**
  (`preferredSource = null`, no stream-candidate picker) — from
  Session 22, still true.
- **HomeScreen Continue Watching resolve failures are not row-scoped in
  state** — see Session 25 notes above (`resumeError` is a single
  most-recent-failure field, not a per-mediaId map). Acceptable given
  only one row can resolve at a time; revisit if concurrent resolution
  is ever supported.
- **HomeScreen rows do not proactively resolve/display real
  titles/artwork** — resolution only happens on tap, not for every
  visible row on screen-load, since resolving every row proactively
  would mean an unbounded number of cache/network calls just from Home
  appearing. Rows still show raw `mediaId` text until tapped. Left as a
  possible future enhancement if showing real titles in the list itself
  (not just after tapping) becomes a priority.
- **SettingsScreen preference edits write to Room on every single
  toggle/dropdown/keystroke** (no debounce, no explicit Save step for
  the free-text language fields). Functionally correct, not
  necessarily optimal — flagged as a possible follow-up if it feels
  laggy on-device, not something to silently "fix" without confirming
  it's actually a problem first.
- **SettingsScreen blank-name and delete-active-profile validation
  errors both surface as a generic error message**, since `AppError`
  has no `ValidationError` case yet and `LocalStorageError.cause` is
  deliberately not shown to the user (see Open TODOs, existing item).
  The UI does proactively hide the Delete action on the active profile
  row to avoid the user hitting this in the common case, but the
  underlying generic-message gap still exists for the create/rename
  paths.
## Carried-Forward Lessons (Composable-context rule, Flow collection
pattern, PlaybackState naming collision, MutableStateFlow .update{}
gotcha, etc.)

- **A one-shot ViewModel → UI event needs a `Channel`, not a second
  `StateFlow`** (Session 25, `HomeViewModel.navigateToPlayer`) — a
  `StateFlow`'s conflated-replay-of-1 semantics risks re-firing the
  event on recomposition or configuration change.
  `Channel<Unit>(Channel.BUFFERED)` + `receiveAsFlow()`, collected via
  `LaunchedEffect` + `collectLatest` in the composable, is the pattern
  to reuse for any future one-shot navigation/event need (e.g. a
  snackbar-style transient message).
- **A generic, already-registered-but-unused Room table is worth
  grepping for before assuming a new one is needed.** `CacheEntryEntity`/
  `CacheEntryDao` existed since (at least) Session 24's schema, fully
  wired into `AppDatabase`/`DatabaseModule`, but nothing consumed it —
  this saved a full new-entity-plus-migration cycle for the Media cache
  layer (Session 25).
- **A repository method gaining a new constructor dependency doesn't
  need a DI module change if the new dependency is itself
  `@Singleton @Inject constructor` and the repository's binding is a
  plain `@Binds` interface binding** (Session 25, `MediaRepositoryImpl`
  + `MediaCache`) — Hilt resolves the chain automatically.
- **Grep the whole repo for test files before assuming a constructor
  change might break something** (Session 25) — confirmed zero test
  files exist in this repo currently, so this is currently a non-issue,
  but re-check whenever tests are eventually added.
- **When a shared-IP GitHub Actions API rate limit blocks CI
  verification, ask for the direct run/job URL and `web_fetch` it
  instead** (Session 25) — this worked cleanly; the job page's
  Annotations section is sufficient to distinguish real build failures
  from infrastructure noise (e.g. Session 25's cache-service and Node
  deprecation warnings) without needing raw log access.
- **Grep build.gradle.kts / libs.versions.toml before importing
  anything from a library not yet used elsewhere in the codebase** —
  even common-seeming APIs may not have their artifact declared.
  (Confirmed again Session 25: `kotlinx.serialization.json` was already
  present, so `MediaCache` needed no new dependency.)
- **Don't declare a session or a file "done" without an actual CI
  result** — checked via the GitHub Actions API or a direct run/job URL
  fetch, not inferred from "build clean" or "pushed" alone.
- **When a paste truncates at a consistent point across retries, that's
  a signal to chunk the paste, not retry it unchanged.** (Session 23.)
  All 9 files/edits delivered in Session 25 were well under the
  ~450-500 line risk zone (largest was `HomeScreen.kt` at 264 lines),
  so no chunking was needed for source files — but this sprint doc
  itself came in at 503 lines and WAS chunked into two pastes.
- **`create_file` fails outright (rather than partially writing) if the
  target path already exists — it does not silently overwrite or
  truncate.** (Session 25, this handoff.) When that happens mid-session,
  the original file is untouched; write the new content to a fresh
  path, verify it, then swap it in — don't assume the original was
  corrupted without checking.
- **`@Composable` functions are only callable from other `@Composable`
  functions** — not from `LaunchedEffect`, coroutine scopes, or other
  suspend contexts. (Session 23.)
- **currentsprint.md on GitHub is the authoritative completion
  record** — project file copies are a convenience cache only.
- **Flow collection:** always `flow.onEach{}.launchIn(viewModelScope)`,
  never `viewModelScope.launch { flow.collect() }`. For one-shot events
  collected in a composable specifically, use `LaunchedEffect` +
  `collectLatest` instead (Session 25 addition to this lesson — the
  `onEach{}.launchIn()` form is for ViewModel-internal Flow collection,
  not composable-side event collection).
- **PlaybackState naming collision:** `CoordinatorState` (sealed
  interface, PlaybackCoordinator.kt) vs `PlayerLifecycleState` (enum,
  SessionState.kt) — resolved via import aliases. Reuse these exact
  names if a file needs both types again.
- **onCleared() cannot reliably run suspend work** — fix belongs in
  Compose screens via `DisposableEffect(Unit).onDispose`, as
  PlayerScreen.kt does.
- **ExoPlayer instance belongs in the Compose screen, not the
  ViewModel.**
- **Jetpack Navigation Compose cannot pass domain objects as nav
  args** — PendingPlaybackHolder singleton is the workaround; does not
  survive process death (documented in the file). Session 25's Media
  cache/lookup layer closes the original blocker for HomeScreen's
  tap-to-resume specifically (a bare mediaId can now be resolved), but
  does NOT replace PendingPlaybackHolder itself or fix the
  process-death gap — that would still need either serializing
  PlaybackRequest through SavedStateHandle or passing mediaId as a real
  nav arg and having PlayerScreen do its own lookup, neither of which
  exists yet (see Next Steps).
- **MutableStateFlow.update{} does not resolve in this build
  environment** — use direct `_flow.value = _flow.value?.copy(...)`
  assignment instead.
- **A synchronous snapshot read alongside an existing Flow-based
  observe method is a reasonable, low-risk addition when a caller only
  needs a one-off value** (`SessionRepository.getCurrentSession()`,
  Session 24).
- **CI/build environment:** GitHub Actions runners are fresh (no local
  build cache); `gradle-wrapper.jar` handled via
  `gradle/actions/setup-gradle@v3`. Gradle's own remote cache service
  can intermittently fail to save cache entries (Session 25 — "Our
  services aren't available right now") without failing the build
  itself; these show up as Annotations, not as a failed job, and are
  infrastructure noise rather than a code problem.
- **Verify pushed file contents directly, don't infer from build
  status** — re-pull and diff against GitHub before treating any edit
  as landed. Session 25 verified all 9 touched files via a fresh
  tarball pull, grepping for specific wiring markers (not just file
  existence) before writing this update.
- **A use case/coordinator/repository chain returning a singular type
  where the domain clearly implies a collection is worth checking
  explicitly before building a screen against it** (Session 22
  SearchResult example).
- **When a repository method's implementation silently drops fields
  the entity/domain model actually has, that's worth fixing as part of
  whatever adjacent work surfaced it, not just noting for later**
  (Session 24 seasonNumber/episodeNumber fix).

## Next Steps, In Order

1. **Episode-picker screen / Details screen.** Not started. Needed to
   lift SearchScreen's TV_SHOW limitation (Session 22) and would also
   give Continue Watching rows somewhere meaningful to navigate to
   directly (rather than straight to Player) now that the Media lookup
   layer exists (Session 25). `GetMediaByIdUseCase` (Session 25) is
   directly reusable here.
2. **PendingPlaybackHolder replacement / real mediaId nav args.** Not
   started — this was the explicitly-deferred stretch goal from
   Session 25's scope discussion with Dia. The Media cache/lookup layer
   (Session 25) removes the original blocker (no way to resolve a bare
   mediaId), but `PendingPlaybackHolder` itself hasn't been touched and
   still doesn't survive process death. Worth its own dedicated
   session: would mean passing `mediaId` (+ optional episode
   identifiers) as real nav args to `Route.Player`, and having
   `PlayerScreen`/`PlayerViewModel` do their own `GetMediaByIdUseCase`
   resolution on entry instead of reading a pre-built `PlaybackRequest`
   from the holder. Needs design discussion before code, per standing
   practice — in particular how `resumePositionMs` and
   `preferredSource` (manual stream selection) would travel if
   `PlaybackRequest` is no longer built by the calling screen.
3. **Stream-candidate picker UI.** Not started. Needed to lift
   SearchScreen's Smart-Defaults-only limitation (Session 22).
4. **`AppError.ValidationError` case.** Would let
   CreateProfileUseCase/UpdateProfileUseCase's blank-name validation
   and DeleteProfileUseCase's delete-active-profile rejection surface
   proper, distinct user-facing messages instead of both falling
   through SettingsScreen's generic `LocalStorageError` message. Low
   urgency (the UI already avoids the delete-active case proactively).
5. **Completion-percentage / markAsCompleted wiring.** Not started.
   `durationMs` is correctly persisted (Session 24) but nothing
   calculates a completion percentage from it or calls
   `PlaybackRepository.markAsCompleted()` automatically at a threshold
   (~90%, per its doc comment).
6. **SettingsScreen preference-write debounce/Save-step
   reconsideration** — only if it turns out to feel laggy on-device;
   not a confirmed problem, just flagged for revisit.
7. **HomeScreen proactive title/artwork display** — only if showing
   real titles in the Continue Watching list itself (not just after
   tapping) becomes a priority; see Known Limitations above. Would need
   to weigh proactive-resolve cost against UX benefit, likely bounded to
   only the currently-visible rows rather than the whole list.

## Open TODOs (carried forward, unchanged unless noted)

- App icon: placeholder system drawable in AndroidManifest.xml
- SearchRepository.updateSearchSession uses `Map<String, String>` for
  filters; revisit if SearchFilters gets promoted to a domain model
- AppError has no ValidationError case; CreateProfileUseCase and
  UpdateProfileUseCase use LocalStorageError(IllegalArgumentException)
  for blank name validation — semantically incorrect; revisit when the
  error model gets a review pass. Also affects DeleteProfileUseCase's
  active-profile rejection, surfaced via SettingsScreen — see Next
  Steps #4.
- StartPlaybackUseCase uses a fully qualified AppError reference inline;
  tidy to a top-level import if preferred
- HomeViewModel.removeItem() has no failure feedback path —
  PlaybackRepository.removeFromContinueWatching() returns Unit;
  deliberately deferred (Session 16)
- HomeViewModel.kt has a fully-qualified kotlinx.coroutines.Job
  reference inline instead of a top-level import (Session 16) — not
  yet tidied
- SearchScreen.kt uses fully-qualified
  `androidx.compose.foundation.text.KeyboardActions`/`KeyboardOptions`
  inline rather than top-level imports, to avoid a possible
  import-name collision that couldn't be verified without seeing the
  exact Compose BOM version pinned in build.gradle.kts. Revisit and
  tidy to top-level imports once confirmed safe.
- SearchScreen's TV_SHOW tap-disabled state and Smart-Defaults-only
  playback are both deliberate, documented limitations, not bugs.
- `DropdownField<T>` in SettingsScreen.kt is a simple `TextButton` +
  `DropdownMenu` implementation, not Material 3's
  `ExposedDropdownMenuBox`. Functional, but not the "official" M3
  dropdown pattern. Fine as-is; revisit only if visual polish on
  Settings becomes a priority.
- No language list/picker exists — subtitle, audio, and
  content-language preferences are all free-text BCP-47 code entry
  fields with a hint string, not validated or autocompleted. Revisit
  if a proper language picker becomes worth building.
- `PlaybackRepositoryImpl`'s `markAsCompleted()` block has inconsistent
  indentation relative to the rest of the file — pre-existing, cosmetic
  only; clean up whenever that method is next touched for functional
  reasons.
- `ExoPlayer.duration` can report `C.TIME_UNSET` before a stream has
  buffered enough to know its length. Currently stored as-is in
  `ContinueWatchingEntity.durationMs` without correction. Will need
  explicit handling once Next Steps #5 (completion-percentage wiring)
  is scoped.
- **NEW (Session 25):** `MediaCache`'s 7-day TTL is a starting
  assumption, not derived from a specific requirement (see "Session 25
  — What Was Done" above). Revisit if it causes staleness complaints
  once real metadata providers exist.
- **NEW (Session 25):** `HomeUiState.resumeError` is not scoped per-row
  — see Known Limitations above. Would need a proper per-mediaId error
  map if concurrent tap-to-resume resolution is ever supported (it
  currently isn't, deliberately).

At the end of the next session, update currentsprint.md (full file, in
a code block) and verify it directly against
raw.githubusercontent.com/diaviloai/Onedebrid/main/currentsprint.md
before treating the session as closed — and do not treat any session
as closed without an actual green CI result for whatever was last
pushed.