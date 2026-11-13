# Pooltophone / Пультофон

Universal remote control for TVs and other devices. Android package `app.poolt` is intentionally unchanged from Poolt so existing installs, settings and device pairing continue working.

Current code ownership: `docs/ARCHITECTURE.md`; AI development rules: `AGENTS.md`; dated source/document review: `docs/DOCUMENTATION_PARITY_2026-10-17.md`. These notes do **not** certify protocol compatibility without device testing.

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

## Android APK signing

**Distribute only the signed release artifact** from
[Pooltophone signed APK](https://github.com/nkuchenov-hash/Poolt/actions/workflows/android-signed.yml).
The application ID stays `app.poolt`. A permanent RSA 3072-bit
PKCS#12 signing certificate is pinned by SHA-256:

`82:C5:C0:7A:90:E0:B1:58:97:73:F9:14:FB:CE:47:33:2F:D2:C5:D8:1B:CC:E8:44:89:57:BC:8E:14:4D:5B:03`

**One-time administrator setup:** the user who owns the private signing bundle
must open [Actions repository secrets](https://github.com/nkuchenov-hash/Poolt/settings/secrets/actions),
create a repository secret named `POOLTOPHONE_SIGNING_BUNDLE`, and paste the
entire one-line value of the private `POOLTOPHONE_SIGNING_BUNDLE.txt` file.
Never commit the keystore, bundle, private key, or passwords to this public
repository; keep the private archive backed up offline.

Every push to `main` then builds `app-release.apk`, verifies its Android
signature and the pinned SHA-256 certificate, and publishes an installable
`Pooltophone-SIGNED-<run>` artifact. Builds fail without the secret or
if the certificate fingerprint differs. The private keystore is recreated
from the secret only in the runner's temporary directory and removed after use.

**Migration:** older GitHub Actions debug APKs used ephemeral default debug
keys, and cannot be updated to this production signing certificate without
one uninstall/reinstall. Back up any app data first. After installing the
first permanent-key signed release, future releases using this key and
increasing `versionCode` can update in place. Android may also reject an
upgrade if the package name changes or versionCode decreases.

The separate debug workflow produces **internal-only** builds.
Never distribute `app-debug.apk` as a user update.


## Stack

- Android
- Kotlin
- Jetpack Compose
- minSdk 26
