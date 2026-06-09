package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;

import java.nio.charset.StandardCharsets;

public final class StrataBridgeConstants {
    public static final byte[] MAGIC_BYTES = "ALPN".getBytes(StandardCharsets.US_ASCII);
    public static final int BRIDGE_V1_SUBPROTOCOL_ID = 2;
    public static final int DEPOSIT_REQUEST_TX_TYPE = 0;
    public static final int DEPOSIT_TX_TYPE = 1;
    public static final int RECOVER_DELAY = 1008;
    public static final int MAX_DRT_DESTINATION_BYTES = 42;
    public static final long MAX_DEPOSIT_SATS = 100L * 100_000_000L;

    private static final String TESTNET_STRATA_RPC_URL = "https://rpc.testnet.alpenlabs.io";
    private static final String TESTNET_BRIDGE_OPERATOR_PUBKEY_HEX = "89f96f834e39766f97e245d70b27236681f741ae51c117df19761af7cb2f657e";

    private StrataBridgeConstants() {
    }

    public static String getStrataRpcUrl(Network network) {
        if(network == Network.MAINNET) {
            return null;
        }
        return TESTNET_STRATA_RPC_URL;
    }

    public static byte[] getBridgeOperatorPubkey(Network network) {
        if(network == Network.MAINNET) {
            throw new IllegalStateException("Strata bridge operator public key is not configured for mainnet");
        }
        return Utils.hexToBytes(TESTNET_BRIDGE_OPERATOR_PUBKEY_HEX);
    }
}
