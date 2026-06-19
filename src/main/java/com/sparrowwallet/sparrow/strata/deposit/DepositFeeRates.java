package com.sparrowwallet.sparrow.strata.deposit;

/**
 * Pure fee-rate helpers for the deposit flow.
 */
public final class DepositFeeRates {
    private DepositFeeRates() {
    }

    /**
     * Fee rate passed to UTXO selectors and {@link DepositRequestService} when constructing a transaction.
     * When the user sets a custom absolute fee amount, using the slider fee rate can cause UTXO selectors
     * to underestimate effective UTXO values and fail to find a solution. In that case, use minimum relay
     * fee rate (~1 sat/vB) for maximum flexibility.
     *
     * @param userFeeSet whether the user typed an absolute fee in the fee field
     * @param sliderFeeRate current UI slider / target-blocks fee rate
     * @param minRelayFeeRate network minimum relay fee rate
     * @return fee rate for UTXO selection and transaction construction
     */
    public static double resolveSelectionFeeRate(boolean userFeeSet, double sliderFeeRate, double minRelayFeeRate) {
        return userFeeSet ? minRelayFeeRate : sliderFeeRate;
    }

    /**
     * Splits total UI fee into mining fee passed to {@link DepositRequestService} as {@code userFee}.
     *
     * @param totalFeeSats absolute fee entered by user, or null if unset
     * @param depFeeSats deposit transaction (DT) fee component at the slider rate
     * @return mining fee sats, or null if total does not cover dep fee
     */
    public static Long resolveMiningFeeFromTotal(Long totalFeeSats, long depFeeSats) {
        if(totalFeeSats == null) {
            return null;
        }
        if(totalFeeSats < depFeeSats) {
            return null;
        }
        return totalFeeSats - depFeeSats;
    }

    /**
     * Bridge-in output fee component ({@code dep_fee}): mining fee to settle the operator deposit
     * transaction within {@code takeback_delay} blocks ({@code DT virtual size × fee rate}).
     */
    public static long calculateDepFee(double sliderFeeRate) {
        return DepositTransactionFeeEstimator.calculateDepFee(sliderFeeRate);
    }

    public static long totalMiningFeeSats(long depositRequestMiningFeeSats, double sliderFeeRate) {
        return depositRequestMiningFeeSats + calculateDepFee(sliderFeeRate);
    }
}
