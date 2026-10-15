package dev.legatus.server.repository;

import dev.legatus.server.domain.Account;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Account entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {}
