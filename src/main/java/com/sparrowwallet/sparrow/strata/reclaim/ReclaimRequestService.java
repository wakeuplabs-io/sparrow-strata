package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;

public final class ReclaimRequestService {
    private ReclaimRequestService() {
    }

    public static PSBT buildReclaimPsbt(Wallet wallet, List<ReclaimEntry> entries, Address destination, double feeRate) {
        validateWallet(wallet);

        List<ReclaimTransactionBuilder.ReclaimSpendInput> spendInputs = ReclaimTransactionBuilder.resolveSpendInputs(wallet, entries);
        return ReclaimTransactionBuilder.buildPsbt(wallet, spendInputs, destination, feeRate);
    }

    private static void validateWallet(Wallet wallet) {
        if(wallet == null) {
            throw new ReclaimException("Wallet is required");
        }
        if(wallet.getScriptType() != ScriptType.P2TR) {
            throw new ReclaimException("Reclaim requires a Taproot (P2TR) wallet");
        }
        if(wallet.getPolicyType() != PolicyType.SINGLE_HD) {
            throw new ReclaimException("Reclaim requires a single signature HD wallet");
        }
        if(wallet.getKeystores().size() != 1 || wallet.getKeystores().get(0).getSource() != KeystoreSource.SW_SEED) {
            throw new ReclaimException("Reclaim requires a software Taproot wallet");
        }
    }
}
