package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.WalletTransaction;

import java.util.Arrays;

public final class DepositPsbtOrdering {
    private DepositPsbtOrdering() {
    }

    public static void align(PSBT psbt, WalletTransaction walletTransaction) {
        Transaction desired = walletTransaction.getTransaction();

        if(desired.getOutputs().size() != psbt.getTransaction().getOutputs().size()) {
            throw new DepositRequestException("PSBT output count does not match deposit transaction");
        }

        for(int i = 0; i < desired.getOutputs().size(); i++) {
            TransactionOutput desiredOutput = desired.getOutputs().get(i);
            int currentIndex = findOutputIndex(psbt.getTransaction(), desiredOutput, i);
            if(currentIndex != i) {
                psbt.moveOutput(currentIndex, i);
            }
        }
    }

    private static int findOutputIndex(Transaction transaction, TransactionOutput desiredOutput, int startFrom) {
        for(int i = startFrom; i < transaction.getOutputs().size(); i++) {
            if(matches(transaction.getOutputs().get(i), desiredOutput)) {
                return i;
            }
        }

        for(int i = 0; i < startFrom; i++) {
            if(matches(transaction.getOutputs().get(i), desiredOutput)) {
                return i;
            }
        }

        throw new DepositRequestException("PSBT is missing a deposit transaction output");
    }

    private static boolean matches(TransactionOutput output, TransactionOutput desiredOutput) {
        return output.getValue() == desiredOutput.getValue()
                && Arrays.equals(output.getScriptBytes(), desiredOutput.getScriptBytes());
    }
}
