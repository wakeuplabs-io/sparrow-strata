package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.P2TRAddress;
import com.sparrowwallet.drongo.protocol.Script;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DepositRequestLockingScriptTest {
    private static final String RECOVERY_PK_HEX =
            "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";
    private static final String BRIDGE_INTERNAL_KEY_HEX =
            "bd67cd6f1d488245ad3dca07474e0a11ac43c01cc9b90a0b3794dd25ed2ac77e";

    @BeforeEach
    void setUp() {
        Network.set(Network.MAINNET);
    }

    @AfterEach
    void tearDown() {
        Network.set(Network.MAINNET);
    }

    @Test
    void bridgeInScriptAndAddressDependOnRecoveryDelay() {
        byte[] recoveryPk = Utils.hexToBytes(RECOVERY_PK_HEX);
        byte[] bridgeInternalKey = Utils.hexToBytes(BRIDGE_INTERNAL_KEY_HEX);

        Script defaultDelayScript = DepositRequestLockingScript.createLockingScript(
                recoveryPk, bridgeInternalKey, StrataBridgeConstants.RECOVER_DELAY);
        Script customDelayScript = DepositRequestLockingScript.createLockingScript(recoveryPk, bridgeInternalKey, 504);

        P2TRAddress defaultDelayAddress = DepositRequestLockingScript.createBridgeInAddress(
                recoveryPk, bridgeInternalKey, StrataBridgeConstants.RECOVER_DELAY);
        P2TRAddress customDelayAddress = DepositRequestLockingScript.createBridgeInAddress(
                recoveryPk, bridgeInternalKey, 504);

        assertNotEquals(
                Utils.bytesToHex(defaultDelayScript.getProgram()),
                Utils.bytesToHex(customDelayScript.getProgram()));
        assertNotEquals(
                defaultDelayAddress.getAddress(Network.MAINNET),
                customDelayAddress.getAddress(Network.MAINNET));
    }
}
