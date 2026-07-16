package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.sparrow.strata.protocol.StrataBridgeProtocol;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionWitness;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Estimates the mining fee for the bridge operator deposit transaction (DT) that spends a DRT.
 * Per the Strata protocol, users pay this fee via extra sats in the DRT bridge-in output; the UI
 * displays it as {@code dep_fee = DT virtual size * fee rate}.
 * <p>
 * Virtual size is derived from a fixed representative DT structure per {@link Network}.
 */
public final class DepositTransactionFeeEstimator {
    private static final Map<Network, Double> DEPOSIT_TX_VIRTUAL_SIZE_BY_NETWORK = new EnumMap<>(Network.class);

    private DepositTransactionFeeEstimator() {
    }

    public static double getDepositTransactionVirtualSize() {
        return DEPOSIT_TX_VIRTUAL_SIZE_BY_NETWORK.computeIfAbsent(
                Network.get(), DepositTransactionFeeEstimator::computeDepositTransactionVirtualSize);
    }

    public static long calculateDepFee(double feeRateSatPerVb) {
        return (long)Math.floor(getDepositTransactionVirtualSize() * feeRateSatPerVb);
    }

    static void clearCacheForTesting() {
        DEPOSIT_TX_VIRTUAL_SIZE_BY_NETWORK.clear();
    }

    private static double computeDepositTransactionVirtualSize(Network network) {
        return buildRepresentativeDepositTransaction(network).getVirtualSize();
    }

    private static Transaction buildRepresentativeDepositTransaction(Network network) {
        byte[] bridgeOperatorPubkey = StrataBridgeProtocol.getBridgeOperatorPubkey(network);
        Script opReturnScript = Sps50Encoder.encodeOpReturnScript(
                StrataBridgeProtocol.DEPOSIT_TX_TYPE,
                DtHeaderAux.create(0).buildAuxData(),
                StrataBridgeProtocol.getMagicBytesFallback(network)
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
