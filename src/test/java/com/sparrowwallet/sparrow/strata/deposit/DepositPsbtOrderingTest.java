package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.ScriptChunk;
import com.sparrowwallet.drongo.protocol.ScriptOpCodes;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DepositPsbtOrderingTest {
    @Test
    void alignsShuffledPsbtOutputsToModernDepositOrder() {
        Script opReturnScript = new Script(List.of(ScriptChunk.fromOpcode(ScriptOpCodes.OP_RETURN), ScriptChunk.fromData(new byte[] {1, 2, 3})));
        Script paymentScript = new Script(new byte[] {0x51});
        Script changeScript = new Script(new byte[] {0x52});

        Transaction desired = new Transaction();
        TransactionOutput opReturnOutput = desired.addOutput(0L, opReturnScript);
        TransactionOutput paymentOutput = desired.addOutput(1000L, paymentScript);
        TransactionOutput changeOutput = desired.addOutput(500L, changeScript);

        List<WalletTransaction.Output> outputs = List.of(
                new WalletTransaction.NonAddressOutput(opReturnOutput),
                new WalletTransaction.PaymentOutput(paymentOutput, null),
                new WalletTransaction.ChangeOutput(changeOutput, null, 500L)
        );
        WalletTransaction walletTransaction = new WalletTransaction(null, desired, Collections.emptyList(), List.of(), Collections.emptyList(), outputs, Collections.emptyMap(), 100L);

        Transaction shuffled = new Transaction();
        shuffled.addOutput(1000L, paymentScript);
        shuffled.addOutput(500L, changeScript);
        shuffled.addOutput(0L, opReturnScript);

        PSBT psbt = new PSBT(shuffled);
        DepositPsbtOrdering.align(psbt, walletTransaction);

        for(int i = 0; i < desired.getOutputs().size(); i++) {
            TransactionOutput expected = desired.getOutputs().get(i);
            TransactionOutput actual = psbt.getTransaction().getOutputs().get(i);
            assertEquals(expected.getValue(), actual.getValue());
            assertArrayEquals(expected.getScriptBytes(), actual.getScriptBytes());
        }
    }

    @Test
    void alignsShuffledPsbtOutputsToLegacyDepositOrder() {
        Script opReturnScript = new Script(List.of(ScriptChunk.fromOpcode(ScriptOpCodes.OP_RETURN), ScriptChunk.fromData(new byte[] {1, 2, 3})));
        Script paymentScript = new Script(new byte[] {0x51});
        Script changeScript = new Script(new byte[] {0x52});

        Transaction desired = new Transaction();
        TransactionOutput paymentOutput = desired.addOutput(1000L, paymentScript);
        TransactionOutput opReturnOutput = desired.addOutput(0L, opReturnScript);
        TransactionOutput changeOutput = desired.addOutput(500L, changeScript);

        List<WalletTransaction.Output> outputs = List.of(
                new WalletTransaction.PaymentOutput(paymentOutput, null),
                new WalletTransaction.NonAddressOutput(opReturnOutput),
                new WalletTransaction.ChangeOutput(changeOutput, null, 500L)
        );
        WalletTransaction walletTransaction = new WalletTransaction(null, desired, Collections.emptyList(), List.of(), Collections.emptyList(), outputs, Collections.emptyMap(), 100L);

        Transaction shuffled = new Transaction();
        shuffled.addOutput(500L, changeScript);
        shuffled.addOutput(0L, opReturnScript);
        shuffled.addOutput(1000L, paymentScript);

        PSBT psbt = new PSBT(shuffled);
        DepositPsbtOrdering.align(psbt, walletTransaction);

        for(int i = 0; i < desired.getOutputs().size(); i++) {
            TransactionOutput expected = desired.getOutputs().get(i);
            TransactionOutput actual = psbt.getTransaction().getOutputs().get(i);
            assertEquals(expected.getValue(), actual.getValue());
            assertArrayEquals(expected.getScriptBytes(), actual.getScriptBytes());
        }
    }
}
