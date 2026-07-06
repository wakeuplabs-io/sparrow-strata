package com.sparrowwallet.sparrow.strata.model;

public final class AlpenAddressParseResult {
    private final AlpenAddress address;
    private final DepositDescriptor depositDescriptor;

    public AlpenAddressParseResult(AlpenAddress address, DepositDescriptor depositDescriptor) {
        this.address = address;
        this.depositDescriptor = depositDescriptor;
    }

    public AlpenAddress getAddress() {
        return address;
    }

    public DepositDescriptor getDepositDescriptor() {
        return depositDescriptor;
    }
}
