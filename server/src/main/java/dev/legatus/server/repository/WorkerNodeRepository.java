package dev.legatus.server.repository;

import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.WorkerState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the WorkerNode entity.
 */
@SuppressWarnings("unused")
@Repository
public interface WorkerNodeRepository extends JpaRepository<WorkerNode, String>, JpaSpecificationExecutor<WorkerNode> {

    Optional<WorkerNode> findByEnrollmentId(String enrollmentId);

    long countByState(WorkerState state);

    List<WorkerNode> findByStateAndEnrollmentExpiresAtBefore(WorkerState state, Instant expiry);
}
