package com.sparrowwallet.sparrow.strata.reclaim;

public class ReclaimException extends RuntimeException {
    public ReclaimException(String message) {
        super(message);
    }

    public ReclaimException(String message, Throwable cause) {
        super(message, cause);
    }
}
