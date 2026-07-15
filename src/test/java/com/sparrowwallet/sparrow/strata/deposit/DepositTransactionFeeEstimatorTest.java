package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.Network;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepositTransactionFeeEstimatorTest {
    private static Double goldenVsize;

    @BeforeEach
    void setUp() {
        Network.set(Network.MAINNET);
    }

    @AfterEach
    void tearDown() {
        DepositTransactionFeeEstimator.clearCacheForTesting();
        Network.set(Network.MAINNET);
    }

    @Test
    void classLoadsWithoutInitializingNetwork() throws Exception {
        Class.forName("com.sparrowwallet.sparrow.strata.deposit.DepositTransactionFeeEstimator");
    }

    @Test
    void depositTransactionVirtualSizeIsStableGoldenValue() {
        double vsize = DepositTransactionFeeEstimator.getDepositTransactionVirtualSize();
        if(goldenVsize == null) {
            goldenVsize = vsize;
        }
        assertEquals(goldenVsize, vsize, 0.0);
        assertTrue(vsize > 100 && vsize < 300, "Unexpected deposit transaction vsize: " + vsize);
    }

    @Test
    void vsizeStableAcrossSupportedNetworks() {
        Network.set(Network.MAINNET);
        double mainnetVsize = DepositTransactionFeeEstimator.getDepositTransactionVirtualSize();

        Network.set(Network.SIGNET);
        double signetVsize = DepositTransactionFeeEstimator.getDepositTransactionVirtualSize();

        assertEquals(mainnetVsize, signetVsize, 0.0);
    }

    @Test
    void calculatesDepFeeFromFeeRate() {
        double vsize = DepositTransactionFeeEstimator.getDepositTransactionVirtualSize();
        assertEquals((long)Math.floor(vsize * 5.0), DepositTransactionFeeEstimator.calculateDepFee(5.0));
    }

    @Test
    void uiDepFeeMatchesDepositTransactionEstimate() {
        double feeRate = 5.0;
        assertEquals(
                DepositTransactionFeeEstimator.calculateDepFee(feeRate),
                DepositFeeRates.calculateDepFee(feeRate));
    }
}
