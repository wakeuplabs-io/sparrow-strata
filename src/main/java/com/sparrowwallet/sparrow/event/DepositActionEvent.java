package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.strata.reclaim.ReclaimEntry;
import com.sparrowwallet.sparrow.wallet.Function;

import java.util.List;

public class DepositActionEvent extends FunctionActionEvent {
    private final List<BlockTransactionHashIndex> utxos;
    private final boolean selectIfEmpty;
    private final String prefillDestination;
    private final Long prefillAmountSats;
    private final List<ReclaimEntry> retryReclaimEntries;

    public DepositActionEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos) {
        this(wallet, utxos, false);
    }

    public DepositActionEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos, boolean selectIfEmpty) {
        this(wallet, utxos, selectIfEmpty, null, null, List.of());
    }

    /**
     * Used to retry a deposit whose original bridge-in UTXO(s) are being reclaimed. The selected
     * reclaim UTXOs are spent directly, via their recovery script path, as coin-control input(s) for
     * the new deposit, combined with additional wallet UTXOs as needed to cover any shortfall or fees.
     */
    public DepositActionEvent(Wallet wallet, List<ReclaimEntry> retryReclaimEntries, String prefillDestination, long prefillAmountSats) {
        this(wallet, List.of(), true, prefillDestination, prefillAmountSats, retryReclaimEntries);
    }

    private DepositActionEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos, boolean selectIfEmpty,
                                String prefillDestination, Long prefillAmountSats, List<ReclaimEntry> retryReclaimEntries) {
        super(Function.STRATA, wallet);
        this.utxos = utxos;
        this.selectIfEmpty = selectIfEmpty;
        this.prefillDestination = prefillDestination;
        this.prefillAmountSats = prefillAmountSats;
        this.retryReclaimEntries = retryReclaimEntries == null ? List.of() : retryReclaimEntries;
    }

    public List<BlockTransactionHashIndex> getUtxos() {
        return utxos;
    }

    public String getPrefillDestination() {
        return prefillDestination;
    }

    public Long getPrefillAmountSats() {
        return prefillAmountSats;
    }

    public List<ReclaimEntry> getRetryReclaimEntries() {
        return retryReclaimEntries;
    }

    @Override
    public boolean selectFunction() {
        return selectIfEmpty || !getUtxos().isEmpty();
    }
}
