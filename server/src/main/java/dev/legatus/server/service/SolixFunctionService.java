package dev.legatus.server.service;

import dev.legatus.server.domain.SolixFunction;
import dev.legatus.server.repository.SolixFunctionRepository;
import dev.legatus.server.service.dto.SolixFunctionDTO;
import dev.legatus.server.service.mapper.SolixFunctionMapper;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link dev.legatus.server.domain.SolixFunction}.
 */
@Service
@Transactional
public class SolixFunctionService {

    private static final Logger LOG = LoggerFactory.getLogger(SolixFunctionService.class);

    private final SolixFunctionRepository solixFunctionRepository;
    private final SolixFunctionMapper solixFunctionMapper;
    private final dev.legatus.server.security.BytecodeSecurityValidator bytecodeSecurityValidator;
    private final dev.legatus.server.repository.ComputeTaskRepository computeTaskRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private dev.legatus.server.storage.BytecodeStorageService bytecodeStorageService;

    public SolixFunctionService(
        SolixFunctionRepository solixFunctionRepository,
        SolixFunctionMapper solixFunctionMapper,
        dev.legatus.server.security.BytecodeSecurityValidator bytecodeSecurityValidator,
        dev.legatus.server.repository.ComputeTaskRepository computeTaskRepository
    ) {
        this.solixFunctionRepository = solixFunctionRepository;
        this.solixFunctionMapper = solixFunctionMapper;
        this.bytecodeSecurityValidator = bytecodeSecurityValidator;
        this.computeTaskRepository = computeTaskRepository;
    }

    /**
     * Save a solixFunction.
     *
     * @param solixFunctionDTO the entity to save.
     * @return the persisted entity.
     */
    public SolixFunctionDTO save(SolixFunctionDTO solixFunctionDTO) {
        LOG.debug("Request to save SolixFunction : {}", solixFunctionDTO);
        if (solixFunctionDTO.getCreatedAt() == null) {
            solixFunctionDTO.setCreatedAt(java.time.Instant.now());
        }
        if (solixFunctionDTO.getSemver() == null || solixFunctionDTO.getSemver().isBlank()) {
            solixFunctionDTO.setSemver("1.0.0");
        }
        if (solixFunctionDTO.getBytecodeFormat() == null || solixFunctionDTO.getBytecodeFormat().isBlank()) {
            solixFunctionDTO.setBytecodeFormat("solix_bc_v1");
        }
        if (solixFunctionDTO.getEntryPoint() == null || solixFunctionDTO.getEntryPoint().isBlank()) {
            solixFunctionDTO.setEntryPoint("main");
        }
        if (solixFunctionDTO.getDefaultBudget() == null) {
            solixFunctionDTO.setDefaultBudget(1_000_000L);
        }
        if (solixFunctionDTO.getDefaultMemoryBytes() == null) {
            solixFunctionDTO.setDefaultMemoryBytes(33_554_432L);
        }
        if (solixFunctionDTO.getDefaultTimeoutMs() == null) {
            solixFunctionDTO.setDefaultTimeoutMs(5000);
        }
        if (solixFunctionDTO.getBytecodeBlobContentType() == null) {
            solixFunctionDTO.setBytecodeBlobContentType("application/octet-stream");
        }
        if (solixFunctionDTO.getBytecodeBlob() != null) {
            bytecodeSecurityValidator.validateBytecode(solixFunctionDTO.getBytecodeBlob());
            if (solixFunctionDTO.getBytecodeSha256() == null || solixFunctionDTO.getBytecodeSha256().isBlank()) {
                try {
                    var md = java.security.MessageDigest.getInstance("SHA-256");
                    solixFunctionDTO.setBytecodeSha256(java.util.HexFormat.of().formatHex(md.digest(solixFunctionDTO.getBytecodeBlob())));
                } catch (Exception ignored) {}
            }
            if (bytecodeStorageService != null && solixFunctionDTO.getBytecodeBlob().length > 0) {
                try {
                    bytecodeStorageService.store(solixFunctionDTO.getBytecodeBlob());
                } catch (Exception e) {
                    LOG.warn("Failed to store bytecode blob in CAS: {}", e.getMessage());
                }
            }
        }
        SolixFunction solixFunction = solixFunctionMapper.toEntity(solixFunctionDTO);
        solixFunction = solixFunctionRepository.save(solixFunction);
        return enrich(solixFunctionMapper.toDto(solixFunction));
    }

