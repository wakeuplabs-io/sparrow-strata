# Reproducible Builds Spike

This document details the status of reproducible builds for Sparrow, identifying current capabilities, limitations, and a proposed approach to enhance reproducibility across all supported platforms. This deliverable fulfills the requirement from proposal phase 1, "Run reproducible builds spike to identify and address non-deterministic build artifacts (timestamps, paths, metadata) across all three platforms"

---

## Current Status

Sparrow Wallet's existing `docs/reproducible.md` provides a comprehensive guide for achieving reproducible builds for Linux and Windows platforms. As of v1.5.0 and later, users can recreate exact binaries (specifically, the contents of the `.tar.gz` and `.zip` files) by following the documented steps. This process involves using a specific Java runtime version and a standardized build environment.

### Achieved Reproducibility: Linux and Windows

For Linux and Windows, the existing documentation confirms that the application binaries contained within the `.tar.gz` and `.zip` archives can be reproduced bit-for-bit. This allows technical users to:

1. Set up the exact build environment (Java version, specific build tools).
2. Clone the source code at a specific tag, including submodules.
3. Build the application using `./gradlew jpackage`.
4. Compare the generated output directory (`build/jpackage/Sparrow`) against the contents of the officially released `.tar.gz` or `.zip` file using `diff -r`.

This process verifies the integrity of the application payload itself, ensuring that the source code corresponds to the distributed binaries.

### Current Limitations: macOS, Debian, RPM, and EXE Installers

The existing Sparrow documentation notes the following limitations:

- **macOS binaries:** "the OSX binary is code signed and thus can't be directly reproduced yet." (from `docs/reproducible.md`). This is a critical point as code signing introduces non-deterministic elements (timestamps, developer certificates) that make bit-for-bit reproduction impossible without access to the exact signing environment and private keys.
- **Installer Packages (`.deb`, `.rpm`, and `.exe`):** "Due to minor variances, it is not yet possible to reproduce the installer packages" (from `docs/reproducible.md`). These installers often contain platform-specific metadata, build timestamps, and packaging details that lead to non-deterministic output.

---

## Addressing Reproducibility for macOS

The primary challenge for macOS reproducibility lies in the mandatory code signing and notarization process that Apple enforces for distributed applications. This process inherently alters the application bundle in a non-deterministic way from a build-from-source perspective.

### Proposed Solution: Separate Artifacts for Verification vs. Distribution

To address the macOS reproducibility requirement (RFP 1.2) while maintaining a user-friendly distribution channel (RFP 1.5), we propose a dual-artifact strategy:

1. **Unsigned macOS Archive for Reproducibility Verification:**
  - **Description:** For every release, an **unsigned** macOS application bundle (`.app`) will be built, packaged into a `.tar.gz` archive, and published alongside the official signed `.dmg` installer.
  - **Purpose:** This `.tar.gz` archive will serve as the verifiable artifact for technical users. It will be reproducible bit-for-bit using the same build instructions as Linux/Windows, allowing users to build the `.app` locally and `diff -r` against the published unsigned archive.
  - **Manifest Integration:** The SHA-256 hash of this unsigned `.tar.gz` will be included in the release manifest (`sparrow-<version>-manifest.txt`), enabling cryptographic verification of the download's integrity.
2. **Signed macOS `.dmg` Installer for General Distribution:**
  - **Description:** The standard signed and notarized `.dmg` installer will continue to be the primary distribution method for macOS users.
  - **Purpose:** This provides a seamless installation experience, avoids Gatekeeper warnings, and aligns with user expectations for macOS applications (RFP 1.5).
  - **Limitations:** This `.dmg` installer will **not** be bit-for-bit reproducible due to the code signing and notarization process. However, its integrity can still be verified cryptographically via its hash in the release manifest and the GPG signature over that manifest.

### Implementation Details for macOS Reproducibility

- **Build System Configuration:** The existing `jpackage` Gradle task on macOS already generates an unsigned `.app` bundle (as `skipInstaller = os.macOsX` is `true` by default). The modification will involve packaging this `.app` bundle into a `.tar.gz` archive as a separate build artifact.
- **Documentation:** Clear instructions will be added to `docs/reproducible.md` (or a new dedicated macOS section) guiding users through:
  - Building the unsigned `.app` locally.
  - Packaging it into a `.tar.gz`.
  - Comparing it with the officially released unsigned `.tar.gz` artifact.
  - Instructions for a one-time Gatekeeper bypass (e.g., `xattr -dr com.apple.quarantine Sparrow.app` or right-click -> Open) for locally built or downloaded unsigned `.app` bundles.
- **Manifest Update:** The release process will be updated to ensure the SHA-256 hash of the unsigned macOS `.tar.gz` is included in the manifest, alongside the hash of the signed `.dmg`.

---

## Addressing Reproducibility for Installer Packages (DEB, RPM, EXE)

The "minor variances" in installer packages (DEB, RPM, EXE) that prevent bit-for-bit reproducibility are a known challenge in the reproducible builds community. These often stem from:

- Build environment specifics (e.g., specific OS version, system libraries).
- Timestamps embedded in the archive or package metadata.
- Compression settings or tools.

### Proposed Solution: Integrity Verification via Manifest

Given the complexity of achieving bit-for-bit reproducibility for all installer packages, and the focus on the application payload itself (which is reproducible for Linux and Windows via `.tar.gz` and `.zip`), we propose that for `.deb`, `.rpm`, and `.exe` installers, we will rely on **cryptographic integrity verification** rather than bit-for-bit reproducibility from source.

- **Description:** The SHA-256 hashes of these installer packages will be included in the release manifest (`sparrow-<version>-manifest.txt`).
- **Purpose:** Users will be able to verify that the installer they downloaded matches the one intended by Alpen Labs by comparing its hash against the manifest. The manifest itself will be protected by GPG signatures.

---

## Conclusion and Recommendations

Achieving comprehensive reproducible builds is a core goal, and Alpen Labs can provide a strong guarantee of software integrity by:

1. **Maintaining Bit-for-Bit Reproducibility:** For core application payloads (Linux `.tar.gz`, Windows `.zip`, and an **unsigned macOS `.tar.gz`**), technical users will be able to verify that the distributed binaries precisely match the source code.
2. **Enhancing Cryptographic Verification:** All distributed artifacts (including macOS `.dmg`, `.deb`, `.rpm`, `.exe` installers) will have their SHA-256 hashes listed in a GPG-signed manifest, allowing users to verify download integrity against a trusted source.
3. **Clear Documentation:** Comprehensive documentation will guide users through the appropriate verification methods for each artifact type and platform, including instructions for dealing with macOS Gatekeeper for unsigned binaries.

This approach balances the stringent requirements of reproducible builds with the practical needs of broad user distribution, providing multiple layers of verification and trust.