# Safe App — Strata Withdrawals

Overview, requirements, and blockers for the **Strata withdrawal Safe App** (standalone React app, separate repo; Sparrow only links to it).

---

## Overview

The Strata bridge splits user flows across two surfaces:


| Surface                           | Role                                                                                                            |
| --------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| **Sparrow (Strata Edition)** fork | Bitcoin-side: deposit requests, reclaim expired UTXOs; Link to the hosted Safe App URL (no embedded evm wallet) |
| **Strata Withdrawals Safe App**   | Alpen-side: withdrawal requests back to Bitcoin                                                                 |


Withdrawals are implemented as a **Safe App** — a React SPA loaded inside Safe{Wallet} in an iframe. The app uses Safe’s native wallet context: it auto-connects to the user’s **Safe smart account** (contract wallet), proposes a bridge withdrawal transaction, and Safe owners sign. 

This replaces the original RFP approach of WalletConnect from Sparrow. WalletConnect in Java was dropped as too costly; a Safe App gives EVM users a familiar flow with simpler implementation.

**Important constraint:** Safe Apps only work with **Safe smart accounts**, not regular EOAs (MetaMask, Frame, etc.). Users must hold sBTC in a Safe on Alpen and open the app from Safe{Wallet}.

**Scope (PROPOSAL Phase 3, ~1.5 weeks):** separate repo, React + Safe SDK, hosted over HTTPS, CI for lint/test/deploy, link from Sparrow fork.

---

## Requirements

### Wallet & network


| Requirement                                           | Safe App behaviour                                                                                                                                                                      |
| ----------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| User can submit a withdrawal from their Alpen account | Auto-connect to active Safe; show Safe address with copy                                                                                                                                |
| Fields disabled until wallet connected                | Disabled until Safe iframe context is connected                                                                                                                                         |
| Correct Alpen network (mainnet / testnet)             | App reads chain ID from Safe; critical error if Safe is on wrong chain — user switches network in Safe{Wallet}                                                                          |
| Address badge: copy / disconnect                      | Copy Safe address; “disconnect” = N/A in iframe (user closes app or switches Safe)                                                                                                      |
| Mainnet & testnet                                     | Both supported; network mode must match Bitcoin network context Sparrow user expects (TBD how app knows mainnet vs testnet — likely from build config or query param from Sparrow link) |


### Destination


| Requirement                               | Safe App behaviour                                                                                                                    |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| Valid Strata withdrawal destinations only | Accept OP_RETURN or Bitcoin types: P2PKH, P2SH, P2WPKH, P2WSH, P2TR                                                                   |
| Unsupported destination                   | Critical error: *“Destination must be OP_RETURN or one of the following bitcoin address types: P2PKH, P2SH, P2WPKH, P2WSH, or P2TR.”* |
| Correct Bitcoin network                   | Critical error: “Destination must be a [mainnet / testnet] bitcoin address.”                                                          |


### Amount & fees


| Requirement                        | Safe App behaviour                                                                                                                              |
| ---------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| Amount allowed by Strata protocol  | Multiple of `d`**, up to** `y` (initial **100 BTC**)                                                                                            |
| Invalid amount                     | Critical error: *“Amount must be a multiple of `d` BTC, up to `y` BTC.”*                                                                        |
| `d` from Strata node               | Fetched at runtime from Strata full node; updates without redeploy when consensus changes                                                       |
| `y` in source                      | App constant; change should not break tests                                                                                                     |
| Insufficient funds                 | Critical error if `amount + estimated_gas × 1.1 > Safe balance`: *“Insufficient funds, balance must cover withdrawal amount plus network fees”* |
| Show `op_fee` and receiving amount | Display withdrawal fee and `withdrawal amount − op_fee`                                                                                         |
| No custom gas UI in app            | Safe{Wallet} / signers handle gas at signing time; app estimates for validation only                                                            |


### Confirm & transaction


