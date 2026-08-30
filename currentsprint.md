# OneDebrid — Current Sprint

## Status

Implementation in progress. Architectural design phase complete.

This file is fully rewritten each session — it reflects actual current
code state, verified by pulling the repo and reading files directly, not
appended to informally.

Build verification: project compiles cleanly as of Session 29's close,
confirmed via GitHub Actions on the latest pushed commit — job "build"
succeeded in 3m 49s, per the direct run/job URL
(`github.com/diaviloai/Onedebrid/actions/runs/33336479138/job/99324170819`).
All files touched this session were independently re-pulled from
`raw.githubusercontent.com` after each push and diffed against intended
content (brace-balance checked) before this file was updated.

**Sessions 1–25 summary** (condensed from prior full write-ups, which
remain in git history on this file if the detail is ever needed): built
layer by layer — domain models → error types → provider interfaces →
repository interfaces → Room entities/DAOs → Hilt wiring → coroutine
infrastructure → use cases → coordinators → ViewModels → Compose screens
(SearchScreen, HomeScreen, SettingsScreen). Session 25 added Continue
Watching tap-to-resume (`PendingPlaybackHolder` + direct-to-Player nav)
and cache-first `MediaRepository` reads via `MediaCache`.

**Session 26 summary:** built the Details/Episode-picker screen
(`ui/details/DetailsScreen.kt` + `DetailsViewModel.kt`), reached only
from Search. `Route.Details` became the first route in the graph to
carry a `mediaId` nav arg. Search's results became fully tappable.
Continue Watching's direct-to-Player flow was left unchanged (explicit
scope decision). Also fixed a real pre-existing bug: `Media.kt` was
missing `@Serializable` on the `Media` class itself.

**Session 27 summary:** retired `PendingPlaybackHolder` (an in-memory
singleton) entirely, replacing it with real Navigation Compose arguments
to the Player route (`mediaId` required, `episodeId`/`resumeMs` optional
via sentinel values). `PlayerViewModel` now resolves `Media`/`Episode`/
active profile itself on init via `GetMediaByIdUseCase`/new
`GetEpisodeByIdUseCase`/`GetActiveProfileUseCase`, then builds its own
`PlaybackRequest`. `HomeViewModel`/`DetailsViewModel` simplified as a
consequence — both emit a `PlayerNavArgs` (new type, `ui.navigation`)
carrying primitives instead of pre-resolving `Media`. New
`MediaRepository.getEpisodeById()` added via `getEpisodes()` + in-memory
filter. `PendingPlaybackHolder.kt` deleted. Two real compile-breaking
mistakes were made and fixed within the session (a `str_replace`
insertion landing after the actual closing brace; a missing final
closing brace) — both root-caused and fixed before CI came back green.
Comment-only `PendingPlaybackHolder` references were left behind in
three files, cleared in Session 28.

**Session 28 summary:** Investigated the stream-candidate picker UI (the
top Next Step from Session 27) but before designing it, traced
`preferredSource = null` end-to-end and found `ResolvePlaybackUseCase`
was failing immediately with `NoCachedStreamAvailable` in that case —
there was no actual Smart Defaults fallback anywhere, despite every
current caller passing null. Also found `SearchProvider` had exactly one
Hilt-bound implementation, `StubSearchProvider`, which always returned
`ProviderError.ServiceUnavailable` — meaning there was no real search
data in the app at all, and the picker UI would have had nothing real to
render. Discussed with Dia and reprioritized: fixed the Smart Defaults
fallback bug that session (small, contained, real, independently
valuable) and deferred the picker to a dedicated session once real
search data exists. `ResolvePlaybackUseCase.invoke()` gained a
`profileId` param and a private `resolveSmartDefault()` method —
searched by `request.media.title`, filtered to `SearchResult`s matching
`request.media.id` exactly, picked the first `StreamCandidate` with a
non-null hash, resolved it. `PlaybackCoordinator.play()` updated to pass
`profileId` through. Also cleared three comment-only
`PendingPlaybackHolder` references punted from Session 27. Both pushes
verified independently green via direct run/job URL. Key finding carried
into Session 29: `SearchProvider` had no real implementation at all —
this blocked both the picker UI and any real testing of the new Smart
Defaults fallback.

## Package Structure

