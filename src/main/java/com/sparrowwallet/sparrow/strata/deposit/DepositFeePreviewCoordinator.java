package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.wallet.InsufficientFundsException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;

import java.util.Objects;
import java.util.Optional;

/**
 * Manages async deposit fee preview state and {@link DepositFeeService} lifecycle.
 */
public class DepositFeePreviewCoordinator {
    public enum UpdateOutcome {
        SKIPPED_INVALID_INPUT,
        SKIPPED_AMOUNT_VALIDATION,
        SKIPPED_INVALID_CUSTOM_FEE,
        WALLET_INCOMPATIBLE,
        CACHE_HIT,
        IN_FLIGHT,
        STARTED
    }

    public record UpdateResult(UpdateOutcome outcome, DepositFeeRequestKey requestKey) {
    }

    public interface ServiceFactory {
        DepositFeeService create(DepositFeeRequestKey requestKey, String depositLabel, WalletRecoveryKey recoveryKey);
    }

    public interface Listener {
        void onPreviewSucceeded(WalletTransaction walletTransaction, DepositFeeRequestKey requestKey, long amountSats);

        void onPreviewFailed(boolean insufficientFunds);

        void onWalletIncompatible(String message);

        void onPreviewInvalidated();

        void onCacheHit();
    }

    private final Wallet wallet;
    private final ServiceFactory serviceFactory;
    private final Listener listener;

    private WalletRecoveryKey previewRecoveryKey;
    private DepositDescriptor lastPreviewDescriptor;
    private Long lastPreviewAmountSats;
    private DepositFeeRequestKey lastCompletedFeeRequest;
    private DepositFeeRequestKey inFlightFeeRequest;
    private Long previewAmountSats;
    private DepositFeeService depositFeeService;

    public DepositFeePreviewCoordinator(Wallet wallet, ServiceFactory serviceFactory, Listener listener) {
        this.wallet = wallet;
        this.serviceFactory = serviceFactory;
        this.listener = listener;
    }

    public Long getPreviewAmountSats() {
        return previewAmountSats;
    }

    public boolean isRunning() {
        return depositFeeService != null && depositFeeService.isRunning();
    }

    public void cancelRunningPreview() {
        if(depositFeeService != null && depositFeeService.isRunning()) {
            depositFeeService.setIgnoreResult(true);
            depositFeeService.cancel();
        }
        inFlightFeeRequest = null;
    }

    public UpdateResult requestPreview(DepositDescriptor descriptor, long amountSats, DepositFeeRequestKey requestKey,
                                       boolean amountValidationFailed, boolean invalidCustomFee,
                                       WalletTransaction currentPreview) {
        if(descriptor == null || amountSats <= 0) {
            resetRecoveryKey();
            invalidatePreview(false);
            listener.onPreviewInvalidated();
            return new UpdateResult(UpdateOutcome.SKIPPED_INVALID_INPUT, null);
        }

        if(amountValidationFailed) {
            invalidatePreview(false);
            listener.onPreviewInvalidated();
            return new UpdateResult(UpdateOutcome.SKIPPED_AMOUNT_VALIDATION, null);
        }

        if(invalidCustomFee) {
            invalidatePreview(false);
            listener.onPreviewInvalidated();
            return new UpdateResult(UpdateOutcome.SKIPPED_INVALID_CUSTOM_FEE, null);
        }

        Optional<String> walletCompatibilityError = WalletRecoveryKeySelector.getCompatibilityError(wallet);
        if(walletCompatibilityError.isPresent()) {
            invalidatePreview(false);
            listener.onWalletIncompatible(walletCompatibilityError.get());
            return new UpdateResult(UpdateOutcome.WALLET_INCOMPATIBLE, null);
        }

        if(requestKey.equals(lastCompletedFeeRequest) && Objects.equals(previewAmountSats, amountSats) && currentPreview != null) {
            listener.onCacheHit();
            return new UpdateResult(UpdateOutcome.CACHE_HIT, requestKey);
        }

        if(depositFeeService != null && depositFeeService.isRunning() && requestKey.equals(inFlightFeeRequest)) {
            return new UpdateResult(UpdateOutcome.IN_FLIGHT, requestKey);
        }

        if(depositFeeService != null && depositFeeService.isRunning()) {
            depositFeeService.setIgnoreResult(true);
            depositFeeService.cancel();
        }

        syncPreviewRecoveryKey(descriptor, amountSats);
        startService(requestKey, requestKey.label());
        return new UpdateResult(UpdateOutcome.STARTED, requestKey);
    }

    public void invalidatePreview() {
        invalidatePreview(true);
    }

    private void invalidatePreview(boolean notifyListener) {
        lastCompletedFeeRequest = null;
        inFlightFeeRequest = null;
        previewAmountSats = null;
        if(depositFeeService != null && depositFeeService.isRunning()) {
            depositFeeService.setIgnoreResult(true);
            depositFeeService.cancel();
        }
        if(notifyListener) {
            listener.onPreviewInvalidated();
        }
    }

    public void resetRecoveryKey() {
        previewRecoveryKey = null;
        lastPreviewDescriptor = null;
        lastPreviewAmountSats = null;
        lastCompletedFeeRequest = null;
        inFlightFeeRequest = null;
        previewAmountSats = null;
    }

    private void syncPreviewRecoveryKey(DepositDescriptor descriptor, long amountSats) {
        if(!Objects.equals(descriptor, lastPreviewDescriptor) || !Objects.equals(amountSats, lastPreviewAmountSats)) {
            previewRecoveryKey = null;
            lastPreviewDescriptor = descriptor;
            lastPreviewAmountSats = amountSats;
        }
        if(previewRecoveryKey == null) {
            previewRecoveryKey = WalletRecoveryKeySelector.select(wallet);
        }
    }
    private void startService(DepositFeeRequestKey requestKey, String depositLabel) {
        inFlightFeeRequest = requestKey;
        previewAmountSats = null;
        depositFeeService = serviceFactory.create(requestKey, depositLabel, previewRecoveryKey);

        final DepositFeeService currentService = depositFeeService;
        final DepositFeeRequestKey requestedKey = requestKey;
        depositFeeService.setOnSucceeded(event -> {
            if(!currentService.isIgnoreResult() && isActiveInFlightRequest(requestedKey)) {
                insufficientInputsSucceeded(currentService.getValue(), requestedKey);
            }
        });
        depositFeeService.setOnFailed(event -> {
            if(!currentService.isIgnoreResult() && isActiveInFlightRequest(requestedKey)) {
                inFlightFeeRequest = null;
                lastCompletedFeeRequest = null;
                previewAmountSats = null;
                Throwable exception = event.getSource().getException();
                if(exception instanceof DepositRequestException depositRequestException) {
                    listener.onWalletIncompatible(depositRequestException.getMessage());
                } else {
                    boolean insufficientFunds = exception instanceof InsufficientFundsException;
                    listener.onPreviewFailed(insufficientFunds);
                }
            }
        });

        depositFeeService.start();
    }

    private boolean isActiveInFlightRequest(DepositFeeRequestKey requestKey) {
        return requestKey.equals(inFlightFeeRequest);
    }

    private void insufficientInputsSucceeded(WalletTransaction walletTransaction, DepositFeeRequestKey requestedKey) {
        lastCompletedFeeRequest = requestedKey;
        inFlightFeeRequest = null;
        previewAmountSats = requestedKey.amountSats();
        listener.onPreviewSucceeded(walletTransaction, requestedKey, requestedKey.amountSats());
    }
}
