package dev.legatus.server.service.dto;

import dev.legatus.server.domain.enumeration.SecretScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Lob;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link dev.legatus.server.domain.SecretEntry} entity.
 */
@Schema(
    description = "Envelope-encrypted guest task secrets protected via AES-256-GCM\nderived from LEGATUS_MASTER_KEY (Table: secrets_vault)"
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class SecretEntryDTO implements Serializable {

    @NotNull
    @Size(max = 128)
    private String secretKey;

    @NotNull
    private SecretScope scope;

    @Schema(description = "AES-256-GCM encrypted secret payload", requiredMode = Schema.RequiredMode.REQUIRED)
    @Lob
    private byte[] cipherText;

    private String cipherTextContentType;

    @Schema(description = "96-bit (12-byte) Initialization Vector", requiredMode = Schema.RequiredMode.REQUIRED)
    @Lob
    private byte[] gcmIv;

    private String gcmIvContentType;

    @Schema(description = "128-bit (16-byte) GCM Authentication Tag", requiredMode = Schema.RequiredMode.REQUIRED)
    @Lob
    private byte[] authTag;

    private String authTagContentType;

    @NotNull
    private Instant updatedAt;

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public SecretScope getScope() {
        return scope;
    }

    public void setScope(SecretScope scope) {
        this.scope = scope;
    }

    public byte[] getCipherText() {
        return cipherText;
    }

    public void setCipherText(byte[] cipherText) {
        this.cipherText = cipherText;
    }

    public String getCipherTextContentType() {
        return cipherTextContentType;
    }

    public void setCipherTextContentType(String cipherTextContentType) {
        this.cipherTextContentType = cipherTextContentType;
    }

    public byte[] getGcmIv() {
        return gcmIv;
    }

    public void setGcmIv(byte[] gcmIv) {
        this.gcmIv = gcmIv;
    }

    public String getGcmIvContentType() {
        return gcmIvContentType;
    }

    public void setGcmIvContentType(String gcmIvContentType) {
        this.gcmIvContentType = gcmIvContentType;
    }

    public byte[] getAuthTag() {
        return authTag;
    }

    public void setAuthTag(byte[] authTag) {
        this.authTag = authTag;
    }

    public String getAuthTagContentType() {
        return authTagContentType;
    }

    public void setAuthTagContentType(String authTagContentType) {
        this.authTagContentType = authTagContentType;
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
        if (!(o instanceof SecretEntryDTO)) {
            return false;
        }

        SecretEntryDTO secretEntryDTO = (SecretEntryDTO) o;
        if (this.secretKey == null) {
            return false;
        }
        return Objects.equals(this.secretKey, secretEntryDTO.secretKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.secretKey);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "SecretEntryDTO{" +
            "secretKey='" + getSecretKey() + "'" +
            ", scope='" + getScope() + "'" +
            ", cipherText='" + getCipherText() + "'" +
            ", gcmIv='" + getGcmIv() + "'" +
            ", authTag='" + getAuthTag() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            "}";
    }
}
