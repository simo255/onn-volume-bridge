# Contributing

This repository is strictly a volume/mute bridge for onn remotes. Keep launcher
features, media catalogs, debloating, Home takeover, power automation and unrelated
remote buttons out of this project.

To add a protocol:

1. Verify its published control API or a primary implementation. Do not assume the
   TV brand implies a particular OS or control protocol.
2. Add a `TvProtocol` for HTTP, or a `VolumeTarget` for an asynchronous protocol.
3. Keep only UP, DOWN and MUTE commands. Do not add generic arbitrary-command APIs.
4. Validate local endpoints, pairing and credentials. Do not relax existing pins
   or enable redirects to make a failing TV appear supported.
5. Add request, response, error and no-side-effect probe tests. Prefer a local fake
   server for transport behavior rather than mocking away the whole network path.
6. Update provider selection, optional setup tooling and compatibility docs.
7. Label the adapter experimental until a physical remote/TV acceptance report
   establishes the exact model, OS and firmware boundary.

Run JDK 17 / Android SDK 34 checks:

```sh
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

No APKs, keys, IP inventories, local.properties, account information or raw device
captures in Git. Documentation screenshots must show only the app UI, with no
addresses, identifiers, credentials or account details. The Gradle wrapper is build tooling, not a prebuilt app. Keep MIT
notices for derived source. Do not modify a contributor's installed TV as part of
routine repository work; device deployment needs a specific test scope.
