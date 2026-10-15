package dev.legatus.server.service;

import dev.legatus.server.domain.SecretEntry;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.repository.SecretEntryRepository;
import dev.legatus.server.security.SecretVaultService;
import dev.legatus.server.service.dto.CreateSecretRequest;
import dev.legatus.server.service.dto.SecretEntryDTO;
import dev.legatus.server.service.dto.SecretMetadataResponse;
import dev.legatus.server.service.dto.UpdateSecretRequest;
import dev.legatus.server.service.mapper.SecretEntryMapper;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link dev.legatus.server.domain.SecretEntry}.
 */
@Service
@Transactional
public class SecretEntryService {

    private static final Logger LOG = LoggerFactory.getLogger(SecretEntryService.class);

    private final SecretEntryRepository secretEntryRepository;
    private final SecretEntryMapper secretEntryMapper;
    private final SecretVaultService secretVaultService;
    private final WormAuditService wormAuditService;

    public SecretEntryService(
        SecretEntryRepository secretEntryRepository,
        SecretEntryMapper secretEntryMapper,
        SecretVaultService secretVaultService,
        WormAuditService wormAuditService
    ) {
        this.secretEntryRepository = secretEntryRepository;
        this.secretEntryMapper = secretEntryMapper;
        this.secretVaultService = secretVaultService;
        this.wormAuditService = wormAuditService;
    }

    /**
     * Save a secretEntry.
     *
     * @param secretEntryDTO the entity to save.
     * @return the persisted entity.
     */
    public SecretEntryDTO save(SecretEntryDTO secretEntryDTO) {
        LOG.debug("Request to save SecretEntry : {}", secretEntryDTO);
        SecretEntry secretEntry = secretEntryMapper.toEntity(secretEntryDTO);
        secretEntry = secretEntryRepository.save(secretEntry);
        return secretEntryMapper.toDto(secretEntry);
    }

    /**
     * Update a secretEntry.
     *
     * @param secretEntryDTO the entity to save.
     * @return the persisted entity.
     */
    public SecretEntryDTO update(SecretEntryDTO secretEntryDTO) {
        LOG.debug("Request to update SecretEntry : {}", secretEntryDTO);
        SecretEntry secretEntry = secretEntryMapper.toEntity(secretEntryDTO);
        secretEntry.setIsPersisted();
        secretEntry = secretEntryRepository.save(secretEntry);
        return secretEntryMapper.toDto(secretEntry);
    }

    /**
     * Partially update a secretEntry.
     *
     * @param secretEntryDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<SecretEntryDTO> partialUpdate(SecretEntryDTO secretEntryDTO) {
        LOG.debug("Request to partially update SecretEntry : {}", secretEntryDTO);

        return secretEntryRepository
            .findById(secretEntryDTO.getSecretKey())
            .map(existingSecretEntry -> {
                secretEntryMapper.partialUpdate(existingSecretEntry, secretEntryDTO);

                return existingSecretEntry;
            })
            .map(secretEntryRepository::save)
            .map(secretEntryMapper::toDto);
    }

    /**
     * Get all the secretEntries.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<SecretEntryDTO> findAll() {
        LOG.debug("Request to get all SecretEntries");
        return secretEntryRepository.findAll().stream().map(secretEntryMapper::toDto).collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one secretEntry by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<SecretEntryDTO> findOne(String id) {
        LOG.debug("Request to get SecretEntry : {}", id);
        return secretEntryRepository.findById(id).map(secretEntryMapper::toDto);
    }

    /**
     * Delete the secretEntry by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete SecretEntry : {}", id);
        secretEntryRepository.deleteById(id);
    }

    /**
     * Creates and envelope-encrypts a new secret, saving it to secrets_vault and recording a WORM audit event.
     */
    public SecretMetadataResponse createSecret(CreateSecretRequest request, String actorId, String actorRole, String sourceIp) {
        Objects.requireNonNull(request, "request cannot be null");
        if (secretEntryRepository.existsById(request.secretKey())) {
            throw new IllegalArgumentException("Secret key already exists: " + request.secretKey());
        }

        SecretEntry entry = secretVaultService.storeSecret(request.secretKey(), request.scope(), request.value());

        wormAuditService.recordEvent(
            actorId != null ? actorId : "SYSTEM",
            actorRole != null ? actorRole : "ROLE_CLUSTER_ADMIN",
            "SECRET_CREATED",
            "SECRET",
            request.secretKey(),
            AuditActionStatus.SUCCESS,
            "{\"scope\":\"" + request.scope() + "\"}",
            sourceIp != null ? sourceIp : "127.0.0.1",
            null
        );

        return new SecretMetadataResponse(entry.getSecretKey(), entry.getScope(), entry.getUpdatedAt());
    }

