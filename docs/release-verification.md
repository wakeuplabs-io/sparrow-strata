# Release Verification

This document describes the GPG release signing and verification process for Sparrow (Strata Edition). It covers how releases are signed by the team, how trusted public keys are bundled into the application, and how end users verify downloads.

## How verification works

Sparrow uses OpenPGP (GPG) signatures so users can verify that a release was published by a trusted entity. A release is considered valid when:

1. The manifest signature verifies against at least one trusted public key.
2. The SHA-256 hash of the downloaded binary matches the corresponding entry in the manifest.

### What gets signed

Each release ships three artifacts alongside the binaries:

- **Manifest** (`sparrow-<version>-manifest.txt`): a plain-text file with one line per release file in the format `<sha256hex>  <filename>`.
- **Signature** (`sparrow-<version>-manifest.txt.asc`): a detached armored GPG signature over the manifest.
- **Binaries**: the installers and archives whose hashes are listed in the manifest.

### Trust store

The set of trusted public keys is determined by the `.asc` files packaged under `gpg/` inside `drongo.jar`. At startup, `PGPUtils.getApplicationKeyRingCollection()` loads every bundled `.asc` file as a trusted public key. There is no separate configuration file — the packaged key listing *is* the trust store.

For Strata builds, these keys come from [`config/gpg/`](../config/gpg/) via a build-time overlay (see [Configuring signers](#configuring-signers) below). Upstream Sparrow keys in the `drongo` submodule are excluded at build time.

### Verification logic

When verifying a signature, the app tries the following key sources:

1. **User-supplied key** — a `.asc` file the user provides manually in the UI (`PGPKeySource.USER`).
2. **System GnuPG keyring** — `~/.gnupg/pubring.kbx` or `pubring.gpg`, or the path in `$GNUPGHOME` (`PGPKeySource.GPG`).
3. **Bundled application keys** — everything packaged under `gpg/` in the application JAR (`PGPKeySource.APPLICATION`).

One valid signature from any of these sources is sufficient. The result includes the signer's user ID, fingerprint, and whether the key was expired at the time of signing.

After a valid signature is found, the app separately SHA-256 hashes the selected release file and compares it to the corresponding entry in the manifest. Both checks must pass for the release to be considered verified.

## Multi-employee signing (RFP)

The RFP requires that users should be able to verify that a release was published and approved by **multiple employees** of Alpen Labs. The current implementation accepts any one valid signature from a trusted source and does not enforce a minimum number of distinct signers.

### Proposed enhancement

To address the multi-employee requirement while still publishing a single `.asc` file:

1. **Combined signatures:** Multiple team members sign the same manifest. Their individual detached GPG signatures are concatenated into one `sparrow-<version>-manifest.txt.asc` file for download.
2. **Verification policy (future work):** `PGPUtils.verify` currently returns after the first valid signature. A future change would collect all valid signatures from the combined `.asc` file and display each signer's status in the UI.

This approach lets users verify with a single signature file while the application can eventually enforce a multi-signer policy programmatically.

## Configuring signers

### Build-time overlay for public keys

Instead of modifying the `drongo` submodule, Strata uses a build-time overlay to manage Alpen Labs trusted GPG public keys:

- **Canonical keys in main repo:** Team public keys (`.asc` files) live in [`config/gpg/`](../config/gpg/).
- **Drongo submodule independence:** The `drongo` submodule stays pinned to upstream commits without a fork for key management.
- **Safe submodule updates:** Updating `drongo` does not overwrite or remove team keys.

#### Build overlay steps

1. Place team public keys in `config/gpg/` (see [`config/gpg/README.md`](../config/gpg/README.md)).
2. At build time, `gradle/gpg-overlay.gradle` (applied from the root `build.gradle`) runs `:drongo:overlayTeamGpgKeys`, which copies `config/gpg/*.asc` into `drongo/build/generated-resources/gpg/`.
3. `:drongo:processResources` excludes upstream `gpg/**` from the submodule and merges the overlay so only team keys ship in the JAR.
4. Verify the overlay:

```bash
./gradlew :drongo:jar :drongo:listBundledGpgKeys
```

#### Key management

1. **Generate a GPG keypair for each team member.** Each signer needs their own RSA 4096 (or Ed25519) key. The key identity (name and email) is shown in the "Signed By" field during verification.

2. **Export each public key:**

```bash
gpg --armor --export team@email.com > Firstname_Lastname_team@email.com.asc
```

3. **Place keys in `config/gpg/`.** Use the naming convention `Firstname_Lastname_email.asc`.

4. **Sign a release.** At release time, authorized team members sign the manifest. Combine signatures into one file for publishing:

```bash
# Create the manifest (sha256sum on all release files)
sha256sum sparrow-*.deb sparrow-*.rpm sparrow-*.tar.gz sparrow-*.exe sparrow-*.dmg > sparrow-<version>-manifest.txt

# Each authorized team member signs the manifest
gpg --detach-sign --armor --output manifest.txt.alice.asc sparrow-<version>-manifest.txt
gpg --detach-sign --armor --output manifest.txt.bob.asc sparrow-<version>-manifest.txt

# Combine all individual signatures into one file
cat manifest.txt.alice.asc manifest.txt.bob.asc > sparrow-<version>-manifest.txt.asc

# Publish binaries, manifest, and combined signature (e.g. as GitHub Release assets)
```

## User verification

### In-app verification

Users verify a release via **Tools → Verify Download**.

| Field | File |
|-------|------|
| Signature | `sparrow-<version>-manifest.txt.asc` |
| Manifest | `sparrow-<version>-manifest.txt` (auto-detected if in same directory) |
| Public Key | optional — only needed if the signer's key is not bundled |
| Release File | the downloaded binary (`.deb`, `.exe`, `.dmg`, etc.) |

The dialog reports:

- **Signed By** — user ID and fingerprint from the matched key(s), and whether the key is bundled, from the local GnuPG keyring, or user-supplied.
- **Release Hash** — SHA-256 of the release file and whether it matched the manifest.
- **Verified** — final pass/fail result and a link to open the installer.

The app auto-discovers related files when they are in the same directory: dropping any one of signature, manifest, or release file auto-fills the others when they follow the standard naming convention.

### Terminal verification (advanced)

#### 1. Import public keys (if not already in keyring)

```bash
gpg --import Firstname_Lastname_team@example.com.asc
gpg --import AnotherTeamMember_another@example.com.asc
```

#### 2. Verify GPG signature

```bash
gpg --verify sparrow-<version>-manifest.txt.asc sparrow-<version>-manifest.txt
```

If multiple signatures are present in the `.asc` file, GnuPG verifies each one. Inspect the output to confirm the required trusted signers have signed the manifest.

#### 3. Verify release file integrity

```bash
sha256sum <downloaded_release_file>
```

Compare the output with the corresponding hash in `sparrow-<version>-manifest.txt`.

#### Example GPG output

```
gpg: Signature made Wed Jun  3 10:00:00 2026 EDT
gpg:                using RSA key 0xABCDEF1234567890
gpg: Good signature from "Alice Smith (Team Lead) <alice@example.com>" [full]
gpg: Signature made Wed Jun  3 10:05:00 2026 EDT
gpg:                using RSA key 0xABCDEFABCDEF1234
gpg: Good signature from "Bob Jones (Developer) <bob@example.com>" [full]
```

Confirm that the named signers are part of the trusted release signers for this distribution.

## Open questions

1. Is multi-member verification enforcement required, or is a single valid signature from any trusted source sufficient?
2. Is standard upstream Sparrow reproducibility (deterministic Linux/Windows builds, best-effort macOS) sufficient, or is further macOS unsigned-app reconstruction needed?
3. For Apple releases, is an Apple Developer account already set up for code signing and distribution?
