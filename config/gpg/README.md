# GPG Public Keys

This directory holds the Alpen Labs team GPG public keys that are bundled into the application at build time.

## Current keys

The `.asc` files here are **test placeholders** for development and CI. They must be replaced with real Alpen Labs signer public keys before any production release.

| File | Purpose |
|------|---------|
| `Alice_Test_alice@example.com.asc` | Mock test signer (replace before release) |
| `Bob_Test_bob@example.com.asc` | Mock test signer (replace before release) |

## How the overlay works

At build time, `gradle/gpg-overlay.gradle` copies `config/gpg/*.asc` into `drongo/build/generated-resources/gpg/` and packages only those keys into `drongo.jar`. Upstream keys in the `drongo` submodule (`drongo/src/main/resources/gpg/`) are excluded and never shipped.

Verify which keys are bundled:

```bash
./gradlew :drongo:jar :drongo:listBundledGpgKeys
```

## Adding real signer keys

1. Each team member who will sign releases generates their own GPG key (RSA 4096 recommended). The name and email shown on the key appear in the app's "Signed By" field during verification.

2. Export each public key:

```bash
gpg --armor --export team@email.com > Firstname_Lastname_team@email.com.asc
```

3. Remove the mock test keys and place the real `.asc` files in this directory.

4. Use the naming convention `Firstname_Lastname_email.asc` (e.g. `Jane_Doe_jane@alpenlabs.io.asc`).

5. Rebuild and confirm the overlay:

```bash
./gradlew :drongo:jar :drongo:listBundledGpgKeys
```

The output should list only the team keys you added — not upstream Sparrow keys such as `Craig_Raw_...`.

## Signing a release

See [Team release process](../../docs/release-verification.md#team-release-process) in `docs/release-verification.md` for the full coordinator and signer workflow (CI build, manifest, multi-signer GPG, GitHub Release).
