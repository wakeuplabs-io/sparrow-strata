package com.sparrowwallet.sparrow.strata.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalLong;

final class StrataRollupParamsParser {
    private static final int MAGIC_BYTES_LEN = 4;

    private StrataRollupParamsParser() {
    }

    static Optional<StrataRollupParams> parse(JsonObject result) {
        if(result == null) {
            return Optional.empty();
        }

        JsonObject paramsObject = resolveParamsObject(result);
        byte[] magicBytes = parseMagicBytes(paramsObject)
                .or(() -> parseMagicBytes(result))
                .orElse(null);
        OptionalLong depositAmount = StrataDepositDenominationParser.parseDepositAmountFromRollupParams(result);
        Long depositAmountSats = depositAmount.isPresent() ? depositAmount.getAsLong() : null;
        Integer recoveryDelay = parseRecoveryDelay(paramsObject)
                .or(() -> parseRecoveryDelay(result))
                .orElse(null);

        StrataRollupParams params = StrataRollupParams.of(magicBytes, depositAmountSats, recoveryDelay);
        return params == null || params.isEmpty() ? Optional.empty() : Optional.of(params);
    }

    private static JsonObject resolveParamsObject(JsonObject result) {
        if(result.has("rollup") && result.get("rollup").isJsonObject()) {
            return result.getAsJsonObject("rollup");
        }
        return result;
    }

    private static Optional<byte[]> parseMagicBytes(JsonObject paramsObject) {
        if(paramsObject == null || !paramsObject.has("magic_bytes")) {
            return Optional.empty();
        }
        JsonElement magicElement = paramsObject.get("magic_bytes");
        if(magicElement == null || !magicElement.isJsonPrimitive() || !magicElement.getAsJsonPrimitive().isString()) {
            return Optional.empty();
        }
        String magic = magicElement.getAsString();
        if(magic.length() != MAGIC_BYTES_LEN) {
            return Optional.empty();
        }
        return Optional.of(magic.getBytes(StandardCharsets.US_ASCII));
    }

    private static Optional<Integer> parseRecoveryDelay(JsonObject paramsObject) {
        if(paramsObject == null || !paramsObject.has("recovery_delay")) {
            return Optional.empty();
        }
        JsonElement delayElement = paramsObject.get("recovery_delay");
        if(delayElement == null || !delayElement.isJsonPrimitive()) {
            return Optional.empty();
        }
        if(delayElement.getAsJsonPrimitive().isNumber()) {
            return Optional.of(delayElement.getAsInt());
        }
        if(delayElement.getAsJsonPrimitive().isString()) {
            try {
                return Optional.of(Integer.parseInt(delayElement.getAsString()));
            } catch(NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
