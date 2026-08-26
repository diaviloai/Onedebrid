# OneDebrid — Current Sprint

## Status

Implementation in progress. Architectural design phase complete.

This file is fully rewritten each session — it reflects actual current
code state, verified by pulling the repo and reading files directly, not
appended to informally.

Build verification: project compiles cleanly as of Session 28's close,
confirmed via GitHub Actions on the latest pushed commit — job "build"
succeeded, per the direct run/job URL Dia provided
(`github.com/diaviloai/Onedebrid/actions/runs/32914259505/job/98014508092`).
All files touched this session were independently re-pulled from
`raw.githubusercontent.com` after the push and diffed against intended
content (brace-balance checked) before this file was updated. Also
verified this session, as its own separate small change: the
comment-only `PendingPlaybackHolder` cleanup punted from Session 27 (see
Session 28 below) — CI run
`runs/32855111601` confirmed green for that push too.

**Sessions 1–25 summary** (condensed from prior full write-ups, which
remain in git history on this file if the detail is ever needed): built
layer by layer — domain models → error types → provider interfaces →
repository interfaces → Room entities/DAOs → Hilt wiring → coroutine
infrastructure → use cases → coordinators → ViewModels → Compose screens
(SearchScreen, HomeScreen, SettingsScreen). Session 25 added Continue
Watching tap-to-resume (`PendingPlaybackHolder` + direct-to-Player nav)
and cache-first `MediaRepository` reads via `MediaCache`.

