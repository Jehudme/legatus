package dev.legatus.server.service;

import dev.legatus.server.domain.*; // for static metamodels
import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.service.criteria.ComputeTaskCriteria;
import dev.legatus.server.service.dto.ComputeTaskDTO;
import dev.legatus.server.service.mapper.ComputeTaskMapper;
import jakarta.persistence.criteria.JoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link ComputeTask} entities in the database.
 * The main input is a {@link ComputeTaskCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link ComputeTaskDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class ComputeTaskQueryService extends QueryService<ComputeTask> {

    private static final Logger LOG = LoggerFactory.getLogger(ComputeTaskQueryService.class);

    private final ComputeTaskRepository computeTaskRepository;

    private final ComputeTaskMapper computeTaskMapper;

    public ComputeTaskQueryService(ComputeTaskRepository computeTaskRepository, ComputeTaskMapper computeTaskMapper) {
        this.computeTaskRepository = computeTaskRepository;
        this.computeTaskMapper = computeTaskMapper;
    }

    /**
     * Return a {@link Page} of {@link ComputeTaskDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<ComputeTaskDTO> findByCriteria(ComputeTaskCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<ComputeTask> specification = createSpecification(criteria);
        return computeTaskRepository.findAll(specification, page).map(computeTaskMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(ComputeTaskCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<ComputeTask> specification = createSpecification(criteria);
        return computeTaskRepository.count(specification);
    }

    /**
     * Function to convert {@link ComputeTaskCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<ComputeTask> createSpecification(ComputeTaskCriteria criteria) {
        Specification<ComputeTask> specification = Specification.unrestricted();
        specification = specification.and((root, query, builder) -> {
            if (Long.class != query.getResultType()) {
                root.fetch(ComputeTask_.function, JoinType.LEFT);
                root.fetch(ComputeTask_.assignedWorker, JoinType.LEFT);
            }
            return null;
        });
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = specification.and(
                Specification.allOf(
                    Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                    buildStringSpecification(criteria.getTaskId(), ComputeTask_.taskId),
                    buildStringSpecification(criteria.getName(), ComputeTask_.name),
                    buildSpecification(criteria.getStatus(), ComputeTask_.status),
                    buildSpecification(criteria.getPriority(), ComputeTask_.priority),
                    buildRangeSpecification(criteria.getPriorityWeight(), ComputeTask_.priorityWeight),
                    buildSpecification(criteria.getIdempotent(), ComputeTask_.idempotent),
                    buildStringSpecification(criteria.getEntryPoint(), ComputeTask_.entryPoint),
                    buildStringSpecification(criteria.getBytecodeSha256(), ComputeTask_.bytecodeSha256),
                    buildRangeSpecification(criteria.getInstructionBudget(), ComputeTask_.instructionBudget),
                    buildRangeSpecification(criteria.getMaxMemoryBytes(), ComputeTask_.maxMemoryBytes),
                    buildRangeSpecification(criteria.getTimeoutMs(), ComputeTask_.timeoutMs),
                    buildRangeSpecification(criteria.getExitCode(), ComputeTask_.exitCode),
                    buildRangeSpecification(criteria.getInstructionsConsumed(), ComputeTask_.instructionsConsumed),
                    buildRangeSpecification(criteria.getPeakMemoryBytes(), ComputeTask_.peakMemoryBytes),
                    buildRangeSpecification(criteria.getWallTimeMs(), ComputeTask_.wallTimeMs),
                    buildRangeSpecification(criteria.getAttemptCount(), ComputeTask_.attemptCount),
                    buildRangeSpecification(criteria.getMaxRetries(), ComputeTask_.maxRetries),
                    buildRangeSpecification(criteria.getCreatedAt(), ComputeTask_.createdAt),
                    buildRangeSpecification(criteria.getStartedAt(), ComputeTask_.startedAt),
                    buildRangeSpecification(criteria.getCompletedAt(), ComputeTask_.completedAt),
                    buildSpecification(criteria.getFunctionId(), root ->
                        root.join(ComputeTask_.function, JoinType.LEFT).get(SolixFunction_.functionId)
                    ),
                    buildSpecification(criteria.getAssignedWorkerId(), root ->
                        root.join(ComputeTask_.assignedWorker, JoinType.LEFT).get(WorkerNode_.workerId)
                    )
                )
            );
        }
        return specification;
    }
}
