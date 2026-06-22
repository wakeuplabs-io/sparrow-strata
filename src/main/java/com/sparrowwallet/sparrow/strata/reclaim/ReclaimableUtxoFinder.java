package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.P2TRAddress;
import com.sparrowwallet.drongo.protocol.HashIndex;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.strata.deposit.DepositRequestLockingScript;
import com.sparrowwallet.sparrow.strata.deposit.StrataBridgeConstants;
import com.sparrowwallet.sparrow.strata.net.StrataBridgeParametersService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ReclaimableUtxoFinder {
    private ReclaimableUtxoFinder() {
    }

    public static List<ReclaimEntry> findReclaimableUtxos(Wallet wallet) {
        if(wallet == null) {
            return List.of();
        }

        Network network = wallet.getNetwork();
        if(StrataBridgeConstants.getBridgeOperatorPubkeyHex(network) == null) {
            return List.of();
        }

        byte[] bridgeOperatorPubkey = StrataBridgeConstants.getBridgeOperatorPubkey(network);
        byte[] magicBytes = StrataBridgeParametersService.getInstance().getMagicBytes();
        int recoveryDelay = StrataBridgeParametersService.getInstance().getRecoveryDelay();
        Integer currentHeight = AppServices.getCurrentBlockHeight();

        Set<HashIndex> spentOutpoints = wallet.getTransactions().values().stream()
                .flatMap(blockTransaction -> blockTransaction.getSpending().stream())
                .collect(Collectors.toSet());

        List<ReclaimEntry> reclaimable = new ArrayList<>();
        for(BlockTransaction blockTransaction : wallet.getTransactions().values()) {
            Transaction transaction = blockTransaction.getTransaction();
            if(transaction == null || blockTransaction.getHeight() <= 0) {
                continue;
            }

            if(currentHeight != null && currentHeight < blockTransaction.getHeight() + recoveryDelay) {
                continue;
            }

            Optional<byte[]> recoveryPk = DepositRequestTagParser.parseRecoveryPk(transaction, magicBytes);
            if(recoveryPk.isEmpty()) {
                continue;
            }

            for(int index = 0; index < transaction.getOutputs().size(); index++) {
                TransactionOutput output = transaction.getOutputs().get(index);
                if(!ScriptType.P2TR.isScriptType(output.getScript())) {
                    continue;
                }

                P2TRAddress expectedAddress = DepositRequestLockingScript.createBridgeInAddress(
                        recoveryPk.get(), bridgeOperatorPubkey, recoveryDelay);
                if(!output.getScript().equals(expectedAddress.getOutputScript())) {
                    continue;
                }

                HashIndex outpoint = new HashIndex(blockTransaction.getHash(), index);
                if(spentOutpoints.contains(outpoint)) {
                    continue;
                }

                BlockTransactionHashIndex hashIndex = new BlockTransactionHashIndex(
                        blockTransaction.getHash(),
                        blockTransaction.getHeight(),
                        blockTransaction.getDate(),
                        blockTransaction.getFee(),
                        index,
                        output.getValue());
                reclaimable.add(new ReclaimEntry(wallet, hashIndex, expectedAddress));
            }
        }

        reclaimable.sort(Comparator.comparing((ReclaimEntry entry) -> entry.getBlockTransaction().getDate(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(entry -> entry.getHashIndex().getHash().toString())
                .thenComparingLong(entry -> entry.getHashIndex().getIndex()));
        return reclaimable;
    }

    public static long getReclaimableBalanceSats(Wallet wallet) {
        return findReclaimableUtxos(wallet).stream().mapToLong(ReclaimEntry::getValue).sum();
    }
}
