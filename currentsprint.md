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

Build verification: project compiles cleanly as of Session 30's close,
confirmed via GitHub Actions on the latest pushed commit — job "build"
succeeded in 4m 21s, per the direct run/job URL
(`github.com/diaviloai/Onedebrid/actions/runs/33456443813/job/99697300273`).
All files touched this session were independently re-pulled from
`raw.githubusercontent.com`/the tarball after each push and diffed against
intended content before this file was updated.

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

**Session 29 summary** (condensed this session; full detail in git
history on this file): built `TorrentioSearchProvider`, OneDebrid's first
real `SearchProvider`, targeting `torrentio.strem.fun` (free, keyless,
Stremio-protocol torrent-indexer aggregator, confirmed live via web
search). Key finding: Torrentio's only endpoint requires an already-known
IMDb ID — no free-text search exists anywhere in Torrentio, or anywhere
else in the codebase at the time. Added `SearchProvider.searchByMedia(
media, filters)` as a new method alongside the existing free-text
`search()`, rather than replacing it — `search()` stayed honestly
non-functional (`ProviderError.NotFound`) pending a real metadata-search
provider. `ResolvePlaybackUseCase.resolveSmartDefault()` was switched to
call the new ID-based path, which also fixed a real Session 28 omission
(`request.episode` was never passed through, so TV shows could never
resolve via Smart Defaults). First real Retrofit/OkHttp wiring landed in
`NetworkModule.kt`. Two mistakes were made and caught via content
diffing: a `ProviderModule.kt` paste that corrupted the file (fixed with
a full-file overwrite), and a deprecated OkHttp API call caught by CI
(fixed with the correct `toMediaType()` import). Key finding carried into
Session 30: `searchByMedia()` requires `Media.imdbId`, and nothing in the
app produced a real one — no `MetadataProvider` existed beyond the stub.

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
    │       ├── MediaRepository.kt (unchanged this session — interface
    │       │   already had searchStreamsByMedia() from Session 29)
    │       ├── MediaRepositoryImpl.kt (Session 30: TWO changes — (1)
    │       │   getMediaDetails()/getEpisodes() fixed from
    │       │   ExternalIdType.IMDB to ExternalIdType.TMDB, a real
    │       │   pre-existing bug found and fixed this session, see
    │       │   "Session 30 — What Was Done" below; (2) search() now
    │       │   delegates to metadataProvider.searchMedia() instead of
    │       │   searchProvider.search(), mapping each Media to a
    │       │   SearchResult with an empty candidates list)
    │       ├── PlaybackRepository.kt / PlaybackRepositoryImpl.kt
    │       ├── ProfileRepository.kt / ProfileRepositoryImpl.kt
    │       ├── RepositoryResult.kt
    │       ├── SearchRepository.kt / SearchRepositoryImpl.kt
    │       ├── SessionRepository.kt / SessionRepositoryImpl.kt
    │       └── (Subtitle/Download repositories not yet built)
    ├── di/
    │   ├── CoroutineDispatchers.kt
    │   ├── NetworkModule.kt (Session 30: added a second, TMDB-qualified
    │   │   Retrofit instance + TmdbApi, a dedicated TmdbOkHttpClient
    │   │   carrying a new AuthInterceptor (Bearer token from
    │   │   BuildConfig), and extracted the shared HttpLoggingInterceptor
    │   │   into its own @Provides so both OkHttp clients reuse the same
    │   │   instance. Torrentio's existing wiring unchanged/untouched)
    │   └── DatabaseModule, RepositoryModule, ProviderModule
    │       (ProviderModule Session 30: bindMetadataProvider() now binds
    │       TmdbMetadataProvider, replacing StubMetadataProvider)
    ├── domain/
    │   ├── error/
    │   │   └── AppError.kt (unchanged this session — see Open TODOs re:
    │   │       ValidationError)
    │   └── model/
    │       ├── Media.kt (unchanged this session, but see IMPORTANT note
    │       │   below — Media.id's real-world meaning was decided this
    │       │   session, not a code change)
    │       ├── Episode.kt
    │       ├── PlaybackRequest.kt
    │       ├── SearchResult.kt (StreamCandidate defined here)
    │       ├── SessionState.kt
    │       ├── StreamSource.kt (VideoQuality enum)
    │       ├── SubtitleTrack.kt
    │       ├── UserProfile.kt
    │       └── WatchedItem.kt
    ├── provider/
    │   ├── search/ (unchanged this session — Torrentio work is
    │   │   Session 29's, see condensed summary above)
    │   │   ├── SearchProvider.kt
    │   │   ├── StubSearchProvider.kt
    │   │   └── torrentio/
    │   │       ├── TorrentioApi.kt
    │   │       ├── TorrentioDto.kt
    │   │       └── TorrentioSearchProvider.kt
    │   └── metadata/
    │       ├── MetadataProvider.kt (Session 30: added searchMedia(query)
    │       │   to the interface, alongside the three existing ID-based
    │       │   methods — same additive pattern as Session 29's
    │       │   searchByMedia() on SearchProvider. See "Session 30 — What
    │       │   Was Done" below for the full reasoning)
    │       ├── StubMetadataProvider.kt (Session 30: added a matching
    │       │   searchMedia() override, also fails; no longer Hilt-bound
    │       │   as of this session, kept as a reference/fallback impl)
    │       ├── ExternalIdType.kt (enum, defined inside
    │       │   MetadataProvider.kt — IMDB/TMDB/TVDB/TRAKT)
    │       └── tmdb/ (NEW, Session 30)
    │           ├── TmdbApi.kt — Retrofit interface: searchMulti(),
    │           │   getMovieDetails(), getTvDetails(), getTvSeason()
    │           ├── TmdbDto.kt — @Serializable response DTOs, including
    │           │   the movie/TV imdb_id asymmetry (see below)
    │           └── TmdbMetadataProvider.kt — real MetadataProvider
    │               implementation, now Hilt-bound via ProviderModule
    │   (DebridProvider, others — unchanged; still no real DebridProvider
    │    exists)
    ├── ui/ (unchanged this session — no UI screens were touched;
    │   resolve-on-tap already worked end-to-end from Session 27/29's
    │   work, see "Session 30 — What Was Done" below)
    │   ├── details/ (DetailsScreen.kt, DetailsViewModel.kt)
    │   ├── home/ (HomeScreen.kt, HomeViewModel.kt)
    │   ├── navigation/ (NavGraph.kt, PlayerNavArgs.kt)
    │   ├── player/ (PlayerScreen.kt, PlayerViewModel.kt)
    │   ├── search/ (SearchScreen.kt, SearchViewModel.kt)
    │   └── settings/ (SettingsScreen.kt, ProfileViewModel.kt)
    └── usecase/ (unchanged this session)
        ├── CreateProfileUseCase.kt
        ├── DeleteProfileUseCase.kt
        ├── EndPlaybackSessionUseCase.kt
        ├── GetActiveProfileUseCase.kt
        ├── GetContinueWatchingUseCase.kt
        ├── GetEpisodeByIdUseCase.kt
        ├── GetEpisodesUseCase.kt
        ├── GetMediaByIdUseCase.kt
        ├── RemoveFromContinueWatchingUseCase.kt
        ├── ResolvePlaybackUseCase.kt
        ├── SavePlaybackPositionUseCase.kt
        ├── SearchMediaUseCase.kt
        ├── SwitchProfileUseCase.kt
        ├── UpdateProfileUseCase.kt
        └── (others per earlier sessions)

(This tree reflects what's been directly read/touched across sessions,
not a guaranteed exhaustive listing — see the repo itself for ground
truth on files not mentioned in recent session notes.)
## Build Configuration (NEW, Session 30)

**`app/build.gradle.kts`** now reads `local.properties` at configuration
time (via `java.util.Properties`) and requires a
`TMDB_READ_ACCESS_TOKEN` entry to exist there — throws a `GradleException`
with a clear message if it's missing, rather than compiling with a blank
token and failing confusingly at runtime. Exposed to app code as
`BuildConfig.TMDB_READ_ACCESS_TOKEN`. `buildFeatures.buildConfig = true`
was added (not previously enabled).

**Local dev:** Dia's device has a `local.properties` (gitignored, never
committed) containing the line `TMDB_READ_ACCESS_TOKEN=<her v4 Read
Access Token — the long JWT, NOT the shorter v3 API key>`.

**CI:** a GitHub Actions repository secret named `TMDB_READ_ACCESS_TOKEN`
(same value) is set at
`github.com/diaviloai/Onedebrid/settings/secrets/actions`. The workflow
writes it into a fresh `local.properties` on the runner immediately after
checkout — **before any Gradle-invoking step**, including "Regenerate
Gradle wrapper." This ordering matters and was the cause of a real bug
this session — see "Session 30 — What Was Done" below.

## Known, Deliberate Limitations (documented in code, not silently
worked around)

- **`SearchProvider.search()` (Torrentio's free-text path) is still
  permanently non-functional** — unchanged from Session 29. This is
  intentionally NOT what fixes free-text search; `MediaRepository.
  search()` no longer calls it at all as of this session (calls
  `MetadataProvider.searchMedia()`/TMDB instead). Torrentio's `search()`
  remains implemented-but-honest for any future direct caller.
- **`Media.imdbId` is null for every `Media` returned by `searchMedia()`**
  (TMDB search results) — this is a real, permanent constraint of TMDB's
  API, not a bug: `append_to_response` (TMDB's only mechanism for
  returning `imdb_id`) is documented as working only on detail endpoints,
  never on `/search/multi`. A caller needing a specific item's `imdbId`
  (e.g. before `SearchProvider.searchByMedia()`/Torrentio can run) must
  call `fetchMediaDetails()` on it afterward — which is exactly what the
  existing resolve-on-tap flow (Details → Player →
  `ResolvePlaybackUseCase`) already does via `GetMediaByIdUseCase`, no
  new code needed for this session's scope.
- **Movie vs TV asymmetry for `imdb_id`** — movie detail responses
  include `imdb_id` at the top level with no `append_to_response` needed;
  TV detail responses do not, and require `append_to_response=
  external_ids` to get it at all, nested under a separate object.
  `TmdbMetadataProvider` handles both shapes explicitly, not assumed
  identical. Verified against TMDB's own documentation and independent
  real-world integration reports this session, not assumed from training
  data.
- **`fetchMediaDetails()` tries `/movie/{id}` first, falls back to
  `/tv/{id}` on 404** — `Media.id` (a TMDB id) does not by itself
  indicate movie vs TV, and nothing in the domain model currently
  disambiguates before this call. Every TV lookup costs one extra,
  wasted HTTP call today. A cleaner fix would carry `MediaType` alongside
  the id through `getMediaDetails()`'s callers — not done this session,
  known and flagged, not hidden.
- **`fetchEpisodes(season = null)` ("all seasons") is an N+1 call
  pattern** — TMDB has no single-call "all episodes" endpoint (verified,
  not assumed). Fetches TV details first (to learn season count), then
  each season sequentially. Callers should pass an explicit season where
  possible.
- **`resolveExternalId()` is not implemented** — `TmdbMetadataProvider`
  returns `ProviderError.ServiceUnavailable` honestly rather than
  pretending to look something up. TMDB's `/find/{external_id}` endpoint
  would implement this properly; nothing in the app calls this method
  yet, so building it now would have been speculative.
- **Free-text Search does NOT eagerly resolve streams** — a Session 30
  design decision, discussed explicitly with Dia and deliberately chosen
  over the alternative (eager per-result Torrentio lookups, estimated at
  roughly 2N+1 network calls per search against a provider already
  documented as periodically unreliable). Streams resolve only when a
  user taps into Details/Player, reusing Session 27/29's existing
  resolve-on-tap path unchanged. Matches UI/UX Design v0.1's "Zero-Click
  to Content" and "Non-Blocking UI" principles.
- **`ResolvePlaybackUseCase`'s Smart Defaults selection is still "first
  candidate with a hash,"** not a real ranking algorithm — unchanged
  from Session 28.
- **Torrentio's own reliability is a known, accepted tradeoff** —
  unchanged from Session 29.
- **No real `DebridProvider` exists yet** — unchanged.
- **Stream-candidate picker UI still not built** — unchanged from
  Session 28/29; now meaningfully less blocked (real `Media`/`imdbId`
  can exist end-to-end for the first time as of this session).
- All Session 28/29 limitations not superseded above remain accurate —
  see git history on this file for the full lists.

## Carried-Forward Lessons

- **A workflow step that writes a required secret to a file must run
  before ANY step that invokes Gradle, not just before the final build
  step** (Session 30 — new lesson). `gradle wrapper --gradle-version=X`
  evaluates the project's build scripts as part of configuring the
  wrapper task — it is not a lightweight, script-free operation. A
  `build.gradle.kts` guard that throws when a required
  `local.properties` value is missing will fire during wrapper
  regeneration if the secret-writing step comes later in the workflow,
  even though the actual build step never ran. The fix was moving "Write
  local.properties" to immediately after checkout, before "Set up JDK"
  and everything after it.
- **A misleading old CI log can look identical to a new failure with a
  different real cause** (Session 30). The `local.properties` missing-
  token error text was byte-for-byte identical on both the very first
  failed run (genuinely no secret written anywhere yet) and a later run
  (secret existed, but the step order was wrong) — same exception,
  same message, different root cause each time. The annotation summary
  view alone was not enough to tell these apart; only opening the actual
  failed step's raw log (not just the job's top-level annotation list)
  showed which step was actually running when the exception fired.
- **A "green checkmark" reported secondhand is not sufficient
  verification** (Session 30, reinforcing existing standing practice) —
  Dia's own read of the Actions tab as "succeeded" was correct in this
  case, but was still independently confirmed via the direct job URL
  before being treated as true, consistent with "verify, don't trust"
  applying to CI status as much as to file content.
- **A YAML workflow file is whitespace/structure-sensitive in ways a
  human proofreading a pasted diff can easily miss** (Session 30) — a
  find/replace-by-description on `build.yml` produced a file with a
  missing indent on one line and a duplicated `-` on another, which
  caused the workflow to fail to parse at all (instant failure, several
  steps greyed out) rather than fail partway through a step. Full-file
  replacement (select-all-delete, paste fresh) was used to recover,
  rather than another round of targeted find/replace on a
  whitespace-sensitive file.
- All Session 27/28/29 lessons not superseded above remain accurate —
  see git history on this file for the full list (brace-balance checks,
  Composable-context rule, Flow collection pattern,
  `MutableStateFlow.update{}` gotcha, nav-arg sentinel-value pattern,
  infra-noise-isn't-sufficient CI lesson, CI-error-category-can-mislead
  lesson, "check data availability before scoping UI work," etc.)
## Next Steps, In Order

1. **Stream-candidate picker UI.** Now the top priority — no longer
   blocked on real search data (Session 29) or a real `imdbId` source
   (Session 30). `ResolvePlaybackUseCase.resolveSmartDefault()`'s
   candidate-fetch logic remains the natural extraction point. Worth
   confirming with Dia whether this should surface all
   `searchByMedia()` candidates or keep "first candidate with a hash"
   as the default with the picker as an override path.
2. **Continue Watching → Details routing with resumePositionMs.**
   Unchanged from Session 28/29's Next Steps — independent of all
   provider work, could be picked up instead if Dia wants a smaller,
   self-contained session.
3. **`AppError.ValidationError` case.** Unchanged, low urgency.
4. **Completion-percentage / markAsCompleted wiring.** Not started.
5. **SettingsScreen preference-write debounce** — only if needed.
6. **HomeScreen proactive title/artwork display** — only if a priority.
7. **`fetchMediaDetails()`'s movie/TV-ambiguity extra HTTP call** — worth
   a look if it proves costly in practice; carrying `MediaType` through
   `getMediaDetails()`'s call chain would fix it cleanly.
8. **`resolveExternalId()` real implementation (TMDB `/find` endpoint)**
   — only if a real caller emerges; speculative otherwise.

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
  automated tests exist in this repo — NOTE (Session 30): `Media.id`'s
  actual meaning was formally decided this session (TMDB id, stringified
  — see "Session 30 — What Was Done" below); this TODO is about test
  coverage of the round-trip, not about the meaning being undefined
  anymore.
- `TorrentioSearchProvider`'s title-text quality/size/seeder parsing is a
  simple pattern match, not exhaustive (Session 29, unchanged)
- No retry/backoff logic exists for Torrentio's documented periodic
  unreliability (Session 29, unchanged)
- **NEW (Session 30):** `fetchMediaDetails()` costs one wasted HTTP call
  for every TV lookup (tries `/movie/{id}` first, always 404s for TV
  before trying `/tv/{id}`) — see Next Steps #7.
- **NEW (Session 30):** `fetchEpisodes(season = null)` is an N+1 call
  pattern (fetches TV details for season count, then each season
  sequentially) — no single-call TMDB alternative exists.
- **NEW (Session 30):** `resolveExternalId()` returns
  `ServiceUnavailable` unconditionally — not implemented, no caller
  exists yet to justify building it. See Next Steps #8.
- **NEW (Session 30):** TMDB search/multi results have `genreIds` (raw
  int ids) but `TmdbMetadataProvider.toMedia()` for search results maps
  `genres = emptyList()` rather than resolving names — genre names are
  only available from the detail endpoints today. Not discussed as
  in-scope; search-result cards needing genre names would need either a
  detail call per result (rejected for the same reason eager stream
  resolution was rejected — see Known Limitations) or a local
  id→name genre map built from TMDB's `/genre/movie/list` /
  `/genre/tv/list` endpoints (not fetched anywhere yet).

## Session 30 — What Was Done

**Scope confirmed with Dia up front:** the real `MetadataProvider` was
chosen from Session 29's three carried-forward candidates, correctly
identified as the actual remaining blocker for both free-text search and
for `searchByMedia()`/Torrentio to ever run against real data.

**Credential decided with Dia:** TMDB v4 Read Access Token (Bearer JWT),
not the v3 API key — chosen specifically because it never appears in a
URL, so it can't leak into logs, proxy records, or CI logs by accident.

**Architectural decision made and confirmed with Dia before any code was
written — `Media.id`'s meaning, previously undefined in code:** tracing
`MediaRepositoryImpl.getMediaDetails()` found it already hardcoded
`idType = ExternalIdType.IMDB`, implicitly assuming `Media.id == imdbId`
— but this had never actually been exercised against a real provider
before this session, so the assumption was silently wrong and undetected
until now. TMDB's `search/multi` returns TMDB's own numeric ids natively;
IMDb ids are only obtainable from TMDB's detail endpoints. Discussed the
tradeoff directly with Dia: TMDB id was chosen as `Media.id`'s permanent,
canonical meaning app-wide, since it requires no extra resolve step for
search results (IMDb id would have required an extra detail call per
search result just to populate `Media.id` at all). This is a real,
load-bearing decision — noted explicitly rather than buried in a diff,
since it affects cache keys, Continue Watching, nav args, Details, and
Player, even though none of those call sites needed code changes this
session (they already treat `Media.id` as an opaque string).

**Second design decision made and confirmed with Dia — free-text Search
results do NOT eagerly resolve Torrentio streams:** initially proposed
otherwise per Dia's first answer, but walked through the real cost
(TMDB search/multi doesn't return `imdbId`; getting it requires a
per-result detail call; then a per-result Torrentio `searchByMedia()`
call — roughly 2N+1 network calls per search, against a provider already
documented as periodically flaky) and Dia switched to resolve-on-tap.
Confirmed this path already exists end-to-end with zero new code needed
— `DetailsViewModel`'s `onPlayMovie`/`onPlayEpisode` already navigate by
`mediaId`/`episodeId` only, and `PlayerViewModel` (Session 27) already
resolves `Media` and calls `ResolvePlaybackUseCase` (Session 29's
`searchStreamsByMedia()` path) itself on init — this session's
`TmdbMetadataProvider` is the only missing piece that was blocking this
already-built flow from working against real data.

**TMDB API shapes verified via web search before writing any DTOs, not
assumed from training data** (training data on a specific third-party
API's current wire format is exactly the kind of thing that goes stale):
confirmed `append_to_response` only works on detail endpoints, never on
search endpoints; confirmed movies get `imdb_id` at the top level with no
append needed while TV requires `append_to_response=external_ids` for
it, nested under a separate object (one contradicting 2021-era forum post
was weighed against TMDB's own current reference docs and a same-year
forum confirmation, and set aside as outdated); confirmed `search/multi`
field names (`media_type`, `title`/`name`, `release_date`/
`first_air_date`); confirmed `/tv/{id}/season/{n}` returns a full
`episodes` array in one call, unpaginated.

**Files created:**
- **`provider/metadata/tmdb/TmdbDto.kt`** — `@Serializable` response
  DTOs: `TmdbSearchResultDto` (single shape for movie/TV/person search
  hits, disambiguated by `mediaType` at mapping time — not a sealed
  hierarchy, not worth the polymorphic serialization complexity for this
  few fields), `TmdbExternalIdsDto`, `TmdbMovieDetailsDto`,
  `TmdbTvDetailsDto`, `TmdbGenreDto`, `TmdbSeasonDto`, `TmdbEpisodeDto`.
  Movie and TV detail DTOs shaped differently on purpose (see the
  `imdb_id` asymmetry above), not for stylistic consistency. Corrected
  mid-session to add a missing `numberOfSeasons` field (see "mistakes"
  below).
- **`provider/metadata/tmdb/TmdbApi.kt`** — Retrofit interface:
  `searchMulti()`, `getMovieDetails()`, `getTvDetails()`, `getTvSeason()`.
  `appendToResponse` defaults to `"external_ids"` on both detail calls.
  Auth intentionally not handled here — applied by an OkHttp interceptor
  instead, keeping this interface a pure wire-contract description.
- **`provider/metadata/tmdb/TmdbMetadataProvider.kt`** — real
  `MetadataProvider` implementation. `fetchMediaDetails()` only accepts
  `idType == TMDB` (returns `NotFound` otherwise — `resolveExternalId()`
  is the missing path that would convert other id types, not
  implemented this session, see Known Limitations); tries `/movie/{id}`
  first, falls back to `/tv/{id}` on 404. `fetchEpisodes(season = null)`
  fetches all seasons via an N+1 call pattern (see Known Limitations).
  `searchMedia()` filters `search/multi` results to `movie`/`tv` only,
  always sets `imdbId = null` (real API constraint, not a bug). Same
  `HttpException`/`IOException`/`SerializationException` → `ProviderError`
  mapping convention as `TorrentioSearchProvider` (Session 29).

**Files modified:**
- **`provider/metadata/MetadataProvider.kt`** — added `searchMedia(
  query): ProviderResult<List<Media>>` to the interface, with a doc
  comment explaining why this lives here (a title-catalog lookup, same
  kind of operation as `fetchMediaDetails()` just keyed by text) rather
  than on `SearchProvider` (which owns stream/torrent discovery, a
  genuinely different capability — see `TorrentioSearchProvider`'s own
  doc comment on why it can't do free-text at all).
- **`provider/metadata/StubMetadataProvider.kt`** — added a matching
  `searchMedia()` override (also fails); no longer Hilt-bound as of this
  session, kept as a reference/fallback implementation.
- **`data/repository/MediaRepositoryImpl.kt`** — three edits: (1)/(2)
  fixed a real pre-existing bug in `getMediaDetails()` and
  `getEpisodes()`, both hardcoded to `ExternalIdType.IMDB`, changed to
  `ExternalIdType.TMDB` to match this session's `Media.id` decision —
  this bug had never been caught before because no real provider had
  ever exercised this code path; (3) `search()` rewired from
  `searchProvider.search()` (Torrentio, permanently `NotFound`) to
  `metadataProvider.searchMedia()` (TMDB), mapping each returned `Media`
  to a `SearchResult` with `candidates = emptyList()` — deliberate, not
  a placeholder (see the eager-vs-on-tap decision above).
- **`di/NetworkModule.kt`** — added a `@TmdbOkHttpClient`-qualified
  `OkHttpClient` carrying a new private `AuthInterceptor` (adds
  `Authorization: Bearer <BuildConfig.TMDB_READ_ACCESS_TOKEN>` to every
  request), a `@TmdbRetrofit`-qualified `Retrofit` instance pointed at
  `https://api.themoviedb.org/3/`, and `TmdbApi`. Deliberately a
  *separate* `OkHttpClient` from Torrentio's — an auth header meant for
  TMDB must never reach Torrentio (keyless) or any future differently-
  authed provider, and vice versa. Extracted the shared
  `HttpLoggingInterceptor` into its own `@Provides` function so both
  clients reuse one instance rather than constructing separate ones —
  flagged as a real, if low-risk, restructuring of existing Session 29
  code, not just an addition.
- **`di/ProviderModule.kt`** — `bindMetadataProvider()` now binds
  `TmdbMetadataProvider` instead of `StubMetadataProvider`.
- **`app/build.gradle.kts`** — added `local.properties` reading logic
  (see "Build Configuration" section above), `buildConfigField` for
  `TMDB_READ_ACCESS_TOKEN`, and `buildFeatures.buildConfig = true`.
- **`.github/workflows/build.yml`** — added a "Write local.properties"
  step. Required a real fix mid-session (see "mistakes" below) to move
  it before, not after, the Gradle-wrapper-regeneration step.

**Four real mistakes this session — all caught before being treated as
done, consistent with this project's standing verification practice:**

1. **`TmdbDto.kt`'s `TmdbTvDetailsDto` was missing a `numberOfSeasons`
   field** that `TmdbMetadataProvider.kt` (written and presented
   immediately after) referenced for its "all seasons" episode-fetch
   logic. Caught by re-reading the two files together before Dia pasted
   either, not after a compile failure — a real design-time cross-file
   mistake on Claude's part, fixed via a small find/replace on
   `TmdbDto.kt` before `TmdbMetadataProvider.kt` was presented as ready.
2. **`.github/workflows/build.yml`'s step order was wrong from the
   start** — "Write local.properties" was placed after "Regenerate
   Gradle wrapper" (a step that, as it turns out, itself evaluates
   `build.gradle.kts` and therefore needs the token to already exist).
   This produced a confusing failure: the first CI run's error
   ("Missing TMDB_READ_ACCESS_TOKEN") looked exactly like a secret-
   configuration problem on Dia's end, and required real debugging (an
   Actions API rate-limit, then reading a misleading annotation-only
   summary, then finally the raw step log) to find the true cause — a
   design mistake in the workflow file Claude wrote, not anything Dia
   did wrong. Fixed by moving the step to immediately after checkout.
3. **A subsequent find/replace-by-description on `build.yml` produced a
   YAML parse error** — a missing indent on the `Checkout code` step and
   a duplicated `-` on the `Grant execute permission` step, causing the
   workflow to fail to parse entirely (instant failure, several steps
   greyed out) rather than fail partway through a step. Caught by Dia
   pasting the actual file content for review rather than assuming the
   edit landed correctly. Fixed via a full-file replacement (select-all-
   delete, paste fresh) rather than another targeted edit.
4. Two GitHub Actions API rate-limit hits during debugging, worked
   around the standard way (direct run/job URL via `web_fetch`,
   consistent with Sessions 27–29).

**Build verification:** final push verified green via direct job URL:
`github.com/diaviloai/Onedebrid/actions/runs/33456443813/job/99697300273`
— job "build" succeeded in 4m 21s. All annotations on that run
(13 warnings) confirmed to be GitHub infrastructure noise (Gradle cache-
service 400s/outage messages, Node.js 20/setup-java v4 deprecation
notices) by checking the job's actual top-level status line, not
inferred from the annotation list alone — same standing lesson as
Sessions 27–29, reapplied correctly. All ten touched/created files were
re-pulled fresh after the final push and spot-checked (Hilt bindings,
`idType` fixes, `search()` rewiring, `numberOfSeasons` field) against
intended content — all matched, no mismatches found.

At the end of the next session, update currentsprint.md (full file, in
a code block, chunked into sequential pastes if it's likely to exceed
~450-500 lines) and verify it directly against
raw.githubusercontent.com/diaviloai/Onedebrid/main/currentsprint.md
before treating the session as closed — and do not treat any session as
closed without an actual green CI result for whatever was last pushed,
verified via the direct run/job URL if the Actions API is rate-limited.