| Requirement                     | Safe App behaviour                                                                                                                                                                             |
| ------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Confirm disabled until valid    | Enabled only when all fields pass validation                                                                                                                                                   |
| Submit withdrawal tx            | Propose Safe transaction to Strata bridge contract on Alpen RPC                                                                                                                                |
| Bridge status link after submit | [Mainnet](https://status.alpenlabs.io/bridge) / [testnet](https://status.testnet.alpenlabs.io/bridge)                                                                                          |
| Live bridge operators           | The Strata bridge may involve multiple operators. For successful withdrawals, the app should prefer operators known to be live and processing requests (potentially via a status page or API). |
| HW verification (Ledger/Trezor) | Safe owners signing via HW should verify genuine bridge contract and params on device — **risk:** calldata may not render legibly                                                              |
| Success / failure indication    | Poll Safe tx status; show outcome                                                                                                                                                              |
| Retry on failure                | Retry button resubmits; editing fields resets to Confirm                                                                                                                                       |


### Safe App baseline (Safe Global)


| Requirement                 | Notes                                       |
| --------------------------- | ------------------------------------------- |
| `manifest.json` at app root | Name, description, SVG icon                 |
| CORS on manifest            | Required for Safe{Wallet} to fetch metadata |
| Auto-connect to Safe        | No separate wallet picker when in iframe    |
| HTTPS in production         | Required for hosted app                     |


---

## Blockers


| Blocker                                | Severity | Detail                                                                                                                                                                                                                |
| -------------------------------------- | -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Alpen chain not on app.safe.global** | High     | Alpen testnet (chain ID `8150`) is not a default Safe{Wallet} network. Users cannot open a Safe on Alpen via the public Safe UI unless Alpen registers the chain or hosts its own Safe stack.                         |
| **Safe contracts on Alpgiten unknown** | High     | Safe Apps assume a deployed Safe (proxy + singleton). Confirm canonical Safe contracts exist on Alpen testnet/mainnet and that Transaction Service indexes them. Without this, there is no Safe to load the app from. |
| **Safe infrastructure not deployed**   | High     | Full Safe stack (Config Service, Transaction Service, Wallet web) may be required on Alpen. Self-hosting is non-trivial; native listing on app.safe.global is a separate, slower path.                                |
| **Smart wallets only**                 | High     | Safe App path excludes regular EOAs. Original RFP required any Alpen wallet via WalletConnect — product decision needed (see open question #5).                                                                       |
| **Official Safe App listing paused**   | Medium   | Safe Global has paused new app listings while they rework submission. Pre-assessment form and GitHub listing issues are not reviewed. Does not block launch via Custom App.                                           |
| **Which Safe{Wallet} URL users use**   | High     | Product decision: Alpen-hosted Safe vs public app.safe.global (once Alpen is supported). App `allowedDomains` and QA targets depend on this. Unconfirmed with Alpen.                                                  |
| **App not in Safe Config Service**     | Medium   | Even with self-hosted Safe, the withdrawal app must be registered (Custom App URL or Config Service entry with Alpen `chainIds`). Chain must have `SAFE_APPS` feature enabled.                                        |
| **Bridge contract / withdrawal API**   | High     | Contract address, ABI, and calldata must come from Alpen / Strata CLI reference.                                                                                                                                      |
| `d` and `op_fee` node source           | Medium   | Runtime values from Strata full node; exact endpoint TBD.                                                                                                                                                             |

---

## Open questions for Alpen

1. Is Safe deployed on Alpen testnet/mainnet? Contract addresses? What about other safe infrastructure?
2. How does the Safe App identify and prefer live operators for routing withdrawals?
3. Safe Apps only work with **Safe smart accounts** (contract wallets), not regular EOAs (MetaMask, Frame, etc.). The original RFP allowed withdrawal from any Alpen wallet via WalletConnect. Is restricting withdrawals to Safe holders acceptable, or is a separate path needed for non-Safe users?

