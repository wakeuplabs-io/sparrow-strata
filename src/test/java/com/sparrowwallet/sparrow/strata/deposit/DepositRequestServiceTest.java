package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.ScriptOpCodes;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.PresetUtxoSelector;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.io.Electrum;
import com.sparrowwallet.sparrow.io.ImportException;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import com.sparrowwallet.sparrow.strata.model.Eip55Address;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeKeyVerificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepositRequestServiceTest {
    private static final String BRIDGE_PRECOMPILE = "5400000000000000000000000000000000000001";
    private static final long UTXO_VALUE = 2_000_000_000L;
    private static final long DEPOSIT_AMOUNT = StrataBridgeConstants.DEPOSIT_UTXO_AMOUNT_SATS;

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
    void honorsPresetUtxoSelector() throws Exception {
        Wallet wallet = createWalletWithTwoUtxos();
        BlockTransactionHashIndex utxo1 = wallet.getSpendableUtxos().keySet().stream().findFirst().orElseThrow();
        BlockTransactionHashIndex utxo2 = wallet.getSpendableUtxos().keySet().stream().filter(u -> !u.equals(utxo1)).findFirst().orElseThrow();

        DepositDescriptor descriptor = DepositDescriptor.forAlpenDeposit(Eip55Address.parse("0x" + BRIDGE_PRECOMPILE));
        List<BlockTransactionHashIndex> presetUtxos = List.of(utxo1);
        PresetUtxoSelector presetSelector = new PresetUtxoSelector(presetUtxos, false, false);

        DepositRequestService service = new DepositRequestService(
                wallet,
                descriptor,
                DEPOSIT_AMOUNT,
                "deposit",
                2.0,
                2.0,
                1.0,
                1.0,
                null,
                100,
                false,
                false,
                List.of(presetSelector),
                Set.of(),
                null
        );

        WalletTransaction walletTransaction = service.createWalletTransaction().walletTransaction();

        assertTrue(walletTransaction.isCoinControlUsed());
        assertEquals(presetUtxos.size(), walletTransaction.getSelectedUtxos().size());
        assertTrue(walletTransaction.getSelectedUtxos().containsKey(utxo1));
        assertTrue(walletTransaction.getSelectedUtxos().keySet().stream().noneMatch(u -> u.equals(utxo2)));
    }

    @Test
    void bridgeInOutputIncludesDepFee() throws Exception {
        Wallet wallet = createWalletWithTwoUtxos();
        DepositDescriptor descriptor = DepositDescriptor.forAlpenDeposit(Eip55Address.parse("0x" + BRIDGE_PRECOMPILE));
        double feeRate = 2.0;
        long expectedBridgeOutput = DEPOSIT_AMOUNT + DepositTransactionFeeEstimator.calculateDepFee(feeRate);

        DepositRequestService service = new DepositRequestService(
                wallet,
                descriptor,
                DEPOSIT_AMOUNT,
                "deposit",
                feeRate,
                feeRate,
                1.0,
                1.0,
                null,
                100,
                false,
                false
        );

        WalletTransaction walletTransaction = service.createWalletTransaction().walletTransaction();

        WalletTransaction.PaymentOutput bridgeOutput = walletTransaction.getOutputs().stream()
                .filter(WalletTransaction.PaymentOutput.class::isInstance)
                .map(WalletTransaction.PaymentOutput.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(expectedBridgeOutput, bridgeOutput.getTransactionOutput().getValue());

        WalletTransaction.NonAddressOutput opReturnOutput = walletTransaction.getOutputs().stream()
                .filter(WalletTransaction.NonAddressOutput.class::isInstance)
                .map(WalletTransaction.NonAddressOutput.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(0L, opReturnOutput.getTransactionOutput().getValue());
        assertEquals(ScriptOpCodes.OP_RETURN, opReturnOutput.getTransactionOutput().getScript().getChunks().get(0).getOpcode());
    }

    private static Wallet createWalletWithTwoUtxos() throws ImportException {
        Electrum electrum = new Electrum();
        InputStream is = DepositRequestServiceTest.class.getResourceAsStream("/com/sparrowwallet/sparrow/io/electrum-singlesig-wallet.json");
        Wallet wallet = electrum.importWallet(is, null);
        assertTrue(wallet.isValid());

        Date date = new Date();
        WalletNode receiveNode = wallet.getNode(KeyPurpose.RECEIVE);
        List<WalletNode> addresses = receiveNode.getChildren().stream().sorted().toList();
        WalletNode addr0 = addresses.get(0);
        WalletNode addr1 = addresses.get(1);

        Transaction tx1 = new Transaction();
        tx1.addOutput(UTXO_VALUE, addr0.getOutputScript());
        Sha256Hash hash1 = tx1.getTxId();

        Transaction tx2 = new Transaction();
        tx2.addOutput(UTXO_VALUE, addr1.getOutputScript());
        Sha256Hash hash2 = tx2.getTxId();

        addUtxo(wallet, addr0, hash1, 0, UTXO_VALUE, date, tx1);
        addUtxo(wallet, addr1, hash2, 0, UTXO_VALUE, date, tx2);
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
