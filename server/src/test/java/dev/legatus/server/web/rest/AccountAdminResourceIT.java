package dev.legatus.server.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.Account;
import dev.legatus.server.domain.enumeration.AccountStatus;
import dev.legatus.server.domain.enumeration.OperatorRole;
import dev.legatus.server.repository.AccountRepository;
import dev.legatus.server.security.Argon2idService;
import dev.legatus.server.service.dto.CreateAccountRequest;
import dev.legatus.server.service.dto.MutateAccountStatusRequest;
import dev.legatus.server.service.dto.UpdateAccountRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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
 * Integration tests for {@link AccountAdminResource} endpoints:
 * {@code /api/v1/admin/accounts/**} (RBAC enforcement, CRUD, and soft-delete).
 */
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class AccountAdminResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private Argon2idService argon2idService;

    private static final String TARGET_USERNAME = "managed-operator";
    private Account managedAccount;

    @BeforeEach
    void setUp() {
        accountRepository.findOneByUsernameIgnoreCase(TARGET_USERNAME).ifPresent(accountRepository::delete);

        managedAccount = new Account()
            .username(TARGET_USERNAME)
            .email("managed@legatus.corp")
            .role(OperatorRole.OPERATOR)
            .status(AccountStatus.ACTIVE)
            .passwordHash(argon2idService.hash("InitialComplexPass2026!"))
            .failedLoginAttempts(0)
            .lockoutUntil(null)
            .createdAt(Instant.now().truncatedTo(ChronoUnit.MILLIS))
            .updatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));

        managedAccount = accountRepository.saveAndFlush(managedAccount);
    }

    @Test
    @WithMockUser(username = "admin_user", roles = { "CLUSTER_ADMIN" })
    @DisplayName("Should list accounts with CLUSTER_ADMIN role and never expose password digests")
    void testListAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accounts?page=0&size=10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", not(empty())))
            .andExpect(jsonPath("$.content[*].password_hash").doesNotExist())
            .andExpect(jsonPath("$.content[*].passwordHash").doesNotExist())
            .andExpect(jsonPath("$.content[*].username", hasItem(TARGET_USERNAME)));
    }

    @Test
    @WithMockUser(username = "regular_auditor", roles = { "AUDITOR" })
    @DisplayName("Should deny access with 403 Forbidden when caller lacks CLUSTER_ADMIN role")
    void testListAccountsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accounts"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = { "CLUSTER_ADMIN" })
    @DisplayName("Should create new operator account with status ACTIVE and hashed password")
    void testCreateAccountSuccess() throws Exception {
        String newUsername = "new-cluster-auditor";
        CreateAccountRequest req = new CreateAccountRequest(
            newUsername,
            "auditor@external.compliance",
            OperatorRole.AUDITOR,
            "SuperAuditorPass2026!"
        );

        mockMvc.perform(post("/api/v1/admin/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.account_id", notNullValue()))
            .andExpect(jsonPath("$.username", is(newUsername)))
            .andExpect(jsonPath("$.role", is("AUDITOR")))
            .andExpect(jsonPath("$.status", is("ACTIVE")))
            .andExpect(jsonPath("$.password_hash").doesNotExist());

        Account created = accountRepository.findOneByUsernameIgnoreCase(newUsername).orElseThrow();
        assertThat(created.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(argon2idService.verify("SuperAuditorPass2026!", created.getPasswordHash())).isTrue();
    }

    @Test
    @WithMockUser(username = "admin_user", roles = { "CLUSTER_ADMIN" })
    @DisplayName("Should reject account creation when username already exists with 409 Conflict")
    void testCreateAccountConflict() throws Exception {
        CreateAccountRequest req = new CreateAccountRequest(
            TARGET_USERNAME,
            "another@email.corp",
            OperatorRole.OPERATOR,
            "ValidPassword1234!"
        );

        mockMvc.perform(post("/api/v1/admin/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(req)))
            .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = { "CLUSTER_ADMIN" })
    @DisplayName("Should update account role and email")
    void testUpdateAccount() throws Exception {
        UpdateAccountRequest req = new UpdateAccountRequest(
            "promoted-operator@legatus.corp",
            OperatorRole.CLUSTER_ADMIN
        );

        mockMvc.perform(put("/api/v1/admin/accounts/" + managedAccount.getAccountId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email", is("promoted-operator@legatus.corp")))
            .andExpect(jsonPath("$.role", is("CLUSTER_ADMIN")));

        Account updated = accountRepository.findById(managedAccount.getAccountId()).orElseThrow();
        assertThat(updated.getRole()).isEqualTo(OperatorRole.CLUSTER_ADMIN);
        assertThat(updated.getEmail()).isEqualTo("promoted-operator@legatus.corp");
    }

    @Test
    @WithMockUser(username = "admin_user", roles = { "CLUSTER_ADMIN" })
    @DisplayName("Should mutate account status between lifecycle states")
    void testMutateStatus() throws Exception {
        MutateAccountStatusRequest req = new MutateAccountStatusRequest(
            AccountStatus.SUSPENDED,
            "Administrative suspension for maintenance"
        );

        mockMvc.perform(patch("/api/v1/admin/accounts/" + managedAccount.getAccountId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("SUSPENDED")));

        Account updated = accountRepository.findById(managedAccount.getAccountId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    @WithMockUser(username = "admin_user", roles = { "CLUSTER_ADMIN" })
    @DisplayName("Should soft-delete account to DEACTIVATED preserving database record and audit integrity")
    void testSoftDeleteAccount() throws Exception {
        UUID accountId = managedAccount.getAccountId();

        mockMvc.perform(delete("/api/v1/admin/accounts/" + accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account_id", is(accountId.toString())))
            .andExpect(jsonPath("$.status", is("DEACTIVATED")))
            .andExpect(jsonPath("$.message", containsString("Historical audit records preserved")));

        // Crucial invariant: entity must STILL exist in database with DEACTIVATED status
        Account preserved = accountRepository.findById(accountId).orElseThrow();
        assertThat(preserved.getStatus()).isEqualTo(AccountStatus.DEACTIVATED);
    }

    @Test
    @WithMockUser(username = "admin_user", roles = { "CLUSTER_ADMIN" })
    @DisplayName("Should reset operator password via write-only endpoint")
    void testResetPasswordSuccess() throws Exception {
        UUID accountId = managedAccount.getAccountId();
        dev.legatus.server.service.dto.ResetPasswordRequest req = new dev.legatus.server.service.dto.ResetPasswordRequest(
            "BrandNewResetPass2026!"
        );

        mockMvc.perform(post("/api/v1/admin/accounts/" + accountId + "/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("PASSWORD_RESET")))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.password_hash").doesNotExist());

        Account updated = accountRepository.findById(accountId).orElseThrow();
        assertThat(argon2idService.verify("BrandNewResetPass2026!", updated.getPasswordHash())).isTrue();
    }

    @Test
    @WithMockUser(username = "managed-operator", roles = { "CLUSTER_ADMIN" })
    @DisplayName("Should prevent self-lockout when admin attempts to suspend their own account")
    void testSelfLockoutStatusPrevention() throws Exception {
        UUID accountId = managedAccount.getAccountId();
        MutateAccountStatusRequest req = new MutateAccountStatusRequest(
            AccountStatus.SUSPENDED,
            "Self deactivation attempt"
        );

        mockMvc.perform(patch("/api/v1/admin/accounts/" + accountId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "managed-operator", roles = { "CLUSTER_ADMIN" })
    @DisplayName("Should prevent self-lockout when admin attempts to demote their own account")
    void testSelfLockoutDemotionPrevention() throws Exception {
        UUID accountId = managedAccount.getAccountId();
        UpdateAccountRequest req = new UpdateAccountRequest(
            "managed@legatus.corp",
            OperatorRole.OPERATOR
        );

        mockMvc.perform(put("/api/v1/admin/accounts/" + accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = { "CLUSTER_ADMIN" })
    @DisplayName("Should accept account creation with 'password' JSON field alias")
    void testCreateAccountPasswordAlias() throws Exception {
        String jsonPayload = """
            {
                "username": "alias-user-1",
                "email": "alias1@corp.legatus",
                "role": "OPERATOR",
                "password": "ValidComplexPass2026!"
            }
            """;

        mockMvc.perform(post("/api/v1/admin/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username", is("alias-user-1")))
            .andExpect(jsonPath("$.role", is("OPERATOR")));

        Account created = accountRepository.findOneByUsernameIgnoreCase("alias-user-1").orElseThrow();
        assertThat(argon2idService.verify("ValidComplexPass2026!", created.getPasswordHash())).isTrue();
    }
}
