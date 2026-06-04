# Baseline Ledger/Trezor Compatibility Report

Phase 1 spike: document the **Bitcoin transactions** `alpen-cli` builds for deposit and recover (reclaim), then map each part to what **Sparrow’s hardware-wallet stack** (Lark → Ledger/Trezor) can sign or display.

---

## Summary


| Flow                      | CLI command     | Who signs on-chain                                                         | Needs HW (Ledger/Trezor)?                                 |
| ------------------------- | --------------- | -------------------------------------------------------------------------- | --------------------------------------------------------- |
| **Deposit request (DRT)** | `alpen deposit` | User’s **L1 wallet** (BDK `SignetWallet`)                                  | **Only if** the Sparrow funding wallet uses HW for inputs |
| **Recover (reclaim)**     | `alpen recover` | **Software** recovery wallet (ephemeral descriptor + stored `recovery_sk`) | **No** — not signed via user’s HW                         |


Strata reclaim in the RFP reuses recover’s spend mechanics; retry deposit reuses DRT shape.

---

## 1. Deposit request transaction (DRT)

**Source:** `bin/alpen-cli/src/cmd/deposit.rs`, `bin/strata-test-cli/src/cmd/compute_drt_output.rs`.

### 1.1 What the CLI builds

**Outputs (fixed order, `TxOrdering::Untouched`):**


| Index | Type             | Value                         | Script / payload                                        |
| ----- | ---------------- | ----------------------------- | ------------------------------------------------------- |
| **0** | `OP_RETURN`      | `0`                           | SPS-50: `magic_bytes` + `DrtHeaderAux.build_tag_data()` |
| **1** | P2TR (bridge-in) | `deposit_amount + bridge_fee` | See bridge-in descriptor below                          |


`**DrtHeaderAux` (in OP_RETURN):**

- `recovery_pk`: 32-byte x-only pubkey (fresh per deposit, `even_kp` + `OsRng`)
- `DepositDescriptor`: Alpen EE account serial + Alpen address as `SubjectIdBytes`

**Bridge-in output (output 1) — miniscript descriptor:**

```text
tr(<bridge_musig2_xonly>,
   and_v(v:pk(<recovery_private_key>), older(<recovery_delay>)))
```

- **Key path:** bridge operator MuSig2 aggregate (`settings.bridge_musig2_pubkey`)
- **Script path:** user recovery pubkey + relative timelock (`recovery_delay` from rollup params; tests use `RECOVER_DELAY = 1008` in `crates/primitives/src/constants.rs`)

**Inputs:** Coin-selected from the user’s L1 wallet (whatever descriptor `SignetWallet` uses — typically BIP84/86-style signet wallet). Change may be added by BDK to an unused L1 address.

**Signing (CLI):** `l1w.sign(&mut psbt)` on the **funding wallet only**.

**Side effects:** Persists `(bridge_in_desc, recovery_sk)` in encrypted `DescriptorRecovery` DB with `recover_at = tip + recovery_delay + finality_depth`.

### 1.2 ASCII layout

```text
┌─────────────────────────────────────────────────────────────┐
│ DRT (Deposit Request Transaction)                           │
├─────────────────────────────────────────────────────────────┤
│ IN:  user L1 UTXO(s)  [P2WPKH / P2TR / multisig / …]        │
├─────────────────────────────────────────────────────────────┤
│ OUT[0]: OP_RETURN(0)  → SPS-50 + recovery_pk + DepositDesc  │
│ OUT[1]: P2TR          → value = d + bridge_fee              │
│         internal_key = bridge MuSig2                        │
│         tapscript    = pk(recovery) + older(delay)          │
├─────────────────────────────────────────────────────────────┤
│ OUT[?]: change → user L1 wallet (BDK)                       │
└─────────────────────────────────────────────────────────────┘
```

### 1.3 Sparrow HW module corroboration (deposit / DRT)

Sparrow signs PSBTs through **Lark** (`lark/` → `LedgerClient`, `TrezorClient`, …), invoked from the transaction UI / `DevicePane`.


| DRT feature                                        | Lark / Sparrow support      | Code / notes                                                                                                                  |
| -------------------------------------------------- | --------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| **Sign user inputs** (P2WPKH)                      | Supported                   | Trezor: `ECDSA_SCRIPT_TYPES`; Ledger: standard `wpkh` policy                                                                  |
| **Sign user inputs** (P2TR key path)               | Supported                   | Trezor/Ledger: tap **internal key** path only                                                                                 |
| **Sign user inputs** (multisig P2WSH)              | Supported                   | Ledger/Trezor multisig wallet policies                                                                                        |
| **OP_RETURN output (0 sats)**                      | Supported (Trezor explicit) | `TrezorClient.java`: `PAYTOOPRETURN` + `opReturnData` from script bytes after `OP_RETURN`                                     |
| **Send to external P2TR** (bridge out, not change) | Supported (modern apps)     | Trezor: `getToAddress()` → `PAYTOADDRESS` with bech32m; Ledger: PSBT v2 `signPsbt` (legacy app rejects P2TR **to** address)   |
| **Taproot script-path inputs**                     | **Not supported** on HW     | `TrezorClient.java` / `LedgerClient.java`: *"script path signing is not currently supported"* — **not needed** for DRT inputs |
| **Alpen address on device screen**                 | **Not supported**           | Alpen dest is inside OP_RETURN payload; devices show hex or omit — RFP 2.9 gap                                                |
| **“Genuine Strata” / DepositDescriptor**           | **Not supported**           | No Strata-aware display in Lark; Sparrow UI must carry this                                                                   |


