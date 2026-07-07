package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.sparrow.strata.deposit.DepositRequestLockingScript;
import com.sparrowwallet.sparrow.strata.deposit.RecoveryKeyPair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReclaimControlBlockTest {
    private static final String BRIDGE_INTERNAL_KEY_HEX =
            "bd67cd6f1d488245ad3dca07474e0a11ac43c01cc9b90a0b3794dd25ed2ac77e";

    @Test
    void buildsSingleLeafControlBlock() {
        byte[] internalKey = Utils.hexToBytes(BRIDGE_INTERNAL_KEY_HEX);
        Script tapscript = DepositRequestLockingScript.createRecoveryTapscript(RecoveryKeyPair.generate().getXOnlyPublicKey(), 1008);
        byte[] controlBlock = ReclaimControlBlock.forSingleLeafScript(internalKey, tapscript);

        assertEquals(33, controlBlock.length);
        assertEquals(0xc0, controlBlock[0] & 0xfe);
    }

    /**
     * Per BIP341, the control block's parity bit must reflect the parity of the tweaked taproot output
     * key Q = P + t*G (which depends on the specific tapscript's merkle root), not of the internal key P.
     * Since P is always lifted to its even-y representative, a control block that (incorrectly) always
     * derives the parity from P would always emit 0 - this test picks a tapscript for which the true Q is
     * known to have odd y, so it would fail under that bug.
     */
    @Test
    void controlBlockParityMatchesTweakedOutputKeyNotInternalKey() {
        byte[] internalKey = Utils.hexToBytes(BRIDGE_INTERNAL_KEY_HEX);
        Script tapscript = findTapscriptWithOddYOutputKey(internalKey);

        Sha256Hash merkleRoot = DepositRequestLockingScript.computeTapLeafHash(tapscript);
        ECKey outputKey = DepositRequestLockingScript.computeTaprootOutputPubKey(internalKey, merkleRoot);
        assertEquals(true, outputKey.hasOddYCoord());

        byte[] controlBlock = ReclaimControlBlock.forSingleLeafScript(internalKey, tapscript);
        assertEquals(1, controlBlock[0] & 1);
    }

    private static Script findTapscriptWithOddYOutputKey(byte[] internalKey) {
        for(int i = 0; i < 100; i++) {
            Script tapscript = DepositRequestLockingScript.createRecoveryTapscript(RecoveryKeyPair.generate().getXOnlyPublicKey(), 1008);
            Sha256Hash merkleRoot = DepositRequestLockingScript.computeTapLeafHash(tapscript);
            ECKey outputKey = DepositRequestLockingScript.computeTaprootOutputPubKey(internalKey, merkleRoot);
            if(outputKey.hasOddYCoord()) {
                return tapscript;
            }
        }
        throw new AssertionError("Could not find a tapscript producing an odd-y tweaked output key after 100 attempts");
    }
}
