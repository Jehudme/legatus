package dev.legatus.server.service;

import dev.legatus.server.domain.Account;
import dev.legatus.server.domain.enumeration.AccountStatus;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.domain.enumeration.OperatorRole;
import dev.legatus.server.repository.AccountRepository;
import dev.legatus.server.security.Argon2idService;
import dev.legatus.server.security.SecurityUtils;
import dev.legatus.server.security.TicketBrokerService;
import dev.legatus.server.service.dto.*;
import dev.legatus.server.service.dto.lstcp.EventUserStateChangedFrame;
import dev.legatus.server.service.studio.StudioEventPublisher;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service managing operator authentication, Argon2id credentials, brute-force lockout,
 * initial administrator bootstrap, and RBAC account administration.
 * <p>
 * Complies with {@code server-spec.md} Section 4.5 and {@code api-endpoint.md} Sections 3 &amp; 6:
 * <ul>
 *   <li>Initial cluster admin bootstrap on boot if accounts table is empty.</li>
 *   <li>Argon2id password verification on bounded 4-thread carrier pool.</li>
 *   <li>5-attempt brute-force lockout with 15-minute HTTP 423 Locked suspension.</li>
 *   <li>Issues EdDSA/HS512 JWTs with role claims and single-use 30s Studio WebSocket tickets.</li>
 *   <li>Compliance soft-deletion mandate (strictly non-purging SQL DEACTIVATED updates).</li>
 *   <li>Statutory WORM audit trail recording for all authentication and CRUD mutations.</li>
 * </ul>
 */
@Service
public class OperatorAccountService {

    private static final Logger LOG = LoggerFactory.getLogger(OperatorAccountService.class);

    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    public static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    public static final int MIN_PASSWORD_LENGTH = 12;

    private final AccountRepository accountRepository;
    private final Argon2idService argon2idService;
    private final WormAuditService wormAuditService;
    private final TicketBrokerService ticketBrokerService;
    private final JwtEncoder jwtEncoder;
    private final StudioEventPublisher studioEventPublisher;

    @Value("${jhipster.security.authentication.jwt.token-validity-in-seconds:3600}")
    private long tokenValidityInSeconds;

