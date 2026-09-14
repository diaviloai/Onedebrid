# OneDebrid — Current Sprint

## Status

Implementation in progress. Architectural design phase complete.

This file is fully rewritten each session — it reflects actual current
code state, verified by pulling the repo and reading files directly, not
appended to informally.

**Naming note:** the uploaded architecture docs in this project (Project
Design.md, database design.md, Internal API Specification.md, provider
architecture.md, Technical standards.md, UI UX Design.md) refer to the
app as "OneForAll" throughout. This is the app's old name — it was
renamed to OneDebrid when Dia started working with Claude on the project.
Same app, same docs, just an old header. Not a discrepancy to re-flag in
future sessions.

Build verification: project compiles cleanly as of Session 31's close,
confirmed via GitHub Actions on the latest pushed commit — job "build"
succeeded in 4m 53s, per the direct run/job URL
(`github.com/diaviloai/Onedebrid/actions/runs/34780602105/job/103786798510`).
All files touched this session were independently re-pulled from the
tarball after each push and checked against intended content before this
file was updated.

**Sessions 1–25 summary** (condensed from prior full write-ups, which
remain in git history on this file if the detail is ever needed): built
layer by layer — domain models → error types → provider interfaces →
repository interfaces → Room entities/DAOs → Hilt wiring → coroutine
infrastructure → use cases → coordinators → ViewModels → Compose screens
(SearchScreen, HomeScreen, SettingsScreen). Session 25 added Continue
Watching tap-to-resume (`PendingPlaybackHolder` + direct-to-Player nav)
and cache-first `MediaRepository` reads via `MediaCache`.

**Sessions 26–28 summary** (condensed further this session; full detail
in git history on this file): Session 26 built the Details/Episode-picker
screen, reached from Search, with `mediaId` as the first nav arg in the
graph. Session 27 retired `PendingPlaybackHolder` (an in-memory
singleton) in favor of real Navigation Compose arguments to Player
(`mediaId` required, `episodeId`/`resumeMs` optional via sentinel
values); `PlayerViewModel` now resolves its own `Media`/`Episode`/active
profile on init. Session 28 found `ResolvePlaybackUseCase` had no actual
Smart Defaults fallback despite every caller relying on it, and that
`SearchProvider` had exactly one implementation (`StubSearchProvider`,
always `ServiceUnavailable`) — fixed the fallback logic itself that
session, deferred the stream-candidate picker UI pending real search
data.

**Session 29 summary** (condensed; full detail in git history): built
`TorrentioSearchProvider`, OneDebrid's first real `SearchProvider`,
targeting `torrentio.strem.fun`. Key finding: Torrentio requires an
already-known IMDb ID — no free-text search exists anywhere in it.
Added `SearchProvider.searchByMedia(media, filters)` alongside the
existing free-text `search()`, which stayed honestly non-functional.
`ResolvePlaybackUseCase.resolveSmartDefault()` switched to the new
ID-based path, also fixing a Session 28 omission (`request.episode`
was never passed through). First real Retrofit/OkHttp wiring landed in
`NetworkModule.kt`. Key finding carried into Session 30:
`searchByMedia()` requires `Media.imdbId`, and nothing in the app
produced a real one yet.

**Session 30 summary** (condensed this session; full detail in git
history on this file): built `TmdbMetadataProvider`, OneDebrid's first
real `MetadataProvider`, backed by TMDB API v3 (Bearer JWT / v4 Read
Access Token auth) — the actual remaining blocker flagged at the end of
Session 29. Two load-bearing decisions made and confirmed with Dia: (1)
`Media.id` is permanently the TMDB id, stringified — this also surfaced
and fixed a real, previously undetected bug where
`MediaRepositoryImpl.getMediaDetails()`/`getEpisodes()` were hardcoded
to `ExternalIdType.IMDB`; (2) free-text Search results do NOT eagerly
resolve Torrentio streams — resolve-on-tap instead, reusing Session
27/29's existing Details → Player flow with zero new code needed.
New files: `TmdbDto.kt`, `TmdbApi.kt`, `TmdbMetadataProvider.kt` (all
under `provider/metadata/tmdb/`). Five real mistakes were made and
caught this session, including a `.github/workflows/build.yml` step-
ordering bug (a secret-writing step must run before ANY Gradle-invoking
step, not just the final build step) and a YAML find/replace producing
a parse error (see Carried-Forward Lessons — both lessons remain
active). Ended with the stream-candidate picker UI newly unblocked and
flagged as Session 31's top priority.

