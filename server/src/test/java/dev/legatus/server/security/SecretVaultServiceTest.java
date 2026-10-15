package dev.legatus.server.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.legatus.server.config.LegatusProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
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
}
