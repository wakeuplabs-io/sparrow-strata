package com.sparrowwallet.sparrow.strata.deposit;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepositAmountValidatorTest {
    private static final long DENOMINATION_SATS = 1_000_000_000L;
    private static final long MAX_DEPOSIT_SATS = 10_000_000_000L;

    @Test
    void acceptsExactDenomination() {
        assertTrue(DepositAmountValidator.validate(DENOMINATION_SATS, DENOMINATION_SATS, MAX_DEPOSIT_SATS).isEmpty());
    }

    @Test
    void acceptsExactMultiple() {
        assertTrue(DepositAmountValidator.validate(3 * DENOMINATION_SATS, DENOMINATION_SATS, MAX_DEPOSIT_SATS).isEmpty());
    }

    @Test
    void acceptsMaximumDeposit() {
        assertTrue(DepositAmountValidator.validate(MAX_DEPOSIT_SATS, DENOMINATION_SATS, MAX_DEPOSIT_SATS).isEmpty());
    }

    @Test
    void rejectsNonMultiple() {
        Optional<String> error = DepositAmountValidator.validate(DENOMINATION_SATS + 1, DENOMINATION_SATS, MAX_DEPOSIT_SATS);
        assertTrue(error.isPresent());
        assertEquals(DepositAmountValidator.formatInvalidAmountMessage(DENOMINATION_SATS, MAX_DEPOSIT_SATS), error.get());
    }

    @Test
    void rejectsAboveMaximum() {
        Optional<String> error = DepositAmountValidator.validate(MAX_DEPOSIT_SATS + DENOMINATION_SATS, DENOMINATION_SATS, MAX_DEPOSIT_SATS);
        assertTrue(error.isPresent());
    }

    @Test
    void rejectsZeroAmount() {
        Optional<String> error = DepositAmountValidator.validate(0, DENOMINATION_SATS, MAX_DEPOSIT_SATS);
        assertTrue(error.isPresent());
        assertEquals("Amount must be greater than zero", error.get());
    }

    @Test
    void largestValidAmountCapsAtMaximumAndDenomination() {
        long candidate = MAX_DEPOSIT_SATS + (DENOMINATION_SATS / 2);
        assertEquals(MAX_DEPOSIT_SATS, DepositAmountValidator.largestValidAmount(candidate, DENOMINATION_SATS, MAX_DEPOSIT_SATS));
        assertEquals(2 * DENOMINATION_SATS, DepositAmountValidator.largestValidAmount(2 * DENOMINATION_SATS + 1, DENOMINATION_SATS, MAX_DEPOSIT_SATS));
    }
}