com.onedebrid.app/
    ├── MainActivity.kt
    ├── OneDebridApplication.kt
    ├── coordinator/
    │   ├── PlaybackCoordinator.kt (Session 28: play() passes profileId
    │   │   through to resolvePlaybackUseCase())
    │   ├── SearchCoordinator.kt
    │   └── SessionCoordinator.kt
    ├── data/
    │   ├── local/
    │   │   ├── AppDatabase.kt
    │   │   ├── MediaCache.kt (Session 25 — wraps CacheEntryDao)
    │   │   ├── TypeConverters.kt
    │   │   ├── dao/ (unchanged)
    │   │   └── entity/ (unchanged)
    │   └── repository/
    │       ├── MediaRepository.kt (Session 29: added
    │       │   searchStreamsByMedia(media, episode) — ID-based stream
    │       │   lookup, separate from the free-text search() method; see
    │       │   "Session 29 — What Was Done" below)
    │       ├── MediaRepositoryImpl.kt (Session 29: implements
    │       │   searchStreamsByMedia() as a pass-through to
    │       │   searchProvider.searchByMedia(), translating
    │       │   episode → SearchFilters(season, episode))
    │       ├── PlaybackRepository.kt / PlaybackRepositoryImpl.kt
    │       ├── ProfileRepository.kt / ProfileRepositoryImpl.kt
    │       ├── RepositoryResult.kt
    │       ├── SearchRepository.kt / SearchRepositoryImpl.kt
    │       ├── SessionRepository.kt / SessionRepositoryImpl.kt
    │       └── (Subtitle/Download repositories not yet built)
    ├── di/
    │   ├── CoroutineDispatchers.kt
    │   ├── NetworkModule.kt (NEW, Session 29 — first real Retrofit/
    │   │   OkHttp wiring in the app; provides a shared OkHttpClient, a
    │   │   Torrentio-qualified Retrofit instance, and TorrentioApi)
    │   └── DatabaseModule, RepositoryModule, ProviderModule
    │       (ProviderModule Session 29: binds TorrentioSearchProvider as
    │       the SearchProvider, replacing StubSearchProvider)
    ├── domain/
    │   ├── error/
    │   │   └── AppError.kt (unchanged this session — see Open TODOs re:
    │   │       ValidationError)
    │   └── model/
    │       ├── Media.kt
    │       ├── Episode.kt
    │       ├── PlaybackRequest.kt
    │       ├── SearchResult.kt (StreamCandidate defined here)
    │       ├── SessionState.kt
    │       ├── StreamSource.kt (VideoQuality enum)
    │       ├── SubtitleTrack.kt
    │       ├── UserProfile.kt
    │       └── WatchedItem.kt
    ├── provider/
    │   └── search/
    │       ├── SearchProvider.kt (Session 29: added searchByMedia() —
    │       │   ID-based lookup, alongside the existing free-text
    │       │   search(). See "Session 29 — What Was Done" below for the
    │       │   full design reasoning)
    │       ├── StubSearchProvider.kt (Session 29: added a matching
    │       │   searchByMedia() override, also always fails; no longer
    │       │   Hilt-bound as of this session, kept as a reference/
    │       │   fallback implementation)
    │       └── torrentio/ (NEW, Session 29)
    │           ├── TorrentioApi.kt — Retrofit interface,
    │           │   GET stream/{type}/{id}.json
    │           ├── TorrentioDto.kt — @Serializable response DTOs
    │           └── TorrentioSearchProvider.kt — real SearchProvider
    │               implementation, now Hilt-bound via ProviderModule
    │   (MetadataProvider, StubMetadataProvider, DebridProvider, others —
    │    unchanged; still no real MetadataProvider/DebridProvider exists)
    ├── ui/
    │   ├── details/
    │   │   ├── DetailsScreen.kt (Session 27: onNavigateToPlayer
    │   │   │   signature carries mediaId/episodeId/resumeMs)
    │   │   └── DetailsViewModel.kt (Session 27: emits PlayerNavArgs)
    │   ├── home/
    │   │   ├── HomeScreen.kt (Session 27: dead resolving-state UI
    │   │   │   removed)
    │   │   └── HomeViewModel.kt (Session 27: emits PlayerNavArgs
    │   │       immediately, no longer resolves Media first)
    │   ├── navigation/
    │   │   ├── NavGraph.kt (Session 27: Route.Player carries mediaId/
    │   │   │   episodeId/resumeMs nav args)
    │   │   └── PlayerNavArgs.kt (Session 27)
    │   ├── player/
    │   │   ├── PlayerScreen.kt (Session 27)
    │   │   └── PlayerViewModel.kt (Session 27: resolves its own Media/
    │   │       Episode/active profile on init from nav args)
    │   ├── search/
    │   │   ├── SearchScreen.kt (Session 28: stale
    │   │   │   PendingPlaybackHolder doc-comment references cleared)
    │   │   └── SearchViewModel.kt (Session 28: same cleanup)
    │   └── settings/
    │       ├── SettingsScreen.kt
    │       └── ProfileViewModel.kt
    └── usecase/
        ├── CreateProfileUseCase.kt
        ├── DeleteProfileUseCase.kt
        ├── EndPlaybackSessionUseCase.kt
        ├── GetActiveProfileUseCase.kt
        ├── GetContinueWatchingUseCase.kt
        ├── GetEpisodeByIdUseCase.kt (Session 27)
        ├── GetEpisodesUseCase.kt
        ├── GetMediaByIdUseCase.kt
        ├── RemoveFromContinueWatchingUseCase.kt
        ├── ResolvePlaybackUseCase.kt (Session 29: resolveSmartDefault()
        │   now calls mediaRepository.searchStreamsByMedia(media,
        │   episode) instead of the title-based search() path — also
        │   fixes a real Session 28 omission where request.episode was
        │   never passed through, meaning a TV_SHOW PlaybackRequest could
        │   never have resolved via Smart Defaults even with a working
        │   provider. profileId dropped from the private
        │   resolveSmartDefault() signature — no longer needed there. See
        │   "Session 29 — What Was Done" below)
        ├── SavePlaybackPositionUseCase.kt
        ├── SearchMediaUseCase.kt
        ├── SwitchProfileUseCase.kt
        ├── UpdateProfileUseCase.kt
        └── (others per earlier sessions)

