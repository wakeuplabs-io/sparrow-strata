package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.BitcoinUnit;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.CurrencyRate;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.UnitFormat;
import com.sparrowwallet.sparrow.control.BlockTargetFeeRatesChart;
import com.sparrowwallet.sparrow.control.CoinTextFormatter;
import com.sparrowwallet.sparrow.control.CopyableLabel;
import com.sparrowwallet.sparrow.control.FeeRangeSlider;
import com.sparrowwallet.sparrow.control.FiatLabel;
import com.sparrowwallet.sparrow.control.MempoolSizeFeeRatesChart;
import com.sparrowwallet.sparrow.control.RecentBlocksView;
import com.sparrowwallet.sparrow.event.BitcoinUnitChangedEvent;
import com.sparrowwallet.sparrow.event.BlockSummaryEvent;
import com.sparrowwallet.sparrow.event.FeeRatesSelectionChangedEvent;
import com.sparrowwallet.sparrow.event.FeeRatesUpdatedEvent;
import com.sparrowwallet.sparrow.event.MempoolRateSizesUpdatedEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ExchangeSource;
import com.sparrowwallet.sparrow.net.FeeRatesSource;
import com.sparrowwallet.sparrow.net.MempoolRateSize;
import com.sparrowwallet.sparrow.wallet.FeeRatesSelection;
import com.google.common.eventbus.Subscribe;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import org.controlsfx.glyphfont.Glyph;
import tornadofx.control.Field;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.sparrowwallet.sparrow.AppServices.TARGET_BLOCKS_RANGE;
import static com.sparrowwallet.sparrow.AppServices.getFallbackFeeRate;

/**
 * Fee rate UI for the deposit form (mirrors Send tab fee section).
 */
public class DepositFeeRateSection {
  public record DepositFeeControls(
          ToggleGroup feeSelectionToggleGroup,
          ToggleButton targetBlocksToggle,
          ToggleButton mempoolSizeToggle,
          ToggleButton recentBlocksToggle,
          Field targetBlocksField,
          Slider targetBlocks,
          Field feeRangeField,
          FeeRangeSlider feeRange,
          CopyableLabel feeRate,
          Label feeRatePriority,
          Glyph feeRatePriorityGlyph,
          Label cpfpFeeRate,
          TextField fee,
          ComboBox<BitcoinUnit> feeAmountUnit,
          FiatLabel fiatFeeAmount,
          BlockTargetFeeRatesChart blockTargetFeeRatesChart,
          MempoolSizeFeeRatesChart mempoolSizeFeeRatesChart,
          RecentBlocksView recentBlocksView
  ) {
  }

  private final DepositFeeControls controls;
  private final Wallet wallet;
  private final Runnable onFeeChanged;
  private final BooleanProperty userFeeSet = new SimpleBooleanProperty(false);
  private final ObjectProperty<FeeRatesSelection> feeRatesSelectionProperty = new SimpleObjectProperty<>(null);
  private boolean updateDefaultFeeRate;
  private int applyingPreviewUpdates;
  private ChangeListener<String> feeTextListener;

