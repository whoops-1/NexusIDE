# Known Issues — NexusIDE

> Honest accounting of what is and isn't done. This file exists so a future
> contributor (human or agent) can pick up from a known state instead of
> rediscovering the same gaps.

**Last audit:** 2026-06-16

---

## Status: ❌ Build is **broken** — will not compile

The code currently in `main` is a work-in-progress from a previous session
that mixed two different architectural designs. The Kotlin compiler will
fail on the issues below. A fresh `git clone && ./gradlew assembleDebug`
will **not** succeed.

---

## Build blockers (will prevent `assembleDebug`)

### 1. `ServiceLocator` references non-existent classes
**File:** `app/src/main/java/com/nexus/ide/core/di/ServiceLocator.kt`

Imports classes that do not exist anywhere in the tree:
- `com.nexus.ide.data.repository.AiRepository`
- `com.nexus.ide.data.repository.EditorRepository`
- `com.nexus.ide.data.repository.GitRepository`
- `com.nexus.ide.data.repository.GitHubRepository`
- `com.nexus.ide.data.repository.PluginRepository`
- `com.nexus.ide.data.repository.ProjectRepository`
- `com.nexus.ide.data.repository.TerminalRepository`
- `com.nexus.ide.features.plugin.PluginHost` (package name is `plugins`, not `plugin`)

**Fix:** Either (a) create stub classes for the missing types, or (b) reduce
`ServiceLocator` to only expose what actually exists (`database`, `secureStore`,
`settings`, `termux`, `workspace`, `recents`). Option (b) is faster.

### 2. `ProjectViewModel` references non-existent `ServiceLocator` members
**File:** `app/src/main/java/com/nexus/ide/presentation/viewmodels/ProjectViewModel.kt`

Uses `ServiceLocator.workspace` and `ServiceLocator.recents` — these are not
declared in `ServiceLocator`.

**Fix:** Add `workspace` and `recents` lazy properties to `ServiceLocator`,
or use the existing `WorkspaceService` and `RecentFiles` classes via
`ServiceLocator.init(context)` + direct construction.

### 3. `SettingsScreen` uses the wrong settings API
**File:** `app/src/main/java/com/nexus/ide/presentation/screens/settings/SettingsScreen.kt`

Imports `com.nexus.ide.data.local.prefs.SettingsRepository` (does not exist)
and calls `ServiceLocator.settings(ctx)` as a function. The actual class is
`SettingsStore` (no `ctx` parameter, no `state`/`setEditorFontSize` API).

**Fix:** Rewrite `SettingsScreen` against the real `SettingsStore` API
(individual getters/setters, not a single `state` flow), or create a
`SettingsRepository` façade that wraps `SettingsStore`.

### 4. `HomeScreen` signature mismatch
**File:** `app/src/main/java/com/nexus/ide/presentation/screens/HomeScreen.kt`

Declares 11 callback parameters; `Nav.kt` calls it as `HomeScreen(vm)`.

**Fix:** Either change `HomeScreen` to take a `ProjectViewModel` and the
tab controller, or change `Nav.kt` to pass all 11 callbacks.

### 5. `AiChatScreen`, `DebugScreen`, `GitScreen` use `koinViewModel()`
**Files:** `app/src/main/java/com/nexus/ide/presentation/screens/{ai,debug,git}/*.kt`

These call `koinViewModel()` and accept `adapter: DebugAdapter` /
`repo: GitRepository` parameters. Neither Koin nor those parameters are
wired in `Nav.kt`. Also, `koin-android` / `koin-androidx-compose` are not in
`build.gradle.kts` dependencies.

**Fix:** Either add Koin and configure the modules, or rewrite the screens
to use `ProjectViewModel` like the others.

### 6. `presentation.NexusRoot` is imported from the wrong package
**File:** `app/src/main/java/com/nexus/ide/MainActivity.kt`

`import com.nexus.ide.presentation.NexusRoot` — the symbol is actually
defined in `com.nexus.ide.presentation.navigation.NexusRoot`.

**Fix:** Update the import in `MainActivity.kt`.

### 7. `presentation.components.NexusTopBar` is imported but does not exist
**Files:** Several screens import `NexusTopBar`.

**Fix:** Either create `components/NexusTopBar.kt` or inline a `TopAppBar`
wherever it's used.

### 8. `features.ai.ChatRole` does not exist
The enum is referenced from the AI engine and chat code; only `ChatMessage`
exists in `features/ai`.

**Fix:** Add the `ChatRole` enum, or rewrite the chat code to use a single
`role: String` field on `ChatMessage`.

---

## Issues fixed in this commit

- ✅ `Nav.kt` imports corrected (s