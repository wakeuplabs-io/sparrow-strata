package com.sparrowwallet.sparrow.strata.deposit;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Auxiliary data for a Strata deposit transaction (DT) SPS-50 OP_RETURN header.
 * Encoded with {@code strata_codec::Codec} as a little-endian {@code u32}.
 */
public final class DtHeaderAux {
    private final int depositIdx;

    private DtHeaderAux(int depositIdx) {
        this.depositIdx = depositIdx;
    }

    public static DtHeaderAux create(int depositIdx) {
        if(depositIdx < 0) {
            throw new DepositRequestException("Deposit index must be non-negative");
        }
        return new DtHeaderAux(depositIdx);
    }

    public byte[] buildAuxData() {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(depositIdx).array();
    }
}