    public OperatorAccountService(
        AccountRepository accountRepository,
        Argon2idService argon2idService,
        WormAuditService wormAuditService,
        TicketBrokerService ticketBrokerService,
        JwtEncoder jwtEncoder
    ) {
        this(accountRepository, argon2idService, wormAuditService, ticketBrokerService, jwtEncoder, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OperatorAccountService(
        AccountRepository accountRepository,
        Argon2idService argon2idService,
        WormAuditService wormAuditService,
        TicketBrokerService ticketBrokerService,
        JwtEncoder jwtEncoder,
        @org.springframework.beans.factory.annotation.Autowired(required = false) StudioEventPublisher studioEventPublisher
    ) {
        this.accountRepository = accountRepository;
        this.argon2idService = argon2idService;
        this.wormAuditService = wormAuditService;
        this.ticketBrokerService = ticketBrokerService;
        this.jwtEncoder = jwtEncoder;
        this.studioEventPublisher = studioEventPublisher;
    }

    /**
     * Bootstrap the initial cluster administrator account if the accounts table is empty.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrapAdminIfNeeded() {
        if (!accountRepository.existsByUsernameIgnoreCase("admin")) {
            String initialPassword = System.getenv("LEGATUS_ADMIN_INITIAL_PASSWORD");
            if (initialPassword == null || initialPassword.isBlank()) {
                initialPassword = "admin";
            }

            String passwordHash = argon2idService.hash(initialPassword);
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

            Account admin = new Account()
                .username("admin")
                .email("admin@legatus.internal")
                .role(OperatorRole.CLUSTER_ADMIN)
                .status(AccountStatus.ACTIVE)
                .passwordHash(passwordHash)
                .failedLoginAttempts(0)
                .lockoutUntil(null)
                .createdAt(now)
                .updatedAt(now);

            Account saved = accountRepository.save(admin);
            LOG.warn("Default cluster administrator account created (username: 'admin'). Password must be changed immediately!");

            wormAuditService.recordEvent(
                "system-bootstrap",
                "SYSTEM",
                "INITIAL_ADMIN_BOOTSTRAPPED",
                "ACCOUNT",
                saved.getAccountId().toString(),
                AuditActionStatus.SUCCESS,
                "{\"username\":\"admin\",\"role\":\"CLUSTER_ADMIN\"}",
                "127.0.0.1",
                "SOC2_CC6.1"
            );
        }
    }

    /**
     * Authenticates an operator, verifies Argon2id password digest, handles brute-force lockout,
     * generates a signed JWT and issues a single-use Studio WebSocket ticket.
     */
    @Transactional
    public OperatorLoginResponse login(OperatorLoginRequest request, String clientIp) {
        Objects.requireNonNull(request, "login request cannot be null");
        String username = request.username();
        String password = request.password();

        Optional<Account> accountOpt = accountRepository.findOneByUsernameIgnoreCaseOrEmailIgnoreCase(username, username);
        if (accountOpt.isEmpty()) {
            wormAuditService.recordEvent(
                username,
                "UNKNOWN",
                "OPERATOR_LOGIN_FAILED",
                "ACCOUNT",
                username,
                AuditActionStatus.DENIED,
                "{\"reason\":\"USER_NOT_FOUND\"}",
                clientIp,
                "SOC2_CC6.1"
            );
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ERR_INVALID_CREDENTIALS: Invalid username or password");
        }

        Account account = accountOpt.get();

        if (account.getStatus() == AccountStatus.DEACTIVATED) {
            LOG.warn("Rejected login attempt for deactivated operator account: {}", username);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ERR_ACCOUNT_DEACTIVATED: Account has been deactivated");
        }

        Instant now = Instant.now();

        // Evaluate active lockout window
        if (account.getLockoutUntil() != null) {
            if (now.isBefore(account.getLockoutUntil())) {
                wormAuditService.recordEvent(
                    account.getUsername(),
                    account.getRole().name(),
                    "OPERATOR_LOGIN_BLOCKED",
                    "ACCOUNT",
                    account.getAccountId().toString(),
                    AuditActionStatus.DENIED,
                    String.format("{\"reason\":\"ACCOUNT_LOCKED\",\"lockout_until\":\"%s\"}", account.getLockoutUntil()),
                    clientIp,
                    "SOC2_CC6.1"
                );
                throw new ResponseStatusException(
                    HttpStatus.LOCKED,
                    "ERR_ACCOUNT_LOCKED: Account is locked due to consecutive failed attempts. Retry after " + account.getLockoutUntil()
                );
            } else {
                // Lockout has expired - reset lockout window
                account.setLockoutUntil(null);
                account.setFailedLoginAttempts(0);
                if (account.getStatus() == AccountStatus.SUSPENDED) {
                    account.setStatus(AccountStatus.ACTIVE);
                }
            }
        }

        if (account.getStatus() == AccountStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ERR_ACCOUNT_SUSPENDED: Account is suspended");
        }

        boolean passwordMatches = argon2idService.verify(password, account.getPasswordHash());

        if (!passwordMatches) {
            int attempts = account.getFailedLoginAttempts() + 1;
            account.setFailedLoginAttempts(attempts);
            account.setUpdatedAt(now.truncatedTo(ChronoUnit.MILLIS));

            if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
                account.setStatus(AccountStatus.SUSPENDED);
                Instant lockoutUntil = now.plus(LOCKOUT_DURATION).truncatedTo(ChronoUnit.MILLIS);
                account.setLockoutUntil(lockoutUntil);
                accountRepository.save(account);

                wormAuditService.recordEvent(
                    account.getUsername(),
                    account.getRole().name(),
                    "OPERATOR_ACCOUNT_LOCKED",
                    "ACCOUNT",
                    account.getAccountId().toString(),
                    AuditActionStatus.DENIED,
                    String.format("{\"failed_attempts\":%d,\"lockout_until\":\"%s\"}", attempts, lockoutUntil),
                    clientIp,
                    "SOC2_CC6.1"
                );

                throw new ResponseStatusException(
                    HttpStatus.LOCKED,
                    "ERR_ACCOUNT_LOCKED: Account locked after " + attempts + " failed attempts"
                );
            } else {
                accountRepository.save(account);

                wormAuditService.recordEvent(
                    account.getUsername(),
                    account.getRole().name(),
                    "OPERATOR_LOGIN_FAILED",
                    "ACCOUNT",
                    account.getAccountId().toString(),
                    AuditActionStatus.DENIED,
                    String.format("{\"failed_attempts\":%d}", attempts),
                    clientIp,
                    "SOC2_CC6.1"
                );

                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ERR_INVALID_CREDENTIALS: Invalid username or password");
            }
        }

        // Authentication succeeded: reset failed attempts & lockout
        account.setFailedLoginAttempts(0);
        account.setLockoutUntil(null);
        account.setUpdatedAt(now.truncatedTo(ChronoUnit.MILLIS));
        accountRepository.save(account);

        // Generate signed JWT
        String authorities = "ROLE_" + account.getRole().name() + " " + account.getRole().name();
        Instant validity = now.plusSeconds(tokenValidityInSeconds);

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuedAt(now)
            .expiresAt(validity)
            .subject(account.getUsername())
            .claim(SecurityUtils.AUTHORITIES_CLAIM, authorities)
            .claim("account_id", account.getAccountId().toString())
            .claim("email", account.getEmail())
            .claim("role", account.getRole().name())
            .build();

        JwsHeader jwsHeader = JwsHeader.with(SecurityUtils.JWT_ALGORITHM).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();

        // Issue single-use 30-second Studio ticket
        String studioTicket = ticketBrokerService.issueStudioTicket(account.getUsername(), account.getRole().name());

        wormAuditService.recordEvent(
            account.getUsername(),
            account.getRole().name(),
            "OPERATOR_LOGIN_SUCCESS",
            "ACCOUNT",
            account.getAccountId().toString(),
            AuditActionStatus.SUCCESS,
            String.format("{\"role\":\"%s\"}", account.getRole().name()),
            clientIp,
            "SOC2_CC6.1"
        );

        return new OperatorLoginResponse(
            token,
            tokenValidityInSeconds,
            studioTicket,
            "/ws/studio",
            account.getAccountId(),
            account.getUsername(),
            account.getRole().name()
        );
    }

    /**
     * Enables an authenticated operator to update their own password.
     */
    @Transactional
    public OperatorPasswordChangeResponse changePassword(
        String username,
        OperatorPasswordChangeRequest request,
        String clientIp
    ) {
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(request, "password change request cannot be null");

        Account account = accountRepository
            .findOneByUsernameIgnoreCaseOrEmailIgnoreCase(username, username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ERR_ACCOUNT_NOT_FOUND: Operator account not found"));

        if (request.newPassword() == null || request.newPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "ERR_PASSWORD_POLICY: Password must be at least " + MIN_PASSWORD_LENGTH + " characters long"
            );
        }

        if (!argon2idService.verify(request.currentPassword(), account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ERR_INVALID_PASSWORD: Current password does not match");
        }

        String newPasswordHash = argon2idService.hash(request.newPassword());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        account.setPasswordHash(newPasswordHash);
        account.setUpdatedAt(now);
        accountRepository.save(account);

        wormAuditService.recordEvent(
            account.getUsername(),
            account.getRole().name(),
            "OPERATOR_PASSWORD_CHANGED",
            "ACCOUNT",
            account.getAccountId().toString(),
            AuditActionStatus.SUCCESS,
            "{}",
            clientIp,
            "SOC2_CC6.1"
        );

        return new OperatorPasswordChangeResponse("PASSWORD_UPDATED", now.toEpochMilli());
    }

    /**
     * List operator accounts with optional role and status filters.
     */
    @Transactional(readOnly = true)
    public Page<AccountResponse> listAccounts(OperatorRole role, AccountStatus status, Pageable pageable) {
        Page<Account> page;
        if (role != null && status != null) {
            page = accountRepository.findAllByRoleAndStatus(role, status, pageable);
        } else if (role != null) {
            page = accountRepository.findAllByRole(role, pageable);
        } else if (status != null) {
            page = accountRepository.findAllByStatus(status, pageable);
        } else {
            page = accountRepository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    /**
     * Provisions a new operator or compliance auditor account.
     */
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request, String adminActorId, String clientIp) {
        Objects.requireNonNull(request, "create account request cannot be null");

        if (accountRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ERR_USERNAME_TAKEN: Username already in use");
        }
        if (accountRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ERR_EMAIL_TAKEN: Email address already in use");
        }
        if (request.initialPassword() == null || request.initialPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "ERR_PASSWORD_POLICY: Password must be at least " + MIN_PASSWORD_LENGTH + " characters long"
            );
        }

        String passwordHash = argon2idService.hash(request.initialPassword());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        Account account = new Account()
            .username(request.username())
            .email(request.email())
            .role(request.role())
            .status(AccountStatus.ACTIVE)
            .passwordHash(passwordHash)
            .failedLoginAttempts(0)
            .lockoutUntil(null)
            .createdAt(now)
            .updatedAt(now);

        Account saved = accountRepository.save(account);

        wormAuditService.recordEvent(
            adminActorId,
            "CLUSTER_ADMIN",
            "ACCOUNT_CREATED",
            "ACCOUNT",
            saved.getAccountId().toString(),
            AuditActionStatus.SUCCESS,
            String.format("{\"username\":\"%s\",\"role\":\"%s\"}", saved.getUsername(), saved.getRole()),
            clientIp,
            "SOC2_CC6.1"
        );

        broadcastUserEvent(saved, "CREATED");

        return toResponse(saved);
    }

    /**
     * Updates profile username, email, and assigned RBAC role.
     */
    @Transactional
    public AccountResponse updateAccount(UUID accountId, UpdateAccountRequest request, String adminActorId, String clientIp) {
        Account account = accountRepository
            .findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ERR_ACCOUNT_NOT_FOUND: Operator account not found"));

        boolean isSelf = (adminActorId != null && (account.getUsername().equalsIgnoreCase(adminActorId) || account.getAccountId().toString().equalsIgnoreCase(adminActorId)));

        if (isSelf && request.role() != null && request.role() != OperatorRole.CLUSTER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot modify status or demote the currently logged-in administrator account.");
        }

        if (account.getRole() == OperatorRole.CLUSTER_ADMIN && request.role() != null && request.role() != OperatorRole.CLUSTER_ADMIN) {
            if (accountRepository.countByRoleAndStatus(OperatorRole.CLUSTER_ADMIN, AccountStatus.ACTIVE) <= 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot deactivate or demote the last remaining administrator account.");
            }
        }

        if (request.username() != null && !request.username().isBlank() && !request.username().equalsIgnoreCase(account.getUsername())) {
            if (accountRepository.existsByUsernameIgnoreCase(request.username())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "ERR_USERNAME_TAKEN: Username already in use");
            }
            account.setUsername(request.username().trim());
        }

        if (request.email() != null && !request.email().equalsIgnoreCase(account.getEmail())) {
            if (accountRepository.existsByEmailIgnoreCase(request.email())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "ERR_EMAIL_TAKEN: Email address already in use");
            }
            account.setEmail(request.email());
        }

        if (request.role() != null) {
            account.setRole(request.role());
        }

        account.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        Account saved = accountRepository.save(account);

        wormAuditService.recordEvent(
            adminActorId,
            "CLUSTER_ADMIN",
            "ACCOUNT_UPDATED",
            "ACCOUNT",
            saved.getAccountId().toString(),
            AuditActionStatus.SUCCESS,
            String.format("{\"role\":\"%s\",\"email\":\"%s\"}", saved.getRole(), saved.getEmail()),
            clientIp,
            "SOC2_CC6.1"
        );

        broadcastUserEvent(saved, "UPDATED");

        return toResponse(saved);
    }

