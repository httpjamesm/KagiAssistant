# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

KagiAssistant is a native Android app for the Kagi Assistant service, built with Jetpack Compose and Material Design 3. It provides a Material You-themed interface with full dynamic theming, overlay mode for quick access, and digital assistant integration.

**Key technical details:**
- **Min/Target SDK**: Android 16 (API 36)
- **Language**: Kotlin 2.0
- **Build System**: Gradle with Kotlin DSL
- **KSP**: Used for Moshi codegen (version 2.0.21-1.0.25)

## Common Commands

### Building
```bash
./gradlew build                    # Build debug and release variants
./gradlew assembleDebug            # Build debug APK
./gradlew assembleRelease          # Build release APK
./gradlew installDebug             # Install debug build to connected device
```

### Testing
```bash
./gradlew test                     # Run unit tests
./gradlew connectedAndroidTest     # Run instrumented tests on connected device
./gradlew test --tests AssistantClientUnitTest  # Run specific test class
```

### Code Generation
```bash
./gradlew kspCommonKotlinMetadata  # Run KSP annotation processing (Moshi codegen)
```

### Linting
```bash
./gradlew lint                     # Run Android linter
./gradlew lintDebug                # Lint debug variant only
```

## Architecture

### MVVM + Repository Pattern

The app follows a clean MVVM architecture with a repository layer:

```
ui/viewmodel/          # ViewModels manage state with StateFlow
ui/main/               # Main chat screen
ui/overlay/            # Mini overlay interface (floating window)
ui/settings/           # Settings management
ui/companions/         # Kagi companion (model) management
ui/landing/            # Login/authorization flow
data/repository/       # Repository interface and implementation
AssistantClient.kt     # Network layer (Kagi API communication)
MainActivity.kt        # Entry point with navigation
```

### Key Architectural Concepts

**ViewModel State Management**: Each screen has a dedicated ViewModel with state classes (e.g., `OverlayUiState`, `ThreadsUiState`). State is exposed via `StateFlow<T>` and updated internally via `MutableStateFlow<T>`.

**Repository Pattern**: `AssistantRepository` interface abstracts the `AssistantClient`. The repository is injected via `AssistantViewModelFactory` (manual DI - no Hilt).

**Streaming Responses**: The Kagi API uses server-sent events (SSE). `AssistantClient.fetchStream()` handles chunked responses with an `onChunk` callback. ViewModels update state incrementally as chunks arrive.

**Overlay Mode**: The app can be invoked as a digital assistant, showing a mini overlay window instead of full app. This uses `SpeechRecognizer` for voice input and `TextToSpeech` for reading responses. Screenshots can be captured and attached to messages.

**Authentication**: Uses Kagi's QR code authorization flow. Session tokens are persisted in SharedPreferences and restored on app launch.

### Data Flow

1. User action → Compose UI calls ViewModel method
2. ViewModel → Repository (or directly to AssistantClient)
3. AssistantClient → OkHttp → Kagi API
4. Streaming response → chunks parsed and emitted via callback
5. ViewModel updates `MutableStateFlow`
6. UI observes via `StateFlow` and recomposes

## Key Libraries

- **UI**: Jetpack Compose, Material 3, Navigation Compose
- **Networking**: OkHttp 5.0.0-alpha.14 (specifically compiled against Kotlin 2.0)
- **JSON**: Moshi 1.15.1 with KSP codegen + Kotlinx Serialization
- **HTML**: Jsoup 1.18.1
- **Image Loading**: Coil 2.7.0 with SVG support
- **Coroutines**: Kotlinx Coroutines
- **ML Kit**: Language identification (for text detection)

## Important Notes

### JSON Serialization
The project uses **both** Moshi and Kotlinx Serialization:
- Moshi for most API responses (codegen via KSP)
- Kotlinx Serialization for specific request types

When adding new data models, use `@JsonClass(generateAdapter = true)` for Moshi codegen.

### OkHttp Version
The project uses OkHttp 5.0.0-alpha.14 specifically compiled against Kotlin 2.0. Do not upgrade to a version that doesn't explicitly support Kotlin 2.0.

### Overlay Permissions
The overlay mode requires `SYSTEM_ALERT_WINDOW` permission. The app guides users through granting this in Settings.

### File Upload
Images attached to messages must be under 16MB. The app generates thumbnails for preview using content resolvers.

### Testing
Unit tests are in `app/src/test/java/`. The main test file (`AssistantClientUnitTest`) requires a valid `SESSION_TOKEN` to run integration tests. Leave this empty in the codebase.

### Navigation Structure
- `Landing` → Entry point, handles login
- `Main` → Primary chat interface
- `Settings` → App configuration
- `Companions` → Model/character selection

Navigation uses Jetpack Navigation Compose with screen-based routes.
