package com.sparrowwallet.sparrow.strata.deposit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepositTransactionFeeEstimatorTest {
    @Test
    void depositTransactionVirtualSizeIsStable() {
        double vsize = DepositTransactionFeeEstimator.getDepositTransactionVirtualSize();
        assertTrue(vsize > 100 && vsize < 300, "Unexpected deposit transaction vsize: " + vsize);
    }

    @Test
    void calculatesDepFeeFromFeeRate() {
        double vsize = DepositTransactionFeeEstimator.getDepositTransactionVirtualSize();
        assertEquals((long)Math.floor(vsize * 5.0), DepositTransactionFeeEstimator.calculateDepFee(5.0));
    }
}
