package com.sparrowwallet.sparrow.strata.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrataBridgeKeyParserTest {
    @Test
    void parsesTopLevelBridgePubkey() {
        JsonObject result = JsonParser.parseString("{\"bridge_pubkey\":\"0xAA\"}").getAsJsonObject();

        Optional<String> pubkey = StrataBridgeKeyParser.parseBridgePubkeyFromResult(result);

        assertTrue(pubkey.isPresent());
        assertEquals("aa", pubkey.get());
    }

    @Test
    void parsesNestedBridgePubkey() {
        JsonObject result = JsonParser.parseString("{\"bridge\":{\"bridge_pubkey\":\"bb\"}}").getAsJsonObject();

        Optional<String> pubkey = StrataBridgeKeyParser.parseBridgePubkeyFromResult(result);

        assertTrue(pubkey.isPresent());
        assertEquals("bb", pubkey.get());
    }

    @Test
    void returnsEmptyWhenMissingBridgePubkey() {
        JsonObject result = JsonParser.parseString("{\"bridge\":{}}").getAsJsonObject();

        Optional<String> pubkey = StrataBridgeKeyParser.parseBridgePubkeyFromResult(result);

        assertFalse(pubkey.isPresent());
    }
}

