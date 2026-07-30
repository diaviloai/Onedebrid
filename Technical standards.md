OneForAll
Technical Standards
Version: 0.1 (Draft)
Purpose
This document defines the implementation guidelines, coding conventions, architectural standards, and technical constraints for OneForAll.
Its goal is to ensure consistency, maintainability, performance, and long-term quality across the codebase while enforcing the design principles established in higher-level architectural specifications.
All implementation code must comply with these technical standards.
Tech Stack & Dependencies
To maintain a lean footprint and minimize build complexity, OneForAll restricts external dependencies to core Android/Kotlin libraries maintained by Google and Jetpack, alongside industry-standard open-source networking and serialization tools.
CategoryStandard Tool / LibraryJustification
LanguageKotlin (Latest Stable)Platform standard; built-in asynchronous support via Coroutines.
UI FrameworkJetpack ComposeDeclarative UI, dynamic styling (Material You), robust state management.
Design SystemMaterial 3 (Material You)First-class Android UI system, adaptive themes, accessible design token support.
Architecture / DIHilt (Dagger)Standardized, compile-time safe dependency injection for Android lifecycle components.
Asynchronous ExecutionKotlin Coroutines & FlowAsynchronous execution model, structured concurrency, reactive data streams.
PersistenceRoomType-safe SQLite abstraction, explicit schema migration support, native Flow integration.
NetworkingOkHttp + RetrofitProduction-ready HTTP client, interceptor chain for auth/rate limits, connection pooling.
Serializationkotlinx.serializationNative Kotlin reflection-free JSON parsing with high performance and type safety.
Media PlaybackJetpack Media3 (ExoPlayer)Canonical 

Android media engine with native subtitle, audio, and stream unrestricting capabilities.
Image LoadingCoilCompose-first image loading library built specifically for Kotlin Coroutines.
Architectural Consistency & Boundaries
OneForAll enforces a strict unidirectional layer hierarchy as defined in Internal API Specification v0.1. Dependencies point strictly downward.
flowchart TD
    subgraph UI_Layer [UI Layer]
        UI[Compose Screens] --> VM[ViewModels]
    end

    subgraph App_Logic [Application Logic Layer]
        VM --> UC[Use Cases]
        VM --> CO[Coordinators]
        UC --> CO
        UC --> REPO[Repositories]
        CO --> REPO
    end

    subgraph Data_Layer [Data & Provider Layer]
        REPO --> DB[(Room Database)]
        REPO --> PI[Provider Interfaces]
        PI --> PI_IMP[Provider Implementations]
        PI_IMP --> EXT[External APIs / Web Services]
    end
    

Layer Rules
UI Layer (:ui):
Contains Compose screens, components, dynamic color handlers, and ViewModels.
Boundary Rule: Components in the UI layer must never import or reference database entities, network DTOs, provider implementations, or repositories directly. ViewModels communicate strictly with Use Cases and Coordinators using Domain Models.
Application Logic Layer (:domain / :core):
Contains Use Cases, Coordinators, Repositories (contracts and implementations), and Domain Models.
Boundary Rule: Zero dependencies on android.* framework classes (with explicit exceptions for Android lifecycle components where strictly necessary, such as AndroidX ViewModel). Core business logic must be platform-agnostic and unit-testable on JVM.
Provider Layer (:provider):
Implements DebridProvider, MetadataProvider, SearchProvider, and SubtitleProvider.
Boundary Rule: All raw API responses (DTOs marked with @Serializable) must be caught and mapped to internal Domain Models or mapped into ProviderError types within the provider implementation itself. DTOs must never leak into Repositories or Use Cases.
State Management & Concurrency Standards
Unidirectional Data Flow (UDF)
Every screen UI state is modeled as an immutable StateFlow<UIState> exposed by a ViewModel.

// Example canonical UI State model
sealed interface ScreenUiState<out T> {
    data object Loading : ScreenUiState<Nothing>
    data class Success<out T>(val data: T) : ScreenUiState<T>
    data class Error(val errorContext: UIErrorContext) : ScreenUiState<Nothing>
}


