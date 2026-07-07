package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.P2TRAddress;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.strata.deposit.DepositFeeRates;
import com.sparrowwallet.sparrow.strata.deposit.DepositRequestLockingScript;
import com.sparrowwallet.sparrow.strata.deposit.DrtHeaderAux;
import com.sparrowwallet.sparrow.strata.deposit.RecoveryKeyPair;
import com.sparrowwallet.sparrow.strata.deposit.Sps50Encoder;
import com.sparrowwallet.sparrow.strata.deposit.StrataBridgeConstants;
import com.sparrowwallet.sparrow.strata.deposit.WalletRecoveryKey;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import com.sparrowwallet.sparrow.strata.model.Eip55Address;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeKeyVerificationService;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeParametersService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryDepositTransactionBuilderTest {
    private static final String BRIDGE_PRECOMPILE = "5400000000000000000000000000000000000001";
    private static final long RECLAIMED_UTXO_VALUE = 500_000_000L;
    private static final long WALLET_UTXO_VALUE = 2_000_000_000L;
    private static final long NEW_DEPOSIT_AMOUNT = StrataBridgeConstants.DEPOSIT_UTXO_AMOUNT_SATS;

    private RecoveryKeyPair recoveryKeyPair;
    private byte[] bridgeOperatorPubkey;

    @BeforeEach
    void setUp() {
        Network.set(Network.MAINNET);
        StrataBridgeKeyVerificationService.getInstance().setChecksDisabled(true);
        StrataBridgeParametersService.setParametersForTesting(
                StrataBridgeConstants.MAGIC_BYTES, StrataBridgeConstants.DEPOSIT_UTXO_AMOUNT_SATS, StrataBridgeConstants.RECOVER_DELAY);
        recoveryKeyPair = RecoveryKeyPair.generate();
        bridgeOperatorPubkey = StrataBridgeConstants.getBridgeOperatorPubkey(Network.MAINNET);
    }

    @AfterEach
    void tearDown() {
        StrataBridgeKeyVerificationService.getInstance().setChecksDisabled(false);
        StrataBridgeParametersService.clearParametersForTesting();
        Network.set(Network.MAINNET);
    }

    @Test
    void combinesReclaimedUtxoWithWalletFundingAndSignsMixedPsbt() throws Exception {
        Wallet wallet = createWalletWithSpendableUtxo();
        ReclaimEntry reclaimEntry = createReclaimEntry(wallet);

        DepositDescriptor descriptor = DepositDescriptor.forAlpenDeposit(Eip55Address.parse("0x" + BRIDGE_PRECOMPILE));
        double feeRate = 2.0;
        long depFee = DepositFeeRates.calculateDepFee(feeRate);
        WalletRecoveryKey newDepositRecoveryKey = testRecoveryKey(wallet);

        RetryDepositTransactionBuilder.RetryDepositResult result = RetryDepositTransactionBuilder.createWalletTransaction(
                wallet, List.of(reclaimEntry), descriptor, NEW_DEPOSIT_AMOUNT, "retry deposit",
                feeRate, feeRate, 1.0, 1.0, null, 100, false, false,
                null, Set.of(), null, newDepositRecoveryKey);

        WalletTransaction walletTransaction = result.walletTransaction();

        //One reclaimed input plus at least one wallet-funded input
        assertEquals(2, walletTransaction.getTransaction().getInputs().size());
        assertTrue(walletTransaction.getSelectedUtxos().containsKey(reclaimEntry.getHashIndex()));

        WalletTransaction.PaymentOutput bridgeOutput = walletTransaction.getOutputs().stream()
                .filter(WalletTransaction.PaymentOutput.class::isInstance)
                .map(WalletTransaction.PaymentOutput.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(NEW_DEPOSIT_AMOUNT + depFee, bridgeOutput.getTransactionOutput().getValue());

        long totalIn = walletTransaction.getTransaction().getInputs().size() == 2
                ? RECLAIMED_UTXO_VALUE + WALLET_UTXO_VALUE : 0;
        long totalOut = walletTransaction.getTransaction().getOutputs().stream().mapToLong(o -> o.getValue()).sum();
        assertEquals(totalIn - totalOut, walletTransaction.getFee());
        assertTrue(walletTransaction.getFee() > 0);

        PSBT psbt = RetryDepositTransactionBuilder.createPsbt(wallet, result);
        assertTrue(ReclaimPsbt.isReclaimPsbt(psbt));

        PSBTInput reclaimInput = psbt.getPsbtInputs().get(psbt.getPsbtInputs().size() - 1);
        assertTrue(ReclaimPsbt.hasInputRecoveryPk(reclaimInput));
        for(int i = 0; i < psbt.getPsbtInputs().size() - 1; i++) {
            assertTrue(!ReclaimPsbt.hasInputRecoveryPk(psbt.getPsbtInputs().get(i)));
        }

        //Mixed signing: reclaim tapscript path signer, then normal wallet key-path signing
        ReclaimPsbtSigner.sign(wallet, psbt);
        assertTrue(reclaimInput.isFinalized());
        assertTrue(!psbt.isSigned());

        wallet.sign(wallet.getSigningNodes(psbt));
        assertTrue(psbt.isSigned());

        wallet.finalise(psbt);
        assertTrue(psbt.isFinalized());

        Transaction finalTx = psbt.extractTransaction();
        assertEquals(2, finalTx.getInputs().size());
    }

    private ReclaimEntry createReclaimEntry(Wallet wallet) {
        DepositDescriptor oldDescriptor = DepositDescriptor.forAlpenDeposit(Eip55Address.parse("0x" + BRIDGE_PRECOMPILE));
        DrtHeaderAux headerAux = DrtHeaderAux.create(recoveryKeyPair.getXOnlyPublicKey(), oldDescriptor.encodeToBytes());
        Script opReturnScript = Sps50Encoder.encodeOpReturnScript(headerAux.buildAuxData(), StrataBridgeConstants.MAGIC_BYTES);
        P2TRAddress bridgeInAddress = DepositRequestLockingScript.createBridgeInAddress(
                recoveryKeyPair.getXOnlyPublicKey(), bridgeOperatorPubkey, StrataBridgeConstants.RECOVER_DELAY);

        Transaction depositTx = new Transaction();
        depositTx.addOutput(0L, opReturnScript);
        depositTx.addOutput(RECLAIMED_UTXO_VALUE, bridgeInAddress.getOutputScript());
        Sha256Hash depositHash = depositTx.getTxId();

        BlockTransaction blockTransaction = new BlockTransaction(depositHash, 100, new Date(), null, depositTx);
        wallet.updateTransactions(Map.of(depositHash, blockTransaction));

        BlockTransactionHashIndex hashIndex = new BlockTransactionHashIndex(depositHash, 100, new Date(), null, 1, RECLAIMED_UTXO_VALUE);
        return new ReclaimEntry(wallet, hashIndex, bridgeInAddress, null);
    }

    private Wallet createWalletWithSpendableUtxo() {
        Wallet wallet = new Wallet();
        wallet.setPolicyType(PolicyType.SINGLE_HD);
        wallet.setScriptType(ScriptType.P2TR);

        Keystore keystore = new Keystore() {
            @Override
            public ECKey getKey(WalletNode walletNode) {
                return recoveryKeyPair.getPrivateKey();
            }

            @Override
            public ECKey getPubKey(WalletNode walletNode) {
                return recoveryKeyPair.getPrivateKey();
            }

            @Override
            public boolean hasPrivateKey() {
                return true;
            }
        };
        keystore.setSource(KeystoreSource.SW_SEED);
        keystore.setKeyDerivation(new com.sparrowwallet.drongo.KeyDerivation("00000000", "m/86'/0'/0'"));
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(com.sparrowwallet.drongo.policy.Policy.getPolicy(
                PolicyType.SINGLE_HD, ScriptType.P2TR, wallet.getKeystores(), 1));

        WalletNode addr0 = wallet.getNode(KeyPurpose.RECEIVE).getChildren().iterator().next();

        Transaction fundTx = new Transaction();
        fundTx.addOutput(WALLET_UTXO_VALUE, addr0.getOutputScript());
        Sha256Hash fundHash = fundTx.getTxId();
        wallet.updateTransactions(Map.of(fundHash, new BlockTransaction(fundHash, 100, new Date(), null, fundTx)));

        Set<BlockTransactionHashIndex> txos = new TreeSet<>();
        txos.add(new BlockTransactionHashIndex(fundHash, 100, new Date(), null, 0, WALLET_UTXO_VALUE));
        addr0.updateTransactionOutputs(wallet, txos);
        wallet.setStoredBlockHeight(100);

        return wallet;
    }

    private static WalletRecoveryKey testRecoveryKey(Wallet wallet) {
        RecoveryKeyPair pair = RecoveryKeyPair.generate();
        WalletNode changeNode = wallet.getNode(KeyPurpose.CHANGE).getChildren().iterator().next();
        return WalletRecoveryKey.forTesting(changeNode, pair.getXOnlyPublicKey());
    }
}
