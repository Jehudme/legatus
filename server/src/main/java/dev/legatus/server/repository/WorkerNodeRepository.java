package dev.legatus.server.repository;

import dev.legatus.server.domain.WorkerNode;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the WorkerNode entity.
 */
@SuppressWarnings("unused")
@Repository
public interface WorkerNodeRepository extends JpaRepository<WorkerNode, String>, JpaSpecificationExecutor<WorkerNode> {}
