# Security Policy

## Supported versions

| Version | Supported          |
|---------|--------------------|
| 0.1.x   | ✅ Active          |
| < 0.1   | ❌ End of life     |

## Reporting a vulnerability

Please **do not** open a public issue for security vulnerabilities. Send a
private report to the maintainers via GitHub's "Report a vulnerability"
button on the Security tab, or contact the repo owner directly.

We will:

1. Acknowledge receipt within **3 business days**.
2. Provide an initial assessment within **10 business days**.
3. Coordinate a fix and disclosure timeline.

Please include:

- A clear description of the issue
- Reproduction steps
- Impact assessment
- Any known mitigations

## Scope

In-scope:

- The NexusIDE app and any bundled binaries.
- Official Termux integration shims.
- The NexusIDE GitHub Actions workflows.

Out-of-scope:

- Issues in upstream dependencies — please report to the relevant project
  (Termux, JGit, etc.).
- Social engineering attacks.
- Self-hosted user data — NexusIDE does not transmit your code anywhere
  unless you explicitly configure it to do so.

## Best practices for users

- **Use the official release** from the repo or Play Store (when available).
- **Keep Termux up to date** (`pkg update && pkg upgrade`).
- **Do not grant `WRITE_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE`**
  unless you need to edit files outside the app sandbox.
- **Review AI provider keys** before pasting them into NexusIDE.
- **Enable biometric lock** in Settings → Security.
