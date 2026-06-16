package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.P2TRAddress;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.sparrow.strata.model.AlpenConstants;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import com.sparrowwallet.sparrow.strata.model.Eip55Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden vectors for DRT output 0 (SPS-50 OP_RETURN) and output 1 (bridge-in P2TR).
 *
 * <p>Expected values were captured from {@code strata-test-cli compute-drt-output} in the Alpen
 * reference repo and are embedded here so tests do not invoke any external tooling. Inputs used
 * when capturing:</p>
 * <ul>
 *   <li>Operator key: test tprv from {@code strata-test-cli} constants (single-operator MuSig2 aggregate)</li>
 *   <li>Recovery x-only pubkey: {@link #RECOVERY_PK_HEX}</li>
 *   <li>Alpen EE address: {@code 0x5400…0001}, account serial {@link AlpenConstants#ALPEN_EE_ACCT_SERIAL}</li>
 *   <li>Bitcoin network: mainnet (bech32m {@code bc1p…} prefix)</li>
 * </ul>
 */
class DepositRequestOutputGoldenTest {
  /**
   * MuSig2-aggregated bridge operator internal key for the test operator tprv above.
   */
  private static final String BRIDGE_INTERNAL_KEY_HEX =
      "bd67cd6f1d488245ad3dca07474e0a11ac43c01cc9b90a0b3794dd25ed2ac77e";

  private static final String RECOVERY_PK_HEX =
      "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";

  private static final String ALPEN_EE_ADDRESS = "0x5400000000000000000000000000000000000001";

  private static final String EXPECTED_BRIDGE_IN_SCRIPT_HEX =
      "5120801aa9f78e7658598e5f8bff9ec28fd63d9c21a0d7e0b736e8874257a1fb1943";

  private static final String EXPECTED_OP_RETURN_SCRIPT_HEX =
      "6a3c414c504e0200aabbccddeeff00112233445566778899aabbccddeeff0011223344556677889900805400000000000000000000000000000000000001";

  private static final String EXPECTED_BRIDGE_IN_ADDRESS_MAINNET =
      "bc1psqd2nauwwev9nrjl30leas506c7ecgdq6lstwdhgsap90g0mr9pskl5uss";

  @BeforeEach
  void setUp() {
    Network.set(Network.MAINNET);
  }

  @AfterEach
  void tearDown() {
    Network.set(Network.MAINNET);
  }

  @Test
  void depositRequestOutputsMatchReferenceVectors() {
    byte[] recoveryPk = Utils.hexToBytes(RECOVERY_PK_HEX);
    byte[] bridgeInternalKey = Utils.hexToBytes(BRIDGE_INTERNAL_KEY_HEX);
    DepositDescriptor descriptor = DepositDescriptor.forAlpenDeposit(Eip55Address.parse(ALPEN_EE_ADDRESS));

    DrtHeaderAux headerAux = DrtHeaderAux.create(recoveryPk, descriptor.encodeToBytes());
    Script opReturnScript = Sps50Encoder.encodeOpReturnScript(headerAux.buildAuxData());
    assertEquals(
        EXPECTED_OP_RETURN_SCRIPT_HEX,
        Utils.bytesToHex(opReturnScript.getProgram()),
        "OP_RETURN script must match strata-test-cli compute-drt-output");

    Script bridgeInScript =
        DepositRequestLockingScript.createLockingScript(
            recoveryPk, bridgeInternalKey, StrataBridgeConstants.RECOVER_DELAY);
    assertEquals(
        EXPECTED_BRIDGE_IN_SCRIPT_HEX,
        Utils.bytesToHex(bridgeInScript.getProgram()),
        "Bridge-in locking script must match strata-test-cli compute-drt-output");

    P2TRAddress bridgeInAddress =
        DepositRequestLockingScript.createBridgeInAddress(
            recoveryPk, bridgeInternalKey, StrataBridgeConstants.RECOVER_DELAY);
    assertEquals(
        EXPECTED_BRIDGE_IN_ADDRESS_MAINNET,
        bridgeInAddress.getAddress(Network.MAINNET),
        "Bridge-in address must match strata-test-cli compute-drt-output");
  }
}
