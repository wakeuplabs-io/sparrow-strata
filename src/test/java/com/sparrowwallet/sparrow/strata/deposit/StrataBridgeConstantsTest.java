package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.Network;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrataBridgeConstantsTest {
    @Test
    void signetMagicBytesFallbackIsLowercaseAlpn() {
        assertArrayEquals("alpn".getBytes(StandardCharsets.US_ASCII), StrataBridgeConstants.getMagicBytesFallback(Network.SIGNET));
    }

    @Test
    void mainnetMagicBytesFallbackIsUppercaseAlpn() {
        assertArrayEquals("ALPN".getBytes(StandardCharsets.US_ASCII), StrataBridgeConstants.getMagicBytesFallback(Network.MAINNET));
    }

    @Test
    void bitcoinTestnetIsNotAlpenTestnetNetwork() {
        assertFalse(StrataBridgeConstants.isAlpenTestnetNetwork(Network.TESTNET));
        assertNull(StrataBridgeConstants.getStrataRpcUrl(Network.TESTNET));
    }

    @Test
    void signetUsesAlpenTestnetChainEndpoints() {
        assertTrue(StrataBridgeConstants.isAlpenTestnetNetwork(Network.SIGNET));
        assertEquals("https://alpen.testnet.alpen.org", StrataBridgeConstants.getStrataRpcUrl(Network.SIGNET));
        assertEquals("https://explorer.testnet.alpen.org", StrataBridgeConstants.getAlpenExplorerUrl(Network.SIGNET));
    }

    @Test
    void signetHasBridgeKeyVerificationUrlDistinctFromChainRpc() {
        String bridgeKeyVerificationUrl = StrataBridgeConstants.getBridgeKeyVerificationUrl(Network.SIGNET);
        assertNotEquals(StrataBridgeConstants.getStrataRpcUrl(Network.SIGNET), bridgeKeyVerificationUrl,
                "Bridge key verification must not silently reuse the Alpen testnet chain RPC, which does not expose strata_ methods");
    }
}
