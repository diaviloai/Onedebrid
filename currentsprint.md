# OneDebrid — Current Sprint

## Status

Implementation in progress. Architectural design phase complete.

This file is fully rewritten each session — it reflects actual current
code state, verified by pulling the repo and reading files directly, not
appended to informally.

Build verification: project compiles cleanly as of Session 26's close,
confirmed via GitHub Actions on commit `fdedc16` ("update") — job "build"
succeeded in 4m 6s, per the direct run/job URL Dia provided
(`github.com/diaviloai/Onedebrid/actions/runs/32326148911/job/96297691726`),
fetched via web_fetch since the GitHub Actions jobs API hit the same
shared-IP rate limit as prior sessions. The only annotations on the run
are Gradle cache-service infrastructure warnings ("Our services aren't
available right now") and Node 20/setup-java v4 deprecation notices; none
relate to this session's code. All files touched this session — including
the Session-25-era `Media.kt` fix (see below) — were independently
re-pulled from `raw.githubusercontent.com` and diff-checked against what
was delivered before this file was updated.

**Important correction from this session:** the version of this file
carried into Session 26 (reflecting Session 25's close) claimed
`domain/model/Media.kt` had `@Serializable` added to the `Media` class
itself. On a fresh read at the start of this session, that was false —
only `MediaType` (the enum in the same file) had the annotation; `Media`
did not. Since `MediaCache.kt` calls `json.encodeToString(media)` /
`json.decodeFromString<Media>(...)` directly, this should require a
compile-time serializer for `Media`. Verified against the actual Session
25 CI job log (not just the green/red status) that the build had
genuinely succeeded despite this — the discrepancy is unresolved as a
"why did that compile," but is now moot: `@Serializable` was added to
`Media` this session (commit `291cc10`), verified green independently,
and the live file confirmed to match. Recorded here, not swept under the
rug, per Dia's standing preference for honest documentation of build
problems.

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
    │       │   getMediaDetails()/getEpisodes() cache-first via
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
    │       needed changes this session)
    ├── domain/
    │   ├── error/
    │   │   └── AppError.kt
    │   └── model/
    │       ├── Media.kt (Session 26: @Serializable added to the Media
    │       │   class itself — see "Important correction" above)
    │       ├── Episode.kt (@Serializable present since Session 25)
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
    │   ├── details/ (Session 26 — new package)
    │   │   ├── DetailsScreen.kt
    │   │   └── DetailsViewModel.kt
    │   ├── home/
    │   │   ├── HomeScreen.kt (Session 25: rows tappable, unchanged this
    │   │   │   session — see Session 26 scope decision below)
    │   │   └── HomeViewModel.kt (Session 25: onItemClick(), one-shot
    │   │       navigateToPlayer event — unchanged this session)
    │   ├── navigation/
    │   │   ├── NavGraph.kt (Session 26: added Route.Details with a
    │   │   │   mediaId nav arg — first route in the graph to carry one)
    │   │   └── PendingPlaybackHolder.kt
    │   ├── player/
    │   │   ├── PlayerScreen.kt
    │   │   └── PlayerViewModel.kt
    │   ├── search/
    │   │   ├── SearchScreen.kt (Session 26: all results now tappable,
    │   │   │   navigate to Details instead of building PlaybackRequest
    │   │   │   directly)
    │   │   └── SearchViewModel.kt (Session 26: two doc-comment edits
    │   │       only — activeProfileId is now unused, see Open TODOs)
    │   └── settings/
    │       ├── SettingsScreen.kt
    │       └── ProfileViewModel.kt
    └── usecase/
        ├── CreateProfileUseCase.kt
        ├── DeleteProfileUseCase.kt
        ├── EndPlaybackSessionUseCase.kt
        ├── GetContinueWatchingUseCase.kt
        ├── GetEpisodesUseCase.kt (Session 26 — new, thin wrapper around
        │   MediaRepository.getEpisodes(), same shape as
        │   GetMediaByIdUseCase)
        ├── GetMediaByIdUseCase.kt (Session 25)
        ├── RemoveFromContinueWatchingUseCase.kt
        ├── SavePlaybackPositionUseCase.kt
        ├── SearchMediaUseCase.kt
        ├── SwitchProfileUseCase.kt
        ├── UpdateProfileUseCase.kt
        └── (others per earlier sessions)

(This tree reflects what's been directly read/touched across sessions,
not a guaranteed exhaustive listing — see the repo itself for ground
truth on files not mentioned in recent session notes.)

## App Navigation State (as of Session 26)

Five routes exist, all wired into a single `NavGraph.kt`:

- **Home** (`Route.Home`, start destination) → real `HomeScreen.kt`.
  Shows Continue Watching. Rows are tappable to resume playback directly
  (Session 25) — this flow is **unchanged this session**, deliberately;
  see Session 26 scope decision below. Top bar has Search and Settings
  actions.
- **Search** (`Route.Search`) → real `SearchScreen.kt`. Reached via
  Home's Search button. As of Session 26, tapping any result (movie or
  TV show) navigates to **Details** with that result's `mediaId`, rather
  than building a `PlaybackRequest` itself.
