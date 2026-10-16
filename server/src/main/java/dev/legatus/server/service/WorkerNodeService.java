package dev.legatus.server.service;

import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.service.dto.WorkerNodeDTO;
import dev.legatus.server.service.mapper.WorkerNodeMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link dev.legatus.server.domain.WorkerNode}.
 */
@Service
@Transactional
public class WorkerNodeService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerNodeService.class);

    private final WorkerNodeRepository workerNodeRepository;

    private final WorkerNodeMapper workerNodeMapper;

    public WorkerNodeService(WorkerNodeRepository workerNodeRepository, WorkerNodeMapper workerNodeMapper) {
        this.workerNodeRepository = workerNodeRepository;
        this.workerNodeMapper = workerNodeMapper;
    }

    /**
     * Save a workerNode.
     *
     * @param workerNodeDTO the entity to save.
     * @return the persisted entity.
     */
    public WorkerNodeDTO save(WorkerNodeDTO workerNodeDTO) {
        LOG.debug("Request to save WorkerNode : {}", workerNodeDTO);
        WorkerNode workerNode = workerNodeMapper.toEntity(workerNodeDTO);
        workerNode = workerNodeRepository.save(workerNode);
        return workerNodeMapper.toDto(workerNode);
    }

    /**
     * Update a workerNode.
     *
     * @param workerNodeDTO the entity to save.
     * @return the persisted entity.
     */
    public WorkerNodeDTO update(WorkerNodeDTO workerNodeDTO) {
        LOG.debug("Request to update WorkerNode : {}", workerNodeDTO);
        WorkerNode workerNode = workerNodeRepository
            .findById(workerNodeDTO.getWorkerId())
            .orElseGet(() -> workerNodeMapper.toEntity(workerNodeDTO));
        workerNodeMapper.partialUpdate(workerNode, workerNodeDTO);
        workerNode.setIsPersisted();
        workerNode = workerNodeRepository.save(workerNode);
        return workerNodeMapper.toDto(workerNode);
    }

    /**
     * Partially update a workerNode.
     *
     * @param workerNodeDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<WorkerNodeDTO> partialUpdate(WorkerNodeDTO workerNodeDTO) {
        LOG.debug("Request to partially update WorkerNode : {}", workerNodeDTO);

        return workerNodeRepository
            .findById(workerNodeDTO.getWorkerId())
            .map(existingWorkerNode -> {
                workerNodeMapper.partialUpdate(existingWorkerNode, workerNodeDTO);

                return existingWorkerNode;
            })
            .map(workerNodeRepository::save)
            .map(workerNodeMapper::toDto);
    }

    /**
     * Get one workerNode by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<WorkerNodeDTO> findOne(String id) {
        LOG.debug("Request to get WorkerNode : {}", id);
        return workerNodeRepository.findById(id).map(workerNodeMapper::toDto);
    }

    /**
     * Delete the workerNode by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete WorkerNode : {}", id);
        workerNodeRepository.deleteById(id);
    }
}
