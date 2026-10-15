package dev.legatus.server.domain;

import static dev.legatus.server.domain.SecretEntryTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class SecretEntryTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(SecretEntry.class);
        SecretEntry secretEntry1 = getSecretEntrySample1();
        SecretEntry secretEntry2 = new SecretEntry();
        assertThat(secretEntry1).isNotEqualTo(secretEntry2);

        secretEntry2.setSecretKey(secretEntry1.getSecretKey());
        assertThat(secretEntry1).isEqualTo(secretEntry2);

        secretEntry2 = getSecretEntrySample2();
        assertThat(secretEntry1).isNotEqualTo(secretEntry2);
    }
}