    /**
     * Mutates account lifecycle state (ACTIVE, SUSPENDED, DEACTIVATED).
     */
    @Transactional
    public AccountResponse mutateStatus(UUID accountId, MutateAccountStatusRequest request, String adminActorId, String clientIp) {
        Account account = accountRepository
            .findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ERR_ACCOUNT_NOT_FOUND: Operator account not found"));

        boolean isSelf = (adminActorId != null && (account.getUsername().equalsIgnoreCase(adminActorId) || account.getAccountId().toString().equalsIgnoreCase(adminActorId)));

        if (isSelf && request.status() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot modify status or demote the currently logged-in administrator account.");
        }

        if (account.getRole() == OperatorRole.CLUSTER_ADMIN && request.status() != AccountStatus.ACTIVE) {
            if (accountRepository.countByRoleAndStatus(OperatorRole.CLUSTER_ADMIN, AccountStatus.ACTIVE) <= 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot deactivate or demote the last remaining administrator account.");
            }
        }

        account.setStatus(request.status());
        if (request.status() == AccountStatus.ACTIVE) {
            account.setFailedLoginAttempts(0);
            account.setLockoutUntil(null);
        }

        account.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        Account saved = accountRepository.save(account);

        wormAuditService.recordEvent(
            adminActorId,
            "CLUSTER_ADMIN",
            "ACCOUNT_STATUS_MUTATED",
            "ACCOUNT",
            saved.getAccountId().toString(),
            AuditActionStatus.SUCCESS,
            String.format("{\"new_status\":\"%s\",\"reason\":\"%s\"}", request.status(), request.reason() != null ? request.reason() : ""),
            clientIp,
            "SOC2_CC6.1"
        );

        broadcastUserEvent(saved, "STATUS_MUTATED");

        return toResponse(saved);
    }

