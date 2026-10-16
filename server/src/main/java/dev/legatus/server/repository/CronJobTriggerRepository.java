package dev.legatus.server.repository;

import dev.legatus.server.domain.CronJobTrigger;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link CronJobTrigger} entity.
 */
@Repository
public interface CronJobTriggerRepository extends JpaRepository<CronJobTrigger, String> {

    List<CronJobTrigger> findAllByActiveTrue();

    List<CronJobTrigger> findAllByFunctionId(String functionId);
}
