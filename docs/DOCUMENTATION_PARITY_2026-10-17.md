# Poolt — documentation/code review (2026-10-17)

## Scope
Read `main` at `ff3de82056`; inspected README, Android/Kotlin entry paths, driver registry, catalog repository and CI manifest. No Android Gradle build or physical device pairing/IR testing executed.

## What the code actually does
- `app/src/main/java/app/poolt/core/DriverRegistry.kt` resolves `Device.driverId` to a `DeviceDriver` and aggregates per-driver discovery without duplicate `Device.id`.
- `app/src/main/java/app/poolt/drivers/DriverCatalogRepository.kt` reads a cached catalog from SharedPreferences, falls back to bundled `driver_catalog.json`, and refreshes from the version-controlled `drivers/catalog.json` URL.
- Native/packaged driver files include `SamsungDriver.kt`, `LgWebOsDriver.kt`, `RokuDriver.kt` and parser/package definitions.
- `app/src/main/assets/ir_catalog.json` and IR encoders are a distinct subsystem; don't assume network drivers and IR drivers use identical capabilities.
- `.github/workflows/android-debug.yml` is the existing APK build workflow. The repo has no dedicated architecture, agent or test contract docs.

## Next actions
1. Add `docs/ARCHITECTURE.md` based on existing classes, differentiating discovery/driver registry, declarative packages, transport implementation, IR and UI. No manufacturer-enum rewrite.
2. Add `AGENTS.md` to route AI edits through the code and verification, with a prohibition on untested pairing/protocol claims.
3. Test `./gradlew assembleDebug` in an Android toolchain checkout; validate network device protocols on actual TVs/boxes and IR output on real hardware before declaring support.
4. Preserve all driver packages/catalog entries and bundled fallback files unless a consumer audit demonstrates exact redundancy; do not delete or auto-disable them on documentation grounds.
