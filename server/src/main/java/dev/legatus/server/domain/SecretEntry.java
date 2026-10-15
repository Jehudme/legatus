package dev.legatus.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.legatus.server.domain.enumeration.SecretScope;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.domain.Persistable;

/**
 * Envelope-encrypted guest task secrets protected via AES-256-GCM
 * derived from LEGATUS_MASTER_KEY (Table: secrets_vault)
 */
@Entity
@Table(name = "secrets_vault")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@JsonIgnoreProperties(value = { "new", "id" })
@SuppressWarnings("common-java:DuplicatedBlocks")
public class SecretEntry implements Serializable, Persistable<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Size(max = 128)
    @Id
    @Column(name = "secret_key", length = 128, nullable = false, unique = true)
    private String secretKey;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private SecretScope scope;

    /**
     * AES-256-GCM encrypted secret payload
     */
    @Lob
    @Column(name = "cipher_text", nullable = false)
    private byte[] cipherText;

    @NotNull
    @Column(name = "cipher_text_content_type", nullable = false)
    private String cipherTextContentType;

    /**
     * 96-bit (12-byte) Initialization Vector
     */
    @Lob
    @Column(name = "gcm_iv", nullable = false)
    private byte[] gcmIv;

    @NotNull
    @Column(name = "gcm_iv_content_type", nullable = false)
    private String gcmIvContentType;

    /**
     * 128-bit (16-byte) GCM Authentication Tag
     */
    @Lob
    @Column(name = "auth_tag", nullable = false)
    private byte[] authTag;

    @NotNull
    @Column(name = "auth_tag_content_type", nullable = false)
    private String authTagContentType;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @org.springframework.data.annotation.Transient
    @Transient
    private boolean isPersisted;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getSecretKey() {
        return this.secretKey;
    }

    public SecretEntry secretKey(String secretKey) {
        this.setSecretKey(secretKey);
        return this;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public SecretScope getScope() {
        return this.scope;
    }

    public SecretEntry scope(SecretScope scope) {
        this.setScope(scope);
        return this;
    }

    public void setScope(SecretScope scope) {
        this.scope = scope;
    }

    public byte[] getCipherText() {
        return this.cipherText;
    }

    public SecretEntry cipherText(byte[] cipherText) {
        this.setCipherText(cipherText);
        return this;
    }

    public void setCipherText(byte[] cipherText) {
        this.cipherText = cipherText;
    }

    public String getCipherTextContentType() {
        return this.cipherTextContentType;
    }

    public SecretEntry cipherTextContentType(String cipherTextContentType) {
        this.cipherTextContentType = cipherTextContentType;
        return this;
    }

    public void setCipherTextContentType(String cipherTextContentType) {
        this.cipherTextContentType = cipherTextContentType;
    }

    public byte[] getGcmIv() {
        return this.gcmIv;
    }

    public SecretEntry gcmIv(byte[] gcmIv) {
        this.setGcmIv(gcmIv);
        return this;
    }

    public void setGcmIv(byte[] gcmIv) {
        this.gcmIv = gcmIv;
    }

    public String getGcmIvContentType() {
        return this.gcmIvContentType;
    }

    public SecretEntry gcmIvContentType(String gcmIvContentType) {
        this.gcmIvContentType = gcmIvContentType;
        return this;
    }

    public void setGcmIvContentType(String gcmIvContentType) {
        this.gcmIvContentType = gcmIvContentType;
    }

    public byte[] getAuthTag() {
        return this.authTag;
    }

    public SecretEntry authTag(byte[] authTag) {
        this.setAuthTag(authTag);
        return this;
    }

    public void setAuthTag(byte[] authTag) {
        this.authTag = authTag;
    }

    public String getAuthTagContentType() {
        return this.authTagContentType;
    }

    public SecretEntry authTagContentType(String authTagContentType) {
        this.authTagContentType = authTagContentType;
        return this;
    }

    public void setAuthTagContentType(String authTagContentType) {
        this.authTagContentType = authTagContentType;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public SecretEntry updatedAt(Instant updatedAt) {
        this.setUpdatedAt(updatedAt);
        return this;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PostLoad
    @PostPersist
    public void updateEntityState() {
        this.setIsPersisted();
    }

    @Override
    public String getId() {
        return this.secretKey;
    }

    @org.springframework.data.annotation.Transient
    @Transient
    @Override
    public boolean isNew() {
        return !this.isPersisted;
    }

    public SecretEntry setIsPersisted() {
        this.isPersisted = true;
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SecretEntry)) {
            return false;
        }
        return getSecretKey() != null && getSecretKey().equals(((SecretEntry) o).getSecretKey());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "SecretEntry{" +
            "secretKey=" + getSecretKey() +
            ", scope='" + getScope() + "'" +
            ", cipherText='" + getCipherText() + "'" +
            ", cipherTextContentType='" + getCipherTextContentType() + "'" +
            ", gcmIv='" + getGcmIv() + "'" +
            ", gcmIvContentType='" + getGcmIvContentType() + "'" +
            ", authTag='" + getAuthTag() + "'" +
            ", authTagContentType='" + getAuthTagContentType() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            "}";
    }
}