**Conclusion (deposit + HW):** Sparrow can sign a DRT **if** the open wallet’s inputs are a type Lark already handles (P2WPKH, P2TR key path, multisig). The Strata-specific outputs (OP_RETURN + foreign P2TR) are passed through Trezor’s OP_RETURN and address output types; **semantic** verification of Alpen destination remains in-app, not on device.

---

## 2. Recover transaction (RFP “reclaim” spend)

**Source:** `bin/alpen-cli/src/cmd/recover.rs` (command name `recover`, not `reclaim`).

### 2.1 What the CLI builds

For each matured entry in `DescriptorRecovery` (height ≤ current tip):

1. Load saved `bridge_in_desc` into a **temporary** single-descriptor BDK wallet.
2. Sync that wallet — UTXOs sit on the bridge-in P2TR address from the original DRT.
3. If `confirmed balance > 0`, build a **drain** transaction:
  - **Spend path:** script-path branch **1** (`policy_path` → recovery + `older`)
  - **Outputs:** `drain_to` → next unused address on the user’s main `SignetWallet`
  - **Fee:** user/config fee rate

**Signing:** `recovery_wallet.sign(&mut psbt)` — uses the **software** `recovery_private_key` embedded in the saved descriptor. The user’s Ledger/Trezor is **not** called.

### 2.2 ASCII layout

```text
┌─────────────────────────────────────────────────────────────┐
│ Recover tx                                                  │
├─────────────────────────────────────────────────────────────┤
│ IN:  DRT bridge-in P2TR UTXO(s)                             │
│      SPEND: tapscript pk(recovery) + older(delay)  [branch 1]│
│      SIGN:  recovery_sk (software, from DescriptorRecovery) │
├─────────────────────────────────────────────────────────────┤
│ OUT: drain → user L1 receive address (+ fee)                │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 Sparrow HW module corroboration (recover)


| Recover feature                       | Lark / Sparrow support       | Notes                                                                                                                                                   |
| ------------------------------------- | ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Taproot script-path spend** (input) | HW: **not supported**        | Same Lark comment on script path — **CLI never uses HW here**                                                                                           |
| **Software sign script path**         | Sparrow `Wallet.sign` / PSBT | `Wallet.java` finalise has `TODO: Handle taproot scriptpath spending` — **must implement** script-path finalization in Strata (likely SW-only keystore) |
| **Drain to P2WPKH/P2TR**              | HW N/A (outputs only)        | Destination is normal user address                                                                                                                      |


**Conclusion (reclaim):** RFP reclaim **does not** require Ledger/Trezor for the recover spend. Strata must persist recovery keys and sign script-path spends in software (same as CLI). HW compatibility spike for reclaim is about **not breaking** parallel use of HW wallets for the main wallet, not signing recover itself.

---

## 3. RFP vs CLI vs Sparrow (deposit + reclaim only)


| RFP item                            | CLI behavior                                      | Sparrow stack today                                                     |
| ----------------------------------- | ------------------------------------------------- | ----------------------------------------------------------------------- |
| 1.7 Any wallet type for **deposit** | L1 wallet-agnostic inputs; bridge out is separate | Yes for input types Lark supports                                       |
| 2.9 HW verify deposit details       | N/A in CLI (SW sign)                              | Partial: amounts/addresses for standard outputs; not Alpen in OP_RETURN |
| §4 Reclaim list + actions           | `recover` only; no list UI                        | Detection/UI new; spend = SW script path                                |


---

## 4. Compatibility matrix (Lark)

Pending simulator confirmation; static analysis from `lark/` sources.


| Capability                        | Ledger (Bitcoin app 2.x) | Trezor                | Needed for                    |
| --------------------------------- | ------------------------ | --------------------- | ----------------------------- |
| P2WPKH input sign                 | Yes                      | Yes                   | DRT funding                   |
| P2TR key-path input sign          | Yes                      | Yes                   | DRT funding (Taproot wallet)  |
| Multisig input sign               | Yes                      | Yes                   | DRT funding (multisig wallet) |
| OP_RETURN output in same tx       | Yes (via PSBT)           | Yes (`PAYTOOPRETURN`) | DRT out[0]                    |
| Payment to external P2TR          | Yes (not legacy app)     | Yes (`getToAddress`)  | DRT out[1]                    |
| Taproot script-path input         | **No**                   | **No**                | Recover only → **use SW**     |
| Decode Strata OP_RETURN on device | **No**                   | **No**                | RFP 2.9                       |


---

## 6. Open questions

1. Confirm `recovery_delay` on target network (1008 in `strata_primitives`, vs older “144 block” docs).
