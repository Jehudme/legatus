package dev.legatus.server.service;

import dev.legatus.server.domain.ExecutionProfile;
import dev.legatus.server.repository.ExecutionProfileRepository;
import dev.legatus.server.service.dto.ExecutionProfileDTO;
import dev.legatus.server.service.mapper.ExecutionProfileMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service Implementation for managing {@link ExecutionProfile}.
 */
@Service
@Transactional
public class ExecutionProfileService {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionProfileService.class);

    private final ExecutionProfileRepository executionProfileRepository;
    private final ExecutionProfileMapper executionProfileMapper;

    public ExecutionProfileService(
        ExecutionProfileRepository executionProfileRepository,
        ExecutionProfileMapper executionProfileMapper
    ) {
        this.executionProfileRepository = executionProfileRepository;
        this.executionProfileMapper = executionProfileMapper;
    }

    /**
     * Save an execution profile.
     *
     * @param dto the entity DTO to save.
     * @return the persisted entity DTO.
     */
    public ExecutionProfileDTO save(ExecutionProfileDTO dto) {
        LOG.debug("Request to save ExecutionProfile : {}", dto);
        Instant now = Instant.now();

        if (dto.getProfileId() == null || dto.getProfileId().isBlank()) {
            dto.setProfileId(UUID.randomUUID().toString());
            dto.setCreatedAt(now);
        } else {
            executionProfileRepository.findById(dto.getProfileId()).ifPresentOrElse(
                existing -> {
                    if (dto.getCreatedAt() == null) {
                        dto.setCreatedAt(existing.getCreatedAt());
                    }
                },
                () -> {
                    if (dto.getCreatedAt() == null) {
                        dto.setCreatedAt(now);
                    }
                }
            );
        }
        dto.setUpdatedAt(now);

        // Enforce uniqueness of (function_id, profile_name)
        Optional<ExecutionProfile> duplicate = executionProfileRepository.findByFunctionIdAndProfileName(
            dto.getFunctionId(),
            dto.getProfileName()
        );
        if (duplicate.isPresent() && !duplicate.get().getProfileId().equals(dto.getProfileId())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "An execution profile named '" + dto.getProfileName() + "' already exists for this function"
            );
        }

        try {
            ExecutionProfile entity = executionProfileMapper.toEntity(dto);
            entity = executionProfileRepository.save(entity);
            return executionProfileMapper.toDto(entity);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Profile name conflict or constraint violation: " + e.getMessage()
            );
        }
    }

    /**
     * Get all execution profiles for a specific function.
     *
     * @param functionId function UUID.
     * @return list of profiles.
     */
    @Transactional(readOnly = true)
    public List<ExecutionProfileDTO> findAllByFunctionId(UUID functionId) {
        LOG.debug("Request to get all ExecutionProfiles for function : {}", functionId);
        return executionProfileRepository.findAllByFunctionIdOrderByProfileNameAsc(functionId)
            .stream()
            .map(executionProfileMapper::toDto)
            .toList();
    }

    /**
     * Get all execution profiles across all functions.
     *
     * @return list of all profiles.
     */
    @Transactional(readOnly = true)
    public List<ExecutionProfileDTO> findAll() {
        LOG.debug("Request to get all ExecutionProfiles");
        return executionProfileRepository.findAllByOrderByProfileNameAsc()
            .stream()
            .map(executionProfileMapper::toDto)
            .toList();
    }

    /**
     * Get one execution profile by id.
     *
     * @param id the profile id.
     * @return the entity DTO.
     */
    @Transactional(readOnly = true)
    public Optional<ExecutionProfileDTO> findOne(String id) {
        LOG.debug("Request to get ExecutionProfile : {}", id);
        return executionProfileRepository.findById(id).map(executionProfileMapper::toDto);
    }

    /**
     * Delete the execution profile by id.
     *
     * @param id the profile id.
     */
    public void delete(String id) {
        LOG.debug("Request to delete ExecutionProfile : {}", id);
        executionProfileRepository.deleteById(id);
    }

    /**
     * Delete execution profile by function id and profile name.
     */
    public void deleteByFunctionIdAndName(UUID functionId, String profileName) {
        LOG.debug("Request to delete ExecutionProfile for function {} and name {}", functionId, profileName);
        executionProfileRepository.deleteByFunctionIdAndProfileName(functionId, profileName);
    }
}
