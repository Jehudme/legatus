package dev.legatus.server.security;

import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.SecretEntry;
import dev.legatus.server.domain.enumeration.SecretScope;
import dev.legatus.server.repository.SecretEntryRepository;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing AES-256-GCM envelope encryption for guest secrets in {@code secrets_vault}.
 * <p>
 * Complies with {@code ADR-0001}, {@code server-spec.md} Section 5.2, and {@code GL-0011}:
 * <ul>
 *   <li>Derives a 256-bit AES master key via HKDF-SHA256 from {@code LEGATUS_MASTER_KEY}.</li>
 *   <li>Aborts startup with exit code {@code 78} ({@code EX_CONFIG}) if master key is missing or &lt; 32 chars in {@code prod}.</li>
 *   <li>Encrypts secrets using AES-256-GCM with unique 96-bit random IVs and 128-bit authentication tags.</li>
 *   <li>Provides zeroization utilities for wiping plaintext byte and char arrays in memory.</li>
 * </ul>
 */
@Service
public class SecretVaultService {

    private static final Logger LOG = LoggerFactory.getLogger(SecretVaultService.class);

    public static final int GCM_IV_LEN = 12; // 96 bits
    public static final int GCM_TAG_LEN_BITS = 128; // 16 bytes
    public static final int GCM_TAG_LEN_BYTES = 16;
    public static final int MIN_MASTER_KEY_LEN = 32;

    private static final byte[] HKDF_SALT = "legatus-vault-salt-v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HKDF_INFO = "legatus-aes-256-gcm-master-key".getBytes(StandardCharsets.UTF_8);
    private static final String DEV_FALLBACK_KEY = "legatus-default-dev-insecure-master-key-32chars";

    private final LegatusProperties legatusProperties;
    private final Environment environment;
    private final SecretEntryRepository secretEntryRepository;
    private final SecureRandom secureRandom;
    private Consumer<Integer> exitHandler = System::exit;

    private SecretKey derivedMasterKey;

    public SecretVaultService(LegatusProperties legatusProperties, Environment environment) {
        this(legatusProperties, environment, null);
    }

    public SecretVaultService(
        LegatusProperties legatusProperties,
        Environment environment,
        SecretEntryRepository secretEntryRepository
    ) {
        this.legatusProperties = legatusProperties;
        this.environment = environment;
        this.secretEntryRepository = secretEntryRepository;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Visible for testing: inject custom exit handler to assert exit code without JVM termination.
     */
    void setExitHandler(Consumer<Integer> exitHandler) {
        this.exitHandler = exitHandler;
    }

    @PostConstruct
    public void init() {
        String masterKey = resolveMasterKey();
        boolean isProd = environment != null && environment.acceptsProfiles(Profiles.of("prod"));

        if (masterKey == null || masterKey.trim().length() < MIN_MASTER_KEY_LEN) {
            if (isProd) {
                LOG.error(
                    "Production profile requires LEGATUS_MASTER_KEY environment variable or " +
                    "'legatus.security.master-key' property to be configured with at least {} characters. " +
                    "Aborting startup with exit code 78 (EX_CONFIG).",
                    MIN_MASTER_KEY_LEN
                );
                exitHandler.accept(78);
                throw new IllegalStateException("LEGATUS_MASTER_KEY missing or too short in prod profile (exit 78)");
            } else {
                LOG.warn(
                    "Using default ephemeral development master key for SecretVaultService. " +
                    "DO NOT USE IN PRODUCTION ENVIRONMENTS!"
                );
                masterKey = DEV_FALLBACK_KEY;
            }
        }

        this.derivedMasterKey = deriveAesKey(masterKey);
        LOG.info("SecretVaultService initialized successfully with HKDF-SHA256 derived AES-256-GCM key.");
    }

    /**
     * Resolves master key from environment variable {@code LEGATUS_MASTER_KEY} or configuration property.
     */
    private String resolveMasterKey() {
        String envKey = System.getenv("LEGATUS_MASTER_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        if (legatusProperties != null && legatusProperties.getSecurity() != null) {
            String propKey = legatusProperties.getSecurity().getMasterKey();
            if (propKey != null && !propKey.isBlank()) {
                return propKey.trim();
            }
        }
        return null;
    }

    /**
     * Derives a 256-bit AES SecretKey from input key material using RFC 5869 HKDF-SHA256.
     */
    private SecretKey deriveAesKey(String inputKeyMaterial) {
        byte[] ikm = inputKeyMaterial.getBytes(StandardCharsets.UTF_8);
        try {
            HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
            hkdf.init(new HKDFParameters(ikm, HKDF_SALT, HKDF_INFO));
            byte[] okm = new byte[32]; // 256-bit key
            hkdf.generateBytes(okm, 0, 32);
            return new SecretKeySpec(okm, "AES");
        } finally {
            zeroize(ikm);
        }
    }

    /**
     * Immutable container for encrypted envelope components matching {@code secrets_vault} schema.
     */
    public record EncryptedSecret(
        byte[] cipherText,
        byte[] iv,
        byte[] authTag
    ) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EncryptedSecret that = (EncryptedSecret) o;
            return Arrays.equals(cipherText, that.cipherText) &&
                   Arrays.equals(iv, that.iv) &&
                   Arrays.equals(authTag, that.authTag);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(cipherText);
            result = 31 * result + Arrays.hashCode(iv);
            result = 31 * result + Arrays.hashCode(authTag);
            return result;
        }
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with a unique 96-bit random IV.
     *
     * @param plaintext raw secret bytes
     * @return {@link EncryptedSecret} containing ciphertext, IV, and auth tag
     */
    public EncryptedSecret encrypt(byte[] plaintext) {
        Objects.requireNonNull(plaintext, "plaintext cannot be null");
        if (derivedMasterKey == null) {
            throw new IllegalStateException("SecretVaultService is not initialized");
        }

        byte[] iv = new byte[GCM_IV_LEN];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, derivedMasterKey, new GCMParameterSpec(GCM_TAG_LEN_BITS, iv));
            byte[] combined = cipher.doFinal(plaintext);

