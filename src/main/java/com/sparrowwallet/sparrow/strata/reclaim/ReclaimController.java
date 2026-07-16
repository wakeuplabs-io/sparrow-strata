package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.sparrow.strata.protocol.StrataBridgeProtocol;
import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.event.DepositActionEvent;
import com.sparrowwallet.sparrow.event.NewBlockEvent;
import com.sparrowwallet.sparrow.event.StrataBridgeParametersUpdatedEvent;
import com.sparrowwallet.sparrow.event.ViewPSBTEvent;
import com.sparrowwallet.sparrow.event.WalletHistoryChangedEvent;
import com.sparrowwallet.sparrow.control.ReclaimUtxosTreeTable;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.strata.deposit.DepositAmountValidator;

import com.sparrowwallet.sparrow.strata.model.AlpenAddress;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeParametersService;
import com.sparrowwallet.sparrow.wallet.WalletFormController;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class ReclaimController extends WalletFormController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(ReclaimController.class);

    @FXML
    private ReclaimUtxosTreeTable reclaimTable;

    private Label reclaimableBalanceLabel;

    @FXML
    private Button selectAll;

    @FXML
    private Button clear;

    @FXML
    private Button retryDeposit;

    @FXML
    private Button reclaim;

    private List<ReclaimEntry> reclaimEntries = List.of();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    public void setReclaimableBalanceLabel(Label reclaimableBalanceLabel) {
        this.reclaimableBalanceLabel = reclaimableBalanceLabel;
    }

    @Override
    public void initializeView() {
        reclaimTable.initialize(List.of());
        refreshReclaimEntries();

        clear.setDisable(true);
        retryDeposit.setDisable(true);
        reclaim.setDisable(true);

        reclaimTable.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) change -> updateButtons());
    }

    private void refreshReclaimEntries() {
        reclaimEntries = new ArrayList<>(ReclaimableUtxoFinder.findReclaimableUtxos(getWalletForm().getWallet()));
        reclaimTable.updateEntries(reclaimEntries);
        updateReclaimableBalanceLabel();
        updateButtons();
    }

    private void updateReclaimableBalanceLabel() {
        if(reclaimableBalanceLabel == null) {
            return;
        }
        long balanceSats = reclaimEntries.stream().mapToLong(ReclaimEntry::getValue).sum();
        BitcoinUnit unit = getWalletForm().getWallet().getAutoUnit();
        UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
        String formatted = unit == BitcoinUnit.SATOSHIS
                ? format.formatSatsValue(balanceSats) + " sats"
                : format.formatBtcValue(balanceSats) + " BTC";
        reclaimableBalanceLabel.setText("You have " + formatted + " available to reclaim");
        reclaimableBalanceLabel.setVisible(balanceSats > 0);
        reclaimableBalanceLabel.setManaged(balanceSats > 0);
    }

    private void updateButtons() {
        List<ReclaimEntry> selectedEntries = getSelectedEntries();
        selectAll.setDisable(reclaimEntries.isEmpty() || reclaimEntries.size() == selectedEntries.size());
        clear.setDisable(selectedEntries.isEmpty());
        retryDeposit.setDisable(selectedEntries.isEmpty());
        reclaim.setDisable(selectedEntries.isEmpty());
    }

    private List<ReclaimEntry> getSelectedEntries() {
        return reclaimTable.getSelectionModel().getSelectedCells().stream()
                .filter(tp -> tp.getTreeItem() != null)
                .map(tp -> tp.getTreeItem().getValue())
                .filter(ReclaimEntry.class::isInstance)
                .map(ReclaimEntry.class::cast)
                .collect(Collectors.toList());
    }

    @FXML
    public void selectAll(ActionEvent event) {
        reclaimTable.getSelectionModel().selectAll();
    }

    @FXML
    public void clear(ActionEvent event) {
        reclaimTable.getSelectionModel().clearSelection();
    }

    @FXML
    public void retryDeposit(ActionEvent event) {
        Wallet wallet = getWalletForm().getWallet();
        List<ReclaimEntry> selectedEntries = getSelectedEntries();
        if(selectedEntries.isEmpty()) {
            return;
        }

        long reclaimedTotal = selectedEntries.stream().mapToLong(ReclaimEntry::getValue).sum();
        OptionalLong depositUtxoAmountSats = StrataBridgeParametersService.getInstance().getDepositUtxoAmountSats();
        //Reclaimed value includes the old deposit's dep_fee, which isn't a valid deposit amount on its own -
        //round down to the largest amount that's a valid multiple of the deposit denomination, absorbing the
        //remainder as extra fee/dust. The user can still increase the amount; the new deposit form will pull
        //in additional wallet UTXOs to cover the difference.
        long amountSats = depositUtxoAmountSats.isPresent()
                ? DepositAmountValidator.largestValidAmount(reclaimedTotal, depositUtxoAmountSats.getAsLong(), StrataBridgeProtocol.MAX_DEPOSIT_SATS)
                : 0;
        String destination = getCommonDestination(selectedEntries);
        EventManager.get().post(new DepositActionEvent(wallet, selectedEntries, destination, amountSats));
    }

    /**
     * A reclaimed UTXO's destination is only pre-filled when every selected UTXO was heading to the
     * same place.
     */
    private String getCommonDestination(List<ReclaimEntry> entries) {
        AlpenAddress destination = null;
        for(ReclaimEntry entry : entries) {
            Optional<AlpenAddress> entryDestination = entry.getDestinationAddress();
            if(entryDestination.isEmpty()) {
                return null;
            }
            if(destination == null) {
                destination = entryDestination.get();
            } else if(!destination.equals(entryDestination.get())) {
                return null;
            }
        }
        return destination == null ? null : destination.toHexString();
    }

    @FXML
    public void reclaimSelected(ActionEvent event) {
        Wallet wallet = getWalletForm().getWallet();
        List<ReclaimEntry> selectedEntries = getSelectedEntries();
        if(selectedEntries.isEmpty()) {
            return;
        }

        try {
            Address destination = getUnusedReceiveAddress(wallet);
            double feeRate = Math.max(
                    AppServices.getDefaultFeeRate() != null ? AppServices.getDefaultFeeRate() : AppServices.getFallbackFeeRate(),
                    AppServices.getMinimumRelayFeeRate() != null ? AppServices.getMinimumRelayFeeRate() : AppServices.getFallbackFeeRate()
            );
            PSBT psbt = ReclaimRequestService.buildReclaimPsbt(wallet, selectedEntries, destination, feeRate);
            EventManager.get().post(new ViewPSBTEvent(reclaim.getScene().getWindow(), "Reclaim", null, psbt));
        } catch(ReclaimException e) {
            AppServices.showErrorDialog("Reclaim unavailable", e.getMessage());
        } catch(Exception e) {
            log.error("Failed to build reclaim transaction", e);
            AppServices.showErrorDialog("Reclaim failed", e.getMessage() != null ? e.getMessage() : "Failed to build reclaim transaction");
        }
    }

    private Address getUnusedReceiveAddress(Wallet wallet) {
        WalletNode freshNode = wallet.getFreshNode(KeyPurpose.RECEIVE);
        Address freshAddress = freshNode.getAddress();
        while(freshNode.getLabel() != null && !freshNode.getLabel().isEmpty()) {
            freshNode = wallet.getFreshNode(KeyPurpose.RECEIVE, freshNode);
            freshAddress = freshNode.getAddress();
        }
        return freshAddress;
    }

    public boolean hasReclaimableUtxos() {
        return !reclaimEntries.isEmpty();
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            javafx.application.Platform.runLater(this::refreshReclaimEntries);
        }
    }

    @Subscribe
    public void newBlock(NewBlockEvent event) {
        javafx.application.Platform.runLater(this::refreshReclaimEntries);
    }

    @Subscribe
    public void strataBridgeParametersUpdated(StrataBridgeParametersUpdatedEvent event) {
        javafx.application.Platform.runLater(this::refreshReclaimEntries);
    }
}
