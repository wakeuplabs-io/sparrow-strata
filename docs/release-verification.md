# Release Verification

This document describes the GPG release signing and verification process for Sparrow (Strata Edition). It covers the [team release process](#team-release-process) (CI build through GitHub Release), how trusted public keys are bundled into the application, and how end users verify downloads.

## How verification works

Sparrow uses OpenPGP (GPG) signatures so users can verify that a release was published by a trusted entity. A release is considered valid when:

1. Every bundled trusted signer key in `config/gpg/` has a valid, non-expired signature on the manifest.
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

`ReleaseVerificationService` parses every detached signature block in the combined `.asc` file, matches signatures to bundled keys, and enforces the release policy: **all** keys in `config/gpg/` must have signed the manifest with a valid, non-expired key. Additional valid signatures from user-supplied or GnuPG keys are shown but are not required for policy.

After signatures pass the policy check, the app separately SHA-256 hashes the selected release file and compares it to the corresponding entry in the manifest. Both checks must pass for the release to be considered verified.

## Multi-employee signing (RFP)

The RFP requires that users should be able to verify that a release was published and approved by **multiple employees** of Alpen Labs. Strata enforces this as follows:

1. **Combined signatures:** Multiple team members sign the same manifest. Their individual detached GPG signatures are concatenated into one `sparrow-<version>-manifest.txt.asc` file for download.
2. **Verification policy:** `ReleaseVerificationService.verifyAll()` collects every valid signature from the combined `.asc` file, lists each bundled signer with status (valid, expired, missing, or invalid), and requires signatures from **all** bundled keys before marking the release verified.

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

### Team release process

Releases are a coordinated team effort. One person acts as **release coordinator**; every bundled signer approves the same manifest from their own machine. Private signing keys never enter CI, the repo, or shared drives.

#### Roles

| Role | Responsibility |
|------|----------------|
| **Release coordinator** | Triggers the CI build, downloads artifacts, assembles the release file set, generates the manifest, collects detached signatures, and publishes the GitHub Release. |
| **Signers** (one per key in `config/gpg/`) | Generate and hold their own GPG keypair; provide only their **public** key for bundling; verify the manifest, then sign it locally and return the detached `.asc` to the coordinator. |

#### Security principles

- **Private keys stay local.** Signers generate keypairs on their own hardware. Secret keys are never committed, uploaded to GitHub, or shared with the coordinator.
- **Only public keys are shared.** Each signer exports an armored `.asc` public key and sends it to the coordinator through an agreed channel (e.g. company email, 1:1 chat). Confirm fingerprints out-of-band before adding a key to `config/gpg/`.
- **Sign the manifest, not each binary.** GPG signatures cover `sparrow-<version>-manifest.txt` only. Binary integrity is enforced by the SHA-256 hashes listed in that manifest.
- **Signers verify before signing.** Each signer should read the manifest and confirm the listed filenames and hashes match the binaries the coordinator distributed. Signing without review approves whatever is in the manifest.
- **Apple code signing is separate.** macOS `.dmg` notarization uses repo secrets in CI (`MACOS_*`). That is unrelated to Alpen GPG release signing.

#### 1. Onboard signers (one-time, or when the roster changes)

Each authorized signer, on their own machine:

```bash
# Generate a key (RSA 4096 or Ed25519); protect it with a strong passphrase
gpg --full-generate-key

# Export only the public key
gpg --armor --export team@email.com > Firstname_Lastname_team@email.com.asc

# Share the fingerprint for out-of-band verification
gpg --fingerprint team@email.com
```

The signer sends **only** `Firstname_Lastname_team@email.com.asc` to the release coordinator. The coordinator opens a PR that adds the file to [`config/gpg/`](../config/gpg/) (naming convention: `Firstname_Lastname_email.asc`), removes any placeholder test keys, and confirms the overlay:

```bash
./gradlew :drongo:jar :drongo:listBundledGpgKeys
```

Other team members verify the PR fingerprint matches what the signer communicated before merging. The application that ships in the release must be built **after** the updated public keys are on the release branch.

#### 2. Build platform binaries (GitHub Actions)

The coordinator triggers the [**Package** workflow](../.github/workflows/package.yaml):

1. Go to **Actions → Package → Run workflow**.
2. Select the **release branch or tag** to build (workflow YAML and source both come from that ref).
3. Wait for all matrix jobs to finish: Windows, Linux x86_64, Linux arm64, macOS Intel, macOS Apple Silicon.

The workflow builds installers with `./gradlew jpackage`, packages per-platform archives, and uploads **GitHub Actions artifacts** (not a GitHub Release). Download every artifact from the run page:

| Artifact | Contents |
|----------|----------|
| `Sparrow Build - Windows AMD64` | `Sparrow-<version>.msi`, `Sparrow-<version>.zip` |
| `Sparrow Build - Linux X64` / `ARM64` | `.deb`, `.rpm`, `sparrowwallet-<version>-<arch>.tar.gz` |
| `Sparrow Build - Linux … Headless` | `sparrowserver-*` equivalents |
| `Sparrow Build - macOS <arch> Unsigned` | `Sparrow-<version>-unsigned-<arch>.zip` |
| `Sparrow Build - macOS <arch> Signed` | `Sparrow-<version>-x86_64.dmg`, `Sparrow-<version>-aarch64.dmg` (only if `MACOS_*` secrets are configured) |

Artifacts are retained per the repository's Actions retention policy; download them promptly.

#### 3. Assemble the release (coordinator)

The coordinator collects all downloaded artifacts into a single directory (e.g. `release/sparrow-<version>/`), keeping **exact filenames** as produced by CI. Decide up front which files will be published — the manifest must list every published binary and nothing else.

A typical Strata release mirrors [upstream Sparrow releases](https://github.com/sparrowwallet/sparrow/releases): Windows installer and zip, Linux GUI and headless packages for both architectures, and macOS `.dmg` files (plus unsigned mac zips if you ship them).

#### 4. Generate the manifest (coordinator)

From the release directory:

```bash
sha256sum Sparrow-*.msi Sparrow-*.zip Sparrow-*.dmg Sparrow-*-unsigned-*.zip \
  sparrowwallet_* sparrowwallet-* sparrowserver_* sparrowserver-* \
  > sparrow-<version>-manifest.txt
```

Adjust globs to match the files you are actually publishing. Each line must be `<sha256hex>  <filename>` (two spaces between hash and name, as `sha256sum` produces).

Distribute `sparrow-<version>-manifest.txt` and the binaries (or secure links to them) to every signer for review.

#### 5. Collect signatures (each signer)

Each signer, on their own machine, verifies the manifest and signs it:

```bash
# Review hashes and filenames before signing
cat sparrow-<version>-manifest.txt

# Detached signature (uses the signer's local private key)
gpg --detach-sign --armor --output Firstname_Lastname.asc sparrow-<version>-manifest.txt
```

Each signer returns **only** their `Firstname_Lastname.asc` to the coordinator. The coordinator concatenates every detached signature into one file:

```bash
cat signer-one.asc signer-two.asc > sparrow-<version>-manifest.txt.asc
```

Order does not matter. The combined file must contain one valid signature per bundled key in `config/gpg/`.

#### 6. Publish the GitHub Release (coordinator)

1. Create a release tag (e.g. `v2.5.3-strata.1`) pointing at the built commit.
2. Open a **GitHub Release** for that tag.
3. Upload **all published binaries**, plus `sparrow-<version>-manifest.txt` and `sparrow-<version>-manifest.txt.asc`.

Users download the binary they need plus the manifest and signature files, then verify via **Tools → Verify Download** (see [User verification](#user-verification) below).

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

- **Signed By** — one row per bundled trusted signer showing user ID, fingerprint, timestamp, and status (valid, expired, missing, or invalid). Additional non-bundled signers are listed separately.
- **Release Hash** — SHA-256 of the release file and whether it matched the manifest.
- **Verified** — final pass/fail result and a link to open the installer. Verification passes only when all bundled signers are valid and the release hash matches.

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

If multiple signatures are present in the `.asc` file, GnuPG verifies each one. Inspect the output to confirm that every trusted signer listed in `config/gpg/` has signed the manifest.

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
