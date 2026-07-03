package com.sevis.photoservice.service;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AES-256-GCM encryption with PBKDF2-derived keys.
 * The key is derived on-the-fly from the user's folder password — never persisted.
 * Encrypted format: [12-byte IV][ciphertext+16-byte GCM tag]
 *
 * Derived keys are cached in memory per (userId, saltHex, passwordHash) for KEY_CACHE_TTL_SECONDS
 * so gallery loads don't pay PBKDF2 cost on every photo.
 */
@Service
public class EncryptionService {

    private static final String KEY_ALGORITHM         = "PBKDF2WithHmacSHA256";
    private static final String CIPHER                = "AES/GCM/NoPadding";
    static final int            DEFAULT_PBKDF2_ITERS   = 120_000;
    private static final int    KEY_LENGTH_BITS        = 256;
    private static final int    GCM_IV_BYTES           = 12;
    private static final int    GCM_TAG_BITS           = 128;
    private static final long   KEY_CACHE_TTL_SECONDS  = 1800; // 30 min

    private record CacheEntry(SecretKey key, Instant expiresAt) {}

    private final Map<String, CacheEntry> keyCache = new ConcurrentHashMap<>();

    public String generateSaltHex() {
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    public byte[] encrypt(byte[] plaintext, String password, String saltHex, int iterations) {
        try {
            SecretKey key = getCachedKey(password, saltHex, iterations);
            byte[] iv = new byte[GCM_IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] result = new byte[GCM_IV_BYTES + ciphertext.length];
            System.arraycopy(iv,         0, result, 0,            GCM_IV_BYTES);
            System.arraycopy(ciphertext, 0, result, GCM_IV_BYTES, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] encrypted, String password, String saltHex, int iterations) {
        try {
            SecretKey key = getCachedKey(password, saltHex, iterations);
            byte[] iv         = new byte[GCM_IV_BYTES];
            byte[] ciphertext = new byte[encrypted.length - GCM_IV_BYTES];
            System.arraycopy(encrypted, 0,            iv,         0, GCM_IV_BYTES);
            System.arraycopy(encrypted, GCM_IV_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private SecretKey getCachedKey(String password, String saltHex, int iterations) throws Exception {
        String cacheKey = saltHex + ":" + iterations + ":" + Integer.toHexString(password.hashCode());
        Instant now = Instant.now();

        CacheEntry entry = keyCache.get(cacheKey);
        if (entry != null && entry.expiresAt().isAfter(now)) {
            return entry.key();
        }

        // Evict expired entries lazily to avoid unbounded growth
        keyCache.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));

        SecretKey key = deriveKey(password, HexFormat.of().parseHex(saltHex), iterations);
        keyCache.put(cacheKey, new CacheEntry(key, now.plusSeconds(KEY_CACHE_TTL_SECONDS)));
        return key;
    }

    private SecretKey deriveKey(String password, byte[] salt, int iterations) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
