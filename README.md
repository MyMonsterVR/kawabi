# kawabi

A native Android manga reader, built from scratch because the old fork it
replaced got creaky. Talks to its own backend (`kawabi-server`) and shares
an account with the browser reader (`kawabi-web`) — read a chapter on your
phone, pick it up on the couch, same progress either way.

Personal project, open-sourced because there's no good reason not to. If
you find it useful or want to poke at the code, welcome aboard.

## Status

Library, reader, search, settings, backup/restore, and MAL+Kitsu progress
tracking (account login, per-manga linking, chapter sync) are all shipped
and verified on-device.

## Modules

| Module    | Type            | Purpose |
|-----------|-----------------|---------|
| `app`     | Android app     | Compose UI, composition root (DI wiring, Activities/screens). |
| `core`    | Kotlin/JVM      | Cross-cutting utilities with no Android dependency (coroutine dispatchers, small extensions). |
| `domain`  | Kotlin/JVM      | Domain models, repository interfaces, interactors. No SQLDelight or network dependency — only knows about repository interfaces `data` implements. |
| `data`    | Android library | Repository implementations: local storage (SQLDelight) and the HTTP client for `kawabi-server`. |

Dependency direction: `app` → `data`/`domain`/`core`; `data` → `domain` +
`core`; `domain` → `core` only.

## Building

Requires JDK 17 and the Android SDK (`local.properties` is machine-specific
and gitignored — set `sdk.dir` to your own SDK path before building).

```
./gradlew assembleDebug
```

## Installing over wireless ADB

No cable needed once paired once:

```
adb pair <phone-ip>:<pairing-port>   # code shown in Developer Options > Wireless debugging > Pair device with pairing code
adb connect <phone-ip>:<port>        # port shown on the main Wireless debugging screen
adb devices                          # confirm it shows up, no "unauthorized"
./gradlew installDebug
```

The phone and this machine need to be on the same network. Re-pairing is
only needed if the phone's Wireless debugging is turned off and back on;
plain `adb connect` is enough after that as long as both stay on.

## Tech choices

- **Kotlin/Compose**, Material3.
- **SQLDelight** for local storage — chosen for KMP portability and a
  reactive-query pattern that fits this schema shape well.
- **Koin** for dependency injection — plain, reflection-light, and easy to
  follow in an open-source codebase.
- **OkHttp + kotlinx.serialization** for the `kawabi-server` client, and for
  the MAL/Kitsu tracker APIs.

## Related repos

- `kawabi-server` — Go backend this app talks to.
- `kawabi-web` — Next.js browser reader, same backend, same account.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the practical bits, and
[`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) for the "don't be a jerk" bits.
Issues and PRs both welcome — this moves at hobby-project pace, so don't
expect same-day replies, but everything gets read.
