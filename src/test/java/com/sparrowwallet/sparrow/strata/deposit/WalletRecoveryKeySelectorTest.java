package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Electrum;
import com.sparrowwallet.sparrow.io.ImportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletRecoveryKeySelectorTest {
    @BeforeEach
    void setUp() {
        Network.set(Network.MAINNET);
    }

    @AfterEach
    void tearDown() {
        Network.set(Network.MAINNET);
    }

    @Test
    void rejectsNonTaprootWallet() throws Exception {
        Wallet wallet = loadLegacyWallet();
        assertThrows(DepositRequestException.class, () -> WalletRecoveryKeySelector.select(wallet));
        assertTrue(WalletRecoveryKeySelector.getCompatibilityError(wallet).isPresent());
    }

    private static Wallet loadLegacyWallet() throws ImportException {
        Electrum electrum = new Electrum();
        InputStream is = WalletRecoveryKeySelectorTest.class.getResourceAsStream("/com/sparrowwallet/sparrow/io/electrum-singlesig-wallet.json");
        Wallet wallet = electrum.importWallet(is, null);
        if(wallet.getScriptType() == ScriptType.P2TR) {
            throw new IllegalStateException("Expected legacy wallet fixture");
        }
        return wallet;
    }
}
