package com.sparrowwallet.sparrow.strata.protocol;

import com.sparrowwallet.drongo.Network;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class StrataBridgeProtocolTest {
    @Test
    void signetMagicBytesFallbackIsLowercaseAlpn() {
        assertArrayEquals("alpn".getBytes(StandardCharsets.US_ASCII), StrataBridgeProtocol.getMagicBytesFallback(Network.SIGNET));
    }

    @Test
    void mainnetMagicBytesFallbackIsUppercaseAlpn() {
        assertArrayEquals("ALPN".getBytes(StandardCharsets.US_ASCII), StrataBridgeProtocol.getMagicBytesFallback(Network.MAINNET));
    }
}
