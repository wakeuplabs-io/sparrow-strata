package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.address.P2TRAddress;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeKeyVerificationService;
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
    private final double depFeeRate;
    private final double minimumFeeRate;
    private final double minRelayFeeRate;
    private final Long userFee;
    private final Integer currentBlockHeight;
    private final boolean groupByAddress;
    private final boolean includeMempoolOutputs;
    private final List<UtxoSelector> utxoSelectorsOverride;
    private final Set<WalletNode> excludedChangeNodes;
    private final List<TxoFilter> txoFiltersOverride;

    public DepositRequestService(Wallet wallet, DepositDescriptor depositDescriptor, long amountSats, String label, double feeRate,
                                 double depFeeRate, double minimumFeeRate, double minRelayFeeRate, Long userFee, Integer currentBlockHeight,
                                 boolean groupByAddress, boolean includeMempoolOutputs) {
        this(wallet, depositDescriptor, amountSats, label, feeRate, depFeeRate, minimumFeeRate, minRelayFeeRate, userFee, currentBlockHeight,
                groupByAddress, includeMempoolOutputs, null, Set.of(), null);
    }

    public DepositRequestService(Wallet wallet, DepositDescriptor depositDescriptor, long amountSats, String label, double feeRate,
                                 double depFeeRate, double minimumFeeRate, double minRelayFeeRate, Long userFee, Integer currentBlockHeight,
                                 boolean groupByAddress, boolean includeMempoolOutputs, List<UtxoSelector> utxoSelectorsOverride,
                                 Set<WalletNode> excludedChangeNodes, List<TxoFilter> txoFiltersOverride) {
        this.wallet = wallet;
        this.depositDescriptor = depositDescriptor;
        this.amountSats = amountSats;
        this.label = label;
        this.feeRate = feeRate;
        this.depFeeRate = depFeeRate;
        this.minimumFeeRate = minimumFeeRate;
        this.minRelayFeeRate = minRelayFeeRate;
        this.userFee = userFee;
        this.currentBlockHeight = currentBlockHeight;
        this.groupByAddress = groupByAddress;
        this.includeMempoolOutputs = includeMempoolOutputs;
        this.utxoSelectorsOverride = utxoSelectorsOverride;
        this.excludedChangeNodes = excludedChangeNodes == null ? Set.of() : excludedChangeNodes;
        this.txoFiltersOverride = txoFiltersOverride;
    }

    public DepositRequestResult createWalletTransaction() throws InsufficientFundsException {
        RecoveryKeyPair recoveryKeyPair = RecoveryKeyPair.generate();
        byte[] bridgeOperatorPubkey = StrataBridgeKeyVerificationService.getInstance()
                .getVerifiedBridgeOperatorPubkey()
                .orElseThrow(() -> new DepositRequestException(
                        StrataBridgeKeyVerificationService.getInstance().getMessage() != null
                                ? StrataBridgeKeyVerificationService.getInstance().getMessage()
                                : "Bridge deposit is unavailable"));
        P2TRAddress bridgeInAddress = DepositRequestLockingScript.createBridgeInAddress(
                recoveryKeyPair.getXOnlyPublicKey(),
                bridgeOperatorPubkey,
                StrataBridgeConstants.RECOVER_DELAY
        );

        DrtHeaderAux headerAux = DrtHeaderAux.create(recoveryKeyPair.getXOnlyPublicKey(), depositDescriptor.encodeToBytes());
        Script opReturnScript = Sps50Encoder.encodeOpReturnScript(headerAux.buildAuxData());
        byte[] opReturnPayload = Sps50Encoder.encodeTag(headerAux.buildAuxData());

        long depFee = DepositTransactionFeeEstimator.calculateDepFee(depFeeRate);
        Payment payment = new Payment(bridgeInAddress, label, amountSats + depFee, false);
        List<Payment> payments = List.of(payment);
        List<UtxoSelector> utxoSelectors = utxoSelectorsOverride != null && !utxoSelectorsOverride.isEmpty()
                ? utxoSelectorsOverride
                : getDefaultUtxoSelectors(payments);
        List<TxoFilter> txoFilters = txoFiltersOverride != null
                ? txoFiltersOverride
                : List.of(new SpentTxoFilter(null), new FrozenTxoFilter(), new CoinbaseTxoFilter(wallet));

        TransactionParameters params = new TransactionParameters(
                utxoSelectors,
                txoFilters,
                payments,
                List.of(opReturnPayload),
                excludedChangeNodes,
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

    private List<UtxoSelector> getDefaultUtxoSelectors(List<Payment> payments) {
        long noInputsFee = wallet.getNoInputsFee(payments, feeRate);
        long costOfChange = wallet.getCostOfChange(feeRate, minimumFeeRate);
        return List.of(new BnBUtxoSelector(noInputsFee, costOfChange), new KnapsackUtxoSelector(noInputsFee));
    }

    public record DepositRequestResult(WalletTransaction walletTransaction, RecoveryKeyPair recoveryKeyPair) {
    }
}
