package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;

import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

public final class StrataBridgeConstants {
    public static final byte[] MAGIC_BYTES = "ALPN".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] TESTNET_MAGIC_BYTES = "alpn".getBytes(StandardCharsets.US_ASCII);
    public static final int BRIDGE_V1_SUBPROTOCOL_ID = 2;
    public static final int DEPOSIT_REQUEST_TX_TYPE = 0;
    public static final int DEPOSIT_TX_TYPE = 1;
    public static final int RECOVER_DELAY = 1008;
    public static final int MAX_DRT_DESTINATION_BYTES = 42;
    public static final long MAX_DEPOSIT_SATS = 100L * 100_000_000L;
    public static final long DEPOSIT_UTXO_AMOUNT_SATS = 1_000_000_000L;

    // TODO: Update these before release.
    public static final String MAINNET_STRATA_RPC_URL = "https://rpc.alpenlabs.io";
    public static final String TESTNET_STRATA_RPC_URL = "https://rpc.testnet.alpenlabs.io";

    // TODO: Update these before release.
    public static final String MAINNET_BRIDGE_STATUS_URL = "https://status.alpenlabs.io/bridge";
    public static final String TESTNET_BRIDGE_STATUS_URL = "https://status.testnet.alpenlabs.io/bridge";
    public static final String MAINNET_BRIDGE_WITHDRAWAL_URL = "https://TODO";
    public static final String TESTNET_BRIDGE_WITHDRAWAL_URL = "https://TODO";

    public static final String BRIDGE_KEY_MISMATCH_MESSAGE = "Bridge key mismatch. Please update Sparrow (Strata Edition) and try again.";
    public static final String BRIDGE_KEY_UNAVAILABLE_MESSAGE = "Bridge key unavailable. Please try again later.";

    // Bridge key verification URL is intentionally hardcoded in code (not config/params).
    // TODO: Update this before release.
    private static final String BRIDGE_KEY_VERIFICATION_URL = "http://127.0.0.1:8765";
    private static final String TESTNET_BRIDGE_OPERATOR_PUBKEY_HEX = "50eaad3a98150e584555f1e4a479be2d8ccd8927a4ec3df075d3c65161f47295";
    private static final String MAINNET_BRIDGE_OPERATOR_PUBKEY_HEX = "50eaad3a98150e584555f1e4a479be2d8ccd8927a4ec3df075d3c65161f47295";

    private StrataBridgeConstants() {
    }

    public static OptionalLong getDepositUtxoAmountSats(Network network) {
        if(network == Network.MAINNET || network == Network.TESTNET || network == Network.SIGNET) {
            return OptionalLong.of(DEPOSIT_UTXO_AMOUNT_SATS);
        }
        return OptionalLong.empty();
    }

    public static byte[] getMagicBytesFallback(Network network) {
        if(network == Network.TESTNET || network == Network.SIGNET) {
            return TESTNET_MAGIC_BYTES;
        }
        return MAGIC_BYTES;
    }

    public static String getStrataRpcUrl(Network network) {
        if(network == Network.MAINNET) {
            return MAINNET_STRATA_RPC_URL;
        }
        if(network == Network.TESTNET) {
            return TESTNET_STRATA_RPC_URL;
        }
        return null;
    }

    public static String getBridgeKeyVerificationUrl(Network network) {
        if(network == Network.MAINNET || network == Network.TESTNET || network == Network.SIGNET) {
            return BRIDGE_KEY_VERIFICATION_URL;
        }
        return null;
    }

    public static String getBridgeStatusUrl(Network network) {
        if(network == Network.MAINNET) {
            return MAINNET_BRIDGE_STATUS_URL;
        }
        if(network == Network.TESTNET || network == Network.SIGNET) {
            return TESTNET_BRIDGE_STATUS_URL;
        }
        return null;
    }

    public static String getBridgeWithdrawalUrl(Network network) {
        if(network == Network.MAINNET) {
            return MAINNET_BRIDGE_WITHDRAWAL_URL;
        }
        if(network == Network.TESTNET || network == Network.SIGNET) {
            return TESTNET_BRIDGE_WITHDRAWAL_URL;
        }
        return null;
    }

    public static String getBridgeOperatorPubkeyHex(Network network) {
        if(network == Network.MAINNET) {
            return MAINNET_BRIDGE_OPERATOR_PUBKEY_HEX;
        }
        if(network == Network.TESTNET || network == Network.SIGNET) {
            return TESTNET_BRIDGE_OPERATOR_PUBKEY_HEX;
        }
        return null;
    }

    public static byte[] getBridgeOperatorPubkey(Network network) {
        String hex = getBridgeOperatorPubkeyHex(network);
        if(hex == null) {
            throw new IllegalStateException("Strata bridge operator public key is not configured for " + network);
        }
        return Utils.hexToBytes(hex);
    }
}
