package com.sparrowwallet.sparrow.strata;

import com.sparrowwallet.drongo.Network;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrataNetworkTest {
    @Test
    void bitcoinTestnetIsNotAlpenTestnetNetwork() {
        assertFalse(StrataNetwork.isAlpenTestnetNetwork(Network.TESTNET));
        assertNull(StrataNetwork.getStrataRpcUrl(Network.TESTNET));
    }

    @Test
    void signetUsesAlpenTestnetChainEndpoints() {
        assertTrue(StrataNetwork.isAlpenTestnetNetwork(Network.SIGNET));
        assertEquals("https://alpen.testnet.alpen.org", StrataNetwork.getStrataRpcUrl(Network.SIGNET));
        assertEquals("https://explorer.testnet.alpen.org", StrataNetwork.getAlpenExplorerUrl(Network.SIGNET));
    }

    @Test
    void signetHasBridgeKeyVerificationUrlDistinctFromChainRpc() {
        String bridgeKeyVerificationUrl = StrataNetwork.getBridgeKeyVerificationUrl(Network.SIGNET);
        assertNotEquals(StrataNetwork.getStrataRpcUrl(Network.SIGNET), bridgeKeyVerificationUrl,
                "Bridge key verification must not silently reuse the Alpen testnet chain RPC, which does not expose strata_ methods");
    }
}
