package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.InsufficientFundsException;
import com.sparrowwallet.drongo.wallet.TxoFilter;
import com.sparrowwallet.drongo.wallet.UtxoSelector;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.strata.deposit.DepositFeeRates;
import com.sparrowwallet.sparrow.strata.deposit.DepositOutputOrdering;
import com.sparrowwallet.sparrow.strata.deposit.DepositPsbtOrdering;
import com.sparrowwallet.sparrow.strata.deposit.DepositRequestService;
import com.sparrowwallet.sparrow.strata.deposit.StrataBridgeConstants;
import com.sparrowwallet.sparrow.strata.deposit.WalletRecoveryKey;
import com.sparrowwallet.sparrow.strata.deposit.WalletRecoveryKeyResolver;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeKeyVerificationService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a deposit transaction that spends one or more reclaimed (bridge-in) UTXOs via their
 * recovery tapscript path, combined with additional wallet UTXOs selected normally to cover any
 * shortfall in the requested amount and to pay fees. The resulting PSBT requires "mixed" signing:
 * the reclaimed inputs via {@link ReclaimPsbtSigner}, the rest via normal wallet signing.
 */
public final class RetryDepositTransactionBuilder {
    private RetryDepositTransactionBuilder() {
    }

    public record RetryDepositResult(WalletTransaction walletTransaction, WalletRecoveryKey recoveryKey,
                                      List<ReclaimTransactionBuilder.ReclaimSpendInput> spendInputs) {
    }

    public static RetryDepositResult createWalletTransaction(Wallet wallet, List<ReclaimEntry> reclaimEntries, DepositDescriptor depositDescriptor,
            long amountSats, String label, double feeRate, double depFeeRate, double minimumFeeRate, double minRelayFeeRate,
            Long userFee, Integer currentBlockHeight, boolean groupByAddress, boolean includeMempoolOutputs,
            List<UtxoSelector> utxoSelectorsOverride, Set<WalletNode> excludedChangeNodes, List<TxoFilter> txoFiltersOverride,
            WalletRecoveryKey walletRecoveryKey) throws InsufficientFundsException {
        if(reclaimEntries == null || reclaimEntries.isEmpty()) {
            throw new ReclaimException("No UTXOs selected for retry deposit");
        }

        List<ReclaimTransactionBuilder.ReclaimSpendInput> spendInputs = ReclaimTransactionBuilder.resolveSpendInputs(wallet, reclaimEntries);
        long reclaimedTotal = spendInputs.stream().mapToLong(input -> input.utxo().getValue()).sum();
        //Marginal fee for including the reclaim tapscript input(s), which must come out of their own value
        //since they are not selected in the normal coin-selection pass below.
        long reclaimInputsFee = (long)Math.ceil(ReclaimTransactionBuilder.RECLAIM_INPUT_VBYTES * spendInputs.size() * feeRate);
        long reclaimedNetOfFee = reclaimedTotal - reclaimInputsFee;

        long depFee = DepositFeeRates.calculateDepFee(depFeeRate);
        long desiredBridgeOutput = amountSats + depFee;
        //The wallet only needs to fund what the (fee-adjusted) reclaimed value doesn't already cover.
        long serviceAmountSats = Math.max(0, amountSats - reclaimedNetOfFee);

        DepositRequestService shortfallService = new DepositRequestService(wallet, depositDescriptor, serviceAmountSats, label,
                feeRate, depFeeRate, minimumFeeRate, minRelayFeeRate, userFee, currentBlockHeight, groupByAddress, includeMempoolOutputs,
                utxoSelectorsOverride, excludedChangeNodes, txoFiltersOverride, walletRecoveryKey);
        DepositRequestService.DepositRequestResult shortfallResult = shortfallService.createWalletTransaction();

        WalletTransaction combined = combine(wallet, shortfallResult.walletTransaction(), reclaimEntries, spendInputs,
                reclaimedNetOfFee, reclaimInputsFee, desiredBridgeOutput);
        return new RetryDepositResult(combined, shortfallResult.recoveryKey(), spendInputs);
    }

    public static PSBT createPsbt(Wallet wallet, RetryDepositResult result) {
        return createPsbt(wallet, result.walletTransaction(), result.spendInputs());
    }

    public static PSBT createPsbt(Wallet wallet, WalletTransaction walletTransaction, List<ReclaimTransactionBuilder.ReclaimSpendInput> spendInputs) {
        PSBT psbt = walletTransaction.createPSBT();
        DepositPsbtOrdering.align(psbt, walletTransaction);

        byte[] bridgeOperatorPubkey = StrataBridgeKeyVerificationService.getInstance()
                .getVerifiedBridgeOperatorPubkey()
                .orElseGet(() -> StrataBridgeConstants.getBridgeOperatorPubkey(wallet.getNetwork()));

        int reclaimStartIndex = psbt.getPsbtInputs().size() - spendInputs.size();
        for(int i = 0; i < spendInputs.size(); i++) {
            ReclaimTransactionBuilder.ReclaimSpendInput spendInput = spendInputs.get(i);
            PSBTInput psbtInput = psbt.getPsbtInputs().get(reclaimStartIndex + i);
            //The shortfall transaction's PSBT construction populates this input as if it were a normal
            //wallet-owned key-path spend; overwrite with the actual recovery tapscript metadata.
            psbtInput.setWitnessUtxo(spendInput.utxo());
            psbtInput.setTapInternalKey(ECKey.fromPublicOnly(bridgeOperatorPubkey));
            psbtInput.setTapDerivedPublicKeys(Collections.emptyMap());
            ReclaimPsbt.setInputRecoveryPk(psbtInput, spendInput.recoveryPk());
        }
        ReclaimPsbt.mark(psbt);

        return psbt;
    }

