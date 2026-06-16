package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionWitness;

import java.util.List;

/**
 * Estimates the mining fee for the bridge operator deposit transaction (DT) that spends a DRT.
 * Per the Strata protocol, users pay this fee via extra sats in the DRT bridge-in output; the UI
 * displays it as {@code dep_fee = DT virtual size * fee rate}.
 */
public final class DepositTransactionFeeEstimator {
    private static final double DEPOSIT_TX_VIRTUAL_SIZE = computeDepositTransactionVirtualSize();

    private DepositTransactionFeeEstimator() {
    }

    public static double getDepositTransactionVirtualSize() {
        return DEPOSIT_TX_VIRTUAL_SIZE;
    }

    public static long calculateDepFee(double feeRateSatPerVb) {
        return (long)Math.floor(DEPOSIT_TX_VIRTUAL_SIZE * feeRateSatPerVb);
    }

    private static double computeDepositTransactionVirtualSize() {
        return buildRepresentativeDepositTransaction().getVirtualSize();
    }

    private static Transaction buildRepresentativeDepositTransaction() {
        byte[] bridgeOperatorPubkey = StrataBridgeConstants.getBridgeOperatorPubkey(Network.get());
        Script opReturnScript = Sps50Encoder.encodeOpReturnScript(
                StrataBridgeConstants.DEPOSIT_TX_TYPE,
                DtHeaderAux.create(0).buildAuxData()
        );
        Script bridgeOutScript = ScriptType.P2TR.getOutputScript(bridgeOperatorPubkey);

        Transaction transaction = new Transaction();
        transaction.setVersion(2);
        TransactionWitness witness = new TransactionWitness(transaction, List.of(new byte[64]));
        transaction.addInput(Sha256Hash.ZERO_HASH, 1, new Script(new byte[0]), witness);
        transaction.addOutput(0, opReturnScript);
        transaction.addOutput(1_000_000_000L, bridgeOutScript);
        return transaction;
    }
}