- **Details** (`Route.Details`, new this session) → real
  `DetailsScreen.kt`. Takes a `mediaId` nav argument (the first route in
  the graph to carry one — `Route.Details.build(mediaId)`). Reached only
  from Search. Re-fetches the full `Media` via `GetMediaByIdUseCase`; for
  `MediaType.TV_SHOW`, also fetches the episode list via the new
  `GetEpisodesUseCase`. Movie: header + single Play action. TV show:
  header + episode list grouped by season, tap an episode to play it.
  Both play actions populate `PendingPlaybackHolder` and navigate to
  Player, the same handoff Search used to do directly. Back-press
  returns to Search.
- **Player** (`Route.Player`) → real `PlayerScreen.kt`. Reads
  `PlaybackRequest` from `PendingPlaybackHolder`; routes back to Home
  (with `popUpTo` clearing itself off the stack) if nothing is pending.
  Reachable from Home (direct resume) and from Details (movie Play /
  episode tap), both via the same `PendingPlaybackHolder`-then-navigate
  pattern.
- **Settings** (`Route.Settings`) → real `SettingsScreen.kt`. Reached
  via Home's Settings button. Leaf destination.

No `Route` entries exist beyond these five.

## Session 26 — What Was Done

**Scope agreed with Dia up front:** build the Episode-picker/Details
screen (Next Steps #1 from Session 25), reached only from Search.
Continue Watching's existing direct-to-Player flow was explicitly left
unchanged to preserve exact `resumePositionMs` resume behavior — routing
it through Details too would have silently dropped that unless
`resumePositionMs` were threaded through the nav route as well, which
Dia opted not to do this session. This was a deliberate scope decision,
flagged before writing code, not an oversight.

Before writing code, read the actual current `Media.kt`, `Episode.kt`,
`MediaRepository.kt`, `MediaRepositoryImpl.kt` (`getEpisodes()`
specifically), `GetMediaByIdUseCase.kt`, `SearchResult.kt`,
`PlaybackRequest.kt`, `WatchedItem.kt`, `RepositoryResult.kt`,
`AppError.kt`, `NavGraph.kt`, `PendingPlaybackHolder.kt`, `HomeViewModel.kt`,
`HomeScreen.kt`, `SearchScreen.kt`, `SearchViewModel.kt`, and
`strings.xml` per standing practice, before proposing a design.

**Pre-existing bug found and fixed first, before the main feature:**
`Media.kt` was missing `@Serializable` on the `Media` data class itself
— see "Important correction" above for the full account, including that
CI had genuinely gone green on Session 25's code despite this. Verified
fresh via `raw.githubusercontent.com` (not trusting the tarball alone)
that this wasn't a stale-pull artifact before treating it as real. Fixed
with a single-line addition (commit `291cc10`), verified green
independently via Dia-provided job URL, live file re-confirmed to match.

**Files created:**
- **`usecase/GetEpisodesUseCase.kt`** — thin wrapper around
  `MediaRepository.getEpisodes()`, same one-line-body shape as
  `GetMediaByIdUseCase`. Kept as its own Use Case rather than folded into
  an existing one, per Internal_API_Specification.md's "one Use Case per
  business operation" rule.
- **`ui/details/DetailsViewModel.kt`** — `DetailsUiState` is a single
  data class (not sealed Loading/Success/Error), same reasoning as
  `HomeUiState`: `media`/`isLoadingMedia`/`mediaError` track the primary
  load, `episodes`/`isLoadingEpisodes`/`episodesError` track a second,
  independent load that only applies to `TV_SHOW`. Takes `mediaId` via
  `SavedStateHandle` (Hilt + Nav Compose auto-populates this from the
  route arg — `androidx.hilt:hilt-navigation-compose` was already a
  dependency, no new one needed). `onPlayMovie()`/`onPlayEpisode()`
  build a `PlaybackRequest` with `resumePositionMs = null` (always plays
  from the start — this screen has no resume-position context, see scope
  decision above) and hand off to `PendingPlaybackHolder`, then emit a
  one-shot `Channel<Unit>`-backed navigation event — same pattern as
  `HomeViewModel.navigateToPlayer` (Session 25).
- **`ui/details/DetailsScreen.kt`** — renders `LoadingContent`/
  `ErrorContent` (reusing the same `isRecoverable`-driven convention as
  `PlayerScreen`/`SearchScreen`'s own `ErrorContent` — now a third copy
  of the same pattern, not a new one) or `MediaContent`, which branches
  on `Media.type`: a Play button for `MOVIE`, or an `EpisodeList` for
  `TV_SHOW` (grouped by season, sorted season-then-episode-number
  regardless of the order `getEpisodes()` returns them in).

**Files modified:**
- **`ui/navigation/NavGraph.kt`** — added `Route.Details` (with
  `build(mediaId)` helper) and its `composable()` registration using
  `navArgument("mediaId") { type = NavType.StringType }`. Search's
  destination now takes `onNavigateToDetails` instead of
  `onNavigateToPlayer`/`pendingPlaybackHolder`. Doc comments for Home,
  Search, Player, Settings, and the new Details entry all updated.
- **`ui/search/SearchScreen.kt`** — removed direct `PlaybackRequest`
  building and the `pendingPlaybackHolder` param entirely; tapping any
  result now calls `onNavigateToDetails(searchResult.media.id)`.
  `SearchResultRow` no longer splits on `isPlayable` — every row is
  `clickable` now, and the "Not yet supported" trailing label is gone
  (its string resource `search_tv_show_unsupported` is now unused, left
  in place — see Open TODOs). This closes the "TV_SHOW not yet playable"
  gap flagged since Session 22.
- **`ui/search/SearchViewModel.kt`** — two doc-comment-only edits (no
  logic changes). `SearchUiState.activeProfileId` is now dead state
  (SearchScreen no longer reads it, since the `PendingPlaybackHolder`
  handoff moved to `DetailsViewModel`). Discussed with Dia explicitly:
  **left in place rather than removed**, flagged in Open TODOs to
  revisit near project end if still unused. Stale doc comments
  referencing the old reason it existed were corrected to reflect this.
- **`res/values/strings.xml`** — added `details_back`, `details_play`,
  `details_no_episodes`, `details_season_header`,
  `details_episode_fallback_title`. Added an inline XML comment flagging
  `search_tv_show_unsupported` as unused as of this session (string left
  in place, not deleted).

**Important, expected, and deliberately-not-worked-around limitation:**
same as Session 25's Continue Watching gap — `MetadataProvider` is still
`StubMetadataProvider`, so `GetEpisodesUseCase` will surface
`AppError.AllProvidersUnavailable` in the episode list at runtime today
unless that `mediaId` was already cache-hit. `DetailsScreen` renders
this correctly via its own `ErrorContent`/retry path. Not a new gap —
the same one already documented for Continue Watching — just now visible
in a second place. Will resolve automatically once a real
`MetadataProvider` exists, no revisit needed here.

All 7 touched files (3 new, 4 edited) were independently re-pulled from
`raw.githubusercontent.com` and diffed against what was delivered
(byte-for-byte identical aside from trailing-newline differences, which
are cosmetic) before this session was treated as closed.

## Known, Deliberate Limitations (documented in code, not silently
worked around)

