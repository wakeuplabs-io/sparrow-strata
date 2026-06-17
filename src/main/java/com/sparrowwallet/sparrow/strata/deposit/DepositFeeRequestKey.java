package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import com.sparrowwallet.sparrow.wallet.OptimizationStrategy;

/**
 * Cache key for async deposit fee preview requests.
 *
 * @param sliderFeeRate UI slider rate; drives dep_fee and display
 * @param selectionFeeRate rate passed to UTXO selectors / {@link DepositRequestService}
 */
public record DepositFeeRequestKey(
        DepositDescriptor descriptor,
        long amountSats,
        double sliderFeeRate,
        double selectionFeeRate,
        Long userFee,
        String label,
        OptimizationStrategy optimizationStrategy,
        int coinControlHash
) {
}
