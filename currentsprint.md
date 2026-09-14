# Current Sprint Document: OneDebrid

## 1. Objectives & Focus
- Ensure robust end-to-end compilation across navigation, viewmodels, and screens.
- Enhance UI/UX simplicity across media details and stream selection workflows.

## 2. Completed Tasks
- [x] **Navigation & Build Alignment**: Standardized `PlayerNavArgs` and updated `NavGraph.kt` routing parameters to fix build failures across `HomeScreen`, `DetailsScreen`, and `PlayerScreen`.
- [x] **DetailsScreen Error Handling**: Mapped `AppError` domain types safely in `DetailsScreen.kt` and eliminated missing string resource references.
- [x] **Stream Picker UI Enhancements**: Replaced basic button list in `StreamPickerBottomSheet` with styled `StreamCandidateRow` cards featuring quality badges (4K, 1080p, 720p, SD), formatted file sizes, and clean fallback states for empty streams.
- [x] **CI Verification**: Verified green builds for `assembleDebug` on GitHub Actions pipeline.

## 3. Active / Next Up Backlog
- [ ] **Player Screen Integration**: Verify `PlayerScreen` handling of `PlayerNavArgs` during playback initialization, buffering states, and error handling.
- [ ] **Player Lifecycle & Progress Sync**: Ensure playback session teardown and position tracking save cleanly on back navigation or session end.
- [ ] **String Resources Cleanup**: Audit hardcoded UI strings and migrate to `strings.xml`.

## 4. Architectural Guiding Principles
- **UI/UX Simplicity**: Keep stream picking and media navigation as simple and clear as possible for the user.
- **Domain Decoupling**: ViewModels and Navigation accept domain types (`MediaType`, `StreamCandidate`, `PlayerNavArgs`) rather than pre-serialized raw strings.
