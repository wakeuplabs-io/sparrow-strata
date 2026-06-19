package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.strata.model.AlpenConstants;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeParametersService;

import java.util.Arrays;

/**
 * Estimates DRT output weight (OP_RETURN + bridge-in P2TR) for fee reservation.
 */
public final class DepositDrtOutputVbytesEstimator {
    private static final byte[] SIZE_ESTIMATE_RECOVERY_PK = new byte[32];

    private DepositDrtOutputVbytesEstimator() {
    }

    public static long estimateOutputVbytes(DepositDescriptor descriptor) {
        StrataBridgeParametersService bridgeParameters = StrataBridgeParametersService.getInstance();
        byte[] bridgeOperatorPubkey = StrataBridgeConstants.getBridgeOperatorPubkey(Network.get());
        return estimateOutputVbytes(
                descriptor,
                bridgeParameters.getMagicBytes(),
                bridgeParameters.getRecoveryDelay(),
                bridgeOperatorPubkey);
    }

    public static long estimateOutputVbytes(DepositDescriptor descriptor, byte[] magicBytes, int recoveryDelay, byte[] bridgeOperatorPubkey) {
        byte[] destinationBytes = Sps50Encoder.usesLegacyTagFormat(magicBytes)
                ? descriptor.getDestSubject()
                : descriptor.encodeToBytes();
        DrtHeaderAux headerAux = DrtHeaderAux.create(SIZE_ESTIMATE_RECOVERY_PK, destinationBytes);
        Script opReturnScript = Sps50Encoder.encodeOpReturnScript(headerAux.buildAuxData(), magicBytes);
        Script bridgeInScript = DepositRequestLockingScript.createLockingScript(
                SIZE_ESTIMATE_RECOVERY_PK, bridgeOperatorPubkey, recoveryDelay);

        Transaction transaction = new Transaction();
        if(Sps50Encoder.usesLegacyTagFormat(magicBytes)) {
            transaction.addOutput(1L, bridgeInScript);
            transaction.addOutput(0, opReturnScript);
        } else {
            transaction.addOutput(0, opReturnScript);
            transaction.addOutput(1L, bridgeInScript);
        }
        return (long)Math.ceil(transaction.getVirtualSize());
    }

    public static DepositDescriptor conservativeDescriptorForEstimate() {
        byte[] maxSubject = new byte[AlpenConstants.MAX_SUBJECT_BYTES];
        Arrays.fill(maxSubject, (byte)0xff);
        return DepositDescriptor.create(AlpenConstants.ALPEN_EE_ACCT_SERIAL, maxSubject);
    }
}
