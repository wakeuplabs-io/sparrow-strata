# Release Verification

This document details the GPG release signing and verification process within Strata. This deliverable fulfills the requirement from the proposal's phase 1: "Define the multi-employee release signing process using GPG, including key publication and verification instructions for end users".

---

## Current Implementation

### How it works: One Valid Signature (any trusted source)

Sparrow Wallet uses OpenPGP (GPG) signatures to allow users to verify that a release was published by a trusted entity. The process involves release signing by the team and in-app verification by the user.

A release is considered valid if the manifest signature can be verified by **any one** of the public keys from its combined trusted sources. This means that if multiple public keys are bundled or available to the application, only one matching signature is sufficient for the verification to pass.

#### What gets signed

Each release ships three artifacts alongside the binaries:

- **Manifest** (`sparrow-<version>-manifest.txt`): a plain-text file with one line per release file in the format `<sha256hex>  <filename>`.
- **Signature** (`sparrow-<version>-manifest.txt.asc`): a detached armored GPG signature over the manifest.
- **Binaries**: the actual installers and archives whose hashes are listed in the manifest.

#### Trust store

The set of trusted public keys is determined by the `.asc` files in:

```
drongo/src/main/resources/gpg/
```

Every file in that directory is loaded at startup by `PGPUtils.getApplicationKeyRingCollection()` and treated as a trusted public key. There is no separate configuration file — the filesystem listing *is* the trust store. These keys are embedded in the application JAR at build time.

#### Verification logic

When verifying a signature, the app tries the following key sources in order:

1. **User-supplied key** — a `.asc` file the user provides manually in the UI (`PGPKeySource.USER`).
2. **System GnuPG keyring** — `~/.gnupg/pubring.kbx` or `pubring.gpg`, or the path in `$GNUPGHOME` (`PGPKeySource.GPG`).
3. **Bundled application keys** — everything in `drongo/src/main/resources/gpg/` (`PGPKeySource.APPLICATION`).

One valid signature from any of these sources is sufficient. The result includes the signer's user ID, fingerprint, and whether the key was expired at the time of signing.

After a valid signature is found, the app separately SHA-256 hashes the selected release file and compares it to the corresponding entry in the manifest. Both checks must pass for the release to be considered verified.

---

## Addressing the "Multiple Employees" Requirement (RFP)

The RFP requires that "The user SHOULD be able to cryptographically verify that the release ... was published and approved by **multiple employees** of Alpen Labs." The current implementation of "one valid signature from any trusted source" does not strictly fulfill this requirement, as it does not enforce a minimum number of distinct signers.

### Proposed Solution: Enhancing Multi-Employee Signature Verification

To address the "multiple employees" requirement more robustly while still providing a single `.asc` file for user convenience:

1. **Combined Signatures:** Multiple team members will sign the same manifest file, and their individual detached GPG signatures will be concatenated into a *single* `sparrow-<version>-manifest.txt.asc` file. This single file will then be published for users to download.
2. **Code Modification for Verification Policy:** The application's `PGPUtils.verify` method currently returns after finding the *first* valid signature. To enforce a multi-employee policy, this logic will be modified to:
  - **Collect all valid signatures:** Iterate through all signatures present in the combined `.asc` file and collect `PGPVerificationResult` for every successfully verified signature against the bundled (or other trusted) public keys.
  - **Enumerate all signers:** Collect all signatures and display them in the UI.
  - The UI would then clearly indicate the status of each signature (e.g., missing, invalid, valid) and which signers are associated with the release.

This approach allows users to verify with a single signature file, while the application programmatically enforces the "multiple employees" requirement based on the actual signatures contained within.

---

## Configuring Signers

### Strategy: Build-time Overlay for Public Keys

Instead of directly modifying the `drongo` submodule, we will use a build-time overlay approach to manage Alpen's trusted GPG public keys. This strategy offers the following benefits:

- **Canonical Keys in Main Repo:** The team's canonical GPG public keys (`.asc` files) will reside in the main `alpenlabs/sparrow` repository, e.g., in `config/gpg/`.
- **Drongo Submodule Independence:** The `drongo` submodule can remain pinned to upstream commits without needing to fork it solely for key management.
- **Simplified Submodule Updates:** Updating the `drongo` submodule will not inadvertently overwrite or remove team's trusted public keys.

#### Implementation Steps for Build-time Overlay:

1. **Define a dedicated directory** in `alpenlabs/sparrow` repository for the team's GPG public keys, e.g., `config/gpg/`.
2. **Place the team's public keys** (exported as `.asc` files) into this directory.
3. **Create a Gradle task** within `alpenlabs/sparrow` (e.g., in `build.gradle` or a custom `*.gradle` file) that performs the following actions:
  - Wipes the contents of `drongo/src/main/resources/gpg/`.
  - Copies the team's `.asc` files from `config/gpg/` into `drongo/src/main/resources/gpg/`.
4. **Hook this Gradle task** to an appropriate build phase, such as `:drongo:processResources` or `:drongo:compileJava`, to ensure it runs before the `drongo` JAR is packaged.

