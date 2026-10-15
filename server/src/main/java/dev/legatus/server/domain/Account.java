package dev.legatus.server.domain;

import dev.legatus.server.domain.enumeration.AccountStatus;
import dev.legatus.server.domain.enumeration.OperatorRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * Multi-operator accounts with Argon2id password digests and brute-force
 * lockout tracking (Table: accounts)
 */
@Entity
@Table(name = "accounts")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Account implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Id
    @GeneratedValue
    @Column(name = "account_id", nullable = false, unique = true)
    private UUID accountId;

    @NotNull
    @Size(min = 3, max = 255)
    @Column(name = "username", length = 255, nullable = false, unique = true)
    private String username;

    @NotNull
    @Size(min = 5, max = 255)
    @Column(name = "email", length = 255, nullable = false, unique = true)
    private String email;

    /**
     * Salted Argon2id password hash (m=64MB, t=3, p=4)
     */
    @NotNull
    @Size(max = 255)
    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private OperatorRole role;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @NotNull
    @Min(value = 0)
    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts;

    @Column(name = "lockout_until")
    private Instant lockoutUntil;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public UUID getAccountId() {
        return this.accountId;
    }

    public Account accountId(UUID accountId) {
        this.setAccountId(accountId);
        return this;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getUsername() {
        return this.username;
    }

    public Account username(String username) {
        this.setUsername(username);
        return this;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return this.email;
    }

    public Account email(String email) {
        this.setEmail(email);
        return this;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return this.passwordHash;
    }

    public Account passwordHash(String passwordHash) {
        this.setPasswordHash(passwordHash);
        return this;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public OperatorRole getRole() {
        return this.role;
    }

    public Account role(OperatorRole role) {
        this.setRole(role);
        return this;
    }

    public void setRole(OperatorRole role) {
        this.role = role;
    }

    public AccountStatus getStatus() {
        return this.status;
    }

    public Account status(AccountStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public Integer getFailedLoginAttempts() {
        return this.failedLoginAttempts;
    }

    public Account failedLoginAttempts(Integer failedLoginAttempts) {
        this.setFailedLoginAttempts(failedLoginAttempts);
        return this;
    }

    public void setFailedLoginAttempts(Integer failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public Instant getLockoutUntil() {
        return this.lockoutUntil;
    }

    public Account lockoutUntil(Instant lockoutUntil) {
        this.setLockoutUntil(lockoutUntil);
        return this;
    }

    public void setLockoutUntil(Instant lockoutUntil) {
        this.lockoutUntil = lockoutUntil;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public Account createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public Account updatedAt(Instant updatedAt) {
        this.setUpdatedAt(updatedAt);
        return this;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Account)) {
            return false;
        }
        return getAccountId() != null && getAccountId().equals(((Account) o).getAccountId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Account{" +
            "accountId=" + getAccountId() +
            ", username='" + getUsername() + "'" +
            ", email='" + getEmail() + "'" +
            ", passwordHash='" + getPasswordHash() + "'" +
            ", role='" + getRole() + "'" +
            ", status='" + getStatus() + "'" +
            ", failedLoginAttempts=" + getFailedLoginAttempts() +
            ", lockoutUntil='" + getLockoutUntil() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            "}";
    }
}
