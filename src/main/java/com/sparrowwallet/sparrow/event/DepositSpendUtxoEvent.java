package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.util.List;

public class DepositSpendUtxoEvent {
    private final Wallet wallet;
    private final List<BlockTransactionHashIndex> utxos;

    public DepositSpendUtxoEvent(Wallet wallet, List<BlockTransactionHashIndex> utxos) {
        this.wallet = wallet;
        this.utxos = utxos;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public List<BlockTransactionHashIndex> getUtxos() {
        return utxos;
    }
}
