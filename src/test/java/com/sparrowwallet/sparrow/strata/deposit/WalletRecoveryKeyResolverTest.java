package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Electrum;
import com.sparrowwallet.sparrow.io.ImportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletRecoveryKeyResolverTest {
    @BeforeEach
    void setUp() {
        Network.set(Network.MAINNET);
    }

    @AfterEach
    void tearDown() {
        Network.set(Network.MAINNET);
    }

    @Test
    void returnsEmptyForNonTaprootWallet() throws Exception {
        Wallet wallet = loadLegacyWallet();
        byte[] recoveryPk = RecoveryKeyPair.generate().getXOnlyPublicKey();

        Optional<com.sparrowwallet.drongo.wallet.WalletNode> signingNode =
                WalletRecoveryKeyResolver.findSigningNode(wallet, recoveryPk);

        assertTrue(signingNode.isEmpty());
    }

    private static Wallet loadLegacyWallet() throws ImportException {
        Electrum electrum = new Electrum();
        InputStream is = WalletRecoveryKeyResolverTest.class.getResourceAsStream("/com/sparrowwallet/sparrow/io/electrum-singlesig-wallet.json");
        return electrum.importWallet(is, null);
    }
}
