# OneForAll

Internal API Specification

Version: 0.1 (Draft)

---

# Purpose

This document defines the contracts between the major architectural components of OneForAll.

Its purpose is to ensure that every subsystem communicates through stable, well-defined interfaces rather than direct implementation knowledge.

Implementations may change.

Contracts should remain stable whenever possible.

---

# Design Principles

Internal APIs should be:

- Simple
- Explicit
- Consistent
- Testable
- Asynchronous
- Independent of Android framework classes

Interfaces describe *what* is provided, not *how* it is implemented.

---

# Communication Model

```mermaid
flowchart LR

VM[ViewModels]

UC[Use Cases]

CO[Coordinators]

REPO[Repositories]

PROV[Provider Interfaces]

EXT[External Services]

VM --> UC
VM --> CO

UC --> REPO

CO --> REPO

REPO --> PROV

PROV --> EXT
Communication flows in one direction.
Responses flow back through the same chain.
API Layers
ViewModel API
Purpose:
Expose business operations to the UI.
Responsibilities:
Start workflows
Observe state
Receive results
Never access repositories directly
ViewModels communicate only with:
Use Cases
Coordinators
Use Case API
Purpose:
Represent one business operation.
Each Use Case exposes:
One public execute() operation
Example operations:
SearchMedia
ResolvePlayback
ResumePlayback
DownloadMedia
SwitchProfile
Use Cases may call:
Repositories
Coordinators
Use Cases never call provider implementations directly.
Coordinator API
Purpose:
Coordinate long-running workflows.
Examples:
Playback Coordinator
Search Coordinator
Session Coordinator
Coordinators expose operations for:
Starting workflows
Monitoring progress
Cancelling operations
Receiving events
Coordinators may use:
Repositories
Multiple Use Cases
Other Coordinators when appropriate
Repository API
Purpose:
Provide application data.
Repositories hide:
Network requests
Cache
Local storage
Provider implementations
Repositories expose business data rather than transport models.
Examples:
MediaRepository
SubtitleRepository
PlaybackRepository
ProfileRepository
SessionRepository
Provider API
Purpose:
Communicate with external services.
Provider interfaces define application-owned contracts.
Implementations convert external responses into Domain Models.
Examples:
DebridProvider
MetadataProvider
SubtitleProvider
SearchProvider
Providers never expose raw HTTP responses.
Domain Models
Every layer communicates using Domain Models.
Network DTOs remain inside providers.
Database entities remain inside repositories.
Mermaid
flowchart LR

HTTP[HTTP Response]

DTO[Network DTO]

Domain[Domain Model]

Entity[Database Entity]

HTTP --> DTO

DTO --> Domain

Domain --> Entity
Only Domain Models cross subsystem boundaries.
Error Model
All internal APIs return structured results.
Errors should never be represented by null values.
Errors should contain:
Type
Message
Recoverability
Source
Business logic determines how errors are handled.
Presentation determines how they are displayed.
Async Operations
Long-running operations are asynchronous.
Examples:
Search
Metadata retrieval
Cache checks
Link resolution
Subtitle downloads
Blocking operations should be avoided whenever possible.
Events
Long-running workflows may publish events.
Examples:
Search Started
Search Progress
Search Completed
Playback Started
Playback Buffering
Download Progress
Events communicate state changes without tightly coupling systems.
Cancellation
Every long-running operation should support cancellation.
Cancellation should:
Stop unnecessary work
Release resources
Preserve application stability
Cancellation is considered a normal operation rather than an error.
Versioning
Internal interfaces should evolve carefully.
Breaking changes should be minimized.
When breaking changes are required:
Update documentation first.
Review architectural impact.
Update dependent interfaces together.
Design Rules
✓ ViewModels never access repositories.
✓ ViewModels never access providers.
✓ Use Cases own business workflows.
✓ Repositories own data access.
✓ Coordinators own long-running orchestration.
✓ Providers own external communication.
✓ Domain Models are shared.
✓ DTOs remain inside providers.
✓ Database entities remain inside repositories.
✓ Dependencies always point downward.