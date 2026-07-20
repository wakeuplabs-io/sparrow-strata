package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReclaimWalletCompatibilityTest {
    @Test
    void supportsSoftwareTaprootSingleSig() {
        Wallet wallet = createWallet(ScriptType.P2TR, PolicyType.SINGLE_HD, KeystoreSource.SW_SEED, WalletModel.SPARROW);

        assertTrue(ReclaimWalletCompatibility.supportsReclaimSigning(wallet));
        assertTrue(ReclaimWalletCompatibility.getCompatibilityError(wallet).isEmpty());
    }

    @Test
    void rejectsNonTaprootWallet() {
        Wallet wallet = createWallet(ScriptType.P2WPKH, PolicyType.SINGLE_HD, KeystoreSource.SW_SEED, WalletModel.SPARROW);

        assertFalse(ReclaimWalletCompatibility.supportsReclaimSigning(wallet));
        assertEquals(Optional.of("Reclaim requires a Taproot single-sig wallet"),
                ReclaimWalletCompatibility.getCompatibilityError(wallet));
    }

    @Test
    void rejectsMultisigWallet() {
        Wallet wallet = createWallet(ScriptType.P2TR, PolicyType.MULTI_HD, KeystoreSource.SW_SEED, WalletModel.SPARROW);

        assertFalse(ReclaimWalletCompatibility.supportsReclaimSigning(wallet));
        assertEquals(Optional.of("Reclaim requires a Taproot single-sig wallet"),
                ReclaimWalletCompatibility.getCompatibilityError(wallet));
    }

    @Test
    void rejectsMultiKeystoreWallet() {
        Wallet wallet = createWallet(ScriptType.P2TR, PolicyType.SINGLE_HD, KeystoreSource.SW_SEED, WalletModel.SPARROW);
        Keystore second = new Keystore();
        second.setSource(KeystoreSource.SW_SEED);
        second.setWalletModel(WalletModel.SPARROW);
        wallet.getKeystores().add(second);

        assertFalse(ReclaimWalletCompatibility.supportsReclaimSigning(wallet));
        assertEquals(Optional.of("Reclaim requires a single keystore wallet"),
                ReclaimWalletCompatibility.getCompatibilityError(wallet));
    }

    @Test
    void rejectsTrezorWithUnsupportedMessage() {
        Wallet wallet = createWallet(ScriptType.P2TR, PolicyType.SINGLE_HD, KeystoreSource.HW_USB, WalletModel.TREZOR_T);

        assertFalse(ReclaimWalletCompatibility.supportsReclaimSigning(wallet));
        Optional<String> error = ReclaimWalletCompatibility.getCompatibilityError(wallet);
        assertTrue(error.isPresent());
        assertTrue(error.get().contains("Trezor"));
        assertTrue(error.get().startsWith("Reclaim is not supported with "));
    }

    @Test
    void rejectsBitBox02WithUnsupportedMessage() {
        Wallet wallet = createWallet(ScriptType.P2TR, PolicyType.SINGLE_HD, KeystoreSource.HW_USB, WalletModel.BITBOX_02);

        assertFalse(ReclaimWalletCompatibility.supportsReclaimSigning(wallet));
        Optional<String> error = ReclaimWalletCompatibility.getCompatibilityError(wallet);
        assertTrue(error.isPresent());
        assertTrue(error.get().contains("BitBox"));
        assertTrue(error.get().startsWith("Reclaim is not supported with "));
    }

    @Test
    void rejectsLedgerWithUnsupportedMessage() {
        Wallet wallet = createWallet(ScriptType.P2TR, PolicyType.SINGLE_HD, KeystoreSource.HW_USB, WalletModel.LEDGER_NANO_X);

        assertFalse(ReclaimWalletCompatibility.supportsReclaimSigning(wallet));
        Optional<String> error = ReclaimWalletCompatibility.getCompatibilityError(wallet);
        assertTrue(error.isPresent());
        assertTrue(error.get().contains("Ledger"));
        assertTrue(error.get().startsWith("Reclaim is not supported with "));
        assertTrue(error.get().endsWith("Use a software Taproot wallet."));
    }

    @Test
    void rejectsNullWallet() {
        assertFalse(ReclaimWalletCompatibility.supportsReclaimSigning(null));
        assertEquals(Optional.of("Wallet is required for reclaim"),
                ReclaimWalletCompatibility.getCompatibilityError(null));
    }

    private static Wallet createWallet(ScriptType scriptType, PolicyType policyType, KeystoreSource source, WalletModel model) {
        Wallet wallet = new Wallet();
        wallet.setPolicyType(policyType);
        wallet.setScriptType(scriptType);

        Keystore keystore = new Keystore();
        keystore.setSource(source);
        keystore.setWalletModel(model);
        wallet.getKeystores().add(keystore);
        return wallet;
    }
}
