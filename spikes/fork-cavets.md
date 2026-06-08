# Fork Caveats

This document records operational and technical consequences of maintaining **Sparrow (Strata Edition)** as a fork of [upstream Sparrow](https://github.com/sparrowwallet/sparrow), rather than as a thin plugin or extension. Items here are things the team must deliberately own, change, or re-validate on every release and when merging upstream.

---

## Branding, Identity, and User Data Paths

Upstream branding is still largely intact. For a distinct product (Strata Edition), decide explicitly what to change:


| Constant / setting | Location                         | Upstream value                                            | Fork concern                                                           |
| ------------------ | -------------------------------- | --------------------------------------------------------- | ---------------------------------------------------------------------- |
| `APP_NAME`         | `SparrowWallet.java`             | `"Sparrow"`                                               | Window titles, HWI client name, user-facing strings                    |
| `APP_ID`           | `SparrowWallet.java`             | `"sparrow"`                                               | Single-instance lock file name; **two installs conflict** if unchanged |
| Home directory     | `Storage.java`                   | `~/.sparrow` (macOS/Linux), `%APPDATA%/Sparrow` (Windows) | Wallets/config shared with upstream Sparrow if unchanged               |
| `jpackage` names   | `build.gradle`                   | `imageName` / `installerName` = `"Sparrow"`               | Install paths, menu entries, binary names                              |
| macOS dock         | `build.gradle`                   | `-Xdock:name=Sparrow`                                     | Dock label                                                             |
| Icons / FXML       | `src/main/deploy/`, `about.fxml` | Sparrow artwork and copy                                  | Donation link still points at upstream                                 |


**Co-installation:** Running Strata Edition alongside upstream Sparrow on the same machine requires at minimum a different `APP_ID` and home directory (or documented use of `-d` / `sparrow.home`). Otherwise instance locking and config/wallet paths collide.

---

## Hardcoded Upstream URLs and Services

These call or link to **sparrowwallet.com** (or Craig Raw’s infrastructure). They will mislead users or phone home to the wrong project unless disabled or repointed:


| Feature                    | Location                                                                | URL / behavior                                                                                                                                                     |
| -------------------------- | ----------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Automatic update check** | `VersionCheckService.java`                                              | `https://www.sparrowwallet.com/version` — polls on a schedule; compares to `APP_VERSION`; signature tied to hardcoded address `1LiJx1HQ49L2LzhBwbgwXdHiGodvPg5YaV` |
| Download latest            | `AppController.java`                                                    | `https://www.sparrowwallet.com/download`                                                                                                                           |
| Documentation              | `AppController.java`, `ServerSettingsController.java`, `lark/Lark.java` | `sparrowwallet.com/docs`, FAQ links                                                                                                                                |
| Support / bugs             | `AppController.java`                                                    | `opensupport`, `submitbugreport`                                                                                                                                   |
| Donate                     | `AboutController.java`, `about.fxml`                                    | `sparrowwallet.com/donate`                                                                                                                                         |
| Linux package metadata     | `control`, `sparrowwallet.spec`                                         | Maintainer, URL fields                                                                                                                                             |
| GitHub Issues (README)     | `README.md`                                                             | Points users to `github.com/sparrowwallet/sparrow/issues`                                                                                                          |


---

## Implications

The tables above are not just documentation — they define work the team must own for every release and when merging upstream. Below are the concrete consequences and recommended actions.

### Custom version check endpoint

Upstream Sparrow polls `https://www.sparrowwallet.com/version` on a schedule via `VersionCheckService.java`. If we leave that URL unchanged, Strata Edition users will be told about upstream releases (or miss Strata releases entirely).

**We need our own endpoint** that serves the same JSON shape. A static file in GitHub works fine — for example:

- Raw file in this repo: `https://raw.githubusercontent.com/<org>/<repo>/main/version`
- GitHub Pages: `https://<org>.github.io/<repo>/version`

The file format matches upstream (see `version` in this repo):

```json
{
  "version": "2.5.2",
  "signatures": {
    "1ABC...": "base64-encoded-signature"
  }
}
```

The app compares `version` to `APP_VERSION` and only prompts for an update when the remote version is newer **and** the signature verifies.

**Code changes required:**

1. Change `VERSION_CHECK_URL` in `VersionCheckService.java` to the Strata endpoint.
2. Replace the hardcoded signer address `1LiJx1HQ49L2LzhBwbgwXdHiGodvPg5YaV` with an Alpen-controlled address (see below).
3. Repoint the "Download latest" link in `AppController.java` to the Strata download page or GitHub Releases.

**Release workflow:** After each Strata release, update the hosted `version` file with the new version string and a fresh signature. This can be a CI step or a manual step in the release checklist.

**Operational steps:**

1. Generate a dedicated Bitcoin keypair for version announcements (do not reuse a hot wallet or user funds key).
2. Record the P2PKH address and hardcode it in `VersionCheckService.java` (replacing Craig Raw's address).
3. Store the private key securely (password manager, HSM, or offline signing machine) — only needed at release time.
4. At each release, sign the version string (e.g. `"2.5.3"`) with that key and publish the base64 signature in the hosted JSON.

Hosting on GitHub raw or Pages is acceptable; the signature is what prevents a compromised GitHub account (or MITM) from pushing a fake "new version" to users. HTTPS alone is insufficient because the app explicitly verifies the signer.

### Branding, co-installation, and user data

If Strata Edition ships with upstream `APP_ID`, `APP_NAME`, and home directory unchanged:

- Only one instance can run at a time (lock file collision).
- Config, wallets, and transaction labels are shared with upstream Sparrow in `~/.sparrow` (or `%APPDATA%/Sparrow`).
- Users cannot distinguish the two products in the OS (dock, installer names, menu entries).

**Minimum for side-by-side install:** different `APP_ID`, different home directory (or documented `-d` / `sparrow.home`), and distinct `jpackage` / installer names. Branding (icons, about dialog, donation link) should follow so users know which product they are running.

### Upstream URL replacements

Every link in the "Hardcoded Upstream URLs" table must be explicitly handled — not left as-is:


| Action                        | Examples                                                                            |
| ----------------------------- | ----------------------------------------------------------------------------------- |
| **Repoint**                   | Download page → GitHub Releases or Alpen download site; docs → Strata documentation |
| **Replace**                   | Support / bug report → Alpen issue tracker or support channel                       |
| **Remove or replace**         | Donate link in About dialog                                                         |
| **Update packaging metadata** | Linux `control` / `.spec` maintainer and URL fields                                 |
| **Update README**             | Issue tracker, build instructions, attribution                                      |


Leaving any of these unchanged misleads users and sends telemetry or support traffic to the wrong project.

### Open decisions

- **Endpoint host (recommendation):** Use **GitHub raw** in the release repo for now — e.g. `https://raw.githubusercontent.com/<org>/sparrow-strata/main/version`. Zero extra infrastructure; the file can live beside release artifacts and be updated by CI when a GitHub Release is published. Bitcoin message signing is what actually protects users, not the hostname. Move to **GitHub Pages with a custom subdomain** (e.g. `updates.strata.example.com`) only if you want a cleaner URL without owning a full product site. A **dedicated domain** is worth it once you already run a Strata website or download page — not for a lone JSON file in early releases.
- **Disable vs replace:** Should automatic update check be disabled until the Strata endpoint is live, or ship with the new URL from day one?
- **Signer key custody:** Single release-manager key vs multi-signer (would require code changes — upstream only accepts one hardcoded address today).

---

