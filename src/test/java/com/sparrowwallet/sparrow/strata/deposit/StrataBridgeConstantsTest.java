package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.Network;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class StrataBridgeConstantsTest {
    @Test
    void testnetMagicBytesFallbackIsLowercaseAlpn() {
        assertArrayEquals("alpn".getBytes(StandardCharsets.US_ASCII), StrataBridgeConstants.getMagicBytesFallback(Network.TESTNET));
    }

    @Test
    void mainnetMagicBytesFallbackIsUppercaseAlpn() {
        assertArrayEquals("ALPN".getBytes(StandardCharsets.US_ASCII), StrataBridgeConstants.getMagicBytesFallback(Network.MAINNET));
    }
}
