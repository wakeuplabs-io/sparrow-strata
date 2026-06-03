# Upstream Maintenance and Release Workflow

This document outlines the git discipline, branching strategy, and step-by-step procedures for maintaining **Sparrow (Strata Edition)** as a clean, reviewable, and up-to-date fork of [upstream Sparrow](https://github.com/sparrowwallet/sparrow).

---

## Core Objectives and Principles

To comply with the requirements of the PRD/RFP and align with engineering best practices, our upstream maintenance workflow is designed around three core pillars:

1. **Reviewability and Clean Diffs:** Any technical user or auditor must be able to easily review the exact changes made for the Strata Edition. Our custom logic must sit cleanly on top of the upstream release base. A simple `git diff <upstream-tag>...HEAD` should return only Alpen-specific changes, with zero noise.
2. **Conventional Commits:** All changes introduced to the fork must follow [Conventional Commits](https://www.conventionalcommits.org/) (e.g., `feat(deposit): ...`, `fix(reclaim): ...`, `chore(deps): ...`). This ensures a structured, legible commit history that is easy to audit and automate.
3. **Frictionless Upstream Upgrades:** When upstream releases a new tag, bringing those changes over should be a structured, repeatable, and low-risk process.

---

## Repository Architecture

Our Git repository maintains connections to two primary remotes:

- `**origin`**: The Alpen Labs remote repository hosting our customized Sparrow (Strata Edition) codebase.
- `**upstream**`: The official Sparrow Wallet repository (`https://github.com/sparrowwallet/sparrow.git`).

### Branching Strategy

To keep development separated from official, clean releases, we maintain the following branches:

- `**main**`: The primary development and integration branch for Strata-specific features. All active feature branches target `main` via pull requests.
- `**release/<version>-strata**` (e.g., `release/2.5.2-strata`): Temporary stabilization and release preparation branches created when syncing with a new upstream release tag.
- `**upstream-tracking**` (optional): A branch tracking the upstream `master` or latest releases to easily reference upstream changes.

---

## Commit and Submodule Discipline

### 1. Custom Commit Isolation

To keep `git diff <upstream-tag>...HEAD` completely clean, we **never** interleave upstream merges into our custom commit history on release branches. Instead, we use a **rebase-centric workflow** for aligning with new upstream tags. This places all Strata-specific commits linearly at the very top of the commit graph, directly after the upstream base tag.

### 2. Submodule Management

Upstream Sparrow includes two Git submodules:

- `drongo` (`sparrowwallet/drongo`)
- `lark` (`sparrowwallet/lark`)

**Best Practice Design (Build-time Overlay):**
Rather than fork and maintain parallel downstream versions of the `drongo` and `lark` submodules (which exponentially increases maintenance overhead), we keep these submodules pinned precisely to the commits specified by the upstream release tag.
Any customization we require—such as replacing Craig Raw’s GPG release verification keys with Alpen's GPG keys—is implemented as a **build-time overlay** in the main repository (e.g., copying keys from `config/gpg/` to `drongo/src/main/resources/gpg/` during the Gradle build). This allows us to update submodules seamlessly without code changes inside them.

---

## Step-by-Step Upstream Sync and Release Workflow

When upstream releases a new tag (e.g., `2.5.3`), follow this checklist to bring the changes into Sparrow (Strata Edition) and release our matching version.

### Phase 1: Environment and Remote Setup

1. **Verify your local repository is clean:**
  ```bash
   git status
  ```
2. **Ensure the upstream remote is configured:**
  ```bash
   # Add upstream if not already present
   git remote add upstream https://github.com/sparrowwallet/sparrow.git 2>/dev/null || true
   git fetch upstream --tags
  ```

### Phase 2: Create Release Preparation Branch

Assuming our current customized version sits on `main` (which is based on the previous upstream tag, e.g., `2.5.2`), we prepare a release branch for the new target tag (e.g., `2.5.3`).

1. **Create and switch to the release prep branch:**
  ```bash
   git checkout -b release/2.5.3-strata origin/main
  ```

### Phase 3: The Rebase (Transplanting Custom Work)

To maintain a clean diff, we transplant our custom commits from the previous upstream base tag (`2.5.2`) onto the new upstream base tag (`2.5.3`).

1. **Perform a precise rebase using `--onto`:**
  ```bash
   git rebase --onto 2.5.3 2.5.2 release/2.5.3-strata
  ```
   *Note: This command isolates only the commits introduced between `2.5.2` and our branch, and cleanly replays them on top of `2.5.3`.*
2. **Handle Conflicts (if any):**
  If conflicts occur during the rebase:
  - Carefully resolve the conflicts.
  - Verify that Strata-specific logic remains structurally sound.
  - Add resolved files and continue the rebase:
    ```bash
    git add <conflicted-file>
    git rebase --continue
    ```
  - *Do not* commit resolutions as separate merge commits; they are incorporated directly into the replayed commits during the rebase.

### Phase 4: Submodule Alignment

Once the rebase is complete, align the submodules to the exact commits pinned by upstream `2.5.3`.

1. **Synchronize and update submodules recursively:**
  ```bash
   git submodule sync --recursive
   git submodule update --init --checkout --recursive
  ```
2. **Verify submodule status:**
  ```bash
   git submodule status
  ```
   The submodule hashes should match the official commits tagged by upstream for `2.5.3`.

### Phase 5: Re-Validation and Testing

1. **Clean and Build the project:**
  Ensure the build-time GPG overlays and dependency resolution work perfectly:
2. **Run the Test Suite:**
  Ensure no regressions are introduced by upstream changes or rebase conflict resolutions:
3. **Verify Version Alignment:**
  Double-check that all version files and Constants match the new base (e.g., `2.5.3`):
  - `build.gradle` (Gradle project version)
  - `src/main/java/.../SparrowWallet.java` (`APP_VERSION` and `APP_VERSION_SUFFIX`)
4. **Verify Diff Reviewability:**
  Confirm that the diff against upstream is clean and contains ONLY Alpen-specific modifications:

### Phase 6: Tagging and Publishing

Following best practices and user requirements, we tag our release matching the upstream version name. To prevent confusion and clarify that this is the Strata Edition, we use a suffix identifier for our official release tag.

1. **Tag the release commit:**
  Use conventional practices for release tagging.
2. **Push the branch and the tag to origin:**
  ```bash
   git push origin release/2.5.3-strata
   git push origin v2.5.3-strata.1
  ```
3. **Merge back to main:**
  Once the release is verified and published, fast-forward or merge the release branch back into `main` to keep development up-to-date:

---