State Exposure: ViewModels expose state using StateFlow and process user actions via discrete UIEvent calls or exposed functions.
One-Off Events: Transient actions (navigation, snackbars) are emitted via Kotlin Channel or SharedFlow exposed as a Flow (referred to as UIEffect).
Concurrency Rules
Dispatchers: Hardcoded coroutine dispatchers (e.g., Dispatchers.IO) inside Use Cases, Repositories, or ViewModels are strictly prohibited. All dispatchers must be injected via Hilt using an abstraction interface (e.g., CoroutineDispatchers) to enable deterministic unit testing.
Structured Concurrency: Asynchronous tasks must be tied to a parent CoroutineScope (viewModelScope, applicationScope). Stray jobs using GlobalScope are explicitly forbidden.
Cancellation: Long-running operations (such as multi-provider search or stream resolution) must periodically check for active cancellation (coroutineContext.ensureActive()) or wrap blocking IO calls to ensure resources are freed immediately when a user navigates away.
Technical Constraints & Android Hardware Handling
To fulfill custom constraints, hardware limitations, and mobile platform optimizations:
Lightroom Mobile & External App Interoperability:
Media processing, image selection, and photo workflows in OneForAll must respect Android-specific intent APIs (Intent.ACTION_GET_CONTENT, SAF / Storage Access Framework) without assuming system-level desktop privileges.
Android Mobile Processing Limitations:
Memory Management: High-resolution artwork images from Metadata Providers must be downsampled at the network/loader level using Coil target boundaries matching screen density before being held in memory.
Background Processing: Heavy extraction routines, bulk stream caching, or large subtitle downloads must run off the Main Thread and observe thermal and power constraints. Unrestricted background tasks must leverage Android Jetpack WorkManager.
Platform-Independent Subtitle Engine:
Subtitles (SRT, VTT, ASS) parsed by SubtitleProvider must be decoded asynchronously on IO threads into generic SubtitleTrack models before attachment to Media3. Rendering must rely on native ExoPlayer UI overlays to minimize frame dropping during video decoding.
Complexity Reduction & Tradeoff Analysis
In accordance with OneForAll's Smart Defaults philosophy and Simplicity First design principle, the architecture balances flexibility against implementation overhead.
1. Unified Domain Model vs. System-Specific Entities
Tradeoff Analyzed: Creating separate representation models for search results, metadata entities, and playback streams versus using a single unified Media Domain Model.
Decision: Adopt a single canonical Media domain object enriched progressively through optional extensions/metadata decorators.
Rationale: Eliminates object mapping boilerplates across system boundaries and prevents UI state inconsistencies during transition between search, details, and playback.
2. Provider Aggregator / Parallel Race Strategy vs. Sequential Processing
Tradeoff Analyzed: Sequential fallback queries vs. concurrent provider racing.
Decision: Use parallel racing (e.g., async { ... }.awaitAll()) exclusively for latency-sensitive, read-only search operations (Subtitle & Search Providers). Use priority fallback for state-modifying or quota-limited requests (Debrid unrestrict API).
Rationale: Prevents unnecessary Debrid API quota consumption while ensuring instant UI presentation for content search and subtitle discovery.
3. Room Database vs. Key-Value / JSON Storage for Caching
Tradeoff Analyzed: Using raw JSON string storage or SharedPreferences/DataStore vs. relational Room tables for cached metadata and search history.
Decision: Use Room for entity relations (e.g., Profiles, Continue Watching, Media Cache) and Jetpack DataStore exclusively for basic application flags and Settings.
Rationale: Room guarantees schema migrations, indexed query performance, and dynamic multi-profile isolation without the performance bottlenecks of serializing massive JSON strings on the UI thread.
Verification, Testing & Error Handling
Error Handling
All external or data layer errors must map directly into structured domain models before reaching business logic. Standard exceptions must never be thrown across subsystem boundaries.

sealed interface ProviderError {
    data object AuthenticationFailed : ProviderError
    data class RateLimited(val retryAfterSeconds: Long?) : ProviderError
    data object NotFound : ProviderError
    data object ServiceUnavailable : ProviderError
    data object NetworkError : ProviderError
    data class ParsingError(val cause: Throwable) : ProviderError
}


Testing Requirements
Unit Testing:
All UseCases, Coordinators, and Repositories must achieve >80% unit test coverage.
Tests must use fake implementations for Provider Interfaces (FakeDebridProvider) rather than mock frameworks to guarantee contractual reliability.
Integration Testing:
Room Database migrations must be verified via automated Room Migration Test Rules.
Architecture Verification:
Package boundary integrity should be enforced using automated architecture tests (e.g., Konsist or ArchUnit for Kotlin) to ensure lower layers do not reference higher-level UI/ViewModel components.