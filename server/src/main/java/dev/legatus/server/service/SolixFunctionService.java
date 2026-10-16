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

    public SolixFunctionService(
        SolixFunctionRepository solixFunctionRepository,
        SolixFunctionMapper solixFunctionMapper,
        dev.legatus.server.security.BytecodeSecurityValidator bytecodeSecurityValidator
    ) {
        this.solixFunctionRepository = solixFunctionRepository;
        this.solixFunctionMapper = solixFunctionMapper;
        this.bytecodeSecurityValidator = bytecodeSecurityValidator;
    }

    /**
     * Save a solixFunction.
     *
     * @param solixFunctionDTO the entity to save.
     * @return the persisted entity.
     */
    public SolixFunctionDTO save(SolixFunctionDTO solixFunctionDTO) {
        LOG.debug("Request to save SolixFunction : {}", solixFunctionDTO);
        if (solixFunctionDTO.getBytecodeBlob() != null) {
            bytecodeSecurityValidator.validateBytecode(solixFunctionDTO.getBytecodeBlob());
        }
        SolixFunction solixFunction = solixFunctionMapper.toEntity(solixFunctionDTO);
        solixFunction = solixFunctionRepository.save(solixFunction);
        return solixFunctionMapper.toDto(solixFunction);
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
        }
        SolixFunction solixFunction = solixFunctionMapper.toEntity(solixFunctionDTO);
        solixFunction = solixFunctionRepository.save(solixFunction);
        return solixFunctionMapper.toDto(solixFunction);
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
        return solixFunctionRepository.findAll(pageable).map(solixFunctionMapper::toDto);
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
        return solixFunctionRepository.findById(id).map(solixFunctionMapper::toDto);
    }

    /**
     * Delete the solixFunction by id.
     *
     * @param id the id of the entity.
     */
    public void delete(UUID id) {
        LOG.debug("Request to delete SolixFunction : {}", id);
        solixFunctionRepository.deleteById(id);
    }
}
