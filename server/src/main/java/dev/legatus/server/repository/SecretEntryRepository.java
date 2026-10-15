package dev.legatus.server.repository;

import dev.legatus.server.domain.SecretEntry;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the SecretEntry entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SecretEntryRepository extends JpaRepository<SecretEntry, String> {}
