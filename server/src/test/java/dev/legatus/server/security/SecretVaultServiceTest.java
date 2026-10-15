package dev.legatus.server.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.legatus.server.config.LegatusProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit tests for {@link SecretVaultService}.
 */
class SecretVaultServiceTest {

    @Test
    @DisplayName("Should initialize with dev fallback key when key is omitted in dev profile")
    void testDevProfileFallback() throws GeneralSecurityException {
        LegatusProperties props = new LegatusProperties();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        SecretVaultService service = new SecretVaultService(props, env);
        service.init();

        byte[] secret = "database_password_secret_123".getBytes(StandardCharsets.UTF_8);
        SecretVaultService.EncryptedSecret encrypted = service.encrypt(secret);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted.cipherText()).isNotEmpty();
        assertThat(encrypted.iv()).hasSize(12);
        assertThat(encrypted.authTag()).hasSize(16);

        byte[] decrypted = service.decrypt(encrypted.cipherText(), encrypted.iv(), encrypted.authTag());
        assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo("database_password_secret_123");
    }

    @Test
    @DisplayName("Should generate unique IVs and ciphertexts for identical plaintext")
    void testUniqueIvsPerEncryption() {
        LegatusProperties props = new LegatusProperties();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        SecretVaultService service = new SecretVaultService(props, env);
        service.init();

        byte[] secret = "same_secret_payload".getBytes(StandardCharsets.UTF_8);
        SecretVaultService.EncryptedSecret enc1 = service.encrypt(secret);
        SecretVaultService.EncryptedSecret enc2 = service.encrypt(secret);

        assertThat(enc1.iv()).isNotEqualTo(enc2.iv());
        assertThat(enc1.cipherText()).isNotEqualTo(enc2.cipherText());
    }

    @Test
    @DisplayName("Should fail decryption when ciphertext, IV, or auth tag is tampered")
    void testTamperDetection() {
        LegatusProperties props = new LegatusProperties();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        SecretVaultService service = new SecretVaultService(props, env);
        service.init();

        byte[] secret = "tamper_evident_payload".getBytes(StandardCharsets.UTF_8);
        SecretVaultService.EncryptedSecret enc = service.encrypt(secret);

        // Tamper with ciphertext
        byte[] tamperedCipher = enc.cipherText().clone();
        tamperedCipher[0] ^= 0xFF;
        assertThatThrownBy(() -> service.decrypt(tamperedCipher, enc.iv(), enc.authTag()))
            .isInstanceOf(GeneralSecurityException.class);

        // Tamper with IV
        byte[] tamperedIv = enc.iv().clone();
        tamperedIv[0] ^= 0xFF;
        assertThatThrownBy(() -> service.decrypt(enc.cipherText(), tamperedIv, enc.authTag()))
            .isInstanceOf(GeneralSecurityException.class);

        // Tamper with auth tag
        byte[] tamperedTag = enc.authTag().clone();
        tamperedTag[0] ^= 0xFF;
        assertThatThrownBy(() -> service.decrypt(enc.cipherText(), enc.iv(), tamperedTag))
            .isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    @DisplayName("Should invoke exit code 78 and throw exception if master key missing in prod profile")
    void testProdMissingKeyAborts() {
        LegatusProperties props = new LegatusProperties();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        SecretVaultService service = new SecretVaultService(props, env);
        AtomicInteger exitCode = new AtomicInteger(-1);
        service.setExitHandler(exitCode::set);

        assertThatThrownBy(service::init)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exit 78");

        assertThat(exitCode.get()).isEqualTo(78);
    }

    @Test
    @DisplayName("Should initialize cleanly in prod profile when valid 32+ char key is configured")
    void testProdWithValidMasterKey() throws GeneralSecurityException {
        LegatusProperties props = new LegatusProperties();
        props.getSecurity().setMasterKey("super-secret-production-master-key-32chars!");
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        SecretVaultService service = new SecretVaultService(props, env);
        AtomicInteger exitCode = new AtomicInteger(-1);
        service.setExitHandler(exitCode::set);

        service.init();
        assertThat(exitCode.get()).isEqualTo(-1); // No exit invoked

        byte[] secret = "production_vault_item".getBytes(StandardCharsets.UTF_8);
        SecretVaultService.EncryptedSecret enc = service.encrypt(secret);
        byte[] decrypted = service.decrypt(enc.cipherText(), enc.iv(), enc.authTag());

        assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo("production_vault_item");
    }

    @Test
    @DisplayName("Should zeroize byte and char arrays")
    void testZeroize() {
        byte[] bytes = new byte[] { 1, 2, 3, 4, 5 };
        SecretVaultService.zeroize(bytes);
        assertThat(bytes).containsOnly((byte) 0);

        char[] chars = new char[] { 'p', 'a', 's', 's' };
        SecretVaultService.zeroize(chars);
        assertThat(chars).containsOnly('\0');
    }

    @Test
    @DisplayName("Should store secret, encrypt via AES-256-GCM, and immediately zeroize input plaintext buffer")
    void testStoreSecretAndZeroization() {
        LegatusProperties props = new LegatusProperties();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        dev.legatus.server.repository.SecretEntryRepository repo = org.mockito.Mockito.mock(dev.legatus.server.repository.SecretEntryRepository.class);
        org.mockito.Mockito.when(repo.save(org.mockito.ArgumentMatchers.any(dev.legatus.server.domain.SecretEntry.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        SecretVaultService service = new SecretVaultService(props, env, repo);
        service.init();

        byte[] plaintext = "super_confidential_db_secret_key".getBytes(StandardCharsets.UTF_8);
        byte[] copyBeforeWipe = plaintext.clone();

        dev.legatus.server.domain.SecretEntry saved = service.storeSecret("DB_CREDENTIALS", dev.legatus.server.domain.enumeration.SecretScope.GLOBAL, plaintext);

        assertThat(saved).isNotNull();
        assertThat(saved.getSecretKey()).isEqualTo("DB_CREDENTIALS");
        assertThat(saved.getScope()).isEqualTo(dev.legatus.server.domain.enumeration.SecretScope.GLOBAL);
        assertThat(saved.getCipherText()).isNotEqualTo(copyBeforeWipe);
        assertThat(saved.getGcmIv()).hasSize(12);
        assertThat(saved.getAuthTag()).hasSize(16);
        assertThat(saved.getUpdatedAt()).isNotNull();

        // Plaintext input buffer MUST be zeroized
        assertThat(plaintext).containsOnly((byte) 0);
    }

    @Test
    @DisplayName("Should decrypt stored secret from repository and zeroize string conversion buffer")
    void testGetDecryptedSecret() throws GeneralSecurityException {
        LegatusProperties props = new LegatusProperties();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        dev.legatus.server.repository.SecretEntryRepository repo = org.mockito.Mockito.mock(dev.legatus.server.repository.SecretEntryRepository.class);
        SecretVaultService service = new SecretVaultService(props, env, repo);
        service.init();

        SecretVaultService.EncryptedSecret enc = service.encrypt("api_token_abc_123".getBytes(StandardCharsets.UTF_8));
        dev.legatus.server.domain.SecretEntry entry = new dev.legatus.server.domain.SecretEntry()
            .secretKey("API_TOKEN")
            .scope(dev.legatus.server.domain.enumeration.SecretScope.CLUSTER)
            .cipherText(enc.cipherText())
            .cipherTextContentType("application/octet-stream")
            .gcmIv(enc.iv())
            .gcmIvContentType("application/octet-stream")
            .authTag(enc.authTag())
            .authTagContentType("application/octet-stream")
            .updatedAt(java.time.Instant.now());

        org.mockito.Mockito.when(repo.findById("API_TOKEN")).thenReturn(Optional.of(entry));

        Optional<byte[]> decryptedOpt = service.getDecryptedSecret("API_TOKEN");
        assertThat(decryptedOpt).isPresent();
        assertThat(new String(decryptedOpt.get(), StandardCharsets.UTF_8)).isEqualTo("api_token_abc_123");
        SecretVaultService.zeroize(decryptedOpt.get());

        Optional<String> strOpt = service.getDecryptedSecretAsString("API_TOKEN");
        assertThat(strOpt).contains("api_token_abc_123");
    }

    @Test
    @DisplayName("Should resolve multiple task secrets and guarantee zeroization in dispatch callback")
    void testWithDecryptedSecretsAndZeroization() throws Exception {
        LegatusProperties props = new LegatusProperties();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        dev.legatus.server.repository.SecretEntryRepository repo = org.mockito.Mockito.mock(dev.legatus.server.repository.SecretEntryRepository.class);
        SecretVaultService service = new SecretVaultService(props, env, repo);
        service.init();

        SecretVaultService.EncryptedSecret enc1 = service.encrypt("secret_value_one".getBytes(StandardCharsets.UTF_8));
        SecretVaultService.EncryptedSecret enc2 = service.encrypt("secret_value_two".getBytes(StandardCharsets.UTF_8));

        dev.legatus.server.domain.SecretEntry entry1 = new dev.legatus.server.domain.SecretEntry()
            .secretKey("SEC_ONE")
            .scope(dev.legatus.server.domain.enumeration.SecretScope.FUNCTION)
            .cipherText(enc1.cipherText())
            .cipherTextContentType("application/octet-stream")
            .gcmIv(enc1.iv())
            .gcmIvContentType("application/octet-stream")
            .authTag(enc1.authTag())
            .authTagContentType("application/octet-stream")
            .updatedAt(java.time.Instant.now());

        dev.legatus.server.domain.SecretEntry entry2 = new dev.legatus.server.domain.SecretEntry()
            .secretKey("SEC_TWO")
            .scope(dev.legatus.server.domain.enumeration.SecretScope.FUNCTION)
            .cipherText(enc2.cipherText())
            .cipherTextContentType("application/octet-stream")
            .gcmIv(enc2.iv())
            .gcmIvContentType("application/octet-stream")
            .authTag(enc2.authTag())
            .authTagContentType("application/octet-stream")
            .updatedAt(java.time.Instant.now());

        org.mockito.Mockito.when(repo.findById("SEC_ONE")).thenReturn(Optional.of(entry1));
        org.mockito.Mockito.when(repo.findById("SEC_TWO")).thenReturn(Optional.of(entry2));

        java.util.concurrent.atomic.AtomicBoolean callbackExecuted = new java.util.concurrent.atomic.AtomicBoolean(false);

        service.withDecryptedSecrets(java.util.List.of("SEC_ONE", "SEC_TWO"), resolved -> {
            callbackExecuted.set(true);
            assertThat(resolved).containsEntry("SEC_ONE", "secret_value_one");
            assertThat(resolved).containsEntry("SEC_TWO", "secret_value_two");
            return null;
        });

        assertThat(callbackExecuted).isTrue();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when secret requested for task dispatch is missing")
    void testWithDecryptedSecretsMissingSecret() {
        LegatusProperties props = new LegatusProperties();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        dev.legatus.server.repository.SecretEntryRepository repo = org.mockito.Mockito.mock(dev.legatus.server.repository.SecretEntryRepository.class);
        SecretVaultService service = new SecretVaultService(props, env, repo);
        service.init();

        org.mockito.Mockito.when(repo.findById("UNKNOWN_KEY")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withDecryptedSecrets(java.util.List.of("UNKNOWN_KEY"), resolved -> null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Secret not found in vault: UNKNOWN_KEY");
    }

    @Test
    @DisplayName("Should delete secret from repository")
    void testDeleteSecret() {
        LegatusProperties props = new LegatusProperties();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        dev.legatus.server.repository.SecretEntryRepository repo = org.mockito.Mockito.mock(dev.legatus.server.repository.SecretEntryRepository.class);
        SecretVaultService service = new SecretVaultService(props, env, repo);
        service.init();

        org.mockito.Mockito.when(repo.existsById("EXISTING_KEY")).thenReturn(true);
        org.mockito.Mockito.when(repo.existsById("MISSING_KEY")).thenReturn(false);

        assertThat(service.deleteSecret("EXISTING_KEY")).isTrue();
        org.mockito.Mockito.verify(repo).deleteById("EXISTING_KEY");

        assertThat(service.deleteSecret("MISSING_KEY")).isFalse();
    }
}
