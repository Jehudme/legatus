package dev.legatus.server.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.Account;
import dev.legatus.server.domain.enumeration.AccountStatus;
import dev.legatus.server.domain.enumeration.OperatorRole;
import dev.legatus.server.repository.AccountRepository;
import dev.legatus.server.security.Argon2idService;
import dev.legatus.server.service.dto.OperatorLoginRequest;
import dev.legatus.server.service.dto.OperatorPasswordChangeRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Integration tests for {@link OperatorAuthResource} endpoints:
 * {@code POST /api/v1/auth/operator/login} and {@code PUT /api/v1/auth/operator/password}.
 */
@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class OperatorAuthResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private Argon2idService argon2idService;

    private static final String TEST_USERNAME = "operator-auth-test";
    private static final String TEST_PASSWORD = "Password123!Secure";
    private Account testAccount;

    @BeforeEach
    void setUp() {
        accountRepository.findOneByUsernameIgnoreCase(TEST_USERNAME).ifPresent(accountRepository::delete);

        testAccount = new Account()
            .username(TEST_USERNAME)
            .email("operator-test@legatus.corp")
            .role(OperatorRole.OPERATOR)
            .status(AccountStatus.ACTIVE)
            .passwordHash(argon2idService.hash(TEST_PASSWORD))
            .failedLoginAttempts(0)
            .lockoutUntil(null)
            .createdAt(Instant.now().truncatedTo(ChronoUnit.MILLIS))
            .updatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));

        accountRepository.saveAndFlush(testAccount);
    }

    @Test
    @DisplayName("Should successfully authenticate operator, return JWT token and Studio WebSocket ticket")
    void testLoginSuccess() throws Exception {
        OperatorLoginRequest req = new OperatorLoginRequest(TEST_USERNAME, TEST_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/operator/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token", notNullValue()))
            .andExpect(jsonPath("$.expires_in_seconds", greaterThan(0)))
            .andExpect(jsonPath("$.studio_ws_ticket", startsWith("st_tkt_")))
            .andExpect(jsonPath("$.studio_ws_url", is("/ws/studio")))
            .andExpect(jsonPath("$.username", is(TEST_USERNAME)))
            .andExpect(jsonPath("$.role", is("OPERATOR")));

        Account refreshed = accountRepository.findOneByUsernameIgnoreCase(TEST_USERNAME).orElseThrow();
        assertThat(refreshed.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(refreshed.getLockoutUntil()).isNull();
    }

    @Test
    @DisplayName("Should reject authentication with 401 on incorrect password and increment failed attempts")
    void testLoginWrongPassword() throws Exception {
        OperatorLoginRequest req = new OperatorLoginRequest(TEST_USERNAME, "IncorrectPassword!");

        mockMvc.perform(post("/api/v1/auth/operator/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(req)))
            .andExpect(status().isUnauthorized());

        Account refreshed = accountRepository.findOneByUsernameIgnoreCase(TEST_USERNAME).orElseThrow();
        assertThat(refreshed.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should lock account with 423 Locked on 5th consecutive incorrect password attempt")
    void testBruteForceLockoutAfterFiveAttempts() throws Exception {
        OperatorLoginRequest req = new OperatorLoginRequest(TEST_USERNAME, "WrongPassword!");

        // 4 failed attempts
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/auth/operator/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(req)))
                .andExpect(status().isUnauthorized());
        }

        // 5th failed attempt -> 423 Locked
        mockMvc.perform(post("/api/v1/auth/operator/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(req)))
            .andExpect(status().isLocked());

        Account locked = accountRepository.findOneByUsernameIgnoreCase(TEST_USERNAME).orElseThrow();
        assertThat(locked.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(locked.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(locked.getLockoutUntil()).isNotNull();
        assertThat(locked.getLockoutUntil()).isAfter(Instant.now());

        // Subsequent attempt rejected with 423 even with correct password
        OperatorLoginRequest correctReq = new OperatorLoginRequest(TEST_USERNAME, TEST_PASSWORD);
        mockMvc.perform(post("/api/v1/auth/operator/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(correctReq)))
            .andExpect(status().isLocked());
    }

    @Test
    @WithMockUser(username = TEST_USERNAME, roles = { "OPERATOR" })
    @DisplayName("Should update password when authenticated and new password satisfies policy")
    void testChangePasswordSuccess() throws Exception {
        String newPassword = "BrandNewSuperSecure2026!";
        OperatorPasswordChangeRequest req = new OperatorPasswordChangeRequest(TEST_PASSWORD, newPassword);

        mockMvc.perform(put("/api/v1/auth/operator/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("PASSWORD_UPDATED")))
            .andExpect(jsonPath("$.updated_at", notNullValue()));

        Account updated = accountRepository.findOneByUsernameIgnoreCase(TEST_USERNAME).orElseThrow();
        assertThat(argon2idService.verify(newPassword, updated.getPasswordHash())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_USERNAME, roles = { "OPERATOR" })
    @DisplayName("Should reject password change when current password does not match")
    void testChangePasswordWrongCurrent() throws Exception {
        OperatorPasswordChangeRequest req = new OperatorPasswordChangeRequest("WrongCurrentPass!", "BrandNewSuperSecure2026!");

        mockMvc.perform(put("/api/v1/auth/operator/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(req)))
            .andExpect(status().isBadRequest());
    }
}
