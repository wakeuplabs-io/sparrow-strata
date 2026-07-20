package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.SigHash;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.protocol.TransactionSignature;
import com.sparrowwallet.drongo.protocol.TransactionWitness;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletNode;

import java.util.List;

public final class ReclaimTapscriptSigner {
    private ReclaimTapscriptSigner() {
    }

    public static TransactionWitness signInput(Transaction transaction, int inputIndex, List<TransactionOutput> spentUtxos,
                                             Script tapscript, byte[] controlBlock, Keystore keystore, WalletNode signingNode) {
        try {
            ECKey privateKey = keystore.getKey(signingNode);
            if(privateKey.hasOddYCoord()) {
                privateKey = privateKey.negatePrivate();
            }

            Sha256Hash hash = transaction.hashForTaprootSignature(spentUtxos, inputIndex, true, tapscript, SigHash.DEFAULT, null);
            TransactionSignature signature = privateKey.sign(hash, SigHash.DEFAULT, TransactionSignature.Type.SCHNORR);

            List<byte[]> witnessStack = List.of(
                    signature.encodeToBitcoin(),
                    tapscript.getProgram(),
                    controlBlock
            );
            return new TransactionWitness(transaction, witnessStack);
        } catch(Exception e) {
            throw new ReclaimException("Failed to sign reclaim input", e);
        }
    }
}
