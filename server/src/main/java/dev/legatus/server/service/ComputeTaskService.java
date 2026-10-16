package dev.legatus.server.service;

import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.service.dto.ComputeTaskDTO;
import dev.legatus.server.service.mapper.ComputeTaskMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link dev.legatus.server.domain.ComputeTask}.
 */
@Service
@Transactional
public class ComputeTaskService {

    private static final Logger LOG = LoggerFactory.getLogger(ComputeTaskService.class);

    private final ComputeTaskRepository computeTaskRepository;

    private final ComputeTaskMapper computeTaskMapper;

    public ComputeTaskService(ComputeTaskRepository computeTaskRepository, ComputeTaskMapper computeTaskMapper) {
        this.computeTaskRepository = computeTaskRepository;
        this.computeTaskMapper = computeTaskMapper;
    }

    /**
     * Save a computeTask.
     *
     * @param computeTaskDTO the entity to save.
     * @return the persisted entity.
     */
    public ComputeTaskDTO save(ComputeTaskDTO computeTaskDTO) {
        LOG.debug("Request to save ComputeTask : {}", computeTaskDTO);
        ComputeTask computeTask = computeTaskMapper.toEntity(computeTaskDTO);
        computeTask = computeTaskRepository.save(computeTask);
        return computeTaskMapper.toDto(computeTask);
    }

    /**
     * Update a computeTask.
     *
     * @param computeTaskDTO the entity to save.
     * @return the persisted entity.
     */
    public ComputeTaskDTO update(ComputeTaskDTO computeTaskDTO) {
        LOG.debug("Request to update ComputeTask : {}", computeTaskDTO);
        ComputeTask computeTask = computeTaskRepository
            .findById(computeTaskDTO.getTaskId())
            .orElseGet(() -> computeTaskMapper.toEntity(computeTaskDTO));
        computeTaskMapper.partialUpdate(computeTask, computeTaskDTO);
        computeTask.setIsPersisted();
        computeTask = computeTaskRepository.save(computeTask);
        return computeTaskMapper.toDto(computeTask);
    }

    /**
     * Partially update a computeTask.
     *
     * @param computeTaskDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ComputeTaskDTO> partialUpdate(ComputeTaskDTO computeTaskDTO) {
        LOG.debug("Request to partially update ComputeTask : {}", computeTaskDTO);

        return computeTaskRepository
            .findById(computeTaskDTO.getTaskId())
            .map(existingComputeTask -> {
                computeTaskMapper.partialUpdate(existingComputeTask, computeTaskDTO);

                return existingComputeTask;
            })
            .map(computeTaskRepository::save)
            .map(computeTaskMapper::toDto);
    }

    /**
     * Get all the computeTasks with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<ComputeTaskDTO> findAllWithEagerRelationships(Pageable pageable) {
        return computeTaskRepository.findAllWithEagerRelationships(pageable).map(computeTaskMapper::toDto);
    }

    /**
     * Get one computeTask by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ComputeTaskDTO> findOne(String id) {
        LOG.debug("Request to get ComputeTask : {}", id);
        return computeTaskRepository.findOneWithEagerRelationships(id).map(computeTaskMapper::toDto);
    }

    /**
     * Delete the computeTask by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete ComputeTask : {}", id);
        computeTaskRepository.deleteById(id);
    }
}