    /**
     * Updates an existing secret's payload and/or scope, recording a WORM audit event.
     */
    public SecretMetadataResponse updateSecret(String secretKey, UpdateSecretRequest request, String actorId, String actorRole, String sourceIp) {
        Objects.requireNonNull(secretKey, "secretKey cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        if (!secretEntryRepository.existsById(secretKey)) {
            throw new IllegalArgumentException("Secret key not found: " + secretKey);
        }

        SecretEntry entry = secretVaultService.storeSecret(secretKey, request.scope(), request.value());

        wormAuditService.recordEvent(
            actorId != null ? actorId : "SYSTEM",
            actorRole != null ? actorRole : "ROLE_CLUSTER_ADMIN",
            "SECRET_UPDATED",
            "SECRET",
            secretKey,
            AuditActionStatus.SUCCESS,
            "{\"scope\":\"" + request.scope() + "\"}",
            sourceIp != null ? sourceIp : "127.0.0.1",
            null
        );

        return new SecretMetadataResponse(entry.getSecretKey(), entry.getScope(), entry.getUpdatedAt());
    }

    /**
     * Deletes a secret from the vault and records a WORM audit event.
     */
    public boolean deleteSecret(String secretKey, String actorId, String actorRole, String sourceIp) {
        Objects.requireNonNull(secretKey, "secretKey cannot be null");
        boolean deleted = secretVaultService.deleteSecret(secretKey);
        if (deleted) {
            wormAuditService.recordEvent(
                actorId != null ? actorId : "SYSTEM",
                actorRole != null ? actorRole : "ROLE_CLUSTER_ADMIN",
                "SECRET_DELETED",
                "SECRET",
                secretKey,
                AuditActionStatus.SUCCESS,
                null,
                sourceIp != null ? sourceIp : "127.0.0.1",
                null
            );
        }
        return deleted;
    }

    /**
     * Returns metadata for all registered secrets (masked, no ciphertext or plaintext exposed).
     */
    @Transactional(readOnly = true)
    public List<SecretMetadataResponse> listSecretMetadata() {
        return secretEntryRepository.findAll().stream()
            .map(e -> new SecretMetadataResponse(e.getSecretKey(), e.getScope(), e.getUpdatedAt()))
            .toList();
    }

    /**
     * Returns metadata for a single secret.
     */
    @Transactional(readOnly = true)
    public Optional<SecretMetadataResponse> getSecretMetadata(String secretKey) {
        return secretEntryRepository.findById(secretKey)
            .map(e -> new SecretMetadataResponse(e.getSecretKey(), e.getScope(), e.getUpdatedAt()));
    }

    /**
     * Executes an action with decrypted task secrets, guaranteeing in-memory zeroization of all plaintext buffers.
     */
    public <T> T dispatchWithTaskSecrets(Collection<String> secretRefs, SecretVaultService.SecretDispatchCallback<T> callback) throws Exception {
        return secretVaultService.withDecryptedSecrets(secretRefs, callback);
    }
}
