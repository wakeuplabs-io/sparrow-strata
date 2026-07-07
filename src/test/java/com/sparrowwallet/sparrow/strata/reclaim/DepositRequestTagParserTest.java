package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.sparrow.strata.deposit.DrtHeaderAux;
import com.sparrowwallet.sparrow.strata.deposit.Sps50Encoder;
import com.sparrowwallet.sparrow.strata.deposit.StrataBridgeConstants;
import com.sparrowwallet.sparrow.strata.model.AlpenAddress;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import com.sparrowwallet.sparrow.strata.model.Eip55Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepositRequestTagParserTest {
    private static final String RECOVERY_PK_HEX =
            "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";

    @BeforeEach
    void setUp() {
        Network.set(Network.MAINNET);
    }

    private static final String DESTINATION_HEX = "0x5400000000000000000000000000000000000001";

    @Test
    void parsesModernDepositRequestTag() {
        byte[] recoveryPk = Utils.hexToBytes(RECOVERY_PK_HEX);
        DepositDescriptor descriptor = DepositDescriptor.forAlpenDeposit(Eip55Address.parse(DESTINATION_HEX));
        DrtHeaderAux headerAux = DrtHeaderAux.create(recoveryPk, descriptor.encodeToBytes());
        Script opReturnScript = Sps50Encoder.encodeOpReturnScript(headerAux.buildAuxData());

        Transaction transaction = new Transaction();
        transaction.addOutput(0L, opReturnScript);

        Optional<byte[]> parsed = DepositRequestTagParser.parseRecoveryPk(transaction, StrataBridgeConstants.MAGIC_BYTES);
        assertTrue(parsed.isPresent());
        assertArrayEquals(recoveryPk, parsed.get());

        Optional<AlpenAddress> destination = DepositRequestTagParser.parseDestinationAddress(transaction, StrataBridgeConstants.MAGIC_BYTES);
        assertTrue(destination.isPresent());
        assertEquals(Eip55Address.parse(DESTINATION_HEX), destination.get());
    }

    @Test
    void parsesLegacyDepositRequestTag() {
        byte[] recoveryPk = Utils.hexToBytes(RECOVERY_PK_HEX);
        DepositDescriptor descriptor = DepositDescriptor.forAlpenDeposit(Eip55Address.parse(DESTINATION_HEX));
        DrtHeaderAux headerAux = DrtHeaderAux.create(recoveryPk, descriptor.getDestSubject());
        Script opReturnScript = Sps50Encoder.encodeOpReturnScript(headerAux.buildAuxData(), StrataBridgeConstants.TESTNET_MAGIC_BYTES);

        Transaction transaction = new Transaction();
        transaction.addOutput(0L, opReturnScript);

        Optional<byte[]> parsed = DepositRequestTagParser.parseRecoveryPk(transaction, StrataBridgeConstants.TESTNET_MAGIC_BYTES);
        assertTrue(parsed.isPresent());
        assertArrayEquals(recoveryPk, parsed.get());

        Optional<AlpenAddress> destination = DepositRequestTagParser.parseDestinationAddress(transaction, StrataBridgeConstants.TESTNET_MAGIC_BYTES);
        assertTrue(destination.isPresent());
        assertEquals(Eip55Address.parse(DESTINATION_HEX), destination.get());
    }

    @Test
    void ignoresNonDepositTransactions() {
        Transaction transaction = new Transaction();
        transaction.addOutput(1_000L, ScriptType.P2WPKH.getOutputScript(new byte[20]));

        Optional<byte[]> parsed = DepositRequestTagParser.parseRecoveryPk(transaction, StrataBridgeConstants.MAGIC_BYTES);
        assertTrue(parsed.isEmpty());

        Optional<AlpenAddress> destination = DepositRequestTagParser.parseDestinationAddress(transaction, StrataBridgeConstants.MAGIC_BYTES);
        assertTrue(destination.isEmpty());
    }
}
