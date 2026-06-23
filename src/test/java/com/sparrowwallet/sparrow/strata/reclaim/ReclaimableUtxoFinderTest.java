package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.P2TRAddress;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.protocol.TransactionWitness;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.io.Electrum;
import com.sparrowwallet.sparrow.strata.deposit.DepositRequestLockingScript;
import com.sparrowwallet.sparrow.strata.deposit.DrtHeaderAux;
import com.sparrowwallet.sparrow.strata.deposit.RecoveryKeyPair;
import com.sparrowwallet.sparrow.strata.deposit.Sps50Encoder;
import com.sparrowwallet.sparrow.strata.deposit.StrataBridgeConstants;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import com.sparrowwallet.sparrow.strata.model.Eip55Address;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeKeyVerificationService;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeParametersService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ReclaimableUtxoFinderTest {
    private static final String BRIDGE_PRECOMPILE = "5400000000000000000000000000000000000001";

    @BeforeEach
    void setUp() {
        Network.set(Network.MAINNET);
        StrataBridgeKeyVerificationService.getInstance().setChecksDisabled(true);
    }

    @AfterEach
    void tearDown() {
        StrataBridgeKeyVerificationService.getInstance().setChecksDisabled(false);
        Network.set(Network.MAINNET);
    }

    @Test
    void findsUnspentBridgeInOutputFromWalletHistory() throws Exception {
        Wallet wallet = loadWallet();
        RecoveryKeyPair recoveryKeyPair = RecoveryKeyPair.generate();
        byte[] bridgeOperatorPubkey = StrataBridgeConstants.getBridgeOperatorPubkey(Network.MAINNET);
        int recoveryDelay = StrataBridgeParametersService.getInstance().getRecoveryDelay();

        DepositDescriptor descriptor = DepositDescriptor.forAlpenDeposit(Eip55Address.parse("0x" + BRIDGE_PRECOMPILE));
        DrtHeaderAux headerAux = DrtHeaderAux.create(recoveryKeyPair.getXOnlyPublicKey(), descriptor.encodeToBytes());
        Script opReturnScript = Sps50Encoder.encodeOpReturnScript(headerAux.buildAuxData(), StrataBridgeConstants.MAGIC_BYTES);
        P2TRAddress bridgeInAddress = DepositRequestLockingScript.createBridgeInAddress(
                recoveryKeyPair.getXOnlyPublicKey(), bridgeOperatorPubkey, recoveryDelay);

        Transaction depositTx = new Transaction();
        depositTx.addOutput(0L, opReturnScript);
        depositTx.addOutput(1_000_000_000L, bridgeInAddress.getOutputScript());
        Sha256Hash depositHash = depositTx.getTxId();

        BlockTransaction blockTransaction = new BlockTransaction(depositHash, 100, new Date(), null, depositTx);
        wallet.updateTransactions(Map.of(depositHash, blockTransaction));

        List<ReclaimEntry> reclaimable = ReclaimableUtxoFinder.findReclaimableUtxos(wallet);
        assertFalse(reclaimable.isEmpty());
        assertEquals(depositHash, reclaimable.get(0).getHashIndex().getHash());
        assertEquals(1L, reclaimable.get(0).getHashIndex().getIndex());
        assertEquals(1_000_000_000L, reclaimable.get(0).getValue());
    }

    private static Wallet loadWallet() throws Exception {
        Electrum electrum = new Electrum();
        InputStream is = ReclaimableUtxoFinderTest.class.getResourceAsStream("/com/sparrowwallet/sparrow/io/electrum-singlesig-wallet.json");
        Wallet wallet = electrum.importWallet(is, null);
        WalletNode receiveNode = wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next();
        Transaction fundTx = new Transaction();
        fundTx.addOutput(2_000_000_000L, receiveNode.getOutputScript());
        wallet.updateTransactions(Map.of(fundTx.getTxId(), new BlockTransaction(fundTx.getTxId(), 1, new Date(), null, fundTx)));
        return wallet;
    }
}