    private static WalletTransaction combine(Wallet wallet, WalletTransaction shortfallTransaction, List<ReclaimEntry> reclaimEntries,
            List<ReclaimTransactionBuilder.ReclaimSpendInput> spendInputs, long reclaimedNetOfFee, long reclaimInputsFee, long desiredBridgeOutput) {
        Transaction original = shortfallTransaction.getTransaction();
        Transaction transaction = new Transaction();
        transaction.setVersion(original.getVersion());
        transaction.setLocktime(original.getLocktime());

        for(TransactionInput input : original.getInputs()) {
            TransactionInput newInput = transaction.addInput(input.getOutpoint().getHash(), input.getOutpoint().getIndex(), input.getScriptSig(), input.getWitness());
            newInput.setSequenceNumber(input.getSequenceNumber());
        }

        for(ReclaimTransactionBuilder.ReclaimSpendInput spendInput : spendInputs) {
            TransactionInput newInput = transaction.addInput(spendInput.depositTransaction().getTxId(), spendInput.outputIndex(), new Script(new byte[0]));
            newInput.setSequenceNumber(ReclaimTransactionBuilder.csvSequence(spendInput.recoveryDelay()));
        }

        //The bridge output absorbs as much of the (fee-adjusted) reclaimed value as is needed to reach the
        //requested deposit amount; any remainder (the user lowered the amount below the reclaimed value)
        //is returned as ordinary wallet change rather than over-funding the bridge-in output.
        WalletTransaction.PaymentOutput shortfallBridgeOutput = shortfallTransaction.getOutputs().stream()
                .filter(WalletTransaction.PaymentOutput.class::isInstance)
                .map(WalletTransaction.PaymentOutput.class::cast)
                .findFirst()
                .orElseThrow(() -> new ReclaimException("Deposit transaction is missing the bridge-in payment output"));
        long bridgeBump = Math.min(reclaimedNetOfFee, desiredBridgeOutput - shortfallBridgeOutput.getTransactionOutput().getValue());
        long changeBump = reclaimedNetOfFee - bridgeBump;

        List<WalletTransaction.Output> outputs = new ArrayList<>();
        WalletNode bumpedChangeNode = null;
        boolean bumpedChange = changeBump <= 0;
        for(WalletTransaction.Output output : shortfallTransaction.getOutputs()) {
            TransactionOutput originalOutput = output.getTransactionOutput();
            if(output == shortfallBridgeOutput) {
                long bumpedValue = originalOutput.getValue() + bridgeBump;
                TransactionOutput newOutput = transaction.addOutput(bumpedValue, originalOutput.getScript());
                shortfallBridgeOutput.getPayment().setAmount(bumpedValue);
                outputs.add(new WalletTransaction.PaymentOutput(newOutput, shortfallBridgeOutput.getPayment()));
            } else if(!bumpedChange && output instanceof WalletTransaction.ChangeOutput changeOutput) {
                long bumpedValue = originalOutput.getValue() + changeBump;
                TransactionOutput newOutput = transaction.addOutput(bumpedValue, originalOutput.getScript());
                outputs.add(new WalletTransaction.ChangeOutput(newOutput, changeOutput.getWalletNode(), bumpedValue));
                bumpedChangeNode = changeOutput.getWalletNode();
                bumpedChange = true;
            } else {
                TransactionOutput newOutput = transaction.addOutput(originalOutput.getValue(), originalOutput.getScript());
                outputs.add(DepositOutputOrdering.copyOutput(output, newOutput));
            }
        }

        Map<WalletNode, Long> changeMap = new LinkedHashMap<>(shortfallTransaction.getChangeMap());
        if(!bumpedChange) {
            //No existing change output to bump (the shortfall transaction needed none) - create a new one.
            WalletNode changeNode = wallet.getFreshNode(KeyPurpose.CHANGE);
            TransactionOutput newOutput = transaction.addOutput(changeBump, changeNode.getOutputScript());
            outputs.add(new WalletTransaction.ChangeOutput(newOutput, changeNode, changeBump));
            changeMap.merge(changeNode, changeBump, Long::sum);
        } else if(bumpedChangeNode != null) {
            changeMap.merge(bumpedChangeNode, changeBump, Long::sum);
        }

        Map<BlockTransactionHashIndex, WalletNode> combinedSelectedUtxos = new LinkedHashMap<>(shortfallTransaction.getSelectedUtxos());
        for(int i = 0; i < spendInputs.size(); i++) {
            ReclaimTransactionBuilder.ReclaimSpendInput spendInput = spendInputs.get(i);
            BlockTransactionHashIndex hashIndex = reclaimEntries.get(i).getHashIndex();
            WalletRecoveryKeyResolver.findSigningNode(wallet, spendInput.recoveryPk())
                    .ifPresent(node -> combinedSelectedUtxos.put(hashIndex, node));
        }

        return new WalletTransaction(wallet, transaction, shortfallTransaction.getUtxoSelectors(), List.of(combinedSelectedUtxos),
                shortfallTransaction.getPayments(), outputs, changeMap, shortfallTransaction.getFee() + reclaimInputsFee);
    }
}
