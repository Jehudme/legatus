package dev.legatus.server.repository;

import dev.legatus.server.domain.Account;
import dev.legatus.server.domain.enumeration.AccountStatus;
import dev.legatus.server.domain.enumeration.OperatorRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Account entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findOneByUsernameIgnoreCase(String username);

    Optional<Account> findOneByEmailIgnoreCase(String email);

    Optional<Account> findOneByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    Page<Account> findAllByRole(OperatorRole role, Pageable pageable);

    Page<Account> findAllByStatus(AccountStatus status, Pageable pageable);

    Page<Account> findAllByRoleAndStatus(OperatorRole role, AccountStatus status, Pageable pageable);

    long countByRoleAndStatus(OperatorRole role, AccountStatus status);
}
