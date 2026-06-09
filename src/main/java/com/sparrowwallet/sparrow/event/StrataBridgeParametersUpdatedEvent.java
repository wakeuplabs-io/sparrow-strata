package com.sparrowwallet.sparrow.event;

public class StrataBridgeParametersUpdatedEvent {
    private final Long depositUtxoAmountSats;

    public StrataBridgeParametersUpdatedEvent(Long depositUtxoAmountSats) {
        this.depositUtxoAmountSats = depositUtxoAmountSats;
    }

    public Long getDepositUtxoAmountSats() {
        return depositUtxoAmountSats;
    }
}
