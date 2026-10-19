# Poolt — current Android architecture

> Evidence-based snapshot from `main` at `ff3de82056`, 2026-10-17. This is a code map, not proof that all device protocols work on real hardware.

## Stack and app entry

- Native Android/Kotlin + Jetpack Compose.
- `app/src/main/java/app/poolt/MainActivity.kt`: activity, splash startup, Compose `PooltApp` controls.
- `MainViewModel.kt`: `MainUiState` and `ControlMode` (`IR` / `WIFI`), scan/pair and action dispatch through adapters. Device presets refer to string driver IDs.

## Network driver boundary

```text
Compose UI → MainViewModel → DriverRegistry → DeviceDriver implementation → device
                         └→ DriverCatalogRepository → DriverCatalogParser
```

- `core/DeviceDriver.kt` exposes `id`, `version`, `discover()`, `pair()`, `send()`, `disconnect()`.
- `core/DriverRegistry.kt` resolves a `Device.driverId` to a native driver and aggregates discovery (deduplicates by device ID).
- `drivers/SamsungDriver.kt`, `LgWebOsDriver.kt`, `RokuDriver.kt` are native implementations present in source. They require real-device verification before claiming compatibility.
- `drivers/DriverPackage.kt` represents declarative catalog metadata, transport enum, discovery and command descriptions.
- `drivers/DriverCatalogParser.kt` parses declarative JSON to typed package models. **A parsed entry alone is not a working network driver.**

## Catalog and offline fallback

`drivers/DriverCatalogRepository.kt` tries SharedPreferences cache, then bundled asset `driver_catalog.json`, and refreshes from `https://raw.githubusercontent.com/nkuchenov-hash/Poolt/main/drivers/catalog.json`. The network refresh uses timeouts and stores parsed JSON locally. Avoid regressions that leave the user without a working local catalog when offline.

## IR boundary

- `ir/IrEncoder.kt`: NEC, extended NEC, Samsung32 and Sony pulse encodings; raw patterns handled separately.
- `ir/IrTransmitter.kt` and `ir/IrProfileRepository.kt`: platform IR capability and profile catalog (read implementation before changes).
- `app/src/main/assets/ir_catalog.json`: bundled reference data.
- No IR transmitter on device → mode must surface unavailable hardware rather than silently claiming success.

## Validation and deployment

- Gradle / Kotlin Android app in `app/`; manifest at `app/src/main/AndroidManifest.xml`.
- `.github/workflows/android-debug.yml` runs `gradle :app:assembleDebug --stacktrace` for commits pushed to `main`, uploads `app-debug.apk`.
- Confirm compilation and actual phone/TV behavior separately. No claim of passing tests or confirmed protocol support was made in this documentation-only change.

## Ownership policy

Extend driver packages rather than adding manufacturer-specific UI branches. Preserve ID compatibility and bundled fallback. Keep domain, transport, IR, UI and persistence concerns separated. Make focused changes, no speculative refactor, no unrelated deletions.
