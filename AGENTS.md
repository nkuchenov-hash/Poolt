# Poolt — AI development guide

This file routes work to actual Kotlin source. It is not a new product proposal.

## Read first

- `README.md`: user-facing scope, driver categories and APK workflow.
- `docs/ARCHITECTURE.md`: verified layer map.
- `docs/DOCUMENTATION_PARITY_2026-10-17.md`: dated static documentation review.

## Source ownership

- `app/src/main/java/app/poolt/MainActivity.kt`: Android Activity and Compose UI.
- `MainViewModel.kt`: UI state, IR/Wi-Fi mode, scanning, pairing and commands.
- `core/DeviceDriver.kt`, `core/DriverRegistry.kt`: stable driver interface and discovery registry.
- `drivers/`: native protocol implementations, declarative package parsing and catalog cache/refresh.
- `ir/`: IR profile repository, transmitter and waveform encoder.
- `drivers/catalog.json`: remotely refreshed catalog; `app/src/main/assets/driver_catalog.json` is a bundled fallback.
- `app/src/main/assets/ir_catalog.json`: bundled IR reference data.
- `.github/workflows/android-debug.yml`: CI debug APK build.

## Mandatory guardrails

1. Preserve string driver IDs; never replace with a fixed manufacturer enum.
2. Differentiate a catalog entry from a genuinely functioning protocol implementation; do not promise control for untested TV models.
3. Preserve the last usable local catalog when remote refresh fails. Do not drop cached/bundled fallback data.
4. Keep IR transport separate from network driver discovery and pairing. An Android phone without an IR emitter must have explicit non-IR behavior.
5. Do not rewrite working UI or protocols to satisfy documentation alone; verify actual code and devices.
6. Do not delete driver packages, fixtures, catalogs or old firmware/protocol notes without an explicit usage audit.
7. Do not commit secrets, local credentials, device addresses or pairing tokens.

## Verification

Run `gradle :app:assembleDebug --stacktrace` in a configured Android toolchain, consistent with the existing CI workflow. For changed IR/Wi-Fi flows, test on a physical phone and corresponding real hardware. Report exact build SHA, commands, test results and unresolved limitations. Static code inspection is **not** device acceptance.
