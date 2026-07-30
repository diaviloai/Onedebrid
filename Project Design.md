# OneForAll

Version: 0.1 (Draft)

## Status

Design Phase

## Purpose

OneForAll is a native Android application built specifically around Real-Debrid.

Its goal is to provide the fastest, most reliable, and most intelligent experience possible for searching, selecting, and streaming cached content.

Unlike traditional media applications, OneForAll is designed around Smart Defaults, minimizing user interaction while still exposing powerful options for advanced users.

---

# Core Principles

- Smart Defaults
- Speed over unnecessary features
- Reliability before convenience
- Modular architecture
- Extensibility
- Offline-friendly caching
- Native Android experience
- Clean Material You interface

---

# Design Principles

## 1. Simplicity First

The architecture should favor the simplest solution that satisfies current requirements. Complexity must be justified by clear long-term value.

## 2. Smart Defaults

The most common user actions should require the fewest interactions possible while still allowing advanced customization when desired.

## 3. Modular by Design

Every major subsystem should be independently replaceable through well-defined interfaces.

## 4. Single Responsibility

Each module should have one clearly defined purpose. Features should not bleed into unrelated systems.

## 5. Loose Coupling

Modules communicate through abstractions rather than direct implementation knowledge. Changes in one subsystem should have minimal impact elsewhere.

## 6. Reliability Before Features

Stability, predictable behavior, and graceful error handling take priority over adding new functionality.

## 7. Performance Matters

Optimize for responsiveness, efficient resource usage, and minimizing unnecessary network requests.

## 8. Native Android Experience

Follow modern Android architecture, Material You guidelines, and platform conventions wherever practical.

## 9. Extensible Architecture

Future capabilities should integrate by adding modules instead of rewriting existing ones.

## 10. Fail Gracefully

Failures in external services should degrade functionality without crashing the application whenever recovery is possible.

## 11. User Control When It Matters

Automate the obvious. Expose the important.

The application should make intelligent decisions automatically while always allowing users to override decisions that significantly affect their experience.

---

# Documentation Standards

Project documentation is considered part of the architecture and must evolve alongside the application.

## Source of Truth

Architectural decisions are documented before implementation begins.

Implementation must follow approved documentation rather than redefining architecture during development.

## Documentation Format

Documentation should prioritize clarity over completeness.

Whenever possible:

- Text explains intent and responsibilities.
- Mermaid diagrams illustrate architecture and data flow.
- Implementation details belong in technical documents rather than design documents.

## Diagram Standard

Architecture, system communication, and workflow diagrams should use Mermaid whenever practical.

Mermaid diagrams are preferred because they:

- remain version controlled alongside documentation
- render natively on GitHub
- are easy to review in pull requests
- eliminate the need for externally maintained images
- encourage documentation to stay synchronized with implementation

---

# Current Approved Decisions

✓ Modular Service Provider architecture

✓ Smart Defaults

✓ Profiles

✓ Session System

✓ Subtitle-first playback

✓ Application Logic architecture

---

# High-Level Architecture

UI

↓

ViewModels

↓

Application Logic

↓

Service Providers

↓

External Services

---

# System Overview

OneForAll is composed of independent systems that communicate through well-defined interfaces. Each system has a single responsibility and should evolve independently without unnecessary coupling.

## User Interface

Responsible for presenting information and receiving user input.

- Navigation
- Screens
- User interaction
- Status presentation
- Error presentation

Contains no business logic.

---

## Search System

Responsible for locating media.

- Search requests
- Provider coordination
- Result ranking
- Duplicate removal
- Standardized search results

---

## Metadata System

Responsible for enriching media.

- Titles
- Artwork
- Descriptions
- Ratings
- Seasons
- Episodes
- Cast

Metadata should never block playback.

---

## Debrid System

Responsible for all Real-Debrid communication.

- Authentication
- Availability
- Cache checks
- Link resolution
- Downloads
- Account information

No other system communicates directly with Real-Debrid.

---

## Playback System

Responsible for media playback.

- Stream preparation
- Player initialization
- Resume support
- Audio selection
- Subtitle attachment
- Playback state

---

## Subtitle System

Responsible for subtitle management.

- Search
- Automatic selection
- Download
- Synchronization
- User overrides

Subtitle support is considered a first-class feature.

---

## Profile System

Stores persistent user preferences.

- Playback preferences
- Subtitle preferences
- Search preferences
- Provider priorities
- Theme preferences

