package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.wallet.UtxoSelector;
import com.sparrowwallet.drongo.wallet.TxoFilter;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.util.List;
import java.util.Set;

public class DepositFeeService extends Service<WalletTransaction> {
    private final DepositRequestService depositRequestService;
    private boolean ignoreResult;

    public DepositFeeService(Wallet wallet, DepositDescriptor depositDescriptor, long amountSats, String label,
                             double selectionFeeRate, double sliderFeeRate, double minimumFeeRate, double minRelayFeeRate,
                             Long userFee, Integer currentBlockHeight, boolean groupByAddress, boolean includeMempoolOutputs,
                             List<UtxoSelector> utxoSelectors, Set<WalletNode> excludedChangeNodes, List<TxoFilter> txoFilters,
                             RecoveryKeyPair recoveryKeyPair) {
        this.depositRequestService = new DepositRequestService(wallet, depositDescriptor, amountSats, label,
                selectionFeeRate, sliderFeeRate, minimumFeeRate, minRelayFeeRate, userFee, currentBlockHeight, groupByAddress, includeMempoolOutputs,
                utxoSelectors, excludedChangeNodes, txoFilters, recoveryKeyPair);
    }

    @Override
    protected Task<WalletTransaction> createTask() {
        return new Task<>() {
            @Override
            protected WalletTransaction call() throws Exception {
                try {
                    updateMessage("Selecting UTXOs...");
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
