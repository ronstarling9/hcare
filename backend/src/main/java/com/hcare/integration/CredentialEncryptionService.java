package com.hcare.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption/decryption service for integration credentials.
 *
 * <p>Key is sourced from the {@code INTEGRATION_ENCRYPTION_KEY} environment variable,
 * which must be a Base64-encoded 256-bit (32-byte) key. Encrypted values are stored
 * as {@code base64(iv):base64(ciphertext)}.
 */
@Service
public class CredentialEncryptionService {

  private static final Logger log = LoggerFactory.getLogger(CredentialEncryptionService.class);

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH_BYTES = 12; // 96-bit IV per NIST recommendation
  private static final int GCM_TAG_LENGTH_BITS = 128;
  private static final int REQUIRED_KEY_BYTES = 32; // 256-bit key

  private final String encodedKey;
  private final ObjectMapper objectMapper;

  private SecretKey secretKey;
  private SecureRandom secureRandom;

  public CredentialEncryptionService(
      @Value("${integration.encryption-key:}") String encodedKey,
      ObjectMapper objectMapper) {
    this.encodedKey = encodedKey;
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  void init() {
    if (encodedKey == null || encodedKey.isBlank()) {
      throw new IllegalStateException(
          "integration.encryption-key must be set (Base64-encoded 256-bit key)");
    }
    byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
    if (keyBytes.length != REQUIRED_KEY_BYTES) {
      throw new IllegalStateException(
          "integration.encryption-key must decode to exactly 32 bytes (256-bit); "
              + "got " + keyBytes.length + " bytes");
    }
    secretKey = new SecretKeySpec(keyBytes, "AES");
    secureRandom = new SecureRandom();
    log.info("CredentialEncryptionService initialized with AES-256-GCM key");
  }

  /**
   * Encrypts plaintext using AES-256-GCM with a random 96-bit IV.
   *
   * @param plaintext the value to encrypt
   * @return {@code base64(iv) + ":" + base64(ciphertext)}
   */
  public String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      return Base64.getEncoder().encodeToString(iv)
          + ":"
          + Base64.getEncoder().encodeToString(ciphertext);
    } catch (Exception e) {
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  /**
   * Encrypts a typed credential object by serializing it to JSON and applying AES-256-GCM.
   *
   * @param credential the credential object to encrypt
   * @param <T>        the credential type
   * @return {@code base64(iv) + ":" + base64(ciphertext)}
   */
  public <T> String encrypt(T credential) throws Exception {
    String json = objectMapper.writeValueAsString(credential);
    return encrypt(json);
  }

  /**
   * Decrypts an {@code iv:ciphertext} value and deserializes the JSON result into the given class.
   *
   * @param encrypted       the encrypted value in {@code base64(iv):base64(ciphertext)} format
   * @param credentialClass the target type for JSON deserialization
   * @param <T>             the credential type
   * @return the deserialized credential object
   */
  public <T> T decrypt(String encrypted, Class<T> credentialClass) {
    try {
      String[] parts = encrypted.split(":", 2);
      if (parts.length != 2) {
        throw new IllegalArgumentException(
            "Encrypted value must be in 'base64(iv):base64(ciphertext)' format");
      }

      byte[] iv = Base64.getDecoder().decode(parts[0]);
      byte[] ciphertext = Base64.getDecoder().decode(parts[1]);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

      byte[] plaintext = cipher.doFinal(ciphertext);
      return objectMapper.readValue(plaintext, credentialClass);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Decryption failed", e);
    }
  }
}