#### Detailed Steps for Key Management:

1. **Generate a GPG keypair for each team member**
  Each team member who will sign releases needs a GPG key: Use RSA 4096 or Ed25519. The key identity (name and email) will be shown to the user in the "Signed By" field during verification, so use something recognizable.
2. **Export each public key**
  ```shell
    gpg --armor --export team@email.com > TeamMember_team@email.com.asc
  ```
    Use a descriptive filename — by convention the existing keys follow the pattern `Firstname_Lastname_email.asc`, e.g. `TeamMember_team@example.com.asc`.
3. **Place keys in `config/gpg/`**
  Copy the team's exported public keys into the dedicated directory:
4. **Create Gradle task (example `build.gradle` snippet)**
  This is an example of how one might configure the Gradle task. Adaptation to the specific `build.gradle` structure would be required.
    This task will ensure that `drongo/src/main/resources/gpg/` always contains only the team's specified keys at build time.
5. Sign a release

At release time, team members involved in the release process will sign the manifest. Multiple signatures should be combined into a single file for publishing.

```shell
# Create the manifest (sha256sum on all release files)
# This step might be part of an automated build or CI pipeline
sha256sum sparrow-*.deb sparrow-*.rpm sparrow-*.tar.gz sparrow-*.exe sparrow-*.dmg > sparrow-<version>-manifest.txt

# Each authorized team member signs the manifest with their private key
gpg --detach-sign --armor --output manifest.txt.alice.asc sparrow-<version>-manifest.txt
gpg --detach-sign --armor --output manifest.txt.bob.asc sparrow-<version>-manifest.txt
# ... and so on for other signers

# Combine all individual signatures into one file for the user
cat manifest.txt.alice.asc manifest.txt.bob.asc > sparrow-<version>-manifest.txt.asc

# Publish the binaries, sparrow-<version>-manifest.txt, and the combined sparrow-<version>-manifest.txt.asc
# (e.g. as GitHub Release assets or via the distribution mechanism)
```

---

## User Verification

### UI Verification

Users verify a release via **Tools → Verify Download** in the application.

They need to supply:


| Field        | File                                                                  |
| ------------ | --------------------------------------------------------------------- |
| Signature    | `sparrow-<version>-manifest.txt.asc`                                  |
| Manifest     | `sparrow-<version>-manifest.txt` (auto-detected if in same directory) |
| Public Key   | optional — only needed if the signer's key is not bundled             |
| Release File | the downloaded binary (`.deb`, `.exe`, `.dmg`, etc.)                  |


The dialog reports:

- **Signed By** — the user ID and fingerprint from the matched key(s), and whether it is bundled, from the local GnuPG keyring, or user-supplied. With the proposed multi-employee solution, this section would ideally list all verified signers meeting the policy.
- **Release Hash** — the SHA-256 of the release file and whether it matched the manifest.
- **Verified** — final pass/fail result and a link to open the installer.

The app also auto-discovers the related files if they are all in the same directory: dropping any one of the three files (signature, manifest, or release) will auto-fill the others when they follow the standard naming convention.

### Terminal Command Verification (Advanced Users)

For advanced users, verification can also be performed manually using command-line tools. This method requires a GnuPG installation and familiarity with terminal commands.

#### 1. Import Public Keys (if not already in keyring)

To trust the team's public keys, they must be imported into the local GnuPG keyring. This step is typically done once.

```shell
gpg --import TeamMember_team@example.com.asc
gpg --import AnotherTeamMember_another@example.com.asc
# ... import all relevant team public keys
```

#### 2. Verify GPG Signature

Verify the detached GPG signature against the manifest file.

```shell
gpg --verify sparrow-<version>-manifest.txt.asc sparrow-<version>-manifest.txt
```

The output will indicate the signing key(s) and their validity. If multiple signatures are present in `sparrow-<version>-manifest.txt.asc`, GnuPG will attempt to verify each one. Users must manually inspect the output to ensure the required number of trusted signers (based on the team's policy) have signed the manifest.

#### 3. Verify Release File Integrity (SHA256 Check)

Calculate the SHA256 hash of the downloaded release binary and compare it with the hash listed in the `sparrow-<version>-manifest.txt` file.

```shell
sha256sum <downloaded_release_file>
```

Compare the output hash with the corresponding hash for `<downloaded_release_file>` found in `sparrow-<version>-manifest.txt`. A match confirms the integrity of the downloaded file.

#### Example Verification Output (GPG)

```
gpg: Signature made Wed Jun  3 10:00:00 2026 EDT
gpg:                using RSA key 0xABCDEF1234567890
gpg: Good signature from "Alice Smith (Team Lead) <alice@example.com>" [full]
gpg: Signature made Wed Jun  3 10:05:00 2026 EDT
gpg:                using RSA key 0xABCDEFABCDEF1234
gpg: Good signature from "Bob Jones (Developer) <bob@example.com>" [full]
```

Users would need to confirm that `Alice Smith` and `Bob Jones` are indeed part of the trusted signers for the release. The interpretation of "good signature" and the identification of signers is a manual step in terminal verification.