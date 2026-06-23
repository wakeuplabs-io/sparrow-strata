package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.P2TRAddress;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.strata.deposit.DepositRequestLockingScript;
import com.sparrowwallet.sparrow.strata.deposit.RecoveryKeyPair;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReclaimPsbtSignerTest {
    private static final String BRIDGE_INTERNAL_KEY_HEX =
            "bd67cd6f1d488245ad3dca07474e0a11ac43c01cc9b90a0b3794dd25ed2ac77e";

    @Test
    void unsignedReclaimPsbtCanBeSignedThroughSigner() {
        RecoveryKeyPair recoveryKeyPair = RecoveryKeyPair.generate();
        byte[] bridgeInternalKey = Utils.hexToBytes(BRIDGE_INTERNAL_KEY_HEX);
        int recoveryDelay = 1;

        TransactionOutput utxo = new TransactionOutput(null, 1_000_000L,
                DepositRequestLockingScript.createBridgeInAddress(recoveryKeyPair.getXOnlyPublicKey(), bridgeInternalKey, recoveryDelay).getOutputScript()) {
            @Override
            public Sha256Hash getHash() {
                return Sha256Hash.wrap("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            }

            @Override
            public int getIndex() {
                return 0;
            }
        };

        Transaction depositTransaction = new Transaction();
        depositTransaction.addOutput(utxo);

        ReclaimTransactionBuilder.ReclaimSpendInput spendInput = new ReclaimTransactionBuilder.ReclaimSpendInput(
                utxo, 0, recoveryKeyPair.getXOnlyPublicKey(), recoveryDelay, depositTransaction);
        Wallet wallet = createWallet(recoveryKeyPair);
        P2TRAddress destination = new P2TRAddress(recoveryKeyPair.getXOnlyPublicKey());

        PSBT psbt = ReclaimTransactionBuilder.buildPsbt(wallet, List.of(spendInput), destination, 1.0);
        assertTrue(ReclaimPsbt.isReclaimPsbt(psbt));
        assertFalse(psbt.isSigned());

        ReclaimPsbtSigner.sign(wallet, psbt);

        assertTrue(psbt.isSigned());
        assertTrue(psbt.isFinalized());
    }

    private static Wallet createWallet(RecoveryKeyPair recoveryKeyPair) {
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
        };
        keystore.setSource(KeystoreSource.SW_SEED);
        wallet.getKeystores().add(keystore);

        WalletNode receiveNode = wallet.getNode(com.sparrowwallet.drongo.KeyPurpose.RECEIVE);
        WalletNode child = new WalletNode(wallet, com.sparrowwallet.drongo.KeyPurpose.RECEIVE, 0);
        receiveNode.getChildren().add(child);
        return wallet;
    }
}
