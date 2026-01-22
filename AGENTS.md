# KagiAssistant Agent Guide

## Purpose

This file documents how agentic assistants should approach the Android/Kotlin Compose project at the
repository root. Follow these instructions before making changes and while coding to keep the
workspace consistent, safe, and maintainable.

---

## Environment & Tooling

- **Java / Kotlin**: Standard Kotlin/JVM targets bundled with Android Gradle plugin (AGP 8.x+). Use
  the Gradle wrapper located at `./gradlew` for every command.
- **Android API**: Build targets API 34 (check `app/build.gradle` for precise values). Use Android
  Studio Electric Eel+ or CLI gradlew.
- **Lint/Test Runner**: Prefer Gradle wrapper commands so that dependencies resolve reproducibly.

### Key Commands

| Purpose         | Command                                 | Notes                                                                                          |
|-----------------|-----------------------------------------|------------------------------------------------------------------------------------------------|
| Clean build     | `./gradlew clean`                       | Removes previous build outputs.                                                                |
| Full build      | `./gradlew assembleDebug`               | Main app artifact.                                                                             |
| Run lint checks | `./gradlew lint`                        | Ensures XML/Kotlin/Compose lint rules pass.                                                    |
| Format checks   | `./gradlew ktlintCheck` (if configured) | If ktlint is not configured, rely on `./gradlew lint` and `./gradlew build` as primary checks. |

---

## Repository Structure Signals

- Kotlin sources live under `app/src/main/java/space/httpjames/kagiassistantmaterial/` with Compose
  UI under `ui/` organized by screen/group (e.g., `main`, `chat`, `message`). Keep this hierarchy
  intact.
- ViewModels (e.g., `MainViewModel`) contain complex streaming/state logic; avoid moving them unless
  refactoring is part of the task.
- Resources (layouts, drawables, strings) follow Android conventions—tweak only as needed.

---

## Style & Code Guidelines

### Kotlin/Compose Formatting

1. **Imports**: Keep sorted and grouped by `androidx`/`kotlinx`/project-specific packages. Trim
   unused imports via the IDE or `./gradlew lint`. Do not reorder manually unless clearly improving
   structure.
2. **Spacing & Braces**: Use 4-space indentation. Open braces for functions/objects follow Kotlin
   conventions (`fun foo() {`). Keep expression bodies on the same line when simple (
   `fun foo() = bar`).
3. **Line Length**: Aim for <= 120 characters. Break long Compose modifier chains by placing each
   `Modifier` call on a new line.
4. **Compose Functions**: Place `@Composable` functions near related screens. Keep parameter lists
   explicit and avoid `Any` unless unavoidable.
5. **State**: Prefer immutable `StateFlow`/`MutableStateFlow` in ViewModels. Expose `asStateFlow()`
   for consumers.
6. **Builders**: When building lists (e.g., `LazyColumn`), avoid `forEach` outside Compose scope.
   Use dedicated items/lists to keep Compose recomposition predictable.

### Naming Conventions

1. **Files**: Match class names (e.g., `MainViewModel.kt` hosts `MainViewModel`). Compose screen
   files include the screen name (e.g., `ChatArea.kt`).
2. **Variables**: Use camelCase for properties and functions, PascalCase for classes/objects. Avoid
   abbreviations if unreadable (e.g., prefer `messageState` over `msgSt`).
3. **State Flows & LiveData**: Name backing flows with an underscore prefix (e.g.,
   `_messagesState`). Shared states should borrow the same suffix (e.g., `messagesState`).
4. **Composables**: Start with `@Composable fun FooBarScreen()`. When representing events (e.g.,
   `onRetryClick`), use prefix `on` + action.

### Types & Nullability

1. **Explicit Nullability**: Annotate optional values explicitly (`String?`). Ship dead-null checks
   before use.
2. **Data Classes**: Favor domain-specific data classes (e.g., `AssistantThreadMessage`). Always
   declare default values to ease serialization/deserialization.
3. **Collections**: Prefer immutable `List`/`Map` when exposing state; mutate underlying
   `MutableList` copies only within ViewModel boundaries.
4. **Coroutine Contexts**: Keep IO-bound work on `Dispatchers.IO`. Use
   `withContext(Dispatchers.Main)` only for UI-synced state updates. Avoid `runBlocking` in
   production code.

### Error Handling

