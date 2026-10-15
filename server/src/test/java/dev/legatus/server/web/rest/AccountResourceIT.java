package dev.legatus.server.web.rest;

import static dev.legatus.server.domain.AccountAsserts.*;
import static dev.legatus.server.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.Account;
import dev.legatus.server.domain.enumeration.AccountStatus;
import dev.legatus.server.domain.enumeration.OperatorRole;
import dev.legatus.server.repository.AccountRepository;
import dev.legatus.server.service.dto.AccountDTO;
import dev.legatus.server.service.mapper.AccountMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for the {@link AccountResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class AccountResourceIT {

    private static final String DEFAULT_USERNAME = "AAAAAAAAAA";
    private static final String UPDATED_USERNAME = "BBBBBBBBBB";

    private static final String DEFAULT_EMAIL = "AAAAAAAAAA";
    private static final String UPDATED_EMAIL = "BBBBBBBBBB";

    private static final String DEFAULT_PASSWORD_HASH = "AAAAAAAAAA";
    private static final String UPDATED_PASSWORD_HASH = "BBBBBBBBBB";

    private static final OperatorRole DEFAULT_ROLE = OperatorRole.CLUSTER_ADMIN;
    private static final OperatorRole UPDATED_ROLE = OperatorRole.OPERATOR;

    private static final AccountStatus DEFAULT_STATUS = AccountStatus.PENDING_ACTIVATION;
    private static final AccountStatus UPDATED_STATUS = AccountStatus.ACTIVE;

    private static final Integer DEFAULT_FAILED_LOGIN_ATTEMPTS = 0;
    private static final Integer UPDATED_FAILED_LOGIN_ATTEMPTS = 1;

    private static final Instant DEFAULT_LOCKOUT_UNTIL = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_LOCKOUT_UNTIL = Instant.ofEpochMilli(1703883753520L);

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.ofEpochMilli(1703883753520L);

    private static final Instant DEFAULT_UPDATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_UPDATED_AT = Instant.ofEpochMilli(1703883753520L);

    private static final String ENTITY_API_URL = "/api/accounts";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{accountId}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restAccountMockMvc;

    private Account account;

    private Account insertedAccount;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Account createEntity() {
        return new Account()
            .username(DEFAULT_USERNAME)
            .email(DEFAULT_EMAIL)
            .passwordHash(DEFAULT_PASSWORD_HASH)
            .role(DEFAULT_ROLE)
            .status(DEFAULT_STATUS)
            .failedLoginAttempts(DEFAULT_FAILED_LOGIN_ATTEMPTS)
            .lockoutUntil(DEFAULT_LOCKOUT_UNTIL)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Account createUpdatedEntity() {
        return new Account()
            .username(UPDATED_USERNAME)
            .email(UPDATED_EMAIL)
            .passwordHash(UPDATED_PASSWORD_HASH)
            .role(UPDATED_ROLE)
            .status(UPDATED_STATUS)
            .failedLoginAttempts(UPDATED_FAILED_LOGIN_ATTEMPTS)
            .lockoutUntil(UPDATED_LOCKOUT_UNTIL)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
    }

    @BeforeEach
    void initTest() {
        account = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedAccount != null) {
            accountRepository.delete(insertedAccount);
            insertedAccount = null;
        }
    }

    @Test
    @Transactional
    void createAccount() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Account
        AccountDTO accountDTO = accountMapper.toDto(account);
        var returnedAccountDTO = om.readValue(
            restAccountMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(accountDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            AccountDTO.class
        );

        // Validate the Account in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedAccount = accountMapper.toEntity(returnedAccountDTO);
        assertAccountUpdatableFieldsEquals(returnedAccount, getPersistedAccount(returnedAccount));

        insertedAccount = returnedAccount;
    }

    @Test
    @Transactional
    void createAccountWithExistingId() throws Exception {
        // Create the Account with an existing ID
        insertedAccount = accountRepository.saveAndFlush(account);
        AccountDTO accountDTO = accountMapper.toDto(account);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(accountDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Account in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkUsernameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        account.setUsername(null);

        // Create the Account, which fails.
        AccountDTO accountDTO = accountMapper.toDto(account);

        restAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(accountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkEmailIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        account.setEmail(null);

        // Create the Account, which fails.
        AccountDTO accountDTO = accountMapper.toDto(account);

        restAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(accountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkPasswordHashIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        account.setPasswordHash(null);

        // Create the Account, which fails.
        AccountDTO accountDTO = accountMapper.toDto(account);

        restAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(accountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkRoleIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        account.setRole(null);

        // Create the Account, which fails.
        AccountDTO accountDTO = accountMapper.toDto(account);

        restAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(accountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkStatusIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        account.setStatus(null);

        // Create the Account, which fails.
        AccountDTO accountDTO = accountMapper.toDto(account);

        restAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(accountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkFailedLoginAttemptsIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        account.setFailedLoginAttempts(null);

        // Create the Account, which fails.
        AccountDTO accountDTO = accountMapper.toDto(account);

        restAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(accountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        account.setCreatedAt(null);

        // Create the Account, which fails.
        AccountDTO accountDTO = accountMapper.toDto(account);

        restAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(accountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkUpdatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        account.setUpdatedAt(null);

        // Create the Account, which fails.
        AccountDTO accountDTO = accountMapper.toDto(account);

        restAccountMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(accountDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllAccounts() throws Exception {
        // Initialize the database
        insertedAccount = accountRepository.saveAndFlush(account);

        // Get all the accountList
        restAccountMockMvc
            .perform(get(ENTITY_API_URL + "?sort=accountId,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].accountId").value(hasItem(account.getAccountId().toString())))
            .andExpect(jsonPath("$.[*].username").value(hasItem(DEFAULT_USERNAME)))
            .andExpect(jsonPath("$.[*].email").value(hasItem(DEFAULT_EMAIL)))
            .andExpect(jsonPath("$.[*].passwordHash").value(hasItem(DEFAULT_PASSWORD_HASH)))
            .andExpect(jsonPath("$.[*].role").value(hasItem(DEFAULT_ROLE.toString())))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].failedLoginAttempts").value(hasItem(DEFAULT_FAILED_LOGIN_ATTEMPTS)))
            .andExpect(jsonPath("$.[*].lockoutUntil").value(hasItem(DEFAULT_LOCKOUT_UNTIL.toString())))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));
    }

    @Test
    @Transactional
    void getAccount() throws Exception {
        // Initialize the database
        insertedAccount = accountRepository.saveAndFlush(account);

        // Get the account
        restAccountMockMvc
            .perform(get(ENTITY_API_URL_ID, account.getAccountId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.accountId").value(account.getAccountId().toString()))
            .andExpect(jsonPath("$.username").value(DEFAULT_USERNAME))
            .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
            .andExpect(jsonPath("$.passwordHash").value(DEFAULT_PASSWORD_HASH))
            .andExpect(jsonPath("$.role").value(DEFAULT_ROLE.toString()))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.failedLoginAttempts").value(DEFAULT_FAILED_LOGIN_ATTEMPTS))
            .andExpect(jsonPath("$.lockoutUntil").value(DEFAULT_LOCKOUT_UNTIL.toString()))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()))
            .andExpect(jsonPath("$.updatedAt").value(DEFAULT_UPDATED_AT.toString()));
    }

    @Test
    @Transactional
    void getNonExistingAccount() throws Exception {
        // Get the account
        restAccountMockMvc.perform(get(ENTITY_API_URL_ID, UUID.randomUUID().toString())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingAccount() throws Exception {
        // Initialize the database
        insertedAccount = accountRepository.saveAndFlush(account);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the account
        Account updatedAccount = accountRepository.findById(account.getAccountId()).orElseThrow();
        // Disconnect from session so that the updates on updatedAccount are not directly saved in db
        em.detach(updatedAccount);
        updatedAccount
            .username(UPDATED_USERNAME)
            .email(UPDATED_EMAIL)
            .passwordHash(UPDATED_PASSWORD_HASH)
            .role(UPDATED_ROLE)
            .status(UPDATED_STATUS)
            .failedLoginAttempts(UPDATED_FAILED_LOGIN_ATTEMPTS)
            .lockoutUntil(UPDATED_LOCKOUT_UNTIL)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        AccountDTO accountDTO = accountMapper.toDto(updatedAccount);

        restAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, accountDTO.getAccountId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(accountDTO))
            )
            .andExpect(status().isOk());

        // Validate the Account in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedAccountToMatchAllProperties(updatedAccount);
    }

    @Test
    @Transactional
    void putNonExistingAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        account.setAccountId(UUID.randomUUID());

        // Create the Account
        AccountDTO accountDTO = accountMapper.toDto(account);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, accountDTO.getAccountId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(accountDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Account in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        account.setAccountId(UUID.randomUUID());

        // Create the Account
        AccountDTO accountDTO = accountMapper.toDto(account);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAccountMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(accountDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Account in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        account.setAccountId(UUID.randomUUID());

        // Create the Account
        AccountDTO accountDTO = accountMapper.toDto(account);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAccountMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(accountDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Account in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateAccountWithPatch() throws Exception {
        // Initialize the database
        insertedAccount = accountRepository.saveAndFlush(account);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the account using partial update
        Account partialUpdatedAccount = new Account();
        partialUpdatedAccount.setAccountId(account.getAccountId());

        partialUpdatedAccount.email(UPDATED_EMAIL).createdAt(UPDATED_CREATED_AT);

        restAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedAccount.getAccountId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedAccount))
            )
            .andExpect(status().isOk());

        // Validate the Account in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertAccountUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedAccount, account), getPersistedAccount(account));
    }

    @Test
    @Transactional
    void fullUpdateAccountWithPatch() throws Exception {
        // Initialize the database
        insertedAccount = accountRepository.saveAndFlush(account);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the account using partial update
        Account partialUpdatedAccount = new Account();
        partialUpdatedAccount.setAccountId(account.getAccountId());

        partialUpdatedAccount
            .username(UPDATED_USERNAME)
            .email(UPDATED_EMAIL)
            .passwordHash(UPDATED_PASSWORD_HASH)
            .role(UPDATED_ROLE)
            .status(UPDATED_STATUS)
            .failedLoginAttempts(UPDATED_FAILED_LOGIN_ATTEMPTS)
            .lockoutUntil(UPDATED_LOCKOUT_UNTIL)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);

        restAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedAccount.getAccountId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedAccount))
            )
            .andExpect(status().isOk());

        // Validate the Account in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertAccountUpdatableFieldsEquals(partialUpdatedAccount, getPersistedAccount(partialUpdatedAccount));
    }

    @Test
    @Transactional
    void patchNonExistingAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        account.setAccountId(UUID.randomUUID());

        // Create the Account
        AccountDTO accountDTO = accountMapper.toDto(account);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, accountDTO.getAccountId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(accountDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Account in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        account.setAccountId(UUID.randomUUID());

        // Create the Account
        AccountDTO accountDTO = accountMapper.toDto(account);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAccountMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(accountDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Account in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamAccount() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        account.setAccountId(UUID.randomUUID());

        // Create the Account
        AccountDTO accountDTO = accountMapper.toDto(account);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAccountMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(accountDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Account in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteAccount() throws Exception {
        // Initialize the database
        insertedAccount = accountRepository.saveAndFlush(account);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the account
        restAccountMockMvc
            .perform(delete(ENTITY_API_URL_ID, account.getAccountId().toString()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return accountRepository.count();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertDecrementedRepositoryCount(long countBefore) {
        assertThat(countBefore - 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected Account getPersistedAccount(Account account) {
        return accountRepository.findById(account.getAccountId()).orElseThrow();
    }

    protected void assertPersistedAccountToMatchAllProperties(Account expectedAccount) {
        assertAccountAllPropertiesEquals(expectedAccount, getPersistedAccount(expectedAccount));
    }

    protected void assertPersistedAccountToMatchUpdatableProperties(Account expectedAccount) {
        assertAccountAllUpdatablePropertiesEquals(expectedAccount, getPersistedAccount(expectedAccount));
    }
}
