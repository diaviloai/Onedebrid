# OneDebrid — Current Sprint

## Status

Implementation in progress. Architectural design phase complete.

This file is fully rewritten each session — it reflects actual current
code state, verified by pulling the repo and reading files directly, not
appended to informally.

Build verification: project compiles cleanly as of Session 23's close,
confirmed via GitHub Actions CI on commit `d66318a3` ("update
SettingsScreen.kt"), checked directly via the GitHub Actions API
(`api.github.com/repos/diaviloai/Onedebrid/actions/runs`) — status
`completed`, conclusion `success`. This is the fourth CI result checked
this session; the first three (SearchScreen→NavGraph wiring succeeded
cleanly, HomeScreen succeeded cleanly, but SettingsScreen.kt failed twice
before succeeding) are detailed below in "Session 23 — Build Failures &
Fixes," since the failure pattern itself is a real lesson for future
sessions, not just a footnote.

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
    │       ├── PlaybackRequest.kt
    │       ├── SearchResult.kt
    │       ├── SessionState.kt
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
        ├── GetContinueWatchingUseCase.kt
        ├── RemoveFromContinueWatchingUseCase.kt
        ├── SearchMediaUseCase.kt
        ├── SwitchProfileUseCase.kt
        ├── UpdateProfileUseCase.kt
        └── (others per earlier sessions)

(This tree reflects what's been directly read/touched across sessions,
not a guaranteed exhaustive listing — see the repo itself for ground
truth on files not mentioned in recent session notes.)

## App Navigation State (as of Session 23)

All four primary screens now exist and are wired into a single
`NavGraph.kt`:

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
  full `Media`. No Media cache/lookup layer exists yet (Next Steps #2,
  in chunk 2) to turn a bare `mediaId` back into a `Media`. Rows show
  mediaId + progress % only, with a Remove action.
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

## Session 23 — What Was Done

All three items completed and independently CI-verified (checked via
`api.github.com/repos/diaviloai/Onedebrid/actions/runs`, not just
inferred from "build clean" reports — see rationale in "Verification
Method" in chunk 2 below).

### 1. Wired SearchScreen into NavGraph.kt

Added `Route.Search`. Decided with Dia (explicit choice, not assumed):
Home stays the start destination; a "Search" button was added to the
then-placeholder Home route to make Search reachable. `NavGraph.kt`
rewritten in full. Verified byte-for-byte against
`raw.githubusercontent.com`. CI run
`https://github.com/diaviloai/Onedebrid/actions/runs/31959197234` —
**success**.

### 2. HomeScreen.kt (new) — replaces NavGraph's inline placeholder

New file, wired into `NavGraph.kt` in place of the placeholder Composable
used since Session 21. Shows Continue Watching via `HomeViewModel`
(already existed, no changes needed to it). Loading/empty/list states.
Rows show `mediaId` + a computed progress percentage (new
`home_continue_watching_progress` string resource, `%1$d%% watched`
format) with a Remove action. Deliberately **not** tappable — see Known
Limitations above for why (`WatchedItem` has no `Media`). `strings.xml`
updated (Home section added). Verified byte-for-byte. CI run
`https://github.com/diaviloai/Onedebrid/actions/runs/31960490550` —
**success**.

### 3. SettingsScreen.kt (new) — full preferences editor

Scope decided explicitly with Dia: full editor for all four
`UserProfile` preference groups (playback, subtitles, search, theme) in
this session, not deferred. New file. Two responsibilities:

- **Profile management:** list all profiles, switch active, create new
  (name-entry dialog), rename (same dialog, prefilled), delete
  (disabled in the UI for the currently-active profile, since
  `DeleteProfileUseCase` rejects deleting the active profile
  server-side — UI reflects the rule rather than letting the user
  discover it via an error).
- **Preference editing**, scoped to the active profile only (no
  per-profile "select which profile you're editing" UI — Smart
  Defaults always apply to whichever profile is active). Each group
  (Playback / Subtitles / Search / Theme) is its own private composable
  section. Controls: `Switch` for booleans, a custom `DropdownField<T>`
  for enums and nullable tri-state values (`VideoQuality` excluding
  `UNKNOWN`; `SubtitleFormat?` with an explicit "No preference" entry
  standing in for `null`; `darkMode: Boolean?` as a 3-way System/On/Off
  dropdown), and a free-text `OutlinedTextField` for BCP-47 language
  codes (no language list/lookup exists in the codebase to back a
  proper picker yet).

`HomeScreen.kt` updated to add a `onNavigateToSettings` callback + a
second TopAppBar action. `NavGraph.kt` updated to add `Route.Settings`
and wire `SettingsScreen()` as a leaf destination. `strings.xml`
extended with a `Settings` section (~30 new strings) plus
`home_settings_action`.

**A real bug was caught and fixed before any of this was pasted**, not
after: `CreateProfileUseCase` / `ProfileRepositoryImpl.createProfile`
pass `UserProfile.id` straight through to Room's `@PrimaryKey` column
with zero ID-generation anywhere in that chain (confirmed by reading
`ProfileRepositoryImpl.kt` and `ProfileEntity.kt` directly before
writing the screen, per the project's standing "never write against
assumed interfaces" rule). The initial screen draft would have created
profiles with an empty-string ID. Fixed by generating
`UUID.randomUUID().toString()` in `SettingsScreen.kt` at creation time
(`java.util.UUID` — standard JDK, no new dependency risk, unlike the
Session 22 icons situation).

Icon usage was deliberately conservative: no new `Icons.Filled.*` symbol
was introduced beyond `Icons.Filled.Close` (already proven to compile
via `HomeScreen.kt` earlier in this same session). All new actions
(add/delete/rename/switch) use text buttons instead of icons, to avoid
any repeat of Session 22's "assumed an icon was available without
checking the declared dependency" failure mode.
## Session 23 — Build Failures & Fixes (SettingsScreen.kt)

Three build attempts before a green result. Documented in full because
the failure pattern — not just the final fix — is the actual lesson for
future sessions.

**Attempt 1 — paste truncation, not a code defect.** The full
~450-line file was given as one code block. What actually landed on
GitHub was 531 lines, cut off mid-identifier
(`preferences.preferredContentLang` with no closing) partway through
the final function. This produced misleading-looking compiler errors
(`Unresolved reference 'settingsErrorMessage'`,
`Unresolved reference 'ThemePreferencesSection'`) that look like
missing-definition bugs but were actually "the definitions exist, they
just never arrived on GitHub." Diagnosed by pulling the live file via
`raw.githubusercontent.com` and reading the actual tail of the file
directly — confirmed truncation, not a code issue, before proposing any
fix. Dia confirmed the copy/paste consistently truncated at the same
spot across multiple attempts, indicating a Spck clipboard length
limit rather than a one-off mistake.

**Fix for Attempt 1:** the file was split into two sequential paste
chunks at a safe boundary (end of `PreferencesSection`, before the
shared UI helper composables), each given as its own complete code
block with explicit instructions on where the split was and that the
first chunk intentionally has no closing brace. This landed completely
(571 lines, verified) on the next push.

**Attempt 2 — a real bug, not a paste issue.** With the file now
complete, CI failed with
`@Composable invocations can only happen from the context of a
@Composable function` at the line calling `settingsErrorMessage(...)`
from inside `LaunchedEffect { }`. Root cause: `settingsErrorMessage`
was declared `@Composable` (because it internally calls
`stringResource()`), but `LaunchedEffect`'s block is a suspend lambda,
not composable context — you cannot call a `@Composable` function from
inside it. **Fix:** stopped resolving the error to a `String` inside
`LaunchedEffect`; instead store the raw `AppError` in state
(`pendingError: AppError?` replacing the old `errorMessage: String?`)
and only call `settingsErrorMessage()` at the point where it's
actually rendered — inside `AlertDialog`'s `text = { ... }` lambda,
which is valid composable context. This was given as three small,
precisely-located find/replace edits rather than a full-file
replacement, specifically to avoid re-triggering the Attempt 1
clipboard issue on an already-large file.

**Attempt 3 — one of the three edits didn't land.** CI still failed:
`Unresolved reference 'pendingError'` at the exact lines that should
have declared and used it. Pulled the live file and diffed line-by-line
rather than guessing which edit was missing. Found: 2 of 3 edits had
landed correctly; the state *declaration* line
(`var errorMessage by remember { mutableStateOf<String?>(null) }`) had
not been changed to `var pendingError by remember { mutableStateOf<AppError?>(null) }` —
every other line already referenced the not-yet-declared `pendingError`.
**Fix:** the single missing line, given precisely, no ambiguity. Landed
and confirmed via CI on the next push.

**Generalizable lessons from this sequence:**

- When a paste consistently truncates at the same point across
  multiple attempts, that's a length/buffer signal — switch to
  sequential chunked pastes at a safe syntactic boundary rather than
  retrying the same full-file paste again.
- When CI reports "unresolved reference" for something that looks like
  it should obviously be defined, check whether the file actually
  landed completely on GitHub *before* assuming the code itself is
  wrong — Attempt 1's errors were paste-truncation symptoms wearing a
  missing-definition costume.
- `@Composable` functions cannot be called from `LaunchedEffect`,
  `rememberCoroutineScope().launch { }`, `viewModelScope`, or any other
  suspend/coroutine context — only from other `@Composable` functions.
  If a helper needs `stringResource()`, either call it from composable
  context and pass the resolved `String` in, or (as done here) delay
  resolution until the value is actually rendered.
- For small, targeted fixes to an already-large file, giving 2–3
  precise find/replace edits rather than a full-file re-paste both
  avoids re-triggering clipboard truncation and makes it easier to spot
  exactly which edit didn't land, if any didn't.
- **Verify every single line of a multi-part edit landed** — don't
  assume 3-for-3 just because CI ran again; 2 of 3 landing correctly
  still produces a full build failure, and the fastest diagnosis is
  pulling the live file and diffing against exactly what was given,
  line by line if needed.

## Verification Method (standing practice, reconfirmed this session)

`raw.githubusercontent.com` single-file pulls plus direct byte/line
comparison against what was given, for every file, every push, no
exceptions — this caught the Attempt 1 truncation immediately rather
than after a confusing round of guessing at "code bugs" that didn't
exist.

For CI status specifically: `api.github.com`'s unauthenticated rate
limit was hit twice this session (shared IP pool in this environment) —
when that happens, the fallback is asking Dia for the direct Actions
run URL or job URL and fetching it via `web_fetch`, which worked
reliably both times. When the API is reachable directly (confirmed
working again by end of session), it's preferable since it doesn't
depend on Dia copying a URL — checked via:

    curl -sL "https://api.github.com/repos/diaviloai/Onedebrid/actions/runs?per_page=3" \
      -H "Accept: application/vnd.github+json"

Both methods are legitimate; try the API first, fall back to asking for
the run URL if rate-limited. Either way, a run must show
`"conclusion": "success"` (API) or "Status: Success" (web view) before
any file or session is declared done — a "build clean" or "pushed"
report from Dia is a trigger to go verify, not a substitute for
verifying.

## Key Learnings & Principles (cumulative, all still in force)

- **Read actual files before writing any code** — non-negotiable.
  Sessions 14, 16, and 22 all had build failures traceable to writing
  against assumed interfaces. Session 23's UUID-generation catch
  (ProfileRepositoryImpl) is a case of this working correctly:
  reading the actual repository implementation before writing the
  screen caught a real bug before it was ever pasted.
- **Grep build.gradle.kts / libs.versions.toml before importing
  anything from a library not yet used elsewhere in the codebase** —
  even common-seeming APIs may not have their artifact declared.
  Session 23 applied this conservatively for Settings: rather than
  research whether additional `Icons.Filled.*` symbols beyond `Close`
  were safe, the screen simply avoided introducing any new icon
  symbols at all, using text buttons instead.
- **Don't declare a session or a file "done" without an actual CI
  result** — checked via the GitHub Actions API or a direct run/job
  URL fetch, not inferred from "build clean" or "pushed" alone. This
  caught nothing new this session (Dia's reports were accurate every
  time), but remains the standing verification step regardless.
- **When a paste truncates at a consistent point across retries,
  that's a signal to chunk the paste, not retry it unchanged.** New
  lesson from Session 23 — see "Build Failures & Fixes" above for full
  detail.
- **`@Composable` functions are only callable from other `@Composable`
  functions** — not from `LaunchedEffect`, coroutine scopes, or other
  suspend contexts. New lesson from Session 23.
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
  ViewModel.**
- **Jetpack Navigation Compose cannot pass domain objects as nav
  args** — PendingPlaybackHolder singleton is the workaround; does not
  survive process death (documented in the file).
- **MutableStateFlow.update{} does not resolve in this build
  environment** — use direct `_flow.value = _flow.value?.copy(...)`
  assignment instead.
- **CI/build environment:** GitHub Actions runners are fresh (no local
  build cache); `gradle-wrapper.jar` handled via
  `gradle/actions/setup-gradle@v3`.
- **Verify pushed file contents directly, don't infer from build
  status** — re-pull and diff against GitHub before treating any edit
  as landed.
- **A use case/coordinator/repository chain returning a singular type
  where the domain clearly implies a collection is worth checking
  explicitly before building a screen against it** (Session 22
  SearchResult example).

## Next Steps, In Order

1. **Continue Watching persistence gap.** `SavePlaybackPositionUseCase`
   currently only updates in-memory `SessionState`, not the Room-backed
   `ContinueWatchingEntity` table. Not persisted end-to-end yet.
2. **Media cache/lookup layer.** Not yet started. Would let the nav
   graph pass a bare `mediaId` as a real nav arg instead of
   `PendingPlaybackHolder`, fix the process-death gap, let SearchScreen
   route TV_SHOW results to a real episode-picker instead of marking
   them unsupported, and — newly relevant after Session 23 — let
   HomeScreen's Continue Watching rows become tappable/resumable and
   show real titles instead of raw `mediaId` strings.
3. **Episode-picker screen / Details screen.** Not started. Needed to
   lift SearchScreen's TV_SHOW limitation (Session 22) and would also
   give Continue Watching rows somewhere meaningful to navigate once
   the Media lookup layer exists.
4. **Stream-candidate picker UI.** Not started. Needed to lift
   SearchScreen's Smart-Defaults-only limitation (Session 22).
5. **`AppError.ValidationError` case.** Would let
   CreateProfileUseCase/UpdateProfileUseCase's blank-name validation
   and DeleteProfileUseCase's delete-active-profile rejection surface
   proper, distinct user-facing messages instead of both falling
   through SettingsScreen's generic `LocalStorageError` message. Low
   urgency (the UI already avoids the delete-active case proactively)
   but worth doing whenever the broader error model gets a review
   pass.
6. **SettingsScreen preference-write debounce/Save-step reconsideration**
   — only if it turns out to feel laggy on-device; not a confirmed
   problem, just flagged for revisit (see Known Limitations above).

## Open TODOs (carried forward, unchanged unless noted)

- App icon: placeholder system drawable in AndroidManifest.xml
- SearchRepository.updateSearchSession uses `Map<String, String>` for
  filters; revisit if SearchFilters gets promoted to a domain model
- AppError has no ValidationError case; CreateProfileUseCase and
  UpdateProfileUseCase use LocalStorageError(IllegalArgumentException)
  for blank name validation — semantically incorrect; revisit when the
  error model gets a review pass. **Now also affects
  DeleteProfileUseCase's active-profile rejection, surfaced via
  SettingsScreen — see Next Steps #5.**
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
- **NEW (Session 23):** `DropdownField<T>` in SettingsScreen.kt is a
  simple `TextButton` + `DropdownMenu` implementation, not Material
  3's `ExposedDropdownMenuBox`. Functional, but not the "official"
  M3 dropdown pattern (which has its own anchor/positioning
  requirements). Fine as-is; revisit only if visual polish on
  Settings becomes a priority.
- **NEW (Session 23):** No language list/picker exists — subtitle,
  audio, and content-language preferences are all free-text BCP-47
  code entry fields with a hint string, not validated or
  autocompleted. Revisit if a proper language picker becomes worth
  building.

At the end of the next session, update currentsprint.md (full file, in
a code block) and verify it directly against
raw.githubusercontent.com/diaviloai/Onedebrid/main/currentsprint.md
before treating the session as closed — and do not treat any session
as closed without an actual green CI result for whatever was last
pushed.