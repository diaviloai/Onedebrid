# OneDebrid — Current Sprint

## Status

Implementation in progress. Architectural design phase complete.

This file is fully rewritten each session — it reflects actual current
code state, verified by pulling the repo and reading files directly, not
appended to informally.

Build verification: project compiles cleanly as of Session 24's close,
confirmed via GitHub Actions CI on commit `ad1a5d98` ("update"), checked
directly via the GitHub Actions API
(`api.github.com/repos/diaviloai/Onedebrid/actions/runs`) — status
`completed`, conclusion `success`. All 7 files touched this session were
also independently verified byte-for-byte against
`raw.githubusercontent.com` (cross-checked against a fresh full-repo
tarball pull too, to rule out CDN lag) before this file was updated.

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
    │       ├── MediaRepository.kt / MediaRepositoryImpl.kt
    │       ├── PlaybackRepository.kt / PlaybackRepositoryImpl.kt
    │       ├── ProfileRepository.kt / ProfileRepositoryImpl.kt
    │       ├── RepositoryResult.kt
    │       ├── SearchRepository.kt / SearchRepositoryImpl.kt
    │       ├── SessionRepository.kt / SessionRepositoryImpl.kt
    │       └── (Subtitle/Download repositories not yet built)
    ├── di/
    │   ├── CoroutineDispatchers.kt
    │   └── (Hilt modules)
    ├── domain/
    │   ├── error/
    │   │   └── AppError.kt
    │   └── model/
    │       ├── Media.kt
    │       ├── Episode.kt
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
    │   └── (SearchProvider, StubSearchProvider, others)
    ├── ui/
    │   ├── home/
    │   │   ├── HomeScreen.kt
    │   │   └── HomeViewModel.kt
    │   ├── navigation/
    │   │   ├── NavGraph.kt
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
        ├── RemoveFromContinueWatchingUseCase.kt
        ├── SavePlaybackPositionUseCase.kt
        ├── SearchMediaUseCase.kt
        ├── SwitchProfileUseCase.kt
        ├── UpdateProfileUseCase.kt
        └── (others per earlier sessions)

(This tree reflects what's been directly read/touched across sessions,
not a guaranteed exhaustive listing — see the repo itself for ground
truth on files not mentioned in recent session notes.)

## App Navigation State (as of Session 23, unchanged this session)

All four primary screens exist and are wired into a single `NavGraph.kt`:

- **Home** (`Route.Home`, start destination) → real `HomeScreen.kt`.
  Shows Continue Watching (list, not resumable yet — see Known
  Limitations). Top bar has Search and Settings actions.
- **Search** (`Route.Search`) → real `SearchScreen.kt`. Reached via
  Home's Search button. Forwards to Player via
  `PendingPlaybackHolder` + `Route.Player.path`.
- **Player** (`Route.Player`) → real `PlayerScreen.kt`. Reads
  `PlaybackRequest` from `PendingPlaybackHolder`; routes back to Home
  (with `popUpTo` clearing itself off the stack) if nothing is pending.
- **Settings** (`Route.Settings`) → real `SettingsScreen.kt`. Reached
  via Home's Settings button. Leaf destination (no further forward
  navigation from it). Profile list (switch/create/rename/delete) +
  full preference editor for all four `UserProfile` groups (playback,
  subtitles, search, theme).

No `Route` entries exist beyond these four. Adding an entry without a
real destination is deliberately avoided (would be dead code implying
more is wired than actually is).

## Known, Deliberate Limitations (documented in code, not silently
worked around)

- **HomeScreen Continue Watching rows are not tappable to resume
  playback.** `WatchedItem` only carries `mediaId` + progress/episode
  context, never a full `Media` object. `PlaybackRequest` requires a
  full `Media`. No Media cache/lookup layer exists yet (Next Steps #1,
  now the top item) to turn a bare `mediaId` back into a `Media`. Rows
  show mediaId + progress % only, with a Remove action. Progress now
  persists correctly across app restarts as of Session 24 — this
  limitation is purely about tap-to-resume UI, not data persistence.
- **SearchScreen TV_SHOW results are non-interactive** (no
  episode-picker yet) — from Session 22, still true.
- **SearchScreen playback always uses Smart Defaults**
  (`preferredSource = null`, no stream-candidate picker) — from
  Session 22, still true.
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
  blank-name case.
