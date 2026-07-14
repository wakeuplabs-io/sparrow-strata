package com.sparrowwallet.sparrow.release;

import com.sparrowwallet.drongo.pgp.PGPVerificationResult;

import java.util.List;

public record ReleaseVerificationResult(List<SignerVerificationStatus> bundledSigners, List<PGPVerificationResult> additionalSignatures, List<SignerVerificationStatus> invalidSignatures, boolean policySatisfied) {
    public int missingSignerCount() {
        return (int)bundledSigners.stream().filter(signer -> signer.status() == SignerStatus.MISSING).count();
    }

    public int invalidSignerCount() {
        return (int)bundledSigners.stream().filter(signer -> signer.status() == SignerStatus.INVALID || signer.status() == SignerStatus.EXPIRED).count()
                + invalidSignatures.size();
    }

    public String getPolicyMessage() {
        if(policySatisfied) {
            return "All trusted signers verified";
        }

        int missing = missingSignerCount();
        int invalid = invalidSignerCount();
        if(missing > 0 && invalid > 0) {
            return "Missing signatures from " + missing + " of " + bundledSigners.size() + " trusted signers, " + invalid + " invalid or expired";
        }
        if(missing > 0) {
            return "Missing signatures from " + missing + " of " + bundledSigners.size() + " trusted signers";
        }
        if(invalid > 0) {
            return invalid + " invalid or expired signature(s) from trusted signers";
        }
        if(bundledSigners.isEmpty()) {
            return "No trusted release signer keys configured";
        }

        return "Release signing policy not satisfied";
    }
}
