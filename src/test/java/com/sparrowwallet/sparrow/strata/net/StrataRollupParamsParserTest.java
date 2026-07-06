package com.sparrowwallet.sparrow.strata.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrataRollupParamsParserTest {
    @Test
    void parsesMagicBytesDepositAmountAndRecoveryDelay() {
        JsonObject result = JsonParser.parseString("""
                {
                  "magic_bytes": "alpn",
                  "deposit_amount": 1000000000,
                  "recovery_delay": 1008
                }
                """).getAsJsonObject();

        Optional<StrataRollupParams> params = StrataRollupParamsParser.parse(result);

        assertTrue(params.isPresent());
        assertArrayEquals("alpn".getBytes(StandardCharsets.US_ASCII), params.get().getMagicBytes().orElseThrow());
        assertEquals(1_000_000_000L, params.get().getDepositAmountSats().orElseThrow());
        assertEquals(1008, params.get().getRecoveryDelay().orElseThrow());
    }

    @Test
    void parsesUppercaseMagicBytes() {
        JsonObject result = JsonParser.parseString("{\"magic_bytes\":\"ALPN\",\"deposit_amount\":1000000000}").getAsJsonObject();

        Optional<StrataRollupParams> params = StrataRollupParamsParser.parse(result);

        assertTrue(params.isPresent());
        assertArrayEquals("ALPN".getBytes(StandardCharsets.US_ASCII), params.get().getMagicBytes().orElseThrow());
    }

    @Test
    void rejectsInvalidMagicBytesLength() {
        JsonObject result = JsonParser.parseString("{\"magic_bytes\":\"alp\",\"deposit_amount\":1000000000}").getAsJsonObject();

        Optional<StrataRollupParams> params = StrataRollupParamsParser.parse(result);

        assertTrue(params.isPresent());
        assertFalse(params.get().getMagicBytes().isPresent());
        assertEquals(1_000_000_000L, params.get().getDepositAmountSats().orElseThrow());
    }

    @Test
    void parsesNestedRollupFields() {
        JsonObject result = JsonParser.parseString("""
                {
                  "rollup": {
                    "magic_bytes": "alpn",
                    "recovery_delay": 504
                  },
                  "deposit_amount": 2000000000
                }
                """).getAsJsonObject();

        Optional<StrataRollupParams> params = StrataRollupParamsParser.parse(result);

        assertTrue(params.isPresent());
        assertArrayEquals("alpn".getBytes(StandardCharsets.US_ASCII), params.get().getMagicBytes().orElseThrow());
        assertEquals(2_000_000_000L, params.get().getDepositAmountSats().orElseThrow());
        assertEquals(504, params.get().getRecoveryDelay().orElseThrow());
    }

    @Test
    void returnsEmptyWhenNoRecognizedFields() {
        JsonObject result = JsonParser.parseString("{\"rollup\":{\"bridge_fee\":1000}}").getAsJsonObject();

        assertFalse(StrataRollupParamsParser.parse(result).isPresent());
    }
}
