package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.SigHash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.protocol.TransactionSignature;
import com.sparrowwallet.drongo.protocol.TransactionWitness;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.strata.deposit.DepositRequestLockingScript;
import com.sparrowwallet.drongo.address.P2TRAddress;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.sparrow.strata.deposit.RecoveryKeyPair;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReclaimTapscriptSignerTest {
    private static final String BRIDGE_INTERNAL_KEY_HEX =
            "bd67cd6f1d488245ad3dca07474e0a11ac43c01cc9b90a0b3794dd25ed2ac77e";

    @Test
    void signsTapscriptInputWithValidWitnessStack() {
        RecoveryKeyPair recoveryKeyPair = RecoveryKeyPair.generate();
        byte[] bridgeInternalKey = Utils.hexToBytes(BRIDGE_INTERNAL_KEY_HEX);
        int recoveryDelay = 1;
        Script tapscript = DepositRequestLockingScript.createRecoveryTapscript(recoveryKeyPair.getXOnlyPublicKey(), recoveryDelay);
        byte[] controlBlock = ReclaimControlBlock.forSingleLeafScript(bridgeInternalKey);

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
        Transaction transaction = new Transaction();
        transaction.addInput(utxo.getHash(), utxo.getIndex(), new Script(new byte[0]));
        transaction.getInputs().get(0).setSequenceNumber(recoveryDelay);
        transaction.setVersion(2);
        transaction.addOutput(900_000L, new P2TRAddress(recoveryKeyPair.getXOnlyPublicKey()).getOutputScript());

        TestKeystore keystore = new TestKeystore(recoveryKeyPair.getPrivateKey());
        WalletNode dummyNode = new WalletNode(null, com.sparrowwallet.drongo.KeyPurpose.CHANGE, 0);

        TransactionWitness witness = ReclaimTapscriptSigner.signInput(
                transaction, 0, List.of(utxo), tapscript, controlBlock, keystore, dummyNode);

        assertEquals(3, witness.getPushCount());
        TransactionSignature signature = witness.getSignatures().get(0);
        assertEquals(64, signature.encodeToBitcoin().length);

        Sha256Hash hash = transaction.hashForTaprootSignature(List.of(utxo), 0, true, tapscript, SigHash.DEFAULT, null);
        assertTrue(recoveryKeyPair.getPrivateKey().verify(hash, signature));
    }

    private static final class TestKeystore extends Keystore {
        private final ECKey privateKey;

        private TestKeystore(ECKey privateKey) {
            this.privateKey = privateKey;
        }

        @Override
        public ECKey getKey(WalletNode walletNode) {
            return privateKey;
        }
    }
}