- **SearchScreen playback always uses Smart Defaults**
  (`preferredSource = null`, no stream-candidate picker) — from
  Session 22, still true. Now also true of Details' play actions, for
  the same reason (no picker UI exists yet).
- **Details' play actions always start from position 0** — no
  resume-position context reaches this screen; only Continue Watching's
  direct-to-Player flow (Session 25) carries `resumePositionMs`. A
  future enhancement could have Details check Continue Watching for the
  given `mediaId` and offer resume-from-here, but this was explicitly
  scoped out of Session 26 (see "What Was Done" above).
- **HomeScreen Continue Watching resolve failures are not row-scoped in
  state** (`resumeError` is a single most-recent-failure field, not a
  per-mediaId map) — from Session 25, unchanged. Acceptable given only
  one row can resolve at a time.
- **HomeScreen rows do not proactively resolve/display real
  titles/artwork** — from Session 25, unchanged. Resolution only happens
  on tap.
- **SettingsScreen preference edits write to Room on every single
  toggle/dropdown/keystroke** (no debounce, no explicit Save step) —
  from earlier sessions, unchanged. Flagged as a possible follow-up only
  if it feels laggy on-device.
- **SettingsScreen blank-name and delete-active-profile validation
  errors both surface as a generic error message** — `AppError` has no
  `ValidationError` case yet. See Open TODOs.
