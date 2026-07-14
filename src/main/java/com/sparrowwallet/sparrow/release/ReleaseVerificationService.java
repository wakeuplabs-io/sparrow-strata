package com.sparrowwallet.sparrow.release;

import com.sparrowwallet.drongo.IOUtils;
import com.sparrowwallet.drongo.pgp.PGPKeySource;
import com.sparrowwallet.drongo.pgp.PGPUtils;
import com.sparrowwallet.drongo.pgp.PGPVerificationException;
import com.sparrowwallet.drongo.pgp.PGPVerificationResult;
import org.bouncycastle.gpg.keybox.KeyBlob;
import org.bouncycastle.gpg.keybox.PublicKeyRingBlob;
import org.bouncycastle.gpg.keybox.bc.BcKeyBox;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.util.io.Streams;
import org.pgpainless.PGPainless;
import org.pgpainless.decryption_verification.ConsumerOptions;
import org.pgpainless.decryption_verification.DecryptionStream;
import org.pgpainless.decryption_verification.MessageMetadata;
import org.pgpainless.decryption_verification.SignatureVerification;
import org.pgpainless.key.SubkeyIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ReleaseVerificationService {
    private static final Logger log = LoggerFactory.getLogger(ReleaseVerificationService.class);

    private ReleaseVerificationService() {
    }

    public static ReleaseVerificationResult verifyAll(InputStream publicKeyStream, InputStream contentStream, InputStream detachedSignatureStream) throws IOException, PGPVerificationException {
        return verifyAll(publicKeyStream, contentStream, detachedSignatureStream, null);
    }

    public static ReleaseVerificationResult verifyAll(InputStream publicKeyStream, InputStream contentStream, InputStream detachedSignatureStream, PGPPublicKeyRingCollection applicationKeyRingsOverride) throws IOException, PGPVerificationException {
        PGPPublicKeyRing userProvidedKeyRing = null;
        if(publicKeyStream != null) {
            userProvidedKeyRing = PGPainless.readKeyRing().publicKeyRing(publicKeyStream);
            if(userProvidedKeyRing == null) {
                throw new PGPVerificationException("Invalid public key provided");
            }
        }

        PGPPublicKeyRingCollection userPgpPublicKeyRingCollection = getUserKeyRingCollection();
        PGPPublicKeyRingCollection appPgpPublicKeyRingCollection = applicationKeyRingsOverride != null ? applicationKeyRingsOverride : getApplicationKeyRingCollection();
        byte[] contentBytes = contentStream.readAllBytes();
        boolean hasApplicationKeys = hasApplicationKeys(appPgpPublicKeyRingCollection);

        try {
            List<PGPVerificationResult> verifiedResults = new ArrayList<>();
            Map<Long, PGPVerificationResult> verifiedByKeyId = new HashMap<>();
            List<SignerVerificationStatus> invalidSignatures = new ArrayList<>();

            if(detachedSignatureStream != null) {
                for(String signatureBlock : splitSignatureBlocks(detachedSignatureStream.readAllBytes())) {
                    verifySignatureBlock(contentBytes, signatureBlock, userProvidedKeyRing, userPgpPublicKeyRingCollection, appPgpPublicKeyRingCollection, hasApplicationKeys, verifiedResults, verifiedByKeyId, invalidSignatures);
                }
            } else {
                verifySignatureBlock(contentBytes, null, userProvidedKeyRing, userPgpPublicKeyRingCollection, appPgpPublicKeyRingCollection, hasApplicationKeys, verifiedResults, verifiedByKeyId, invalidSignatures);
            }

            if(verifiedResults.isEmpty()) {
                if(!invalidSignatures.isEmpty()) {
                    throw new PGPVerificationException(invalidSignatures.getFirst().message());
                }
                throw new PGPVerificationException("No signatures found");
            }

            List<SignerVerificationStatus> bundledSigners = buildBundledSignerStatuses(appPgpPublicKeyRingCollection, verifiedByKeyId);
            List<PGPVerificationResult> additionalSignatures = collectAdditionalSignatures(appPgpPublicKeyRingCollection, verifiedResults);
            boolean policySatisfied = !bundledSigners.isEmpty() && bundledSigners.stream().allMatch(signer -> signer.status() == SignerStatus.VALID);

            return new ReleaseVerificationResult(bundledSigners, additionalSignatures, invalidSignatures, policySatisfied);
        } catch(PGPVerificationException e) {
            throw e;
        } catch(Exception e) {
            log.warn("Failed to verify release signatures", e);
            throw new PGPVerificationException(e.getMessage());
        }
    }

    private static void verifySignatureBlock(byte[] contentBytes, String signatureBlock, PGPPublicKeyRing userProvidedKeyRing, PGPPublicKeyRingCollection userPgpPublicKeyRingCollection, PGPPublicKeyRingCollection appPgpPublicKeyRingCollection, boolean hasApplicationKeys,
                                             List<PGPVerificationResult> verifiedResults, Map<Long, PGPVerificationResult> verifiedByKeyId, List<SignerVerificationStatus> invalidSignatures) throws Exception {
        try {
            ConsumerOptions options = ConsumerOptions.get();
            if(userProvidedKeyRing != null) {
                options.addVerificationCert(userProvidedKeyRing);
            }
            if(userPgpPublicKeyRingCollection != null) {
                options.addVerificationCerts(userPgpPublicKeyRingCollection);
            }
            if(hasApplicationKeys) {
                options.addVerificationCerts(appPgpPublicKeyRingCollection);
            }
            if(signatureBlock != null) {
                options.addVerificationOfDetachedSignatures(new ByteArrayInputStream(signatureBlock.getBytes(StandardCharsets.UTF_8)));
            }

            try(InputStream contentInputStream = new ByteArrayInputStream(contentBytes)) {
                DecryptionStream verificationStream = PGPainless.decryptAndOrVerify()
                        .onInputStream(contentInputStream)
                        .withOptions(options);

                Streams.drain(verificationStream);
                verificationStream.close();

                MessageMetadata metadata = verificationStream.getMetadata();
                List<SignatureVerification> verifiedSignatures = new ArrayList<>(metadata.getVerifiedDetachedSignatures());
                verifiedSignatures.addAll(metadata.getVerifiedSignatures());

                for(SignatureVerification signatureVerification : verifiedSignatures) {
                    PGPVerificationResult result = toVerificationResult(signatureVerification, userProvidedKeyRing, userPgpPublicKeyRingCollection, appPgpPublicKeyRingCollection);
                    if(result != null && verifiedByKeyId.putIfAbsent(result.keyId(), result) == null) {
                        verifiedResults.add(result);
                    }
                }

                for(SignatureVerification.Failure failure : metadata.getRejectedDetachedSignatures()) {
                    invalidSignatures.add(toInvalidStatus(failure));
                }
                for(SignatureVerification.Failure failure : metadata.getRejectedInlineSignatures()) {
                    invalidSignatures.add(toInvalidStatus(failure));
                }
            }
        } catch(Exception e) {
            if(signatureBlock != null) {
                invalidSignatures.add(SignerVerificationStatus.invalidSignature("", "", e.getMessage()));
            } else {
                throw e;
            }
        }
    }

    private static boolean hasApplicationKeys(PGPPublicKeyRingCollection applicationKeys) {
        return applicationKeys.iterator().hasNext();
    }

    private static List<String> splitSignatureBlocks(byte[] signatureBytes) {
        if(signatureBytes == null || signatureBytes.length == 0) {
            return List.of();
        }

        String content = new String(signatureBytes, StandardCharsets.UTF_8);
        List<String> blocks = new ArrayList<>();
        int searchFrom = 0;
        while(true) {
            int start = content.indexOf("-----BEGIN PGP SIGNATURE-----", searchFrom);
            if(start < 0) {
                break;
            }

            int end = content.indexOf("-----END PGP SIGNATURE-----", start);
            if(end < 0) {
                break;
            }

            end += "-----END PGP SIGNATURE-----".length();
            blocks.add(content.substring(start, end));
            searchFrom = end;
        }

        if(blocks.isEmpty()) {
            blocks.add(content);
        }

        return blocks;
    }

    private static List<SignerVerificationStatus> buildBundledSignerStatuses(PGPPublicKeyRingCollection applicationKeys, Map<Long, PGPVerificationResult> verifiedByKeyId) {
        List<SignerVerificationStatus> bundledSigners = new ArrayList<>();
        Set<Long> seenKeyIds = new HashSet<>();

        for(PGPPublicKeyRing keyRing : applicationKeys) {
            PGPPublicKey primaryKey = keyRing.getPublicKey();
            long primaryKeyId = primaryKey.getKeyID();
            if(!seenKeyIds.add(primaryKeyId)) {
                continue;
            }

            String userId = getPrimaryUserId(primaryKey);
            String fingerprint = PGPainless.inspectKeyRing(keyRing).getFingerprint().prettyPrint();
            PGPVerificationResult verificationResult = verifiedByKeyId.get(primaryKeyId);

            SignerStatus status;
            if(verificationResult == null) {
                status = SignerStatus.MISSING;
            } else if(verificationResult.expired()) {
                status = SignerStatus.EXPIRED;
            } else {
                // Key ID match is sufficient regardless of whether the key came from the bundle, GnuPG, or user input.
                status = SignerStatus.VALID;
            }

            bundledSigners.add(SignerVerificationStatus.bundledSigner(primaryKeyId, userId, fingerprint, verificationResult, status));
        }

        return bundledSigners;
    }

    private static List<PGPVerificationResult> collectAdditionalSignatures(PGPPublicKeyRingCollection applicationKeys, List<PGPVerificationResult> verifiedResults) {
        List<PGPVerificationResult> additionalSignatures = new ArrayList<>();
        for(PGPVerificationResult result : verifiedResults) {
            if(applicationKeys.getPublicKey(result.keyId()) == null) {
                additionalSignatures.add(result);
            }
        }
        return additionalSignatures;
    }

    private static PGPVerificationResult toVerificationResult(SignatureVerification signatureVerification, PGPPublicKeyRing userProvidedKeyRing, PGPPublicKeyRingCollection userPgpPublicKeyRingCollection, PGPPublicKeyRingCollection appPgpPublicKeyRingCollection) {
        SubkeyIdentifier subkeyIdentifier = signatureVerification.getSigningKey();
        if(subkeyIdentifier == null) {
            return null;
        }

        long primaryKeyId = subkeyIdentifier.getPrimaryKeyId();
        PGPPublicKey signedByKey = null;
        PGPKeySource keySource;
        if(userProvidedKeyRing != null && userProvidedKeyRing.getPublicKey(primaryKeyId) != null) {
            signedByKey = userProvidedKeyRing.getPublicKey(primaryKeyId);
            keySource = PGPKeySource.USER;
        } else if(appPgpPublicKeyRingCollection.getPublicKey(primaryKeyId) != null && !PGPUtils.isExpired(appPgpPublicKeyRingCollection.getPublicKey(primaryKeyId))) {
            signedByKey = appPgpPublicKeyRingCollection.getPublicKey(primaryKeyId);
            keySource = PGPKeySource.APPLICATION;
        } else if(userPgpPublicKeyRingCollection != null && userPgpPublicKeyRingCollection.getPublicKey(primaryKeyId) != null) {
            signedByKey = userPgpPublicKeyRingCollection.getPublicKey(primaryKeyId);
            keySource = PGPKeySource.GPG;
        } else if(appPgpPublicKeyRingCollection.getPublicKey(primaryKeyId) != null) {
            signedByKey = appPgpPublicKeyRingCollection.getPublicKey(primaryKeyId);
            keySource = PGPKeySource.APPLICATION;
        } else {
            keySource = PGPKeySource.NONE;
        }

        String fingerprint = subkeyIdentifier.getPrimaryKeyFingerprint().prettyPrint();
        String userId = fingerprint;
        boolean expired = false;
        if(signedByKey != null) {
            userId = getPrimaryUserId(signedByKey);
            expired = PGPUtils.isExpired(signedByKey);
        }

        return new PGPVerificationResult(primaryKeyId, userId, fingerprint, signatureVerification.getSignature().getCreationTime(), expired, keySource);
    }

    private static SignerVerificationStatus toInvalidStatus(SignatureVerification.Failure failure) {
        SubkeyIdentifier subkeyIdentifier = failure.getSigningKey();
        String fingerprint = subkeyIdentifier != null ? subkeyIdentifier.getPrimaryKeyFingerprint().prettyPrint() : "";
        String userId = fingerprint;
        String message = failure.getValidationException().getMessage();
        return SignerVerificationStatus.invalidSignature(userId, fingerprint, message);
    }

    private static String getPrimaryUserId(PGPPublicKey publicKey) {
        Iterator<String> userIds = publicKey.getUserIDs();
        if(userIds.hasNext()) {
            return userIds.next();
        }
        return Long.toHexString(publicKey.getKeyID()).toUpperCase(Locale.ROOT);
    }

    // Key ring loading mirrors drongo PGPUtils (private there); keep in sync when upgrading upstream.
    private static PGPPublicKeyRingCollection getApplicationKeyRingCollection() {
        List<PGPPublicKeyRing> rings = new ArrayList<>();
        try {
            String[] keyFiles = IOUtils.getResourceListing(PGPUtils.class, PGPUtils.APPLICATION_KEYRING_DIR);
            for(String keyFile : keyFiles) {
                try(InputStream pubkeyStream = PGPUtils.class.getResourceAsStream("/" + PGPUtils.APPLICATION_KEYRING_DIR + keyFile)) {
                    if(pubkeyStream != null) {
                        PGPPublicKeyRing keyRing = PGPainless.readKeyRing().publicKeyRing(pubkeyStream);
                        if(keyRing != null) {
                            rings.add(keyRing);
                        }
                    }
                }
            }
        } catch(Exception e) {
            log.warn("Error loading application key rings", e);
        }

        return new PGPPublicKeyRingCollection(rings);
    }

    private static PGPPublicKeyRingCollection getUserKeyRingCollection() {
        try {
            File gnupgHome = getGnuPGHome();
            if(gnupgHome.exists()) {
                File kbxPubRing = new File(gnupgHome, PGPUtils.PUBRING_KBX);
                if(kbxPubRing.exists()) {
                    BcKeyBox bcKeyBox = new BcKeyBox(new FileInputStream(kbxPubRing));
                    List<PGPPublicKeyRing> rings = new ArrayList<>();
                    for(KeyBlob keyBlob : bcKeyBox.getKeyBlobs()) {
                        if(keyBlob instanceof PublicKeyRingBlob publicKeyRingBlob) {
                            rings.add(publicKeyRingBlob.getPGPPublicKeyRing());
                        }
                    }
                    if(!rings.isEmpty()) {
                        return new PGPPublicKeyRingCollection(rings);
                    }
                }

                File gpgPubRing = new File(gnupgHome, PGPUtils.PUBRING_GPG);
                if(gpgPubRing.exists()) {
                    return PGPainless.readKeyRing().publicKeyRingCollection(new FileInputStream(gpgPubRing));
                }
            }
        } catch(Exception e) {
            log.warn("Error loading user key rings: " + e.getMessage());
        }

        return null;
    }

    private static File getGnuPGHome() {
        String gnupgHome = System.getenv("GNUPGHOME");
        if(gnupgHome != null && !gnupgHome.isEmpty()) {
            File envHome = new File(gnupgHome);
            if(envHome.exists()) {
                return envHome;
            }
        }

        if(isWindows()) {
            File winHome = new File(System.getenv("APPDATA"), "gnupg");
            if(winHome.exists()) {
                return winHome;
            }
        }

        return new File(System.getProperty("user.home"), ".gnupg");
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name");
        return (osName != null && osName.toLowerCase(Locale.ROOT).startsWith("windows"));
    }
}
