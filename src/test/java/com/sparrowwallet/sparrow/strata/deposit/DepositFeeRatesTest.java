package com.sparrowwallet.sparrow.strata.deposit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DepositFeeRatesTest {
    @Test
    void selectionFeeRateUsesSliderWhenUserFeeNotSet() {
        assertEquals(5.0, DepositFeeRates.resolveSelectionFeeRate(false, 5.0, 1.0));
    }

    @Test
    void selectionFeeRateUsesMinRelayWhenUserFeeSet() {
        assertEquals(1.0, DepositFeeRates.resolveSelectionFeeRate(true, 50.0, 1.0));
    }

    @Test
    void resolveMiningFeeFromTotalReturnsNullWhenTotalBelowDepFee() {
        assertNull(DepositFeeRates.resolveMiningFeeFromTotal(100L, 200L));
    }

    @Test
    void resolveMiningFeeFromTotalSubtractsDepFee() {
        assertEquals(300L, DepositFeeRates.resolveMiningFeeFromTotal(500L, 200L));
    }

    @Test
    void resolveMiningFeeFromTotalReturnsNullWhenTotalUnset() {
        assertNull(DepositFeeRates.resolveMiningFeeFromTotal(null, 200L));
    }
}
