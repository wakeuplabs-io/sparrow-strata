package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.wallet.Function;
import com.sparrowwallet.sparrow.wallet.HashIndexEntry;

public class ReclaimEntry extends HashIndexEntry {
    private final Address address;

    public ReclaimEntry(Wallet wallet, BlockTransactionHashIndex hashIndex, Address address) {
        super(wallet, hashIndex, Type.OUTPUT, KeyPurpose.RECEIVE);
        this.address = address;
    }

    public Address getAddress() {
        return address;
    }

    @Override
    public String getDescription() {
        return getHashIndex().getHash().toString().substring(0, 8) + "..:" + getHashIndex().getIndex();
    }

    @Override
    public String getEntryType() {
        return "Reclaim UTXO";
    }

    @Override
    public Function getWalletFunction() {
        return Function.STRATA;
    }
}
