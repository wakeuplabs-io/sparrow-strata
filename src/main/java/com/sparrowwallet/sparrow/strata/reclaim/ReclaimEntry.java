package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.strata.model.AlpenAddress;
import com.sparrowwallet.sparrow.wallet.Function;
import com.sparrowwallet.sparrow.wallet.HashIndexEntry;

import java.util.Optional;

public class ReclaimEntry extends HashIndexEntry {
    private final Address address;
    private final AlpenAddress destinationAddress;

    public ReclaimEntry(Wallet wallet, BlockTransactionHashIndex hashIndex, Address address, AlpenAddress destinationAddress) {
        super(wallet, hashIndex, Type.OUTPUT, KeyPurpose.RECEIVE);
        this.address = address;
        this.destinationAddress = destinationAddress;
    }

    public Address getAddress() {
        return address;
    }

    /**
     * The original Alpen deposit destination this UTXO was heading to, recovered from the DRT's
     * OP_RETURN tag, if it could be parsed.
     */
    public Optional<AlpenAddress> getDestinationAddress() {
        return Optional.ofNullable(destinationAddress);
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
