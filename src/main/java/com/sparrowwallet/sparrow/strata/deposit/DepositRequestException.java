package com.sparrowwallet.sparrow.strata.deposit;

public class DepositRequestException extends RuntimeException {
    public DepositRequestException(String message) {
        super(message);
    }

    public DepositRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
