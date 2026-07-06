package com.sparrowwallet.sparrow.strata.deposit;

import java.util.Arrays;

public final class DrtHeaderAux {
    private final byte[] recoveryPk;
    private final byte[] destination;

    private DrtHeaderAux(byte[] recoveryPk, byte[] destination) {
        this.recoveryPk = recoveryPk;
        this.destination = destination;
    }

    public static DrtHeaderAux create(byte[] recoveryPk, byte[] destination) {
        if(recoveryPk == null || recoveryPk.length != 32) {
            throw new DepositRequestException("Recovery public key must be 32 bytes");
        }
        if(destination == null || destination.length > StrataBridgeConstants.MAX_DRT_DESTINATION_BYTES) {
            throw new DepositRequestException("Deposit descriptor exceeds maximum DRT destination size of "
                    + StrataBridgeConstants.MAX_DRT_DESTINATION_BYTES + " bytes");
        }
        return new DrtHeaderAux(Arrays.copyOf(recoveryPk, recoveryPk.length), Arrays.copyOf(destination, destination.length));
    }

    public byte[] getRecoveryPk() {
        return Arrays.copyOf(recoveryPk, recoveryPk.length);
    }

    public byte[] getDestination() {
        return Arrays.copyOf(destination, destination.length);
    }

    public byte[] buildAuxData() {
        byte[] auxData = new byte[32 + destination.length];
        System.arraycopy(recoveryPk, 0, auxData, 0, 32);
        System.arraycopy(destination, 0, auxData, 32, destination.length);
        return auxData;
    }
}
