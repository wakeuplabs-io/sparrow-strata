package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.sparrow.strata.protocol.StrataBridgeProtocol;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptChunk;
import com.sparrowwallet.drongo.protocol.ScriptOpCodes;

import java.util.Arrays;
import java.util.List;

public final class Sps50Encoder {
    private static final int MODERN_MIN_TAG_LEN = 6;
    private static final int LEGACY_MIN_TAG_LEN = 4;
    private static final int MAX_AUX_LEN = 74;
    private static final int MAX_OP_RETURN_LEN = MODERN_MIN_TAG_LEN + MAX_AUX_LEN;

    private Sps50Encoder() {
    }

    /**
     * Alpen testnet bridge nodes expect the legacy tag: magic + recovery_pk +
     * raw EVM destination, with the bridge-in P2TR output at index 0 and OP_RETURN at index 1.
     */
    public static boolean usesLegacyTagFormat(byte[] magicBytes) {
        return Arrays.equals(magicBytes, StrataBridgeProtocol.TESTNET_MAGIC_BYTES);
    }

    public static Script encodeOpReturnScript(byte[] auxData) {
        return encodeOpReturnScript(StrataBridgeProtocol.DEPOSIT_REQUEST_TX_TYPE, auxData, StrataBridgeProtocol.MAGIC_BYTES);
    }

    public static Script encodeOpReturnScript(byte[] auxData, byte[] magicBytes) {
        return encodeOpReturnScript(StrataBridgeProtocol.DEPOSIT_REQUEST_TX_TYPE, auxData, magicBytes);
    }

    public static Script encodeOpReturnScript(int txType, byte[] auxData) {
        return encodeOpReturnScript(txType, auxData, StrataBridgeProtocol.MAGIC_BYTES);
    }

    public static Script encodeOpReturnScript(int txType, byte[] auxData, byte[] magicBytes) {
        byte[] tag = encodeTag(txType, auxData, magicBytes);
        return new Script(List.of(ScriptChunk.fromOpcode(ScriptOpCodes.OP_RETURN), ScriptChunk.fromData(tag)));
    }

    public static byte[] encodeTag(byte[] auxData) {
        return encodeTag(StrataBridgeProtocol.DEPOSIT_REQUEST_TX_TYPE, auxData, StrataBridgeProtocol.MAGIC_BYTES);
    }

    public static byte[] encodeTag(byte[] auxData, byte[] magicBytes) {
        return encodeTag(StrataBridgeProtocol.DEPOSIT_REQUEST_TX_TYPE, auxData, magicBytes);
    }

    public static byte[] encodeTag(int txType, byte[] auxData) {
        return encodeTag(txType, auxData, StrataBridgeProtocol.MAGIC_BYTES);
    }

    public static byte[] encodeTag(int txType, byte[] auxData, byte[] magicBytes) {
        if(auxData == null || auxData.length > MAX_AUX_LEN) {
            throw new DepositRequestException("SPS-50 auxiliary data exceeds maximum length of " + MAX_AUX_LEN + " bytes");
        }
        if(magicBytes == null || magicBytes.length != 4) {
            throw new DepositRequestException("SPS-50 magic bytes must be exactly 4 bytes");
        }

        if(txType == StrataBridgeProtocol.DEPOSIT_REQUEST_TX_TYPE && usesLegacyTagFormat(magicBytes)) {
            byte[] tag = new byte[LEGACY_MIN_TAG_LEN + auxData.length];
            System.arraycopy(magicBytes, 0, tag, 0, magicBytes.length);
            System.arraycopy(auxData, 0, tag, LEGACY_MIN_TAG_LEN, auxData.length);
            if(tag.length > MAX_OP_RETURN_LEN) {
                throw new DepositRequestException("SPS-50 tag exceeds maximum OP_RETURN length");
            }
            return tag;
        }

        byte[] tag = new byte[MODERN_MIN_TAG_LEN + auxData.length];
        System.arraycopy(magicBytes, 0, tag, 0, magicBytes.length);
        tag[4] = (byte)StrataBridgeProtocol.BRIDGE_V1_SUBPROTOCOL_ID;
        tag[5] = (byte)txType;
        System.arraycopy(auxData, 0, tag, MODERN_MIN_TAG_LEN, auxData.length);
        if(tag.length > MAX_OP_RETURN_LEN) {
            throw new DepositRequestException("SPS-50 tag exceeds maximum OP_RETURN length");
        }
        return tag;
    }
}
