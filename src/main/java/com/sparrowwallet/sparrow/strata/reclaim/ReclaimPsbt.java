package com.sparrowwallet.sparrow.strata.reclaim;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.strata.deposit.WalletRecoveryKeyResolver;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ReclaimPsbt {
    private static final String GLOBAL_RECLAIM_KEY = Utils.bytesToHex("strata-reclaim".getBytes(StandardCharsets.UTF_8));
    private static final String GLOBAL_RECLAIM_VALUE = Utils.bytesToHex(new byte[]{1});
    private static final String INPUT_RECOVERY_PK_KEY = Utils.bytesToHex("recovery-pk".getBytes(StandardCharsets.UTF_8));

    private ReclaimPsbt() {
    }

    public static void mark(PSBT psbt) {
        psbt.getGlobalProprietary().put(GLOBAL_RECLAIM_KEY, GLOBAL_RECLAIM_VALUE);
    }

    public static boolean isReclaimPsbt(PSBT psbt) {
        return psbt != null && GLOBAL_RECLAIM_VALUE.equals(psbt.getGlobalProprietary().get(GLOBAL_RECLAIM_KEY));
    }

    public static void setInputRecoveryPk(PSBTInput psbtInput, byte[] recoveryPk) {
        if(recoveryPk == null || recoveryPk.length != 32) {
            throw new ReclaimException("Recovery public key must be 32 bytes");
        }
        psbtInput.getProprietary().put(INPUT_RECOVERY_PK_KEY, Utils.bytesToHex(recoveryPk));
    }

    public static byte[] getInputRecoveryPk(PSBTInput psbtInput) {
        String hex = psbtInput.getProprietary().get(INPUT_RECOVERY_PK_KEY);
        if(hex == null) {
            throw new ReclaimException("Reclaim PSBT input is missing recovery public key metadata");
        }
        byte[] recoveryPk = Utils.hexToBytes(hex);
        if(recoveryPk.length != 32) {
            throw new ReclaimException("Invalid recovery public key in reclaim PSBT input");
        }
        return recoveryPk;
    }

    public static boolean canWalletSign(Wallet wallet, PSBT psbt) {
        if(!isReclaimPsbt(psbt) || wallet == null || !wallet.isValid()) {
            return false;
        }
        if(wallet.getScriptType() != ScriptType.P2TR || wallet.getPolicyType() != PolicyType.SINGLE_HD) {
            return false;
        }
        if(wallet.getKeystores().size() != 1 || wallet.getKeystores().get(0).getSource() != KeystoreSource.SW_SEED) {
            return false;
        }
        return getSigningNodes(wallet, psbt).size() == psbt.getPsbtInputs().size();
    }

    public static Map<PSBTInput, WalletNode> getSigningNodes(Wallet wallet, PSBT psbt) {
        Map<PSBTInput, WalletNode> signingNodes = new LinkedHashMap<>();
        if(!isReclaimPsbt(psbt)) {
            return signingNodes;
        }

        for(PSBTInput psbtInput : psbt.getPsbtInputs()) {
            byte[] recoveryPk = getInputRecoveryPk(psbtInput);
            Optional<WalletNode> signingNode = WalletRecoveryKeyResolver.findSigningNode(wallet, recoveryPk);
            signingNode.ifPresent(node -> signingNodes.put(psbtInput, node));
        }

        return signingNodes;
    }
}
