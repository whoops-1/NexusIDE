<div align="center">

# 🦅 NexusIDE

**A lightweight, professional mobile IDE for Android.**

*Code, terminal, git, AI — all from your phone.*

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg)](#)
[![Min SDK](https://img.shields.io/badge/min%20SDK-26-3DDC84.svg)](#)
[![Target SDK](https://img.shields.io/badge/target%20SDK-34-3DDC84.svg)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Compose-2024.02.00-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

</div>

---

## ✨ Overview

NexusIDE is a **production-grade Android IDE** designed to replace desktop
development workflows for most developers — without the weight of VS Code
or Android Studio. It runs natively on Android, with a UI adapted perfectly
for touch, and integrates deeply with the local Linux environment through
[Termux](https://termux.dev/).

It is **lightweight** (small APK, low RAM, fast cold start), **fast** (custom
text engine, no Electron, no Chromium), **stable**, and **secure** (sandboxed
processes, scoped storage, encrypted secrets).

## 🎯 Key Features

### Editor
- 🎨 **Syntax highlighting** for 20+ languages (Kotlin, Java, Python, JS, TS,
  C/C++, Go, Rust, HTML, CSS, JSON, YAML, Markdown, SQL, Shell, PHP, Ruby, Swift, XML, properties)
- 🔍 **Find / Replace** with regex
- 📑 **Multi-tab editing**
- ↶↷ **Undo/Redo** with linear stack
- 🧠 **Bracket matching & auto-indent**
- 📏 **Configurable font size, line height, tab width**
- 🌗 **GitHub Dark, Monokai, Solarized Light/Dark themes**
- ⌨️ **Touch-optimized** soft keyboard with code-friendly key row

### File System
- 📂 **Workspace browser** with tree view, breadcrumbs
- 💾 **Sandboxed local storage** + opt-in access to shared storage
- 🔗 **Termux home bridging** (`$HOME`, `$PREFIX`)
- 📋 **File operations**: create, rename, delete, copy, move

### Build & Run
- ⚙️ **Termux integration** — auto-detect GCC, G++, Clang, Python, Node.js, Go, Rust
- 🚀 **One-tap build & run** for C/C++/Python/Node
- 📜 **Output panel** with stderr/stdout capture

### Terminal
- 🖥️ **Local PTY session** through Termux (`run-as` or `am start` deep-link)
- 🌐 **SSH client** with key management
- 📟 **Multiple sessions**

### Version Control
- 🔀 **Git client** — clone, commit, push/pull, branch, merge, log, diff
- 🔐 **HTTPS + SSH** remotes
- 📊 **Inline diff view** & status panel

### AI Assistant
- 🤖 **Code completion** (OpenAI / Anthropic / OpenRouter / custom)
- 🛠️ **Refactor / Explain / Generate test / Fix bug** actions
- 🔁 **Multi-provider streaming**

### Connectivity
- ☁️ **SFTP** file browser
- 🔌 **ADB** device interaction
- 📡 **HTTP client** for testing endpoints

### Extras
- 🔒 **Biometric lock** for opening the app
- 🌙 **Material 3** with dynamic color
- 🎛️ **Gesture navigation** (swipe to switch tabs, two-finger pan/zoom)
- 🔔 **Crash reporting & analytics** (opt-in)

## 🏗 Architecture

NexusIDE follows clean architecture with strict separation of concerns:

```
┌─────────────────────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose)                                 │
│  ├─ Editor │ File Browser │ Terminal │ Git │ AI │ Settings  │
└─────────────────────────────────────────────────────────────┘
            │                │
            ▼                ▼
┌─────────────────────────────────────────────────────────────┐
│  Domain Layer (Use Cases + Models)                          │
└─────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────┐
│  Data Layer                                                 │
│  ├─ Repositories (File, Project, Git, AI, Termux, SSH)     │
│  ├─ Local (Room DB, DataStore, Files)                       │
│  └─ Remote (JGit, OkHttp, JSch, Process exec)              │
└─────────────────────────────────────────────────────────────┘
```

### Tech stack
- **Language**: Kotlin 1.9
- **UI**: Jetpack Compose + Material 3
- **Async**: Kotlin Coroutines + Flow
- **DI**: Hilt
- **DB**: Room + DataStore Preferences
- **Networking**: OkHttp + Moshi
- **Git**: JGit (Eclipse)
- **SSH**: JSch
- **Terminal**: ConPty / Termux:API
- **AI**: SSE streaming via OkHttp

## 📁 Project Structure

```
NexusIDE/
├── app/                       # Android app module
│   ├── src/main/
│   │   ├── java/com/nexus/ide/
│   │   │   ├── NexusApp.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── core/          # Core utilities, extensions
│   │   │   ├── data/          # Repositories + local sources
│   │   │   ├── domain/        # Use cases + models
│   │   │   ├── editor/        # Custom text engine
│   │   │   ├── ui/            # Compose screens & components
│   │   │   ├── termux/        # Termux bridge
│   │   │   ├── terminal/      # Local + SSH PTY
│   │   │   ├── git/           # JGit wrapper
│   │   │   └── ai/            # AI provider integrations
│   │   └── res/               # Resources, themes, drawables
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/            # Gradle wrapper
├── gradlew / gradlew.bat
├── .github/workflows/         # CI (build + lint)
├── LICENSE                    # Apache 2.0
├── CODE_OF_CONDUCT.md
└── README.md
```

## 🚀 Building

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34
- (Optional) [Termux](https://termux.dev/) installed for full functionality

### Steps
```bash
git clone https://github.com/whoops-1/NexusIDE.git
cd NexusIDE
./gradlew assembleDebug           # Build a debug APK
./gradlew installDebug            # Install on a connected device
./gradlew lintDebug               # Run Android Lint
./gradlew testDebugUnitTest       # Run unit tests
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Termux integration
For full toolchain access (GCC, G++, Python, Node, etc.), install
[Termux](https://f-droid.org/packages/com.termux/) and run:
```bash
pkg install python nodejs clang rust golang git openssh
```
NexusIDE auto-detects Termux, surfaces installed packages in the Build
panel, and routes compilation/run commands through it.

## 🧪 Testing

```bash
./gradlew test                  # Unit tests
./gradlew connectedAndroidTest  # Instrumented tests
```

## 🛣 Roadmap

- [x] Code editor with syntax highlighting
- [x] Compose UI scaffold + themes
- [x] Termux detection + bridge
- [x] Local + SSH terminal
- [x] Git operations
- [x] AI provider integrations
- [x] SFTP browser
- [x] Gradle + CI scaffolding
- [ ] LSP integration (kotlin-language-server, pylsp, etc.)
- [ ] In-app debugger
- [ ] Web-based preview pane
- [ ] Extension/plugin system
- [ ] Cloud sync (optional)
- [ ] ProGuard/R8 hardened release

## 🤝 Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) (TBD)
and our [Code of Conduct](CODE_OF_CONDUCT.md).

1. Fork this repo
2. Create a feature branch (`git checkout -b feat/amazing-thing`)
3. Commit your changes
4. Open a Pull Request

## 📄 License

This project is licensed under the **Apache License 2.0** — see
[LICENSE](LICENSE) for the full text.

## 🙏 Acknowledgments

- [Termux](https://termux.dev/) — making a real Linux userland on Android possible
- [JGit](https://www.eclipse.org/jgit/) & [JSch](http://www.jcraft.com/jsch/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose) team
- Every open-source library that made this possible

---

<div align="center">
Made with ❤️ for developers who code on the go.
</div>
