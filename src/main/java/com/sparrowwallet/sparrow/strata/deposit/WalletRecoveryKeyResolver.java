package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;

import java.util.Arrays;
import java.util.Optional;

public final class WalletRecoveryKeyResolver {
    private WalletRecoveryKeyResolver() {
    }

    public static Optional<WalletNode> findSigningNode(Wallet wallet, byte[] recoveryPk) {
        if(wallet == null || recoveryPk == null || recoveryPk.length != 32) {
            return Optional.empty();
        }
        if(wallet.getScriptType() != ScriptType.P2TR || wallet.getPolicyType() != PolicyType.SINGLE_HD) {
            return Optional.empty();
        }
        if(wallet.getKeystores().size() != 1 || wallet.getKeystores().get(0).getSource() != KeystoreSource.SW_SEED) {
            return Optional.empty();
        }

        Keystore keystore = wallet.getKeystores().get(0);
        for(KeyPurpose keyPurpose : new KeyPurpose[] { KeyPurpose.CHANGE, KeyPurpose.RECEIVE }) {
            WalletNode purposeNode = wallet.getNode(keyPurpose);
            for(WalletNode child : purposeNode.getChildren()) {
                ECKey pubKey = keystore.getPubKey(child);
                byte[] xOnly = pubKey.getPubKeyXCoord();
                if(pubKey.hasOddYCoord()) {
                    xOnly = ECKey.fromPublicOnly(xOnly).negate().getPubKeyXCoord();
                }
                if(Arrays.equals(xOnly, recoveryPk)) {
                    return Optional.of(child);
                }
            }
        }

        return Optional.empty();
    }
}
