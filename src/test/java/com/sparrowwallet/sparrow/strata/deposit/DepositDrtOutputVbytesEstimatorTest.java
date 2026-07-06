package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import com.sparrowwallet.sparrow.strata.model.Eip55Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DepositDrtOutputVbytesEstimatorTest {
    @BeforeEach
    void setUp() {
        Network.set(Network.MAINNET);
    }

    @AfterEach
    void tearDown() {
        Network.set(Network.MAINNET);
    }

    @Test
    void outputVbytesIncreaseWithLongerDescriptor() {
        DepositDescriptor shortDescriptor = DepositDescriptor.forAlpenDeposit(
                Eip55Address.parse("0x5400000000000000000000000000000000000001"));
        DepositDescriptor longDescriptor = DepositDrtOutputVbytesEstimator.conservativeDescriptorForEstimate();

        long shortVbytes = DepositDrtOutputVbytesEstimator.estimateOutputVbytes(shortDescriptor);
        long longVbytes = DepositDrtOutputVbytesEstimator.estimateOutputVbytes(longDescriptor);

        assertTrue(shortVbytes > 0);
        assertTrue(longVbytes > shortVbytes);
    }
}
