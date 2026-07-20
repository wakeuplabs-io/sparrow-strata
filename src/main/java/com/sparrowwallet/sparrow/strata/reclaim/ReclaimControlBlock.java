package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.strata.deposit.DepositRequestLockingScript;

public final class ReclaimControlBlock {
    private ReclaimControlBlock() {
    }

    /**
     * Builds the control block for spending a single-leaf taproot tree via its only script (tapscript).
     * Per BIP341, the control block's parity bit is the parity of the tweaked taproot output key
     * {@code Q = P + t*G} (which depends on this specific tapscript's merkle root), not of the internal
     * key {@code P} itself.
     */
    public static byte[] forSingleLeafScript(byte[] internalKeyXOnly, Script tapscript) {
        if(internalKeyXOnly.length != 32) {
            throw new ReclaimException("Taproot internal key must be 32 bytes");
        }

        Sha256Hash merkleRoot = DepositRequestLockingScript.computeTapLeafHash(tapscript);
        ECKey outputKey = DepositRequestLockingScript.computeTaprootOutputPubKey(internalKeyXOnly, merkleRoot);
        int parity = outputKey.hasOddYCoord() ? 1 : 0;

        byte[] controlBlock = new byte[33];
        controlBlock[0] = (byte)(Transaction.LEAF_VERSION_TAPSCRIPT | parity);
        System.arraycopy(internalKeyXOnly, 0, controlBlock, 1, 32);
        return controlBlock;
    }
}
