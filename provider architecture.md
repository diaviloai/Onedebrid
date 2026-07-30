OneForAll
Provider Architecture
Version: 0.1 (Draft)
Purpose
This document defines the architecture, contracts, and lifecycle for external service integration within OneForAll.
The Provider Architecture abstracts external APIs—such as Debrid services, metadata databases, search indices, and subtitle repositories—behind unified application-owned interfaces.
This guarantees that external changes or new service integrations never break internal business rules or UI workflows.
Core Principles
Strict Interface Isolation: The core application never interacts directly with external HTTP endpoints, third-party SDKs, or raw API models.
Domain Normalization: All provider implementations must map external Data Transfer Objects (DTOs) into unified Domain Models before passing data up the architecture.
Resilience & Graceful Degradation: Failures in individual providers (e.g., rate limits, downtime) must fail gracefully, trigger secondary fallbacks, or report structured errors without crashing the application.
Modularity & Plugability: Adding new providers (e.g., additional Debrid services, alternative metadata providers) should require only implementing the provider interface and registering it with the Provider Registry.
Smart Defaults: Automatic selection and priority-based orchestration of providers based on active profile settings and real-time availability.
Provider Taxonomy
Providers in OneForAll are categorized into four primary functional types:
flowchart TD
    PR[Provider Registry] --> DP[Debrid Providers]
    PR --> MP[Metadata Providers]
    PR --> SP[Search Providers]
    PR --> SUBP[Subtitle Providers]

    DP --> RD[Real-Debrid Implementation]
    DP --> FD[Future Debrid Services]

    MP --> TMDB[TMDB Implementation]
    MP --> TVDB[TVDB / Trakt Implementation]

    SP --> SCR[Torrent / Indexer Implementations]

    SUBP --> OS[OpenSubtitles Implementation]
    SUBP --> SUB_EXT[External Subtitle Sources]
    
Provider Interfaces
Each provider type implements a dedicated, single-responsibility interface.
1. Debrid Provider (DebridProvider)
Responsible for all interaction with cached stream providers and cloud download services.
Core Capabilities
Account Verification: Validate user credentials, check subscription status, and monitor API quota limits.
Cache Inspection: Query hash availability across cloud storage.
Stream Unrestricting: Convert torrent/magnet links or cached hashes into direct streaming URLs.
Cloud Storage Management: View active cloud transfers and manage account storage space.
2. Metadata Provider (MetadataProvider)
Responsible for enriching basic media identifiers with human-readable information, artwork, and structured hierarchy.
Core Capabilities
Details Enrichment: Retrieve titles, synopses, ratings, release dates, genres, and artwork URLs.
Structural Hierarchy: Map TV shows to seasons and episodes.
External Mapping: Map external IDs (e.g., IMDB, TMDB, TVDB) to canonical internal media models.
Note: Metadata operations are strictly non-blocking. Media playback can start even if metadata enrichment is still loading or partially fails.
3. Search Provider (SearchProvider)
Responsible for discovering media streams, magnets, or metadata across available search engines or scrapers.
Core Capabilities
Media Querying: Search content by title, year, or external identifier.
Result Stream Parsing: Parse raw search results into standard stream candidates containing title, file size, seeders, quality, and audio/video code specs.
4. Subtitle Provider (SubtitleProvider)
Responsible for locating and fetching external subtitle files.
Core Capabilities
Subtitle Discovery: Query subtitles matching media metadata, hash, season/episode, or language preferences.
Download & Parsing: Fetch and decode subtitle formats (e.g., SRT, VTT) into standard application subtitle objects.
Orchestration & Registry Pattern
To manage multiple provider instances, OneForAll uses a central Provider Registry combined with Provider Coordinators.
Provider Registry
The Provider Registry holds all registered implementations and exposes them based on type, capability, and user priority preferences set in the active profile.
flowchart LR
    Repo[Repository] --> Orchestration[Provider Coordinator]
    Orchestration --> Registry[Provider Registry]
    Registry --> P1[Primary Provider]
    Registry --> P2[Fallback Provider]
    
Execution Strategies
When Repositories request data from providers, Coordinators execute one of three strategies:
StrategyDescriptionTypical Use Case
Priority FallbackTries the highest-priority active provider; falls back to secondary on network/auth failure.Link Unrestricting (DebridProvider)
Parallel Race / Fast ResponseQueries all configured providers simultaneously and uses the fastest valid response.Subtitle Discovery (SubtitleProvider)
Aggregator / UnionQueries multiple providers concurrently and merges/deduplicates the results.Search & Scraping (SearchProvider)
Error Handling & Normalization
Providers catch raw HTTP errors, timeouts, and JSON parsing exceptions, translating them into normalized ProviderError domain types.
flowchart TD
    HttpErr[Raw HTTP / API Error] --> Prov[Provider Implementation]
    Prov --> Catch{Error Mapping}
    Catch -->|401 / Auth Failed| AuthErr[ProviderError.AuthenticationFailed]
    Catch -->|429 / Rate Limited| RateErr[ProviderError.RateLimited]
    Catch -->|404 / Missing| NotFoundErr[ProviderError.NotFound]
    Catch -->|5xx / Outage| OutageErr[ProviderError.ServiceUnavailable]
    Catch -->|Timeout / Network| NetErr[ProviderError.NetworkError]
    
Provider Error Classification
AuthenticationFailed: Invalid API key or expired token.
RateLimited: Quota or speed limit hit; includes optional retry backoff time.
NotFound: Content or link not present on provider.
ServiceUnavailable: Provider API is offline or responding with internal server errors.
NetworkError: No internet connection or connection timed out.
ParsingError: API payload structure changed or malformed data received.
Data Flow & Mapping Boundary
To ensure the rest of the application remains isolated from network implementation details:
API Responses are deserialized into internal provider DTOs (@Serializable network objects).
Provider Implementations map DTOs into immutable Domain Models.
Domain Models cross the boundary into Repositories and Application Logic.
sequenceDiagram
    autonumber
    participant Repo as Repository
    participant Prov as Provider Implementation
    participant Ext as External Service API

    Repo->>Prov: Execute Operation (e.g., resolveStream)
    Prov->>Ext: HTTP Request (REST / GraphQL)
    Ext-->>Prov: Raw HTTP Response (JSON)
    Note over Prov: Deserialize to Provider DTO
    Note over Prov: Map DTO to Domain Model
    Prov-->>Repo: Return Domain Model / Structured Result
    
