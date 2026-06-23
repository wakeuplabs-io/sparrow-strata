package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.Utils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReclaimControlBlockTest {
    private static final String BRIDGE_INTERNAL_KEY_HEX =
            "bd67cd6f1d488245ad3dca07474e0a11ac43c01cc9b90a0b3794dd25ed2ac77e";

    @Test
    void buildsSingleLeafControlBlock() {
        byte[] internalKey = Utils.hexToBytes(BRIDGE_INTERNAL_KEY_HEX);
        byte[] controlBlock = ReclaimControlBlock.forSingleLeafScript(internalKey);

        assertEquals(33, controlBlock.length);
        assertEquals(0xc0, controlBlock[0] & 0xfe);
    }
}
