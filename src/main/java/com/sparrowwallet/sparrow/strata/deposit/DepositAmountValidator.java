package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.BitcoinUnit;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;

public final class DepositAmountValidator {
    private DepositAmountValidator() {
    }

    public static Optional<String> validate(long amountSats, long depositUtxoAmountSats, long maxDepositSats) {
        if(amountSats <= 0) {
            return Optional.of("Amount must be greater than zero");
        }
        if(depositUtxoAmountSats <= 0) {
            return Optional.empty();
        }
        if(amountSats > maxDepositSats || amountSats % depositUtxoAmountSats != 0) {
            return Optional.of(formatInvalidAmountMessage(depositUtxoAmountSats, maxDepositSats));
        }
        return Optional.empty();
    }

    public static String formatInvalidAmountMessage(long depositUtxoAmountSats, long maxDepositSats) {
        return "Amount must be a multiple of " + formatBtc(depositUtxoAmountSats) + " BTC up to " + formatBtc(maxDepositSats) + " BTC";
    }

    public static long largestValidAmount(long candidateSats, long depositUtxoAmountSats, long maxDepositSats) {
        if(candidateSats <= 0 || depositUtxoAmountSats <= 0) {
            return 0;
        }
        long capped = Math.min(candidateSats, maxDepositSats);
        return (capped / depositUtxoAmountSats) * depositUtxoAmountSats;
    }

    private static String formatBtc(long sats) {
        DecimalFormat df = new DecimalFormat("#.########", DecimalFormatSymbols.getInstance(Locale.ROOT));
        df.setMaximumFractionDigits(8);
        df.setMinimumFractionDigits(0);
        return df.format(BitcoinUnit.BTC.getValue(sats));
    }
}
