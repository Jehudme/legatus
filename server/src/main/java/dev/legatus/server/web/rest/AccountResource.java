package dev.legatus.server.web.rest;

import dev.legatus.server.repository.AccountRepository;
import dev.legatus.server.service.AccountService;
import dev.legatus.server.service.dto.AccountDTO;
import dev.legatus.server.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link dev.legatus.server.domain.Account}.
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountResource {

    private static final Logger LOG = LoggerFactory.getLogger(AccountResource.class);

    private static final String ENTITY_NAME = "account";

    @Value("${jhipster.clientApp.name:legatusServer}")
    private String applicationName;

    private final AccountService accountService;

    private final AccountRepository accountRepository;

    public AccountResource(AccountService accountService, AccountRepository accountRepository) {
        this.accountService = accountService;
        this.accountRepository = accountRepository;
    }

    /**
     * {@code POST  /accounts} : Create a new account.
     *
     * @param accountDTO the accountDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new accountDTO, or with status {@code 400 (Bad Request)} if the account already has an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<AccountDTO> createAccount(@Valid @RequestBody AccountDTO accountDTO) throws URISyntaxException {
        LOG.debug("REST request to save Account : {}", accountDTO);
        if (accountDTO.getAccountId() != null) {
            throw new BadRequestAlertException("A new account cannot already have an ID", ENTITY_NAME, "idexists");
        }
        accountDTO = accountService.save(accountDTO);
        return ResponseEntity.created(new URI("/api/accounts/" + accountDTO.getAccountId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, accountDTO.getAccountId().toString()))
            .body(accountDTO);
    }

    /**
     * {@code PUT  /accounts/:accountId} : Updates an existing account.
     *
     * @param accountId the id of the accountDTO to save.
     * @param accountDTO the accountDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated accountDTO,
     * or with status {@code 400 (Bad Request)} if the accountDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the accountDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{accountId}")
    public ResponseEntity<AccountDTO> updateAccount(
        @PathVariable(value = "accountId", required = false) final UUID accountId,
        @Valid @RequestBody AccountDTO accountDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update Account : {}, {}", accountId, accountDTO);
        if (accountDTO.getAccountId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(accountId, accountDTO.getAccountId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!accountRepository.existsById(accountId)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        accountDTO = accountService.update(accountDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, accountDTO.getAccountId().toString()))
            .body(accountDTO);
    }

    /**
     * {@code PATCH  /accounts/:accountId} : Partial updates given fields of an existing account, field will ignore if it is null
     *
     * @param accountId the id of the accountDTO to save.
     * @param accountDTO the accountDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated accountDTO,
     * or with status {@code 400 (Bad Request)} if the accountDTO is not valid,
     * or with status {@code 404 (Not Found)} if the accountDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the accountDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{accountId}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<AccountDTO> partialUpdateAccount(
        @PathVariable(value = "accountId", required = false) final UUID accountId,
        @NotNull @RequestBody AccountDTO accountDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partially update Account : {}, {}", accountId, accountDTO);
        if (accountDTO.getAccountId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(accountId, accountDTO.getAccountId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!accountRepository.existsById(accountId)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<AccountDTO> result = accountService.partialUpdate(accountDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, accountDTO.getAccountId().toString())
        );
    }

    /**
     * {@code GET  /accounts} : get all the Accounts.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Accounts in body.
     */
    @GetMapping("")
    public ResponseEntity<List<AccountDTO>> getAllAccounts(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of Accounts");
        Page<AccountDTO> page = accountService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /accounts/:id} : get the "id" account.
     *
     * @param id the id of the accountDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the accountDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccountDTO> getAccount(@PathVariable("id") UUID id) {
        LOG.debug("REST request to get Account : {}", id);
        Optional<AccountDTO> accountDTO = accountService.findOne(id);
        return ResponseUtil.wrapOrNotFound(accountDTO);
    }

    /**
     * {@code DELETE  /accounts/:id} : delete the "id" account.
     *
     * @param id the id of the accountDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable("id") UUID id) {
        LOG.debug("REST request to delete Account : {}", id);
        accountService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
