package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;

/**
 * Pure confirm-button and transaction-diagram gating for the deposit form.
 */
public final class DepositConfirmGate {
    private DepositConfirmGate() {
    }

    public record DepositConfirmState(
            boolean validationInvalid,
            boolean addressPresent,
            boolean labelPresent,
            boolean amountPresent,
            boolean depositAllowed,
            WalletTransaction preview,
            double minRelayFeeRate,
            boolean feeServiceRunning
    ) {
    }

    public record DepositDiagramState(
            boolean insufficientInputs,
            boolean addressValid,
            boolean labelPresent,
            DepositDescriptor descriptor,
            Long amountSats,
            Double sliderFeeRate,
            boolean userFeeSet,
            Long miningFeeFromTotal,
            WalletTransaction preview,
            Long previewAmountSats
    ) {
    }

    public static boolean isConfirmEnabled(DepositConfirmState state) {
        boolean fieldsValid = !state.validationInvalid()
                && state.addressPresent()
                && state.labelPresent()
                && state.amountPresent()
                && state.depositAllowed();
        boolean previewReady = state.preview() != null && !isInsufficientFeeRate(state.preview(), state.minRelayFeeRate());
        boolean notBuilding = !state.feeServiceRunning();
        return fieldsValid && previewReady && notBuilding;
    }

    public static boolean isInsufficientFeeRate(WalletTransaction preview, double minRelayFeeRate) {
        return preview != null && preview.getFeeRate() < minRelayFeeRate;
    }

    public static boolean canDisplayDiagram(DepositDiagramState state, double minRelayFeeRate) {
        if(state.insufficientInputs()) {
            return false;
        }
        if(!state.addressValid()) {
            return false;
        }
        if(!state.labelPresent()) {
            return false;
        }
        if(state.descriptor() == null || state.amountSats() == null || state.amountSats() <= 0 || state.sliderFeeRate() == null) {
            return false;
        }
        if(state.userFeeSet() && state.miningFeeFromTotal() == null) {
            return false;
        }
        if(state.preview() == null || !java.util.Objects.equals(state.previewAmountSats(), state.amountSats())) {
            return false;
        }
        return !isInsufficientFeeRate(state.preview(), minRelayFeeRate);
    }
}
