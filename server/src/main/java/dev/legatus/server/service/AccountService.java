package dev.legatus.server.service;

import dev.legatus.server.domain.Account;
import dev.legatus.server.repository.AccountRepository;
import dev.legatus.server.service.dto.AccountDTO;
import dev.legatus.server.service.mapper.AccountMapper;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link dev.legatus.server.domain.Account}.
 */
@Service
@Transactional
public class AccountService {

    private static final Logger LOG = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;

    private final AccountMapper accountMapper;

    public AccountService(AccountRepository accountRepository, AccountMapper accountMapper) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
    }

    /**
     * Save a account.
     *
     * @param accountDTO the entity to save.
     * @return the persisted entity.
     */
    public AccountDTO save(AccountDTO accountDTO) {
        LOG.debug("Request to save Account : {}", accountDTO);
        Account account = accountMapper.toEntity(accountDTO);
        account = accountRepository.save(account);
        return accountMapper.toDto(account);
    }

    /**
     * Update a account.
     *
     * @param accountDTO the entity to save.
     * @return the persisted entity.
     */
    public AccountDTO update(AccountDTO accountDTO) {
        LOG.debug("Request to update Account : {}", accountDTO);
        Account account = accountMapper.toEntity(accountDTO);
        account = accountRepository.save(account);
        return accountMapper.toDto(account);
    }

    /**
     * Partially update a account.
     *
     * @param accountDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<AccountDTO> partialUpdate(AccountDTO accountDTO) {
        LOG.debug("Request to partially update Account : {}", accountDTO);

        return accountRepository
            .findById(accountDTO.getAccountId())
            .map(existingAccount -> {
                accountMapper.partialUpdate(existingAccount, accountDTO);

                return existingAccount;
            })
            .map(accountRepository::save)
            .map(accountMapper::toDto);
    }

    /**
     * Get all the accounts.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<AccountDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all Accounts");
        return accountRepository.findAll(pageable).map(accountMapper::toDto);
    }

    /**
     * Get one account by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<AccountDTO> findOne(UUID id) {
        LOG.debug("Request to get Account : {}", id);
        return accountRepository.findById(id).map(accountMapper::toDto);
    }

    /**
     * Delete the account by id.
     *
     * @param id the id of the entity.
     */
    public void delete(UUID id) {
        LOG.debug("Request to delete Account : {}", id);
        accountRepository.deleteById(id);
    }
}
