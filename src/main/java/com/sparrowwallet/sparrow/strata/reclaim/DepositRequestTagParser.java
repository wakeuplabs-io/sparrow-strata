package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.sparrow.strata.protocol.StrataBridgeProtocol;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptChunk;
import com.sparrowwallet.drongo.protocol.ScriptOpCodes;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.strata.deposit.Sps50Encoder;

import com.sparrowwallet.sparrow.strata.model.AlpenAddress;
import com.sparrowwallet.sparrow.strata.protocol.AlpenConstants;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;

import java.util.Arrays;
import java.util.Optional;

final class DepositRequestTagParser {
    private static final int RECOVERY_PK_LEN = 32;
    private static final int LEGACY_MIN_TAG_LEN = 4 + RECOVERY_PK_LEN;
    private static final int MODERN_MIN_TAG_LEN = 6 + RECOVERY_PK_LEN;

    private DepositRequestTagParser() {
    }

    static Optional<byte[]> parseRecoveryPk(Transaction transaction, byte[] magicBytes) {
        return findTag(transaction, magicBytes).map(tag -> {
            int offset = recoveryPkOffset(magicBytes);
            return Arrays.copyOfRange(tag, offset, offset + RECOVERY_PK_LEN);
        });
    }

    /**
     * Recovers the original Alpen deposit destination from the DRT's OP_RETURN tag, so a retried
     * deposit can be pre-filled without asking the user to re-enter it.
     */
    static Optional<AlpenAddress> parseDestinationAddress(Transaction transaction, byte[] magicBytes) {
        return findTag(transaction, magicBytes).flatMap(tag -> decodeDestinationAddress(tag, magicBytes));
    }

    private static int recoveryPkOffset(byte[] magicBytes) {
        return Sps50Encoder.usesLegacyTagFormat(magicBytes) ? 4 : 6;
    }

    private static Optional<AlpenAddress> decodeDestinationAddress(byte[] tag, byte[] magicBytes) {
        int destinationOffset = recoveryPkOffset(magicBytes) + RECOVERY_PK_LEN;
        if(tag.length <= destinationOffset) {
            return Optional.empty();
        }
        byte[] destinationBytes = Arrays.copyOfRange(tag, destinationOffset, tag.length);

        try {
            if(Sps50Encoder.usesLegacyTagFormat(magicBytes)) {
                return destinationBytes.length == AlpenConstants.EVM_ADDRESS_BYTES
                        ? Optional.of(new AlpenAddress(destinationBytes)) : Optional.empty();
            }

            DepositDescriptor descriptor = DepositDescriptor.decodeFromBytes(destinationBytes);
            if(descriptor.getDestAcctSerial() != AlpenConstants.ALPEN_EE_ACCT_SERIAL
                    || descriptor.getDestSubject().length != AlpenConstants.EVM_ADDRESS_BYTES) {
                return Optional.empty();
            }
            return Optional.of(new AlpenAddress(descriptor.getDestSubject()));
        } catch(RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<byte[]> findTag(Transaction transaction, byte[] magicBytes) {
        if(transaction == null || magicBytes == null || magicBytes.length != 4) {
            return Optional.empty();
        }

        for(var output : transaction.getOutputs()) {
            Optional<byte[]> tag = findTag(output.getScript(), magicBytes);
            if(tag.isPresent()) {
                return tag;
            }
        }

        return Optional.empty();
    }

    private static Optional<byte[]> findTag(Script script, byte[] magicBytes) {
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
            return Optional.of(tag);
        }

        if(tag.length < MODERN_MIN_TAG_LEN
                || tag[4] != (byte)StrataBridgeProtocol.BRIDGE_V1_SUBPROTOCOL_ID
                || tag[5] != (byte)StrataBridgeProtocol.DEPOSIT_REQUEST_TX_TYPE) {
            return Optional.empty();
        }

        return Optional.of(tag);
    }
}
