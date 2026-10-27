# Architecture

JustWatch uses Android Views with a UI layer and a data layer. A domain layer would add little value at the current size.

`WatchViewModel` owns an immutable `WatchState`, exposed as a read-only `StateFlow`. The Activity sends user actions to the ViewModel and collects state inside `repeatOnLifecycle(STARTED)`. The Activity hosts dedicated screen views under `ui/screens`. Each screen updates its own controls from state; only a destination change replaces the mounted screen. The player owns its ExoPlayer instance and keeps it across unrelated state updates. Link setup rebuilds its report only when the report or wizard mode changes. Android intents, permissions, window insets, WebView, and ExoPlayer remain UI responsibilities.

The data layer contains three repositories:

- `WatchRepository` coordinates extraction and metadata fallback and exposes loading, playable, completed, and failure states as a Flow.
- `SettingsRepository` stores preferences, reads Android link associations, records settings visits, and runs diagnostics and engine updates.
- `UpdateRepository` checks releases, downloads signed updates, and verifies cached APKs before installation.

Blocking network, extraction, and file operations run on `Dispatchers.IO`. The embedded extraction runtime is protected by a coroutine mutex because engine installation and extraction share files. Cancellation interrupts its worker. Update cancellation also closes the active HTTP call. A cancelled coroutine must never become a user-visible extraction error.

The ViewModel is the single owner of the navigation destination and link draft. A `SavedStateHandle` stores their restorable values along with the current source URL. After process death, media preparation restarts from that URL; a growing file is never assumed to have survived. Extraction and downloads stay in the existing ViewModel across Activity recreation. The Activity releases its player and WebView when destroyed. Closing a video cancels the download and schedules file deletion on IO. Cleanup after a cleared ViewModel uses a small process-wide cleanup scope because `viewModelScope` is already cancelled at that point. Process death leaves cache files for cleanup at the next extraction runtime initialization.

UI progress callbacks from the extraction library and downloader are adapted to Flows at the data boundary. App code does not expose callback-based fetching APIs to the UI.

App updates retain the existing package, version, checksum, signing certificate, and scoped FileProvider checks. Installing an update still requires the user's confirmation in Android's installer.

UI copy and accessibility labels are in `res/values/strings.xml`. ViewModel messages use resource-backed `UiText` values resolved by the UI, and engine updates return a structured result instead of display copy. Technical diagnostic reports remain plain text.

## Verification

The Android workflow runs JVM tests, builds the APK, and runs emulator tests for navigation, link setup, local playback, progressive playback, and Activity recreation. The release workflow also verifies signing and version metadata. These checks use local video fixtures. They do not prove that every Instagram, TikTok, or Facebook URL remains extractable on every network.
