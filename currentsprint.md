# Current Sprint Document: OneDebrid

## Active Phase Summary
- **Current Milestone**: Core System Architecture & Coordinator Layer Verification
- **Focus Area**: Unit Testing, Test Suite Stabilization, Coroutine Lifecycle Management

---

## Session Changelog & Completed Tasks

### Test Suite & Architecture Stabilization
- **Coordinator Unit Testing**: Added and stabilized comprehensive unit tests for `SearchCoordinatorTest` and `SessionCoordinatorTest`.
- **Interface Alignment**: Resolved build errors by aligning unit test fakes with production contracts (`MediaRepository`, `SearchRepository`, `ProfileRepository`, and `AppError`).
- **Coroutine Scope Fixes**: Resolved `UncompletedCoroutinesError` and `JobCancellationException` by isolating long-running background collection flows inside `SessionCoordinator` and `SearchCoordinator` using child `SupervisorJob` instances (`CoroutineScope(testDispatcher + supervisorJob)`) rather than executing directly on `testScope`.
- **Build Status**: `./gradlew testDebugUnitTest` is fully passing green.

---

## Active Architecture & Known Constraints

### Repository Contracts
- **`MediaRepository`**: Supports `getMediaDetails`, `getEpisodes`, `getEpisodeById`, `resolveStream`, `checkCacheStatus`, `search`, and `searchStreamsByMedia`.
- **`SearchRepository`**: Exposes reactive search history observation via `observeSearchHistory(profileId: String): Flow<List<String>>`, along with `addSearchQuery`, `removeSearchQuery`, and `clearSearchHistory`.
- **`AppError` Model**: Utilizes structured sealed interface instances (`NoCachedStreamAvailable`, `StreamResolutionFailed`, `NotAuthenticated`, `NoNetworkConnection`, `AllProvidersUnavailable`, `LocalStorageError`, `Unknown`).

---

## Open Tasks & Next Steps

1. **Expand Coordinator Test Coverage**:
   - Add unit tests for `PlaybackCoordinatorTest` and `NavigationCoordinatorTest` using the established `SupervisorJob` testing pattern.
2. **ViewModel Layer Wiring**:
   - Verify ViewModel integration with stabilized Coordinators (`SearchViewModel`, `PlayerViewModel`, `SessionViewModel`).
3. **Provider Integration**:
   - Transition stubbed media and search providers to real implementations (e.g., TMDB, Torrentio, Real-Debrid API integration).
4. **AppError Standardizations**:
   - Conduct planned error-model review (e.g., introducing `AppError.ValidationError` for edge-case ID mismatches).
