package dev.legatus.server.service;

import dev.legatus.server.domain.*; // for static metamodels
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.service.criteria.WorkerNodeCriteria;
import dev.legatus.server.service.dto.WorkerNodeDTO;
import dev.legatus.server.service.mapper.WorkerNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link WorkerNode} entities in the database.
 * The main input is a {@link WorkerNodeCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link WorkerNodeDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class WorkerNodeQueryService extends QueryService<WorkerNode> {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerNodeQueryService.class);

    private final WorkerNodeRepository workerNodeRepository;

    private final WorkerNodeMapper workerNodeMapper;

    public WorkerNodeQueryService(WorkerNodeRepository workerNodeRepository, WorkerNodeMapper workerNodeMapper) {
        this.workerNodeRepository = workerNodeRepository;
        this.workerNodeMapper = workerNodeMapper;
    }

    /**
     * Return a {@link Page} of {@link WorkerNodeDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<WorkerNodeDTO> findByCriteria(WorkerNodeCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<WorkerNode> specification = createSpecification(criteria);
        return workerNodeRepository.findAll(specification, page).map(workerNodeMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(WorkerNodeCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<WorkerNode> specification = createSpecification(criteria);
        return workerNodeRepository.count(specification);
    }

    /**
     * Function to convert {@link WorkerNodeCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<WorkerNode> createSpecification(WorkerNodeCriteria criteria) {
        Specification<WorkerNode> specification = Specification.unrestricted();
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = specification.and(
                Specification.allOf(
                    Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                    buildStringSpecification(criteria.getWorkerId(), WorkerNode_.workerId),
                    buildStringSpecification(criteria.getEnrollmentId(), WorkerNode_.enrollmentId),
                    buildStringSpecification(criteria.getHostname(), WorkerNode_.hostname),
                    buildSpecification(criteria.getOs(), WorkerNode_.os),
                    buildSpecification(criteria.getArch(), WorkerNode_.arch),
                    buildStringSpecification(criteria.getAgentVersion(), WorkerNode_.agentVersion),
                    buildStringSpecification(criteria.getEd25519Pubkey(), WorkerNode_.ed25519Pubkey),
                    buildStringSpecification(criteria.getFingerprint(), WorkerNode_.fingerprint),
                    buildStringSpecification(criteria.getVisualCode(), WorkerNode_.visualCode),
                    buildStringSpecification(criteria.getSecretHash(), WorkerNode_.secretHash),
                    buildSpecification(criteria.getState(), WorkerNode_.state),
                    buildStringSpecification(criteria.getAssignedCluster(), WorkerNode_.assignedCluster),
                    buildStringSpecification(criteria.getSourceIp(), WorkerNode_.sourceIp),
                    buildRangeSpecification(criteria.getPhysicalCores(), WorkerNode_.physicalCores),
                    buildRangeSpecification(criteria.getLogicalCores(), WorkerNode_.logicalCores),
                    buildRangeSpecification(criteria.getTotalMemoryBytes(), WorkerNode_.totalMemoryBytes),
                    buildSpecification(criteria.getGpuPresent(), WorkerNode_.gpuPresent),
                    buildStringSpecification(criteria.getGpuModel(), WorkerNode_.gpuModel),
                    buildRangeSpecification(criteria.getAllocatableCores(), WorkerNode_.allocatableCores),
                    buildRangeSpecification(criteria.getMaxMemoryBytes(), WorkerNode_.maxMemoryBytes),
                    buildRangeSpecification(criteria.getMaxInstructionBudget(), WorkerNode_.maxInstructionBudget),
                    buildSpecification(criteria.getDraining(), WorkerNode_.draining),
                    buildStringSpecification(criteria.getActiveSessionId(), WorkerNode_.activeSessionId),
                    buildRangeSpecification(criteria.getEnrollmentExpiresAt(), WorkerNode_.enrollmentExpiresAt),
                    buildRangeSpecification(criteria.getLastSeenAt(), WorkerNode_.lastSeenAt),
                    buildRangeSpecification(criteria.getCreatedAt(), WorkerNode_.createdAt)
                )
            );
        }
        return specification;
    }
}
