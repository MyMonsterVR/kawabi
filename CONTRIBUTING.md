# Contributing to kawabi

Thanks for taking a look. This is a small personal-scale project, so the process is
intentionally light — but a few conventions keep the codebase consistent.

## Getting set up

See [`README.md`](README.md) for build requirements and running the app. In short:
JDK 17, the Android SDK, and a `local.properties` pointing at your own backend
(`kawabi.baseUrl=`) — there's no shared/public instance to build against.

## Before opening a PR

- Run `./gradlew assembleDebug` locally and make sure it builds clean.
- Keep changes scoped — a PR that does one thing is much easier to review than one
  that mixes a feature with unrelated refactors.
- Match the existing style in the file/module you're touching rather than
  introducing a new pattern. In particular:
  - Module layering is one-directional: `app` → `data`/`domain`/`core`; `data` →
    `domain` + `core`; `domain` → `core` only. Don't add a dependency that goes
    the other way.
  - Dependency injection is plain Koin (`single {}` / `viewModel {}` blocks in
    each module's `*Module.kt`), not an annotation processor.
  - Comments should explain *why*, not *what* — skip comments that just restate
    the code below them.
- If you're changing behavior (not just refactoring), a short note in the PR
  description on what changed and why is more useful than a long one.

## Reporting bugs / requesting features

Open an issue with as much reproduction detail as you can — device/Android
version if it's UI-related, and steps to reproduce. For backend-affecting
issues, note whether you're running your own `kawabi-server` instance or
something else.

## Code of conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). Be
respectful — that's really the whole rule.
