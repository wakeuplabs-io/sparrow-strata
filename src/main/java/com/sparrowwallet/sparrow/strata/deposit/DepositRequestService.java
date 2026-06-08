package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.P2TRAddress;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class DepositRequestService {
    private static final Logger log = LoggerFactory.getLogger(DepositRequestService.class);

    private final Wallet wallet;
    private final DepositDescriptor depositDescriptor;
    private final long amountSats;
    private final String label;
    private final double feeRate;
    private final double minimumFeeRate;
    private final double minRelayFeeRate;
    private final Long userFee;
    private final Integer currentBlockHeight;
    private final boolean groupByAddress;
    private final boolean includeMempoolOutputs;

    public DepositRequestService(Wallet wallet, DepositDescriptor depositDescriptor, long amountSats, String label, double feeRate,
                                 double minimumFeeRate, double minRelayFeeRate, Long userFee, Integer currentBlockHeight,
                                 boolean groupByAddress, boolean includeMempoolOutputs) {
        this.wallet = wallet;
        this.depositDescriptor = depositDescriptor;
        this.amountSats = amountSats;
        this.label = label;
        this.feeRate = feeRate;
        this.minimumFeeRate = minimumFeeRate;
        this.minRelayFeeRate = minRelayFeeRate;
        this.userFee = userFee;
        this.currentBlockHeight = currentBlockHeight;
        this.groupByAddress = groupByAddress;
        this.includeMempoolOutputs = includeMempoolOutputs;
    }

    public DepositRequestResult createWalletTransaction() throws InsufficientFundsException {
        RecoveryKeyPair recoveryKeyPair = RecoveryKeyPair.generate();
        byte[] bridgeOperatorPubkey = StrataBridgeConstants.getBridgeOperatorPubkey(Network.get());
        P2TRAddress bridgeInAddress = DepositRequestLockingScript.createBridgeInAddress(
                recoveryKeyPair.getXOnlyPublicKey(),
                bridgeOperatorPubkey,
                StrataBridgeConstants.RECOVER_DELAY
        );

        DrtHeaderAux headerAux = DrtHeaderAux.create(recoveryKeyPair.getXOnlyPublicKey(), depositDescriptor.encodeToBytes());
        Script opReturnScript = Sps50Encoder.encodeOpReturnScript(headerAux.buildAuxData());
        byte[] opReturnPayload = Sps50Encoder.encodeTag(headerAux.buildAuxData());

        Payment payment = new Payment(bridgeInAddress, label, amountSats, false);
        List<Payment> payments = List.of(payment);
        List<UtxoSelector> utxoSelectors = getUtxoSelectors(payments);
        List<TxoFilter> txoFilters = List.of(new SpentTxoFilter(null), new FrozenTxoFilter(), new CoinbaseTxoFilter(wallet));

        TransactionParameters params = new TransactionParameters(
                utxoSelectors,
                txoFilters,
                payments,
                List.of(opReturnPayload),
                Set.of(),
                feeRate,
                minimumFeeRate,
                minRelayFeeRate,
                userFee,
                currentBlockHeight,
                groupByAddress,
                includeMempoolOutputs,
                true
        );

        WalletTransaction walletTransaction = wallet.createWalletTransaction(params);
        WalletTransaction orderedTransaction = DepositOutputOrdering.reorder(walletTransaction, opReturnScript);

        log.info("Built deposit request transaction with recovery public key {}", Utils.bytesToHex(recoveryKeyPair.getXOnlyPublicKey()));
        return new DepositRequestResult(orderedTransaction, recoveryKeyPair);
    }

    private List<UtxoSelector> getUtxoSelectors(List<Payment> payments) {
        long noInputsFee = wallet.getNoInputsFee(payments, feeRate);
        long costOfChange = wallet.getCostOfChange(feeRate, minimumFeeRate);
        return List.of(new BnBUtxoSelector(noInputsFee, costOfChange), new KnapsackUtxoSelector(noInputsFee));
    }

    public record DepositRequestResult(WalletTransaction walletTransaction, RecoveryKeyPair recoveryKeyPair) {
    }
}
