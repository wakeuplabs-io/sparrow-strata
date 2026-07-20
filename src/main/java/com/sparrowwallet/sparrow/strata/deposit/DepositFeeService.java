package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.wallet.UtxoSelector;
import com.sparrowwallet.drongo.wallet.TxoFilter;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import com.sparrowwallet.sparrow.strata.reclaim.ReclaimEntry;
import com.sparrowwallet.sparrow.strata.reclaim.RetryDepositTransactionBuilder;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.util.List;
import java.util.Set;

public class DepositFeeService extends Service<WalletTransaction> {
    private final DepositRequestService depositRequestService;
    private final RetryDepositRequest retryDepositRequest;
    private boolean ignoreResult;

    private record RetryDepositRequest(Wallet wallet, List<ReclaimEntry> reclaimEntries, DepositDescriptor depositDescriptor,
            long amountSats, String label, double feeRate, double depFeeRate, double minimumFeeRate, double minRelayFeeRate,
            Long userFee, Integer currentBlockHeight, boolean groupByAddress, boolean includeMempoolOutputs,
            List<UtxoSelector> utxoSelectors, Set<WalletNode> excludedChangeNodes, List<TxoFilter> txoFilters, WalletRecoveryKey recoveryKey) {
    }

    public DepositFeeService(Wallet wallet, DepositDescriptor depositDescriptor, long amountSats, String label,
                             double selectionFeeRate, double sliderFeeRate, double minimumFeeRate, double minRelayFeeRate,
                             Long userFee, Integer currentBlockHeight, boolean groupByAddress, boolean includeMempoolOutputs,
                             List<UtxoSelector> utxoSelectors, Set<WalletNode> excludedChangeNodes, List<TxoFilter> txoFilters,
                             WalletRecoveryKey recoveryKey) {
        this(wallet, null, depositDescriptor, amountSats, label, selectionFeeRate, sliderFeeRate, minimumFeeRate, minRelayFeeRate,
                userFee, currentBlockHeight, groupByAddress, includeMempoolOutputs, utxoSelectors, excludedChangeNodes, txoFilters, recoveryKey);
    }

    public DepositFeeService(Wallet wallet, List<ReclaimEntry> retryReclaimEntries, DepositDescriptor depositDescriptor, long amountSats, String label,
                             double selectionFeeRate, double sliderFeeRate, double minimumFeeRate, double minRelayFeeRate,
                             Long userFee, Integer currentBlockHeight, boolean groupByAddress, boolean includeMempoolOutputs,
                             List<UtxoSelector> utxoSelectors, Set<WalletNode> excludedChangeNodes, List<TxoFilter> txoFilters,
                             WalletRecoveryKey recoveryKey) {
        if(retryReclaimEntries != null && !retryReclaimEntries.isEmpty()) {
            this.depositRequestService = null;
            this.retryDepositRequest = new RetryDepositRequest(wallet, retryReclaimEntries, depositDescriptor, amountSats, label,
                    selectionFeeRate, sliderFeeRate, minimumFeeRate, minRelayFeeRate, userFee, currentBlockHeight, groupByAddress,
                    includeMempoolOutputs, utxoSelectors, excludedChangeNodes, txoFilters, recoveryKey);
        } else {
            this.depositRequestService = new DepositRequestService(wallet, depositDescriptor, amountSats, label,
                    selectionFeeRate, sliderFeeRate, minimumFeeRate, minRelayFeeRate, userFee, currentBlockHeight, groupByAddress, includeMempoolOutputs,
                    utxoSelectors, excludedChangeNodes, txoFilters, recoveryKey);
            this.retryDepositRequest = null;
        }
    }

    @Override
    protected Task<WalletTransaction> createTask() {
        return new Task<>() {
            @Override
            protected WalletTransaction call() throws Exception {
                try {
                    updateMessage("Selecting UTXOs...");
                    if(retryDepositRequest != null) {
                        RetryDepositRequest r = retryDepositRequest;
                        return RetryDepositTransactionBuilder.createWalletTransaction(r.wallet(), r.reclaimEntries(), r.depositDescriptor(),
                                r.amountSats(), r.label(), r.feeRate(), r.depFeeRate(), r.minimumFeeRate(), r.minRelayFeeRate(), r.userFee(),
                                r.currentBlockHeight(), r.groupByAddress(), r.includeMempoolOutputs(), r.utxoSelectors(), r.excludedChangeNodes(),
                                r.txoFilters(), r.recoveryKey()).walletTransaction();
                    }
                    return depositRequestService.createWalletTransaction().walletTransaction();
                } finally {
                    updateMessage("");
                }
            }
        };
    }

    public boolean isIgnoreResult() {
        return ignoreResult;
    }

    public void setIgnoreResult(boolean ignoreResult) {
        this.ignoreResult = ignoreResult;
    }
}
