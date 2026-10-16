package dev.legatus.server.repository;

import dev.legatus.server.domain.ClusterSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ClusterSetting entity.
 */
@Repository
public interface ClusterSettingRepository extends JpaRepository<ClusterSetting, String> {}
