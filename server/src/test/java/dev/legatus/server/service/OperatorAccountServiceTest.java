package dev.legatus.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import dev.legatus.server.domain.Account;
import dev.legatus.server.domain.enumeration.AccountStatus;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.domain.enumeration.OperatorRole;
import dev.legatus.server.repository.AccountRepository;
import dev.legatus.server.security.Argon2idService;
import dev.legatus.server.security.TicketBrokerService;
import dev.legatus.server.service.dto.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OperatorAccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private Argon2idService argon2idService;

    @Mock
    private WormAuditService wormAuditService;

    @Mock
    private TicketBrokerService ticketBrokerService;

    @Mock
    private JwtEncoder jwtEncoder;

    private OperatorAccountService operatorAccountService;

    @BeforeEach
    void setUp() {
        operatorAccountService = new OperatorAccountService(
            accountRepository,
            argon2idService,
            wormAuditService,
            ticketBrokerService,
            jwtEncoder
        );
        ReflectionTestUtils.setField(operatorAccountService, "tokenValidityInSeconds", 3600L);
    }

    @Test
    @DisplayName("Should bootstrap cluster admin when account table is empty")
    void testBootstrapAdminWhenEmpty() {
        when(accountRepository.count()).thenReturn(0L);
        when(argon2idService.hash(anyString())).thenReturn("$argon2id$v=19$m=65536,t=3,p=4$hash");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        operatorAccountService.bootstrapAdminIfNeeded();

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        Account saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getEmail()).isEqualTo("admin@legatus.internal");
        assertThat(saved.getRole()).isEqualTo(OperatorRole.CLUSTER_ADMIN);
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getFailedLoginAttempts()).isEqualTo(0);

        verify(wormAuditService).recordEvent(
            eq("system-bootstrap"),
            eq("SYSTEM"),
            eq("INITIAL_ADMIN_BOOTSTRAPPED"),
            eq("ACCOUNT"),
            anyString(),
            eq(AuditActionStatus.SUCCESS),
            anyString(),
            eq("127.0.0.1"),
            eq("SOC2_CC6.1")
        );
    }

    @Test
    @DisplayName("Should not bootstrap cluster admin when accounts already exist")
    void testBootstrapAdminWhenNotEmpty() {
        when(accountRepository.count()).thenReturn(5L);

        operatorAccountService.bootstrapAdminIfNeeded();

        verify(accountRepository, never()).save(any());
        verify(wormAuditService, never()).recordEvent(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should authenticate operator with valid credentials and issue JWT and Studio ticket")
    void testLoginSuccess() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account()
            .accountId(accountId)
            .username("sre-lead")
            .email("sre@legatus.corp")
            .role(OperatorRole.OPERATOR)
            .status(AccountStatus.ACTIVE)
            .passwordHash("$argon2id$mocked-hash")
            .failedLoginAttempts(2)
            .lockoutUntil(null);

        when(accountRepository.findOneByUsernameIgnoreCaseOrEmailIgnoreCase("sre-lead", "sre-lead"))
            .thenReturn(Optional.of(account));
        when(argon2idService.verify("correct-pass", "$argon2id$mocked-hash")).thenReturn(true);

        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getTokenValue()).thenReturn("mocked.jwt.token");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);
        when(ticketBrokerService.issueStudioTicket("sre-lead", "OPERATOR")).thenReturn("st_tkt_12345");

        OperatorLoginResponse response = operatorAccountService.login(
            new OperatorLoginRequest("sre-lead", "correct-pass"),
            "192.168.1.10"
        );

        assertThat(response.accessToken()).isEqualTo("mocked.jwt.token");
        assertThat(response.expiresInSeconds()).isEqualTo(3600L);
        assertThat(response.studioWsTicket()).isEqualTo("st_tkt_12345");
        assertThat(response.studioWsUrl()).isEqualTo("/ws/studio");
        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.username()).isEqualTo("sre-lead");
        assertThat(response.role()).isEqualTo("OPERATOR");

        // Failed attempts reset
        assertThat(account.getFailedLoginAttempts()).isEqualTo(0);
        verify(accountRepository).save(account);

        verify(wormAuditService).recordEvent(
            eq("sre-lead"),
            eq("OPERATOR"),
            eq("OPERATOR_LOGIN_SUCCESS"),
            eq("ACCOUNT"),
            eq(accountId.toString()),
            eq(AuditActionStatus.SUCCESS),
            anyString(),
            eq("192.168.1.10"),
            eq("SOC2_CC6.1")
        );
    }

    @Test
    @DisplayName("Should reject authentication for non-existent account with 401")
    void testLoginUserNotFound() {
        when(accountRepository.findOneByUsernameIgnoreCaseOrEmailIgnoreCase("non-existent", "non-existent"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> operatorAccountService.login(
            new OperatorLoginRequest("non-existent", "password"), "10.0.0.1"
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);

        verify(wormAuditService).recordEvent(
            eq("non-existent"),
            eq("UNKNOWN"),
            eq("OPERATOR_LOGIN_FAILED"),
            eq("ACCOUNT"),
            eq("non-existent"),
            eq(AuditActionStatus.DENIED),
            anyString(),
            eq("10.0.0.1"),
            eq("SOC2_CC6.1")
        );
    }

    @Test
    @DisplayName("Should reject login for deactivated account with 403 Forbidden")
    void testLoginDeactivatedAccount() {
        Account account = new Account()
            .username("deactivated-user")
            .status(AccountStatus.DEACTIVATED);

        when(accountRepository.findOneByUsernameIgnoreCaseOrEmailIgnoreCase("deactivated-user", "deactivated-user"))
            .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> operatorAccountService.login(
            new OperatorLoginRequest("deactivated-user", "pass"), "10.0.0.1"
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Should increment failed attempts on invalid password")
    void testLoginInvalidPasswordIncrement() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account()
            .accountId(accountId)
            .username("admin")
            .role(OperatorRole.CLUSTER_ADMIN)
            .status(AccountStatus.ACTIVE)
            .passwordHash("$argon2id$mocked-hash")
            .failedLoginAttempts(2);

        when(accountRepository.findOneByUsernameIgnoreCaseOrEmailIgnoreCase("admin", "admin"))
            .thenReturn(Optional.of(account));
        when(argon2idService.verify("wrong-pass", "$argon2id$mocked-hash")).thenReturn(false);

        assertThatThrownBy(() -> operatorAccountService.login(
            new OperatorLoginRequest("admin", "wrong-pass"), "10.0.0.1"
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);

        assertThat(account.getFailedLoginAttempts()).isEqualTo(3);
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("Should lock account with 423 Locked on 5th consecutive failed attempt")
    void testLoginFifthFailedAttemptLockout() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account()
            .accountId(accountId)
            .username("target-account")
            .role(OperatorRole.OPERATOR)
            .status(AccountStatus.ACTIVE)
            .passwordHash("$argon2id$mocked-hash")
            .failedLoginAttempts(4);

        when(accountRepository.findOneByUsernameIgnoreCaseOrEmailIgnoreCase("target-account", "target-account"))
            .thenReturn(Optional.of(account));
        when(argon2idService.verify("wrong-pass", "$argon2id$mocked-hash")).thenReturn(false);

        assertThatThrownBy(() -> operatorAccountService.login(
            new OperatorLoginRequest("target-account", "wrong-pass"), "10.0.0.1"
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.LOCKED);

        assertThat(account.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(account.getLockoutUntil()).isAfter(Instant.now());
        verify(accountRepository).save(account);

        verify(wormAuditService).recordEvent(
            eq("target-account"),
            eq("OPERATOR"),
            eq("OPERATOR_ACCOUNT_LOCKED"),
            eq("ACCOUNT"),
            eq(accountId.toString()),
            eq(AuditActionStatus.DENIED),
            anyString(),
            eq("10.0.0.1"),
            eq("SOC2_CC6.1")
        );
    }

    @Test
    @DisplayName("Should immediately reject login during active lockout window with 423 Locked")
    void testLoginDuringActiveLockout() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account()
            .accountId(accountId)
            .username("locked-user")
            .role(OperatorRole.OPERATOR)
            .status(AccountStatus.SUSPENDED)
            .failedLoginAttempts(5)
            .lockoutUntil(Instant.now().plus(10, ChronoUnit.MINUTES));

        when(accountRepository.findOneByUsernameIgnoreCaseOrEmailIgnoreCase("locked-user", "locked-user"))
            .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> operatorAccountService.login(
            new OperatorLoginRequest("locked-user", "any-pass"), "10.0.0.1"
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.LOCKED);

        // Password verification should not even be called during lockout
        verify(argon2idService, never()).verify(any(), any());
    }

    @Test
    @DisplayName("Should enable login after lockout window has elapsed")
    void testLoginAfterLockoutExpired() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account()
            .accountId(accountId)
            .username("recovered-user")
            .email("rec@corp.net")
            .role(OperatorRole.OPERATOR)
            .status(AccountStatus.SUSPENDED)
            .passwordHash("$argon2id$mocked-hash")
            .failedLoginAttempts(5)
            .lockoutUntil(Instant.now().minus(1, ChronoUnit.MINUTES));

        when(accountRepository.findOneByUsernameIgnoreCaseOrEmailIgnoreCase("recovered-user", "recovered-user"))
            .thenReturn(Optional.of(account));
        when(argon2idService.verify("valid-pass", "$argon2id$mocked-hash")).thenReturn(true);

        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getTokenValue()).thenReturn("fresh.jwt.token");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);
        when(ticketBrokerService.issueStudioTicket("recovered-user", "OPERATOR")).thenReturn("st_tkt_rev");

        OperatorLoginResponse response = operatorAccountService.login(
            new OperatorLoginRequest("recovered-user", "valid-pass"), "10.0.0.1"
        );

        assertThat(response.accessToken()).isEqualTo("fresh.jwt.token");
        assertThat(account.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(account.getLockoutUntil()).isNull();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should successfully change password when current password matches and new password satisfies policy")
    void testChangePasswordSuccess() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account()
            .accountId(accountId)
            .username("auditor-1")
            .role(OperatorRole.AUDITOR)
            .passwordHash("$argon2id$old-hash");

        when(accountRepository.findOneByUsernameIgnoreCaseOrEmailIgnoreCase("auditor-1", "auditor-1"))
            .thenReturn(Optional.of(account));
        when(argon2idService.verify("old-secret-password-123", "$argon2id$old-hash")).thenReturn(true);
        when(argon2idService.hash("new-super-secure-password-2026")).thenReturn("$argon2id$new-hash");

        OperatorPasswordChangeResponse response = operatorAccountService.changePassword(
            "auditor-1",
            new OperatorPasswordChangeRequest("old-secret-password-123", "new-super-secure-password-2026"),
            "127.0.0.1"
        );

        assertThat(response.status()).isEqualTo("PASSWORD_UPDATED");
        assertThat(account.getPasswordHash()).isEqualTo("$argon2id$new-hash");
        verify(accountRepository).save(account);

        verify(wormAuditService).recordEvent(
            eq("auditor-1"),
            eq("AUDITOR"),
            eq("OPERATOR_PASSWORD_CHANGED"),
            eq("ACCOUNT"),
            eq(accountId.toString()),
            eq(AuditActionStatus.SUCCESS),
            anyString(),
            eq("127.0.0.1"),
            eq("SOC2_CC6.1")
        );
    }

    @Test
    @DisplayName("Should reject password change when current password does not match")
    void testChangePasswordInvalidCurrent() {
        Account account = new Account()
            .username("auditor-1")
            .passwordHash("$argon2id$old-hash");

        when(accountRepository.findOneByUsernameIgnoreCaseOrEmailIgnoreCase("auditor-1", "auditor-1"))
            .thenReturn(Optional.of(account));
        when(argon2idService.verify("wrong-pass", "$argon2id$old-hash")).thenReturn(false);

        assertThatThrownBy(() -> operatorAccountService.changePassword(
            "auditor-1",
            new OperatorPasswordChangeRequest("wrong-pass", "new-super-secure-password-2026"),
            "127.0.0.1"
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should reject password change when new password is under 12 characters")
    void testChangePasswordTooShort() {
        Account account = new Account()
            .username("auditor-1")
            .passwordHash("$argon2id$old-hash");

        when(accountRepository.findOneByUsernameIgnoreCaseOrEmailIgnoreCase("auditor-1", "auditor-1"))
            .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> operatorAccountService.changePassword(
            "auditor-1",
            new OperatorPasswordChangeRequest("old-pass", "short"),
            "127.0.0.1"
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should create account with 12+ char password and ACTIVE status")
    void testCreateAccountSuccess() {
        when(accountRepository.existsByUsernameIgnoreCase("new-operator")).thenReturn(false);
        when(accountRepository.existsByEmailIgnoreCase("new@corp.internal")).thenReturn(false);
        when(argon2idService.hash("complex-operator-pass-2026")).thenReturn("$argon2id$created-hash");
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            a.setAccountId(UUID.randomUUID());
            return a;
        });

        AccountResponse response = operatorAccountService.createAccount(
            new CreateAccountRequest("new-operator", "new@corp.internal", OperatorRole.OPERATOR, "complex-operator-pass-2026"),
            "cluster-admin",
            "127.0.0.1"
        );

        assertThat(response.username()).isEqualTo("new-operator");
        assertThat(response.email()).isEqualTo("new@corp.internal");
        assertThat(response.role()).isEqualTo(OperatorRole.OPERATOR);
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);

        verify(wormAuditService).recordEvent(
            eq("cluster-admin"),
            eq("CLUSTER_ADMIN"),
            eq("ACCOUNT_CREATED"),
            eq("ACCOUNT"),
            anyString(),
            eq(AuditActionStatus.SUCCESS),
            anyString(),
            eq("127.0.0.1"),
            eq("SOC2_CC6.1")
        );
    }

    @Test
    @DisplayName("Should reject account creation when username exists with 409 Conflict")
    void testCreateAccountUsernameConflict() {
        when(accountRepository.existsByUsernameIgnoreCase("existing-user")).thenReturn(true);

        assertThatThrownBy(() -> operatorAccountService.createAccount(
            new CreateAccountRequest("existing-user", "unique@corp.internal", OperatorRole.OPERATOR, "complex-operator-pass-2026"),
            "cluster-admin",
            "127.0.0.1"
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should soft-delete account to DEACTIVATED preserving audit log integrity")
    void testSoftDelete() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account()
            .accountId(accountId)
            .username("leaving-user")
            .status(AccountStatus.ACTIVE);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        DeactivateAccountResponse response = operatorAccountService.softDelete(accountId, "cluster-admin", "127.0.0.1");

        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.status()).isEqualTo("DEACTIVATED");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.DEACTIVATED);

        // Crucial: delete() is NEVER called on repository
        verify(accountRepository, never()).delete(any());
        verify(accountRepository, never()).deleteById(any());
        verify(accountRepository).save(account);

        verify(wormAuditService).recordEvent(
            eq("cluster-admin"),
            eq("CLUSTER_ADMIN"),
            eq("ACCOUNT_DEACTIVATED"),
            eq("ACCOUNT"),
            eq(accountId.toString()),
            eq(AuditActionStatus.SUCCESS),
            anyString(),
            eq("127.0.0.1"),
            eq("SOC2_CC6.1")
        );
    }
}
