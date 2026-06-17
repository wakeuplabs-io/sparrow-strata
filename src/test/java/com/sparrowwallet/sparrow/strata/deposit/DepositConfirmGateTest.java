package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.ScriptOpCodes;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.sparrow.io.Electrum;
import com.sparrowwallet.sparrow.io.ImportException;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import com.sparrowwallet.sparrow.strata.model.Eip55Address;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeKeyVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepositConfirmGateTest {
    private static final String BRIDGE_PRECOMPILE = "5400000000000000000000000000000000000001";
    private static final double MIN_RELAY = 1.0;
    private WalletTransaction samplePreview;

    @BeforeEach
    void setUp() throws Exception {
        Network.set(Network.MAINNET);
        StrataBridgeKeyVerificationService.getInstance().setChecksDisabled(true);
        samplePreview = createSamplePreview();
    }

    @Test
    void confirmEnabledWhenAllConditionsMet() {
        assertTrue(DepositConfirmGate.isConfirmEnabled(validConfirmState(samplePreview, false)));
    }

    @Test
    void confirmDisabledWhenValidationInvalid() {
        assertFalse(DepositConfirmGate.isConfirmEnabled(new DepositConfirmGate.DepositConfirmState(
                true, true, true, true, true, samplePreview, MIN_RELAY, false)));
    }

    @Test
    void confirmDisabledWhenAddressMissing() {
        assertFalse(DepositConfirmGate.isConfirmEnabled(new DepositConfirmGate.DepositConfirmState(
                false, false, true, true, true, samplePreview, MIN_RELAY, false)));
    }

    @Test
    void confirmDisabledWhenLabelMissing() {
        assertFalse(DepositConfirmGate.isConfirmEnabled(new DepositConfirmGate.DepositConfirmState(
                false, true, false, true, true, samplePreview, MIN_RELAY, false)));
    }

    @Test
    void confirmDisabledWhenAmountMissing() {
        assertFalse(DepositConfirmGate.isConfirmEnabled(new DepositConfirmGate.DepositConfirmState(
                false, true, true, false, true, samplePreview, MIN_RELAY, false)));
    }

    @Test
    void confirmDisabledWhenDepositNotAllowed() {
        assertFalse(DepositConfirmGate.isConfirmEnabled(new DepositConfirmGate.DepositConfirmState(
                false, true, true, true, false, samplePreview, MIN_RELAY, false)));
    }

    @Test
    void confirmDisabledWhenPreviewMissing() {
        assertFalse(DepositConfirmGate.isConfirmEnabled(validConfirmState(null, false)));
    }

    @Test
    void confirmDisabledWhenInsufficientFeeRate() {
        assertTrue(DepositConfirmGate.isInsufficientFeeRate(samplePreview, 100.0));
        assertFalse(DepositConfirmGate.isConfirmEnabled(new DepositConfirmGate.DepositConfirmState(
                false, true, true, true, true, samplePreview, 100.0, false)));
    }

    @Test
    void confirmDisabledWhenFeeServiceRunning() {
        assertFalse(DepositConfirmGate.isConfirmEnabled(validConfirmState(samplePreview, true)));
    }

    @Test
    void diagramHiddenWhenInsufficientInputs() {
        DepositConfirmGate.DepositDiagramState base = validDiagramState(samplePreview);
        assertFalse(DepositConfirmGate.canDisplayDiagram(new DepositConfirmGate.DepositDiagramState(
                true, base.addressValid(), base.labelPresent(), base.descriptor(), base.amountSats(), base.sliderFeeRate(),
                base.userFeeSet(), base.miningFeeFromTotal(), base.preview(), base.previewAmountSats()), MIN_RELAY));
    }

    @Test
    void diagramHiddenWhenPreviewAmountMismatch() {
        DepositConfirmGate.DepositDiagramState base = validDiagramState(samplePreview);
        assertFalse(DepositConfirmGate.canDisplayDiagram(new DepositConfirmGate.DepositDiagramState(
                base.insufficientInputs(), base.addressValid(), base.labelPresent(), base.descriptor(), base.amountSats(), base.sliderFeeRate(),
                base.userFeeSet(), base.miningFeeFromTotal(), base.preview(), 999L), MIN_RELAY));
    }

    @Test
    void diagramHiddenWhenCustomFeeDoesNotCoverDepFee() {
        DepositConfirmGate.DepositDiagramState base = validDiagramState(samplePreview);
        assertFalse(DepositConfirmGate.canDisplayDiagram(new DepositConfirmGate.DepositDiagramState(
                base.insufficientInputs(), base.addressValid(), base.labelPresent(), base.descriptor(), base.amountSats(), base.sliderFeeRate(),
                true, null, base.preview(), base.previewAmountSats()), MIN_RELAY));
    }

    @Test
    void diagramShownWhenValid() {
        assertTrue(DepositConfirmGate.canDisplayDiagram(validDiagramState(samplePreview), MIN_RELAY));
    }

    private static DepositConfirmGate.DepositConfirmState validConfirmState(WalletTransaction preview, boolean feeServiceRunning) {
        return new DepositConfirmGate.DepositConfirmState(false, true, true, true, true, preview, MIN_RELAY, feeServiceRunning);
    }

    private DepositConfirmGate.DepositDiagramState validDiagramState(WalletTransaction preview) {
        try {
            DepositDescriptor descriptor = DepositDescriptor.forAlpenDeposit(Eip55Address.parse("0x" + BRIDGE_PRECOMPILE));
            return new DepositConfirmGate.DepositDiagramState(
                    false, true, true, descriptor, StrataBridgeConstants.DEPOSIT_UTXO_AMOUNT_SATS, 2.0,
                    false, null, preview, StrataBridgeConstants.DEPOSIT_UTXO_AMOUNT_SATS);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static WalletTransaction createSamplePreview() throws Exception {
        Wallet wallet = createWalletWithTwoUtxos();
        DepositDescriptor descriptor = DepositDescriptor.forAlpenDeposit(Eip55Address.parse("0x" + BRIDGE_PRECOMPILE));
        DepositRequestService service = new DepositRequestService(
                wallet, descriptor, StrataBridgeConstants.DEPOSIT_UTXO_AMOUNT_SATS, "deposit",
                2.0, 2.0, 1.0, 1.0, null, 100, false, false);
        return service.createWalletTransaction().walletTransaction();
    }

    private static Wallet createWalletWithTwoUtxos() throws ImportException {
        Electrum electrum = new Electrum();
        InputStream is = DepositConfirmGateTest.class.getResourceAsStream("/com/sparrowwallet/sparrow/io/electrum-singlesig-wallet.json");
        Wallet wallet = electrum.importWallet(is, null);
        Date date = new Date();
        WalletNode addr0 = wallet.getNode(KeyPurpose.RECEIVE).getChildren().stream().sorted().toList().get(0);
        WalletNode addr1 = wallet.getNode(KeyPurpose.RECEIVE).getChildren().stream().sorted().toList().get(1);
        Transaction tx1 = new Transaction();
        tx1.addOutput(2_000_000_000L, addr0.getOutputScript());
        Transaction tx2 = new Transaction();
        tx2.addOutput(2_000_000_000L, addr1.getOutputScript());
        addUtxo(wallet, addr0, tx1.getTxId(), 0, 2_000_000_000L, date, tx1);
        addUtxo(wallet, addr1, tx2.getTxId(), 0, 2_000_000_000L, date, tx2);
        wallet.setStoredBlockHeight(100);
        return wallet;
    }

    private static void addUtxo(Wallet wallet, WalletNode node, Sha256Hash hash, int index, long value, Date date, Transaction transaction) {
        BlockTransaction blockTransaction = new BlockTransaction(hash, 100, date, null, transaction);
        wallet.updateTransactions(Map.of(hash, blockTransaction));
        Set<BlockTransactionHashIndex> txos = new TreeSet<>();
        txos.add(new BlockTransactionHashIndex(hash, 100, date, null, index, value));
        node.updateTransactionOutputs(wallet, txos);
    }
}
