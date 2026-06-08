package com.sparrowwallet.sparrow.strata.model;

import com.sparrowwallet.drongo.Network;

public final class AlpenConstants {
    public static final int ALPEN_TESTNET_CHAIN_ID = 8150;

    /**
     * Placeholder until Alpen mainnet chain ID is published. ERC-7930 addresses with an explicit
     * chain reference on bitcoin mainnet must match this value once finalized.
     */
    public static final int ALPEN_MAINNET_CHAIN_ID = 0;

    public static final int ALPEN_EE_ACCT_SERIAL = 128;

    public static final int MAX_SUBJECT_BYTES = 32;

    public static final int ERC7930_VERSION = 0x0001;

    public static final int ERC7930_EVM_CHAIN_TYPE = 0x0000;

    public static final int EVM_ADDRESS_BYTES = 20;

    public static final String INVALID_ALPEN_ADDRESS_MESSAGE = "Destination must be an Alpen address.";

    private AlpenConstants() {
    }

    public static int expectedAlpenChainId(Network bitcoinNetwork) {
        return Network.MAINNET.equals(bitcoinNetwork) ? ALPEN_MAINNET_CHAIN_ID : ALPEN_TESTNET_CHAIN_ID;
    }
}
