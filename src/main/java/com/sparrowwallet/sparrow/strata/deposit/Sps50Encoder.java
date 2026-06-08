package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptChunk;
import com.sparrowwallet.drongo.protocol.ScriptOpCodes;

import java.util.List;

public final class Sps50Encoder {
    private static final int MIN_TAG_LEN = 6;
    private static final int MAX_AUX_LEN = 74;
    private static final int MAX_OP_RETURN_LEN = MIN_TAG_LEN + MAX_AUX_LEN;

    private Sps50Encoder() {
    }

    public static Script encodeOpReturnScript(byte[] auxData) {
        byte[] tag = encodeTag(auxData);
        return new Script(List.of(ScriptChunk.fromOpcode(ScriptOpCodes.OP_RETURN), ScriptChunk.fromData(tag)));
    }

    public static byte[] encodeTag(byte[] auxData) {
        if(auxData == null || auxData.length > MAX_AUX_LEN) {
            throw new DepositRequestException("SPS-50 auxiliary data exceeds maximum length of " + MAX_AUX_LEN + " bytes");
        }

        byte[] tag = new byte[MIN_TAG_LEN + auxData.length];
        System.arraycopy(StrataBridgeConstants.MAGIC_BYTES, 0, tag, 0, StrataBridgeConstants.MAGIC_BYTES.length);
        tag[4] = (byte)StrataBridgeConstants.BRIDGE_V1_SUBPROTOCOL_ID;
        tag[5] = (byte)StrataBridgeConstants.DEPOSIT_REQUEST_TX_TYPE;
        System.arraycopy(auxData, 0, tag, 6, auxData.length);
        if(tag.length > MAX_OP_RETURN_LEN) {
            throw new DepositRequestException("SPS-50 tag exceeds maximum OP_RETURN length");
        }
        return tag;
    }
}
