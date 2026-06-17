package com.sparrowwallet.sparrow.strata.deposit;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.InsufficientFundsException;
import com.sparrowwallet.drongo.wallet.PresetUtxoSelector;
import com.sparrowwallet.drongo.wallet.UtxoSelector;
import com.sparrowwallet.drongo.wallet.BnBUtxoSelector;
import com.sparrowwallet.drongo.wallet.KnapsackUtxoSelector;
import com.sparrowwallet.drongo.wallet.StonewallUtxoSelector;
import com.sparrowwallet.drongo.wallet.ExcludeTxoFilter;
import com.sparrowwallet.drongo.wallet.MaxUtxoSelector;
import com.sparrowwallet.drongo.wallet.SpentTxoFilter;
import com.sparrowwallet.drongo.wallet.FrozenTxoFilter;
import com.sparrowwallet.drongo.wallet.CoinbaseTxoFilter;
import com.sparrowwallet.drongo.wallet.TxoFilter;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.wallet.WalletNodePayment;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.*;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeKeyVerificationService;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeParametersService;
import com.sparrowwallet.sparrow.strata.model.AlpenAddressParseResult;
import com.sparrowwallet.sparrow.strata.model.AlpenAddressParser;
import com.sparrowwallet.sparrow.strata.model.AlpenConstants;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import com.sparrowwallet.sparrow.wallet.OptimizationStrategy;
import com.sparrowwallet.sparrow.wallet.WalletFormController;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.Duration;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tornadofx.control.Field;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.sparrowwallet.sparrow.AppServices.*;