## Carried-Forward Lessons

- **Verify a stale-seeming discrepancy against the actual repo before
  assuming the doc is right** (Session 26) — this session's sprint doc
  claimed `Media.kt` had `@Serializable`; a fresh read showed it didn't.
  Confirmed via an independent `raw.githubusercontent.com` pull (not
  just the tarball) that this wasn't a stale-pull artifact, and via the
  actual CI job log (not just green/red status) that the prior session's
  build had genuinely succeeded anyway, before concluding it was a real
  gap worth fixing rather than a misread. Memory/handoff notes can be
  wrong; the live repo and live CI logs are the actual source of truth.
- **A one-shot ViewModel → UI event needs a `Channel`, not a second
  `StateFlow`** (Session 25, reused as-is Session 26 for
  `DetailsViewModel.navigateToPlayer`) — `Channel<Unit>(Channel.BUFFERED)`
  + `receiveAsFlow()`, collected via `LaunchedEffect` + `collectLatest`
  in the composable.
- **Nav args need explicit `NavType` registration** — `composable(route,
  arguments = listOf(navArgument("x") { type = NavType.StringType }))`;
  `SavedStateHandle["x"]` in the ViewModel picks it up automatically via
  `hilt-navigation-compose` (already a dependency, confirmed present
  before assuming it would just work).
- **A generic, already-registered-but-unused Room table is worth
  grepping for before assuming a new one is needed** (Session 25,
  `CacheEntryEntity`/`CacheEntryDao`).
- **A repository method gaining a new constructor dependency doesn't
  need a DI module change if the new dependency is itself
  `@Singleton @Inject constructor` and the repository's binding is a
  plain `@Binds` interface binding** (Session 25) — Hilt resolves the
  chain automatically.
