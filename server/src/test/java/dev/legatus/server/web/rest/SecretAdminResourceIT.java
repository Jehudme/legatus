package dev.legatus.server.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.AuditLog;
import dev.legatus.server.domain.SecretEntry;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.domain.enumeration.SecretScope;
import dev.legatus.server.repository.AuditLogRepository;
import dev.legatus.server.repository.SecretEntryRepository;
import dev.legatus.server.security.SecretVaultService;
import dev.legatus.server.service.dto.CreateSecretRequest;
import dev.legatus.server.service.dto.UpdateSecretRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link SecretAdminResource} endpoints:
 * {@code /api/v1/admin/secrets/**} (RBAC enforcement, AES-256-GCM vault persistence, and WORM auditing).
 */
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class SecretAdminResourceIT {

    private static final String API_URL = "/api/v1/admin/secrets";
    private static final String TEST_SECRET_KEY = "STRIPE_API_KEY";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private SecretEntryRepository secretEntryRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SecretVaultService secretVaultService;

    @BeforeEach
    void setUp() {
        secretEntryRepository.findById(TEST_SECRET_KEY).ifPresent(secretEntryRepository::delete);
    }

    @Test
    @DisplayName("Should create envelope-encrypted secret and record WORM audit log as CLUSTER_ADMIN")
    @WithMockUser(username = "cluster_admin_user", roles = { "CLUSTER_ADMIN" })
    void testCreateSecretAsClusterAdmin() throws Exception {
        CreateSecretRequest request = new CreateSecretRequest(
            TEST_SECRET_KEY,
            SecretScope.GLOBAL,
            "sk_live_very_secret_stripe_token_999"
        );

        mockMvc.perform(post(API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.secret_key").value(TEST_SECRET_KEY))
            .andExpect(jsonPath("$.scope").value("GLOBAL"))
            .andExpect(jsonPath("$.updated_at").isNotEmpty())
            .andExpect(jsonPath("$.value").doesNotExist())
            .andExpect(jsonPath("$.cipher_text").doesNotExist());

        // Verify entity persisted in database
        Optional<SecretEntry> entryOpt = secretEntryRepository.findById(TEST_SECRET_KEY);
        assertThat(entryOpt).isPresent();
        SecretEntry entry = entryOpt.get();
        assertThat(entry.getScope()).isEqualTo(SecretScope.GLOBAL);
        assertThat(entry.getGcmIv()).hasSize(12);
        assertThat(entry.getAuthTag()).hasSize(16);
        assertThat(entry.getCipherText()).isNotNull();

        // Verify ciphertext can be properly decrypted by SecretVaultService
        Optional<String> decryptedOpt = secretVaultService.getDecryptedSecretAsString(TEST_SECRET_KEY);
        assertThat(decryptedOpt).contains("sk_live_very_secret_stripe_token_999");

        // Verify WORM audit log was generated
        Optional<AuditLog> latestAudit = auditLogRepository.findTopByOrderBySequenceNumDesc();
        assertThat(latestAudit).isPresent();
        assertThat(latestAudit.get().getEventType()).isEqualTo("SECRET_CREATED");
        assertThat(latestAudit.get().getTargetType()).isEqualTo("SECRET");
        assertThat(latestAudit.get().getTargetId()).isEqualTo(TEST_SECRET_KEY);
        assertThat(latestAudit.get().getActionStatus()).isEqualTo(AuditActionStatus.SUCCESS);
    }

    @Test
    @DisplayName("Should list vault secrets metadata without exposing ciphertext or plaintext")
    @WithMockUser(username = "cluster_admin_user", roles = { "CLUSTER_ADMIN" })
    void testListSecretsAsClusterAdmin() throws Exception {
        secretVaultService.storeSecret(TEST_SECRET_KEY, SecretScope.CLUSTER, "database_pass_123");

        mockMvc.perform(get(API_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$[?(@.secret_key == '" + TEST_SECRET_KEY + "')].scope").value(hasItem("CLUSTER")))
            .andExpect(jsonPath("$[*].value").doesNotExist())
            .andExpect(jsonPath("$[*].cipher_text").doesNotExist());
    }

    @Test
    @DisplayName("Should retrieve single secret metadata")
    @WithMockUser(username = "cluster_admin_user", roles = { "CLUSTER_ADMIN" })
    void testGetSecretAsClusterAdmin() throws Exception {
        secretVaultService.storeSecret(TEST_SECRET_KEY, SecretScope.FUNCTION, "function_key_xyz");

        mockMvc.perform(get(API_URL + "/" + TEST_SECRET_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.secret_key").value(TEST_SECRET_KEY))
            .andExpect(jsonPath("$.scope").value("FUNCTION"))
            .andExpect(jsonPath("$.value").doesNotExist());
    }

    @Test
    @DisplayName("Should update secret and record SECRET_UPDATED in WORM audit log")
    @WithMockUser(username = "cluster_admin_user", roles = { "CLUSTER_ADMIN" })
    void testUpdateSecretAsClusterAdmin() throws Exception {
        secretVaultService.storeSecret(TEST_SECRET_KEY, SecretScope.GLOBAL, "original_token");

        UpdateSecretRequest updateRequest = new UpdateSecretRequest(
            SecretScope.CLUSTER,
            "updated_token_payload_456"
        );

        mockMvc.perform(put(API_URL + "/" + TEST_SECRET_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.secret_key").value(TEST_SECRET_KEY))
            .andExpect(jsonPath("$.scope").value("CLUSTER"));

        Optional<String> decryptedOpt = secretVaultService.getDecryptedSecretAsString(TEST_SECRET_KEY);
        assertThat(decryptedOpt).contains("updated_token_payload_456");

        Optional<AuditLog> latestAudit = auditLogRepository.findTopByOrderBySequenceNumDesc();
        assertThat(latestAudit).isPresent();
        assertThat(latestAudit.get().getEventType()).isEqualTo("SECRET_UPDATED");
        assertThat(latestAudit.get().getTargetId()).isEqualTo(TEST_SECRET_KEY);
    }

    @Test
    @DisplayName("Should delete secret and record SECRET_DELETED in WORM audit log")
    @WithMockUser(username = "cluster_admin_user", roles = { "CLUSTER_ADMIN" })
    void testDeleteSecretAsClusterAdmin() throws Exception {
        secretVaultService.storeSecret(TEST_SECRET_KEY, SecretScope.GLOBAL, "doomed_secret");

        mockMvc.perform(delete(API_URL + "/" + TEST_SECRET_KEY))
            .andExpect(status().isNoContent());

        assertThat(secretEntryRepository.existsById(TEST_SECRET_KEY)).isFalse();

        Optional<AuditLog> latestAudit = auditLogRepository.findTopByOrderBySequenceNumDesc();
        assertThat(latestAudit).isPresent();
        assertThat(latestAudit.get().getEventType()).isEqualTo("SECRET_DELETED");
        assertThat(latestAudit.get().getTargetId()).isEqualTo(TEST_SECRET_KEY);

        mockMvc.perform(get(API_URL + "/" + TEST_SECRET_KEY))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should reject secret management with 403 Forbidden for OPERATOR role")
    @WithMockUser(username = "regular_operator", roles = { "OPERATOR" })
    void testRbacDenialForOperator() throws Exception {
        mockMvc.perform(get(API_URL))
            .andExpect(status().isForbidden());

        CreateSecretRequest request = new CreateSecretRequest(
            "FORBIDDEN_KEY",
            SecretScope.GLOBAL,
            "forbidden_value"
        );

        mockMvc.perform(post(API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should reject secret management with 403 Forbidden for AUDITOR role")
    @WithMockUser(username = "regular_auditor", roles = { "AUDITOR" })
    void testRbacDenialForAuditor() throws Exception {
        mockMvc.perform(get(API_URL))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should reject secret management with 401 Unauthorized for unauthenticated requests")
    void testRbacDenialForUnauthenticated() throws Exception {
        mockMvc.perform(get(API_URL))
            .andExpect(status().isUnauthorized());
    }
}