public class DepositController extends WalletFormController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(DepositController.class);
    private static final long ESTIMATED_DRT_OUTPUT_VBYTES = 120;

    @FXML
    private TextField depositTo;

    @FXML
    private TextField label;

    @FXML
    private TextField amount;

    @FXML
    private ComboBox<BitcoinUnit> amountUnit;

    @FXML
    private FiatLabel fiatAmount;

    @FXML
    private Label amountStatus;

    @FXML
    private ToggleButton maxButton;

    @FXML
    private Button scanQrButton;

    @FXML
    private ToggleGroup feeSelectionToggleGroup;

    @FXML
    private ToggleButton targetBlocksToggle;

    @FXML
    private ToggleButton mempoolSizeToggle;

    @FXML
    private ToggleButton recentBlocksToggle;

    @FXML
    private Field targetBlocksField;

    @FXML
    private Slider targetBlocks;

    @FXML
    private Field feeRangeField;

    @FXML
    private FeeRangeSlider feeRange;

    @FXML
    private CopyableLabel feeRate;

    @FXML
    private Label feeRatePriority;

    @FXML
    private Glyph feeRatePriorityGlyph;

    @FXML
    private Label cpfpFeeRate;

    @FXML
    private TextField fee;

    @FXML
    private ComboBox<BitcoinUnit> feeAmountUnit;

    @FXML
    private FiatLabel fiatFeeAmount;

    @FXML
    private BlockTargetFeeRatesChart blockTargetFeeRatesChart;

    @FXML
    private MempoolSizeFeeRatesChart mempoolSizeFeeRatesChart;

    @FXML
    private RecentBlocksView recentBlocksView;

    @FXML
    private TransactionDiagram transactionDiagram;

    @FXML
    private ToggleGroup optimizationToggleGroup;

    @FXML
    private ToggleButton efficiencyToggle;

    @FXML
    private ToggleButton privacyToggle;

    @FXML
    private HelpLabel optimizationHelp;

    @FXML
    private Label privacyAnalysis;

    @FXML
    private Hyperlink depositStatusLink;

    @FXML
    private Hyperlink bridgeWithdrawalsLink;

    @FXML
    private Button confirmButton;

    @FXML
    private Button clearButton;

    private ValidationSupport validationSupport;

    private DepositFeeRateSection feeRateSection;

    private DepositFeePreviewCoordinator previewCoordinator;

    private final ObjectProperty<AlpenAddressParseResult> depositAddressProperty = new SimpleObjectProperty<>(null);

    private PauseTransition feeUpdatePause;

    private final BooleanProperty insufficientInputsProperty = new SimpleBooleanProperty(false);

    private final BooleanProperty emptyAmountProperty = new SimpleBooleanProperty(true);

    private final ObjectProperty<UtxoSelector> utxoSelectorProperty = new SimpleObjectProperty<>(null);

    private final ObjectProperty<TxoFilter> txoFilterProperty = new SimpleObjectProperty<>(null);

    private final ObjectProperty<WalletTransaction> walletTransactionProperty = new SimpleObjectProperty<>(null);

    private final Set<WalletNode> excludedChangeNodes = new HashSet<>();

    private final StringProperty utxoLabelSelectionProperty = new SimpleStringProperty("");

    private final ChangeListener<String> amountListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
            if(feeRateSection != null && feeRateSection.getApplyingPreviewUpdates() > 0) {
                return;
            }
            emptyAmountProperty.set(newValue == null || newValue.isEmpty());
            if(utxoSelectorProperty.get() instanceof MaxUtxoSelector) {
                utxoSelectorProperty.setValue(null);
            }
            maxButton.setSelected(false);
            if(feeRateSection != null) {
                feeRateSection.resetUserFeeSet();
            }
            Long amountSats = tryParseAmountValueSats();
            if(amountSats != null && amountSats > 0) {
                setFiatAmount(AppServices.getFiatCurrencyExchangeRate(), amountSats);
            } else {
                fiatAmount.setText("");
            }
            updateConfirmButton();
            revalidate(amount, amountListener);
            applyTransactionDiagramState();
            scheduleUpdateFee();
        }
    };

    private final ChangeListener<String> feeListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
            if(feeRateSection != null && feeRateSection.getApplyingPreviewUpdates() > 0) {
                return;
            }
            validationSupport.setErrorDecorationEnabled(true);
            feeRateSection.onFeeFieldChanged(newValue);
        }
    };

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        feeRateSection = new DepositFeeRateSection(new DepositFeeRateSection.DepositFeeControls(
                feeSelectionToggleGroup, targetBlocksToggle, mempoolSizeToggle, recentBlocksToggle,
                targetBlocksField, targetBlocks, feeRangeField, feeRange, feeRate, feeRatePriority, feeRatePriorityGlyph,
                cpfpFeeRate, fee, feeAmountUnit, fiatFeeAmount, blockTargetFeeRatesChart, mempoolSizeFeeRatesChart, recentBlocksView),
                getWalletForm().getWallet(), this::scheduleUpdateFee);
        feeRateSection.setFeeTextListener(feeListener);
        feeRateSection.initialize(Config.get().getBitcoinUnit());
        feeRateSection.register();
        fee.textProperty().addListener(feeListener);

        previewCoordinator = new DepositFeePreviewCoordinator(
                (requestKey, depositLabel, recoveryKeyPair) -> new DepositFeeService(
                        getWalletForm().getWallet(),
                        requestKey.descriptor(),
                        requestKey.amountSats(),
                        depositLabel,
                        requestKey.selectionFeeRate(),
                        requestKey.sliderFeeRate(),
                        feeRateSection.getMinimumFeeRate(),
                        AppServices.getMinimumRelayFeeRate(),
                        requestKey.userFee(),
                        AppServices.getCurrentBlockHeight(),
                        Config.get().isGroupByAddress(),
                        Config.get().isIncludeMempoolOutputs(),
                        getUtxoSelectors(),
                        excludedChangeNodes,
                        getTxoFilters(),
                        recoveryKeyPair),
                new DepositFeePreviewCoordinator.Listener() {
                    @Override
                    public void onPreviewSucceeded(WalletTransaction walletTransaction, DepositFeeRequestKey requestKey, long amountSats) {
                        insufficientInputsProperty.set(false);
                        walletTransactionProperty.setValue(walletTransaction);
                        applyTransactionDiagramState();
                        revalidate(amount, amountListener);
                        feeRateSection.revalidateFeeField(feeListener);
                        updateConfirmButton();
                    }

                    @Override
                    public void onPreviewFailed(boolean insufficientFunds) {
                        clearWalletTransactionPreview();
                        insufficientInputsProperty.set(insufficientFunds);
                        revalidate(amount, amountListener);
                        feeRateSection.revalidateFeeField(feeListener);
                        applyTransactionDiagramState();
                        updateConfirmButton();
                    }

                    @Override
                    public void onPreviewInvalidated() {
                        clearWalletTransactionPreview();
                    }

                    @Override
                    public void onCacheHit() {
                        feeRateSection.setFiatFeeAmount(AppServices.getFiatCurrencyExchangeRate(), feeRateSection.getFeeValueSats());
                    }
                },
                ignored -> buildCurrentFeeRequestKey());

        addValidation();
        initializeAmountFields();
        initializeCoinControl();
        initializeBridgeLinks();
        StrataBridgeParametersService.getInstance().refresh();
        StrataBridgeKeyVerificationService.getInstance().refresh();
        updateConfirmButton();
        updateMaxButton();
        updateFee();
    }

    private void initializeBridgeLinks() {
        updateBridgeLinkVisibility();
    }

    private void updateBridgeLinks() {
        updateBridgeLinkVisibility();
    }

    private void updateBridgeLinkVisibility() {
        Network network = Network.get();
        String statusUrl = StrataBridgeConstants.getBridgeStatusUrl(network);
        String withdrawalUrl = StrataBridgeConstants.getBridgeWithdrawalUrl(network);
        depositStatusLink.setVisible(statusUrl != null);
        depositStatusLink.setManaged(statusUrl != null);
        bridgeWithdrawalsLink.setVisible(withdrawalUrl != null);
        bridgeWithdrawalsLink.setManaged(withdrawalUrl != null);
    }

    @FXML
    public void openDepositStatus(ActionEvent event) {
        openBridgeUrl(StrataBridgeConstants.getBridgeStatusUrl(Network.get()));
    }

    @FXML
    public void openBridgeWithdrawals(ActionEvent event) {
        openBridgeUrl(StrataBridgeConstants.getBridgeWithdrawalUrl(Network.get()));
    }

    private void openBridgeUrl(String url) {
        if(url != null && !url.isBlank()) {
            AppServices.get().getApplication().getHostServices().showDocument(url);
        }
    }

    private void addValidation() {
        validationSupport = new ValidationSupport();
        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
        validationSupport.setErrorDecorationEnabled(false);

        validationSupport.registerValidator(depositTo, false, Validator.combine(
                Validator.createEmptyValidator("Deposit address is required"),
                (Control control, String value) -> validateDepositAddress(control, value)
        ));
        validationSupport.registerValidator(label, false, Validator.createEmptyValidator("Label is required"));
        validationSupport.registerValidator(amount, false, Validator.combine(
                (Control control, String value) -> validateDepositAmount(control, value),
                (Control c, String newValue) -> ValidationResult.fromErrorIf(c, "Insufficient Inputs", tryParseAmountValueSats() != null && insufficientInputsProperty.get())
        ));
        validationSupport.registerValidator(fee, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf(c, "Insufficient Inputs", feeRateSection.isUserFeeSet() && insufficientInputsProperty.get()),
                (Control c, String newValue) -> ValidationResult.fromErrorIf(c, "Insufficient Fee Rate", isInsufficientFeeRate())
        ));

        insufficientInputsProperty.addListener((observable, oldValue, newValue) -> {
            revalidate(amount, amountListener);
            revalidate(fee, feeListener);
            applyTransactionDiagramState();
            updateConfirmButton();
        });

        validationSupport.validationResultProperty().addListener((observable, oldValue, newValue) -> {
            applyTransactionDiagramState();
            updateConfirmButton();
        });
        depositTo.textProperty().addListener((observable, oldValue, newValue) -> {
            validationSupport.setErrorDecorationEnabled(true);
            syncDepositAddressProperty(newValue);
            updateConfirmButton();
            updateMaxButton();
            scheduleUpdateFee();
        });
        label.textProperty().addListener((observable, oldValue, newValue) -> {
            updateConfirmButton();
            updateMaxButton();
            scheduleUpdateFee();
        });
        amount.textProperty().addListener(amountListener);
    }

    private void clearFeeBuildMessageListener() {
    }

    private DepositFeeRequestKey buildFeeRequestKey(DepositDescriptor descriptor, long amountSats, double sliderFeeRate, Long userFee, String depositLabel) {
        OptimizationStrategy optimizationStrategy = (OptimizationStrategy)optimizationToggleGroup.getSelectedToggle().getUserData();
        int coinControlHash = Objects.hash(utxoSelectorProperty.get(), txoFilterProperty.get(), excludedChangeNodes);
        return new DepositFeeRequestKey(descriptor, amountSats, sliderFeeRate, feeRateSection.getSelectionFeeRate(), userFee, depositLabel, optimizationStrategy, coinControlHash);
    }

    private DepositFeeRequestKey buildCurrentFeeRequestKey() {
        DepositDescriptor descriptor = getDepositDescriptor();
        Long amountSats = tryParseAmountValueSats();
        Double sliderFeeRate = feeRateSection.getSliderFeeRate();
        if(descriptor == null || amountSats == null || sliderFeeRate == null) {
            return null;
        }
        Long userFee = feeRateSection.isUserFeeSet() ? feeRateSection.resolveMiningFeeFromTotal(sliderFeeRate) : null;
        if(feeRateSection.isUserFeeSet() && userFee == null) {
            return null;
        }
        String depositLabel = label.getText() == null || label.getText().isBlank() ? "deposit" : label.getText();
        return buildFeeRequestKey(descriptor, amountSats, sliderFeeRate, userFee, depositLabel);
    }

    private void scheduleUpdateFee() {
        if(feeUpdatePause == null) {
            feeUpdatePause = new PauseTransition(Duration.millis(300));
            feeUpdatePause.setOnFinished(event -> updateFee());
        }
        feeUpdatePause.playFromStart();
    }

    private ValidationResult validateDepositAmount(Control control, String value) {
        if(value == null || value.isEmpty()) {
            return ValidationResult.fromError(control, "Amount is required");
        }
        try {
            Optional<String> error = getDepositAmountValidationError(getAmountValueSats());
            return error.map(message -> ValidationResult.fromError(control, message)).orElseGet(ValidationResult::new);
        } catch(NumberFormatException e) {
            return ValidationResult.fromError(control, "Invalid amount");
        }
    }

    private Optional<String> getDepositAmountValidationError(Long amountSats) {
        if(amountSats == null) {
            return Optional.of("Invalid amount");
        }
        OptionalLong depositUtxoAmountSats = StrataBridgeParametersService.getInstance().getDepositUtxoAmountSats();
        if(depositUtxoAmountSats.isEmpty()) {
            return Optional.of("Deposit denomination is not available for this network");
        }
        return DepositAmountValidator.validate(amountSats, depositUtxoAmountSats.getAsLong(), StrataBridgeConstants.MAX_DEPOSIT_SATS);
    }

    private ValidationResult validateDepositAddress(Control control, String value) {
        if(value == null || value.isBlank()) {
            depositAddressProperty.set(null);
            return new ValidationResult();
        }

        try {
            AlpenAddressParseResult result = AlpenAddressParser.parse(value, Network.get());
            depositAddressProperty.set(result);
            return new ValidationResult();
        } catch(IllegalArgumentException e) {
            depositAddressProperty.set(null);
            String message = e.getMessage();
            if(message == null || message.isBlank() || "Deposit address is required".equals(message)) {
                message = AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE;
            }
            return ValidationResult.fromError(control, message);
        }
    }

    public DepositDescriptor getDepositDescriptor() {
        AlpenAddressParseResult result = depositAddressProperty.get();
        return result == null ? null : result.getDepositDescriptor();
    }

    private void syncDepositAddressProperty(String value) {
        if(value == null || value.isBlank()) {
            depositAddressProperty.set(null);
            return;
        }

        try {
            depositAddressProperty.set(AlpenAddressParser.parse(value, Network.get()));
        } catch(IllegalArgumentException e) {
            depositAddressProperty.set(null);
        }
    }

    private boolean isValidDepositAddress() {
        String value = depositTo.getText();
        if(value == null || value.isBlank()) {
            return false;
        }

        try {
            AlpenAddressParser.parse(value, Network.get());
            return true;
        } catch(IllegalArgumentException e) {
            return false;
        }
    }

    private void initializeAmountFields() {
        amount.setTextFormatter(new CoinTextFormatter(Config.get().getUnitFormat()));
        amountUnit.getSelectionModel().select(BitcoinUnit.BTC);
        amountUnit.valueProperty().addListener((observable, oldValue, newValue) -> {
            Long value = getAmountValueSats(oldValue);
            if(value != null) {
                setAmountValueSats(value);
            }
            updateConfirmButton();
            updateFee();
        });

        amountStatus.managedProperty().bind(amountStatus.visibleProperty());
        amountStatus.visibleProperty().bind(insufficientInputsProperty.and(emptyAmountProperty.not()));
    }

    private void initializeCoinControl() {
        utxoLabelSelectionProperty.addListener((observable, oldValue, newValue) -> {
            maxButton.setText("Max" + newValue);
            clearButton.setText("Clear" + newValue);
        });

        utxoSelectorProperty.addListener((observable, oldValue, utxoSelector) -> {
            updateMaxClearButtons(utxoSelector, txoFilterProperty.get());
            updateOptimizationButtons();
            updateMaxButton();
            updateFee();
        });

        txoFilterProperty.addListener((observable, oldValue, txoFilter) -> {
            updateMaxClearButtons(utxoSelectorProperty.get(), txoFilter);
            updateMaxButton();
            updateFee();
        });

        walletTransactionProperty.addListener((observable, oldValue, walletTransaction) -> {
            feeRateSection.incrementApplyingPreviewUpdates();
            try {
                if(walletTransaction != null) {
                    feeRateSection.applyPreviewFees(walletTransaction, getWalletForm().getWallet());
                    if(feeRateSection.isUserFeeSet()) {
                        feeRateSection.revalidateFeeField(feeListener);
                    }
                }
                updatePrivacyAnalysis(walletTransaction);
            } finally {
                feeRateSection.decrementApplyingPreviewUpdates();
            }
            applyTransactionDiagramState();
            updateConfirmButton();
        });

        transactionDiagram.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if(oldScene == null && newScene != null) {
                applyTransactionDiagramState();
            }
        });

        optimizationHelp.managedProperty().bind(optimizationHelp.visibleProperty());
        privacyAnalysis.managedProperty().bind(privacyAnalysis.visibleProperty());
        optimizationHelp.visibleProperty().bind(privacyAnalysis.visibleProperty().not());

        efficiencyToggle.setOnAction(event -> {
            Config.get().setSendOptimizationStrategy(OptimizationStrategy.EFFICIENCY);
            updateFee();
        });
        privacyToggle.setOnAction(event -> {
            Config.get().setSendOptimizationStrategy(OptimizationStrategy.PRIVACY);
            updateFee();
        });

        OptimizationStrategy strategy = Config.get().getSendOptimizationStrategy();
        if(strategy == OptimizationStrategy.PRIVACY) {
            privacyToggle.setSelected(true);
        } else {
            efficiencyToggle.setSelected(true);
        }
        transactionDiagram.setOptimizationStrategy(strategy);
        updatePrivacyAnalysis(null);
    }

    private void updateMaxClearButtons(UtxoSelector utxoSelector, TxoFilter txoFilter) {
        if(utxoSelector instanceof PresetUtxoSelector presetUtxoSelector) {
            int num = presetUtxoSelector.getPresetUtxos().size();
            String selection = " (" + num + " UTXO" + (num != 1 ? "s" : "") + " selected)";
            utxoLabelSelectionProperty.set(selection);
        } else if(txoFilter instanceof ExcludeTxoFilter excludeTxoFilter) {
            int num = excludeTxoFilter.getExcludedTxos().size();
            String exclusion = " (" + num + " UTXO" + (num != 1 ? "s" : "") + " excluded)";
            utxoLabelSelectionProperty.set(exclusion);
        } else {
            utxoLabelSelectionProperty.set("");
        }
    }

    private void updateOptimizationButtons() {
        boolean coinControl = utxoSelectorProperty.get() != null;
        efficiencyToggle.setDisable(coinControl);
        privacyToggle.setDisable(coinControl);
    }

    private boolean isValidAddressAndLabel() {
        return isValidDepositAddress() && label.getText() != null && !label.getText().isBlank();
    }

    private long getAvailableBalanceSats() {
        UtxoSelector utxoSelector = utxoSelectorProperty.get();
        if(utxoSelector instanceof PresetUtxoSelector presetUtxoSelector) {
            return presetUtxoSelector.getPresetUtxos().stream().mapToLong(BlockTransactionHashIndex::getValue).sum();
        }
        return getWalletForm().getWallet().getSpendableUtxos().keySet().stream().mapToLong(BlockTransactionHashIndex::getValue).sum();
    }

    private void updatePrivacyAnalysis(WalletTransaction walletTransaction) {
        if(walletTransaction == null) {
            privacyAnalysis.setVisible(false);
            privacyAnalysis.setTooltip(null);
        } else {
            privacyAnalysis.setVisible(true);
            Tooltip tooltip = new Tooltip();
            tooltip.setShowDelay(new Duration(50));
            tooltip.setShowDuration(Duration.INDEFINITE);
            OptimizationStrategy optimizationStrategy = (OptimizationStrategy)optimizationToggleGroup.getSelectedToggle().getUserData();
            tooltip.setGraphic(new DepositPrivacyAnalysisTooltip(walletTransaction, getWalletForm().getWallet(), optimizationStrategy, utxoSelectorProperty.get()));
            privacyAnalysis.setTooltip(tooltip);
        }
    }

    private boolean isMaxButtonEnabled() {
        long balanceSats = getAvailableBalanceSats();
        if(balanceSats <= 0) {
            if(log.isDebugEnabled()) {
                log.debug("Deposit max disabled: no spendable UTXOs");
            }
            return false;
        }
        if(StrataBridgeParametersService.getInstance().getDepositUtxoAmountSats().isEmpty()) {
            if(log.isDebugEnabled()) {
                log.debug("Deposit max disabled: deposit denomination unavailable for network {}", Network.get());
            }
            return false;
        }
        boolean enabled = isValidAddressAndLabel() || utxoSelectorProperty.get() instanceof PresetUtxoSelector;
        if(log.isDebugEnabled() && !enabled) {
            log.debug("Deposit max disabled: valid address and label required (addressValid={}, labelPresent={})",
                    isValidDepositAddress(), label.getText() != null && !label.getText().isBlank());
        }
        return enabled;
    }

    private void updateMaxButton() {
        maxButton.setDisable(!isMaxButtonEnabled());
    }

    private boolean applyMaxAmountFromBalance(long balanceSats, boolean selectMaxToggle) {
        OptionalLong depositUtxoAmountSats = StrataBridgeParametersService.getInstance().getDepositUtxoAmountSats();
        if(balanceSats <= 0) {
            log.warn("Deposit max: no spendable balance");
            if(selectMaxToggle) {
                maxButton.setSelected(false);
            }
            return false;
        }
        if(depositUtxoAmountSats.isEmpty()) {
            log.warn("Deposit max: deposit denomination unavailable for network {}", Network.get());
            if(selectMaxToggle) {
                maxButton.setSelected(false);
            }
            return false;
        }

        Double sliderFeeRate = feeRateSection.getSliderFeeRate();
        long spendableBalance = balanceSats;
        long reservedForFees = 0;
        if(sliderFeeRate != null) {
            reservedForFees = estimateReservedSatsForDepositFees(sliderFeeRate);
            spendableBalance = Math.max(0, balanceSats - reservedForFees);
        }

        long maxAmount = DepositAmountValidator.largestValidAmount(spendableBalance, depositUtxoAmountSats.getAsLong(), StrataBridgeConstants.MAX_DEPOSIT_SATS);
        if(maxAmount > 0) {
            setAmountValueSats(maxAmount);
            if(selectMaxToggle) {
                maxButton.setSelected(true);
            }
            if(log.isDebugEnabled()) {
                log.debug("Deposit max: set amount to {} sats (balance {} sats, reserved {} sats, denomination {} sats)",
                        maxAmount, balanceSats, reservedForFees, depositUtxoAmountSats.getAsLong());
            }
            return true;
        }

        log.warn("Deposit max: insufficient funds after fees (balance {} sats, reserved {} sats, spendable {} sats, denomination {} sats)",
                balanceSats, reservedForFees, spendableBalance, depositUtxoAmountSats.getAsLong());
        if(selectMaxToggle) {
            maxButton.setSelected(false);
        }
        return false;
    }

    private List<UtxoSelector> getUtxoSelectors() {
        if(utxoSelectorProperty.get() != null) {
            return List.of(utxoSelectorProperty.get());
        }

        Wallet wallet = getWalletForm().getWallet();
        double feeRate = feeRateSection.getSliderFeeRate() != null ? feeRateSection.getSliderFeeRate() : getFallbackFeeRate();
        long bridgeOutputSats = amountSatsOrZero() + DepositFeeRates.calculateDepFee(feeRate);
        // Proxy receive address for output vsize estimation (bridge P2TR address not yet derived)
        long noInputsFee = wallet.getNoInputsFee(List.of(new Payment(wallet.getNode(KeyPurpose.RECEIVE).getAddress(), null, bridgeOutputSats, false)), feeRate);
        long costOfChange = wallet.getCostOfChange(feeRate, feeRateSection.getMinimumFeeRate());

        List<UtxoSelector> selectors = new ArrayList<>();
        OptimizationStrategy optimizationStrategy = (OptimizationStrategy)optimizationToggleGroup.getSelectedToggle().getUserData();
        if(optimizationStrategy == OptimizationStrategy.PRIVACY) {
            selectors.add(new StonewallUtxoSelector(getWalletForm().getWallet().getNode(KeyPurpose.RECEIVE).getAddress().getScriptType(), noInputsFee));
        }

        selectors.addAll(List.of(new BnBUtxoSelector(noInputsFee, costOfChange), new KnapsackUtxoSelector(noInputsFee)));
        return selectors;
    }

    private long amountSatsOrZero() {
        Long amountSats = getAmountValueSats();
        return amountSats == null ? 0L : amountSats;
    }

    private List<TxoFilter> getTxoFilters() {
        TxoFilter txoFilter = txoFilterProperty.get();
        if(txoFilter != null) {
            return List.of(txoFilter, new SpentTxoFilter(null), new FrozenTxoFilter(), new CoinbaseTxoFilter(getWalletForm().getWallet()));
        }

        return List.of(new SpentTxoFilter(null), new FrozenTxoFilter(), new CoinbaseTxoFilter(getWalletForm().getWallet()));
    }

    @FXML
    public void clear(ActionEvent event) {
        depositTo.setText("");
        label.setText("");
        depositAddressProperty.set(null);

        feeRateSection.incrementApplyingPreviewUpdates();
        try {
            amount.textProperty().removeListener(amountListener);
            amount.setText("");
            amount.textProperty().addListener(amountListener);
            emptyAmountProperty.set(true);
            setFiatAmount(null, null);
        } finally {
            feeRateSection.decrementApplyingPreviewUpdates();
        }

        utxoSelectorProperty.setValue(null);
        txoFilterProperty.setValue(null);
        excludedChangeNodes.clear();
        previewCoordinator.resetRecoveryKey();
        clearWalletTransactionPreview();
        getWalletForm().setCreatedWalletTransaction(null);

        clearFeeBuildMessageListener();
        feeRateSection.clearFee();
        insufficientInputsProperty.set(false);

        if(validationSupport != null) {
            validationSupport.setErrorDecorationEnabled(false);
        }

        feeRateSection.hideCpfp();
        feeRateSection.setDefaultFeeRate();
        maxButton.setSelected(false);
        updateOptimizationButtons();
        updateMaxButton();
        updatePrivacyAnalysis(null);
        applyTransactionDiagramState();
        updateConfirmButton();
        updateFee();
    }

    private void updateFee() {
        if(feeUpdatePause != null) {
            feeUpdatePause.stop();
        }

        if(maxButton.isSelected()) {
            applyMaxAmountFromBalance(getAvailableBalanceSats(), true);
        }

        DepositDescriptor descriptor = getDepositDescriptor();
        Long amountSats = tryParseAmountValueSats();
        Double sliderFeeRate = feeRateSection.getSliderFeeRate();

        if(descriptor == null || amountSats == null || amountSats <= 0 || sliderFeeRate == null) {
            if(log.isDebugEnabled() && maxButton.isSelected()) {
                log.debug("Deposit fee update skipped: descriptor={}, amountSats={}", descriptor != null, amountSats);
            }
            previewCoordinator.resetRecoveryKey();
            previewCoordinator.invalidatePreview();
            insufficientInputsProperty.set(false);
            if(!feeRateSection.isUserFeeSet()) {
                feeRateSection.clearFee();
            }
            updateConfirmButton();
            return;
        }

        boolean amountValidationFailed = getDepositAmountValidationError(amountSats).isPresent();
        Long userFee = feeRateSection.isUserFeeSet() ? feeRateSection.resolveMiningFeeFromTotal(sliderFeeRate) : null;
        boolean invalidCustomFee = feeRateSection.isUserFeeSet() && userFee == null;

        if(amountValidationFailed) {
            previewCoordinator.invalidatePreview();
            insufficientInputsProperty.set(false);
            if(!feeRateSection.isUserFeeSet()) {
                feeRateSection.clearFee();
            }
            applyTransactionDiagramState();
            updateConfirmButton();
            return;
        }

        if(invalidCustomFee) {
            previewCoordinator.invalidatePreview();
            insufficientInputsProperty.set(false);
            applyTransactionDiagramState();
            updateConfirmButton();
            return;
        }

        String depositLabel = label.getText() == null || label.getText().isBlank() ? "deposit" : label.getText();
        DepositFeeRequestKey requestKey = buildFeeRequestKey(descriptor, amountSats, sliderFeeRate, userFee, depositLabel);
        previewCoordinator.requestPreview(descriptor, amountSats, requestKey, false, invalidCustomFee, walletTransactionProperty.get());
        applyTransactionDiagramState();
        updateConfirmButton();
    }

    private void clearWalletTransactionPreview() {
        walletTransactionProperty.set(null);
        clearTransactionDiagram();
    }

    private void clearTransactionDiagram() {
        if(transactionDiagram.getWalletTransaction() != null || !transactionDiagram.getChildren().isEmpty()) {
            transactionDiagram.update((WalletTransaction)null);
        }
    }

    private boolean canDisplayTransactionDiagram() {
        Double sliderFeeRate = feeRateSection.getSliderFeeRate();
        Long miningFeeFromTotal = sliderFeeRate != null && feeRateSection.isUserFeeSet()
                ? feeRateSection.resolveMiningFeeFromTotal(sliderFeeRate) : null;
        return DepositConfirmGate.canDisplayDiagram(new DepositConfirmGate.DepositDiagramState(
                insufficientInputsProperty.get(),
                isValidDepositAddress(),
                label.getText() != null && !label.getText().isBlank(),
                getDepositDescriptor(),
                tryParseAmountValueSats(),
                sliderFeeRate,
                feeRateSection.isUserFeeSet(),
                miningFeeFromTotal,
                walletTransactionProperty.get(),
                previewCoordinator.getPreviewAmountSats()),
                AppServices.getMinimumRelayFeeRate());
    }

    private void applyTransactionDiagramState() {
        if(!canDisplayTransactionDiagram()) {
            clearTransactionDiagram();
            return;
        }
        updateTransactionDiagram(walletTransactionProperty.get());
    }

    private void updateTransactionDiagram(WalletTransaction walletTransaction) {
        WalletTransaction existing = transactionDiagram.getWalletTransaction();
        if(walletTransaction != null && isSameDiagramTransaction(existing, walletTransaction)) {
            return;
        }
        transactionDiagram.setOptimizationStrategy((OptimizationStrategy)optimizationToggleGroup.getSelectedToggle().getUserData());
        transactionDiagram.update(walletTransaction);
    }

    private boolean isSameDiagramTransaction(WalletTransaction existing, WalletTransaction updated) {
        if(existing == null || updated == null) {
            return false;
        }
        return existing.getSelectedUtxos().keySet().equals(updated.getSelectedUtxos().keySet()) && existing.getFee() == updated.getFee();
    }

    private void updateConfirmButton() {
        boolean validationInvalid = validationSupport != null && validationSupport.isInvalid();
        confirmButton.setDisable(!DepositConfirmGate.isConfirmEnabled(new DepositConfirmGate.DepositConfirmState(
                validationInvalid,
                depositTo.getText() != null && !depositTo.getText().isBlank(),
                label.getText() != null && !label.getText().isBlank(),
                amount.getText() != null && !amount.getText().isBlank(),
                StrataBridgeKeyVerificationService.getInstance().isDepositAllowed(),
                walletTransactionProperty.get(),
                AppServices.getMinimumRelayFeeRate(),
                previewCoordinator.isRunning())));
    }

    public boolean isInsufficientFeeRate() {
        return DepositConfirmGate.isInsufficientFeeRate(walletTransactionProperty.get(), AppServices.getMinimumRelayFeeRate());
    }

    private void revalidate(TextField field, ChangeListener<String> listener) {
        field.textProperty().removeListener(listener);
        String amt = field.getText();
        int caret = field.getCaretPosition();
        field.setText(amt + "0");
        field.setText(amt);
        field.positionCaret(caret);
        field.textProperty().addListener(listener);
    }

    @FXML
    public void scanQrAddress(ActionEvent event) {
        QRScanDialog qrScanDialog = new QRScanDialog();
        qrScanDialog.initOwner(scanQrButton.getScene().getWindow());
        Optional<QRScanDialog.Result> optionalResult = qrScanDialog.showAndWait();
        if(optionalResult.isPresent()) {
            QRScanDialog.Result result = optionalResult.get();
            if(result.payload != null) {
                depositTo.setText(result.payload);
            } else if(result.exception != null) {
                log.error("Error scanning QR", result.exception);
                AppServices.showErrorDialog("Error scanning QR", result.exception.getMessage());
            }
        }
    }

    @FXML
    public void setMaxAmount(ActionEvent event) {
        if(maxButton.isDisable()) {
            log.warn("Deposit max clicked while disabled (balance {} sats, addressValid={}, labelPresent={}, coinControl={})",
                    getAvailableBalanceSats(), isValidDepositAddress(), label.getText() != null && !label.getText().isBlank(),
                    utxoSelectorProperty.get() instanceof PresetUtxoSelector);
            return;
        }

        try {
            UtxoSelector utxoSelector = utxoSelectorProperty.get();
            if(utxoSelector == null) {
                if(log.isDebugEnabled()) {
                    log.debug("Deposit max: enabling MaxUtxoSelector");
                }
                utxoSelectorProperty.set(new MaxUtxoSelector());
            } else if(utxoSelector instanceof PresetUtxoSelector presetUtxoSelector && !isValidAddressAndLabel()) {
                long presetBalance = presetUtxoSelector.getPresetUtxos().stream().mapToLong(BlockTransactionHashIndex::getValue).sum();
                if(log.isDebugEnabled()) {
                    log.debug("Deposit max: applying amount from {} preset UTXOs ({} sats)", presetUtxoSelector.getPresetUtxos().size(), presetBalance);
                }
                if(!applyMaxAmountFromBalance(presetBalance, true)) {
                    log.warn("Deposit max: failed to apply amount from preset UTXOs");
                }
                updateFee();
                return;
            }

            long balance = getAvailableBalanceSats();
            if(log.isDebugEnabled()) {
                log.debug("Deposit max: applying amount from available balance {} sats (selector={})",
                        balance, utxoSelectorProperty.get() == null ? "none" : utxoSelectorProperty.get().getClass().getSimpleName());
            }
            if(!applyMaxAmountFromBalance(balance, true)) {
                log.warn("Deposit max: failed to apply amount from available balance {} sats", balance);
            }
            updateFee();
        } catch(Exception e) {
            maxButton.setSelected(false);
            log.error("Deposit max failed", e);
            AppServices.showErrorDialog("Deposit max failed", e.getMessage());
        }
    }

    private long estimateReservedSatsForDepositFees(double sliderFeeRate) {
        Long uiFee = feeRateSection.getFeeValueSats();
        if(uiFee != null && uiFee > 0) {
            return uiFee;
        }

        Wallet wallet = getWalletForm().getWallet();
        long depFee = DepositFeeRates.calculateDepFee(sliderFeeRate);
        long inputFee = (long)Math.ceil(wallet.getInputVbytes() * sliderFeeRate);
        long outputFee = (long)Math.ceil(ESTIMATED_DRT_OUTPUT_VBYTES * sliderFeeRate);
        long changeCost = wallet.getCostOfChange(sliderFeeRate, feeRateSection.getMinimumFeeRate());
        return depFee + inputFee + outputFee + changeCost;
    }

    @FXML
    public void confirm(ActionEvent event) {
        if(!StrataBridgeKeyVerificationService.getInstance().isDepositAllowed()) {
            AppServices.showErrorDialog("Deposit unavailable", StrataBridgeKeyVerificationService.getInstance().getMessage());
            return;
        }

        DepositDescriptor descriptor = getDepositDescriptor();
        if(descriptor == null) {
            AppServices.showErrorDialog("Invalid deposit", AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
            return;
        }

        Long amountSats = getAmountValueSats();
        Optional<String> amountError = getDepositAmountValidationError(amountSats);
        if(amountError.isPresent()) {
            AppServices.showErrorDialog("Invalid amount", amountError.get());
            return;
        }

        WalletTransaction walletTransaction = walletTransactionProperty.get();
        if(walletTransaction != null && !isInsufficientFeeRate()) {
            addWalletTransactionNodes(walletTransaction);
            getWalletForm().setCreatedWalletTransaction(walletTransaction);
            PSBT psbt = walletTransaction.createPSBT();
            DepositPsbtOrdering.align(psbt, walletTransaction);
            EventManager.get().post(new ViewPSBTEvent(confirmButton.getScene().getWindow(), label.getText(), null, psbt));
            return;
        }

        try {
            Double sliderFeeRate = feeRateSection.getSliderFeeRate();
            if(sliderFeeRate == null) {
                AppServices.showErrorDialog("Unknown fee rate", "Fee rates are not available. Check your connection and try again.");
                return;
            }

            Long userFee = feeRateSection.isUserFeeSet() ? feeRateSection.resolveMiningFeeFromTotal(sliderFeeRate) : null;
            if(feeRateSection.isUserFeeSet() && userFee == null) {
                AppServices.showErrorDialog("Invalid fee", "Mining fee must cover the deposit transaction fee.");
                return;
            }

            double minimumFeeRate = feeRateSection.getMinimumFeeRate();
            Wallet wallet = getWalletForm().getWallet();
            DepositRequestService service = new DepositRequestService(
                    wallet,
                    descriptor,
                    amountSats,
                    label.getText(),
                    feeRateSection.getSelectionFeeRate(),
                    sliderFeeRate,
                    minimumFeeRate,
                    AppServices.getMinimumRelayFeeRate(),
                    userFee,
                    AppServices.getCurrentBlockHeight(),
                    Config.get().isGroupByAddress(),
                    Config.get().isIncludeMempoolOutputs(),
                    getUtxoSelectors(),
                    excludedChangeNodes,
                    getTxoFilters()
            );

            DepositRequestService.DepositRequestResult result = service.createWalletTransaction();
            addWalletTransactionNodes(result.walletTransaction());
            getWalletForm().setCreatedWalletTransaction(result.walletTransaction());
            PSBT psbt = result.walletTransaction().createPSBT();
            DepositPsbtOrdering.align(psbt, result.walletTransaction());
            EventManager.get().post(new ViewPSBTEvent(confirmButton.getScene().getWindow(), label.getText(), null, psbt));
        } catch(InsufficientFundsException e) {
            AppServices.showErrorDialog("Insufficient funds", e.getMessage());
        } catch(DepositRequestException e) {
            log.error("Failed to create deposit request transaction", e);
            AppServices.showErrorDialog("Deposit failed", e.getMessage());
        } catch(IllegalStateException e) {
            log.error("Failed to create deposit request transaction", e);
            AppServices.showErrorDialog("Deposit unavailable", e.getMessage());
        }
    }

    private void addWalletTransactionNodes(WalletTransaction walletTransaction) {
        Set<WalletNode> nodes = new LinkedHashSet<>(walletTransaction.getSelectedUtxos().values());
        nodes.addAll(walletTransaction.getChangeMap().keySet());
        nodes.addAll(walletTransaction.getWalletNodePayments().stream().map(WalletNodePayment::getWalletNode).collect(Collectors.toList()));
        getWalletForm().addWalletTransactionNodes(nodes);
    }

    private BitcoinUnit getBitcoinUnit(BitcoinUnit bitcoinUnit) {
        BitcoinUnit unit = bitcoinUnit;
        if(unit == null || unit.equals(BitcoinUnit.AUTO)) {
            unit = getWalletForm().getWallet().getAutoUnit();
        }
        return unit;
    }

    private Long getAmountValueSats() {
        return getAmountValueSats(amountUnit.getSelectionModel().getSelectedItem());
    }

    private Long tryParseAmountValueSats() {
        try {
            return getAmountValueSats();
        } catch(NumberFormatException e) {
            return null;
        }
    }

    private Long getAmountValueSats(BitcoinUnit bitcoinUnit) {
        if(amount.getText() != null && !amount.getText().isEmpty()) {
            UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
            double fieldValue = Double.parseDouble(amount.getText().replaceAll(Pattern.quote(format.getGroupingSeparator()), "").replaceAll(",", "."));
            return bitcoinUnit.getSatsValue(fieldValue);
        }
        return null;
    }

    private void setAmountValueSats(long amountValue) {
        feeRateSection.incrementApplyingPreviewUpdates();
        try {
            amount.textProperty().removeListener(amountListener);
            UnitFormat unitFormat = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
            DecimalFormat df = new DecimalFormat("#.#", unitFormat.getDecimalFormatSymbols());
            df.setMaximumFractionDigits(8);
            amount.setText(df.format(amountUnit.getValue().getValue(amountValue)));
            amount.textProperty().addListener(amountListener);
            emptyAmountProperty.set(false);
            setFiatAmount(AppServices.getFiatCurrencyExchangeRate(), amountValue);
            updateConfirmButton();
        } finally {
            feeRateSection.decrementApplyingPreviewUpdates();
        }
    }

    private void setFiatAmount(CurrencyRate currencyRate, Long value) {
        if(value != null && value > 0) {
            fiatAmount.set(currencyRate, value);
        } else {
            fiatAmount.setCurrency(null);
            fiatAmount.setBtcRate(0.0);
        }
    }

    @Subscribe
    public void unitFormatChanged(UnitFormatChangedEvent event) {
        applyTransactionDiagramState();
        if(amount.getTextFormatter() instanceof CoinTextFormatter coinTextFormatter && coinTextFormatter.getUnitFormat() != event.getUnitFormat()) {
            UnitFormat format = coinTextFormatter.getUnitFormat() == null ? UnitFormat.DOT : coinTextFormatter.getUnitFormat();
            Long value = getAmountValueSats(format, amountUnit.getSelectionModel().getSelectedItem());
            amount.setTextFormatter(new CoinTextFormatter(event.getUnitFormat()));
            if(value != null) {
                setAmountValueSats(value);
            }
        }
        fiatAmount.refresh(event.getUnitFormat());
        feeRateSection.refreshFiatFeeAmount(event.getUnitFormat());
    }

    @Subscribe
    public void connectionEvent(ConnectionEvent event) {
        StrataBridgeParametersService.getInstance().refresh();
        StrataBridgeKeyVerificationService.getInstance().refresh();
        Platform.runLater(this::updateBridgeLinks);
    }

    @Subscribe
    public void strataBridgeKeyVerificationUpdated(StrataBridgeKeyVerificationUpdatedEvent event) {
        Platform.runLater(() -> {
            updateConfirmButton();
        });
    }

    @Subscribe
    public void strataBridgeParametersUpdated(StrataBridgeParametersUpdatedEvent event) {
        Platform.runLater(() -> {
            revalidate(amount, amountListener);
            updateConfirmButton();
            updateMaxButton();
            updateFee();
        });
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.fromThisOrNested(getWalletForm().getWallet()) && getWalletForm().getCreatedWalletTransaction() != null) {
            Platform.runLater(() -> {
                if(getWalletForm().getCreatedWalletTransaction().getSelectedUtxos() != null && allSelectedUtxosSpent(event.getAllHistoryChangedNodes())) {
                    clear(null);
                } else {
                    updateMaxButton();
                    updateFee();
                }
            });
        } else if(event.fromThisOrNested(getWalletForm().getWallet())) {
            Platform.runLater(() -> {
                updateMaxButton();
                if(maxButton.isSelected()) {
                    updateFee();
                }
            });
        }
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            Platform.runLater(() -> clear(null));
        }
    }

    private boolean allSelectedUtxosSpent(List<WalletNode> historyChangedNodes) {
        Set<BlockTransactionHashIndex> unspentUtxos = new HashSet<>(getWalletForm().getCreatedWalletTransaction().getSelectedUtxos().keySet());

        for(Map.Entry<BlockTransactionHashIndex, WalletNode> selectedUtxoEntry : getWalletForm().getCreatedWalletTransaction().getSelectedUtxos().entrySet()) {
            BlockTransactionHashIndex utxo = selectedUtxoEntry.getKey();
            WalletNode utxoWalletNode = selectedUtxoEntry.getValue();

            for(WalletNode changedNode : historyChangedNodes) {
                if(utxoWalletNode.equals(changedNode)) {
                    Optional<BlockTransactionHashIndex> spentTxo = changedNode.getTransactionOutputs().stream().filter(txo -> txo.getHash().equals(utxo.getHash()) && txo.getIndex() == utxo.getIndex() && txo.isSpent()).findAny();
                    if(spentTxo.isPresent()) {
                        unspentUtxos.remove(utxo);
                    }
                }
            }
        }

        return unspentUtxos.isEmpty();
    }

    @Subscribe
    public void depositSpendUtxos(DepositSpendUtxoEvent event) {
        if(event.getUtxos() != null && !event.getUtxos().isEmpty() && event.getWallet().equals(getWalletForm().getWallet())) {
            utxoSelectorProperty.set(new PresetUtxoSelector(event.getUtxos(), false, false));
            txoFilterProperty.set(null);
            long balance = event.getUtxos().stream().mapToLong(BlockTransactionHashIndex::getValue).sum();
            applyMaxAmountFromBalance(balance, true);
            updateOptimizationButtons();
            updateMaxButton();
            updateFee();
        }
    }

    @Subscribe
    public void excludeUtxo(ExcludeUtxoEvent event) {
        if(event.getWalletTransaction() == walletTransactionProperty.get()) {
            UtxoSelector utxoSelector = utxoSelectorProperty.get();
            if(utxoSelector instanceof MaxUtxoSelector) {
                Collection<BlockTransactionHashIndex> utxos = event.getWalletTransaction().getSelectedUtxos().keySet();
                utxos.remove(event.getUtxo());
                PresetUtxoSelector presetUtxoSelector = new PresetUtxoSelector(utxos);
                presetUtxoSelector.getExcludedUtxos().add(event.getUtxo());
                utxoSelectorProperty.set(presetUtxoSelector);
                updateFee();
            } else if(utxoSelector instanceof PresetUtxoSelector existingUtxoSelector) {
                PresetUtxoSelector presetUtxoSelector = new PresetUtxoSelector(existingUtxoSelector.getPresetUtxos(), existingUtxoSelector.getExcludedUtxos());
                presetUtxoSelector.getPresetUtxos().remove(event.getUtxo());
                presetUtxoSelector.getExcludedUtxos().add(event.getUtxo());
                utxoSelectorProperty.set(presetUtxoSelector);
                updateFee();
            } else {
                ExcludeTxoFilter excludeTxoFilter = new ExcludeTxoFilter();
                if(txoFilterProperty.get() instanceof ExcludeTxoFilter existingTxoFilter) {
                    excludeTxoFilter.getExcludedTxos().addAll(existingTxoFilter.getExcludedTxos());
                }

                excludeTxoFilter.getExcludedTxos().add(event.getUtxo());
                txoFilterProperty.set(excludeTxoFilter);
                updateFee();
            }
        }
    }

    @Subscribe
    public void replaceChangeAddress(ReplaceChangeAddressEvent event) {
        if(event.getWalletTransaction() == walletTransactionProperty.get()) {
            excludedChangeNodes.addAll(event.getWalletTransaction().getChangeMap().keySet());
            updateFee();
        }
    }

    @Subscribe
    public void walletUtxoStatusChanged(WalletUtxoStatusChangedEvent event) {
        if(event.fromThisOrNested(getWalletForm().getWallet())) {
            UtxoSelector utxoSelector = utxoSelectorProperty.get();
            if(utxoSelector instanceof MaxUtxoSelector) {
                updateFee();
            } else if(utxoSelector instanceof PresetUtxoSelector presetUtxoSelector) {
                PresetUtxoSelector updated = new PresetUtxoSelector(presetUtxoSelector.getPresetUtxos());
                updated.getPresetUtxos().removeAll(event.getUtxos());
                utxoSelectorProperty.set(updated);
                updateFee();
            } else {
                updateFee();
            }
        }
    }

    @Subscribe
    public void fiatCurrencySelected(FiatCurrencySelectedEvent event) {
        if(event.getExchangeSource() == ExchangeSource.NONE) {
            feeRateSection.clearFiatFeeAmount();
        } else {
            feeRateSection.setFiatFeeAmount(AppServices.getFiatCurrencyExchangeRate(), feeRateSection.getFeeValueSats());
        }
    }

    @Subscribe
    public void exchangeRatesUpdated(ExchangeRatesUpdatedEvent event) {
        setFiatAmount(event.getCurrencyRate(), getAmountValueSats());
        feeRateSection.setFiatFeeAmount(event.getCurrencyRate(), feeRateSection.getFeeValueSats());
        feeRateSection.refreshFiatFeeAmount();
    }

    @Subscribe
    public void hideAmountsStatusChanged(HideAmountsStatusEvent event) {
        feeRateSection.refreshFiatFeeAmount();
        fiatAmount.refresh();
    }

    private Long getAmountValueSats(UnitFormat unitFormat, BitcoinUnit bitcoinUnit) {
        if(amount.getText() != null && !amount.getText().isEmpty()) {
            UnitFormat format = unitFormat == null ? UnitFormat.DOT : unitFormat;
            double fieldValue = Double.parseDouble(amount.getText().replaceAll(Pattern.quote(format.getGroupingSeparator()), "").replaceAll(",", "."));
            return bitcoinUnit.getSatsValue(fieldValue);
        }
        return null;
    }
}
