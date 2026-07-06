package com.sparrowwallet.sparrow.strata.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Optional;

public final class StrataBridgeKeyParser {
    private StrataBridgeKeyParser() {
    }

    public static Optional<String> parseBridgePubkeyFromResult(JsonObject result) {
        if(result == null) {
            return Optional.empty();
        }
        if(result.has("bridge_pubkey")) {
            return parsePubkeyField(result.get("bridge_pubkey"));
        }
        if(result.has("bridge") && result.get("bridge").isJsonObject()) {
            JsonObject bridge = result.getAsJsonObject("bridge");
            if(bridge.has("bridge_pubkey")) {
                return parsePubkeyField(bridge.get("bridge_pubkey"));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> parsePubkeyField(JsonElement element) {
        if(element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        String value = element.getAsString();
        if(value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(normalizeHex(value));
    }

    public static String normalizeHex(String hex) {
        String normalized = hex.trim().toLowerCase(Locale.ROOT);
        if(normalized.startsWith("0x")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }
}