## Package Structure

com.onedebrid.app/
    ├── MainActivity.kt
    ├── OneDebridApplication.kt
    ├── coordinator/
    │   ├── PlaybackCoordinator.kt
    │   ├── SearchCoordinator.kt
    │   └── SessionCoordinator.kt
    ├── data/
    │   ├── local/ (AppDatabase.kt, MediaCache.kt, TypeConverters.kt,
    │   │   dao/, entity/ — unchanged this session)
    │   └── repository/
    │       ├── MediaRepository.kt (unchanged this session —
    │       │   searchStreamsByMedia() already existed from Session 29,
    │       │   now also called via the new GetStreamCandidatesUseCase
    │       │   as well as ResolvePlaybackUseCase directly — see Session
    │       │   31 notes below for why both call sites were kept)
    │       ├── MediaRepositoryImpl.kt (unchanged this session)
    │       ├── PlaybackRepository.kt / PlaybackRepositoryImpl.kt
    │       ├── ProfileRepository.kt / ProfileRepositoryImpl.kt
    │       ├── RepositoryResult.kt
    │       ├── SearchRepository.kt / SearchRepositoryImpl.kt
    │       ├── SessionRepository.kt / SessionRepositoryImpl.kt
    │       └── (Subtitle/Download repositories not yet built)
    ├── di/ (unchanged this session)
    │   ├── CoroutineDispatchers.kt
    │   ├── NetworkModule.kt
    │   └── DatabaseModule, RepositoryModule, ProviderModule
    ├── domain/
    │   ├── error/
    │   │   └── AppError.kt (unchanged this session — see Open TODOs re:
    │   │       ValidationError)
    │   └── model/
    │       ├── Media.kt (unchanged)
    │       ├── Episode.kt
    │       ├── PlaybackRequest.kt (unchanged this session —
    │       │   preferredSource already existed from an earlier session,
    │       │   now actually populated for the first time, see Session
    │       │   31 notes)
    │       ├── SearchResult.kt (Session 31: `StreamCandidate` made
    │       │   `@Serializable`, so a manually-picked candidate can
    │       │   travel through a Navigation Compose nav arg as JSON)
    │       ├── SessionState.kt
    │       ├── StreamSource.kt (Session 31: `VideoQuality` enum made
    │       │   `@Serializable`, required for `StreamCandidate` above to
    │       │   be `@Serializable` — `StreamSource` itself left alone,
    │       │   it never crosses a nav-arg boundary)
    │       ├── SubtitleTrack.kt
    │       ├── UserProfile.kt
    │       └── WatchedItem.kt
    ├── provider/ (unchanged this session)
    │   ├── search/
    │   │   ├── SearchProvider.kt
    │   │   ├── StubSearchProvider.kt
    │   │   └── torrentio/
    │   │       ├── TorrentioApi.kt
    │   │       ├── TorrentioDto.kt
    │   │       └── TorrentioSearchProvider.kt
    │   └── metadata/
    │       ├── MetadataProvider.kt
    │       ├── StubMetadataProvider.kt
    │       ├── ExternalIdType.kt
    │       └── tmdb/
    │           ├── TmdbApi.kt
    │           ├── TmdbDto.kt
    │           └── TmdbMetadataProvider.kt
    │   (DebridProvider, others — unchanged; still no real DebridProvider
    │    exists)
    ├── ui/
    │   ├── details/ (Session 31 — both files changed, see below)
    │   │   ├── DetailsScreen.kt (new "Choose a stream" affordance next
    │   │   │   to Play and next to each episode row; new PickerSheet
    │   │   │   ModalBottomSheet composable + CandidateRow +
    │   │   │   formatVideoQuality()/formatFileSize() helpers)
    │   │   └── DetailsViewModel.kt (new PickerUiState sealed interface;
    │   │       new `picker` field on DetailsUiState; new
    │   │       onChooseStream()/retryChooseStream()/onDismissPicker()/
    │   │       onCandidateSelected() methods; onPlayMovie()/
    │   │       onPlayEpisode() now explicitly pass
    │   │       preferredSource = null)
    │   ├── home/ (HomeScreen.kt: Session 31 — onNavigateToPlayer gained
    │   │   a 4th parameter, preferredSource, always null from this
    │   │   screen's own flow; HomeViewModel.kt unchanged)
    │   ├── navigation/ (Session 31 — both files changed)
    │   │   ├── NavGraph.kt (Route.Player's route pattern and build()
    │   │   │   gained a preferredSource query param — JSON-encoded via
    │   │   │   kotlinx.serialization, then Uri.encode()'d; "" is the
    │   │   │   "no candidate" sentinel; both onNavigateToPlayer call
    │   │   │   sites updated to the new 4-param signature)
    │   │   └── PlayerNavArgs.kt (gained `preferredSource:
    │   │       StreamCandidate? = null`)
    │   ├── player/ (PlayerScreen.kt unchanged; PlayerViewModel.kt —
    │   │   Session 31: decodes the new preferredSource nav arg at
    │   │   construction via a new decodePreferredSource() top-level
    │   │   function, uses it in both resolveAndPlay() and retryPlay())
    │   ├── search/ (SearchScreen.kt, SearchViewModel.kt — unchanged)
    │   └── settings/ (SettingsScreen.kt, ProfileViewModel.kt —
    │       unchanged)
    └── usecase/
        ├── CreateProfileUseCase.kt
        ├── DeleteProfileUseCase.kt
        ├── EndPlaybackSessionUseCase.kt
        ├── GetActiveProfileUseCase.kt
        ├── GetContinueWatchingUseCase.kt
        ├── GetEpisodeByIdUseCase.kt
        ├── GetEpisodesUseCase.kt
        ├── GetMediaByIdUseCase.kt
        ├── GetStreamCandidatesUseCase.kt (NEW, Session 31 — thin
        │   wrapper over MediaRepository.searchStreamsByMedia(), added
        │   so DetailsViewModel can fetch candidates without violating
        │   Internal_API_Specification.md's "ViewModels never access
        │   repositories directly" rule. Deliberately NOT used by
        │   ResolvePlaybackUseCase.resolveSmartDefault(), which keeps
        │   its own pre-existing direct repository call — see Session
        │   31 notes below for why that wasn't retrofitted)
        ├── RemoveFromContinueWatchingUseCase.kt
        ├── ResolvePlaybackUseCase.kt (unchanged this session)
        ├── SavePlaybackPositionUseCase.kt
        ├── SearchMediaUseCase.kt
        ├── SwitchProfileUseCase.kt
        ├── UpdateProfileUseCase.kt
        └── (others per earlier sessions)

(This tree reflects what's been directly read/touched across sessions,
not a guaranteed exhaustive listing — see the repo itself for ground
truth on files not mentioned in recent session notes.)## Build Configuration

Unchanged this session — see git history on this file for the full
Session 30 write-up (TMDB `local.properties`/CI-secret setup,
`BuildConfig.TMDB_READ_ACCESS_TOKEN`).

## Known, Deliberate Limitations (documented in code, not silently
worked around)

- **`SearchProvider.search()` (Torrentio's free-text path) is still
  permanently non-functional** — unchanged from Session 29/30.
- **`Media.imdbId` is null for every `Media` returned by
  `searchMedia()`** (TMDB search results) — unchanged from Session 30,
  a real TMDB API constraint, not a bug.
- **Movie vs TV asymmetry for `imdb_id`** — unchanged from Session 30.
- **`fetchMediaDetails()` tries `/movie/{id}` first, falls back to
  `/tv/{id}` on 404** — unchanged, one wasted HTTP call per TV lookup.
- **`fetchEpisodes(season = null)` is an N+1 call pattern** —
  unchanged, no single-call TMDB alternative exists.
- **`resolveExternalId()` is not implemented** — unchanged.
- **Free-text Search does NOT eagerly resolve streams** — unchanged
  from Session 30, a deliberate design decision.
- **`ResolvePlaybackUseCase`'s Smart Defaults selection is still "first
  candidate with a hash,"** not a real ranking algorithm — unchanged.
  The stream-candidate picker (built this session) is a manual
  *override* of this, not a replacement for it — see Session 31 notes.
- **Torrentio's own reliability is a known, accepted tradeoff** —
  unchanged.
- **No real `DebridProvider` exists yet** — unchanged.
- **NEW (Session 31): the picker sheet shows every candidate
  `GetStreamCandidatesUseCase` returns, unranked** — no sorting by
  quality/seeders/size is applied before display. Candidates are shown
  in whatever order `MediaRepository.searchStreamsByMedia()` (ultimately
  Torrentio) returns them. Not discussed as in-scope for this session;
  worth a look if the unsorted list proves confusing in practice.
- **NEW (Session 31): `VideoQuality`/file-size labels in the picker
  sheet are NOT localized** — `formatVideoQuality()`/`formatFileSize()`
  in `DetailsScreen.kt` return plain English strings directly rather
  than going through `stringResource()`/`strings.xml`, unlike the rest
  of that screen's copy. A deliberate scope choice, not an oversight —
  see that function's doc comment in the file itself.
- **NEW (Session 31): `GetStreamCandidatesUseCase` exists as a second
  call path to `MediaRepository.searchStreamsByMedia()`, alongside
  `ResolvePlaybackUseCase.resolveSmartDefault()`'s own pre-existing
  direct call to the same repository method** — deliberately NOT
  unified into a single call site this session, to keep the diff
  additive and avoid touching Smart Defaults' already-working,
  already-tested resolve path for a purely cosmetic consistency gain.
  A future cleanup pass could route `resolveSmartDefault()` through the
  new Use Case too, but this was not discussed as in-scope.
- All Session 28/29/30 limitations not superseded above remain accurate
  — see git history on this file for the full lists.

## Carried-Forward Lessons

- **A workflow step that writes a required secret to a file must run
  before ANY step that invokes Gradle, not just before the final build
  step** (Session 30). `gradle wrapper --gradle-version=X` evaluates the
  project's build scripts as part of configuring the wrapper task — not
  a lightweight, script-free operation.
- **A misleading old CI log can look identical to a new failure with a
  different real cause** (Session 30) — the annotation summary view
  alone was not enough to tell two different root causes apart; only
  the raw step log did.
- **A YAML workflow file is whitespace/structure-sensitive in ways easy
  to miss** (Session 30) — find/replace-by-description produced a
  parse error; full-file replacement was used to recover. YAML files
  always get full-file replacement now, never targeted find/replace.
- **NEW (Session 31): a chunked file paste can silently glue two
  sections together with no newline between them at the join point,
  even when both halves are individually correct.** `DetailsScreen.kt`
  (537 lines, split into two sequential pastes per the ~450–500 line
  Spck limit) landed with `}@Composable` on one line at the Part 1/
  Part 2 boundary — the closing brace of `EpisodeList()` and the
  `@Composable` annotation on the next function, glued together with no
  blank line. This turned out to be syntactically valid Kotlin (verified
  by reasoning through the language's whitespace-insignificance rules
  after CI came back green, not assumed) and did not cause a build
  failure, but it was corrected anyway for readability once found.
  Standing lesson: when instructing a chunked paste, be explicit that a
  literal blank line must survive at the join boundary, and check the
  join point specifically (not just each half) when re-pulling and
  verifying afterward.
- **NEW (Session 31): don't declare a CI run "stale" or "must not have
  tested this code" without actually checking the run's timestamp
  against the current time.** Claude incorrectly flagged a build as
  likely testing old code based on the *content* of a suspected defect
  matching what "should" have failed, without first checking whether
  the job's timestamp was consistent with the just-completed push. Dia
  correctly pushed back (the job was in fact only ~11 minutes old).
  Standing lesson: verify timestamps/recency directly (`user_time_v0`
  or equivalent) before asserting a build result doesn't reflect the
  current code — an inferred contradiction between "this should have
  failed" and "CI says it passed" should prompt re-checking the
  inference (in this case: is the suspected defect actually a defect?),
  not defaulting to "the test must not have run."
- All Session 27/28/29/30 lessons not superseded above remain accurate
  — see git history on this file for the full list (brace-balance
  checks, Composable-context rule, Flow collection pattern,
  `MutableStateFlow.update{}` gotcha, nav-arg sentinel-value pattern,
  infra-noise-isn't-sufficient CI lesson, CI-error-category-can-mislead
  lesson, "check data availability before scoping UI work," etc.)

## Next Steps, In Order

1. **Continue Watching → Details routing with resumePositionMs.**
   Moves up to the top of the list now that the stream-candidate picker
   (previously Next Step #1) is done. Independent of all provider work.
2. **`AppError.ValidationError` case.** Unchanged, low urgency.
3. **Completion-percentage / markAsCompleted wiring.** Not started.
4. **SettingsScreen preference-write debounce** — only if needed.
5. **HomeScreen proactive title/artwork display** — only if a priority.
6. **`fetchMediaDetails()`'s movie/TV-ambiguity extra HTTP call** — worth
   a look if it proves costly in practice.
7. **`resolveExternalId()` real implementation (TMDB `/find` endpoint)**
   — only if a real caller emerges; speculative otherwise.
8. **Picker candidate sorting** (NEW, Session 31) — the picker currently
   shows candidates unranked (see Known Limitations). Worth confirming
   with Dia whether this matters in practice before investing time.
9. **Unify `GetStreamCandidatesUseCase` and `ResolvePlaybackUseCase`'s
   direct repository call** (NEW, Session 31) — cosmetic consistency
   cleanup only, not urgent. See Known Limitations for why it wasn't
   done this session.

## Open TODOs (carried forward, unchanged unless noted)

- App icon: placeholder system drawable in AndroidManifest.xml
- SearchRepository.updateSearchSession uses `Map<String, String>` for
  filters; revisit if SearchFilters gets promoted to a domain model
- AppError has no ValidationError case; also relevant to
  `searchByMedia()`'s missing-`imdbId` NotFound case
- StartPlaybackUseCase uses a fully qualified AppError reference inline
- HomeViewModel.removeItem() has no failure feedback path
- SearchScreen.kt uses fully-qualified Compose imports inline
- `DropdownField<T>` in SettingsScreen.kt is a TextButton + DropdownMenu,
  not Material 3's ExposedDropdownMenuBox
- No language list/picker exists — free-text BCP-47 code entry only
- `PlaybackRepositoryImpl`'s markAsCompleted() indentation is cosmetic
- `ExoPlayer.duration` can report C.TIME_UNSET before buffering
- `MediaCache`'s 7-day TTL is a starting assumption
- `R.string.home_resolving_media`/`home_resume_error` are unused
- `getEpisodeById()`'s not-found path reuses AppError.Unknown
- `SearchUiState.activeProfileId` is dead state (Session 26 call)
- `search_tv_show_unsupported` string resource is unused
- `Media.id` round-trip between Search/Details/Player is unverified, no
  automated tests exist in this repo (meaning itself was decided in
  Session 30 — TMDB id, stringified; this TODO is about test coverage)
- `TorrentioSearchProvider`'s title-text quality/size/seeder parsing is a
  simple pattern match, not exhaustive (Session 29, unchanged)
- No retry/backoff logic exists for Torrentio's documented periodic
  unreliability (Session 29, unchanged)
- `fetchMediaDetails()` costs one wasted HTTP call for every TV lookup
  (Session 30, unchanged) — see Next Steps #6.
- `fetchEpisodes(season = null)` is an N+1 call pattern (Session 30,
  unchanged) — no single-call TMDB alternative exists.
- `resolveExternalId()` returns `ServiceUnavailable` unconditionally
  (Session 30, unchanged) — see Next Steps #7.
- TMDB search/multi results have `genreIds` but `toMedia()` for search
  results maps `genres = emptyList()` (Session 30, unchanged) — not
  in-scope; would need either a detail call per result (rejected, same
  reasoning as eager stream resolution) or a local genre id→name map.
- **NEW (Session 31):** picker candidates are shown unranked (no
  quality/seeders/size sort) — see Known Limitations and Next Steps #8.
- **NEW (Session 31):** `formatVideoQuality()`/`formatFileSize()` in
  `DetailsScreen.kt` are not localized (plain English strings, not
  `stringResource()`) — see Known Limitations.
- **NEW (Session 31):** `GetStreamCandidatesUseCase` and
  `ResolvePlaybackUseCase.resolveSmartDefault()` both call
  `MediaRepository.searchStreamsByMedia()` independently rather than
  sharing one call path — see Known Limitations and Next Steps #9.## Session 31 — What Was Done

**Scope confirmed with Dia up front, per standing practice, before any
design was proposed:** the stream-candidate picker UI (Session 30's
Next Step #1, newly unblocked) was chosen. Two design questions flagged
as undecided at the end of Session 30 were resolved this session with
Dia directly, before any code was written:

1. **Picker behavior on tap.** Two options were presented: (A) always
   show the picker, no auto-play; or (B) keep Smart Default auto-play as
   the primary one-tap action, picker as a manual override only. Dia
   chose **B** — consistent with Project_Design.md's "Zero-Click to
   Content" and Smart Defaults principles, and the smaller, safer
   change. Play (`onPlayMovie`/`onPlayEpisode`) is completely unchanged
   in this session's diff; the picker is a new, additive, secondary
   affordance next to it.
2. **How a manually-picked `StreamCandidate` reaches `PlayerViewModel`.**
   Two options were presented: (a) a small in-memory holder scoped
   similarly to the `PendingPlaybackHolder` Session 27 deliberately
   deleted (rejected — Dia and Claude agreed reintroducing that pattern,
   even in a smaller form, would contradict a previously-made and
   documented decision without a strong enough reason); or (b)
   JSON-serializing the candidate into a new optional nav-arg query
   param on `Route.Player`, following the exact same sentinel-value
   pattern already used for `episodeId`/`resumeMs`. Dia chose **(b)** —
   survives process death, no new singleton, consistent with existing
   patterns.

**Files read before any code was written** (per standing "read first"
practice): `DetailsScreen.kt`, `DetailsViewModel.kt`, `PlayerViewModel.kt`,
`PlayerNavArgs.kt`, `NavGraph.kt`, `ResolvePlaybackUseCase.kt`,
`SearchResult.kt`, `PlaybackRequest.kt`, `StreamSource.kt`,
`MediaRepository.kt` (interface), `HomeScreen.kt`,
`GetEpisodesUseCase.kt` (as a Use Case style template). This surfaced
two things that shaped the design before writing began: (1)
`PlaybackRequest.preferredSource` already existed as a field but had
never actually been populated by any caller — `ResolvePlaybackUseCase`
already honored it correctly, so no changes were needed there at all;
(2) no existing Use Case wrapped `MediaRepository.searchStreamsByMedia()`
for a standalone "get candidates" purpose, which meant a new
`GetStreamCandidatesUseCase` was required to keep `DetailsViewModel`
compliant with `Internal_API_Specification.md`'s "ViewModels never
access repositories directly" rule — flagged to Dia as an unplanned but
necessary new file before it was written, not silently added.

**The data flow, end to end:** `DetailsScreen`'s new "Choose a stream"
button/row action → `DetailsViewModel.onChooseStream(episode)` → new
`GetStreamCandidatesUseCase` → `MediaRepository.searchStreamsByMedia()`
(the same method `ResolvePlaybackUseCase.resolveSmartDefault()` already
uses) → results shown in a new `PickerSheet` `ModalBottomSheet` →
tapping a candidate calls `DetailsViewModel.onCandidateSelected()` →
emits the existing `navigateToPlayer` event (same `Channel` Play
already uses) but with `preferredSource` set on `PlayerNavArgs` →
`NavGraph.kt`'s `Route.Player.build()` JSON-encodes the candidate
(kotlinx.serialization) and `Uri.encode()`s it into a new
`preferredSource` query param (empty string `""` is the "no candidate"
sentinel, distinct from `episodeId`'s existing `"none"` sentinel) →
`PlayerViewModel` decodes it back (`Uri.decode()` then
`Json.decodeFromString()`) at construction via a new
`decodePreferredSource()` function, defensively returning `null` on an
empty string or a `SerializationException` → the decoded
`StreamCandidate?` is passed into `PlaybackRequest.preferredSource` in
both `resolveAndPlay()` and (new this session) `retryPlay()`, so a
retry after a failed manual pick retries that same candidate rather
than silently falling back to Smart Defaults.

**Files created:**
- **`usecase/GetStreamCandidatesUseCase.kt`** — thin wrapper over
  `MediaRepository.searchStreamsByMedia()`. See Known Limitations for
  why `ResolvePlaybackUseCase` was deliberately NOT retrofitted to use
  it too.

**Files modified:**
- **`domain/model/StreamSource.kt`** — `VideoQuality` enum made
  `@Serializable`.
- **`domain/model/SearchResult.kt`** — `StreamCandidate` made
  `@Serializable`.
- **`ui/navigation/PlayerNavArgs.kt`** — added
  `preferredSource: StreamCandidate? = null`.
- **`ui/navigation/NavGraph.kt`** — `Route.Player`'s route pattern and
  `build()` gained the JSON-encoded `preferredSource` query param (see
  data-flow description above); both `onNavigateToPlayer` call sites
  (Home, Details) updated to the new 4-parameter signature.
- **`ui/home/HomeScreen.kt`** — `onNavigateToPlayer` signature updated
  to match; always passes through `navArgs.preferredSource` (always
  `null` from Continue Watching's own flow, but passed through rather
  than hardcoded so this call site stays correct if that ever changes).
- **`ui/player/PlayerViewModel.kt`** — new private
  `preferredSource: StreamCandidate?` field decoded once at
  construction; new top-level `decodePreferredSource()` function; both
  `resolveAndPlay()` and `retryPlay()` now pass the decoded value
  instead of hardcoding `null`.
- **`ui/details/DetailsViewModel.kt`** — new `PickerUiState` sealed
  interface (`Closed`/`Loading`/`Loaded`/`Error`, each carrying the
  `Episode?` the picker was opened for, except `Closed`); new `picker`
  field on `DetailsUiState`; new `onChooseStream()`/
  `retryChooseStream()`/`onDismissPicker()`/`onCandidateSelected()`
  methods; `onPlayMovie()`/`onPlayEpisode()` now explicitly pass
  `preferredSource = null` (previously implicit via default arg) to
  make the "Play always means Smart Default" intent explicit in code,
  not just in the doc comment.
- **`ui/details/DetailsScreen.kt`** — `onNavigateToPlayer` signature
  updated to 4 params; new "Choose a stream" `TextButton` next to Play
  (movie) and next to each episode row (TV); new `PickerSheet`
  `ModalBottomSheet` composable (loading/error/empty/loaded states);
  new `CandidateRow` composable; new `formatVideoQuality()`/
  `formatFileSize()` private helper functions (deliberately not
  localized, see Known Limitations).
- **`res/values/strings.xml`** — added `details_choose_stream`,
  `details_picker_title`, `details_picker_empty`,
  `details_picker_seeders`, `details_picker_error_generic`.

**Two real mistakes this session, both caught before being treated as
done:**

1. **A `stringResource()` call was initially placed inside a
   `buildString { }` lambda** in `CandidateRow` (`DetailsScreen.kt`) —
   `buildString`'s lambda is not `@Composable`, so `stringResource()`
   cannot be called there directly. Caught by Claude re-reading the
   file immediately after writing it, before presenting it to Dia —
   fixed by resolving the string (`stringResource(R.string.
   details_picker_seeders, it)`) in the enclosing `@Composable`
   function's scope first, then only appending the already-resolved
   `String` inside `buildString`.
2. **A chunked-paste join artifact** — see the new Carried-Forward
   Lessons entry above for the full description
   (`}@Composable` glued together with no blank line at the
   `DetailsScreen.kt` Part 1/Part 2 boundary). Turned out to be
   syntactically valid Kotlin and did not break the build, but was
   still corrected for readability once found. A related non-mistake
   worth noting: Claude initially, incorrectly, flagged the first green
   CI run as possibly "stale" (i.e., not actually testing this
   defect) without checking the run's timestamp first — Dia correctly
   pushed back, and the run was confirmed to be current. See the
   second new Carried-Forward Lessons entry above.

**Build verification:** final push verified green via direct job URL:
`github.com/diaviloai/Onedebrid/actions/runs/34780602105/job/103786798510`
— job "build" succeeded in 4m 53s. All annotations on that run (13
warnings) confirmed to be GitHub infrastructure noise (Gradle
cache-service errors, Node.js 20/setup-java v4 deprecation notices) by
checking the job's actual top-level status line, not inferred from the
annotation list alone. All ten touched/created files were re-pulled
fresh after each push and checked against intended content — including
the `DetailsScreen.kt` chunk-join boundary specifically, where the one
real (if harmless) defect of this session was actually found.

At the end of the next session, update currentsprint.md (full file, in
a code block, chunked into sequential pastes if it's likely to exceed
~450-500 lines — and if chunked, explicitly confirm a blank line
survives at the join boundary, per this session's new lesson above) and
verify it directly against
raw.githubusercontent.com/diaviloai/Onedebrid/main/currentsprint.md
before treating the session as closed — and do not treat any session as
closed without an actual green CI result for whatever was last pushed,
verified via the direct run/job URL if the Actions API is rate-limited.