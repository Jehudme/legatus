package dev.legatus.server.repository;

import dev.legatus.server.domain.ExecutionProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link ExecutionProfile} entity.
 */
@Repository
public interface ExecutionProfileRepository extends JpaRepository<ExecutionProfile, String> {

    List<ExecutionProfile> findAllByFunctionIdOrderByProfileNameAsc(UUID functionId);

    List<ExecutionProfile> findAllByOrderByProfileNameAsc();

    Optional<ExecutionProfile> findByFunctionIdAndProfileName(UUID functionId, String profileName);

    void deleteByFunctionIdAndProfileName(UUID functionId, String profileName);
}
