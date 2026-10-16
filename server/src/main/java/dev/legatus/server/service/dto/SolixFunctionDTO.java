package dev.legatus.server.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Lob;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A DTO for the {@link dev.legatus.server.domain.SolixFunction} entity.
 */
@Schema(
    description = "Cataloged Solix bytecode functions with SHA-256 content-addressable\ndeduplication and default execution constraints (Table: functions)"
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class SolixFunctionDTO implements Serializable {

    @NotNull
    private UUID functionId;

    @NotNull
    @Size(max = 128)
    private String name;

    @NotNull
    @Size(max = 32)
    private String semver;

    @NotNull
    @Size(max = 128)
    private String entryPoint;

    @NotNull
    @Size(max = 32)
    private String bytecodeFormat;

    @NotNull
    @Size(max = 64)
    @Schema(description = "64-character lowercase hex SHA-256 digest of raw bytecode", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bytecodeSha256;

    @Lob
    private byte[] bytecodeBlob;

    private String bytecodeBlobContentType;

    @NotNull
    @Min(value = 1000L)
    @Max(value = 1000000000L)
    private Long defaultBudget;

    @NotNull
    @Min(value = 8388608L)
    @Max(value = 1099511627776L)
    private Long defaultMemoryBytes;

    @NotNull
    @Min(value = 100)
    @Max(value = 3600000)
    private Integer defaultTimeoutMs;

    @NotNull
    private Instant createdAt;

    @Size(max = 1024)
    private String tags;

    @Size(max = 2048)
    private String description;

    private Long invocationsLastHour;

    public UUID getFunctionId() {
        return functionId;
    }

    public void setFunctionId(UUID functionId) {
        this.functionId = functionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSemver() {
        return semver;
    }

    public void setSemver(String semver) {
        this.semver = semver;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    public void setEntryPoint(String entryPoint) {
        this.entryPoint = entryPoint;
    }

    public String getBytecodeFormat() {
        return bytecodeFormat;
    }

    public void setBytecodeFormat(String bytecodeFormat) {
        this.bytecodeFormat = bytecodeFormat;
    }

    public String getBytecodeSha256() {
        return bytecodeSha256;
    }

    public void setBytecodeSha256(String bytecodeSha256) {
        this.bytecodeSha256 = bytecodeSha256;
    }

    public byte[] getBytecodeBlob() {
        return bytecodeBlob;
    }

    public void setBytecodeBlob(byte[] bytecodeBlob) {
        this.bytecodeBlob = bytecodeBlob;
    }

    public String getBytecodeBlobContentType() {
        return bytecodeBlobContentType;
    }

    public void setBytecodeBlobContentType(String bytecodeBlobContentType) {
        this.bytecodeBlobContentType = bytecodeBlobContentType;
    }

    public Long getDefaultBudget() {
        return defaultBudget;
    }

    public void setDefaultBudget(Long defaultBudget) {
        this.defaultBudget = defaultBudget;
    }

    public Long getDefaultMemoryBytes() {
        return defaultMemoryBytes;
    }

    public void setDefaultMemoryBytes(Long defaultMemoryBytes) {
        this.defaultMemoryBytes = defaultMemoryBytes;
    }

    public Integer getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    public void setDefaultTimeoutMs(Integer defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getInvocationsLastHour() {
        return invocationsLastHour;
    }

    public void setInvocationsLastHour(Long invocationsLastHour) {
        this.invocationsLastHour = invocationsLastHour;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SolixFunctionDTO)) {
            return false;
        }

        SolixFunctionDTO solixFunctionDTO = (SolixFunctionDTO) o;
        if (this.functionId == null) {
            return false;
        }
        return Objects.equals(this.functionId, solixFunctionDTO.functionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.functionId);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "SolixFunctionDTO{" +
            "functionId='" + getFunctionId() + "'" +
            ", name='" + getName() + "'" +
            ", semver='" + getSemver() + "'" +
            ", entryPoint='" + getEntryPoint() + "'" +
            ", bytecodeFormat='" + getBytecodeFormat() + "'" +
            ", bytecodeSha256='" + getBytecodeSha256() + "'" +
            ", bytecodeBlob='" + getBytecodeBlob() + "'" +
            ", defaultBudget=" + getDefaultBudget() +
            ", defaultMemoryBytes=" + getDefaultMemoryBytes() +
            ", defaultTimeoutMs=" + getDefaultTimeoutMs() +
            ", createdAt='" + getCreatedAt() + "'" +
            "}";
    }
}