Profiles never store temporary application state.

---

## Session System

Maintains the application's active state.

- Active profile
- Current playback session
- Active search filters
- Navigation state
- Resume context
- Temporary selections

Sessions are temporary and independent from profile data.

---

## Settings System

Controls global application behavior.

- Network settings
- Provider configuration
- Backup & Restore
- Debug options
- Experimental features

---

## Cache System

Improves performance through temporary storage.

- Search cache
- Metadata cache
- Artwork cache
- Subtitle cache
- Provider cache
- Expiration policies

Loss of cache should never result in permanent data loss.

---

## Download System

Manages offline media.

- Download queue
- Progress
- Retry handling
- Storage management
- Cleanup

---

# System Communication

User

↓

User Interface

↓

Application Logic

↓

Core Systems

↓

Service Providers

↓

External Services

Systems communicate through defined interfaces and should remain loosely coupled.

---

# Primary Systems

- Search
- Metadata
- Real-Debrid
- Playback
- Subtitles
- Profiles
- Session
- Settings
- Cache
- Downloads

---

# Future Systems

- Android TV
- Ability to use other debrid services
- Trakt
- Offline Downloads
- Jellyfin Integration
- Additional Debrid Providers

---

# Current Status

Project currently in architectural design.

Implementation has not begun.

---

## Application Logic

The Application Logic layer coordinates all major application systems and contains the business rules that define OneForAll's behavior.

It serves as the boundary between the Android UI and the underlying service providers, ensuring that user interface components remain free of business logic and external service implementations remain isolated behind well-defined interfaces.

Application Logic is responsible for:

- Coordinating workflows across multiple systems.
- Enforcing business rules.
- Providing a stable API for ViewModels.
- Shielding the UI from implementation details.
- Maintaining loose coupling between subsystems.

The layer consists of five primary architectural components:

### Use Cases

Use Cases represent individual business operations or user actions.

Each Use Case should:

- Have a single responsibility.
- Coordinate one complete workflow.
- Remain independent of Android framework classes.
- Be reusable across the application.

Examples include:

- Search Media
- Resolve Playback
- Start Playback
- Resume Playback
- Download Media
- Switch Profile
- Synchronize Subtitles

### Repositories

Repositories provide a unified interface for application data.

They abstract whether information originates from:

- Local storage
- Cache
- Real-Debrid
- Metadata providers
- Subtitle providers

Repositories own data access while hiding implementation details from the rest of the application.

### Coordinators

Coordinators manage long-running application workflows that span multiple operations or systems.

Examples include:

- Search Coordinator
- Playback Coordinator
- Session Coordinator
- Download Coordinator

Unlike Use Cases, Coordinators maintain the state and orchestration required for ongoing processes.

### Provider Interfaces

External services are accessed only through provider interfaces owned by the application.

Examples include:

- Debrid Provider
- Search Provider
- Metadata Provider
- Subtitle Provider

This allows provider implementations to be replaced without affecting the rest of the architecture.

### Domain Models

Domain Models represent the application's core business objects.

Examples include:

- Media
- Episode
- Search Result
- Playback Request
- Subtitle Track
- User Profile
- Session State

Domain Models remain independent from network responses and database entities.

### Media Domain Model

Media is the application's canonical representation of playable content.

Rather than allowing individual subsystems to maintain separate representations of movies, television episodes, or other media, OneForAll defines a single Media domain model shared throughout the application.

Media acts as the common language between systems and serves as the foundation for business operations involving content.

The Media model should remain independent of:

- External API response formats
- Database entities
- Provider-specific identifiers

Individual subsystems may enrich or reference Media, but they should not redefine it.

Examples include:

- Search results reference Media.
- Metadata enriches Media.
- Playback consumes Media.
- Continue Watching references Media.
- Downloads reference Media.
- Subtitle selection references Media.

This separation ensures that every subsystem operates on a consistent representation of content while allowing provider implementations and storage mechanisms to evolve independently.

### Dependency Flow

Application Logic follows a strict one-way dependency flow:

```mermaid
flowchart TD

VM[ViewModels]
UC[Use Cases]
CO[Coordinators]
REPO[Repositories]
PI[Provider Interfaces]
PROV[Provider Implementations]
EXT[External Services]

VM --> UC
VM --> CO

UC --> REPO
UC --> CO

CO --> REPO

REPO --> PI

PI --> PROV
PROV --> EXT