(This tree reflects what's been directly read/touched across sessions,
not a guaranteed exhaustive listing — see the repo itself for ground
truth on files not mentioned in recent session notes.)

## App Navigation State (unchanged since Session 27)

Five routes exist, all wired into a single `NavGraph.kt`: Home (start
destination), Search, Details, Player, Settings. See git history on this
file (Session 27/28 entries) for the full per-route description — no
navigation changes were made in Session 29.

## Session 29 — What Was Done

**Scope confirmed with Dia up front:** three candidates were on the
table per Session 28's Next Steps (a real SearchProvider; the
stream-candidate picker UI; Continue Watching → Details routing). Dia
chose the SearchProvider work, correctly identifying it as the actual
blocker underneath the other two.

**Backend choice — Torrentio, researched and confirmed live via web
search** (not assumed from training data, since this is exactly the
kind of "current status" fact that goes stale): Torrentio
(`torrentio.strem.fun`) is a free, keyless, Stremio-protocol torrent-
indexer aggregator, confirmed reachable as of session date via an
independent third-party status-monitoring source that checks it every
minute. Known tradeoff, discussed with Dia and accepted: multiple
independent sources describe Torrentio as periodically flaky under
high load — designed for defensively from the start (see error mapping
below), not treated as a hidden risk.

**Design problem found before writing any code — this was the key
architectural discussion this session:** Torrentio's only endpoint,
`GET /stream/{type}/{id}.json`, requires an already-known IMDb ID (and
season/episode for series) — it has no free-text search capability at
all. But `SearchProvider.search(query: String)` — the only method that
existed — is free-text, and every call site in the app (SearchScreen →
SearchViewModel → SearchCoordinator → SearchMediaUseCase →
MediaRepository.search()) was free-text end to end. A
`TorrentioSearchProvider` implementing only `search()` would have been
just as functionally useless as `StubSearchProvider`, silently.

Traced the full blast radius before proposing a fix (every call site of
`.search(` repo-wide) and found something important: **there is no
free-text-capable provider of any kind in this codebase yet** — no TMDB
integration, no title→IMDb-ID resolution step anywhere.
`MetadataProvider` only has ID-based lookup (`fetchMediaDetails(
externalId, idType)`), not free-text search either. This meant the
"real" fix wasn't just fixing `SearchProvider`'s contract — free-text
search has no possible implementation yet regardless of provider,
because nothing in the architecture can turn a title string into an
IMDb ID today.