  private final ChangeListener<Number> targetBlocksListener = new ChangeListener<>() {
    @Override
    public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
      Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
      Integer target = getTargetBlocks();

      if(targetBlocksFeeRates != null) {
        setFeeRate(targetBlocksFeeRates.get(target));
        controls.blockTargetFeeRatesChart().select(target);
      } else {
        controls.feeRate().setText("Unknown");
      }

      controls.targetBlocks().setTooltip(new Tooltip("Target inclusion within " + target + " blocks"));
      userFeeSet.set(false);
      onFeeChanged.run();
    }
  };

  private final ChangeListener<Number> feeRangeListener = new ChangeListener<>() {
    @Override
    public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
      setFeeRate(getFeeRangeRate());
      userFeeSet.set(false);
      onFeeChanged.run();
    }
  };

  public DepositFeeRateSection(DepositFeeControls controls, Wallet wallet, Runnable onFeeChanged) {
    this.controls = controls;
    this.wallet = wallet;
    this.onFeeChanged = onFeeChanged;
  }

  public BooleanProperty userFeeSetProperty() {
    return userFeeSet;
  }

  public boolean isUserFeeSet() {
    return userFeeSet.get();
  }

  public void setApplyingPreviewUpdates(int value) {
    applyingPreviewUpdates = value;
  }

  public int getApplyingPreviewUpdates() {
    return applyingPreviewUpdates;
  }

  public void incrementApplyingPreviewUpdates() {
    applyingPreviewUpdates++;
  }

  public void decrementApplyingPreviewUpdates() {
    applyingPreviewUpdates--;
  }

  public void initialize(BitcoinUnit defaultBitcoinUnit) {
    controls.targetBlocksField().managedProperty().bind(controls.targetBlocksField().visibleProperty());
    controls.targetBlocks().setMin(0);
    controls.targetBlocks().setMax(TARGET_BLOCKS_RANGE.size() - 1);
    controls.targetBlocks().setMajorTickUnit(1);
    controls.targetBlocks().setMinorTickCount(0);
    controls.targetBlocks().setLabelFormatter(new javafx.util.StringConverter<>() {
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
    controls.targetBlocks().valueProperty().addListener(targetBlocksListener);

    controls.feeRangeField().managedProperty().bind(controls.feeRangeField().visibleProperty());
    controls.feeRangeField().visibleProperty().bind(controls.targetBlocksField().visibleProperty().not());
    controls.feeRange().valueProperty().addListener(feeRangeListener);

    controls.blockTargetFeeRatesChart().managedProperty().bind(controls.blockTargetFeeRatesChart().visibleProperty());
    controls.blockTargetFeeRatesChart().visibleProperty().bind(Bindings.equal(feeRatesSelectionProperty, FeeRatesSelection.BLOCK_TARGET));
    controls.blockTargetFeeRatesChart().initialize();
    Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
    if(targetBlocksFeeRates != null) {
      controls.blockTargetFeeRatesChart().update(targetBlocksFeeRates);
    } else {
      controls.feeRate().setText("Unknown");
    }

    controls.mempoolSizeFeeRatesChart().managedProperty().bind(controls.mempoolSizeFeeRatesChart().visibleProperty());
    controls.mempoolSizeFeeRatesChart().visibleProperty().bind(Bindings.equal(feeRatesSelectionProperty, FeeRatesSelection.MEMPOOL_SIZE));
    controls.mempoolSizeFeeRatesChart().initialize();
    Map<Date, Set<MempoolRateSize>> mempoolHistogram = getMempoolHistogram();
    if(mempoolHistogram != null) {
      controls.mempoolSizeFeeRatesChart().update(mempoolHistogram);
    }

    controls.recentBlocksView().managedProperty().bind(controls.recentBlocksView().visibleProperty());
    controls.recentBlocksView().visibleProperty().bind(Bindings.equal(feeRatesSelectionProperty, FeeRatesSelection.RECENT_BLOCKS));
    var blockSummaries = AppServices.getBlockSummaries().values().stream().sorted().toList();
    if(!blockSummaries.isEmpty()) {
      controls.recentBlocksView().update(blockSummaries, AppServices.getNextBlockMedianFeeRate());
    }

    feeRatesSelectionProperty.addListener((observable, oldValue, newValue) -> {
      boolean isBlockTargetSelection = (newValue == FeeRatesSelection.BLOCK_TARGET);
      boolean wasBlockTargetSelection = (oldValue == FeeRatesSelection.BLOCK_TARGET || oldValue == null);
      controls.targetBlocksField().setVisible(isBlockTargetSelection);
      if(isBlockTargetSelection) {
        setTargetBlocks(getTargetBlocks(getFeeRangeRate()));
        onFeeChanged.run();
      } else if(wasBlockTargetSelection) {
        setFeeRangeRate(getTargetBlocksFeeRates().get(getTargetBlocks()));
        onFeeChanged.run();
      }
    });

    FeeRatesSelection feeRatesSelection = Config.get().getFeeRatesSelection();
    feeRatesSelection = (feeRatesSelection == null ? FeeRatesSelection.RECENT_BLOCKS : feeRatesSelection);
    controls.cpfpFeeRate().managedProperty().bind(controls.cpfpFeeRate().visibleProperty());
    controls.cpfpFeeRate().setVisible(false);
    setDefaultFeeRate();
    feeRatesSelectionProperty.set(feeRatesSelection);
    controls.feeSelectionToggleGroup().selectToggle(feeRatesSelection == FeeRatesSelection.BLOCK_TARGET ? controls.targetBlocksToggle() :
            (feeRatesSelection == FeeRatesSelection.MEMPOOL_SIZE ? controls.mempoolSizeToggle() : controls.recentBlocksToggle()));
    controls.feeSelectionToggleGroup().selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
      if(newValue != null) {
        FeeRatesSelection newFeeRatesSelection = (FeeRatesSelection)newValue.getUserData();
        Config.get().setFeeRatesSelection(newFeeRatesSelection);
        EventManager.get().post(new FeeRatesSelectionChangedEvent(wallet, newFeeRatesSelection));
      }
    });

    controls.fee().setTextFormatter(new CoinTextFormatter(Config.get().getUnitFormat()));
    BitcoinUnit unit = getBitcoinUnit(defaultBitcoinUnit);
    controls.feeAmountUnit().getSelectionModel().select(BitcoinUnit.BTC.equals(unit) ? 0 : 1);
    controls.feeAmountUnit().valueProperty().addListener((observable, oldValue, newValue) -> {
      Long value = getFeeValueSats(oldValue);
      if(value != null) {
        setFeeValueSats(value);
      }
    });
  }

  public void setFeeTextListener(ChangeListener<String> feeTextListener) {
    this.feeTextListener = feeTextListener;
  }

  public void register() {
    EventManager.get().register(this);
  }

  public void unregister() {
    EventManager.get().unregister(this);
  }

  public Double getSliderFeeRate() {
    if(controls.targetBlocksField().isVisible()) {
      return getTargetBlocksFeeRates().get(getTargetBlocks());
    }
    return getFeeRangeRate();
  }

  public double getSelectionFeeRate() {
    Double sliderFeeRate = getSliderFeeRate();
    return DepositFeeRates.resolveSelectionFeeRate(userFeeSet.get(), sliderFeeRate != null ? sliderFeeRate : getFallbackFeeRate(),
            AppServices.getMinimumRelayFeeRate());
  }

  public Double getMinimumFeeRate() {
    Optional<Double> optMinFeeRate = getTargetBlocksFeeRates().values().stream().min(Double::compareTo);
    double minRate = optMinFeeRate.orElse(getFallbackFeeRate());
    Double sliderFeeRate = getSliderFeeRate();
    if(sliderFeeRate != null) {
      minRate = Math.min(sliderFeeRate, minRate);
    }
    return Math.max(minRate, Transaction.DEFAULT_MIN_RELAY_FEE);
  }

  public Long getFeeValueSats() {
    return getFeeValueSats(controls.feeAmountUnit().getSelectionModel().getSelectedItem());
  }

  public Long getFeeValueSats(BitcoinUnit bitcoinUnit) {
    if(controls.fee().getText() != null && !controls.fee().getText().isEmpty()) {
      UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
      double fieldValue = Double.parseDouble(controls.fee().getText().replaceAll(Pattern.quote(format.getGroupingSeparator()), "").replaceAll(",", "."));
      return bitcoinUnit.getSatsValue(fieldValue);
    }
    return null;
  }

  public Long resolveMiningFeeFromTotal(double sliderFeeRate) {
    return DepositFeeRates.resolveMiningFeeFromTotal(getFeeValueSats(), DepositFeeRates.calculateDepFee(sliderFeeRate));
  }

  public void setFeeValueSats(long feeValue) {
    applyingPreviewUpdates++;
    try {
      if(feeTextListener != null) {
        controls.fee().textProperty().removeListener(feeTextListener);
      }
      UnitFormat unitFormat = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
      DecimalFormat df = new DecimalFormat("#.#", unitFormat.getDecimalFormatSymbols());
      df.setMaximumFractionDigits(8);
      controls.fee().setText(df.format(controls.feeAmountUnit().getValue().getValue(feeValue)));
      if(feeTextListener != null) {
        controls.fee().textProperty().addListener(feeTextListener);
      }
      setFiatFeeAmount(AppServices.getFiatCurrencyExchangeRate(), feeValue);
      controls.fiatFeeAmount().refresh();
    } finally {
      applyingPreviewUpdates--;
    }
  }

  public void revalidateFeeField(ChangeListener<String> feeListener) {
    controls.fee().textProperty().removeListener(feeListener);
    String text = controls.fee().getText();
    int caret = controls.fee().getCaretPosition();
    controls.fee().setText(text + "0");
    controls.fee().setText(text);
    controls.fee().positionCaret(caret);
    controls.fee().textProperty().addListener(feeListener);
  }

  public void clearFee() {
    applyingPreviewUpdates++;
    try {
      if(feeTextListener != null) {
        controls.fee().textProperty().removeListener(feeTextListener);
      }
      controls.fee().setText("");
      if(feeTextListener != null) {
        controls.fee().textProperty().addListener(feeTextListener);
      }
      clearFiatFeeAmount();
      userFeeSet.set(false);
    } finally {
      applyingPreviewUpdates--;
    }
  }

  public void applyPreviewFees(WalletTransaction walletTransaction, Wallet formWallet) {
    if(!userFeeSet.get()) {
      Double sliderFeeRate = getSliderFeeRate();
      if(sliderFeeRate != null) {
        setFeeValueSats(DepositFeeRates.totalMiningFeeSats(walletTransaction.getFee(), sliderFeeRate));
      }
      setFeeRate(walletTransaction.getFeeRate());
    } else {
      setTargetBlocks(getTargetBlocks(walletTransaction.getFeeRate()));
      setFeeRangeRate(walletTransaction.getFeeRate());
    }
    setEffectiveFeeRate(walletTransaction, formWallet);
  }

  public void hideCpfp() {
    controls.cpfpFeeRate().setVisible(false);
  }

  public void setDefaultFeeRate() {
    int defaultTarget = TARGET_BLOCKS_RANGE.get((TARGET_BLOCKS_RANGE.size() / 2) - 1);
    int index = TARGET_BLOCKS_RANGE.indexOf(defaultTarget);
    Double defaultRate = getTargetBlocksFeeRates().get(defaultTarget);
    controls.targetBlocks().setValue(index);
    controls.blockTargetFeeRatesChart().select(defaultTarget);
    controls.recentBlocksView().updateFeeRate(defaultRate);
    setFeeRangeRate(defaultRate);
    setFeeRate(getFeeRangeRate());
    if(Network.get().equals(Network.MAINNET) && defaultRate == getFallbackFeeRate()) {
      updateDefaultFeeRate = true;
    }
  }

  public void setFiatFeeAmount(CurrencyRate currencyRate, Long amount) {
    if(amount != null && amount > 0 && currencyRate != null && currencyRate.isAvailable() && Config.get().getExchangeSource() != ExchangeSource.NONE) {
      controls.fiatFeeAmount().set(currencyRate, amount);
    }
  }

  public void clearFiatFeeAmount() {
    controls.fiatFeeAmount().setValue(-1);
    controls.fiatFeeAmount().setCurrency(null);
    controls.fiatFeeAmount().setBtcRate(0.0);
  }

  public void refreshFiatFeeAmount() {
    controls.fiatFeeAmount().refresh();
  }

  public void refreshFiatFeeAmount(UnitFormat unitFormat) {
    controls.fiatFeeAmount().refresh(unitFormat);
  }

  public void onFeeFieldChanged(String newValue) {
    if(applyingPreviewUpdates > 0) {
      return;
    }
    if(newValue == null || newValue.isEmpty()) {
      userFeeSet.set(false);
      clearFiatFeeAmount();
    } else {
      userFeeSet.set(true);
      setFiatFeeAmount(AppServices.getFiatCurrencyExchangeRate(), getFeeValueSats());
    }
    onFeeChanged.run();
  }

  public void resetUserFeeSet() {
    userFeeSet.set(false);
  }

  @Subscribe
  public void feeRatesUpdated(FeeRatesUpdatedEvent event) {
    controls.blockTargetFeeRatesChart().update(event.getTargetBlockFeeRates());
    controls.blockTargetFeeRatesChart().select(getTargetBlocks());
    if(controls.targetBlocksField().isVisible()) {
      setFeeRate(event.getTargetBlockFeeRates().get(getTargetBlocks()));
    } else {
      setFeeRatePriority(getFeeRangeRate());
    }
    controls.feeRange().updateTrackHighlight();

    if(event.getNextBlockMedianFeeRate() != null) {
      controls.recentBlocksView().updateFeeRate(event.getNextBlockMedianFeeRate());
    } else {
      controls.recentBlocksView().updateFeeRate(event.getTargetBlockFeeRates());
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
    controls.mempoolSizeFeeRatesChart().update(getMempoolHistogram());
  }

  @Subscribe
  public void blockSummary(BlockSummaryEvent event) {
    Platform.runLater(() -> controls.recentBlocksView().update(AppServices.getBlockSummaries().values().stream().sorted().toList(), AppServices.getNextBlockMedianFeeRate()));
  }

  @Subscribe
  public void bitcoinUnitChanged(BitcoinUnitChangedEvent event) {
    BitcoinUnit unit = getBitcoinUnit(event.getBitcoinUnit());
    controls.feeAmountUnit().getSelectionModel().select(BitcoinUnit.BTC.equals(unit) ? 0 : 1);
  }

  @Subscribe
  public void feeRateSelectionChanged(FeeRatesSelectionChangedEvent event) {
    if(event.getWallet().equals(wallet)) {
      feeRatesSelectionProperty.set(event.getFeeRateSelection());
      Toggle toggle = event.getFeeRateSelection() == FeeRatesSelection.BLOCK_TARGET ? controls.targetBlocksToggle() :
              (event.getFeeRateSelection() == FeeRatesSelection.MEMPOOL_SIZE ? controls.mempoolSizeToggle() : controls.recentBlocksToggle());
      if(controls.feeSelectionToggleGroup().getSelectedToggle() != toggle) {
        controls.feeSelectionToggleGroup().selectToggle(toggle);
      }
    }
  }

  private BitcoinUnit getBitcoinUnit(BitcoinUnit bitcoinUnit) {
    BitcoinUnit unit = bitcoinUnit;
    if(unit == null || unit.equals(BitcoinUnit.AUTO)) {
      unit = wallet.getAutoUnit();
    }
    return unit;
  }

  private Integer getTargetBlocks() {
    int index = (int)controls.targetBlocks().getValue();
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
    controls.targetBlocks().valueProperty().removeListener(targetBlocksListener);
    int index = TARGET_BLOCKS_RANGE.indexOf(target);
    controls.targetBlocks().setValue(index);
    controls.blockTargetFeeRatesChart().select(target);
    controls.targetBlocks().setTooltip(new Tooltip("Target inclusion within " + target + " blocks"));
    controls.targetBlocks().valueProperty().addListener(targetBlocksListener);
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
    return controls.feeRange().getFeeRate();
  }

  private void setFeeRangeRate(Double feeRateAmt) {
    controls.feeRange().valueProperty().removeListener(feeRangeListener);
    controls.feeRange().setFeeRate(feeRateAmt);
    controls.feeRange().valueProperty().addListener(feeRangeListener);
  }

  private Map<Date, Set<MempoolRateSize>> getMempoolHistogram() {
    return AppServices.getMempoolHistogram();
  }

  private void setFeeRate(Double feeRateAmt) {
    UnitFormat format = Config.get().getUnitFormat() == null ? UnitFormat.DOT : Config.get().getUnitFormat();
    controls.feeRate().setText(format.getCurrencyFormat().format(feeRateAmt) + (controls.cpfpFeeRate().isVisible() ? "" : " sats/vB"));
    setFeeRatePriority(feeRateAmt);
  }

  private void setEffectiveFeeRate(WalletTransaction walletTransaction, Wallet formWallet) {
    var unconfirmedUtxoTxs = walletTransaction == null ? java.util.Collections.<BlockTransaction>emptyList() :
            walletTransaction.getSelectedUtxos().keySet().stream().filter(ref -> ref.getHeight() <= 0)
                    .map(ref -> formWallet.getWalletTransaction(ref.getHash()))
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
        controls.cpfpFeeRate().setTooltip(tooltip);
        controls.cpfpFeeRate().setVisible(true);
        controls.cpfpFeeRate().setText(strEffectiveRate + " sats/vB (CPFP)");
      } else {
        controls.cpfpFeeRate().setVisible(false);
      }
    } else {
      controls.cpfpFeeRate().setVisible(false);
    }
  }

  private void setFeeRatePriority(Double feeRateAmt) {
    Map<Integer, Double> targetBlocksFeeRates = getTargetBlocksFeeRates();
    if(targetBlocksFeeRates.get(Integer.MAX_VALUE) != null) {
      Double minFeeRate = targetBlocksFeeRates.get(Integer.MAX_VALUE);
      if(minFeeRate > 1.0 && feeRateAmt < minFeeRate) {
        controls.feeRatePriority().setText("Below Minimum");
        controls.feeRatePriority().setTooltip(new Tooltip("Transactions at this fee rate are currently being purged from the default sized mempool"));
        controls.feeRatePriorityGlyph().setStyle("-fx-text-fill: #a0a1a7cc");
        controls.feeRatePriorityGlyph().setIcon(FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
        return;
      }
    }

    Integer blocks = getTargetBlocks(feeRateAmt);
    if(blocks != null) {
      if(blocks < FeeRatesSource.BLOCKS_IN_HALF_HOUR) {
        controls.feeRatePriority().setText("High Priority");
        controls.feeRatePriority().setTooltip(new Tooltip("Typically confirms within minutes"));
        controls.feeRatePriorityGlyph().setStyle("-fx-text-fill: #c8416499");
        controls.feeRatePriorityGlyph().setIcon(FontAwesome5.Glyph.CIRCLE);
      } else if(blocks < FeeRatesSource.BLOCKS_IN_HOUR) {
        controls.feeRatePriority().setText("Medium Priority");
        controls.feeRatePriority().setTooltip(new Tooltip("Typically confirms within an hour or two"));
        controls.feeRatePriorityGlyph().setStyle("-fx-text-fill: #fba71b99");
        controls.feeRatePriorityGlyph().setIcon(FontAwesome5.Glyph.CIRCLE);
      } else {
        controls.feeRatePriority().setText("Low Priority");
        controls.feeRatePriority().setTooltip(new Tooltip("Typically confirms in a day or longer"));
        controls.feeRatePriorityGlyph().setStyle("-fx-text-fill: #41a9c999");
        controls.feeRatePriorityGlyph().setIcon(FontAwesome5.Glyph.CIRCLE);
      }
    }
  }
}