    /**
     * Update a solixFunction.
     *
     * @param solixFunctionDTO the entity to save.
     * @return the persisted entity.
     */
    public SolixFunctionDTO update(SolixFunctionDTO solixFunctionDTO) {
        LOG.debug("Request to update SolixFunction : {}", solixFunctionDTO);
        if (solixFunctionDTO.getBytecodeBlob() != null) {
            bytecodeSecurityValidator.validateBytecode(solixFunctionDTO.getBytecodeBlob());
            if (bytecodeStorageService != null && solixFunctionDTO.getBytecodeBlob().length > 0) {
                try {
                    bytecodeStorageService.store(solixFunctionDTO.getBytecodeBlob());
                } catch (Exception e) {
                    LOG.warn("Failed to store bytecode blob in CAS: {}", e.getMessage());
                }
            }
        }
        SolixFunction solixFunction = solixFunctionMapper.toEntity(solixFunctionDTO);
        solixFunction = solixFunctionRepository.save(solixFunction);
        return enrich(solixFunctionMapper.toDto(solixFunction));
    }

    /**
     * Partially update a solixFunction.
     *
     * @param solixFunctionDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<SolixFunctionDTO> partialUpdate(SolixFunctionDTO solixFunctionDTO) {
        LOG.debug("Request to partially update SolixFunction : {}", solixFunctionDTO);

        return solixFunctionRepository
            .findById(solixFunctionDTO.getFunctionId())
            .map(existingSolixFunction -> {
                solixFunctionMapper.partialUpdate(existingSolixFunction, solixFunctionDTO);

                return existingSolixFunction;
            })
            .map(solixFunctionRepository::save)
            .map(solixFunctionMapper::toDto);
    }

    /**
     * Get all the solixFunctions.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<SolixFunctionDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all SolixFunctions");
        return solixFunctionRepository.findAll(pageable)
            .map(solixFunctionMapper::toDto)
            .map(this::enrich);
    }

    /**
     * Get one solixFunction by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<SolixFunctionDTO> findOne(UUID id) {
        LOG.debug("Request to get SolixFunction : {}", id);
        return solixFunctionRepository.findById(id)
            .map(solixFunctionMapper::toDto)
            .map(this::enrich);
    }

    /**
     * Update metadata (description and tags) of an existing function.
     */
    public Optional<SolixFunctionDTO> updateMetadata(UUID functionId, String description, String tags) {
        LOG.debug("Request to update metadata for SolixFunction : {}", functionId);
        return solixFunctionRepository.findById(functionId).map(func -> {
            if (description != null) {
                func.setDescription(description);
            }
            if (tags != null) {
                func.setTags(tags);
            }
            SolixFunction saved = solixFunctionRepository.save(func);
            return enrich(solixFunctionMapper.toDto(saved));
        });
    }

    public SolixFunctionDTO enrich(SolixFunctionDTO dto) {
        if (dto != null && dto.getFunctionId() != null && computeTaskRepository != null) {
            java.time.Instant oneHourAgo = java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS);
            long count = computeTaskRepository.countInvocationsSince(dto.getFunctionId(), dto.getBytecodeSha256(), oneHourAgo);
            dto.setInvocationsLastHour(count);
        }
        return dto;
    }

    /**
     * Delete the solixFunction by id.
     *
     * @param id the id of the entity.
     */
    public void delete(UUID id) {
        LOG.debug("Request to delete SolixFunction : {}", id);
        if (computeTaskRepository != null && id != null) {
            computeTaskRepository.unlinkFunctionFromTasks(id);
        }
        solixFunctionRepository.deleteById(id);
    }
}