- **NEW (Session 24): durationMs stored in Continue Watching is not
  yet consumed for anything.** It is correctly captured
  (`ExoPlayer.duration`, threaded through `PlayerViewModel` →
  `SavePlaybackPositionUseCase` → `PlaybackRepository.saveProgress()` →
  `ContinueWatchingEntity.durationMs`) and persists correctly, but
  nothing yet calculates a completion percentage from it or uses it to
  drive `markAsCompleted()`'s ~90% threshold — `markAsCompleted()` is a
  separate method that must still be called explicitly by a caller
  that isn't wired up yet. `ExoPlayer.duration` can also report
  `C.TIME_UNSET` before a stream has buffered enough to know its
  length; this is stored as-is (see `PlayerViewModel.onPlayerStateChanged`
  doc comment) and would need handling once duration is actually
  consumed for a calculation.

## Session 24 — What Was Done

**Continue Watching persistence gap closed** (was Next Steps #1 at the
top of this session). Independently CI-verified: commit `ad1a5d98`
("update"), CI run `https://github.com/diaviloai/Onedebrid/actions/runs/32076694359` —
**success**. All 7 touched files verified byte-for-byte against
`raw.githubusercontent.com` (and cross-checked against a fresh tarball
pull) before this session was treated as done.

### The problem

`SavePlaybackPositionUseCase` only ever called
`SessionRepository.updatePlaybackPosition()` — updating the in-memory
`SessionState` only. It never called `PlaybackRepository.saveProgress()`,
the method that actually writes to the Room-backed
`ContinueWatchingEntity` table. That Room-side plumbing
(`saveProgress()`, `ContinueWatchingDao.upsertProgress()`, the entity
itself) was already correct and had been since an earlier session — it
was simply never invoked. Continue Watching progress did not survive an
app restart before this fix.

### Root-cause read-through (done before writing anything, per standing
practice)

Read `SavePlaybackPositionUseCase.kt`, `PlaybackRepository.kt` /
`PlaybackRepositoryImpl.kt`, `SessionRepository.kt` /
`SessionRepositoryImpl.kt`, `SessionState.kt`, `Episode.kt`,
`PlayerViewModel.kt`, and `PlayerScreen.kt` directly before drafting any
change. This surfaced two things:

1. `SessionRepository` had no synchronous way to read the current
   session — only `observeSession(): Flow<SessionState>`. Needed a
   snapshot read so `SavePlaybackPositionUseCase` could grab
   `profileId`/`mediaId`/`episodeId` without collecting a Flow on every
   position-save tick.
2. **A second, separate pre-existing bug**, found while reading
   `PlaybackRepositoryImpl.saveProgress()`: it built
   `ContinueWatchingEntity` without ever setting `seasonNumber` /
   `episodeNumber`, even though the entity has both columns and
   `HomeScreen` reads them off `WatchedItem`. This meant TV episode
   Continue Watching rows would always have shown null season/episode,
   independent of the main persistence gap. Confirmed with Dia
   explicitly before fixing (not assumed) — decision was to fix it in
   the same session since it was directly adjacent and low-risk.
3. No domain model anywhere carries media duration — `ExoPlayer.duration`
   (read in `PlayerScreen.kt`) is the only source of it. This meant
   `durationMs` had to be threaded as a new parameter through
   `PlayerViewModel.onPlayerStateChanged()` and its ticker, not read
   from session state like the other fields.

### Design decisions made explicitly with Dia before writing code

- **Session snapshot approach:** added `SessionRepository.getCurrentSession(): SessionState?`
  (backed by the existing `MutableStateFlow`'s `.value`) rather than
  having the use case do `observeSession().first()` on every call —
  chosen for simplicity and to avoid flow-collection overhead on a
  5-second ticker.
- **seasonNumber/episodeNumber fix scope:** confirmed to fix it now
  rather than deferring to a future error-model-style cleanup pass,
  since the entity columns already existed and the fix was small and
  isolated to `PlaybackRepository`/`PlaybackRepositoryImpl`.

### Files changed (all given as complete file contents, one at a time,
per standard workflow — no file in this batch was large enough to need
clipboard chunking)

1. **`SessionRepository.kt`** — added
   `fun getCurrentSession(): SessionState?`.
2. **`SessionRepositoryImpl.kt`** — implemented as `_session.value`.
3. **`PlaybackRepository.kt`** — `saveProgress()` gained `seasonNumber: Int?`
   and `episodeNumber: Int?` parameters (matching the existing
   `recordPlayed()` pattern for consistency).
4. **`PlaybackRepositoryImpl.kt`** — `saveProgress()` now passes the two
   new parameters through into `ContinueWatchingEntity`.
5. **`SavePlaybackPositionUseCase.kt`** — signature changed from
   `invoke(positionMs: Long)` to `invoke(positionMs: Long, durationMs: Long)`.
   Body now does two writes: the existing in-memory
   `SessionRepository.updatePlaybackPosition()` call, then reads
   `getCurrentSession()` and — if a session and an active `playback` both
   exist — calls `PlaybackRepository.saveProgress()` with
   `profileId`/`mediaId`/`episodeId`/`seasonNumber`/`episodeNumber` all
   pulled from the session snapshot, plus the passed-in `positionMs`/
   `durationMs`. If there's no active playback session, only the (already
   no-op-safe) in-memory update runs — the Room write is skipped since
   there's nothing to attach it to.