- **When a shared-IP GitHub Actions API rate limit blocks CI
  verification, ask for the direct run/job URL and `web_fetch` it
  instead** (Session 25, confirmed again Session 26) — the job page's
  Annotations section is sufficient to distinguish real build failures
  from infrastructure noise without needing raw log access.
- **Grep build.gradle.kts / libs.versions.toml before importing anything
  from a library not yet used elsewhere in the codebase** — confirmed
  again Session 26 for `hilt-navigation-compose` and `NavType`/
  `navArgument` (both already present via `androidx-navigation-compose`
  and `androidx-hilt-navigation-compose`).
- **Don't declare a session or a file "done" without an actual CI
  result** — checked via the direct run/job URL fetch, not inferred from
  "build clean" or "pushed" alone.
- **`@Composable` functions are only callable from other `@Composable`
  functions** — not from `LaunchedEffect`, coroutine scopes, or other
  suspend contexts. (Session 23.)
- **Flow collection:** always `flow.onEach{}.launchIn(viewModelScope)`,
  never `viewModelScope.launch { flow.collect() }`. For one-shot events
  collected in a composable, use `LaunchedEffect` + `collectLatest`
  instead.
- **`MutableStateFlow.update{}` does not resolve in this build
  environment** — use direct `_flow.value = _flow.value?.copy(...)`
  assignment instead.
- **`PlaybackState` naming collision:** `CoordinatorState` (sealed
  interface, `PlaybackCoordinator.kt`) vs `PlayerLifecycleState` (enum,
  `SessionState.kt`) — resolved via import aliases. Reuse these exact
  names if a file needs both types again.
- **Jetpack Navigation Compose cannot pass domain objects as nav args**
  — primitives only (confirmed again Session 26: `mediaId: String` is
  the first nav arg used in this project). `PendingPlaybackHolder`
  singleton remains the workaround for passing a full `PlaybackRequest`;
  still does not survive process death.
- **ExoPlayer instance belongs in the Compose screen, not the
  ViewModel** (`PlayerScreen.kt`'s `DisposableEffect(Unit).onDispose`
  pattern).
- **When a paste truncates at a consistent point across retries,
  that's a signal to chunk the paste, not retry it unchanged.**
  (Session 23.) All files delivered in Session 26 were well under the
  ~450-500 line risk zone (largest was `DetailsScreen.kt` at ~300
  lines), so no chunking was needed for source files this session.
- **currentsprint.md on GitHub is the authoritative completion record**
  — project file copies / prior-session memory summaries are a
  convenience cache only and can be stale. Confirmed the hard way this
  session (see "Important correction" above).

## Next Steps, In Order

1. **PendingPlaybackHolder replacement / real mediaId nav args to
   Player.** Not started — deferred stretch goal since Session 25.
   Session 26 added `mediaId` nav args for `Route.Details`, which proves
   the pattern works end-to-end, but `Route.Player` still reads from
   `PendingPlaybackHolder`, not its own nav arg. Doing this for Player
   too would mean passing `mediaId` (+ optional episode identifiers) as
   real nav args and having `PlayerViewModel` do its own
   `GetMediaByIdUseCase` resolution on entry. Needs design discussion
   before code — in particular how `resumePositionMs` and
   `preferredSource` would travel if `PlaybackRequest` is no longer
   built by the calling screen, and how Details' current
   `PendingPlaybackHolder`-based handoff would need to change too.
2. **Stream-candidate picker UI.** Not started. Needed to lift both
   SearchScreen's and Details' Smart-Defaults-only limitation.
3. **Continue Watching → Details routing with resumePositionMs.** Not
   started, explicitly deferred this session (see "What Was Done"
   above). Would mean threading an optional `resumePositionMs` through
   `Route.Details`'s nav args so Details' play action can resume exactly
   rather than restarting from 0, if Dia decides Continue Watching
   should route through Details after all.
