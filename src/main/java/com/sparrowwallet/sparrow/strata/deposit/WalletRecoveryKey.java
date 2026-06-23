package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.wallet.WalletNode;

public final class WalletRecoveryKey {
    private final WalletNode walletNode;
    private final byte[] xOnlyPublicKey;

    public WalletRecoveryKey(WalletNode walletNode, byte[] xOnlyPublicKey) {
        this.walletNode = walletNode;
        this.xOnlyPublicKey = xOnlyPublicKey.clone();
    }

    public WalletNode getWalletNode() {
        return walletNode;
    }

    public byte[] getXOnlyPublicKey() {
        return xOnlyPublicKey.clone();
    }

    public static WalletRecoveryKey forTesting(WalletNode walletNode, byte[] xOnlyPublicKey) {
        return new WalletRecoveryKey(walletNode, xOnlyPublicKey);
    }
}
