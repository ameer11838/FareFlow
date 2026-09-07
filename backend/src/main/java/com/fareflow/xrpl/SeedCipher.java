package com.fareflow.xrpl;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM for XRPL seed entropy at rest.
 *
 * <p>A custodial wallet is only as safe as the row that holds it. Encrypting the
 * entropy means a database dump — a leaked backup, an over-broad read replica, a
 * mis-scoped analytics grant — does not hand over the ability to move funds; the
 * attacker also needs the key, which lives in configuration and never in Postgres.
 *
 * <p>GCM rather than CBC because it authenticates as well as encrypts: a tampered
 * ciphertext fails to decrypt instead of yielding a different, valid-looking seed.
 */
public final class SeedCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SeedCipher(String base64Key) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "fareflow.xrpl.custody-key must be base64-encoded", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException(
                    "fareflow.xrpl.custody-key must decode to 32 bytes, got " + decoded.length);
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    /** A fresh nonce per encryption; reuse under one key would leak the plaintext. */
    public Sealed seal(byte[] plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new Sealed(cipher.doFinal(plaintext), nonce);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encrypt XRPL seed entropy", exception);
        }
    }

    public byte[] open(byte[] ciphertext, byte[] nonce) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(ciphertext);
        } catch (Exception exception) {
            // Deliberately terse: the reason a decrypt failed is not something to
            // narrate to a caller, and the exception itself must not carry key data.
            throw new IllegalStateException("Could not decrypt XRPL seed entropy");
        }
    }

    public record Sealed(byte[] ciphertext, byte[] nonce) {}
}