4. **`AppError.ValidationError` case.** Would let profile-related
   validation errors surface distinct user-facing messages instead of a
   generic `LocalStorageError` message. Low urgency.
5. **Completion-percentage / markAsCompleted wiring.** Not started.
   `durationMs` is correctly persisted but nothing calculates completion
   percentage or calls `PlaybackRepository.markAsCompleted()`
   automatically at a threshold (~90%).
6. **SettingsScreen preference-write debounce** — only if it turns out
   to feel laggy on-device; not confirmed, just flagged.
7. **HomeScreen proactive title/artwork display** — only if a priority;
   see Known Limitations.

## Open TODOs (carried forward, unchanged unless noted)

- App icon: placeholder system drawable in AndroidManifest.xml
- SearchRepository.updateSearchSession uses `Map<String, String>` for
  filters; revisit if SearchFilters gets promoted to a domain model
- AppError has no ValidationError case; CreateProfileUseCase and
  UpdateProfileUseCase use LocalStorageError(IllegalArgumentException)
  for blank name validation — semantically incorrect; revisit when the
  error model gets a review pass. Also affects DeleteProfileUseCase's
  active-profile rejection. See Next Steps #4.
- StartPlaybackUseCase uses a fully qualified AppError reference inline;
  tidy to a top-level import if preferred
- HomeViewModel.removeItem() has no failure feedback path — deliberately
  deferred (Session 16)
- HomeViewModel.kt has a fully-qualified kotlinx.coroutines.Job
  reference inline instead of a top-level import (Session 16)
- SearchScreen.kt uses fully-qualified
  `androidx.compose.foundation.text.KeyboardActions`/`KeyboardOptions`
  inline rather than top-level imports (possible import-name collision,
  unverified against the exact Compose BOM version). Revisit once
  confirmed safe.
- `DropdownField<T>` in SettingsScreen.kt is a `TextButton` +
  `DropdownMenu`, not Material 3's `ExposedDropdownMenuBox`. Functional;
  revisit only if visual polish becomes a priority.
- No language list/picker exists — free-text BCP-47 code entry fields
  only. Revisit if a proper picker becomes worth building.
- `PlaybackRepositoryImpl`'s `markAsCompleted()` block has inconsistent
  indentation — cosmetic only.
- `ExoPlayer.duration` can report `C.TIME_UNSET` before a stream has
  buffered enough to know its length; stored as-is currently. Needs
  handling once Next Steps #5 is scoped.
- `MediaCache`'s 7-day TTL is a starting assumption (Session 25), not
  derived from a specific requirement. Revisit if staleness complaints
  arise once real metadata providers exist.
- `HomeUiState.resumeError` is not scoped per-row (Session 25) — see
  Known Limitations.
- **NEW (Session 26):** `SearchUiState.activeProfileId` is dead state —
  SearchScreen no longer reads it. Left in place per Dia's explicit
  Session 26 call; revisit near project end if still unused.
- **NEW (Session 26):** `search_tv_show_unsupported` string resource is
  now unused (its UI usage was removed when SearchResultRow's
  isPlayable split was deleted). Left in place with an inline XML
  comment flagging it; low-priority cleanup.
- **NEW (Session 26):** `Media.id` for a `SearchResult` tapped in
  Search vs. the `Media` re-fetched by `DetailsViewModel` via
  `GetMediaByIdUseCase` are assumed to always resolve to the same
  `mediaId` round-trip cleanly as a String. Not verified with an
  automated test (none exist in this repo) — worth keeping in mind if
  odd "media not found" reports ever surface from Details specifically.

At the end of the next session, update currentsprint.md (full file, in
a code block, chunked into sequential pastes if it's likely to exceed
~450-500 lines) and verify it directly against
raw.githubusercontent.com/diaviloai/Onedebrid/main/currentsprint.md
before treating the session as closed — and do not treat any session as
closed without an actual green CI result for whatever was last pushed,
verified via the direct run/job URL if the Actions API is rate-limited.