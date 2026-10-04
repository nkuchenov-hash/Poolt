# Poolt

Universal remote control for TVs and other devices.

## Driver architecture

Poolt uses stable string driver IDs instead of a fixed manufacturer enum.

Drivers are split into two classes:

- native drivers for protocols that need custom code, pairing, cryptography or persistent sockets;
- declarative driver packages for protocols that can be described as data.

The remote catalog is stored in `drivers/catalog.json`. The Android app has a bundled fallback catalog and can refresh the catalog from GitHub, so compatible data-driven driver definitions and metadata can be updated without changing the UI.

Each package has:
- `id`
- `version`
- minimum app version
- device type
- transport
- discovery definition
- command mapping

Use `tools/add_drivers.py` to merge many driver JSON files into the catalog at once. Existing driver IDs are upgraded when the incoming version is equal or newer.

Initial catalog entries:
- Samsung Smart TV
- LG webOS TV
- Android TV / Google TV
- Roku ECP
- Poolt demo

Complex protocols still require a native implementation before their catalog entry becomes functional. The catalog architecture prevents those implementations from leaking into the UI.

## APK

Every push to `main` runs `.github/workflows/android-debug.yml` and uploads `Poolt-debug` containing `app-debug.apk`.

## Stack

- Android
- Kotlin
- Jetpack Compose
- minSdk 26
