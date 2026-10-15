package dev.legatus.server.service.dto;

import dev.legatus.server.domain.enumeration.AccountStatus;
import dev.legatus.server.domain.enumeration.OperatorRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A DTO for the {@link dev.legatus.server.domain.Account} entity.
 */
@Schema(description = "Multi-operator accounts with Argon2id password digests and brute-force\nlockout tracking (Table: accounts)")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AccountDTO implements Serializable {

    @NotNull
    private UUID accountId;

    @NotNull
    @Size(min = 3, max = 255)
    private String username;

    @NotNull
    @Size(min = 5, max = 255)
    private String email;

    @NotNull
    @Size(max = 255)
    @Schema(description = "Salted Argon2id password hash (m=64MB, t=3, p=4)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String passwordHash;

    @NotNull
    private OperatorRole role;

    @NotNull
    private AccountStatus status;

    @NotNull
    @Min(value = 0)
    private Integer failedLoginAttempts;

    private Instant lockoutUntil;

    @NotNull
    private Instant createdAt;

    @NotNull
    private Instant updatedAt;

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public OperatorRole getRole() {
        return role;
    }

    public void setRole(OperatorRole role) {
        this.role = role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public Integer getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(Integer failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public Instant getLockoutUntil() {
        return lockoutUntil;
    }

    public void setLockoutUntil(Instant lockoutUntil) {
        this.lockoutUntil = lockoutUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccountDTO)) {
            return false;
        }

        AccountDTO accountDTO = (AccountDTO) o;
        if (this.accountId == null) {
            return false;
        }
        return Objects.equals(this.accountId, accountDTO.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.accountId);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AccountDTO{" +
            "accountId='" + getAccountId() + "'" +
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
