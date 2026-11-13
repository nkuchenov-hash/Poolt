#!/usr/bin/env python3
"""Restore the permanent signing keystore from a GitHub Actions repository secret.

The secret is a base64-encoded JSON object. The keystore and passwords must
NEVER be committed to this (public) repository or printed into CI logs.
"""
import base64
import json
import os
import pathlib
import re
import sys


def main() -> None:
    raw = os.environ.get("POOLTOPHONE_SIGNING_BUNDLE", "").strip()
    if not raw:
        sys.exit(
            "POOLTOPHONE_SIGNING_BUNDLE is not configured. "
            "Add the private signing bundle in GitHub Settings -> Secrets and variables -> Actions. "
            "A signed APK was NOT produced."
        )
    try:
        data = json.loads(base64.b64decode(raw, validate=True))
        keystore = base64.b64decode(data["keystore_b64"], validate=True)
        password = data["store_password"]
        key_password = data["key_password"]
        alias = data["alias"]
        if not (0 < len(keystore) <= 1024 * 1024):
            raise ValueError("Invalid signing keystore length")
        if not isinstance(password, str) or not password or "\n" in password:
            raise ValueError("Invalid store password")
        if not isinstance(key_password, str) or not key_password or "\n" in key_password:
            raise ValueError("Invalid key password")
        if not isinstance(alias, str) or not re.fullmatch(r"[a-zA-Z0-9_-]+", alias):
            raise ValueError("Invalid key alias")
    except (ValueError, KeyError, TypeError, json.JSONDecodeError) as exc:
        sys.exit("Signing bundle is malformed; no APK was signed: " + type(exc).__name__)

    target = pathlib.Path(os.environ["RUNNER_TEMP"]) / "pooltophone-private-signing.p12"
    target.write_bytes(keystore)
    target.chmod(0o600)

    # Mask these if accidentally printed by a later command.
    print("::add-mask::" + password)
    print("::add-mask::" + key_password)
    with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as env:
        env.write(f"POOLTOPHONE_SIGNING_KEYSTORE={target}\n")
        env.write(f"POOLTOPHONE_SIGNING_STORE_PASSWORD={password}\n")
        env.write(f"POOLTOPHONE_SIGNING_KEY_PASSWORD={key_password}\n")
        env.write(f"POOLTOPHONE_SIGNING_KEY_ALIAS={alias}\n")
    print("Permanent signing key loaded securely from GitHub Actions secret.")


if __name__ == "__main__":
    main()
