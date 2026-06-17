package com.sparrowwallet.sparrow.strata.deposit;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
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
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.*;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import com.sparrowwallet.sparrow.net.FeeRatesSource;
import com.sparrowwallet.sparrow.net.MempoolRateSize;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeKeyVerificationService;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeParametersService;
import com.sparrowwallet.sparrow.strata.model.AlpenAddressParseResult;
import com.sparrowwallet.sparrow.strata.model.AlpenAddressParser;
import com.sparrowwallet.sparrow.strata.model.AlpenConstants;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import com.sparrowwallet.sparrow.wallet.FeeRatesSelection;
import com.sparrowwallet.sparrow.wallet.OptimizationStrategy;
import com.sparrowwallet.sparrow.wallet.WalletFormController;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
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

    private final ObjectProperty<AlpenAddressParseResult> depositAddressProperty = new SimpleObjectProperty<>(null);

    private final SimpleBooleanProperty userFeeSet = new SimpleBooleanProperty(false);

    private final ObjectProperty<FeeRatesSelection> feeRatesSelectionProperty = new SimpleObjectProperty<>(null);

    private boolean updateDefaultFeeRate;

    private DepositFeeService depositFeeService;

    private PauseTransition feeUpdatePause;

    private RecoveryKeyPair previewRecoveryKeyPair;

    private DepositDescriptor lastPreviewDescriptor;

    private Long lastPreviewAmountSats;

    private FeeRequestKey lastCompletedFeeRequest;

    private FeeRequestKey inFlightFeeRequest;

    private Long previewAmountSats;

    private int applyingPreviewUpdates;

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
            if(applyingPreviewUpdates > 0) {
                return;
            }
            emptyAmountProperty.set(newValue == null || newValue.isEmpty());
            if(utxoSelectorProperty.get() instanceof MaxUtxoSelector) {
                utxoSelectorProperty.setValue(null);
            }
            maxButton.setSelected(false);
            userFeeSet.set(false);
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
            if(applyingPreviewUpdates > 0) {
                return;
            }
            validationSupport.setErrorDecorationEnabled(true);
            if(newValue == null || newValue.isEmpty()) {
                userFeeSet.set(false);
                clearFiatFeeAmount();
            } else {
                userFeeSet.set(true);
                setFiatFeeAmount(AppServices.getFiatCurrencyExchangeRate(), getFeeValueSats());
            }
            updateFee();
        }
    };

    private final ChangeListener<Number> targetBlocksListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
            Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
            Integer target = getTargetBlocks();

            if(targetBlocksFeeRates != null) {
                setFeeRate(targetBlocksFeeRates.get(target));
                blockTargetFeeRatesChart.select(target);
            } else {
                feeRate.setText("Unknown");
            }

            targetBlocks.setTooltip(new Tooltip("Target inclusion within " + target + " blocks"));
            userFeeSet.set(false);
            scheduleUpdateFee();
        }
    };

    private final ChangeListener<Number> feeRangeListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
            setFeeRate(getFeeRangeRate());
            userFeeSet.set(false);
            scheduleUpdateFee();
        }
    };

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        addValidation();
        initializeAmountFields();
        initializeFeeSection();
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
                (Control c, String newValue) -> ValidationResult.fromErrorIf(c, "Insufficient Inputs", userFeeSet.get() && insufficientInputsProperty.get()),
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

    private record FeeRequestKey(DepositDescriptor descriptor, long amountSats, double depFeeRate, double userFeeRate, Long userFee,
                                 String label, OptimizationStrategy optimizationStrategy, int coinControlHash) {
    }

    private FeeRequestKey buildFeeRequestKey(DepositDescriptor descriptor, long amountSats, double depFeeRate, Long userFee, String depositLabel) {
        OptimizationStrategy optimizationStrategy = (OptimizationStrategy)optimizationToggleGroup.getSelectedToggle().getUserData();
        int coinControlHash = Objects.hash(utxoSelectorProperty.get(), txoFilterProperty.get(), excludedChangeNodes);
        return new FeeRequestKey(descriptor, amountSats, depFeeRate, getUserFeeRate(), userFee, depositLabel, optimizationStrategy, coinControlHash);
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

    private void initializeFeeSection() {
        targetBlocksField.managedProperty().bind(targetBlocksField.visibleProperty());
        targetBlocks.setMin(0);
        targetBlocks.setMax(TARGET_BLOCKS_RANGE.size() - 1);
        targetBlocks.setMajorTickUnit(1);
        targetBlocks.setMinorTickCount(0);
        targetBlocks.setLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Double object) {
                String blocks = Integer.toString(TARGET_BLOCKS_RANGE.get(object.intValue()));
                return (object.intValue() == TARGET_BLOCKS_RANGE.size() - 1) ? blocks + "+" : blocks;
            }

            @Override
            public Double fromString(String string) {
                return (double)TARGET_BLOCKS_RANGE.indexOf(Integer.valueOf(string.replace("+", "")));
            }
        });
        targetBlocks.valueProperty().addListener(targetBlocksListener);

        feeRangeField.managedProperty().bind(feeRangeField.visibleProperty());
        feeRangeField.visibleProperty().bind(targetBlocksField.visibleProperty().not());
        feeRange.valueProperty().addListener(feeRangeListener);

        blockTargetFeeRatesChart.managedProperty().bind(blockTargetFeeRatesChart.visibleProperty());
        blockTargetFeeRatesChart.visibleProperty().bind(Bindings.equal(feeRatesSelectionProperty, FeeRatesSelection.BLOCK_TARGET));
        blockTargetFeeRatesChart.initialize();
        Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
        if(targetBlocksFeeRates != null) {
            blockTargetFeeRatesChart.update(targetBlocksFeeRates);
        } else {
            feeRate.setText("Unknown");
        }

        mempoolSizeFeeRatesChart.managedProperty().bind(mempoolSizeFeeRatesChart.visibleProperty());
        mempoolSizeFeeRatesChart.visibleProperty().bind(Bindings.equal(feeRatesSelectionProperty, FeeRatesSelection.MEMPOOL_SIZE));
        mempoolSizeFeeRatesChart.initialize();
        Map<Date, Set<MempoolRateSize>> mempoolHistogram = getMempoolHistogram();
        if(mempoolHistogram != null) {
            mempoolSizeFeeRatesChart.update(mempoolHistogram);
        }

        recentBlocksView.managedProperty().bind(recentBlocksView.visibleProperty());
        recentBlocksView.visibleProperty().bind(Bindings.equal(feeRatesSelectionProperty, FeeRatesSelection.RECENT_BLOCKS));
        List<BlockSummary> blockSummaries = AppServices.getBlockSummaries().values().stream().sorted().toList();
        if(!blockSummaries.isEmpty()) {
            recentBlocksView.update(blockSummaries, AppServices.getNextBlockMedianFeeRate());
        }

        feeRatesSelectionProperty.addListener((observable, oldValue, newValue) -> {
            boolean isBlockTargetSelection = (newValue == FeeRatesSelection.BLOCK_TARGET);
            boolean wasBlockTargetSelection = (oldValue == FeeRatesSelection.BLOCK_TARGET || oldValue == null);
            targetBlocksField.setVisible(isBlockTargetSelection);
            if(isBlockTargetSelection) {
                setTargetBlocks(getTargetBlocks(getFeeRangeRate()));
                updateFee();
            } else if(wasBlockTargetSelection) {
                setFeeRangeRate(getTargetBlocksFeeRates().get(getTargetBlocks()));
                updateFee();
            }
        });

        FeeRatesSelection feeRatesSelection = Config.get().getFeeRatesSelection();
        feeRatesSelection = (feeRatesSelection == null ? FeeRatesSelection.RECENT_BLOCKS : feeRatesSelection);
        cpfpFeeRate.managedProperty().bind(cpfpFeeRate.visibleProperty());
        cpfpFeeRate.setVisible(false);
        setDefaultFeeRate();
        feeRatesSelectionProperty.set(feeRatesSelection);
        feeSelectionToggleGroup.selectToggle(feeRatesSelection == FeeRatesSelection.BLOCK_TARGET ? targetBlocksToggle :
                (feeRatesSelection == FeeRatesSelection.MEMPOOL_SIZE ? mempoolSizeToggle : recentBlocksToggle));
        feeSelectionToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null) {
                FeeRatesSelection newFeeRatesSelection = (FeeRatesSelection)newValue.getUserData();
                Config.get().setFeeRatesSelection(newFeeRatesSelection);
                EventManager.get().post(new FeeRatesSelectionChangedEvent(getWalletForm().getWallet(), newFeeRatesSelection));
            }
        });

        fee.setTextFormatter(new CoinTextFormatter(Config.get().getUnitFormat()));
        fee.textProperty().addListener(feeListener);

        BitcoinUnit unit = getBitcoinUnit(Config.get().getBitcoinUnit());
        feeAmountUnit.getSelectionModel().select(BitcoinUnit.BTC.equals(unit) ? 0 : 1);
        feeAmountUnit.valueProperty().addListener((observable, oldValue, newValue) -> {
            Long value = getFeeValueSats(oldValue);
            if(value != null) {
                setFeeValueSats(value);
            }
        });
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
            applyingPreviewUpdates++;
            try {
                if(walletTransaction != null) {
                    applyWalletTransactionFees(walletTransaction);
                }
                setEffectiveFeeRate(walletTransaction);
                updatePrivacyAnalysis(walletTransaction);
            } finally {
                applyingPreviewUpdates--;
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

        Double feeRate = getFeeRate();
        long spendableBalance = balanceSats;
        long reservedForFees = 0;
        if(feeRate != null) {
            reservedForFees = estimateReservedSatsForDepositFees(feeRate);
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
        double feeRate = getFeeRate() != null ? getFeeRate() : getFallbackFeeRate();
        long bridgeOutputSats = amountSatsOrZero() + DepositTransactionFeeEstimator.calculateDepFee(feeRate);
        // Proxy receive address for output vsize estimation (bridge P2TR address not yet derived)
        long noInputsFee = wallet.getNoInputsFee(List.of(new Payment(wallet.getNode(KeyPurpose.RECEIVE).getAddress(), null, bridgeOutputSats, false)), feeRate);
        long costOfChange = wallet.getCostOfChange(feeRate, getMinimumFeeRate());

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

        applyingPreviewUpdates++;
        try {
            amount.textProperty().removeListener(amountListener);
            amount.setText("");
            amount.textProperty().addListener(amountListener);
            emptyAmountProperty.set(true);
            setFiatAmount(null, null);
        } finally {
            applyingPreviewUpdates--;
        }

        utxoSelectorProperty.setValue(null);
        txoFilterProperty.setValue(null);
        excludedChangeNodes.clear();
        resetPreviewRecoveryKey();
        clearWalletTransactionPreview();
        getWalletForm().setCreatedWalletTransaction(null);

        userFeeSet.set(false);
        clearFeeBuildMessageListener();
        clearFee();
        insufficientInputsProperty.set(false);

        if(validationSupport != null) {
            validationSupport.setErrorDecorationEnabled(false);
        }

        cpfpFeeRate.setVisible(false);
        setDefaultFeeRate();
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
        Double feeRate = getFeeRate();

        if(descriptor == null || amountSats == null || amountSats <= 0 || feeRate == null) {
            if(log.isDebugEnabled() && maxButton.isSelected()) {
                log.debug("Deposit fee update skipped: descriptor={}, amountSats={}", descriptor != null, amountSats);
            }
            resetPreviewRecoveryKey();
            invalidateDepositPreview();
            insufficientInputsProperty.set(false);
            if(!userFeeSet.get()) {
                clearFee();
            }
            updateConfirmButton();
            return;
        }

        if(getDepositAmountValidationError(amountSats).isPresent()) {
            invalidateDepositPreview();
            insufficientInputsProperty.set(false);
            if(!userFeeSet.get()) {
                clearFee();
            }
            applyTransactionDiagramState();
            updateConfirmButton();
            return;
        }

        Long userFee = userFeeSet.get() ? getDepositRequestFeeSats(feeRate) : null;
        if(userFeeSet.get() && userFee == null) {
            invalidateDepositPreview();
            insufficientInputsProperty.set(false);
            applyTransactionDiagramState();
            updateConfirmButton();
            return;
        }

        String depositLabel = label.getText() == null || label.getText().isBlank() ? "deposit" : label.getText();
        FeeRequestKey requestKey = buildFeeRequestKey(descriptor, amountSats, feeRate, userFee, depositLabel);
        if(requestKey.equals(lastCompletedFeeRequest) && Objects.equals(previewAmountSats, amountSats) && walletTransactionProperty.get() != null) {
            setFiatFeeAmount(AppServices.getFiatCurrencyExchangeRate(), getFeeValueSats());
            applyTransactionDiagramState();
            updateConfirmButton();
            return;
        }
        if(depositFeeService != null && depositFeeService.isRunning() && requestKey.equals(inFlightFeeRequest)) {
            applyTransactionDiagramState();
            updateConfirmButton();
            return;
        }

        if(depositFeeService != null && depositFeeService.isRunning()) {
            depositFeeService.setIgnoreResult(true);
            depositFeeService.cancel();
        }

        syncPreviewRecoveryKey(descriptor, amountSats);
        startDepositFeeService(requestKey, depositLabel);
    }

    private boolean matchesCurrentFeeRequest(FeeRequestKey requestKey) {
        DepositDescriptor descriptor = getDepositDescriptor();
        Long amountSats = tryParseAmountValueSats();
        Double feeRate = getFeeRate();
        if(descriptor == null || amountSats == null || feeRate == null) {
            return false;
        }
        Long userFee = userFeeSet.get() ? getDepositRequestFeeSats(feeRate) : null;
        if(userFeeSet.get() && userFee == null) {
            return false;
        }
        String depositLabel = label.getText() == null || label.getText().isBlank() ? "deposit" : label.getText();
        FeeRequestKey current = buildFeeRequestKey(descriptor, amountSats, feeRate, userFee, depositLabel);
        return current.equals(requestKey);
    }

    private void startDepositFeeService(FeeRequestKey requestKey, String depositLabel) {
        inFlightFeeRequest = requestKey;
        Wallet wallet = getWalletForm().getWallet();
        depositFeeService = new DepositFeeService(
                wallet,
                requestKey.descriptor(),
                requestKey.amountSats(),
                depositLabel,
                requestKey.userFeeRate(),
                requestKey.depFeeRate(),
                getMinimumFeeRate(),
                AppServices.getMinimumRelayFeeRate(),
                requestKey.userFee(),
                AppServices.getCurrentBlockHeight(),
                Config.get().isGroupByAddress(),
                Config.get().isIncludeMempoolOutputs(),
                getUtxoSelectors(),
                excludedChangeNodes,
                getTxoFilters(),
                previewRecoveryKeyPair
        );

        final DepositFeeService currentService = depositFeeService;
        final FeeRequestKey requestedKey = requestKey;
        depositFeeService.setOnSucceeded(event -> {
            if(!currentService.isIgnoreResult() && matchesCurrentFeeRequest(requestedKey)) {
                WalletTransaction walletTransaction = currentService.getValue();
                insufficientInputsProperty.set(false);
                lastCompletedFeeRequest = requestedKey;
                inFlightFeeRequest = null;
                previewAmountSats = requestedKey.amountSats();
                walletTransactionProperty.setValue(walletTransaction);
                applyTransactionDiagramState();
                revalidate(amount, amountListener);
                revalidate(fee, feeListener);
            }
            updateConfirmButton();
        });
        depositFeeService.setOnFailed(event -> {
            if(!currentService.isIgnoreResult() && matchesCurrentFeeRequest(requestedKey)) {
                inFlightFeeRequest = null;
                lastCompletedFeeRequest = null;
                clearWalletTransactionPreview();
                if(event.getSource().getException() instanceof InsufficientFundsException) {
                    insufficientInputsProperty.set(true);
                } else {
                    insufficientInputsProperty.set(false);
                }
                revalidate(amount, amountListener);
                revalidate(fee, feeListener);
                applyTransactionDiagramState();
            }
            updateConfirmButton();
        });

        depositFeeService.start();
    }

    private void syncPreviewRecoveryKey(DepositDescriptor descriptor, long amountSats) {
        if(!Objects.equals(descriptor, lastPreviewDescriptor) || !Objects.equals(amountSats, lastPreviewAmountSats)) {
            resetPreviewRecoveryKey();
            lastPreviewDescriptor = descriptor;
            lastPreviewAmountSats = amountSats;
        }
        if(previewRecoveryKeyPair == null) {
            previewRecoveryKeyPair = RecoveryKeyPair.generate();
        }
    }

    private void resetPreviewRecoveryKey() {
        previewRecoveryKeyPair = null;
        lastPreviewDescriptor = null;
        lastPreviewAmountSats = null;
        lastCompletedFeeRequest = null;
        inFlightFeeRequest = null;
        previewAmountSats = null;
    }

    private void clearWalletTransactionPreview() {
        walletTransactionProperty.set(null);
        clearTransactionDiagram();
    }

    private void invalidateDepositPreview() {
        lastCompletedFeeRequest = null;
        inFlightFeeRequest = null;
        previewAmountSats = null;
        if(depositFeeService != null && depositFeeService.isRunning()) {
            depositFeeService.setIgnoreResult(true);
            depositFeeService.cancel();
        }
        clearWalletTransactionPreview();
    }

    private void clearTransactionDiagram() {
        if(transactionDiagram.getWalletTransaction() != null || !transactionDiagram.getChildren().isEmpty()) {
            transactionDiagram.update((WalletTransaction)null);
        }
    }

    private boolean canDisplayTransactionDiagram() {
        if(insufficientInputsProperty.get()) {
            return false;
        }
        if(!isValidDepositAddress()) {
            return false;
        }
        if(label.getText() == null || label.getText().isBlank()) {
            return false;
        }
        DepositDescriptor descriptor = getDepositDescriptor();
        Long amountSats = tryParseAmountValueSats();
        Double feeRate = getFeeRate();
        if(descriptor == null || amountSats == null || amountSats <= 0 || feeRate == null) {
            return false;
        }
        if(getDepositAmountValidationError(amountSats).isPresent()) {
            return false;
        }
        if(userFeeSet.get() && getDepositRequestFeeSats(feeRate) == null) {
            return false;
        }
        WalletTransaction walletTransaction = walletTransactionProperty.get();
        if(walletTransaction == null || !Objects.equals(previewAmountSats, amountSats)) {
            return false;
        }
        return !isInsufficientFeeRate();
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

    private void applyWalletTransactionFees(WalletTransaction walletTransaction) {
        if(!userFeeSet.get()) {
            Double feeRate = getFeeRate();
            if(feeRate != null) {
                setFeeValueSats(getTotalMiningFeeSats(walletTransaction.getFee(), feeRate));
            }
            setFeeRate(walletTransaction.getFeeRate());
        } else {
            setTargetBlocks(getTargetBlocks(walletTransaction.getFeeRate()));
            setFeeRangeRate(walletTransaction.getFeeRate());
            revalidate(fee, feeListener);
        }
    }

    private void clearFee() {
        applyingPreviewUpdates++;
        try {
            fee.textProperty().removeListener(feeListener);
            fee.setText("");
            fee.textProperty().addListener(feeListener);
            clearFiatFeeAmount();
            userFeeSet.set(false);
        } finally {
            applyingPreviewUpdates--;
        }
    }

    private void updateConfirmButton() {
        boolean fieldsValid = validationSupport != null && !validationSupport.isInvalid()
                && depositTo.getText() != null && !depositTo.getText().isBlank()
                && label.getText() != null && !label.getText().isBlank()
                && amount.getText() != null && !amount.getText().isBlank()
                && StrataBridgeKeyVerificationService.getInstance().isDepositAllowed();
        WalletTransaction walletTransaction = walletTransactionProperty.get();
        boolean previewReady = walletTransaction != null && !isInsufficientFeeRate();
        boolean notBuilding = depositFeeService == null || !depositFeeService.isRunning();
        confirmButton.setDisable(!fieldsValid || !previewReady || !notBuilding);
    }

    public boolean isInsufficientFeeRate() {
        return walletTransactionProperty.get() != null && walletTransactionProperty.get().getFeeRate() < AppServices.getMinimumRelayFeeRate();
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

    private void setEffectiveFeeRate(WalletTransaction walletTransaction) {
        List<BlockTransaction> unconfirmedUtxoTxs = walletTransaction == null ? Collections.emptyList() :
                walletTransaction.getSelectedUtxos().keySet().stream().filter(ref -> ref.getHeight() <= 0)
                        .map(ref -> getWalletForm().getWallet().getWalletTransaction(ref.getHash()))
                        .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if(!unconfirmedUtxoTxs.isEmpty() && unconfirmedUtxoTxs.stream().allMatch(blkTx -> blkTx.getFee() != null && blkTx.getFee() > 0)) {
            long utxoTxFee = unconfirmedUtxoTxs.stream().mapToLong(BlockTransaction::getFee).sum();
            double utxoTxSize = unconfirmedUtxoTxs.stream().mapToDouble(blkTx -> blkTx.getTransaction().getVirtualSize()).sum();
            long thisFee = walletTransaction.getFee();
            double thisSize = walletTransaction.getTransaction().getVirtualSize();
            double thisRate = thisFee / thisSize;
            double effectiveRate = (utxoTxFee + thisFee) / (utxoTxSize + thisSize);
            if(thisRate > effectiveRate) {
                UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
                String strEffectiveRate = format.getCurrencyFormat().format(effectiveRate);
                Tooltip tooltip = new Tooltip("CPFP (Child Pays For Parent)\n" + strEffectiveRate + " sats/vB effective rate");
                cpfpFeeRate.setTooltip(tooltip);
                cpfpFeeRate.setVisible(true);
                cpfpFeeRate.setText(strEffectiveRate + " sats/vB (CPFP)");
            } else {
                cpfpFeeRate.setVisible(false);
            }
        } else {
            cpfpFeeRate.setVisible(false);
        }
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
            tooltip.setGraphic(new PrivacyAnalysisTooltip(walletTransaction));
            privacyAnalysis.setTooltip(tooltip);
        }
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

    private long estimateReservedSatsForDepositFees(double feeRate) {
        Long uiFee = getFeeValueSats();
        if(uiFee != null && uiFee > 0) {
            return uiFee;
        }

        Wallet wallet = getWalletForm().getWallet();
        long depFee = getDepFeeSats(feeRate);
        long inputFee = (long)Math.ceil(wallet.getInputVbytes() * feeRate);
        long outputFee = (long)Math.ceil(ESTIMATED_DRT_OUTPUT_VBYTES * feeRate);
        long changeCost = wallet.getCostOfChange(feeRate, getMinimumFeeRate());
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
            Double feeRate = getFeeRate();
            if(feeRate == null) {
                AppServices.showErrorDialog("Unknown fee rate", "Fee rates are not available. Check your connection and try again.");
                return;
            }

            Long userFee = userFeeSet.get() ? getDepositRequestFeeSats(feeRate) : null;
            if(userFeeSet.get() && userFee == null) {
                AppServices.showErrorDialog("Invalid fee", "Mining fee must cover the deposit transaction fee.");
                return;
            }

            double minimumFeeRate = getMinimumFeeRate();
            Wallet wallet = getWalletForm().getWallet();
            DepositRequestService service = new DepositRequestService(
                    wallet,
                    descriptor,
                    amountSats,
                    label.getText(),
                    getUserFeeRate(),
                    feeRate,
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

    private long getDepFeeSats(double feeRate) {
        return DepositTransactionFeeEstimator.calculateDepFee(feeRate);
    }

    private long getTotalMiningFeeSats(long depositRequestFeeSats, double feeRate) {
        return depositRequestFeeSats + getDepFeeSats(feeRate);
    }

    private Long getDepositRequestFeeSats(double feeRate) {
        Long totalFee = getFeeValueSats();
        if(totalFee == null) {
            return null;
        }
        long depFee = getDepFeeSats(feeRate);
        if(totalFee < depFee) {
            return null;
        }
        return totalFee - depFee;
    }

    private Double getUserFeeRate() {
        return userFeeSet.get() ? AppServices.getMinimumRelayFeeRate() : getFeeRate();
    }

    private Double getFeeRate() {
        if(targetBlocksField.isVisible()) {
            return getTargetBlocksFeeRates().get(getTargetBlocks());
        }
        return getFeeRangeRate();
    }

    private Double getMinimumFeeRate() {
        Optional<Double> optMinFeeRate = getTargetBlocksFeeRates().values().stream().min(Double::compareTo);
        double minRate = optMinFeeRate.orElse(getFallbackFeeRate());
        Double userFeeRate = getFeeRate();
        if(userFeeRate != null) {
            minRate = Math.min(userFeeRate, minRate);
        }
        return Math.max(minRate, Transaction.DEFAULT_MIN_RELAY_FEE);
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
        applyingPreviewUpdates++;
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
            applyingPreviewUpdates--;
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

    private Long getFeeValueSats() {
        return getFeeValueSats(feeAmountUnit.getSelectionModel().getSelectedItem());
    }

    private Long getFeeValueSats(BitcoinUnit bitcoinUnit) {
        if(fee.getText() != null && !fee.getText().isEmpty()) {
            UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
            double fieldValue = Double.parseDouble(fee.getText().replaceAll(Pattern.quote(format.getGroupingSeparator()), "").replaceAll(",", "."));
            return bitcoinUnit.getSatsValue(fieldValue);
        }
        return null;
    }

    private void setFeeValueSats(long feeValue) {
        applyingPreviewUpdates++;
        try {
            fee.textProperty().removeListener(feeListener);
            UnitFormat unitFormat = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
            DecimalFormat df = new DecimalFormat("#.#", unitFormat.getDecimalFormatSymbols());
            df.setMaximumFractionDigits(8);
            fee.setText(df.format(feeAmountUnit.getValue().getValue(feeValue)));
            fee.textProperty().addListener(feeListener);
            setFiatFeeAmount(AppServices.getFiatCurrencyExchangeRate(), feeValue);
            fiatFeeAmount.refresh();
        } finally {
            applyingPreviewUpdates--;
        }
    }

    private void setFiatFeeAmount(CurrencyRate currencyRate, Long amount) {
        if(amount != null && amount > 0 && currencyRate != null && currencyRate.isAvailable() && Config.get().getExchangeSource() != ExchangeSource.NONE) {
            fiatFeeAmount.set(currencyRate, amount);
        }
    }

    private void clearFiatFeeAmount() {
        fiatFeeAmount.setValue(-1);
        fiatFeeAmount.setCurrency(null);
        fiatFeeAmount.setBtcRate(0.0);
    }

    private void setDefaultFeeRate() {
        int defaultTarget = TARGET_BLOCKS_RANGE.get((TARGET_BLOCKS_RANGE.size() / 2) - 1);
        int index = TARGET_BLOCKS_RANGE.indexOf(defaultTarget);
        Double defaultRate = getTargetBlocksFeeRates().get(defaultTarget);
        targetBlocks.setValue(index);
        blockTargetFeeRatesChart.select(defaultTarget);
        recentBlocksView.updateFeeRate(defaultRate);
        setFeeRangeRate(defaultRate);
        setFeeRate(getFeeRangeRate());
        if(Network.get().equals(Network.MAINNET) && defaultRate == getFallbackFeeRate()) {
            updateDefaultFeeRate = true;
        }
    }

    private Integer getTargetBlocks() {
        int index = (int)targetBlocks.getValue();
        return TARGET_BLOCKS_RANGE.get(index);
    }

    private Integer getTargetBlocks(double feeRateAmt) {
        Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
        int maxTargetBlocks = 1;
        for(Integer blocks : targetBlocksFeeRates.keySet()) {
            if(TARGET_BLOCKS_RANGE.contains(blocks)) {
                maxTargetBlocks = Math.max(maxTargetBlocks, blocks);
                Double candidate = targetBlocksFeeRates.get(blocks);
                if(Math.round(feeRateAmt) >= Math.round(candidate)) {
                    return blocks;
                }
            }
        }
        return maxTargetBlocks;
    }

    private void setTargetBlocks(Integer target) {
        targetBlocks.valueProperty().removeListener(targetBlocksListener);
        int index = TARGET_BLOCKS_RANGE.indexOf(target);
        targetBlocks.setValue(index);
        blockTargetFeeRatesChart.select(target);
        targetBlocks.setTooltip(new Tooltip("Target inclusion within " + target + " blocks"));
        targetBlocks.valueProperty().addListener(targetBlocksListener);
    }

    private Map<Integer, Double> getTargetBlocksFeeRates() {
        Map<Integer, Double> retrievedFeeRates = AppServices.getTargetBlockFeeRates();
        if(retrievedFeeRates == null) {
            retrievedFeeRates = TARGET_BLOCKS_RANGE.stream().collect(Collectors.toMap(java.util.function.Function.identity(), v -> getFallbackFeeRate(),
                    (u, v) -> { throw new IllegalStateException("Duplicate target blocks"); },
                    LinkedHashMap::new));
        }
        return retrievedFeeRates;
    }

    private Double getFeeRangeRate() {
        return feeRange.getFeeRate();
    }

    private void setFeeRangeRate(Double feeRateAmt) {
        feeRange.valueProperty().removeListener(feeRangeListener);
        feeRange.setFeeRate(feeRateAmt);
        feeRange.valueProperty().addListener(feeRangeListener);
    }

    private Map<Date, Set<MempoolRateSize>> getMempoolHistogram() {
        return AppServices.getMempoolHistogram();
    }

    private void setFeeRate(Double feeRateAmt) {
        UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
        feeRate.setText(format.getCurrencyFormat().format(feeRateAmt) + (cpfpFeeRate.isVisible() ? "" : " sats/vB"));
        setFeeRatePriority(feeRateAmt);
    }

    private void setFeeRatePriority(Double feeRateAmt) {
        Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
        if(targetBlocksFeeRates.get(Integer.MAX_VALUE) != null) {
            Double minFeeRate = targetBlocksFeeRates.get(Integer.MAX_VALUE);
            if(minFeeRate > 1.0 && feeRateAmt < minFeeRate) {
                feeRatePriority.setText("Below Minimum");
                feeRatePriority.setTooltip(new Tooltip("Transactions at this fee rate are currently being purged from the default sized mempool"));
                feeRatePriorityGlyph.setStyle("-fx-text-fill: #a0a1a7cc");
                feeRatePriorityGlyph.setIcon(FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
                return;
            }
        }

        Integer blocks = getTargetBlocks(feeRateAmt);
        if(blocks != null) {
            if(blocks < FeeRatesSource.BLOCKS_IN_HALF_HOUR) {
                feeRatePriority.setText("High Priority");
                feeRatePriority.setTooltip(new Tooltip("Typically confirms within minutes"));
                feeRatePriorityGlyph.setStyle("-fx-text-fill: #c8416499");
                feeRatePriorityGlyph.setIcon(FontAwesome5.Glyph.CIRCLE);
            } else if(blocks < FeeRatesSource.BLOCKS_IN_HOUR) {
                feeRatePriority.setText("Medium Priority");
                feeRatePriority.setTooltip(new Tooltip("Typically confirms within an hour or two"));
                feeRatePriorityGlyph.setStyle("-fx-text-fill: #fba71b99");
                feeRatePriorityGlyph.setIcon(FontAwesome5.Glyph.CIRCLE);
            } else {
                feeRatePriority.setText("Low Priority");
                feeRatePriority.setTooltip(new Tooltip("Typically confirms in a day or longer"));
                feeRatePriorityGlyph.setStyle("-fx-text-fill: #41a9c999");
                feeRatePriorityGlyph.setIcon(FontAwesome5.Glyph.CIRCLE);
            }
        }
    }

    @Subscribe
    public void feeRatesUpdated(FeeRatesUpdatedEvent event) {
        blockTargetFeeRatesChart.update(event.getTargetBlockFeeRates());
        blockTargetFeeRatesChart.select(getTargetBlocks());
        if(targetBlocksField.isVisible()) {
            setFeeRate(event.getTargetBlockFeeRates().get(getTargetBlocks()));
        } else {
            setFeeRatePriority(getFeeRangeRate());
        }
        feeRange.updateTrackHighlight();

        if(event.getNextBlockMedianFeeRate() != null) {
            recentBlocksView.updateFeeRate(event.getNextBlockMedianFeeRate());
        } else {
            recentBlocksView.updateFeeRate(event.getTargetBlockFeeRates());
        }

        if(updateDefaultFeeRate) {
            if(getFeeRangeRate() != null && Long.valueOf((long)getFallbackFeeRate()).equals(getFeeRangeRate().longValue())) {
                setDefaultFeeRate();
            }
            updateDefaultFeeRate = false;
        }
    }

    @Subscribe
    public void mempoolRateSizesUpdated(MempoolRateSizesUpdatedEvent event) {
        mempoolSizeFeeRatesChart.update(getMempoolHistogram());
    }

    @Subscribe
    public void blockSummary(BlockSummaryEvent event) {
        Platform.runLater(() -> recentBlocksView.update(AppServices.getBlockSummaries().values().stream().sorted().toList(), AppServices.getNextBlockMedianFeeRate()));
    }

    @Subscribe
    public void bitcoinUnitChanged(BitcoinUnitChangedEvent event) {
        BitcoinUnit unit = getBitcoinUnit(event.getBitcoinUnit());
        feeAmountUnit.getSelectionModel().select(BitcoinUnit.BTC.equals(unit) ? 0 : 1);
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
        fiatFeeAmount.refresh(event.getUnitFormat());
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
    public void feeRateSelectionChanged(FeeRatesSelectionChangedEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            feeRatesSelectionProperty.set(event.getFeeRateSelection());
            Toggle toggle = event.getFeeRateSelection() == FeeRatesSelection.BLOCK_TARGET ? targetBlocksToggle :
                    (event.getFeeRateSelection() == FeeRatesSelection.MEMPOOL_SIZE ? mempoolSizeToggle : recentBlocksToggle);
            if(feeSelectionToggleGroup.getSelectedToggle() != toggle) {
                feeSelectionToggleGroup.selectToggle(toggle);
            }
        }
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
            clearFiatFeeAmount();
        } else {
            setFiatFeeAmount(AppServices.getFiatCurrencyExchangeRate(), getFeeValueSats());
        }
    }

    @Subscribe
    public void exchangeRatesUpdated(ExchangeRatesUpdatedEvent event) {
        setFiatAmount(event.getCurrencyRate(), getAmountValueSats());
        setFiatFeeAmount(event.getCurrencyRate(), getFeeValueSats());
        fiatFeeAmount.refresh();
    }

    @Subscribe
    public void hideAmountsStatusChanged(HideAmountsStatusEvent event) {
        fiatFeeAmount.refresh();
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

    private static class DepositFeeService extends Service<WalletTransaction> {
        private final DepositRequestService depositRequestService;
        private boolean ignoreResult;

        public DepositFeeService(Wallet wallet, DepositDescriptor depositDescriptor, long amountSats, String label,
                                 double feeRate, double depFeeRate, double minimumFeeRate, double minRelayFeeRate,
                                 Long userFee, Integer currentBlockHeight, boolean groupByAddress, boolean includeMempoolOutputs,
                                 List<UtxoSelector> utxoSelectors, Set<WalletNode> excludedChangeNodes, List<TxoFilter> txoFilters,
                                 RecoveryKeyPair recoveryKeyPair) {
            this.depositRequestService = new DepositRequestService(wallet, depositDescriptor, amountSats, label,
                    feeRate, depFeeRate, minimumFeeRate, minRelayFeeRate, userFee, currentBlockHeight, groupByAddress, includeMempoolOutputs,
                    utxoSelectors, excludedChangeNodes, txoFilters, recoveryKeyPair);
        }

        @Override
        protected Task<WalletTransaction> createTask() {
            return new Task<>() {
                @Override
                protected WalletTransaction call() throws Exception {
                    try {
                        updateMessage("Selecting UTXOs...");
                        return depositRequestService.createWalletTransaction().walletTransaction();
                    } finally {
                        updateMessage("");
                    }
                }
            };
        }

        public boolean isIgnoreResult() {
            return ignoreResult;
        }

        public void setIgnoreResult(boolean ignoreResult) {
            this.ignoreResult = ignoreResult;
        }
    }

    private class PrivacyAnalysisTooltip extends VBox {
        private final List<Label> analysisLabels = new ArrayList<>();

        public PrivacyAnalysisTooltip(WalletTransaction walletTransaction) {
            List<Payment> payments = walletTransaction.getPayments();
            List<Payment> userPayments = payments.stream().filter(payment -> payment.getType() != Payment.Type.FAKE_MIX).collect(Collectors.toList());
            List<WalletNodePayment> walletNodePayments = walletTransaction.getWalletNodePayments();
            OptimizationStrategy optimizationStrategy = (OptimizationStrategy)optimizationToggleGroup.getSelectedToggle().getUserData();
            boolean fakeMixPresent = payments.stream().anyMatch(payment -> payment.getType() == Payment.Type.FAKE_MIX);
            boolean roundPaymentAmounts = userPayments.stream().anyMatch(payment -> payment.getAmount() % 100 == 0);
            boolean mixedAddressTypes = userPayments.stream().anyMatch(payment -> payment.getAddress().getScriptType() != getWalletForm().getWallet().getNode(KeyPurpose.RECEIVE).getAddress().getScriptType());
            boolean addressReuse = walletNodePayments.stream().anyMatch(walletNodePayment -> !walletNodePayment.getWalletNode().getTransactionOutputs().isEmpty());

            if(optimizationStrategy == OptimizationStrategy.PRIVACY) {
                if(fakeMixPresent) {
                    addLabel("Appears as a two person coinjoin", getPlusGlyph());
                } else {
                    if(mixedAddressTypes) {
                        addLabel("Cannot fake coinjoin due to mixed address types", getInfoGlyph());
                    } else if(userPayments.size() > 1) {
                        addLabel("Cannot fake coinjoin due to multiple payments", getInfoGlyph());
                    } else {
                        if(utxoSelectorProperty.get() instanceof MaxUtxoSelector) {
                            addLabel("Cannot fake coinjoin with max amount selected", getInfoGlyph());
                        } else if(utxoSelectorProperty.get() != null) {
                            addLabel("Cannot fake coinjoin due to coin control", getInfoGlyph());
                        } else {
                            addLabel("Cannot fake coinjoin due to insufficient funds", getInfoGlyph());
                        }
                    }
                }
            }

            if(mixedAddressTypes) {
                addLabel("Address types different to the wallet indicate external payments", getMinusGlyph());
            }

            if(roundPaymentAmounts && !fakeMixPresent) {
                addLabel("Rounded payment amounts indicate external payments", getMinusGlyph());
            }

            if(addressReuse) {
                addLabel("Address reuse detected", getMinusGlyph());
            }

            if(!fakeMixPresent && !mixedAddressTypes && !roundPaymentAmounts) {
                addLabel("Appears as a possible self transfer", getPlusGlyph());
            }

            analysisLabels.sort(Comparator.comparingInt(o -> (Integer)o.getGraphic().getUserData()));
            getChildren().addAll(analysisLabels);
            setSpacing(5);
        }

        private void addLabel(String text, Node graphic) {
            Label label = new Label(text);
            label.setStyle("-fx-font-size: 11px");
            label.setGraphic(graphic);
            analysisLabels.add(label);
        }

        private static Glyph getPlusGlyph() {
            Glyph plusGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.PLUS_CIRCLE);
            plusGlyph.setUserData(0);
            plusGlyph.setStyle("-fx-text-fill: rgb(80, 161, 79)");
            plusGlyph.setFontSize(12);
            return plusGlyph;
        }

        private static Glyph getMinusGlyph() {
            Glyph minusGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.MINUS_CIRCLE);
            minusGlyph.setUserData(2);
            minusGlyph.setStyle("-fx-text-fill: #e06c75");
            minusGlyph.setFontSize(12);
            return minusGlyph;
        }

        private static Glyph getInfoGlyph() {
            Glyph infoGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.INFO_CIRCLE);
            infoGlyph.setUserData(3);
            infoGlyph.setStyle("-fx-text-fill: -fx-accent");
            infoGlyph.setFontSize(12);
            return infoGlyph;
        }
    }
}
