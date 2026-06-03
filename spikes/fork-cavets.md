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