**Discussed with Dia and agreed on scope:** rather than also building a
TMDB-backed free-text search provider this session (a separate, larger
piece of work), added `searchByMedia(media, filters)` as a new method
on `SearchProvider` **alongside** the existing `search()` — an addition,
not a replacement. `search()` stays free-text and stays honestly
non-functional (returns `ProviderError.NotFound` from
`TorrentioSearchProvider`, explicitly documented as "this provider
genuinely cannot do this job") until a real metadata-search provider
exists. `searchByMedia()` is where Torrentio actually plugs in
correctly — it's exactly the ID-based shape Torrentio supports, and
exactly what `ResolvePlaybackUseCase.resolveSmartDefault()` already has
the inputs for (it already holds a full `Media` with `imdbId`, from
`PlaybackRequest`).

**Files created:**
- **`provider/search/torrentio/TorrentioDto.kt`** — `@Serializable`
  response DTOs (`TorrentioStreamResponseDto`, `TorrentioStreamDto`,
  `TorrentioBehaviorHintsDto`) matching the Stremio addon stream-
  response protocol. `infoHash` kept nullable — see next bullet.
- **`provider/search/torrentio/TorrentioApi.kt`** — minimal Retrofit
  interface, one method: `GET stream/{type}/{id}.json`.
- **`provider/search/torrentio/TorrentioSearchProvider.kt`** — the real
  implementation. Builds the movie (`imdbId`) or series
  (`imdbId:season:episode`) lookup ID, calls the API, maps
  `TorrentioStreamDto` → `StreamCandidate`. Two things researched and
  handled defensively rather than assumed: (1) a real, documented
  Torrentio API instability (rivenmedia/riven#1342, Jan 2026) where
  `infoHash` is sometimes omitted and only recoverable by extracting a
  40-hex-char segment from the stream's debrid `url` field —
  `extractHash()` tries `infoHash` first, falls back to the URL scan,
  and drops (not crashes on) entries where neither works; (2) quality/
  size/seeder parsing from Torrentio's free-text `title` field via
  simple pattern matching (documented as not exhaustive — a candidate
  with `VideoQuality.UNKNOWN` or a null seeder count is still usable,
  per `VideoQuality`'s own existing doc comment). HTTP errors mapped to
  `ProviderError` by status code (401/403 → AuthenticationFailed, 404 →
  NotFound, 429 → RateLimited, 5xx → ServiceUnavailable); `IOException`
  → NetworkError; `SerializationException` → ParsingError.
- **`di/NetworkModule.kt`** — first real Retrofit/OkHttp wiring in the
  app (the dependencies were already declared in
  `build.gradle.kts`/`libs.versions.toml`, just unused until now).
  Provides a shared `OkHttpClient` (with `HttpLoggingInterceptor` at
  `BASIC` level), a `@TorrentioRetrofit`-qualified `Retrofit` instance
  pointed at `https://torrentio.strem.fun/`, and `TorrentioApi`.
**Files modified:**
- **`provider/search/SearchProvider.kt`** — added `searchByMedia(media,
  filters)` to the interface, doc comment explains the free-text-vs-ID
  split (see above).
- **`provider/search/StubSearchProvider.kt`** — added a matching
  `searchByMedia()` override (also fails), to keep compiling against the
  updated interface. No longer Hilt-bound as of this session (see
  ProviderModule below) but kept in the codebase as a reference/fallback
  implementation, same as before.
- **`di/ProviderModule.kt`** — `bindSearchProvider()` now binds
  `TorrentioSearchProvider` instead of `StubSearchProvider`.
- **`data/repository/MediaRepository.kt`** /
  **`MediaRepositoryImpl.kt`** — added `searchStreamsByMedia(media,
  episode)`, a pass-through to `searchProvider.searchByMedia()` that
  translates `episode?.seasonNumber`/`episodeNumber` into
  `SearchFilters(season, episode)`. Exists so
  `ResolvePlaybackUseCase` never touches the provider directly, per
  `Internal_API_Specification.md`'s layering rules.
- **`usecase/ResolvePlaybackUseCase.kt`** — `resolveSmartDefault()`
  switched from `mediaRepository.search(title)` to
  `mediaRepository.searchStreamsByMedia(media, episode)`. Also fixes a
  real Session 28 omission: `request.episode` was never passed to the
  search call at all, meaning a TV_SHOW `PlaybackRequest` could never
  have resolved via Smart Defaults regardless of provider (Torrentio's
  series endpoint requires season/episode). `profileId` dropped from
  this private method's signature (no longer used inside it); `invoke()`
  still receives and uses `profileId` as before, just doesn't thread it
  into this call anymore.

**Two real mistakes made and caught this session — both via content
diffing, not assumed correct because a paste "went through":**

1. **`ProviderModule.kt` paste corrupted the file** — the intended
   full-file replacement did not cleanly overwrite the existing content;
   the pushed result had `bindSearchProvider()` duplicated (one correct,
   one stale referencing the now-deleted `StubSearchProvider` import)
   and `bindDebridProvider()` missing entirely. Caught by re-pulling and
   reading the actual pushed file rather than trusting the paste. Fixed
   with an explicit full-file overwrite (not a find/replace, since the
   corrupted state made anchor text unreliable) and re-verified clean on
   the next pull.
2. **`NetworkModule.kt` used a deprecated OkHttp API that failed the
   build** — `okhttp3.MediaType.Companion.get(this)` (written as an
   uncertain workaround at authoring time and explicitly flagged as such
   before Dia pasted it) compiled but triggered a deprecation-as-error
   failure (`compileDebugKotlin FAILED`, not a KSP/annotation-processing
   error). Fixed with a proper `okhttp3.MediaType.Companion.toMediaType`
   import and removal of the local wrapper function.

Both were caught by pulling the real CI log / real pushed file content
rather than accepting "ran clean" or a successful paste at face value —
consistent with this project's standing verification practice.

**Build verification:** final push (the `NetworkModule.kt` fix) verified
green via direct job URL:
`github.com/diaviloai/Onedebrid/actions/runs/33336479138/job/99324170819`
— job "build" succeeded in 3m 49s. All 13 annotations on that run were
confirmed to be GitHub infrastructure noise (Gradle cache-service outage
messages, Node.js/setup-java deprecation notices) by checking the job's
actual top-level status line, not inferred from the annotation list
alone — same lesson as Sessions 27/28. All ten touched/created files
were re-pulled fresh from the tarball after the final push and brace-
balance checked; all matched intended content with no mismatches.

## Known, Deliberate Limitations (documented in code, not silently
worked around)

- **`SearchProvider.search()` (free-text) is still non-functional** —
  `TorrentioSearchProvider` explicitly returns `ProviderError.NotFound`
  from it, since Torrentio has no free-text capability at all. This is
  now a permanent, correct limitation of this specific provider, not a
  temporary stub gap — free-text search requires a different kind of
  provider (e.g. TMDB-backed catalog search) that does not exist yet.
  The Search screen itself will continue to show its error state until
  that provider exists.
- **`SearchProvider.searchByMedia()` requires `Media.imdbId` to be
  non-null** — returns `ProviderError.NotFound` (mapped to
  `AppError.NoCachedStreamAvailable`) otherwise. Since there is still no
  real `MetadataProvider` (only `StubMetadataProvider`), no `Media` in
  the app today actually carries a real `imdbId` from a live fetch —
  this path is implemented and correct but, like Session 28's fallback
  fix, not yet exercisable against real data end-to-end. A `Media`
  seeded with a real `imdbId` (e.g. via `MediaCache` directly, for
  manual testing) would exercise it today.
- **`ResolvePlaybackUseCase`'s Smart Defaults selection is still "first
  candidate with a hash,"** not a real ranking algorithm — unchanged
  from Session 28, still deliberately minimal pending real profile-
  preference signal reaching this layer.
- **Torrentio's own reliability is a known, accepted tradeoff** — free
  and keyless, but documented by multiple independent sources as
  periodically flaky under load. Mapped to `ProviderError.ServiceUnavailable`/
  `NetworkError` like any other provider failure; no special retry/
  circuit-breaker logic added this session (not discussed as in-scope).
- **No real `MetadataProvider` or `DebridProvider` exists yet** —
  unchanged. `TorrentioSearchProvider` only replaces the `SearchProvider`
  slot.
- **Stream-candidate picker UI still not built** — unchanged from
  Session 28; now meaningfully less blocked (a real, if narrow,
  `SearchProvider` path exists), but still not started.
- All Session 28 limitations not superseded above remain accurate — see
  git history on this file for the full Session 28 list.

## Carried-Forward Lessons

- **A file-content diff after push can catch a corrupted/duplicated
  paste that a naive "did it push" check would miss** (Session 29 —
  `ProviderModule.kt`). The corruption here wasn't truncation (Session
  27/28's failure mode) — it was old and new content merging incorrectly
  in a way that still produced a syntactically-plausible-looking diff at
  a glance. Full read-through of the pulled file, not just a diff
  summary, caught it.
- **A CI failure's error message can look like the wrong subsystem** —
  the `ProviderModule.kt` corruption surfaced as a KSP/Hilt "could not
  be resolved" error, which reads like a Dagger graph problem, but the
  actual root cause was upstream: a plain content-corruption bug in the
  source file KSP was trying to process. Worth reading past the
  immediate error type to what's actually different in the file.
- **A CI compile failure is not always a KSP/annotation-processing
  error** — the `NetworkModule.kt` deprecation failure showed up as a
  plain `compileDebugKotlin FAILED`, a different task and failure mode
  than the `ProviderModule.kt` issue above. Worth reading which Gradle
  task actually failed, not assuming it's always the same subsystem.
- **Flagging genuine uncertainty about a specific line before it's
  pasted is worth doing even when most of a file is solid** — the
  `MediaType.Companion.get()` workaround in `NetworkModule.kt` was
  explicitly called out as "least certain to compile cleanly" before
  Dia pasted it, and it was in fact the exact line CI failed on. Doesn't
  replace verification, but made the failure fast to diagnose.
- **When a third-party API's exact wire format can't be verified against
  official developer docs (only against end-user troubleshooting content
  and real integration code from other open-source projects), design
  defensively for the documented instability rather than assuming the
  happy-path shape** (Session 29 — Torrentio's `infoHash` omission
  issue, sourced from a real GitHub issue in another project, not
  assumed).
- All Session 27/28 lessons not superseded above remain accurate — see
  git history on this file for the full list (brace-balance checks,
  Composable-context rule, Flow collection pattern,
  `MutableStateFlow.update{}` gotcha, nav-arg sentinel-value pattern,
  infra-noise-isn't-sufficient CI lesson, "check data availability
  before scoping UI work," etc.)

## Next Steps, In Order

1. **A real `MetadataProvider` (e.g. TMDB-backed).** Newly the top
   priority as of Session 29 — this is the actual remaining blocker for
   both free-text search (`SearchProvider.search()`) and for
   `searchByMedia()` to ever run against real `Media` (needs a real
   `imdbId`, which nothing currently produces). Not yet scoped or
   discussed with Dia.
2. **Stream-candidate picker UI.** Less blocked than before (a real,
   narrow `SearchProvider` path exists via `searchByMedia()`), but still
   depends on #1 above to have real `Media`/`imdbId` to search with
   end-to-end. `ResolvePlaybackUseCase.resolveSmartDefault()`'s
   candidate-fetch logic remains the natural extraction point.
3. **Continue Watching → Details routing with resumePositionMs.**
   Unchanged from Session 28's Next Steps — independent of the
   search-provider work, could be picked up instead if Dia wants to
   sidestep the MetadataProvider dependency chain entirely.
4. **`AppError.ValidationError` case.** Unchanged, low urgency.
5. **Completion-percentage / markAsCompleted wiring.** Not started.
6. **SettingsScreen preference-write debounce** — only if needed.
7. **HomeScreen proactive title/artwork display** — only if a priority.

## Open TODOs (carried forward, unchanged unless noted)

- App icon: placeholder system drawable in AndroidManifest.xml
- SearchRepository.updateSearchSession uses `Map<String, String>` for
  filters; revisit if SearchFilters gets promoted to a domain model
- AppError has no ValidationError case; also relevant to
  `searchByMedia()`'s missing-`imdbId` NotFound case (Session 29 — reuses
  the existing NotFound→NoCachedStreamAvailable mapping rather than
  inventing a new path, consistent with getEpisodeById()'s existing
  precedent of reusing broad error types)
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
- `Media.id` round-trip between Search/Details/Player is unverified,
  no automated tests exist in this repo
- **NEW (Session 29):** `TorrentioSearchProvider`'s title-text quality/
  size/seeder parsing is a simple pattern match, not exhaustive — will
  silently under-parse titles that don't match common release-naming
  conventions (falls back to `VideoQuality.UNKNOWN`/null, which is
  handled gracefully elsewhere, not a crash risk).
- **NEW (Session 29):** No retry/backoff logic exists for Torrentio's
  documented periodic unreliability — a transient failure surfaces the
  same as a permanent one today. Not discussed as in-scope this session;
  worth a look if it proves disruptive in practice.

At the end of the next session, update currentsprint.md (full file, in
a code block, chunked into sequential pastes if it's likely to exceed
~450-500 lines) and verify it directly against
raw.githubusercontent.com/diaviloai/Onedebrid/main/currentsprint.md
before treating the session as closed — and do not treat any session as
closed without an actual green CI result for whatever was last pushed,
verified via the direct run/job URL if the Actions API is rate-limited.