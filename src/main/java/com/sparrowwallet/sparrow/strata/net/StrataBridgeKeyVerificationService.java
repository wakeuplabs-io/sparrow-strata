package com.sparrowwallet.sparrow.strata.net;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.StrataBridgeKeyVerificationUpdatedEvent;
import com.sparrowwallet.sparrow.strata.deposit.StrataBridgeConstants;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class StrataBridgeKeyVerificationService {
    private static final Logger log = LoggerFactory.getLogger(StrataBridgeKeyVerificationService.class);

    public enum StrataBridgeKeyStatus {
        VERIFIED,
        MISMATCH,
        UNAVAILABLE,
        CHECKS_DISABLED
    }

    private static StrataBridgeKeyVerificationService instance;
    private static String verificationUrlForTesting;

    private volatile StrataBridgeKeyStatus status = StrataBridgeKeyStatus.UNAVAILABLE;
    private volatile String message = StrataBridgeConstants.BRIDGE_KEY_UNAVAILABLE_MESSAGE;
    private volatile boolean checksDisabled;
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    private StrataBridgeKeyVerificationService() {
    }

    public static StrataBridgeKeyVerificationService getInstance() {
        if(instance == null) {
            instance = new StrataBridgeKeyVerificationService();
        }
        return instance;
    }

    static void clearInstanceForTesting() {
        instance = null;
        verificationUrlForTesting = null;
    }

    static void setVerificationUrlForTesting(String url) {
        verificationUrlForTesting = url;
    }

    public StrataBridgeKeyStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public boolean isChecksDisabled() {
        return checksDisabled;
    }

    public void setChecksDisabled(boolean disabled) {
        checksDisabled = disabled;
        refresh();
    }

    public boolean isDepositAllowed() {
        return status == StrataBridgeKeyStatus.VERIFIED || status == StrataBridgeKeyStatus.CHECKS_DISABLED;
    }

    public Optional<byte[]> getVerifiedBridgeOperatorPubkey() {
        if(!isDepositAllowed()) {
            return Optional.empty();
        }
        Network network = Network.get();
        try {
            return Optional.of(StrataBridgeConstants.getBridgeOperatorPubkey(network));
        } catch(IllegalStateException e) {
            return Optional.empty();
        }
    }

    public void refresh() {
        if(!refreshInProgress.compareAndSet(false, true)) {
            return;
        }

        if(checksDisabled) {
            setStatus(StrataBridgeKeyStatus.CHECKS_DISABLED, null);
            refreshInProgress.set(false);
            return;
        }

        Network network = Network.get();
        String verificationUrl = verificationUrlForTesting != null
                ? verificationUrlForTesting
                : StrataBridgeConstants.getBridgeKeyVerificationUrl(network);
        String hardcodedHex = StrataBridgeConstants.getBridgeOperatorPubkeyHex(network);

        if(verificationUrl == null || hardcodedHex == null) {
            setStatus(StrataBridgeKeyStatus.UNAVAILABLE, StrataBridgeConstants.BRIDGE_KEY_UNAVAILABLE_MESSAGE);
            refreshInProgress.set(false);
            return;
        }

        Schedulers.io().scheduleDirect(() -> {
            try {
                StrataRpcClient client = new StrataRpcClient(AppServices.getHttpClientService(), verificationUrl);
                Optional<String> remoteHex = client.getBridgeOperatorPubkeyHex();
                if(remoteHex.isEmpty()) {
                    setStatus(StrataBridgeKeyStatus.UNAVAILABLE, StrataBridgeConstants.BRIDGE_KEY_UNAVAILABLE_MESSAGE);
                    return;
                }

                String normalizedHardcoded = StrataBridgeKeyParser.normalizeHex(hardcodedHex);
                if(normalizedHardcoded.equals(remoteHex.get())) {
                    setStatus(StrataBridgeKeyStatus.VERIFIED, null);
                    if(log.isInfoEnabled()) {
                        log.info("Bridge operator pubkey verified against {}", verificationUrl);
                    }
                } else {
                    setStatus(StrataBridgeKeyStatus.MISMATCH, StrataBridgeConstants.BRIDGE_KEY_MISMATCH_MESSAGE);
                    if(log.isWarnEnabled()) {
                        log.warn("Bridge operator pubkey mismatch: hardcoded {} vs remote {} from {}",
                                normalizedHardcoded, remoteHex.get(), verificationUrl);
                    }
                }
            } catch(Exception e) {
                setStatus(StrataBridgeKeyStatus.UNAVAILABLE, StrataBridgeConstants.BRIDGE_KEY_UNAVAILABLE_MESSAGE);
                if(log.isWarnEnabled()) {
                    log.warn("Failed to verify bridge operator pubkey from {}", verificationUrl, e);
                }
            } finally {
                refreshInProgress.set(false);
            }
        });
    }

    private void setStatus(StrataBridgeKeyStatus newStatus, String newMessage) {
        if(newStatus == status && (newMessage == null ? message == null : newMessage.equals(message))) {
            return;
        }
        status = newStatus;
        message = newMessage;
        EventManager.get().post(new StrataBridgeKeyVerificationUpdatedEvent(newStatus, newMessage));
    }
}
