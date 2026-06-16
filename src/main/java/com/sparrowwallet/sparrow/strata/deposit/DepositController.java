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
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.*;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
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
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
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
import javafx.scene.control.*;
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

    private final ObjectProperty<UtxoSelector> utxoSelectorProperty = new SimpleObjectProperty<>(null);

    private final ObjectProperty<TxoFilter> txoFilterProperty = new SimpleObjectProperty<>(null);

    private final ObjectProperty<WalletTransaction> walletTransactionProperty = new SimpleObjectProperty<>(null);

    private final Set<WalletNode> excludedChangeNodes = new HashSet<>();

    private final StringProperty utxoLabelSelectionProperty = new SimpleStringProperty("");

    private final ChangeListener<String> feeListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
            userFeeSet.set(true);
            if(newValue.isEmpty()) {
                fiatFeeAmount.setText("");
            } else {
                setFiatFeeAmount(AppServices.getFiatCurrencyExchangeRate(), getFeeValueSats());
            }
            setTargetBlocks(getTargetBlocks());
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
            updateFee();
        }
    };

    private final ChangeListener<Number> feeRangeListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
            setFeeRate(getFeeRangeRate());
            userFeeSet.set(false);
            updateFee();
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

        validationSupport.registerValidator(depositTo, false, Validator.combine(
                Validator.createEmptyValidator("Deposit address is required"),
                (Control control, String value) -> validateDepositAddress(control, value)
        ));
        validationSupport.registerValidator(label, false, Validator.createEmptyValidator("Label is required"));
        validationSupport.registerValidator(amount, false, (Control control, String value) -> validateDepositAmount(control, value));

        validationSupport.validationResultProperty().addListener((observable, oldValue, newValue) -> updateConfirmButton());
        depositTo.textProperty().addListener((observable, oldValue, newValue) -> {
            updateConfirmButton();
            updateFee();
        });
        label.textProperty().addListener((observable, oldValue, newValue) -> updateConfirmButton());
        amount.textProperty().addListener((observable, oldValue, newValue) -> {
            updateConfirmButton();
            updateFee();
        });
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
            } else if(wasBlockTargetSelection) {
                setFeeRangeRate(getTargetBlocksFeeRates().get(getTargetBlocks()));
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
                feeRatesSelectionProperty.set(newFeeRatesSelection);
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
            updateFee();
        });

        txoFilterProperty.addListener((observable, oldValue, txoFilter) -> {
            updateMaxClearButtons(utxoSelectorProperty.get(), txoFilter);
            updateFee();
        });

        walletTransactionProperty.addListener((observable, oldValue, walletTransaction) -> {
            if(walletTransaction != null && !userFeeSet.get()) {
                Double feeRate = getFeeRate();
                if(feeRate != null) {
                    setFeeValueSats(getTotalMiningFeeSats(walletTransaction.getFee(), feeRate));
                }
            }
            transactionDiagram.update(walletTransaction);
        });

        transactionDiagram.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if(oldScene == null && newScene != null) {
                transactionDiagram.update(walletTransactionProperty.get());
            }
        });

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

    private List<UtxoSelector> getUtxoSelectors() {
        if(utxoSelectorProperty.get() != null) {
            return List.of(utxoSelectorProperty.get());
        }

        Wallet wallet = getWalletForm().getWallet();
        double feeRate = getFeeRate() != null ? getFeeRate() : getFallbackFeeRate();
        long noInputsFee = wallet.getNoInputsFee(List.of(new Payment(null, null, amountSatsOrZero(), false)), feeRate);
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
        utxoSelectorProperty.setValue(null);
        txoFilterProperty.setValue(null);
        excludedChangeNodes.clear();
        walletTransactionProperty.setValue(null);
        getWalletForm().setCreatedWalletTransaction(null);

        userFeeSet.set(false);
        fee.textProperty().removeListener(feeListener);
        fee.setText("");
        fee.textProperty().addListener(feeListener);
        fiatFeeAmount.setText("");

        updateOptimizationButtons();
        updateFee();
    }

    private void updateFee() {
        if(userFeeSet.get()) {
            return;
        }

        DepositDescriptor descriptor = getDepositDescriptor();
        Long amountSats = getAmountValueSats();
        if(descriptor == null || amountSats == null || amountSats <= 0) {
            clearFee();
            return;
        }

        Double feeRate = getFeeRate();
        if(feeRate == null) {
            clearFee();
            return;
        }

        if(depositFeeService != null && depositFeeService.isRunning()) {
            depositFeeService.setIgnoreResult(true);
            depositFeeService.cancel();
        }

        String depositLabel = label.getText() == null || label.getText().isBlank() ? "deposit" : label.getText();
        Wallet wallet = getWalletForm().getWallet();
        depositFeeService = new DepositFeeService(
                wallet,
                descriptor,
                amountSats,
                depositLabel,
                feeRate,
                getMinimumFeeRate(),
                AppServices.getMinimumRelayFeeRate(),
                AppServices.getCurrentBlockHeight(),
                Config.get().isGroupByAddress(),
                Config.get().isIncludeMempoolOutputs(),
                getUtxoSelectors(),
                excludedChangeNodes,
                getTxoFilters()
        );

        final DepositFeeService currentService = depositFeeService;
        depositFeeService.setOnSucceeded(event -> {
            if(!currentService.isIgnoreResult()) {
                WalletTransaction walletTransaction = currentService.getValue();
                walletTransactionProperty.setValue(walletTransaction);
                if(walletTransaction != null) {
                    setFeeValueSats(getTotalMiningFeeSats(walletTransaction.getFee(), feeRate));
                }
            }
        });
        depositFeeService.setOnFailed(event -> {
            if(!currentService.isIgnoreResult()) {
                walletTransactionProperty.setValue(null);
                clearFee();
            }
        });
        depositFeeService.start();
    }

    private void clearFee() {
        fee.textProperty().removeListener(feeListener);
        fee.setText("");
        fee.textProperty().addListener(feeListener);
        fiatFeeAmount.setText("");
    }

    private void updateConfirmButton() {
        boolean valid = validationSupport != null && !validationSupport.isInvalid()
                && depositTo.getText() != null && !depositTo.getText().isBlank()
                && label.getText() != null && !label.getText().isBlank()
                && amount.getText() != null && !amount.getText().isBlank()
                && StrataBridgeKeyVerificationService.getInstance().isDepositAllowed();
        confirmButton.setDisable(!valid);
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
        long balance;
        UtxoSelector utxoSelector = utxoSelectorProperty.get();
        if(utxoSelector instanceof PresetUtxoSelector presetUtxoSelector) {
            balance = presetUtxoSelector.getPresetUtxos().stream().mapToLong(BlockTransactionHashIndex::getValue).sum();
        } else {
            balance = getWalletForm().getWallet().getSpendableUtxos().keySet().stream().mapToLong(BlockTransactionHashIndex::getValue).sum();
        }
        OptionalLong depositUtxoAmountSats = StrataBridgeParametersService.getInstance().getDepositUtxoAmountSats();
        if(balance <= 0 || depositUtxoAmountSats.isEmpty()) {
            return;
        }

        Double feeRate = getFeeRate();
        long spendableBalance = balance;
        if(feeRate != null) {
            long reservedForFees = estimateReservedSatsForDepositFees(feeRate);
            spendableBalance = Math.max(0, balance - reservedForFees);
        }

        long maxAmount = DepositAmountValidator.largestValidAmount(spendableBalance, depositUtxoAmountSats.getAsLong(), StrataBridgeConstants.MAX_DEPOSIT_SATS);
        if(maxAmount > 0) {
            setAmountValueSats(maxAmount);
            maxButton.setSelected(true);
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

    private Long getAmountValueSats(BitcoinUnit bitcoinUnit) {
        if(amount.getText() != null && !amount.getText().isEmpty()) {
            UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
            double fieldValue = Double.parseDouble(amount.getText().replaceAll(Pattern.quote(format.getGroupingSeparator()), "").replaceAll(",", "."));
            return bitcoinUnit.getSatsValue(fieldValue);
        }
        return null;
    }

    private void setAmountValueSats(long amountValue) {
        UnitFormat unitFormat = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
        DecimalFormat df = new DecimalFormat("#.#", unitFormat.getDecimalFormatSymbols());
        df.setMaximumFractionDigits(8);
        amount.setText(df.format(amountUnit.getValue().getValue(amountValue)));
        setFiatAmount(AppServices.getFiatCurrencyExchangeRate(), amountValue);
        updateConfirmButton();
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
        fee.textProperty().removeListener(feeListener);
        UnitFormat unitFormat = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
        DecimalFormat df = new DecimalFormat("#.#", unitFormat.getDecimalFormatSymbols());
        df.setMaximumFractionDigits(8);
        fee.setText(df.format(feeAmountUnit.getValue().getValue(feeValue)));
        fee.textProperty().addListener(feeListener);
        setFiatFeeAmount(AppServices.getFiatCurrencyExchangeRate(), feeValue);
    }

    private void setFiatFeeAmount(CurrencyRate currencyRate, Long value) {
        if(value != null && value > 0) {
            fiatFeeAmount.set(currencyRate, value);
        } else {
            fiatFeeAmount.setCurrency(null);
            fiatFeeAmount.setBtcRate(0.0);
        }
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

        updateFee();
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
        transactionDiagram.update(transactionDiagram.getWalletTransaction());
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
            revalidateAmountField();
            updateConfirmButton();
            updateFee();
        });
    }

    private void revalidateAmountField() {
        String current = amount.getText();
        amount.setText(current == null ? "" : current + " ");
        amount.setText(current == null ? "" : current);
    }

    @Subscribe
    public void depositSpendUtxos(DepositSpendUtxoEvent event) {
        if(event.getUtxos() != null && !event.getUtxos().isEmpty() && event.getWallet().equals(getWalletForm().getWallet())) {
            utxoSelectorProperty.set(new PresetUtxoSelector(event.getUtxos(), false, false));
            txoFilterProperty.set(null);
            updateOptimizationButtons();
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
    public void exchangeRatesUpdated(ExchangeRatesUpdatedEvent event) {
        setFiatAmount(event.getCurrencyRate(), getAmountValueSats());
        setFiatFeeAmount(event.getCurrencyRate(), getFeeValueSats());
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
                                 double feeRate, double minimumFeeRate, double minRelayFeeRate,
                                 Integer currentBlockHeight, boolean groupByAddress, boolean includeMempoolOutputs,
                                 List<UtxoSelector> utxoSelectors, Set<WalletNode> excludedChangeNodes, List<TxoFilter> txoFilters) {
            this.depositRequestService = new DepositRequestService(wallet, depositDescriptor, amountSats, label,
                    feeRate, minimumFeeRate, minRelayFeeRate, null, currentBlockHeight, groupByAddress, includeMempoolOutputs,
                    utxoSelectors, excludedChangeNodes, txoFilters);
        }

        @Override
        protected Task<WalletTransaction> createTask() {
            return new Task<>() {
                @Override
                protected WalletTransaction call() throws Exception {
                    return depositRequestService.createWalletTransaction().walletTransaction();
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
}
