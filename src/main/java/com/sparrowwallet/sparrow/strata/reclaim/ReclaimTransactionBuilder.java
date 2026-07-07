package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.strata.deposit.DepositRequestLockingScript;
import com.sparrowwallet.sparrow.strata.deposit.StrataBridgeConstants;
import com.sparrowwallet.sparrow.strata.deposit.WalletRecoveryKeyResolver;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeKeyVerificationService;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeParametersService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ReclaimTransactionBuilder {
    /**
     * Estimated additional virtual bytes contributed by a single reclaim tapscript input
     * (Schnorr signature + tapscript + control block, witness-discounted).
     */
    public static final long RECLAIM_INPUT_VBYTES = 57L;

    private ReclaimTransactionBuilder() {
    }

    public record ReclaimSpendInput(TransactionOutput utxo, int outputIndex, byte[] recoveryPk, int recoveryDelay, Transaction depositTransaction) {
    }

    public static List<ReclaimSpendInput> resolveSpendInputs(Wallet wallet, List<ReclaimEntry> entries) {
        byte[] magicBytes = StrataBridgeParametersService.getInstance().getMagicBytes();
        int recoveryDelay = StrataBridgeParametersService.getInstance().getRecoveryDelay();

        List<ReclaimSpendInput> spendInputs = new ArrayList<>();
        for(ReclaimEntry entry : entries) {
            BlockTransaction blockTransaction = wallet.getTransactions().get(entry.getHashIndex().getHash());
            if(blockTransaction == null || blockTransaction.getTransaction() == null) {
                throw new ReclaimException("Deposit transaction not found in wallet history");
            }

            Transaction depositTransaction = blockTransaction.getTransaction();
            byte[] recoveryPk = DepositRequestTagParser.parseRecoveryPk(depositTransaction, magicBytes)
                    .orElseThrow(() -> new ReclaimException("Recovery public key not found in deposit transaction"));
            int outputIndex = (int)entry.getHashIndex().getIndex();
            if(outputIndex >= depositTransaction.getOutputs().size()) {
                throw new ReclaimException("Deposit output index out of range");
            }

            TransactionOutput utxo = depositTransaction.getOutputs().get(outputIndex);
            spendInputs.add(new ReclaimSpendInput(utxo, outputIndex, recoveryPk, recoveryDelay, depositTransaction));
        }

        return spendInputs;
    }

    public static PSBT buildPsbt(Wallet wallet, List<ReclaimSpendInput> spendInputs, Address destination, double feeRate) {
        if(spendInputs.isEmpty()) {
            throw new ReclaimException("No UTXOs selected for reclaim");
        }

        byte[] bridgeOperatorPubkey = StrataBridgeKeyVerificationService.getInstance()
                .getVerifiedBridgeOperatorPubkey()
                .orElseGet(() -> StrataBridgeConstants.getBridgeOperatorPubkey(wallet.getNetwork()));

        long totalInput = spendInputs.stream().mapToLong(input -> input.utxo().getValue()).sum();
        long fee = estimateFee(spendInputs.size(), feeRate);
        if(totalInput <= fee) {
            throw new ReclaimException("Insufficient value to pay reclaim transaction fee");
        }

        long outputValue = totalInput - fee;
        Transaction transaction = new Transaction();
        transaction.setVersion(2);
        for(ReclaimSpendInput spendInput : spendInputs) {
            TransactionInput input = transaction.addInput(
                    spendInput.depositTransaction().getTxId(),
                    spendInput.outputIndex(),
                    new Script(new byte[0])
            );
            input.setSequenceNumber(csvSequence(spendInput.recoveryDelay()));
        }
        transaction.addOutput(outputValue, destination.getOutputScript());

        PSBT psbt = new PSBT(transaction);
        ReclaimPsbt.mark(psbt);
        for(int i = 0; i < spendInputs.size(); i++) {
            ReclaimSpendInput spendInput = spendInputs.get(i);
            Optional<WalletNode> signingNode = WalletRecoveryKeyResolver.findSigningNode(wallet, spendInput.recoveryPk());
            if(signingNode.isEmpty()) {
                throw new ReclaimException("Could not find wallet key to sign reclaim. Use the same software Taproot wallet that created the deposit.");
            }

            PSBTInput psbtInput = psbt.getPsbtInputs().get(i);
            psbtInput.setWitnessUtxo(spendInput.utxo());
            psbtInput.setTapInternalKey(ECKey.fromPublicOnly(bridgeOperatorPubkey));
            ReclaimPsbt.setInputRecoveryPk(psbtInput, spendInput.recoveryPk());
        }

        return psbt;
    }

    static long csvSequence(int recoveryDelay) {
        if(recoveryDelay < 0 || recoveryDelay > 0xffff) {
            throw new ReclaimException("Recovery delay out of range for CSV: " + recoveryDelay);
        }
        // Block-based relative locktime: bit 31 clear (CSV enabled), bit 22 clear (height-based).
        return recoveryDelay & 0xffffL;
    }

    private static long estimateFee(int inputCount, double feeRate) {
        long vbytes = 11 + 43 + (RECLAIM_INPUT_VBYTES * inputCount);
        return (long)Math.ceil(vbytes * feeRate);
    }
}
