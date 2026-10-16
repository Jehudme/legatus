package dev.legatus.server.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * Cataloged Solix bytecode functions with SHA-256 content-addressable
 * deduplication and default execution constraints (Table: functions)
 */
@Entity
@Table(name = "functions")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class SolixFunction implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Id
    @GeneratedValue
    @Column(name = "function_id", nullable = false, unique = true)
    private UUID functionId;

    @NotNull
    @Size(max = 128)
    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @NotNull
    @Size(max = 32)
    @Column(name = "semver", length = 32, nullable = false)
    private String semver;

    @NotNull
    @Size(max = 128)
    @Column(name = "entry_point", length = 128, nullable = false)
    private String entryPoint;

    @NotNull
    @Size(max = 32)
    @Column(name = "bytecode_format", length = 32, nullable = false)
    private String bytecodeFormat;

    /**
     * 64-character lowercase hex SHA-256 digest of raw bytecode
     */
    @NotNull
    @Size(max = 64)
    @Column(name = "bytecode_sha_256", length = 64, nullable = false)
    private String bytecodeSha256;

    @Lob
    @Column(name = "bytecode_blob", nullable = false)
    private byte[] bytecodeBlob;

    @NotNull
    @Column(name = "bytecode_blob_content_type", nullable = false)
    private String bytecodeBlobContentType;

    @NotNull
    @Min(value = 1000L)
    @Max(value = 1000000000L)
    @Column(name = "default_budget", nullable = false)
    private Long defaultBudget;

    @NotNull
    @Min(value = 8388608L)
    @Max(value = 1099511627776L)
    @Column(name = "default_memory_bytes", nullable = false)
    private Long defaultMemoryBytes;

    @NotNull
    @Min(value = 100)
    @Max(value = 3600000)
    @Column(name = "default_timeout_ms", nullable = false)
    private Integer defaultTimeoutMs;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Size(max = 1024)
    @Column(name = "tags", length = 1024)
    private String tags;

    @Size(max = 2048)
    @Column(name = "description", length = 2048)
    private String description;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public UUID getFunctionId() {
        return this.functionId;
    }

    public SolixFunction functionId(UUID functionId) {
        this.setFunctionId(functionId);
        return this;
    }

    public void setFunctionId(UUID functionId) {
        this.functionId = functionId;
    }

    public String getName() {
        return this.name;
    }

    public SolixFunction name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSemver() {
        return this.semver;
    }

    public SolixFunction semver(String semver) {
        this.setSemver(semver);
        return this;
    }

    public void setSemver(String semver) {
        this.semver = semver;
    }

    public String getEntryPoint() {
        return this.entryPoint;
    }

    public SolixFunction entryPoint(String entryPoint) {
        this.setEntryPoint(entryPoint);
        return this;
    }

    public void setEntryPoint(String entryPoint) {
        this.entryPoint = entryPoint;
    }

    public String getBytecodeFormat() {
        return this.bytecodeFormat;
    }

    public SolixFunction bytecodeFormat(String bytecodeFormat) {
        this.setBytecodeFormat(bytecodeFormat);
        return this;
    }

    public void setBytecodeFormat(String bytecodeFormat) {
        this.bytecodeFormat = bytecodeFormat;
    }

    public String getBytecodeSha256() {
        return this.bytecodeSha256;
    }

    public SolixFunction bytecodeSha256(String bytecodeSha256) {
        this.setBytecodeSha256(bytecodeSha256);
        return this;
    }

    public void setBytecodeSha256(String bytecodeSha256) {
        this.bytecodeSha256 = bytecodeSha256;
    }

    public byte[] getBytecodeBlob() {
        return this.bytecodeBlob;
    }

    public SolixFunction bytecodeBlob(byte[] bytecodeBlob) {
        this.setBytecodeBlob(bytecodeBlob);
        return this;
    }

    public void setBytecodeBlob(byte[] bytecodeBlob) {
        this.bytecodeBlob = bytecodeBlob;
    }

    public String getBytecodeBlobContentType() {
        return this.bytecodeBlobContentType;
    }

    public SolixFunction bytecodeBlobContentType(String bytecodeBlobContentType) {
        this.bytecodeBlobContentType = bytecodeBlobContentType;
        return this;
    }

    public void setBytecodeBlobContentType(String bytecodeBlobContentType) {
        this.bytecodeBlobContentType = bytecodeBlobContentType;
    }

    public Long getDefaultBudget() {
        return this.defaultBudget;
    }

    public SolixFunction defaultBudget(Long defaultBudget) {
        this.setDefaultBudget(defaultBudget);
        return this;
    }

    public void setDefaultBudget(Long defaultBudget) {
        this.defaultBudget = defaultBudget;
    }

    public Long getDefaultMemoryBytes() {
        return this.defaultMemoryBytes;
    }

    public SolixFunction defaultMemoryBytes(Long defaultMemoryBytes) {
        this.setDefaultMemoryBytes(defaultMemoryBytes);
        return this;
    }

    public void setDefaultMemoryBytes(Long defaultMemoryBytes) {
        this.defaultMemoryBytes = defaultMemoryBytes;
    }

    public Integer getDefaultTimeoutMs() {
        return this.defaultTimeoutMs;
    }

    public SolixFunction defaultTimeoutMs(Integer defaultTimeoutMs) {
        this.setDefaultTimeoutMs(defaultTimeoutMs);
        return this;
    }

    public void setDefaultTimeoutMs(Integer defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public SolixFunction createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getTags() {
        return this.tags;
    }

    public SolixFunction tags(String tags) {
        this.setTags(tags);
        return this;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDescription() {
        return this.description;
    }

    public SolixFunction description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SolixFunction)) {
            return false;
        }
        return getFunctionId() != null && getFunctionId().equals(((SolixFunction) o).getFunctionId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "SolixFunction{" +
            "functionId=" + getFunctionId() +
            ", name='" + getName() + "'" +
            ", semver='" + getSemver() + "'" +
            ", entryPoint='" + getEntryPoint() + "'" +
            ", bytecodeFormat='" + getBytecodeFormat() + "'" +
            ", bytecodeSha256='" + getBytecodeSha256() + "'" +
            ", bytecodeBlob='" + getBytecodeBlob() + "'" +
            ", bytecodeBlobContentType='" + getBytecodeBlobContentType() + "'" +
            ", defaultBudget=" + getDefaultBudget() +
            ", defaultMemoryBytes=" + getDefaultMemoryBytes() +
            ", defaultTimeoutMs=" + getDefaultTimeoutMs() +
            ", createdAt='" + getCreatedAt() + "'" +
            "}";
    }
}
