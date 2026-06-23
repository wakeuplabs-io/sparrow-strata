package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.P2TRAddress;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptChunk;
import com.sparrowwallet.drongo.protocol.ScriptOpCodes;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.VarInt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DepositRequestLockingScript {
    private DepositRequestLockingScript() {
    }

    public static Script createLockingScript(byte[] recoveryPk, byte[] bridgeInternalKey, int recoveryDelay) {
        Script tapscript = createRecoveryTapscript(recoveryPk, recoveryDelay);
        Sha256Hash merkleRoot = computeTapLeafHash(tapscript);
        byte[] outputKey = computeTaprootOutputKey(bridgeInternalKey, merkleRoot);
        return ScriptType.P2TR.getOutputScript(outputKey);
    }

    public static P2TRAddress createBridgeInAddress(byte[] recoveryPk, byte[] bridgeInternalKey, int recoveryDelay) {
        Script tapscript = createRecoveryTapscript(recoveryPk, recoveryDelay);
        Sha256Hash merkleRoot = computeTapLeafHash(tapscript);
        byte[] outputKey = computeTaprootOutputKey(bridgeInternalKey, merkleRoot);
        return new P2TRAddress(outputKey);
    }

    public static Script createRecoveryTapscript(byte[] recoveryPk, int recoveryDelay) {
        if(recoveryPk.length != 32) {
            throw new DepositRequestException("Recovery public key must be 32 bytes");
        }
        if(recoveryDelay < 0) {
            throw new DepositRequestException("Recovery delay must be non-negative");
        }

        List<ScriptChunk> chunks = List.of(
                new ScriptChunk(recoveryPk.length, recoveryPk),
                ScriptChunk.fromOpcode(ScriptOpCodes.OP_CHECKSIGVERIFY),
                pushScriptNumber(recoveryDelay),
                ScriptChunk.fromOpcode(ScriptOpCodes.OP_CHECKSEQUENCEVERIFY)
        );
        return new Script(chunks);
    }

    private static ScriptChunk pushScriptNumber(long value) {
        if(value == 0) {
            return ScriptChunk.fromOpcode(ScriptOpCodes.OP_0);
        }
        if(value >= 1 && value <= 16) {
            return ScriptChunk.fromOpcode(Script.encodeToOpN((int)value));
        }

        long absValue = Math.abs(value);
        List<Byte> encoded = new ArrayList<>();
        while(absValue != 0) {
            encoded.add((byte)(absValue & 0xff));
            absValue >>= 8;
        }
        if((encoded.get(encoded.size() - 1) & 0x80) != 0) {
            encoded.add(value < 0 ? (byte)0x80 : (byte)0);
        } else if(value < 0) {
            encoded.set(encoded.size() - 1, (byte)(encoded.get(encoded.size() - 1) | 0x80));
        }

        byte[] bytes = new byte[encoded.size()];
        for(int i = 0; i < encoded.size(); i++) {
            bytes[i] = encoded.get(i);
        }
        return ScriptChunk.fromData(bytes);
    }

    public static Sha256Hash computeTapLeafHash(Script tapscript) {
        try {
            ByteArrayOutputStream leafStream = new ByteArrayOutputStream();
            leafStream.write(Transaction.LEAF_VERSION_TAPSCRIPT);
            byte[] program = tapscript.getProgram();
            leafStream.write(new VarInt(program.length).encode());
            leafStream.write(program);
            return Sha256Hash.wrap(Utils.taggedHash("TapLeaf", leafStream.toByteArray()));
        } catch(IOException e) {
            throw new DepositRequestException("Failed to hash tapscript leaf", e);
        }
    }

    public static byte[] computeTaprootOutputKey(byte[] internalKeyXOnly, Sha256Hash merkleRoot) {
        byte[] tweakData;
        if(merkleRoot != null) {
            tweakData = new byte[64];
            System.arraycopy(internalKeyXOnly, 0, tweakData, 0, 32);
            System.arraycopy(merkleRoot.getBytes(), 0, tweakData, 32, 32);
        } else {
            tweakData = Arrays.copyOf(internalKeyXOnly, internalKeyXOnly.length);
        }

        byte[] tweakHash = Utils.taggedHash("TapTweak", tweakData);
        ECKey internalKey = ECKey.fromPublicOnly(internalKeyXOnly);
        if(internalKey.hasOddYCoord()) {
            internalKey = internalKey.negate();
        }

        return internalKey.add(ECKey.fromPrivate(tweakHash), true).getPubKeyXCoord();
    }
}
