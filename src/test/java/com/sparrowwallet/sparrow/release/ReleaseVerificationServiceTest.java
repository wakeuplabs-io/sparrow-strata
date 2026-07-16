package com.sparrowwallet.sparrow.release;

import com.sparrowwallet.drongo.pgp.PGPUtils;
import com.sparrowwallet.drongo.pgp.PGPVerificationException;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseVerificationServiceTest {
    private static final String MANIFEST = "abcd1234  sparrow-2.5.3-x86_64.deb\n";
    private static final char[] KEY_PASSPHRASE = "test".toCharArray();

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
            ByteArrayInputStream publicKeyStream = new ByteArrayInputStream(armorPublicKeyRing(extractPublicKeyRing(aliceKey)))) {
            assertNotNull(PGPUtils.verify(publicKeyStream, manifestStream, signatureStream));
        }
    }

    @Test
    void satisfiesPolicyWhenAllBundledKeysSigned() throws Exception {
        PGPSecretKeyRing aliceKey = generateKey("Alice Test <alice@example.com>");
        PGPSecretKeyRing bobKey = generateKey("Bob Test <bob@example.com>");
        List<byte[]> applicationKeys = List.of(
            armorPublicKeyRing(extractPublicKeyRing(aliceKey)),
            armorPublicKeyRing(extractPublicKeyRing(bobKey))
        );

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
        List<byte[]> applicationKeys = List.of(
            armorPublicKeyRing(extractPublicKeyRing(aliceKey)),
            armorPublicKeyRing(extractPublicKeyRing(bobKey))
        );

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
        List<byte[]> applicationKeys = List.of(
            armorPublicKeyRing(extractPublicKeyRing(aliceKey)),
            armorPublicKeyRing(extractPublicKeyRing(bobKey))
        );

        String combinedSignature = signDetached(aliceKey) + signDetached(bobKey) + "not a signature block\n";
        ReleaseVerificationResult result = verify(MANIFEST, combinedSignature, applicationKeys);

        assertTrue(result.policySatisfied());
    }

    @Test
    void recordsInvalidSignatureBlocks() throws Exception {
        PGPSecretKeyRing aliceKey = generateKey("Alice Test <alice@example.com>");
        List<byte[]> applicationKeys = List.of(armorPublicKeyRing(extractPublicKeyRing(aliceKey)));

        String combinedSignature = signDetached(aliceKey) + "-----BEGIN PGP SIGNATURE-----\ninvalid\n-----END PGP SIGNATURE-----\n";
        ReleaseVerificationResult result = verify(MANIFEST, combinedSignature, applicationKeys);

        assertTrue(result.policySatisfied());
        assertFalse(result.invalidSignatures().isEmpty());
    }

    @Test
    void throwsWhenNoValidSignaturesFound() throws Exception {
        PGPSecretKeyRing aliceKey = generateKey("Alice Test <alice@example.com>");
        List<byte[]> applicationKeys = List.of(armorPublicKeyRing(extractPublicKeyRing(aliceKey)));

        assertThrows(PGPVerificationException.class, () -> verify(MANIFEST, "-----BEGIN PGP SIGNATURE-----\ninvalid\n-----END PGP SIGNATURE-----\n", applicationKeys));
    }

    @Test
    void verifiesAllBundledSignersWhenUserSuppliesOnePublicKey() throws Exception {
        PGPSecretKeyRing aliceKey = generateKey("Alice Test <alice@example.com>");
        PGPSecretKeyRing bobKey = generateKey("Bob Test <bob@example.com>");
        List<byte[]> applicationKeys = List.of(
            armorPublicKeyRing(extractPublicKeyRing(aliceKey)),
            armorPublicKeyRing(extractPublicKeyRing(bobKey))
        );

        String combinedSignature = signDetached(aliceKey) + signDetached(bobKey);
        try(ByteArrayInputStream manifestStream = new ByteArrayInputStream(MANIFEST.getBytes(StandardCharsets.UTF_8));
            ByteArrayInputStream signatureStream = new ByteArrayInputStream(combinedSignature.getBytes(StandardCharsets.UTF_8));
            ByteArrayInputStream publicKeyStream = new ByteArrayInputStream(armorPublicKeyRing(extractPublicKeyRing(aliceKey)))) {
            ReleaseVerificationResult result = ReleaseVerificationService.verifyAll(publicKeyStream, manifestStream, signatureStream, applicationKeys);

            assertTrue(result.policySatisfied());
            assertTrue(result.invalidSignatures().isEmpty());
            assertEquals(2, result.bundledSigners().size());
            assertTrue(result.bundledSigners().stream().allMatch(signer -> signer.status() == SignerStatus.VALID));
        }
    }

    @Test
    void failsPolicyWhenNoBundledKeysConfigured() throws Exception {
        PGPSecretKeyRing aliceKey = generateKey("Alice Test <alice@example.com>");
        List<byte[]> applicationKeys = List.of();

        try(ByteArrayInputStream manifestStream = new ByteArrayInputStream(MANIFEST.getBytes(StandardCharsets.UTF_8));
            ByteArrayInputStream signatureStream = new ByteArrayInputStream(signDetached(aliceKey).getBytes(StandardCharsets.UTF_8));
            ByteArrayInputStream publicKeyStream = new ByteArrayInputStream(armorPublicKeyRing(extractPublicKeyRing(aliceKey)))) {
            ReleaseVerificationResult result = ReleaseVerificationService.verifyAll(publicKeyStream, manifestStream, signatureStream, applicationKeys);

            assertFalse(result.policySatisfied());
            assertTrue(result.bundledSigners().isEmpty());
            assertEquals(1, result.additionalSignatures().size());
        }
    }

    private static ReleaseVerificationResult verify(String manifest, String combinedSignature, List<byte[]> applicationKeys) throws IOException, PGPVerificationException {
        try(ByteArrayInputStream manifestStream = new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8));
            ByteArrayInputStream signatureStream = new ByteArrayInputStream(combinedSignature.getBytes(StandardCharsets.UTF_8))) {
            return ReleaseVerificationService.verifyAll(null, manifestStream, signatureStream, applicationKeys);
        }
    }

    private static PGPSecretKeyRing generateKey(String userId) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(3072);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PGPKeyPair pgpKeyPair = new JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, keyPair, new Date());
        BcPGPDigestCalculatorProvider digestCalculatorProvider = new BcPGPDigestCalculatorProvider();
        PGPDigestCalculator sha1DigestCalculator = digestCalculatorProvider.get(HashAlgorithmTags.SHA1);
        PGPSignatureSubpacketGenerator hashedSubpackets = new PGPSignatureSubpacketGenerator();
        hashedSubpackets.setKeyFlags(false, KeyFlags.SIGN_DATA | KeyFlags.CERTIFY_OTHER);

        PGPKeyRingGenerator keyRingGenerator = new PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            pgpKeyPair,
            userId,
            sha1DigestCalculator,
            hashedSubpackets.generate(),
            null,
            new JcaPGPContentSignerBuilder(PublicKeyAlgorithmTags.RSA_GENERAL, HashAlgorithmTags.SHA256),
            new BcPBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha1DigestCalculator).build(KEY_PASSPHRASE)
        );

        return keyRingGenerator.generateSecretKeyRing();
    }

    private static PGPPublicKeyRing extractPublicKeyRing(PGPSecretKeyRing secretKeyRing) {
        return secretKeyRing.toCertificate();
    }

    private static byte[] armorPublicKeyRing(PGPPublicKeyRing publicKeyRing) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try(ArmoredOutputStream armoredOutputStream = new ArmoredOutputStream(outputStream)) {
            publicKeyRing.encode(armoredOutputStream);
        }
        return outputStream.toByteArray();
    }

    private static String signDetached(PGPSecretKeyRing secretKeyRing) throws Exception {
        PGPSecretKey secretKey = secretKeyRing.getSecretKey();
        PGPPrivateKey privateKey = secretKey.extractPrivateKey(new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).build(KEY_PASSPHRASE));

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
