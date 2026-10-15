package dev.legatus.server.service;

import dev.legatus.server.domain.*; // for static metamodels
import dev.legatus.server.domain.AuditLog;
import dev.legatus.server.repository.AuditLogRepository;
import dev.legatus.server.service.criteria.AuditLogCriteria;
import dev.legatus.server.service.dto.AuditLogDTO;
import dev.legatus.server.service.mapper.AuditLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.QueryService;

/**
 * Service for executing complex queries for {@link AuditLog} entities in the database.
 * The main input is a {@link AuditLogCriteria} which gets converted to {@link Specification},
 * in a way that all the filters must apply.
 * It returns a {@link Page} of {@link AuditLogDTO} which fulfills the criteria.
 */
@Service
@Transactional(readOnly = true)
public class AuditLogQueryService extends QueryService<AuditLog> {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogQueryService.class);

    private final AuditLogRepository auditLogRepository;

    private final AuditLogMapper auditLogMapper;

    public AuditLogQueryService(AuditLogRepository auditLogRepository, AuditLogMapper auditLogMapper) {
        this.auditLogRepository = auditLogRepository;
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * Return a {@link Page} of {@link AuditLogDTO} which matches the criteria from the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @param page The page, which should be returned.
     * @return the matching entities.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogDTO> findByCriteria(AuditLogCriteria criteria, Pageable page) {
        LOG.debug("find by criteria : {}, page: {}", criteria, page);
        final Specification<AuditLog> specification = createSpecification(criteria);
        return auditLogRepository.findAll(specification, page).map(auditLogMapper::toDto);
    }

    /**
     * Return the number of matching entities in the database.
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the number of matching entities.
     */
    @Transactional(readOnly = true)
    public long countByCriteria(AuditLogCriteria criteria) {
        LOG.debug("count by criteria : {}", criteria);
        final Specification<AuditLog> specification = createSpecification(criteria);
        return auditLogRepository.count(specification);
    }

    /**
     * Function to convert {@link AuditLogCriteria} to a {@link Specification}
     * @param criteria The object which holds all the filters, which the entities should match.
     * @return the matching {@link Specification} of the entity.
     */
    protected Specification<AuditLog> createSpecification(AuditLogCriteria criteria) {
        Specification<AuditLog> specification = Specification.unrestricted();
        if (criteria != null) {
            // This has to be called first, because the distinct method returns null
            specification = specification.and(
                Specification.allOf(
                    Boolean.TRUE.equals(criteria.getDistinct()) ? distinct(criteria.getDistinct()) : Specification.unrestricted(),
                    buildSpecification(criteria.getEventId(), AuditLog_.eventId),
                    buildRangeSpecification(criteria.getSequenceNum(), AuditLog_.sequenceNum),
                    buildRangeSpecification(criteria.getTimestamp(), AuditLog_.timestamp),
                    buildStringSpecification(criteria.getActorId(), AuditLog_.actorId),
                    buildStringSpecification(criteria.getActorRole(), AuditLog_.actorRole),
                    buildStringSpecification(criteria.getEventType(), AuditLog_.eventType),
                    buildStringSpecification(criteria.getTargetType(), AuditLog_.targetType),
                    buildStringSpecification(criteria.getTargetId(), AuditLog_.targetId),
                    buildSpecification(criteria.getActionStatus(), AuditLog_.actionStatus),
                    buildStringSpecification(criteria.getSourceIp(), AuditLog_.sourceIp),
                    buildStringSpecification(criteria.getPrevRecordHash(), AuditLog_.prevRecordHash),
                    buildStringSpecification(criteria.getRecordHash(), AuditLog_.recordHash),
                    buildStringSpecification(criteria.getComplianceFlag(), AuditLog_.complianceFlag)
                )
            );
        }
        return specification;
    }
}
