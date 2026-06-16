# Contributing to NexusIDE

Thank you for your interest in contributing! 🎉

## 🛠 Development setup

1. **Fork & clone** the repo.
2. **Open in Android Studio** Hedgehog or later.
3. Make sure **JDK 17** is your project SDK (File → Project Structure → SDK Location).
4. Sync Gradle.
5. Pick an issue tagged `good first issue` or `help wanted`.
6. Create a branch: `git checkout -b feat/short-description`
7. Code, test, lint.
8. Push & open a PR.

## 📐 Coding standards

- **Kotlin official style** (follow Android Studio formatter).
- **Compose-first** — prefer Composable functions over XML/View-based UI.
- **Hilt** for DI.
- **Coroutines + Flow** for async work.
- **Repository pattern** for data access.
- Keep public APIs documented (KDoc).

## ✅ Before opening a PR

- [ ] `./gradlew lint` passes
- [ ] `./gradlew test` passes
- [ ] Code formatted (`./gradlew spotlessApply` if configured)
- [ ] New public APIs have KDoc
- [ ] PR description explains the change

## 🐛 Filing bugs

Use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.md) and include:
- Device + Android version
- Steps to reproduce
- Expected vs actual
- Relevant logcat (use `adb logcat | grep NexusIDE`)

## 💡 Feature requests

Use the [feature request template](.github/ISSUE_TEMPLATE/feature_request.md).
Big ideas welcome — but please describe the **problem** you're solving, not
just the solution.

## 🔒 Security issues

Please **do not** open a public issue for security vulnerabilities. Email
the maintainers privately instead (see the repo's security tab / About page).

## 📜 License

By contributing, you agree that your contributions will be licensed under
the project's [Apache 2.0 License](LICENSE).