    /**
     * Resets account password (write-only administrator operation).
     */
    @Transactional
    public void resetPassword(UUID accountId, String newPassword, String adminActorId, String clientIp) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "ERR_PASSWORD_POLICY: Password must be at least " + MIN_PASSWORD_LENGTH + " characters long"
            );
        }

        Account account = accountRepository
            .findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ERR_ACCOUNT_NOT_FOUND: Operator account not found"));

        String newPasswordHash = argon2idService.hash(newPassword);
        account.setPasswordHash(newPasswordHash);
        account.setFailedLoginAttempts(0);
        account.setLockoutUntil(null);
        account.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        Account saved = accountRepository.save(account);

        wormAuditService.recordEvent(
            adminActorId,
            "CLUSTER_ADMIN",
            "ACCOUNT_PASSWORD_RESET",
            "ACCOUNT",
            saved.getAccountId().toString(),
            AuditActionStatus.SUCCESS,
            "{}",
            clientIp,
            "SOC2_CC6.1"
        );

        broadcastUserEvent(saved, "PASSWORD_RESET");
    }

    /**
     * Soft-deletes account in compliance with statutory audit invariants (strictly non-purging SQL DEACTIVATED update).
     */
    @Transactional
    public DeactivateAccountResponse softDelete(UUID accountId, String adminActorId, String clientIp) {
        Account account = accountRepository
            .findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ERR_ACCOUNT_NOT_FOUND: Operator account not found"));

        boolean isSelf = (adminActorId != null && (account.getUsername().equalsIgnoreCase(adminActorId) || account.getAccountId().toString().equalsIgnoreCase(adminActorId)));

        if (isSelf) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot modify status or demote the currently logged-in administrator account.");
        }

        if (account.getRole() == OperatorRole.CLUSTER_ADMIN) {
            if (accountRepository.countByRoleAndStatus(OperatorRole.CLUSTER_ADMIN, AccountStatus.ACTIVE) <= 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot deactivate or demote the last remaining administrator account.");
            }
        }

        account.setStatus(AccountStatus.DEACTIVATED);
        account.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        Account saved = accountRepository.save(account);

        wormAuditService.recordEvent(
            adminActorId,
            "CLUSTER_ADMIN",
            "ACCOUNT_DEACTIVATED",
            "ACCOUNT",
            saved.getAccountId().toString(),
            AuditActionStatus.SUCCESS,
            "{\"reason\":\"SOFT_DELETE\"}",
            clientIp,
            "SOC2_CC6.1"
        );

        broadcastUserEvent(saved, "DELETED");

        return new DeactivateAccountResponse(
            saved.getAccountId(),
            "DEACTIVATED",
            "Account successfully deactivated. Historical audit records preserved."
        );
    }

    private void broadcastUserEvent(Account account, String action) {
        if (studioEventPublisher != null && account != null) {
            try {
                studioEventPublisher.publishUserStateChanged(new EventUserStateChangedFrame.Payload(
                    account.getAccountId().toString(),
                    account.getUsername(),
                    account.getEmail(),
                    account.getRole().name(),
                    account.getStatus().name(),
                    action
                ));
            } catch (Exception e) {
                LOG.warn("Failed to publish user state changed event: {}", e.getMessage());
            }
        }
    }

    public AccountResponse toResponse(Account a) {
        return new AccountResponse(
            a.getAccountId(),
            a.getUsername(),
            a.getEmail(),
            a.getRole(),
            a.getStatus(),
            a.getFailedLoginAttempts(),
            a.getLockoutUntil(),
            a.getCreatedAt() != null ? a.getCreatedAt().toEpochMilli() : null,
            a.getUpdatedAt() != null ? a.getUpdatedAt().toEpochMilli() : null
        );
    }
}
