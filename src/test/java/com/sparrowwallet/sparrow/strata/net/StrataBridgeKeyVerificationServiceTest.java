package com.sparrowwallet.sparrow.strata.net;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.StrataBridgeKeyVerificationUpdatedEvent;
import com.sparrowwallet.sparrow.strata.deposit.StrataBridgeConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StrataBridgeKeyVerificationServiceTest {
    @AfterEach
    void clearOverrideUrl() {
        StrataBridgeKeyVerificationService.clearInstanceForTesting();
        Network.set(Network.MAINNET);
    }

    @Test
    void reportsVerifiedOnMatch() throws Exception {
        Network.set(Network.TESTNET);
        StrataBridgeKeyVerificationService service = StrataBridgeKeyVerificationService.getInstance();

        String hardcoded = StrataBridgeConstants.getBridgeOperatorPubkeyHex(Network.get());
        try(StrataBridgeKeyMockServer server = new StrataBridgeKeyMockServer(hardcoded)) {
            StrataBridgeKeyVerificationService.setVerificationUrlForTesting(server.getUrl());

            Awaiter awaiter = new Awaiter();
            EventManager.get().register(awaiter);

            service.refresh();
            awaiter.await();

            assertEquals(StrataBridgeKeyVerificationService.StrataBridgeKeyStatus.VERIFIED, service.getStatus());
        }
    }

    @Test
    void reportsMismatchOnMismatch() throws Exception {
        Network.set(Network.TESTNET);
        StrataBridgeKeyVerificationService service = StrataBridgeKeyVerificationService.getInstance();

        try(StrataBridgeKeyMockServer server = new StrataBridgeKeyMockServer("00")) {
            StrataBridgeKeyVerificationService.setVerificationUrlForTesting(server.getUrl());

            Awaiter awaiter = new Awaiter();
            EventManager.get().register(awaiter);

            service.refresh();
            awaiter.await();

            assertEquals(StrataBridgeKeyVerificationService.StrataBridgeKeyStatus.MISMATCH, service.getStatus());
            assertEquals(StrataBridgeConstants.BRIDGE_KEY_MISMATCH_MESSAGE, service.getMessage());
        }
    }

    @Test
    void reportsUnavailableOnUnreachable() throws Exception {
        Network.set(Network.TESTNET);
        StrataBridgeKeyVerificationService service = StrataBridgeKeyVerificationService.getInstance();

        StrataBridgeKeyVerificationService.setVerificationUrlForTesting("http://127.0.0.1:1/");

        Awaiter awaiter = new Awaiter();
        EventManager.get().register(awaiter);

        // Ensure the status transitions so we get an event even if the default state is UNAVAILABLE.
        service.setChecksDisabled(true);
        awaiter.await();

        Awaiter awaiter2 = new Awaiter();
        EventManager.get().register(awaiter2);
        service.setChecksDisabled(false);
        awaiter2.await();

        assertEquals(StrataBridgeKeyVerificationService.StrataBridgeKeyStatus.UNAVAILABLE, service.getStatus());
        assertEquals(StrataBridgeConstants.BRIDGE_KEY_UNAVAILABLE_MESSAGE, service.getMessage());
    }

    @Test
    void allowsDepositsWhenChecksDisabled() throws Exception {
        Network.set(Network.TESTNET);
        StrataBridgeKeyVerificationService service = StrataBridgeKeyVerificationService.getInstance();

        Awaiter awaiter = new Awaiter();
        EventManager.get().register(awaiter);

        service.setChecksDisabled(true);
        awaiter.await();

        assertEquals(StrataBridgeKeyVerificationService.StrataBridgeKeyStatus.CHECKS_DISABLED, service.getStatus());
        assertNotNull(service.getVerifiedBridgeOperatorPubkey().orElse(null));
    }

    private static class Awaiter {
        private final CountDownLatch latch = new CountDownLatch(1);

        @Subscribe
        public void onUpdate(StrataBridgeKeyVerificationUpdatedEvent event) {
            latch.countDown();
        }

        void await() throws InterruptedException {
            if(!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for bridge key verification update event");
            }
        }
    }
}