6. **`PlayerViewModel.kt`** — `onPlayerStateChanged()` signature changed
   from `(newState, positionMs)` to `(newState, positionMs, durationMs)`.
   Added a `lastKnownDurationMs` field alongside the existing
   `lastKnownPositionMs`, cached the same way, read by the periodic
   ticker. Both the ticker and the immediate PAUSED/ENDED save now pass
   `durationMs` through to `SavePlaybackPositionUseCase`.
7. **`PlayerScreen.kt`** — all three `Player.Listener` callback sites
   (`onPlaybackStateChanged`, `onIsPlayingChanged`, `onPlayerError`) now
   pass `exoPlayer.duration` as the new third argument. One stale doc
   comment (old two-arg signature mentioned in the class doc) was also
   corrected while in the file.

### What this does NOT yet do

Storing `durationMs` correctly does not by itself give completion
percentage or automatic `markAsCompleted()` calling — see the new Known
Limitations entry above. That remains open, not assumed solved. 
## Verification Method (standing practice, reconfirmed this session)

`raw.githubusercontent.com` single-file pulls plus direct byte/line
comparison against what was given, for every file, every push, no
exceptions. This session additionally cross-checked every
`raw.githubusercontent.com` pull against a fresh full-repo tarball pull
(`codeload.github.com`) to positively rule out CDN lag before treating
any file as verified — both layers agreed on all 7 files, first try, no
truncation or mismatch found this session.

For CI status: checked via

    curl -sL "https://api.github.com/repos/diaviloai/Onedebrid/actions/runs?per_page=3" \
      -H "Accept: application/vnd.github+json"

which was reachable directly this session (no rate-limit fallback to
Dia-supplied URLs needed). A run must show `"conclusion": "success"`
(API) or "Status: Success" (web view) before any file or session is
declared done — a "pushed" report from Dia is a trigger to go verify,
not a substitute for verifying.

## Key Learnings & Principles (cumulative, all still in force)

- **Read actual files before writing any code** — non-negotiable.
  Sessions 14, 16, 22, and 23 all had cases where this mattered
  (failures avoided or bugs caught by doing it). Session 24's
  seasonNumber/episodeNumber catch is another instance: reading
  `PlaybackRepositoryImpl.saveProgress()` directly, rather than assuming
  it was complete because it existed and looked plausible, surfaced a
  real gap before any code was written against it.
- **Grep build.gradle.kts / libs.versions.toml before importing
  anything from a library not yet used elsewhere in the codebase** —
  even common-seeming APIs may not have their artifact declared.
- **Don't declare a session or a file "done" without an actual CI
  result** — checked via the GitHub Actions API or a direct run/job URL
  fetch, not inferred from "build clean" or "pushed" alone.
- **When a paste truncates at a consistent point across retries, that's
  a signal to chunk the paste, not retry it unchanged.** (Session 23.)
  Session 24 had no files large enough to risk this (largest was
  `PlayerScreen.kt` at 302 lines, comfortably under the ~450-500 line
  risk zone), but the threshold and mitigation remain in force for
  future sessions.
- **`@Composable` functions are only callable from other `@Composable`
  functions** — not from `LaunchedEffect`, coroutine scopes, or other
  suspend contexts. (Session 23.)
- **currentsprint.md on GitHub is the authoritative completion
  record** — project file copies are a convenience cache only.
- **Flow collection:** always `flow.onEach{}.launchIn(viewModelScope)`,
  never `viewModelScope.launch { flow.collect() }`.
- **PlaybackState naming collision:** `CoordinatorState` (sealed
  interface, PlaybackCoordinator.kt) vs `PlayerLifecycleState` (enum,
  SessionState.kt) — resolved via import aliases. Reuse these exact
  names if a file needs both types again.
- **onCleared() cannot reliably run suspend work** — fix belongs in
  Compose screens via `DisposableEffect(Unit).onDispose`, as
  PlayerScreen.kt does.
- **ExoPlayer instance belongs in the Compose screen, not the
  ViewModel.** Session 24 reaffirmed this: `durationMs`, like
  `positionMs` before it, had to be threaded up from `PlayerScreen.kt`
  through `PlayerViewModel` rather than looked up anywhere else,
  because ExoPlayer is the only source of it and only the screen holds
  the instance.
