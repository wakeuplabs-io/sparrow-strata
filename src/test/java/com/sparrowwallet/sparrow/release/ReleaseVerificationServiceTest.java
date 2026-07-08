package com.sparrowwallet.sparrow.release;

import com.sparrowwallet.drongo.pgp.PGPUtils;
import com.sparrowwallet.drongo.pgp.PGPVerificationException;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.pgpainless.PGPainless;
import org.pgpainless.key.generation.type.rsa.RsaLength;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseVerificationServiceTest {
    private static final String MANIFEST = "abcd1234  sparrow-2.5.3-x86_64.deb\n";

    @Test
    void detachedSignatureIsArmored() throws Exception {
        PGPSecretKeyRing aliceKey = generateKey("Alice Test <alice@example.com>");
        String signature = signDetached(aliceKey);
        assertTrue(signature.contains("BEGIN PGP SIGNATURE"));
    }

    @Test
    void pgpUtilsVerifiesSingleDetachedSignature() throws Exception {
        PGPSecretKeyRing aliceKey = generateKey("Alice Test <alice@example.com>");
        try(ByteArrayInputStream manifestStream = new ByteArrayInputStream(MANIFEST.getBytes(StandardCharsets.UTF_8));
            ByteArrayInputStream signatureStream = new ByteArrayInputStream(signDetached(aliceKey).getBytes(StandardCharsets.UTF_8));
            ByteArrayInputStream publicKeyStream = new ByteArrayInputStream(PGPainless.asciiArmor(PGPainless.extractCertificate(aliceKey)).getBytes(StandardCharsets.UTF_8))) {
            assertNotNull(PGPUtils.verify(publicKeyStream, manifestStream, signatureStream));
        }
    }

    @Test
    void satisfiesPolicyWhenAllBundledKeysSigned() throws Exception {
        PGPSecretKeyRing aliceKey = generateKey("Alice Test <alice@example.com>");
        PGPSecretKeyRing bobKey = generateKey("Bob Test <bob@example.com>");
        PGPPublicKeyRingCollection applicationKeys = new PGPPublicKeyRingCollection(List.of(
            PGPainless.extractCertificate(aliceKey),
            PGPainless.extractCertificate(bobKey)
        ));

        String combinedSignature = signDetached(aliceKey) + signDetached(bobKey);
        ReleaseVerificationResult result = verify(MANIFEST, combinedSignature, applicationKeys);

        assertTrue(result.policySatisfied());
        assertEquals(2, result.bundledSigners().size());
        assertTrue(result.bundledSigners().stream().allMatch(signer -> signer.status() == SignerStatus.VALID));
        assertTrue(result.additionalSignatures().isEmpty());
    }

    @Test
    void reportsMissingBundledSigner() throws Exception {
        PGPSecretKeyRing aliceKey = generateKey("Alice Test <alice@example.com>");
        PGPSecretKeyRing bobKey = generateKey("Bob Test <bob@example.com>");
        PGPPublicKeyRingCollection applicationKeys = new PGPPublicKeyRingCollection(List.of(
            PGPainless.extractCertificate(aliceKey),
            PGPainless.extractCertificate(bobKey)
        ));

        ReleaseVerificationResult result = verify(MANIFEST, signDetached(aliceKey), applicationKeys);

        assertFalse(result.policySatisfied());
        assertEquals(1, result.missingSignerCount());
        assertTrue(result.bundledSigners().stream().anyMatch(signer -> signer.status() == SignerStatus.MISSING));
        assertTrue(result.getPolicyMessage().contains("Missing signatures from 1 of 2 trusted signers"));
    }

    @Test
    void ignoresTrailingNonSignatureContent() throws Exception {
        PGPSecretKeyRing aliceKey = generateKey("Alice Test <alice@example.com>");
        PGPSecretKeyRing bobKey = generateKey("Bob Test <bob@example.com>");
        PGPPublicKeyRingCollection applicationKeys = new PGPPublicKeyRingCollection(List.of(
            PGPainless.extractCertificate(aliceKey),
            PGPainless.extractCertificate(bobKey)
        ));

        String combinedSignature = signDetached(aliceKey) + signDetached(bobKey) + "not a signature block\n";
        ReleaseVerificationResult result = verify(MANIFEST, combinedSignature, applicationKeys);

        assertTrue(result.policySatisfied());
    }

    @Test
    void recordsInvalidSignatureBlocks() throws Exception {
        PGPSecretKeyRing aliceKey = generateKey("Alice Test <alice@example.com>");
        PGPPublicKeyRingCollection applicationKeys = new PGPPublicKeyRingCollection(List.of(PGPainless.extractCertificate(aliceKey)));

        String combinedSignature = signDetached(aliceKey) + "-----BEGIN PGP SIGNATURE-----\ninvalid\n-----END PGP SIGNATURE-----\n";
        ReleaseVerificationResult result = verify(MANIFEST, combinedSignature, applicationKeys);

        assertTrue(result.policySatisfied());
        assertFalse(result.invalidSignatures().isEmpty());
    }

    @Test
    void throwsWhenNoValidSignaturesFound() {
        PGPSecretKeyRing aliceKey = generateKey("Alice Test <alice@example.com>");
        PGPPublicKeyRingCollection applicationKeys = new PGPPublicKeyRingCollection(List.of(PGPainless.extractCertificate(aliceKey)));

        assertThrows(PGPVerificationException.class, () -> verify(MANIFEST, "-----BEGIN PGP SIGNATURE-----\ninvalid\n-----END PGP SIGNATURE-----\n", applicationKeys));
    }

    @Test
    void failsPolicyWhenNoBundledKeysConfigured() throws Exception {
        PGPSecretKeyRing aliceKey = generateKey("Alice Test <alice@example.com>");
        PGPPublicKeyRingCollection applicationKeys = new PGPPublicKeyRingCollection(List.of());

        try(ByteArrayInputStream manifestStream = new ByteArrayInputStream(MANIFEST.getBytes(StandardCharsets.UTF_8));
            ByteArrayInputStream signatureStream = new ByteArrayInputStream(signDetached(aliceKey).getBytes(StandardCharsets.UTF_8));
            ByteArrayInputStream publicKeyStream = new ByteArrayInputStream(PGPainless.asciiArmor(PGPainless.extractCertificate(aliceKey)).getBytes(StandardCharsets.UTF_8))) {
            ReleaseVerificationResult result = ReleaseVerificationService.verifyAll(publicKeyStream, manifestStream, signatureStream, applicationKeys);

            assertFalse(result.policySatisfied());
            assertTrue(result.bundledSigners().isEmpty());
            assertEquals(1, result.additionalSignatures().size());
        }
    }

    private static ReleaseVerificationResult verify(String manifest, String combinedSignature, PGPPublicKeyRingCollection applicationKeys) throws IOException, PGPVerificationException {
        try(ByteArrayInputStream manifestStream = new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8));
            ByteArrayInputStream signatureStream = new ByteArrayInputStream(combinedSignature.getBytes(StandardCharsets.UTF_8))) {
            return ReleaseVerificationService.verifyAll(null, manifestStream, signatureStream, applicationKeys);
        }
    }

    private static PGPSecretKeyRing generateKey(String userId) {
        try {
            return PGPainless.generateKeyRing().simpleRsaKeyRing(userId, RsaLength._3072);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String signDetached(PGPSecretKeyRing secretKeyRing) throws Exception {
        PGPSecretKey secretKey = secretKeyRing.getSecretKey();
        PGPPrivateKey privateKey = secretKey.extractPrivateKey(new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).build(null));

        PGPSignatureGenerator signatureGenerator = new PGPSignatureGenerator(new JcaPGPContentSignerBuilder(secretKey.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256));
        signatureGenerator.init(PGPSignature.BINARY_DOCUMENT, privateKey);
        signatureGenerator.update(MANIFEST.getBytes(StandardCharsets.UTF_8));
        PGPSignature signature = signatureGenerator.generate();

        ByteArrayOutputStream signatureStream = new ByteArrayOutputStream();
        try(ArmoredOutputStream armoredOutputStream = new ArmoredOutputStream(signatureStream)) {
            signature.encode(armoredOutputStream);
        }
        return signatureStream.toString(StandardCharsets.UTF_8);
    }
}
