package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.Function;

import java.util.List;

public class ReclaimUtxosEntry extends Entry {
    public ReclaimUtxosEntry(List<ReclaimEntry> entries) {
        super(entries.isEmpty() ? null : entries.get(0).getWallet(), "Reclaimable UTXOs", List.copyOf(entries));
    }

    @Override
    public Long getValue() {
        return getChildren().stream().mapToLong(Entry::getValue).sum();
    }

    @Override
    public String getEntryType() {
        return "Reclaimable UTXOs";
    }

    @Override
    public Function getWalletFunction() {
        return Function.STRATA;
    }
}