            // JCE AES/GCM appends the 16-byte authentication tag at the end of ciphertext
            int cipherTextLen = combined.length - GCM_TAG_LEN_BYTES;
            byte[] cipherText = Arrays.copyOfRange(combined, 0, cipherTextLen);
            byte[] authTag = Arrays.copyOfRange(combined, cipherTextLen, combined.length);

            return new EncryptedSecret(cipherText, iv, authTag);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt secret via AES-256-GCM", e);
        }
    }

    /**
     * Decrypts ciphertext using AES-256-GCM and verifies authentication tag integrity.
     *
     * @param cipherText raw ciphertext
     * @param iv 12-byte initialization vector
     * @param authTag 16-byte authentication tag
     * @return decrypted plaintext bytes
     * @throws GeneralSecurityException if authentication tag does not match or ciphertext is corrupt
     */
    public byte[] decrypt(byte[] cipherText, byte[] iv, byte[] authTag) throws GeneralSecurityException {
        Objects.requireNonNull(cipherText, "cipherText cannot be null");
        Objects.requireNonNull(iv, "iv cannot be null");
        Objects.requireNonNull(authTag, "authTag cannot be null");

        if (derivedMasterKey == null) {
            throw new IllegalStateException("SecretVaultService is not initialized");
        }
        if (iv.length != GCM_IV_LEN) {
            throw new IllegalArgumentException("IV must be exactly " + GCM_IV_LEN + " bytes");
        }
        if (authTag.length != GCM_TAG_LEN_BYTES) {
            throw new IllegalArgumentException("Auth tag must be exactly " + GCM_TAG_LEN_BYTES + " bytes");
        }

        // Recombine ciphertext and auth tag for JCE GCM decryption
        byte[] combined = new byte[cipherText.length + authTag.length];
        System.arraycopy(cipherText, 0, combined, 0, cipherText.length);
        System.arraycopy(authTag, 0, combined, cipherText.length, authTag.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, derivedMasterKey, new GCMParameterSpec(GCM_TAG_LEN_BITS, iv));
        return cipher.doFinal(combined);
    }

    /**
     * Wipes sensitive byte arrays from JVM memory by filling with zeros.
     */
    public static void zeroize(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }

    /**
     * Wipes sensitive char arrays from JVM memory by filling with null characters.
     */
    public static void zeroize(char[] data) {
        if (data != null) {
            Arrays.fill(data, '\0');
        }
    }

    /**
     * Functional callback interface for consuming decrypted secrets with automatic zeroization.
     */
    @FunctionalInterface
    public interface SecretDispatchCallback<T> {
        T dispatch(Map<String, String> resolvedSecrets) throws Exception;
    }

    /**
     * Encrypts and persists a secret in the {@code secrets_vault} table.
     * The input plaintext byte array is zeroed immediately after encryption.
     *
     * @param secretKey unique identifier of the secret
     * @param scope secret scope tier (GLOBAL, CLUSTER, FUNCTION)
     * @param plaintext raw plaintext bytes (will be zeroed)
     * @return persisted {@link SecretEntry} entity
     */
    @Transactional
    public SecretEntry storeSecret(String secretKey, SecretScope scope, byte[] plaintext) {
        Objects.requireNonNull(secretKey, "secretKey cannot be null");
        Objects.requireNonNull(scope, "scope cannot be null");
        Objects.requireNonNull(plaintext, "plaintext cannot be null");

        if (secretEntryRepository == null) {
            throw new IllegalStateException("SecretEntryRepository is not configured in SecretVaultService");
        }

        EncryptedSecret encrypted = encrypt(plaintext);
        try {
            SecretEntry entry = secretEntryRepository.findById(secretKey).orElseGet(SecretEntry::new);
            entry.setSecretKey(secretKey);
            entry.setScope(scope);
            entry.setCipherText(encrypted.cipherText());
            entry.setCipherTextContentType("application/octet-stream");
            entry.setGcmIv(encrypted.iv());
            entry.setGcmIvContentType("application/octet-stream");
            entry.setAuthTag(encrypted.authTag());
            entry.setAuthTagContentType("application/octet-stream");
            entry.setUpdatedAt(Instant.now());
            entry.setIsPersisted();
            return secretEntryRepository.save(entry);
        } finally {
            zeroize(plaintext);
        }
    }

    /**
     * Encrypts and persists a string secret.
     */
    @Transactional
    public SecretEntry storeSecret(String secretKey, SecretScope scope, String plaintextContent) {
        Objects.requireNonNull(plaintextContent, "plaintextContent cannot be null");
        byte[] bytes = plaintextContent.getBytes(StandardCharsets.UTF_8);
        return storeSecret(secretKey, scope, bytes);
    }

    /**
     * Decrypts a secret from the vault and returns its raw bytes.
     * Callers MUST call {@link #zeroize(byte[])} on the returned buffer when finished.
     */
    @Transactional(readOnly = true)
    public Optional<byte[]> getDecryptedSecret(String secretKey) throws GeneralSecurityException {
        Objects.requireNonNull(secretKey, "secretKey cannot be null");
        if (secretEntryRepository == null) {
            throw new IllegalStateException("SecretEntryRepository is not configured in SecretVaultService");
        }
        Optional<SecretEntry> entryOpt = secretEntryRepository.findById(secretKey);
        if (entryOpt.isEmpty()) {
            return Optional.empty();
        }
        SecretEntry entry = entryOpt.get();
        return Optional.of(decrypt(entry.getCipherText(), entry.getGcmIv(), entry.getAuthTag()));
    }

    /**
     * Decrypts a secret from the vault and returns its string representation.
     */
    @Transactional(readOnly = true)
    public Optional<String> getDecryptedSecretAsString(String secretKey) throws GeneralSecurityException {
        Optional<byte[]> bytesOpt = getDecryptedSecret(secretKey);
        if (bytesOpt.isEmpty()) {
            return Optional.empty();
        }
        byte[] bytes = bytesOpt.get();
        try {
            return Optional.of(new String(bytes, StandardCharsets.UTF_8));
        } finally {
            zeroize(bytes);
        }
    }

    /**
     * Deletes a secret from the vault by its key.
     */
    @Transactional
    public boolean deleteSecret(String secretKey) {
        Objects.requireNonNull(secretKey, "secretKey cannot be null");
        if (secretEntryRepository == null) {
            throw new IllegalStateException("SecretEntryRepository is not configured in SecretVaultService");
        }
        if (secretEntryRepository.existsById(secretKey)) {
            secretEntryRepository.deleteById(secretKey);
            return true;
        }
        return false;
    }

    /**
     * Resolves task secrets by keys, constructs the secrets map, executes the dispatch action,
     * and strictly guarantees in a finally-block that all decrypted plaintext byte buffers are zeroized.
     */
    @Transactional(readOnly = true)
    public <T> T withDecryptedSecrets(Collection<String> secretKeys, SecretDispatchCallback<T> callback) throws Exception {
        Objects.requireNonNull(callback, "callback cannot be null");
        if (secretKeys == null || secretKeys.isEmpty()) {
            return callback.dispatch(Map.of());
        }
        if (secretEntryRepository == null) {
            throw new IllegalStateException("SecretEntryRepository is not configured in SecretVaultService");
        }

        List<byte[]> plaintextBuffers = new ArrayList<>();
        Map<String, String> resolvedSecrets = new HashMap<>();
        try {
            for (String key : secretKeys) {
                SecretEntry entry = secretEntryRepository.findById(key)
                    .orElseThrow(() -> new IllegalArgumentException("Secret not found in vault: " + key));
                byte[] plaintext = decrypt(entry.getCipherText(), entry.getGcmIv(), entry.getAuthTag());
                plaintextBuffers.add(plaintext);
                resolvedSecrets.put(key, new String(plaintext, StandardCharsets.UTF_8));
            }
            return callback.dispatch(resolvedSecrets);
        } finally {
            for (byte[] buf : plaintextBuffers) {
                zeroize(buf);
            }
            resolvedSecrets.clear();
        }
    }
}
