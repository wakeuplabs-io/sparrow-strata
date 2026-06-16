package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.strata.net.StrataBridgeKeyVerificationService;

public class StrataBridgeKeyVerificationUpdatedEvent {
    private final StrataBridgeKeyVerificationService.StrataBridgeKeyStatus status;
    private final String message;

    public StrataBridgeKeyVerificationUpdatedEvent(StrataBridgeKeyVerificationService.StrataBridgeKeyStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public StrataBridgeKeyVerificationService.StrataBridgeKeyStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
