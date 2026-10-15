package dev.legatus.server.repository;

import dev.legatus.server.domain.AuditLog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the AuditLog entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    Optional<AuditLog> findTopByOrderBySequenceNumDesc();

    List<AuditLog> findAllByOrderBySequenceNumAsc();

    List<AuditLog> findBySequenceNumBetweenOrderBySequenceNumAsc(Long startSeq, Long endSeq);
}