1. **Exceptions**: Catch exceptions only when you can recover or provide useful logs. Bubble others
   so they surface to crash reporting.
2. **Logging**: Use simple `println`/`Log.d` sparingly—favor explicit `try/catch` with
   `e.printStackTrace()` only during debugging. Remove verbose prints before merging.
3. **Result Types**: When repository functions return `Result<T>`, inspect `isSuccess/isFailure` and
   call `exceptionOrNull()` before logging.
4. **User Feedback**: Ensure UI communicates call states (`DataFetchingState`). Update these flows
   promptly in error cases so Compose displays appropriate feedback (error screens/spinners).

### Concurrency & Coroutines

1. **ViewModelScope**: Launch streams in `viewModelScope`. Avoid leaking jobs; cancel background
   flows when a session ends.
2. **Flows**: Collect streams thoughtfully; maintain throttling when updating state (
   `STATE_UPDATE_THROTTLE_MS`). Keep shared mutable lists confined to ViewModel code.
3. **Jobs**: Store active `Job` references if you need to cancel (e.g., ongoing streaming per
   thread). Ensure cancellation runs on the same scope that started the job.

### Dependency Access

1. **Repositories**: Access via constructor injection (e.g., `AssistantRepository`). Avoid static
   references; pass `Context` only when required (e.g., for attachments).
2. **Preferences**: Use `SharedPreferences` only on IO threads when writing. Wrap access in helper
   functions so tests can mock them.
3. **Resources**: Compose references should use `stringResource`, `painterResource`, etc. Avoid raw
   `R.string` usage outside Compose preview contexts.

---

## Settings & Preferences Pattern

### Adding a New Setting

When adding a new user-configurable setting, follow these steps:

1. **Add to `PreferenceKey` enum** (`utils/PreferenceKeys.kt`):
   - Add enum entry with key string: `NEW_SETTING("new_setting")`
   - Add default value in `companion object`: `const val DEFAULT_NEW_SETTING = true/false`

2. **Update `SettingsUiState`** (`viewmodel/SettingsViewModel.kt`):
   - Add property: `val newSetting: Boolean = PreferenceKey.DEFAULT_NEW_SETTING`
   - Initialize from prefs in `_uiState`:
     ```kotlin
     newSetting = prefs.getBoolean(
         PreferenceKey.NEW_SETTING.key,
         PreferenceKey.DEFAULT_NEW_SETTING
     )
     ```

3. **Add toggle function** (`SettingsViewModel.kt`):
   ```kotlin
   fun toggleNewSetting() {
       val newValue = !_uiState.value.newSetting
       _uiState.update { it.copy(newSetting = newValue) }
       prefs.edit().putBoolean(PreferenceKey.NEW_SETTING.key, newValue).apply()
   }
   ```

4. **Update `clearAllPrefs`** to include the default value reset.

5. **Add UI item** (`ui/settings/SettingsScreen.kt`):
   - Use `SettingsItem` composable with appropriate position (TOP/MIDDLE/BOTTOM/SINGLE)
   - Add `Switch` in `rightSide` parameter calling `viewModel.toggleNewSetting()`

6. **Pass preference to consumer** (e.g., `MainScreen.kt` or wherever needed):
   ```kotlin
   val prefs = context.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
   val settingValue = prefs.getBoolean(
       PreferenceKey.NEW_SETTING.key,
       PreferenceKey.DEFAULT_NEW_SETTING
   )
   ```

### Preference Key Conventions

- **Never use magic strings** for preference keys. Always use `PreferenceKey.KEY_NAME.key` and `PreferenceKey.DEFAULT_KEY_NAME`
- **Use descriptive names** that clearly indicate what the preference controls
- **Boolean preferences** should have `toggleXxx()` functions in the ViewModel
- **All preferences** must have default values defined in the `companion object`

### SettingsItem Positioning

The `SettingsItemPosition` enum controls visual grouping:
- `SINGLE`: Standalone item with rounded corners on all sides
- `TOP`: First item in a group, top corners rounded
- `MIDDLE`: Middle item in a group, no rounded corners
- `BOTTOM`: Last item in a group, bottom corners rounded

When grouping related settings (e.g., "Sticky scroll" and "Auto keyboard"), use TOP/MIDDLE/BOTTOM in the same `Column` with `verticalArrangement = Arrangement.spacedBy(2.dp)`.
