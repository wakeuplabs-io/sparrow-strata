package com.sparrowwallet.sparrow.strata;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.DepositActionEvent;
import com.sparrowwallet.sparrow.event.StrataBridgeKeyVerificationUpdatedEvent;
import com.sparrowwallet.sparrow.event.WalletTabsClosedEvent;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeKeyVerificationService;
import com.sparrowwallet.sparrow.wallet.WalletFormController;
import com.sparrowwallet.sparrow.strata.deposit.DepositController;
import com.sparrowwallet.sparrow.strata.reclaim.ReclaimController;
import com.sparrowwallet.sparrow.strata.reclaim.ReclaimableUtxoFinder;
import com.sparrowwallet.sparrow.event.NewBlockEvent;
import com.sparrowwallet.sparrow.event.WalletHistoryChangedEvent;
import com.google.common.eventbus.Subscribe;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class StrataController extends WalletFormController implements Initializable {

    // TODO(reclaim): remove before merge — show Reclaim tab even without reclaimable UTXOs
    private static final boolean ALWAYS_SHOW_RECLAIM_TAB = true;

    @FXML
    private ToggleGroup strataFlowToggleGroup;

    @FXML
    private ToggleButton depositToggle;

    @FXML
    private ToggleButton reclaimToggle;

    @FXML
    private StackPane strataContent;

    @FXML
    private Label bridgeKeyError;

    private Node depositPane;
    private DepositController depositController;
    private Node reclaimPane;
    private ReclaimController reclaimController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        bridgeKeyError.managedProperty().bind(bridgeKeyError.visibleProperty());
        loadDepositPane();
        loadReclaimPane();
        updateReclaimTabVisibility();
        selectDeposit(null);
        updateBridgeKeyError();
    }

    private void loadDepositPane() {
        try {
            FXMLLoader loader = new FXMLLoader(AppServices.class.getResource("strata/deposit.fxml"));
            depositPane = loader.load();
            depositController = loader.getController();
            depositController.setWalletForm(getWalletForm());
        } catch(IOException e) {
            throw new IllegalStateException("Cannot load strata/deposit.fxml", e);
        }
    }

    private void loadReclaimPane() {
        try {
            FXMLLoader loader = new FXMLLoader(AppServices.class.getResource("strata/reclaim.fxml"));
            reclaimPane = loader.load();
            reclaimController = loader.getController();
            reclaimController.setWalletForm(getWalletForm());
        } catch(IOException e) {
            throw new IllegalStateException("Cannot load strata/reclaim.fxml", e);
        }
    }

    private void updateReclaimTabVisibility() {
        boolean hasReclaimableUtxos = ALWAYS_SHOW_RECLAIM_TAB
                || ReclaimableUtxoFinder.findReclaimableUtxos(getWalletForm().getWallet()).size() > 0;
        if(reclaimToggle != null) {
            reclaimToggle.setVisible(hasReclaimableUtxos);
            reclaimToggle.setManaged(hasReclaimableUtxos);
            if(!hasReclaimableUtxos && reclaimToggle.isSelected()) {
                selectDeposit(null);
            }
        }
    }

    @FXML
    public void selectDeposit(ActionEvent event) {
        showPane(depositPane);
    }

    @FXML
    public void selectReclaim(ActionEvent event) {
        showPane(reclaimPane);
    }

    private void showPane(Node pane) {
        strataContent.getChildren().setAll(pane);
        Platform.runLater(() -> {
            if(pane == depositPane && depositToggle != null && !depositToggle.isSelected()) {
                depositToggle.setSelected(true);
            } else if(pane == reclaimPane && reclaimToggle != null && !reclaimToggle.isSelected()) {
                reclaimToggle.setSelected(true);
            }
        });
    }

    private void updateBridgeKeyError() {
        StrataBridgeKeyVerificationService service = StrataBridgeKeyVerificationService.getInstance();
        StrataBridgeKeyVerificationService.StrataBridgeKeyStatus keyStatus = service.getStatus();
        boolean showError = keyStatus == StrataBridgeKeyVerificationService.StrataBridgeKeyStatus.MISMATCH
                || keyStatus == StrataBridgeKeyVerificationService.StrataBridgeKeyStatus.UNAVAILABLE;
        bridgeKeyError.setVisible(showError);
        bridgeKeyError.setText(showError ? service.getMessage() : null);
    }

    @Subscribe
    public void depositAction(DepositActionEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            selectDeposit(null);
            if(event.getUtxos() != null && !event.getUtxos().isEmpty()) {
                depositController.applySelectedUtxos(event.getUtxos());
            }
        }
    }

    @Subscribe
    public void strataBridgeKeyVerificationUpdated(StrataBridgeKeyVerificationUpdatedEvent event) {
        Platform.runLater(this::updateBridgeKeyError);
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            Platform.runLater(this::updateReclaimTabVisibility);
        }
    }

    @Subscribe
    public void newBlock(NewBlockEvent event) {
        Platform.runLater(this::updateReclaimTabVisibility);
    }

    @Subscribe
    @Override
    public void walletTabsClosed(WalletTabsClosedEvent event) {
        if(event.getClosedWalletTabData().stream().anyMatch(tabData -> tabData.getWalletForm() == getWalletForm())) {
            if(depositController != null) {
                EventManager.get().unregister(depositController);
            }
            if(reclaimController != null) {
                EventManager.get().unregister(reclaimController);
            }
        }
        super.walletTabsClosed(event);
    }
}
