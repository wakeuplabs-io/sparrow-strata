package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.InsufficientFundsException;
import com.sparrowwallet.drongo.wallet.MaxUtxoSelector;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.drongo.wallet.UtxoSelector;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNodePayment;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.control.TransactionDiagram;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.wallet.OptimizationStrategy;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import org.controlsfx.glyphfont.Glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class DepositPrivacyAnalysisTooltip extends VBox {
    private final List<Label> analysisLabels = new ArrayList<>();

    public DepositPrivacyAnalysisTooltip(WalletTransaction walletTransaction, Wallet wallet,
                                         OptimizationStrategy optimizationStrategy, UtxoSelector utxoSelector) {
        List<Payment> payments = walletTransaction.getPayments();
        List<Payment> userPayments = payments.stream().filter(payment -> payment.getType() != Payment.Type.FAKE_MIX).collect(Collectors.toList());
        List<WalletNodePayment> walletNodePayments = walletTransaction.getWalletNodePayments();
        boolean fakeMixPresent = payments.stream().anyMatch(payment -> payment.getType() == Payment.Type.FAKE_MIX);
        boolean roundPaymentAmounts = userPayments.stream().anyMatch(payment -> payment.getAmount() % 100 == 0);
        boolean mixedAddressTypes = userPayments.stream().anyMatch(payment -> payment.getAddress().getScriptType() != wallet.getNode(KeyPurpose.RECEIVE).getAddress().getScriptType());
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
                    if(utxoSelector instanceof MaxUtxoSelector) {
                        addLabel("Cannot fake coinjoin with max amount selected", getInfoGlyph());
                    } else if(utxoSelector != null) {
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
