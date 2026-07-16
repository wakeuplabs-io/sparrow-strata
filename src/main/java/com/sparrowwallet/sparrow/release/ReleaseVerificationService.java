package com.sparrowwallet.sparrow.release;

import com.sparrowwallet.drongo.IOUtils;
import com.sparrowwallet.drongo.pgp.PGPUtils;
import com.sparrowwallet.drongo.pgp.PGPVerificationException;
import com.sparrowwallet.drongo.pgp.PGPVerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReleaseVerificationService {
    private static final Logger log = LoggerFactory.getLogger(ReleaseVerificationService.class);

    private ReleaseVerificationService() {
    }

    public static ReleaseVerificationResult verifyAll(InputStream publicKeyStream, InputStream contentStream, InputStream detachedSignatureStream) throws IOException, PGPVerificationException {
        return verifyAll(publicKeyStream, contentStream, detachedSignatureStream, null);
    }

    public static ReleaseVerificationResult verifyAll(InputStream publicKeyStream, InputStream contentStream, InputStream detachedSignatureStream, List<byte[]> armoredPublicKeysOverride) throws IOException, PGPVerificationException {
        byte[] publicKeyBytes = publicKeyStream != null ? publicKeyStream.readAllBytes() : null;
        byte[] contentBytes = contentStream.readAllBytes();
        String[] bundledKeyFiles = listBundledKeyFiles();
        List<String> signatureBlocks = detachedSignatureStream != null ? splitSignatureBlocks(detachedSignatureStream.readAllBytes()) : List.of();

        try {
            List<PGPVerificationResult> verifiedResults = new ArrayList<>();
            Map<Long, PGPVerificationResult> verifiedByKeyId = new HashMap<>();
            List<SignerVerificationStatus> invalidSignatures = new ArrayList<>();

            if(!signatureBlocks.isEmpty()) {
                for(String signatureBlock : signatureBlocks) {
                    verifySignatureBlock(contentBytes, publicKeyBytes, signatureBlock, armoredPublicKeysOverride, verifiedResults, verifiedByKeyId, invalidSignatures);
                }
            } else {
                verifySignatureBlock(contentBytes, publicKeyBytes, null, armoredPublicKeysOverride, verifiedResults, verifiedByKeyId, invalidSignatures);
            }

            if(verifiedResults.isEmpty()) {
                if(!invalidSignatures.isEmpty()) {
                    throw new PGPVerificationException(invalidSignatures.getFirst().message());
                }
                throw new PGPVerificationException("No signatures found");
            }

            List<SignerVerificationStatus> bundledSigners = buildBundledSignerStatuses(bundledKeyFiles, armoredPublicKeysOverride, contentBytes, signatureBlocks, verifiedByKeyId);
            List<PGPVerificationResult> additionalSignatures = collectAdditionalSignatures(bundledSigners, verifiedResults);
            boolean policySatisfied = !bundledSigners.isEmpty() && bundledSigners.stream().allMatch(signer -> signer.status() == SignerStatus.VALID);

            return new ReleaseVerificationResult(bundledSigners, additionalSignatures, invalidSignatures, policySatisfied);
        } catch(PGPVerificationException e) {
            throw e;
        } catch(Exception e) {
            log.warn("Failed to verify release signatures", e);
            throw new PGPVerificationException(e.getMessage());
        }
    }

    private static void verifySignatureBlock(byte[] contentBytes, byte[] publicKeyBytes, String signatureBlock, List<byte[]> armoredPublicKeysOverride,
                                             List<PGPVerificationResult> verifiedResults, Map<Long, PGPVerificationResult> verifiedByKeyId, List<SignerVerificationStatus> invalidSignatures) throws PGPVerificationException, IOException {
        List<InputStream> publicKeyStreams = new ArrayList<>();
        if(publicKeyBytes != null && publicKeyBytes.length > 0) {
            publicKeyStreams.add(new ByteArrayInputStream(publicKeyBytes));
        }
        if(armoredPublicKeysOverride != null) {
            for(byte[] armoredPublicKey : armoredPublicKeysOverride) {
                publicKeyStreams.add(new ByteArrayInputStream(armoredPublicKey));
            }
        }
        publicKeyStreams.add(null);

        PGPVerificationException lastFailure = null;
        for(InputStream publicKeyStream : publicKeyStreams) {
            try(ByteArrayInputStream contentStream = new ByteArrayInputStream(contentBytes);
                InputStream detachedSignatureStream = signatureBlock != null ? new ByteArrayInputStream(signatureBlock.getBytes(StandardCharsets.UTF_8)) : null) {
                PGPVerificationResult result = PGPUtils.verify(publicKeyStream, contentStream, detachedSignatureStream);
                if(verifiedByKeyId.putIfAbsent(result.keyId(), result) == null) {
                    verifiedResults.add(result);
                }
                return;
            } catch(PGPVerificationException e) {
                lastFailure = e;
            }
        }

        if(signatureBlock != null) {
            invalidSignatures.add(SignerVerificationStatus.invalidSignature("", "", lastFailure != null ? lastFailure.getMessage() : "No signatures found"));
        } else if(lastFailure != null) {
            throw lastFailure;
        }
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

    private static List<SignerVerificationStatus> buildBundledSignerStatuses(String[] bundledKeyFiles, List<byte[]> armoredPublicKeysOverride, byte[] contentBytes, List<String> signatureBlocks, Map<Long, PGPVerificationResult> verifiedByKeyId) {
        List<SignerVerificationStatus> bundledSigners = new ArrayList<>();
        Set<Long> seenKeyIds = new HashSet<>();
        Set<String> seenKeyFiles = new HashSet<>();
        Set<Integer> seenArmoredKeyHashes = new HashSet<>();

        if(armoredPublicKeysOverride != null) {
            for(byte[] armoredPublicKey : armoredPublicKeysOverride) {
                addBundledSigner(bundledSigners, seenKeyIds, seenKeyFiles, seenArmoredKeyHashes, armoredPublicKey, null, contentBytes, signatureBlocks, verifiedByKeyId);
            }
            return bundledSigners;
        }

        for(String keyFile : bundledKeyFiles) {
            byte[] armoredPublicKey = readBundledKeyBytes(keyFile);
            if(armoredPublicKey != null) {
                addBundledSigner(bundledSigners, seenKeyIds, seenKeyFiles, seenArmoredKeyHashes, armoredPublicKey, keyFile, contentBytes, signatureBlocks, verifiedByKeyId);
            }
        }

        return bundledSigners;
    }

    private static void addBundledSigner(List<SignerVerificationStatus> bundledSigners, Set<Long> seenKeyIds, Set<String> seenKeyFiles, Set<Integer> seenArmoredKeyHashes, byte[] armoredPublicKey, String keyFile, byte[] contentBytes, List<String> signatureBlocks, Map<Long, PGPVerificationResult> verifiedByKeyId) {
        long keyId = resolveKeyId(armoredPublicKey, contentBytes, signatureBlocks);
        if(keyId != 0L) {
            if(!seenKeyIds.add(keyId)) {
                return;
            }
        } else if(keyFile != null) {
            if(!seenKeyFiles.add(keyFile)) {
                return;
            }
        } else if(!seenArmoredKeyHashes.add(java.util.Arrays.hashCode(armoredPublicKey))) {
            return;
        }

        PGPVerificationResult verificationResult = keyId != 0L ? verifiedByKeyId.get(keyId) : null;
        String userId = verificationResult != null ? verificationResult.userId() : userIdFromKeyFilename(keyFile);
        String fingerprint = verificationResult != null ? verificationResult.fingerprint() : "";

        SignerStatus status;
        if(verificationResult == null) {
            status = SignerStatus.MISSING;
        } else if(verificationResult.expired()) {
            status = SignerStatus.EXPIRED;
        } else {
            status = SignerStatus.VALID;
        }

        bundledSigners.add(SignerVerificationStatus.bundledSigner(keyId, userId, fingerprint, verificationResult, status));
    }

    private static long resolveKeyId(byte[] armoredPublicKey, byte[] contentBytes, List<String> signatureBlocks) {
        for(String signatureBlock : signatureBlocks) {
            try(ByteArrayInputStream publicKeyStream = new ByteArrayInputStream(armoredPublicKey);
                ByteArrayInputStream contentStream = new ByteArrayInputStream(contentBytes);
                ByteArrayInputStream signatureStream = new ByteArrayInputStream(signatureBlock.getBytes(StandardCharsets.UTF_8))) {
                return PGPUtils.verify(publicKeyStream, contentStream, signatureStream).keyId();
            } catch(PGPVerificationException | IOException ignored) {
                // Try the next signature block for this public key.
            }
        }

        return 0L;
    }

    private static List<PGPVerificationResult> collectAdditionalSignatures(List<SignerVerificationStatus> bundledSigners, List<PGPVerificationResult> verifiedResults) {
        Set<Long> bundledKeyIds = new HashSet<>();
        for(SignerVerificationStatus signer : bundledSigners) {
            if(signer.expectedKeyId() != 0L) {
                bundledKeyIds.add(signer.expectedKeyId());
            }
        }

        List<PGPVerificationResult> additionalSignatures = new ArrayList<>();
        for(PGPVerificationResult result : verifiedResults) {
            if(!bundledKeyIds.contains(result.keyId())) {
                additionalSignatures.add(result);
            }
        }
        return additionalSignatures;
    }

    private static String userIdFromKeyFilename(String keyFile) {
        if(keyFile == null || keyFile.isBlank()) {
            return "";
        }

        String baseName = keyFile.endsWith(".asc") ? keyFile.substring(0, keyFile.length() - 4) : keyFile;
        int emailSeparator = baseName.lastIndexOf('_');
        if(emailSeparator <= 0 || emailSeparator == baseName.length() - 1) {
            return baseName;
        }

        String email = baseName.substring(emailSeparator + 1);
        String namePart = baseName.substring(0, emailSeparator).replace('_', ' ');
        return namePart + " <" + email + ">";
    }

    private static String[] listBundledKeyFiles() {
        try {
            return IOUtils.getResourceListing(PGPUtils.class, PGPUtils.APPLICATION_KEYRING_DIR);
        } catch(Exception e) {
            log.warn("Error listing application key rings", e);
            return new String[0];
        }
    }

    private static byte[] readBundledKeyBytes(String keyFile) {
        try(InputStream pubkeyStream = PGPUtils.class.getResourceAsStream("/" + PGPUtils.APPLICATION_KEYRING_DIR + keyFile)) {
            return pubkeyStream != null ? pubkeyStream.readAllBytes() : null;
        } catch(IOException e) {
            log.warn("Error loading application key ring " + keyFile, e);
            return null;
        }
    }
}
