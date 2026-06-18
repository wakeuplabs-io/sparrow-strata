package com.sparrowwallet.sparrow.strata;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.DepositActionEvent;
import com.sparrowwallet.sparrow.event.StrataBridgeKeyVerificationUpdatedEvent;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeKeyVerificationService;
import com.sparrowwallet.sparrow.wallet.WalletFormController;
import com.sparrowwallet.sparrow.strata.deposit.DepositController;
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
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class StrataController extends WalletFormController implements Initializable {

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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        bridgeKeyError.managedProperty().bind(bridgeKeyError.visibleProperty());
        loadDepositPane();
        loadReclaimPane();
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
        VBox placeholder = new VBox();
        placeholder.setAlignment(javafx.geometry.Pos.CENTER);
        Label label = new Label("Reclaim flow coming soon");
        label.getStyleClass().add("help-label");
        placeholder.getChildren().add(label);
        reclaimPane = placeholder;
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
}
