package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptChunk;
import com.sparrowwallet.drongo.protocol.ScriptOpCodes;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.strata.deposit.Sps50Encoder;
import com.sparrowwallet.sparrow.strata.deposit.StrataBridgeConstants;

import java.util.Arrays;
import java.util.Optional;

final class DepositRequestTagParser {
    private static final int RECOVERY_PK_LEN = 32;
    private static final int LEGACY_MIN_TAG_LEN = 4 + RECOVERY_PK_LEN;

    private DepositRequestTagParser() {
    }

    static Optional<byte[]> parseRecoveryPk(Transaction transaction, byte[] magicBytes) {
        if(transaction == null || magicBytes == null || magicBytes.length != 4) {
            return Optional.empty();
        }

        for(var output : transaction.getOutputs()) {
            Optional<byte[]> recoveryPk = parseRecoveryPk(output.getScript(), magicBytes);
            if(recoveryPk.isPresent()) {
                return recoveryPk;
            }
        }

        return Optional.empty();
    }

    private static Optional<byte[]> parseRecoveryPk(Script script, byte[] magicBytes) {
        if(script == null) {
            return Optional.empty();
        }

        var chunks = script.getChunks();
        if(chunks.size() != 2 || !chunks.get(0).isOpCode() || chunks.get(0).getOpcode() != ScriptOpCodes.OP_RETURN || chunks.get(1).isOpCode()) {
            return Optional.empty();
        }

        byte[] tag = chunks.get(1).getData();
        if(tag == null || tag.length < LEGACY_MIN_TAG_LEN || !Arrays.equals(Arrays.copyOf(tag, 4), magicBytes)) {
            return Optional.empty();
        }

        if(Sps50Encoder.usesLegacyTagFormat(magicBytes)) {
            return Optional.of(Arrays.copyOfRange(tag, 4, 4 + RECOVERY_PK_LEN));
        }

        if(tag.length < 6 + RECOVERY_PK_LEN) {
            return Optional.empty();
        }

        if(tag[4] != (byte)StrataBridgeConstants.BRIDGE_V1_SUBPROTOCOL_ID
                || tag[5] != (byte)StrataBridgeConstants.DEPOSIT_REQUEST_TX_TYPE) {
            return Optional.empty();
        }

        return Optional.of(Arrays.copyOfRange(tag, 6, 6 + RECOVERY_PK_LEN));
    }
}
