package dev.legatus.server.service;

import dev.legatus.server.domain.SecretEntry;
import dev.legatus.server.repository.SecretEntryRepository;
import dev.legatus.server.service.dto.SecretEntryDTO;
import dev.legatus.server.service.mapper.SecretEntryMapper;
import java.util.LinkedList;
import java.util.List;
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

    public SecretEntryService(SecretEntryRepository secretEntryRepository, SecretEntryMapper secretEntryMapper) {
        this.secretEntryRepository = secretEntryRepository;
        this.secretEntryMapper = secretEntryMapper;
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
}
