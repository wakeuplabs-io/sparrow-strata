package com.sparrowwallet.sparrow.strata.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrataDepositDenominationParserTest {
    @Test
    void parsesTopLevelDepositAmount() {
        JsonObject result = JsonParser.parseString("{\"deposit_amount\":2000000000}").getAsJsonObject();

        OptionalLong amount = StrataDepositDenominationParser.parseDepositAmountFromRollupParams(result);

        assertTrue(amount.isPresent());
        assertEquals(2_000_000_000L, amount.getAsLong());
    }

    @Test
    void parsesNestedRollupDepositAmount() {
        JsonObject result = JsonParser.parseString("{\"rollup\":{\"deposit_amount\":\"1000000000\"}}").getAsJsonObject();

        OptionalLong amount = StrataDepositDenominationParser.parseDepositAmountFromRollupParams(result);

        assertTrue(amount.isPresent());
        assertEquals(1_000_000_000L, amount.getAsLong());
    }

    @Test
    void returnsEmptyWhenRollupParamsMissingDepositAmount() {
        JsonObject result = JsonParser.parseString("{\"rollup\":{\"bridge_fee\":1000}}").getAsJsonObject();

        OptionalLong amount = StrataDepositDenominationParser.parseDepositAmountFromRollupParams(result);

        assertFalse(amount.isPresent());
    }

    @Test
    void rpcExceptionIdentifiesMethodNotFound() {
        StrataRpcClient.StrataRpcException exception = new StrataRpcClient.StrataRpcException(-32601, "Method not found");

        assertTrue(exception.isMethodNotFound());
    }
}
