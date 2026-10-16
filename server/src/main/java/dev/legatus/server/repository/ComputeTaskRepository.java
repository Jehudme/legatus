package dev.legatus.server.repository;

import dev.legatus.server.domain.ComputeTask;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ComputeTask entity.
 */
@Repository
public interface ComputeTaskRepository extends JpaRepository<ComputeTask, String>, JpaSpecificationExecutor<ComputeTask> {
    default Optional<ComputeTask> findOneWithEagerRelationships(String taskId) {
        return this.findOneWithToOneRelationships(taskId);
    }

    default List<ComputeTask> findAllWithEagerRelationships() {
        return this.findAllWithToOneRelationships();
    }

    default Page<ComputeTask> findAllWithEagerRelationships(Pageable pageable) {
        return this.findAllWithToOneRelationships(pageable);
    }

    @Query(
        value = "select computeTask from ComputeTask computeTask left join fetch computeTask.function",
        countQuery = "select count(computeTask) from ComputeTask computeTask"
    )
    Page<ComputeTask> findAllWithToOneRelationships(Pageable pageable);

    @Query("select computeTask from ComputeTask computeTask left join fetch computeTask.function")
    List<ComputeTask> findAllWithToOneRelationships();

    @Query("select computeTask from ComputeTask computeTask left join fetch computeTask.function where computeTask.id =:id")
    Optional<ComputeTask> findOneWithToOneRelationships(@Param("id") String id);

    List<ComputeTask> findAllByAssignedWorkerWorkerIdAndStatusIn(String workerId, java.util.Collection<dev.legatus.server.domain.enumeration.TaskStatus> statuses);

    List<ComputeTask> findAllByStatusOrderByPriorityWeightDescCreatedAtAsc(dev.legatus.server.domain.enumeration.TaskStatus status);

    List<ComputeTask> findAllByStatusOrderByPriorityWeightDescCreatedAtAsc(
        dev.legatus.server.domain.enumeration.TaskStatus status,
        Pageable pageable
    );

    long countByStatus(dev.legatus.server.domain.enumeration.TaskStatus status);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ComputeTask t WHERE t.status IN :terminalStatuses AND t.completedAt < :cutoff")
    int deleteCompletedTasksOlderThan(
        @Param("terminalStatuses") java.util.Collection<dev.legatus.server.domain.enumeration.TaskStatus> terminalStatuses,
        @Param("cutoff") java.time.Instant cutoff
    );

    @Query("SELECT COUNT(t) FROM ComputeTask t WHERE (t.function.functionId = :functionId OR t.bytecodeSha256 = :sha256) AND t.createdAt >= :since")
    long countInvocationsSince(
        @Param("functionId") java.util.UUID functionId,
        @Param("sha256") String sha256,
        @Param("since") java.time.Instant since
    );
}
