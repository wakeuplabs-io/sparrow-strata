package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.Transaction;

public final class ReclaimControlBlock {
    private ReclaimControlBlock() {
    }

    public static byte[] forSingleLeafScript(byte[] internalKeyXOnly) {
        if(internalKeyXOnly.length != 32) {
            throw new ReclaimException("Taproot internal key must be 32 bytes");
        }

        ECKey internalKey = ECKey.fromPublicOnly(internalKeyXOnly);
        int parity = internalKey.hasOddYCoord() ? 1 : 0;
        byte[] controlBlock = new byte[33];
        controlBlock[0] = (byte)(Transaction.LEAF_VERSION_TAPSCRIPT | parity);
        System.arraycopy(internalKeyXOnly, 0, controlBlock, 1, 32);
        return controlBlock;
    }
}
