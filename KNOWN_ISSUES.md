# Known Issues — NexusIDE

> Honest accounting of what is and isn't done. This file exists so a future
> contributor (human or agent) can pick up from a known state instead of
> rediscovering the same gaps.

**Last audit:** 2026-06-18

---

## Status: build blockers from the 2026-06-16 audit are resolved

Verified by reading the actual source for all 8 previously-listed blockers
(ServiceLocator scope, ProjectViewModel members, SettingsScreen API,
HomeScreen signature, koinViewModel usage, MainActivity import,
NexusTopBar, ChatRole) — none of those patterns exist in the tree anymore.

**This was checked by direct code reading, not by running a real build.**
This sandbox has no Android SDK and no network access, so nothing in this
file is backed by an actual `./gradlew assembleDebug`. Run that locally
before trusting any of this.

---

## Fixed this session (2026-06-18)

- **AI 404 on every request.** `AiEngine` and `AgentEngine` both built the
  request URL as `"${baseUrl}/chat/completions"` unconditionally. OpenRouter's
  docs display the complete endpoint URL, so pasting it into "Base URL" (the
  natural thing to do) doubled the path. Fixed with a shared
  `resolveChatCompletionsUrl()` helper in `AiEngine.kt` used by both engines,
  plus a `formatApiError()` helper that surfaces the provider's actual error
  message instead of a raw JSON blob. Settings now has inline copy warning
  against pasting the full endpoint URL.
- **Editor/terminal crash.** `TerminalHost.newLocal()` called `pb.start()`
  uncaught; the default working directory was `System.getProperty("user.home")`,
  which resolves to `File("/")` on Android — a directory this process has no
  permission to chdir into. `newLocal()` now returns `Result<TerminalSession>`,
  and `TerminalView` shows a retry-able error state instead of crashing.
  Callers now pass `WorkspaceService.workspaceRoot`, a directory the app
  actually owns.
- **Terminal output never rendered.** Independent of the crash: output was
  accumulated via in-place mutation of a `StringBuilder` held in
  `mutableStateOf`, which Compose's snapshot system never observes (it tracks
  reassignment, not mutation). Switched to reassigning a `String`, with a
  200k-character scrollback cap to bound memory on long-running processes.
- **`sqlite-jdbc` removed.** It loads its native library via
  `System.loadLibrary()`, which throws `UnsatisfiedLinkError` on Android on
  every device, not as an edge case. `DatabaseService` was rewritten against
  Android's built-in `SQLiteDatabase`, opened with `OPEN_READONLY` so the
  "read-only inspector" claim is OS-enforced rather than a comment. Had zero
  consumers, so this was a contained change.
- **Retrofit + 2 converters removed.** Declared but never called anywhere —
  both engines already talk to OkHttp directly with manual JSON.
- **ProGuard rules added** for OkHttp/okio, JSch, NanoHTTPD, and
  compose-markdown — the libraries that actually need manual keep rules.
  These are written from each library's documented reflection behavior, not
  verified against a real R8 `missing_rules.txt` (again: no SDK in this
  sandbox). Treat as a starting point.

## UI/UX work done this session

- Editor screen: file explorer, terminal, AI assistant, and settings are now
  independently toggleable overlay/split panels, all closed by default, so
  the code surface gets full screen on a phone. Tab bar at top with a dirty
  indicator and per-tab close button.
- AI chat (both AI Chat and Agent screens): messages now render through a
  shared `ChatRichText` component — real markdown for prose, fenced code
  blocks get a distinct surface with a language label and copy button.
  AI panel supports a fullscreen toggle when opened from the editor.
- Settings: added a theme picker (swatches for the 3 built-in themes), wired
  up the editor preference controls that already existed in `SettingsStore`
  but had no UI, added an "Agent behavior" section exposing per-tool
  auto-approve switches (backed by a new `SettingsStore.toolAutoApprove`
  set, read by `AgentViewModel`'s approval gate), and a clearly-labeled
  "coming soon" section for shortcuts/import-export/workspace prefs so the
  IA reflects what was asked for without faking functionality that isn't
  there yet.

---

## Explicitly deferred (not built this session)

These were requested but are each a standalone subsystem, not a UI tweak —
bundling all of them into one unverified pass is exactly the failure mode
that produced the original 8 build blockers:

- Persistent chat history / session management across app restarts
- Conversation search
- Project-level AI context (beyond what's already sent per-request)
- Web Search agent tool (no search API integration exists yet)
- Custom/user-defined agent tools (plugin-style tool registration)
- Multi-agent workflows (orchestrating multiple agent roles/instances)
- Diff viewer (side-by-side / inline / AI-generated summaries / accept-reject)
- Keyboard shortcut customization
- Settings import/export
- Per-workspace setting overrides

## Other gaps found during this audit (not fixed, flagged for awareness)

- `ProjectViewModel.Tab.isDirty` is always `false` — nothing ever sets it
  back to `true` when a buffer changes, so the dirty dot in the tab bar
  will never actually show. Needs a hook into the editor session's
  change events.
- `AgentEngine.callApi()` makes a single blocking `execute()` call, not a
  streamed request, so `AgentEvent.TextDelta` is defined but never emitted —
  `AgentViewModel`'s `TextDelta` branch is dead code. Not a crash, just an
  unused code path.
- README.md lists JGit (Eclipse) as the Git implementation; there is no
  JGit dependency in `build.gradle.kts` and `GitService.kt` imports nothing
  JGit-related. Git operations almost certainly shell out to the `git`
  binary. The README is aspirational here, not a description of the code.
