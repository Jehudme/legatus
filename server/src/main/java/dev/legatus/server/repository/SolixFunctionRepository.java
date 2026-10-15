package dev.legatus.server.repository;

import dev.legatus.server.domain.SolixFunction;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the SolixFunction entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SolixFunctionRepository extends JpaRepository<SolixFunction, UUID> {}
