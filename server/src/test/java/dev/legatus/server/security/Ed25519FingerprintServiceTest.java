package dev.legatus.server.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Ed25519FingerprintService}.
 */
class Ed25519FingerprintServiceTest {

    private Ed25519FingerprintService fingerprintService;

    @BeforeEach
    void setUp() {
        fingerprintService = new Ed25519FingerprintService();
    }

    @Test
    @DisplayName("Should derive deterministic uppercase hex fingerprint and 3-word visual code")
    void testDeriveDeterministic() {
        byte[] pubKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            pubKey[i] = (byte) i;
        }

        Ed25519FingerprintService.FingerprintResult result1 = fingerprintService.derive(pubKey);
        Ed25519FingerprintService.FingerprintResult result2 = fingerprintService.derive(pubKey);

        assertThat(result1).isNotNull();
        assertThat(result1.fingerprintHex())
            .matches("^[0-9A-F]{2}(:[0-9A-F]{2}){7}$")
            .isEqualTo(result2.fingerprintHex());

        assertThat(result1.visualCode())
            .matches("^[A-Za-z]+-[A-Za-z]+-\\d{2}$")
            .isEqualTo(result2.visualCode());

        assertThat(result1.sha256Digest()).hasSize(32);
    }

    @Test
    @DisplayName("Should derive correctly from Base64 encoded public key")
    void testDeriveFromBase64() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String b64 = Base64.getEncoder().encodeToString(raw);

        Ed25519FingerprintService.FingerprintResult expected = fingerprintService.derive(raw);
        Ed25519FingerprintService.FingerprintResult actual = fingerprintService.deriveFromBase64(b64);

        assertThat(actual.fingerprintHex()).isEqualTo(expected.fingerprintHex());
        assertThat(actual.visualCode()).isEqualTo(expected.visualCode());
    }

    @Test
    @DisplayName("Should derive correctly from Hex encoded public key")
    void testDeriveFromHex() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String hex = HexFormat.of().formatHex(raw);

        Ed25519FingerprintService.FingerprintResult expected = fingerprintService.derive(raw);
        Ed25519FingerprintService.FingerprintResult actual = fingerprintService.deriveFromHex(hex);

        assertThat(actual.fingerprintHex()).isEqualTo(expected.fingerprintHex());
        assertThat(actual.visualCode()).isEqualTo(expected.visualCode());
    }

    @Test
    @DisplayName("Should reject invalid public key sizes")
    void testRejectInvalidSizes() {
        assertThatThrownBy(() -> fingerprintService.derive(null))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> fingerprintService.derive(new byte[31]))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exactly 32 bytes");

        assertThatThrownBy(() -> fingerprintService.derive(new byte[33]))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exactly 32 bytes");
    }

    @Test
    @DisplayName("Should produce different fingerprints for different keys")
    void testDistinctKeysProduceDistinctFingerprints() {
        byte[] key1 = new byte[32];
        byte[] key2 = new byte[32];
        key2[0] = 1;

        Ed25519FingerprintService.FingerprintResult r1 = fingerprintService.derive(key1);
        Ed25519FingerprintService.FingerprintResult r2 = fingerprintService.derive(key2);

        assertThat(r1.fingerprintHex()).isNotEqualTo(r2.fingerprintHex());
    }
}
