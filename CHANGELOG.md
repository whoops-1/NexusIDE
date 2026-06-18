# Changelog

## [Unreleased] - 2026-06-18
### Fixed
- AI requests 404ing for any base URL that already includes `/chat/completions`
  (shared `resolveChatCompletionsUrl()` helper, used by both AiEngine and AgentEngine)
- App crash on opening the editor/terminal (`TerminalHost.newLocal()` uncaught
  `IOException` from an unusable default working directory)
- Terminal output never rendering, independent of the crash above (state held
  in a mutated `StringBuilder` inside `mutableStateOf`, which Compose never
  observes)
- `sqlite-jdbc` removed — incompatible with Android (`UnsatisfiedLinkError` on
  every device); `DatabaseService` rewritten against native `SQLiteDatabase`
- Unused Retrofit + 2 converter dependencies removed

### Added
- Toggleable Explorer / Terminal / AI / Settings panels in the editor screen,
  all closed by default (mobile-first, full-screen code surface)
- Tab bar with close buttons and a dirty-state dot
- Markdown + fenced-code-block rendering with copy buttons in both AI Chat
  and Agent screens, via a shared `ChatRichText` component
- Fullscreen toggle for the AI panel
- Theme picker in Settings (swatches for built-in themes)
- Editor preference controls in Settings UI (font size, tab width, word wrap,
  minimap, ligatures — the backing settings already existed, the UI didn't)
- Per-tool "auto-approve" switches for the agent's destructive tools
  (write_file, run_command, delete_file, rename_file)
- ProGuard/R8 keep rules for OkHttp, JSch, NanoHTTPD, compose-markdown

### Known gap
- This file and README.md describe some features (plugin system, multi-DB
  support beyond a read-only SQLite inspector, LSP, Prettier integration)
  that do not appear to exist in the current source tree. Flagged in
  KNOWN_ISSUES.md rather than silently fixed — worth a dedicated audit pass.

## [0.1.0] - 2026-06-16
### Added
- Initial public release of NexusIDE
- Code editor with custom text buffer, syntax highlighting (C/C++/Java/JS/TS/JSX/TSX/Python/Rust/Go/Kotlin/Swift/Ruby/PHP/Shell/HTML/CSS/SCSS/JSON/YAML/XML/SQL/Markdown/TOML/INI/Dockerfile/Makefile)
- Multi-tab terminal with Termux bridge (auto-detects installed packages: GCC, G++, Python, Node.js, etc.)
- File explorer with two-pane tree+listing view
- AI chat with multiple providers (OpenAI, Anthropic, OpenRouter, custom)
- Git operations with GitHub integration
- Settings: theme, font size, AI provider config
- Plugin system with sandboxed execution
- Database tools (SQLite, MySQL, Postgres)
- Web preview with dev server
- Formatter integration (Prettier)
- Language Server Protocol (LSP) for Kotlin
- Build commands per language (gradle, cmake, npm, pip, cargo, go, make)
- Soft keyboard support with auto-indent and shortcuts
- Material 3 design with dark/light theme support
- Permission-aware filesystem access (SAF for user folders)
- Encrypted secure storage for API keys

### Notes
- 49 Kotlin source files (~5,500 lines)
- 0 external runtime dependencies beyond AndroidX + Compose + Room
- Target APK size: < 40 MB
- Min SDK 26 (Android 8.0), target SDK 34
