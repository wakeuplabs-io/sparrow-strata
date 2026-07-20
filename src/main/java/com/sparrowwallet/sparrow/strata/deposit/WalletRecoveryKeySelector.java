package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;

import java.util.Optional;

public final class WalletRecoveryKeySelector {
    private WalletRecoveryKeySelector() {
    }

    public static Optional<String> getCompatibilityError(Wallet wallet) {
        if(wallet == null) {
            return Optional.of("Wallet is required for deposit");
        }
        if(wallet.getScriptType() != ScriptType.P2TR) {
            return Optional.of("Strata deposits require a Taproot (P2TR) wallet");
        }
        if(wallet.getPolicyType() != PolicyType.SINGLE_HD) {
            return Optional.of("Strata deposits require a single signature HD wallet");
        }
        if(wallet.getKeystores().size() != 1) {
            return Optional.of("Strata deposits require a single keystore wallet");
        }

        Keystore keystore = wallet.getKeystores().get(0);
        if(keystore.getSource() != KeystoreSource.SW_SEED) {
            return Optional.of("Strata deposits require a software Taproot wallet");
        }

        return Optional.empty();
    }

    public static WalletRecoveryKey select(Wallet wallet) {
        Optional<String> compatibilityError = getCompatibilityError(wallet);
        if(compatibilityError.isPresent()) {
            throw new DepositRequestException(compatibilityError.get());
        }

        WalletNode changeNode = wallet.getFreshNode(KeyPurpose.CHANGE);
        Keystore keystore = wallet.getKeystores().get(0);
        ECKey pubKey = keystore.getPubKey(changeNode);
        byte[] xOnlyPublicKey = pubKey.getPubKeyXCoord();
        if(pubKey.hasOddYCoord()) {
            xOnlyPublicKey = ECKey.fromPublicOnly(xOnlyPublicKey).negate().getPubKeyXCoord();
        }

        return new WalletRecoveryKey(changeNode, xOnlyPublicKey);
    }
}
