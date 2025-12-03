# KagiAssistantMaterial

This document provides a high-level overview of the KagiAssistantMaterial project, intended to be
used as a reference for developers.

## Project Overview

KagiAssistantMaterial is a native Android application that serves as a client for the Kagi
Assistant. It is built with modern Android development technologies, including:

* **Language:** Kotlin
* **UI:** Jetpack Compose with Material You for a modern, dynamic, and themed user interface.
* **Architecture:** The app follows a single-activity architecture with multiple composable screens
  managed by Jetpack Navigation.
* **Networking:** OkHttp is used for making network requests to the Kagi Assistant API, and Moshi is
  used for JSON serialization/deserialization.
* **Asynchronous Operations:** Kotlin Coroutines are used for managing background threads and
  asynchronous operations.

### Key Features:

* Full dynamic theming with Material You.
* One-tap sign-in to Kagi.
* Can be set as the default assistant on Android.
* High-quality haptic feedback.

## Building and Running

### Prerequisites

* Android Studio
* An Android device or emulator running Android 16 or later (API level 36).

### Building

1. Clone the repository.
2. Open the project in Android Studio.
3. Let Gradle sync and download the required dependencies.
4. Build the project using the "Build" menu or by running `./gradlew build` in the terminal.

### Running

Run the app on an Android device or emulator using the "Run" button in Android Studio or by running
`./gradlew installDebug` in the terminal.

## Development Conventions

* **UI:** The UI is built entirely with Jetpack Compose. Follow Material Design guidelines and use
  the existing theme and components.
* **State Management:** Composable screen state is managed using `State` and `remember` for simple
  cases, and hoisted state objects for more complex screens.
* **Networking:** Use the `AssistantClient` for all interactions with the Kagi Assistant API.
* **Styling:** Follow the existing code style and formatting.
* **Dependencies:** Use the versions specified in the `build.gradle.kts` files.
