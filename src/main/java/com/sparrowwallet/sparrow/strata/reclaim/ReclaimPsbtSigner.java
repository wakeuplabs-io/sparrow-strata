package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.sparrow.strata.protocol.StrataBridgeProtocol;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.protocol.TransactionWitness;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.strata.deposit.DepositRequestLockingScript;

import com.sparrowwallet.sparrow.strata.net.StrataBridgeKeyVerificationService;

import java.util.List;
import java.util.Map;

public final class ReclaimPsbtSigner {
    private ReclaimPsbtSigner() {
    }

    public static void sign(Wallet wallet, PSBT psbt) {
        if(!ReclaimPsbt.isReclaimPsbt(psbt)) {
            throw new ReclaimException("Not a reclaim PSBT");
        }

        Map<PSBTInput, WalletNode> signingNodes = ReclaimPsbt.getSigningNodes(wallet, psbt);
        long reclaimInputCount = psbt.getPsbtInputs().stream().filter(ReclaimPsbt::hasInputRecoveryPk).count();
        if(signingNodes.size() != reclaimInputCount) {
            throw new ReclaimException("Could not find wallet key to sign reclaim. Use the same software Taproot wallet that created the deposit.");
        }

        byte[] bridgeOperatorPubkey = StrataBridgeKeyVerificationService.getInstance()
                .getVerifiedBridgeOperatorPubkey()
                .orElseGet(() -> StrataBridgeProtocol.getBridgeOperatorPubkey(wallet.getNetwork()));

        Transaction transaction = psbt.getTransaction();
        List<TransactionOutput> spentUtxos = psbt.getPsbtInputs().stream().map(PSBTInput::getUtxo).toList();
        Keystore keystore = wallet.getKeystores().get(0);

        for(int i = 0; i < psbt.getPsbtInputs().size(); i++) {
            PSBTInput psbtInput = psbt.getPsbtInputs().get(i);
            if(psbtInput.isSigned() || !ReclaimPsbt.hasInputRecoveryPk(psbtInput)) {
                continue;
            }

            WalletNode signingNode = signingNodes.get(psbtInput);
            byte[] recoveryPk = ReclaimPsbt.getInputRecoveryPk(psbtInput);
            int recoveryDelay = csvRecoveryDelay(transaction.getInputs().get(i).getSequenceNumber());
            Script tapscript = DepositRequestLockingScript.createRecoveryTapscript(recoveryPk, recoveryDelay);
            byte[] controlBlock = ReclaimControlBlock.forSingleLeafScript(bridgeOperatorPubkey, tapscript);
            TransactionWitness witness = ReclaimTapscriptSigner.signInput(
                    transaction, i, spentUtxos, tapscript, controlBlock, keystore, signingNode);

            psbtInput.setFinalScriptWitness(witness);
        }
    }

    private static int csvRecoveryDelay(long sequence) {
        return (int)(sequence & 0xffffL);
    }
}
