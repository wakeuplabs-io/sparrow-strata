package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.util.Optional;

public final class ReclaimWalletCompatibility {
    private ReclaimWalletCompatibility() {
    }

    public static boolean supportsReclaimSigning(Wallet wallet) {
        return getCompatibilityError(wallet).isEmpty();
    }

    public static Optional<String> getCompatibilityError(Wallet wallet) {
        if(wallet == null) {
            return Optional.of("Wallet is required for reclaim");
        }
        if(wallet.getScriptType() != ScriptType.P2TR || wallet.getPolicyType() != PolicyType.SINGLE_HD) {
            return Optional.of("Reclaim requires a Taproot single-sig wallet");
        }
        if(wallet.getKeystores().size() != 1) {
            return Optional.of("Reclaim requires a single keystore wallet");
        }

        Keystore keystore = wallet.getKeystores().get(0);
        if(keystore.getSource() == KeystoreSource.SW_SEED) {
            return Optional.empty();
        }

        WalletModel walletModel = keystore.getWalletModel();
        String device = walletModel != null ? walletModel.toDisplayString() : "this wallet";
        return Optional.of("Reclaim is not supported with " + device + ". Use a software Taproot wallet.");
    }
}
