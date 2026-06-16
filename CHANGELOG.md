# Ch## [0.1.0] - 2026-06-16
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
