package com.sparrowwallet.sparrow.strata.net;

import com.sparrowwallet.sparrow.strata.protocol.StrataBridgeProtocol;
import com.sparrowwallet.drongo.Network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class StrataBridgeKeyVerificationServiceTest {
    @BeforeEach
    void resetService() {
        StrataBridgeKeyVerificationService.clearInstanceForTesting();
        Network.set(Network.MAINNET);
    }

    @AfterEach
    void clearOverrideUrl() {
        StrataBridgeKeyVerificationService.clearInstanceForTesting();
        Network.set(Network.MAINNET);
    }

    @Test
    void reportsVerifiedOnMatch() throws Exception {
        Network.set(Network.SIGNET);
        StrataBridgeKeyVerificationService service = StrataBridgeKeyVerificationService.getInstance();

        String hardcoded = StrataBridgeProtocol.getBridgeOperatorPubkeyHex(Network.get());
        try(StrataBridgeKeyMockServer server = new StrataBridgeKeyMockServer(hardcoded)) {
            StrataBridgeKeyVerificationService.setVerificationUrlForTesting(server.getUrl());

            service.refresh();
            awaitStatus(service, StrataBridgeKeyVerificationService.StrataBridgeKeyStatus.VERIFIED);

            assertEquals(StrataBridgeKeyVerificationService.StrataBridgeKeyStatus.VERIFIED, service.getStatus());
        }
    }

    @Test
    void reportsMismatchOnMismatch() throws Exception {
        Network.set(Network.SIGNET);
        StrataBridgeKeyVerificationService service = StrataBridgeKeyVerificationService.getInstance();

        try(StrataBridgeKeyMockServer server = new StrataBridgeKeyMockServer("00")) {
            StrataBridgeKeyVerificationService.setVerificationUrlForTesting(server.getUrl());

            service.refresh();
            awaitStatus(service, StrataBridgeKeyVerificationService.StrataBridgeKeyStatus.MISMATCH);

            assertEquals(StrataBridgeKeyVerificationService.StrataBridgeKeyStatus.MISMATCH, service.getStatus());
            assertEquals(StrataBridgeKeyVerificationService.BRIDGE_KEY_MISMATCH_MESSAGE, service.getMessage());
        }
    }

    @Test
    void reportsUnavailableOnUnreachable() throws Exception {
        Network.set(Network.SIGNET);
        StrataBridgeKeyVerificationService service = StrataBridgeKeyVerificationService.getInstance();

        StrataBridgeKeyVerificationService.setVerificationUrlForTesting("http://127.0.0.1:1/");

        service.refresh();
        awaitStatus(service, StrataBridgeKeyVerificationService.StrataBridgeKeyStatus.UNAVAILABLE);

        assertEquals(StrataBridgeKeyVerificationService.StrataBridgeKeyStatus.UNAVAILABLE, service.getStatus());
        assertEquals(StrataBridgeKeyVerificationService.BRIDGE_KEY_UNAVAILABLE_MESSAGE, service.getMessage());
    }

    @Test
    void allowsDepositsWhenChecksDisabled() throws Exception {
        Network.set(Network.SIGNET);
        StrataBridgeKeyVerificationService service = StrataBridgeKeyVerificationService.getInstance();

        service.setChecksDisabled(true);
        awaitStatus(service, StrataBridgeKeyVerificationService.StrataBridgeKeyStatus.CHECKS_DISABLED);

        assertEquals(StrataBridgeKeyVerificationService.StrataBridgeKeyStatus.CHECKS_DISABLED, service.getStatus());
        assertNotNull(service.getVerifiedBridgeOperatorPubkey().orElse(null));
    }

    private static void awaitStatus(StrataBridgeKeyVerificationService service, StrataBridgeKeyVerificationService.StrataBridgeKeyStatus expected)
            throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime() < deadlineNanos) {
            if(service.getStatus() == expected) {
                return;
            }
            Thread.sleep(25);
        }
        fail("Timed out waiting for bridge key status " + expected + ", last status was " + service.getStatus());
    }
}

