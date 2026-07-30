Here is the updated UI/UX Design v0.1 document with Watchlist / Saved List integrated directly into Destination 1 (Home / Continue Watching) and reflected in the navigation architecture.
OneForAll
UI/UX Design
Version: 0.1 (Draft)
Purpose
This document defines the user interface (UI) and user experience (UX) architecture for OneForAll.
Its goal is to translate OneForAll’s Smart Defaults philosophy into a clean, predictable, and fast visual interface built with modern Android standards (Jetpack Compose and Material 3 / Material You).  
The UI layer is responsible solely for presenting application state and capturing user interactions. It contains zero business logic and communicates strictly through ViewModel contracts.  
Core UX Principles
Zero-Click to Content (Smart Defaults): The path from opening the application to playing media should require the minimum possible interactions.  
Predictable Navigation: Simple, hierarchical visual navigation with minimal depth. Users should never feel lost or trapped deep in nested menus.
Non-Blocking UI: Operations like metadata enrichment, background syncing, or subtitle pre-fetching must never freeze or block user input.  
Content-First Presentation: Focus on media artwork, high-clarity typography, and essential details rather than complex control interfaces.  
Adaptive Inputs: Design considerations that ensure touch-friendly targets for mobile devices while keeping the layout straightforward enough for future TV/D-pad navigation.  
Screen State Model
To enforce predictability and eliminate UI bugs, every screen follows an explicit Unidirectional Data Flow (UDF) state pattern.
flowchart TD
    VM[ViewModel] -->|Emits UIState| Screen[Compose Screen / View]
    Screen -->|Triggers UIEvent| VM
    VM -->|Executes Workflow| UC[Use Cases / Application Logic]
    UC -->|Updates State| VM
    
State Architecture Components
UIState: An immutable data structure representing everything the UI needs to render at a given frame.
UIEvent: User actions (e.g., button clicks, swipe actions, text input) sent to the ViewModel.  
UIEffect: One-off events that do not modify persistent screen state (e.g., displaying a Snackbar, launching a system intent, navigating to a new screen).
Standard State Structure
Each screen implementation standardizes on four fundamental state variants:
[Idle / Initial] ──► [Loading] ──► [Success(Data)]
                               └──► [Error(FailureContext)]
                               
Primary Application Flows
1. Fast Search & Instant Play Flow
The central journey for locating and playing media.
flowchart TD
    A[Launch App / Home Screen] --> B[Enter Query in Search]
    B --> C[Instant Search Results]
    C --> D[Select Media Item]
    D --> E{Auto-Resolve Stream?}
    E -->|Smart Defaults Enabled| F[Launch Player Directly]
    E -->|Manual Select Enabled| G[Display Stream Source List]
    G --> H[Select Stream & Play]
    
2. Playback & Subtitle Selection Flow
Focuses on a seamless, subtitle-first playback experience.  
flowchart TD
    A[Stream Resolved] --> B[Initialize Player]
    B --> C{Active Profile Subtitle Prefs}
    C -->|Auto Match Found| D[Attach Subtitle & Start Playback]
    C -->|No Match / Overridden| E[Open Subtitle Quick-Picker Overlay]
    E --> F[Fetch External Subtitles via Subtitle System]
    F --> G[Select & Synchronize]
    
Navigation Architecture
The application uses a flat navigation structure to minimize depth and keep essential views easily reachable.
flowchart TD
    Root[App Container] --> NavHost[Navigation Host]
    NavHost --> Home[Home Hub]
    Home --> CW[Continue Watching Row]
    Home --> WL[Watchlist / Saved List Row]
    NavHost --> Search[Search View]
    NavHost --> Details[Media Details Screen]
    NavHost --> Player[Player View]
    NavHost --> Settings[Settings & Profile Management]
    
Main Destinations
Home Hub (Continue Watching & Watchlist): The primary landing area providing quick access to active playback sessions, saved items for later viewing (Watchlist), recently played media, and active profile status.  
Search Screen: Unified search bar with real-time filtering and cached query history.  
Media Details: Non-blocking view displaying metadata, episode structures, quick "Add to Watchlist" toggles, and available stream sources.  
Player View: Native video interface with overlay controls for audio tracks, subtitle adjustments, and stream stats.  
Settings & Profiles: Management of app configurations, Debrid accounts, cache sizes, and profile preferences.  
Component & Design System Guidelines
Visual Language
Color System: Built on Dynamic Color (Material You), adapting automatically to system themes while enforcing contrast ratios that keep text legible over artwork backdrops.  
Typography: Clean, high-legibility sans-serif scales optimized for quick scannability across various viewing distances.  
Feedback & Loading: Shimmer/skeleton placeholders for loading states rather than full-screen spinners, avoiding harsh layout shifts as metadata streams in.  
Error Presentation
Errors are presented to the user based on their severity and recoverability, translating ProviderError domain states into clear user action:  
Error SeverityPresentation MechanismExample
Non-blocking / BackgroundTransient Toast or SnackbarMetadata enrichment failed; showing raw title.
Recoverable OperationalEmbedded Inline State Card with Retry ActionNetwork timeout while fetching stream sources.
Critical / FatalFull-Screen Error View with System ActionsDebrid subscription expired or missing network connection.