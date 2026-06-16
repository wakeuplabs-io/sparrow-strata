package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.Function;

import java.util.List;

public class DepositActionEvent extends FunctionActionEvent {
    private final List<BlockTransactionHashIndex> utxos;
    private final boolean selectIfEmpty;

    public DepositActionEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos) {
        this(wallet, utxos, false);
    }

    public DepositActionEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos, boolean selectIfEmpty) {
        super(Function.STRATA, wallet);
        this.utxos = utxos;
        this.selectIfEmpty = selectIfEmpty;
    }

    public List<BlockTransactionHashIndex> getUtxos() {
        return utxos;
    }

    @Override
    public boolean selectFunction() {
        return selectIfEmpty || !getUtxos().isEmpty();
    }
}