- **Jetpack Navigation Compose cannot pass domain objects as nav
  args** — PendingPlaybackHolder singleton is the workaround; does not
  survive process death (documented in the file).
- **MutableStateFlow.update{} does not resolve in this build
  environment** — use direct `_flow.value = _flow.value?.copy(...)`
  assignment instead.
- **A synchronous snapshot read alongside an existing Flow-based
  observe method is a reasonable, low-risk addition when a caller only
  needs a one-off value** (`SessionRepository.getCurrentSession()`,
  Session 24) — simpler than collecting `.first()` on a hot path like a
  5-second ticker, and doesn't require changing the existing
  `observeSession()` contract for other callers.
- **CI/build environment:** GitHub Actions runners are fresh (no local
  build cache); `gradle-wrapper.jar` handled via
  `gradle/actions/setup-gradle@v3`.
- **Verify pushed file contents directly, don't infer from build
  status** — re-pull and diff against GitHub before treating any edit
  as landed. Session 24 additionally cross-checked
  `raw.githubusercontent.com` against a fresh tarball pull for the same
  reason — belt and suspenders, cheap to do, catches CDN-lag false
  negatives/positives.
- **A use case/coordinator/repository chain returning a singular type
  where the domain clearly implies a collection is worth checking
  explicitly before building a screen against it** (Session 22
  SearchResult example).
- **When a repository method's implementation silently drops fields
  the entity/domain model actually has, that's worth fixing as part of
  whatever adjacent work surfaced it, not just noting for later** —
  Session 24's seasonNumber/episodeNumber fix was small, low-risk, and
  directly adjacent to the work already being done, so it was resolved
  immediately (with Dia's explicit sign-off) rather than deferred.

## Next Steps, In Order

1. **Media cache/lookup layer.** Not yet started. Now the top
   priority — would let the nav graph pass a bare `mediaId` as a real
   nav arg instead of `PendingPlaybackHolder`, fix the process-death
   gap, let SearchScreen route TV_SHOW results to a real episode-picker
   instead of marking them unsupported, and let HomeScreen's Continue
   Watching rows become tappable/resumable and show real titles instead
   of raw `mediaId` strings. This is a substantial, foundational piece
   of work — likely worth its own dedicated session(s) once started.
2. **Episode-picker screen / Details screen.** Not started. Needed to
   lift SearchScreen's TV_SHOW limitation (Session 22) and would also
   give Continue Watching rows somewhere meaningful to navigate once
   the Media lookup layer exists.
3. **Stream-candidate picker UI.** Not started. Needed to lift
   SearchScreen's Smart-Defaults-only limitation (Session 22).
4. **`AppError.ValidationError` case.** Would let
   CreateProfileUseCase/UpdateProfileUseCase's blank-name validation
   and DeleteProfileUseCase's delete-active-profile rejection surface
   proper, distinct user-facing messages instead of both falling
   through SettingsScreen's generic `LocalStorageError` message. Low
   urgency (the UI already avoids the delete-active case proactively)
   but worth doing whenever the broader error model gets a review pass.
5. **Completion-percentage / markAsCompleted wiring.** Not started, new
   as of Session 24. `durationMs` is now correctly persisted but
   nothing calculates a completion percentage from it or calls
   `PlaybackRepository.markAsCompleted()` automatically at a threshold
   (~90%, per its doc comment). Worth scoping alongside or shortly
   after the Media cache/lookup layer, since both touch Continue
   Watching's read side.
6. **SettingsScreen preference-write debounce/Save-step
   reconsideration** — only if it turns out to feel laggy on-device;
   not a confirmed problem, just flagged for revisit (see Known
   Limitations above).

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
- **NEW (Session 24):** `PlaybackRepositoryImpl`'s `markAsCompleted()`
  block (lines ~76-85 as of this session) has inconsistent
  indentation relative to the rest of the file — pre-existing, not
  touched this session to avoid an unrelated risky diff on a file
  already being edited for the saveProgress() change. Cosmetic only;
  clean up whenever that method is next touched for functional
  reasons.
- **NEW (Session 24):** `ExoPlayer.duration` can report `C.TIME_UNSET`
  before a stream has buffered enough to know its length. This is
  currently stored as-is in `ContinueWatchingEntity.durationMs`
  without correction (see `PlayerViewModel.onPlayerStateChanged`'s doc
  comment). Not a problem today since nothing reads durationMs for a
  calculation yet, but will need explicit handling once Next Steps #5
  (completion-percentage wiring) is scoped.

At the end of the next session, update currentsprint.md (full file, in
a code block) and verify it directly against
raw.githubusercontent.com/diaviloai/Onedebrid/main/currentsprint.md
before treating the session as closed — and do not treat any session
as closed without an actual green CI result for whatever was last
pushed.