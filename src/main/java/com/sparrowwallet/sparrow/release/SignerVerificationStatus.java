package com.sparrowwallet.sparrow.release;

import com.sparrowwallet.drongo.pgp.PGPVerificationResult;

public record SignerVerificationStatus(long expectedKeyId, String expectedUserId, String expectedFingerprint, PGPVerificationResult verificationResult, SignerStatus status, String message) {
    public static SignerVerificationStatus bundledSigner(long keyId, String userId, String fingerprint, PGPVerificationResult verificationResult, SignerStatus status) {
        return new SignerVerificationStatus(keyId, userId, fingerprint, verificationResult, status, null);
    }

    public static SignerVerificationStatus invalidSignature(String userId, String fingerprint, String message) {
        return new SignerVerificationStatus(0L, userId, fingerprint, null, SignerStatus.INVALID, message);
    }

    public boolean isBundledSigner() {
        return expectedKeyId != 0L;
    }
}