**Session 26 summary:** built the Details/Episode-picker screen
(`ui/details/DetailsScreen.kt` + `DetailsViewModel.kt`), reached only from
Search. `Route.Details` became the first route in the graph to carry a
`mediaId` nav arg. Search's results became fully tappable (closing the
"TV_SHOW not yet supported" gap). Continue Watching's direct-to-Player
flow was deliberately left unchanged (explicit scope decision, to
preserve exact `resumePositionMs` behavior). Also fixed a real
pre-existing bug: `Media.kt` was missing `@Serializable` on the `Media`
class itself despite `MediaCache.kt` requiring it for JSON
encode/decode — found on a fresh read at the start of that session
(despite Session 25's handoff claiming it was already fixed), corrected,
verified independently green.

**Session 27 summary:** retired `PendingPlaybackHolder` (an in-memory
singleton) entirely, replacing it with real Navigation Compose arguments
to the Player route (`mediaId` required, `episodeId`/`resumeMs` optional
via sentinel values — see Carried-Forward Lessons). `PlayerViewModel` now
resolves `Media`/`Episode`/active profile itself on init via
`GetMediaByIdUseCase`/new `GetEpisodeByIdUseCase`/`GetActiveProfileUseCase`,
then builds its own `PlaybackRequest`, instead of receiving one ready-made
from a singleton. `HomeViewModel` and `DetailsViewModel` simplified as a
consequence — neither pre-resolves `Media` before navigating anymore; both
just emit a `PlayerNavArgs` (new type, `ui.navigation`) carrying
primitives. `HomeScreen`'s old resolving-state UI was removed as
genuinely dead code (an explicit, Dia-confirmed tradeoff: Home's Continue
Watching failure surface now shows on Player's screen via its existing
error card + retry, not inline on the Home row). New
`MediaRepository.getEpisodeById()` added, implemented via the existing
`getEpisodes()` + an in-memory filter (no per-episode cache/provider
granularity exists yet — documented as an internal detail subject to
change). `PendingPlaybackHolder.kt` itself deleted. Twelve files
touched/created, one deleted. Two real compile-breaking mistakes were
made and fixed within the session (a `str_replace` insertion landing
after `MediaRepositoryImpl.kt`'s actual closing brace, duplicating/
truncating `resolveStream()`; a missing final closing brace in
`DetailsViewModel.kt`) — both root-caused and fixed before the session's
CI run came back green. Comment-only `PendingPlaybackHolder` references
were left behind in three files, explicitly punted due to time (cleared
in Session 28, see below).

## Package Structure

com.onedebrid.app/
    ├── MainActivity.kt (Session 27: no longer field-injects
    │   PendingPlaybackHolder — that class deleted that session)
    ├── OneDebridApplication.kt
    ├── coordinator/
    │   ├── PlaybackCoordinator.kt (Session 28: play() now passes
    │   │   profileId through to resolvePlaybackUseCase() — see "What
    │   │   Was Done" below)
    │   ├── SearchCoordinator.kt
    │   └── SessionCoordinator.kt
    ├── data/
    │   ├── local/
    │   │   ├── AppDatabase.kt
    │   │   ├── MediaCache.kt (Session 25 — wraps CacheEntryDao for
    │   │   │   Media/Episode-list JSON caching; stale PendingPlaybackHolder
    │   │   │   doc-comment reference cleared in Session 28)
    │   │   ├── TypeConverters.kt
    │   │   ├── dao/ (unchanged)
    │   │   └── entity/ (unchanged)
    │   └── repository/
    │       ├── MediaRepository.kt (Session 27: added
    │       │   getEpisodeById(mediaId, episodeId) to the interface)
    │       ├── MediaRepositoryImpl.kt (Session 27: implements
    │       │   getEpisodeById() via getEpisodes() + in-memory filter)
    │       ├── PlaybackRepository.kt / PlaybackRepositoryImpl.kt
    │       ├── ProfileRepository.kt / ProfileRepositoryImpl.kt
    │       ├── RepositoryResult.kt
    │       ├── SearchRepository.kt / SearchRepositoryImpl.kt
    │       ├── SessionRepository.kt / SessionRepositoryImpl.kt
    │       └── (Subtitle/Download repositories not yet built)
    ├── di/
    │   ├── CoroutineDispatchers.kt
    │   └── (Hilt modules — DatabaseModule, RepositoryModule,
    │       ProviderModule; unchanged this session. ProviderModule binds
    │       StubSearchProvider as the sole SearchProvider — see Known
    │       Limitations)
    ├── domain/
    │   ├── error/
    │   │   └── AppError.kt (Unknown case covers getEpisodeById()'s
    │   │       not-found case, Session 27 — see Open TODOs re: future
    │   │       ValidationError-style review)
    │   └── model/
    │       ├── Media.kt
    │       ├── Episode.kt
    │       ├── PlaybackRequest.kt (preferredSource: StreamCandidate?
    │       │   — null means Smart Defaults as of Session 28, see below)
    │       ├── SearchResult.kt (StreamCandidate defined here — unresolved
    │       │   torrent/magnet result from search, distinct from
    │       │   StreamSource)
    │       ├── SessionState.kt (SessionState, PlaybackSession,
    │       │   SearchSession, PlaybackState enum)
    │       ├── StreamSource.kt (resolved/playable stream; VideoQuality
    │       │   enum)
    │       ├── SubtitleTrack.kt (SubtitleFormat enum)
    │       ├── UserProfile.kt (PlaybackPreferences, SubtitlePreferences,
    │       │   SearchPreferences, ThemePreferences)
    │       └── WatchedItem.kt
    ├── provider/
    │   └── search/
    │       ├── SearchProvider.kt (interface, unchanged)
    │       └── StubSearchProvider.kt (always returns
    │           ProviderError.ServiceUnavailable — the only SearchProvider
    │           bound via Hilt; see Known Limitations)
    │   (MetadataProvider, StubMetadataProvider, DebridProvider, others —
    │    unchanged; no per-episode-id provider call exists, see Session 27
    │    notes)
    ├── ui/
    │   ├── details/
    │   │   ├── DetailsScreen.kt (Session 27: onNavigateToPlayer
    │   │   │   signature carries mediaId/episodeId/resumeMs)
    │   │   └── DetailsViewModel.kt (Session 27: onPlayMovie/onPlayEpisode
    │   │       emit PlayerNavArgs instead of building a PlaybackRequest;
    │   │       no longer needs GetActiveProfileUseCase)
    │   ├── home/
    │   │   ├── HomeScreen.kt (Session 27: onNavigateToPlayer signature
    │   │   │   changed; dead resolvingMediaId/isResolving/resumeError UI
    │   │   │   removed)
    │   │   └── HomeViewModel.kt (Session 27: onItemClick() emits
    │   │       PlayerNavArgs immediately, no longer resolves Media first;
    │   │       no longer needs GetMediaByIdUseCase)
    │   ├── navigation/
    │   │   ├── NavGraph.kt (Session 27: Route.Player carries mediaId/
    │   │   │   episodeId/resumeMs nav args instead of a
    │   │   │   PendingPlaybackHolder parameter)
    │   │   └── PlayerNavArgs.kt (Session 27 — shared nav-arg payload type
    │   │       used by both HomeViewModel and DetailsViewModel)
    │   ├── player/
    │   │   ├── PlayerScreen.kt (Session 27: no longer takes
    │   │   │   pendingPlaybackHolder/onMissingRequest params; renders a
    │   │   │   ResolveState layer above the existing CoordinatorState
    │   │   │   handling)
    │   │   └── PlayerViewModel.kt (Session 27: resolves its own Media/
    │   │       Episode/active-profile on init from SavedStateHandle nav
    │   │       args, builds its own PlaybackRequest)
    │   ├── search/
    │   │   ├── SearchScreen.kt (Session 28: stale PendingPlaybackHolder
    │   │   │   doc-comment references cleared, reworded to describe the
    │   │   │   current PlayerNavArgs-based flow)
    │   │   └── SearchViewModel.kt (Session 28: same cleanup — comment
    │   │       explaining dead activeProfileId state reworded, no longer
    │   │       names the deleted class)
    │   └── settings/
    │       ├── SettingsScreen.kt
    │       └── ProfileViewModel.kt
    └── usecase/
        ├── CreateProfileUseCase.kt
        ├── DeleteProfileUseCase.kt
        ├── EndPlaybackSessionUseCase.kt
        ├── GetActiveProfileUseCase.kt
        ├── GetContinueWatchingUseCase.kt
        ├── GetEpisodeByIdUseCase.kt (Session 27 — thin wrapper around
        │   MediaRepository.getEpisodeById(), same shape as
        │   GetMediaByIdUseCase)
        ├── GetEpisodesUseCase.kt
        ├── GetMediaByIdUseCase.kt
        ├── RemoveFromContinueWatchingUseCase.kt
        ├── ResolvePlaybackUseCase.kt (Session 28: invoke() now takes
        │   profileId; added a private resolveSmartDefault() fallback for
        │   the preferredSource == null case — see "What Was Done" below)
        ├── SavePlaybackPositionUseCase.kt
        ├── SearchMediaUseCase.kt
        ├── SwitchProfileUseCase.kt
        ├── UpdateProfileUseCase.kt
        └── (others per earlier sessions)

(This tree reflects what's been directly read/touched across sessions,
not a guaranteed exhaustive listing — see the repo itself for ground
truth on files not mentioned in recent session notes.)

## App Navigation State (as of Session 27, unchanged in Session 28)

Five routes exist, all wired into a single `NavGraph.kt`:

- **Home** (`Route.Home`, start destination) → real `HomeScreen.kt`.
  Shows Continue Watching. Rows are tappable to resume playback, and
  navigate immediately to Player via
  `Route.Player.build(mediaId, episodeId, resumeMs)`. Any resolution
  failure surfaces on the Player screen itself via its existing error
  card + retry, not inline on the Home row (explicit, discussed
  tradeoff — see Session 27 summary). Top bar has Search and Settings
  actions.
- **Search** (`Route.Search`) → real `SearchScreen.kt`. Reached via
  Home's Search button. Tapping any result (movie or TV show) navigates
  to Details with that result's `mediaId`.
- **Details** (`Route.Details`) → real `DetailsScreen.kt`. Takes a
  `mediaId` nav argument. Reached only from Search. Re-fetches the full
  `Media` via `GetMediaByIdUseCase`; for `MediaType.TV_SHOW`, also
  fetches the episode list via `GetEpisodesUseCase`. Movie: header +
  single Play action. TV show: header + episode list grouped by season.
  Both play actions navigate directly to Player via
  `Route.Player.build(mediaId, episodeId, resumeMs = null)`. Back-press
  returns to Search.
- **Player** (`Route.Player`) → real `PlayerScreen.kt`. Takes `mediaId`
  (required path segment), `episodeId` (optional, sentinel `"none"`), and
  `resumeMs` (optional, sentinel `-1L`) as real nav args. `PlayerViewModel`
  resolves `Media`/`Episode`/active profile itself from these on init,
  builds its own `PlaybackRequest`, and calls `PlaybackCoordinator.play()`.
  `preferredSource` is always `null` (no stream-candidate picker UI
  exists yet — as of Session 28 this now triggers a real Smart Defaults
  search-and-select fallback in `ResolvePlaybackUseCase` rather than an
  immediate failure, see below, though it has no real data to work with
  until a non-stub `SearchProvider` exists).
- **Settings** (`Route.Settings`) → real `SettingsScreen.kt`. Reached
  via Home's Settings button. Leaf destination.

No `Route` entries exist beyond these five.
## Session 28 — What Was Done

**Scope confirmed with Dia up front:** two candidates were on the table
(stream-candidate picker UI; Continue Watching → Details routing), per
Session 27's Next Steps. Investigated the picker UI first per Dia's
choice, but before proposing a design, traced the actual current
behavior of `preferredSource = null` end-to-end and found it was not
"Smart Defaults" at all — `ResolvePlaybackUseCase` failed immediately
with `NoCachedStreamAvailable` whenever `preferredSource` was null,
which is every caller in the codebase today. There was no fallback
selection logic anywhere. This is a real bug relative to
`Project_Design.md`'s Smart Defaults principle, not a stylistic gap.

Also found, while tracing this: `SearchProvider` has exactly one Hilt-
bound implementation, `StubSearchProvider`, which unconditionally
returns `ProviderError.ServiceUnavailable`. There is no real search/
scraper integration in this codebase yet. This means a stream-candidate
picker UI, if built now, would have no real data to render — every
search attempt fails today, picker or no picker.

**Discussed with Dia and agreed:** given the stub-provider situation,
building the picker screen now would mean building real UI/nav/ViewModel
surface area against fake or nonexistent data, likely requiring
significant rework once real search exists. Fixing the Smart Defaults
fallback bug first was lower-risk (small, contained, one existing method
plus one new private method, one caller to update) and independently
correct regardless of when the picker gets built — the picker will need
the same "get candidates for this Media" logic, just user-facing instead
of auto-applied. Chose to do the fallback fix this session and leave the
picker for a dedicated session once real search data exists to build and
test it against. **Explicitly flagged for later:** the stream-candidate
picker UI is still on the Next Steps list (see below) — this was a
reprioritization within the session, not a decision to drop it.

**Also completed this session:** the three comment-only
`PendingPlaybackHolder` references punted from Session 27
(`SearchViewModel.kt`, `SearchScreen.kt`, `MediaCache.kt`) were cleaned
up as a separate, smaller change before the main fallback work — each
comment was reworded to describe the current `PlayerNavArgs`-based flow
rather than the deleted class, preserving the original intent of each
comment rather than just deleting references. Verified independently
green via CI (`runs/32855111601`) before moving on to the fallback work.

**Files modified:**
- **`usecase/ResolvePlaybackUseCase.kt`** — `invoke()` signature gained a
  `profileId: String` parameter (needed for the new search call, which
  requires one). Added a private `resolveSmartDefault(request, profileId)`
  method, called when `request.preferredSource == null` instead of
  immediately returning `NoCachedStreamAvailable`. It calls
  `mediaRepository.search(query = request.media.title, profileId)`,
  filters results to `SearchResult`s whose `media.id == request.media.id`
  (a title-text search can plausibly return other matches — e.g. remakes
  or similarly-named shows — and resolving the wrong title would be worse
  than failing), then picks the first `StreamCandidate` with a non-null
  `hash` from the first matching result's `candidates` list, and resolves
  it via the existing `mediaRepository.resolveStream()`. If no matching
  result or no hash-bearing candidate is found, falls back to the same
  `NoCachedStreamAvailable` failure as before — behavior is unchanged in
  the "truly nothing available" case, only the "never even tried" case
  is fixed.
- **`coordinator/PlaybackCoordinator.kt`** — one-line change:
  `resolvePlaybackUseCase(request)` → `resolvePlaybackUseCase(request,
  profileId)`. `profileId` was already a parameter of `play()`, so this
  is a call-site update only, no structural change. Confirmed via
  repo-wide grep that this is the only call site of
  `resolvePlaybackUseCase()` in the codebase — no other caller needed
  updating.
- **`data/local/MediaCache.kt`**, **`ui/search/SearchScreen.kt`**,
  **`ui/search/SearchViewModel.kt`** — comment-only edits, see above.

**Selection rule is deliberately minimal, not a real ranking
algorithm:** "first candidate with a hash" — no quality-preference
weighting, no cached-status prioritization. This was a discussed,
agreed-on choice (see the design discussion above, under "Scope
confirmed with Dia up front") given there is no real
search data or profile-preference signal reaching this layer yet to
rank against meaningfully; inventing a scoring heuristic now would be
guessing at criteria rather than implementing anything real. The
selection logic lives as a private method inside
`ResolvePlaybackUseCase` for now (not a `MediaRepository` method, since
ranking is business logic per `Technical_standards.md`'s layer
boundaries; not yet its own reusable Use Case/component, per Simplicity
First — not enough logic yet to justify the abstraction). Flagged
explicitly as the point to extract from when the stream-candidate picker
UI is eventually built, since the picker needs the same "get candidates
for this Media" step, just surfaced to the user instead of auto-applied.

**Build verification:** two separate pushes this session, both verified
independently green via direct run/job URL (Actions API was rate-limited
both times, consistent with prior sessions):
1. Comment-cleanup push — `runs/32855111601`, succeeded.
2. `ResolvePlaybackUseCase.kt`/`PlaybackCoordinator.kt` push —
   `runs/32914259505/job/98014508092`, succeeded in 4m 44s. Annotations
   on this run were, again, entirely Gradle cache-service outage noise
   ("Our services aren't available right now") plus Node/setup-java
   deprecation warnings — confirmed these were not masking a real
   compiler error by checking the job's actual top-level status line
   ("build succeeded"), not just the absence of red annotations, per the
   Session 27 lesson about infra noise not being sufficient on its own.

All four touched files were re-pulled fresh from
`raw.githubusercontent.com` after each push and diffed against intended
content before this file was updated. `ResolvePlaybackUseCase.kt` had one
cosmetic difference (missing trailing newline at EOF, a Spck/Git
artifact, not a content or compile issue) — everything else matched
exactly, including `PlaybackCoordinator.kt` byte-for-byte. Brace balance
confirmed on all four.

## Known, Deliberate Limitations (documented in code, not silently
worked around)

- **No real `SearchProvider` implementation exists.** `StubSearchProvider`
  is the only Hilt-bound implementation and always returns
  `ProviderError.ServiceUnavailable`. This means: the Search screen
  itself always shows its error state; the new Smart Defaults fallback
  in `ResolvePlaybackUseCase` (Session 28) is architecturally correct
  but cannot currently produce a real result, since its search call also
  goes through this same stub. Both are expected to start working for
  real, unmodified, once a real `SearchProvider` is wired in — this is a
  data-availability gap, not a logic gap. **The stream-candidate picker
  UI (Next Steps #1) has this same dependency** — building it before a
  real `SearchProvider` exists would mean building against data that
  can't be real yet, which is why it was deprioritized behind the
  fallback fix this session (see Session 28 above for the full
  reasoning).
- **`ResolvePlaybackUseCase`'s Smart Defaults selection is "first
  candidate with a hash," not a real ranking algorithm** (Session 28) —
  no quality/profile-preference weighting yet. Deliberately minimal
  given there's no real data to rank against yet; revisit once a real
  `SearchProvider` exists and/or the picker UI is built, since the
  picker will want the same underlying candidate-fetch logic with real
  ranking behind it.
- **SearchScreen and Details' play actions always use Smart Defaults**
  (`preferredSource = null`, no stream-candidate picker) — unchanged,
  also true of Home's and Player's own request-building. As of Session
  28 this no longer means "always fails" — it now means "falls through
  to the search-and-select fallback," which itself currently fails only
  because of the `StubSearchProvider` limitation above, not because the
  fallback logic is missing.
- **Home and Details' play actions always start from position 0**
  (`resumeMs = null`) unless the request came from an actual
  `WatchedItem` (Continue Watching) — unchanged from Session 26.
- **Home's Continue Watching failure surface is on Player's error
  screen**, not inline on the Home row (Session 27) — deliberate,
  discussed tradeoff, not a regression.
- **HomeScreen rows do not proactively resolve/display real
  titles/artwork** — unchanged. Resolution only happens once Player is
  reached.
- **`getEpisodeById()` has no dedicated per-episode cache entry or
  provider call** (Session 27) — implemented via the existing full-list
  `getEpisodes()` plus a filter. Documented as an internal detail in
  `MediaRepository.kt`'s doc comment.
- **SettingsScreen preference edits write to Room on every single
  toggle/dropdown/keystroke** — unchanged, low priority.
- **SettingsScreen blank-name and delete-active-profile validation
  errors both surface as a generic error message** — `AppError` has no
  `ValidationError` case yet. See Open TODOs.

## Carried-Forward Lessons

- **An infra-looking CI annotation set (e.g. Gradle cache-service
  warnings) does not rule out a real compile error underneath it** —
  established Session 27, reconfirmed as a verification habit in Session
  28 (checked the job's top-level status explicitly both times rather
  than inferring from the annotations list alone).
- **Before writing a fix for apparently-lost code, check whether it's
  actually lost** (git history, or a duplicate/earlier occurrence in the
  same file) rather than reconstructing it from memory.
- **A `str_replace` or full-file insertion needs the surrounding class
  structure re-verified, not just the immediate anchor text** — matching
  a signature line is not sufficient to guarantee the insertion lands
  inside the intended scope.
- **A brace-balance grep (open `{` count vs close `}` count) is a cheap,
  fast sanity check worth running on any file after a structural edit**,
  before pushing and relying on CI alone to catch it. Applied routinely
  in Session 28 even for small/low-risk edits.
- **Removing a resolve-before-navigate pattern from a ViewModel usually
  also means removing UI-layer state for it** — don't leave a spinner/
  error path rendering dead state after the producing logic is gone.
- **Simplifying a caller's responsibility often removes a dependency
  entirely**, not just a code path — worth checking imports/constructor
  params for now-unused dependencies after this kind of simplification.
- **A one-shot ViewModel → UI event needs a `Channel`, not a second
  `StateFlow`** — `Channel<T>(Channel.BUFFERED)` + `receiveAsFlow()`,
  collected via `LaunchedEffect` + `collectLatest` in the composable.
- **Nav args need explicit `NavType` registration** — `composable(route,
  arguments = listOf(navArgument("x") { type = NavType.StringType }))`;
  `SavedStateHandle["x"]` in the ViewModel picks it up automatically via
  `hilt-navigation-compose`.
- **Nav Compose has no nullable-String/Long NavType that round-trips
  cleanly through the simple `navArgument {}` builder** — sentinel values
  (`"none"` for String, `-1L` for Long) are the workaround.
- **Jetpack Navigation Compose cannot pass domain objects as nav args** —
  primitives only.
- **When a shared-IP GitHub Actions API rate limit blocks CI
  verification, ask for the direct run/job URL and `web_fetch` it
  instead** — confirmed again this session, twice.
- **Grep `build.gradle.kts` / `libs.versions.toml` before importing
  anything from a library not yet used elsewhere in the codebase.**
- **Don't declare a session or a file "done" without an actual CI
  result.**
- **`@Composable` functions are only callable from other `@Composable`
  functions** — not from `LaunchedEffect`, coroutine scopes, or other
  suspend contexts.
- **Flow collection:** always `flow.onEach{}.launchIn(viewModelScope)`,
  never `viewModelScope.launch { flow.collect() }`.
- **`MutableStateFlow.update{}` does not resolve in this build
  environment** — use direct `_flow.value = _flow.value.copy(...)`
  assignment instead.
- **`PlaybackState` naming collision:** `CoordinatorState` (sealed
  interface, `PlaybackCoordinator.kt`) vs `PlayerLifecycleState` (enum,
  `SessionState.kt`) — resolved via import aliases.
- **ExoPlayer instance belongs in the Compose screen, not the
  ViewModel** (`PlayerScreen.kt`'s `DisposableEffect(Unit).onDispose`
  pattern).
- **When a paste truncates at a consistent point across retries, that's
  a signal to chunk the paste, not retry it unchanged.**
- **currentsprint.md on GitHub is the authoritative completion record**
  — project file copies / prior-session memory summaries are a
  convenience cache only and can be stale.
- **Before proposing a design for a UI feature, trace whether the data
  it would display can actually exist yet** (Session 28) — the
  stream-candidate picker looked ready to build based on the Next Steps
  list alone, but tracing `SearchProvider`'s actual DI binding revealed
  it's a stub that always fails. Worth checking real data availability,
  not just architectural readiness, before scoping a UI-heavy task.
- **A `RepositoryResult<T>`-returning method with an existing
  `Failure` branch can often propagate a nested call's own `Failure`
  directly** (`ResolvePlaybackUseCase.resolveSmartDefault()` returns
  `searchResult` directly in its `is RepositoryResult.Failure` branch)
  rather than re-wrapping the error — kept simple since `search()`'s
  `AppError` values are already meaningful to the caller.
## Next Steps, In Order

1. **Stream-candidate picker UI.** Not started. Blocked in practice on a
   real `SearchProvider` implementation — see Known Limitations above.
   Building the picker now would mean UI against data that can't be
   real, which is why it was deprioritized this session in favor of the
   Smart Defaults fallback fix. The fallback fix's `resolveSmartDefault()`
   candidate-fetch logic in `ResolvePlaybackUseCase` (Session 28) is the
   natural extraction point once this is picked up — the picker needs
   the same "get candidates for this Media" step, just surfaced to the
   user instead of auto-applied.
2. **A real `SearchProvider` implementation.** Not formally scoped yet,
   but surfaced as a hard dependency of Next Step #1 this session (see
   Known Limitations). Worth discussing with Dia as its own prioritized
   item rather than assuming it happens implicitly as a side effect of
   the picker work — likely a larger task than either the picker or the
   fallback fix (real scraper/indexer integration, new DTOs, new error
   mapping), per `Provider_Architecture.md`.
3. **Continue Watching → Details routing with resumePositionMs.** Not
   started. Would mean deciding whether Continue Watching should route
   through Details after all now that Player resolves from nav args
   cleanly either way.
4. **`AppError.ValidationError` case.** Would let profile-related
   validation errors, and `getEpisodeById()`'s not-found case, surface
   distinct user-facing messages instead of reusing
   `LocalStorageError`/`Unknown`. Low urgency, growing slightly with each
   session that reuses `Unknown` as a catch-all.
5. **Completion-percentage / markAsCompleted wiring.** Not started.
6. **SettingsScreen preference-write debounce** — only if it turns out
   to feel laggy on-device; not confirmed, just flagged.
7. **HomeScreen proactive title/artwork display** — only if a priority.

## Open TODOs (carried forward, unchanged unless noted)

- App icon: placeholder system drawable in AndroidManifest.xml
- SearchRepository.updateSearchSession uses `Map<String, String>` for
  filters; revisit if SearchFilters gets promoted to a domain model
- AppError has no ValidationError case; CreateProfileUseCase and
  UpdateProfileUseCase use LocalStorageError(IllegalArgumentException)
  for blank name validation. See Next Steps #4 — also relevant to
  getEpisodeById()'s AppError.Unknown not-found case.
- StartPlaybackUseCase uses a fully qualified AppError reference inline;
  tidy to a top-level import if preferred
- HomeViewModel.removeItem() has no failure feedback path — deliberately
  deferred
- SearchScreen.kt uses fully-qualified
  `androidx.compose.foundation.text.KeyboardActions`/`KeyboardOptions`
  inline rather than top-level imports. Revisit once confirmed safe.
- `DropdownField<T>` in SettingsScreen.kt is a `TextButton` +
  `DropdownMenu`, not Material 3's `ExposedDropdownMenuBox`. Functional;
  revisit only if visual polish becomes a priority.
- No language list/picker exists — free-text BCP-47 code entry fields
  only.
- `PlaybackRepositoryImpl`'s `markAsCompleted()` block has inconsistent
  indentation — cosmetic only.
- `ExoPlayer.duration` can report `C.TIME_UNSET` before a stream has
  buffered enough to know its length; stored as-is currently.
- `MediaCache`'s 7-day TTL is a starting assumption, not derived from a
  specific requirement.
- `R.string.home_resolving_media` and `R.string.home_resume_error` are
  unused (the UI states that read them were removed in Session 27 when
  Home stopped resolving Media before navigating). Left in place, same
  handling as `search_tv_show_unsupported`.
- `getEpisodeById()`'s not-found path reuses `AppError.Unknown` — not
  semantically ideal (see Next Steps #4), but consistent with `Unknown`
  already being this codebase's catch-all.
- `SearchUiState.activeProfileId` is dead state. Left in place per Dia's
  explicit call (Session 26); revisit near project end if still unused.
- `search_tv_show_unsupported` string resource is unused. Left in place
  with an inline XML comment flagging it.
- `Media.id` for a `SearchResult` tapped in Search vs. the `Media`
  re-fetched by `DetailsViewModel`/`PlayerViewModel` via
  `GetMediaByIdUseCase` are assumed to always round-trip cleanly as a
  String `mediaId`. Not covered by an automated test (none exist in this
  repo yet).
- **RESOLVED (Session 28):** `SearchViewModel.kt`, `SearchScreen.kt`,
  `MediaCache.kt`'s stale `PendingPlaybackHolder` doc-comment references
  — cleared, reworded to describe the current flow.
- **NEW (Session 28):** No real `SearchProvider` exists — see Known
  Limitations and Next Steps #2. This is the most significant open item
  from this session; it blocks both the picker UI and any real testing
  of the new Smart Defaults fallback.

At the end of the next session, update currentsprint.md (full file, in
a code block, chunked into sequential pastes if it's likely to exceed
~450-500 lines — Session 27's handoff needed 3 parts) and verify it
directly against
raw.githubusercontent.com/diaviloai/Onedebrid/main/currentsprint.md
before treating the session as closed — and do not treat any session as
closed without an actual green CI result for whatever was last pushed,
verified via the direct run/job URL if the Actions API is rate-limited.