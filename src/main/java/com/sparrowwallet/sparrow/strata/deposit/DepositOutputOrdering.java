package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.wallet.WalletTransaction;

import java.util.ArrayList;
import java.util.List;

public final class DepositOutputOrdering {
    private DepositOutputOrdering() {
    }

    public static WalletTransaction reorder(WalletTransaction walletTransaction, Script opReturnScript, byte[] magicBytes) {
        if(Sps50Encoder.usesLegacyTagFormat(magicBytes)) {
            return reorderLegacy(walletTransaction, opReturnScript);
        }
        return reorderModern(walletTransaction, opReturnScript);
    }

    private static WalletTransaction reorderModern(WalletTransaction walletTransaction, Script opReturnScript) {
        Transaction original = walletTransaction.getTransaction();
        Transaction transaction = new Transaction();
        transaction.setVersion(original.getVersion());
        transaction.setLocktime(original.getLocktime());

        for(TransactionInput input : original.getInputs()) {
            TransactionInput newInput = transaction.addInput(input.getOutpoint().getHash(), input.getOutpoint().getIndex(), input.getScriptSig(), input.getWitness());
            newInput.setSequenceNumber(input.getSequenceNumber());
        }

        List<WalletTransaction.Output> paymentOutputs = new ArrayList<>();
        List<WalletTransaction.Output> changeOutputs = new ArrayList<>();
        for(WalletTransaction.Output output : walletTransaction.getOutputs()) {
            if(output instanceof WalletTransaction.NonAddressOutput) {
                continue;
            } else if(output instanceof WalletTransaction.PaymentOutput paymentOutput) {
                paymentOutputs.add(paymentOutput);
            } else if(output instanceof WalletTransaction.ChangeOutput changeOutput) {
                changeOutputs.add(changeOutput);
            } else if(output instanceof WalletTransaction.SilentPaymentChangeOutput silentPaymentChangeOutput) {
                changeOutputs.add(silentPaymentChangeOutput);
            } else {
                paymentOutputs.add(output);
            }
        }

        List<WalletTransaction.Output> reorderedOutputs = new ArrayList<>();
        TransactionOutput opReturnOutput = transaction.addOutput(0L, opReturnScript);
        reorderedOutputs.add(new WalletTransaction.NonAddressOutput(opReturnOutput));

        for(WalletTransaction.Output output : paymentOutputs) {
            TransactionOutput originalOutput = output.getTransactionOutput();
            TransactionOutput newOutput = transaction.addOutput(originalOutput.getValue(), originalOutput.getScript());
            reorderedOutputs.add(copyOutput(output, newOutput));
        }

        for(WalletTransaction.Output output : changeOutputs) {
            TransactionOutput originalOutput = output.getTransactionOutput();
            TransactionOutput newOutput = transaction.addOutput(originalOutput.getValue(), originalOutput.getScript());
            reorderedOutputs.add(copyOutput(output, newOutput));
        }

        return new WalletTransaction(walletTransaction.getWallet(), transaction, walletTransaction.getUtxoSelectors(), walletTransaction.getSelectedUtxoSets(),
                walletTransaction.getPayments(), reorderedOutputs, walletTransaction.getChangeMap(), walletTransaction.getFee());
    }

    private static WalletTransaction reorderLegacy(WalletTransaction walletTransaction, Script opReturnScript) {
        Transaction original = walletTransaction.getTransaction();
        Transaction transaction = new Transaction();
        transaction.setVersion(original.getVersion());
        transaction.setLocktime(original.getLocktime());

        for(TransactionInput input : original.getInputs()) {
            TransactionInput newInput = transaction.addInput(input.getOutpoint().getHash(), input.getOutpoint().getIndex(), input.getScriptSig(), input.getWitness());
            newInput.setSequenceNumber(input.getSequenceNumber());
        }

        List<WalletTransaction.Output> paymentOutputs = new ArrayList<>();
        List<WalletTransaction.Output> changeOutputs = new ArrayList<>();
        for(WalletTransaction.Output output : walletTransaction.getOutputs()) {
            if(output instanceof WalletTransaction.NonAddressOutput) {
                continue;
            } else if(output instanceof WalletTransaction.PaymentOutput paymentOutput) {
                paymentOutputs.add(paymentOutput);
            } else if(output instanceof WalletTransaction.ChangeOutput changeOutput) {
                changeOutputs.add(changeOutput);
            } else if(output instanceof WalletTransaction.SilentPaymentChangeOutput silentPaymentChangeOutput) {
                changeOutputs.add(silentPaymentChangeOutput);
            } else {
                paymentOutputs.add(output);
            }
        }

        List<WalletTransaction.Output> reorderedOutputs = new ArrayList<>();

        for(WalletTransaction.Output output : paymentOutputs) {
            TransactionOutput originalOutput = output.getTransactionOutput();
            TransactionOutput newOutput = transaction.addOutput(originalOutput.getValue(), originalOutput.getScript());
            reorderedOutputs.add(copyOutput(output, newOutput));
        }

        TransactionOutput opReturnOutput = transaction.addOutput(0L, opReturnScript);
        reorderedOutputs.add(new WalletTransaction.NonAddressOutput(opReturnOutput));

        for(WalletTransaction.Output output : changeOutputs) {
            TransactionOutput originalOutput = output.getTransactionOutput();
            TransactionOutput newOutput = transaction.addOutput(originalOutput.getValue(), originalOutput.getScript());
            reorderedOutputs.add(copyOutput(output, newOutput));
        }

        return new WalletTransaction(walletTransaction.getWallet(), transaction, walletTransaction.getUtxoSelectors(), walletTransaction.getSelectedUtxoSets(),
                walletTransaction.getPayments(), reorderedOutputs, walletTransaction.getChangeMap(), walletTransaction.getFee());
    }

    public static WalletTransaction.Output copyOutput(WalletTransaction.Output output, TransactionOutput transactionOutput) {
        if(output instanceof WalletTransaction.PaymentOutput paymentOutput) {
            return new WalletTransaction.PaymentOutput(transactionOutput, paymentOutput.getPayment());
        } else if(output instanceof WalletTransaction.ChangeOutput changeOutput) {
            return new WalletTransaction.ChangeOutput(transactionOutput, changeOutput.getWalletNode(), changeOutput.getValue());
        } else if(output instanceof WalletTransaction.SilentPaymentChangeOutput silentPaymentChangeOutput) {
            return new WalletTransaction.SilentPaymentChangeOutput(transactionOutput, silentPaymentChangeOutput.getSilentPayment());
        } else if(output instanceof WalletTransaction.SilentPaymentOutput silentPaymentOutput) {
            return new WalletTransaction.SilentPaymentOutput(transactionOutput, silentPaymentOutput.getSilentPayment());
        } else if(output instanceof WalletTransaction.ConsolidationOutput consolidationOutput) {
            return new WalletTransaction.ConsolidationOutput(transactionOutput, consolidationOutput.getWalletNodePayment(), consolidationOutput.getValue());
        } else if(output instanceof WalletTransaction.WalletNodeOutput walletNodeOutput) {
            return new WalletTransaction.WalletNodeOutput(transactionOutput, walletNodeOutput.getWalletNode(), walletNodeOutput.getValue());
        }

        return new WalletTransaction.NonAddressOutput(transactionOutput);
    }
}
