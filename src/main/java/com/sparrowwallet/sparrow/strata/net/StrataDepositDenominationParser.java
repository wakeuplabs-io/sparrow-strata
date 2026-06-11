package com.sparrowwallet.sparrow.strata.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.OptionalLong;

final class StrataDepositDenominationParser {
    private StrataDepositDenominationParser() {
    }

    static OptionalLong parseDepositAmountFromRollupParams(JsonObject result) {
        if(result == null) {
            return OptionalLong.empty();
        }
        if(result.has("deposit_amount")) {
            return parseAmountField(result.get("deposit_amount"));
        }
        if(result.has("rollup") && result.get("rollup").isJsonObject()) {
            JsonObject rollup = result.getAsJsonObject("rollup");
            if(rollup.has("deposit_amount")) {
                return parseAmountField(rollup.get("deposit_amount"));
            }
        }
        return OptionalLong.empty();
    }

    static OptionalLong inferDepositDenomination(List<Long> depositAmounts) {
        long gcd = 0;
        for(Long amount : depositAmounts) {
            if(amount == null || amount <= 0) {
                continue;
            }
            gcd = gcd == 0 ? amount : gcd(gcd, amount);
        }
        return gcd > 0 ? OptionalLong.of(gcd) : OptionalLong.empty();
    }

    static OptionalLong parseAmountField(JsonElement amountElement) {
        if(amountElement == null || amountElement.isJsonNull()) {
            return OptionalLong.empty();
        }
        if(amountElement.isJsonPrimitive()) {
            if(amountElement.getAsJsonPrimitive().isNumber()) {
                return OptionalLong.of(amountElement.getAsLong());
            }
            if(amountElement.getAsJsonPrimitive().isString()) {
                try {
                    return OptionalLong.of(Long.parseLong(amountElement.getAsString()));
                } catch(NumberFormatException e) {
                    return OptionalLong.empty();
                }
            }
        }
        return OptionalLong.empty();
    }

    private static long gcd(long a, long b) {
        while(b != 0) {
            long remainder = a % b;
            a = b;
            b = remainder;
        }
        return a;
    }
}
