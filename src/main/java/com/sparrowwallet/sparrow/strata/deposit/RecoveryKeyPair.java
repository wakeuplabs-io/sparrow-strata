package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.crypto.ECKey;

import java.security.SecureRandom;

public final class RecoveryKeyPair {
    private final ECKey privateKey;
    private final byte[] xOnlyPublicKey;

    private RecoveryKeyPair(ECKey privateKey, byte[] xOnlyPublicKey) {
        this.privateKey = privateKey;
        this.xOnlyPublicKey = xOnlyPublicKey;
    }

    public static RecoveryKeyPair generate() {
        ECKey key = ECKey.fromPrivate(generatePrivateKeyBytes());
        if(key.hasOddYCoord()) {
            key = key.negatePrivate();
        }
        return new RecoveryKeyPair(key, key.getPubKeyXCoord());
    }

    public ECKey getPrivateKey() {
        return privateKey;
    }

    public byte[] getXOnlyPublicKey() {
        return xOnlyPublicKey.clone();
    }

    private static byte[] generatePrivateKeyBytes() {
        byte[] privateKey = new byte[32];
        new SecureRandom().nextBytes(privateKey);
        return privateKey;
    }
}
