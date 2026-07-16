package com.sparrowwallet.sparrow.strata;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.strata.model.AlpenAddressParser;

public final class StrataNetwork {
    //  TODO: Update these before release.
    public static final int ALPEN_TESTNET_CHAIN_ID = 8150;
    public static final int ALPEN_MAINNET_CHAIN_ID = 0;

    // TODO: Update these before release.
    public static final String MAINNET_STRATA_RPC_URL = "https://rpc.alpenlabs.io";
    public static final String TESTNET_STRATA_RPC_URL = "https://rpc.testnet.alpenlabs.io";
    public static final String MAINNET_EXPLORER_URL = "https://explorer.alpen.org";
    public static final String TESTNET_EXPLORER_URL = "https://explorer.testnet.alpen.org";

    // TODO: Update these before release.
    public static final String MAINNET_BRIDGE_STATUS_URL = "https://status.alpenlabs.io/bridge";
    public static final String TESTNET_BRIDGE_STATUS_URL = "https://status.testnet.alpenlabs.io/bridge";
    public static final String MAINNET_BRIDGE_WITHDRAWAL_URL = "https://TODO";
    public static final String TESTNET_BRIDGE_WITHDRAWAL_URL = "https://TODO";

    // Bridge key verification URL is intentionally hardcoded in code (not config/params).
    // TODO: Update this before release.
    private static final String BRIDGE_KEY_VERIFICATION_URL = "http://127.0.0.1:8765";

    private StrataNetwork() {
    }

    // Alpen testnet uses Bitcoin public signet as its L1 counterpart.
    public static boolean isAlpenTestnetNetwork(Network network) {
        return network == Network.SIGNET;
    }

    public static int expectedAlpenChainId(Network bitcoinNetwork) {
        if(Network.MAINNET.equals(bitcoinNetwork)) {
            return ALPEN_MAINNET_CHAIN_ID;
        }
        if(Network.SIGNET.equals(bitcoinNetwork)) {
            return ALPEN_TESTNET_CHAIN_ID;
        }
        throw new IllegalArgumentException(AlpenAddressParser.INVALID_ALPEN_ADDRESS_MESSAGE);
    }

    public static String getStrataRpcUrl(Network network) {
        if(network == Network.MAINNET) {
            return MAINNET_STRATA_RPC_URL;
        }
        if(isAlpenTestnetNetwork(network)) {
            return TESTNET_STRATA_RPC_URL;
        }
        return null;
    }

    public static String getAlpenExplorerUrl(Network network) {
        if(isAlpenTestnetNetwork(network)) {
            return TESTNET_EXPLORER_URL;
        }
        return MAINNET_EXPLORER_URL;
    }

    public static String getBridgeKeyVerificationUrl(Network network) {
        if(network == Network.MAINNET || isAlpenTestnetNetwork(network)) {
            return BRIDGE_KEY_VERIFICATION_URL;
        }
        return null;
    }

    public static String getBridgeStatusUrl(Network network) {
        if(network == Network.MAINNET) {
            return MAINNET_BRIDGE_STATUS_URL;
        }
        if(isAlpenTestnetNetwork(network)) {
            return TESTNET_BRIDGE_STATUS_URL;
        }
        return null;
    }

    public static String getBridgeWithdrawalUrl(Network network) {
        if(network == Network.MAINNET) {
            return MAINNET_BRIDGE_WITHDRAWAL_URL;
        }
        if(isAlpenTestnetNetwork(network)) {
            return TESTNET_BRIDGE_WITHDRAWAL_URL;
        }
        return null;
    }
}
