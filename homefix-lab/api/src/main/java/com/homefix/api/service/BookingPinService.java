package com.homefix.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

@Service
public class BookingPinService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final PasswordEncoder passwordEncoder;
    private final SecretKeySpec encryptionKey;

    public BookingPinService(
            PasswordEncoder passwordEncoder,
            @Value("${app.security.pin-encryption-key:${app.security.jwt-secret}}") String encryptionSecret) {
        this.passwordEncoder = passwordEncoder;
        try {
            byte[] key = MessageDigest.getInstance("SHA-256")
                    .digest(encryptionSecret.getBytes(StandardCharsets.UTF_8));
            this.encryptionKey = new SecretKeySpec(key, "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize booking PIN encryption.", exception);
        }
    }

    public String generate() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    public String hash(String pin) {
        return passwordEncoder.encode(pin);
    }

    public boolean matches(String pin, String hash) {
        return pin != null && pin.matches("[0-9]{6}")
                && hash != null && passwordEncoder.matches(pin, hash);
    }

    public String encrypt(String pin) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(pin.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length)
                            .put(nonce).put(ciphertext).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to protect booking PIN.", exception);
        }
    }

    public String decrypt(String encryptedPin) {
        try {
            byte[] payload = Base64.getUrlDecoder().decode(encryptedPin);
            if (payload.length <= NONCE_BYTES) {
                throw new GeneralSecurityException("Encrypted booking PIN is invalid.");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] ciphertext = new byte[payload.length - NONCE_BYTES];
            ByteBuffer.wrap(payload).get(nonce).get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to read booking PIN.", exception);
        }
    }
}