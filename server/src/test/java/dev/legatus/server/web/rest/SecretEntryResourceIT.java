package dev.legatus.server.web.rest;

import static dev.legatus.server.domain.SecretEntryAsserts.*;
import static dev.legatus.server.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.SecretEntry;
import dev.legatus.server.domain.enumeration.SecretScope;
import dev.legatus.server.repository.SecretEntryRepository;
import dev.legatus.server.service.dto.SecretEntryDTO;
import dev.legatus.server.service.mapper.SecretEntryMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Base64;
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
 * Integration tests for the {@link SecretEntryResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class SecretEntryResourceIT {

    private static final SecretScope DEFAULT_SCOPE = SecretScope.GLOBAL;
    private static final SecretScope UPDATED_SCOPE = SecretScope.CLUSTER;

    private static final byte[] DEFAULT_CIPHER_TEXT = TestUtil.createByteArray(1, "0");
    private static final byte[] UPDATED_CIPHER_TEXT = TestUtil.createByteArray(1, "1");
    private static final String DEFAULT_CIPHER_TEXT_CONTENT_TYPE = "image/jpg";
    private static final String UPDATED_CIPHER_TEXT_CONTENT_TYPE = "image/png";

    private static final byte[] DEFAULT_GCM_IV = TestUtil.createByteArray(1, "0");
    private static final byte[] UPDATED_GCM_IV = TestUtil.createByteArray(1, "1");
    private static final String DEFAULT_GCM_IV_CONTENT_TYPE = "image/jpg";
    private static final String UPDATED_GCM_IV_CONTENT_TYPE = "image/png";

    private static final byte[] DEFAULT_AUTH_TAG = TestUtil.createByteArray(1, "0");
    private static final byte[] UPDATED_AUTH_TAG = TestUtil.createByteArray(1, "1");
    private static final String DEFAULT_AUTH_TAG_CONTENT_TYPE = "image/jpg";
    private static final String UPDATED_AUTH_TAG_CONTENT_TYPE = "image/png";

    private static final Instant DEFAULT_UPDATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_UPDATED_AT = Instant.ofEpochMilli(1703883753520L);

    private static final String ENTITY_API_URL = "/api/secret-entries";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{secretKey}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private SecretEntryRepository secretEntryRepository;

    @Autowired
    private SecretEntryMapper secretEntryMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restSecretEntryMockMvc;

    private SecretEntry secretEntry;

    private SecretEntry insertedSecretEntry;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static SecretEntry createEntity() {
        return new SecretEntry()
            .secretKey(UUID.randomUUID().toString())
            .scope(DEFAULT_SCOPE)
            .cipherText(DEFAULT_CIPHER_TEXT)
            .cipherTextContentType(DEFAULT_CIPHER_TEXT_CONTENT_TYPE)
            .gcmIv(DEFAULT_GCM_IV)
            .gcmIvContentType(DEFAULT_GCM_IV_CONTENT_TYPE)
            .authTag(DEFAULT_AUTH_TAG)
            .authTagContentType(DEFAULT_AUTH_TAG_CONTENT_TYPE)
            .updatedAt(DEFAULT_UPDATED_AT);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static SecretEntry createUpdatedEntity() {
        return new SecretEntry()
            .secretKey(UUID.randomUUID().toString())
            .scope(UPDATED_SCOPE)
            .cipherText(UPDATED_CIPHER_TEXT)
            .cipherTextContentType(UPDATED_CIPHER_TEXT_CONTENT_TYPE)
            .gcmIv(UPDATED_GCM_IV)
            .gcmIvContentType(UPDATED_GCM_IV_CONTENT_TYPE)
            .authTag(UPDATED_AUTH_TAG)
            .authTagContentType(UPDATED_AUTH_TAG_CONTENT_TYPE)
            .updatedAt(UPDATED_UPDATED_AT);
    }

    @BeforeEach
    void initTest() {
        secretEntry = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedSecretEntry != null) {
            secretEntryRepository.delete(insertedSecretEntry);
            insertedSecretEntry = null;
        }
    }

    @Test
    @Transactional
    void createSecretEntry() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the SecretEntry
        SecretEntryDTO secretEntryDTO = secretEntryMapper.toDto(secretEntry);
        var returnedSecretEntryDTO = om.readValue(
            restSecretEntryMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(secretEntryDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            SecretEntryDTO.class
        );

        // Validate the SecretEntry in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedSecretEntry = secretEntryMapper.toEntity(returnedSecretEntryDTO);
        assertSecretEntryUpdatableFieldsEquals(returnedSecretEntry, getPersistedSecretEntry(returnedSecretEntry));

        insertedSecretEntry = returnedSecretEntry;
    }

    @Test
    @Transactional
    void createSecretEntryWithExistingId() throws Exception {
        // Create the SecretEntry with an existing ID
        insertedSecretEntry = secretEntryRepository.saveAndFlush(secretEntry);
        SecretEntryDTO secretEntryDTO = secretEntryMapper.toDto(secretEntry);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restSecretEntryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(secretEntryDTO)))
            .andExpect(status().isBadRequest());

        // Validate the SecretEntry in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkScopeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        secretEntry.setScope(null);

        // Create the SecretEntry, which fails.
        SecretEntryDTO secretEntryDTO = secretEntryMapper.toDto(secretEntry);

        restSecretEntryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(secretEntryDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkUpdatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        secretEntry.setUpdatedAt(null);

        // Create the SecretEntry, which fails.
        SecretEntryDTO secretEntryDTO = secretEntryMapper.toDto(secretEntry);

        restSecretEntryMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(secretEntryDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllSecretEntries() throws Exception {
        // Initialize the database
        secretEntry.setSecretKey(UUID.randomUUID().toString());
        insertedSecretEntry = secretEntryRepository.saveAndFlush(secretEntry);

        // Get all the secretEntryList
        restSecretEntryMockMvc
            .perform(get(ENTITY_API_URL + "?sort=secretKey,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].secretKey").value(hasItem(secretEntry.getSecretKey())))
            .andExpect(jsonPath("$.[*].scope").value(hasItem(DEFAULT_SCOPE.toString())))
            .andExpect(jsonPath("$.[*].cipherTextContentType").value(hasItem(DEFAULT_CIPHER_TEXT_CONTENT_TYPE)))
            .andExpect(jsonPath("$.[*].cipherText").value(hasItem(Base64.getEncoder().encodeToString(DEFAULT_CIPHER_TEXT))))
            .andExpect(jsonPath("$.[*].gcmIvContentType").value(hasItem(DEFAULT_GCM_IV_CONTENT_TYPE)))
            .andExpect(jsonPath("$.[*].gcmIv").value(hasItem(Base64.getEncoder().encodeToString(DEFAULT_GCM_IV))))
            .andExpect(jsonPath("$.[*].authTagContentType").value(hasItem(DEFAULT_AUTH_TAG_CONTENT_TYPE)))
            .andExpect(jsonPath("$.[*].authTag").value(hasItem(Base64.getEncoder().encodeToString(DEFAULT_AUTH_TAG))))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));
    }

    @Test
    @Transactional
    void getSecretEntry() throws Exception {
        // Initialize the database
        secretEntry.setSecretKey(UUID.randomUUID().toString());
        insertedSecretEntry = secretEntryRepository.saveAndFlush(secretEntry);

        // Get the secretEntry
        restSecretEntryMockMvc
            .perform(get(ENTITY_API_URL_ID, secretEntry.getSecretKey()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.secretKey").value(secretEntry.getSecretKey()))
            .andExpect(jsonPath("$.scope").value(DEFAULT_SCOPE.toString()))
            .andExpect(jsonPath("$.cipherTextContentType").value(DEFAULT_CIPHER_TEXT_CONTENT_TYPE))
            .andExpect(jsonPath("$.cipherText").value(Base64.getEncoder().encodeToString(DEFAULT_CIPHER_TEXT)))
            .andExpect(jsonPath("$.gcmIvContentType").value(DEFAULT_GCM_IV_CONTENT_TYPE))
            .andExpect(jsonPath("$.gcmIv").value(Base64.getEncoder().encodeToString(DEFAULT_GCM_IV)))
            .andExpect(jsonPath("$.authTagContentType").value(DEFAULT_AUTH_TAG_CONTENT_TYPE))
            .andExpect(jsonPath("$.authTag").value(Base64.getEncoder().encodeToString(DEFAULT_AUTH_TAG)))
            .andExpect(jsonPath("$.updatedAt").value(DEFAULT_UPDATED_AT.toString()));
    }

    @Test
    @Transactional
    void getNonExistingSecretEntry() throws Exception {
        // Get the secretEntry
        restSecretEntryMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingSecretEntry() throws Exception {
        // Initialize the database
        secretEntry.setSecretKey(UUID.randomUUID().toString());
        insertedSecretEntry = secretEntryRepository.saveAndFlush(secretEntry);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the secretEntry
        SecretEntry updatedSecretEntry = secretEntryRepository.findById(secretEntry.getSecretKey()).orElseThrow();
        // Disconnect from session so that the updates on updatedSecretEntry are not directly saved in db
        em.detach(updatedSecretEntry);
        updatedSecretEntry
            .scope(UPDATED_SCOPE)
            .cipherText(UPDATED_CIPHER_TEXT)
            .cipherTextContentType(UPDATED_CIPHER_TEXT_CONTENT_TYPE)
            .gcmIv(UPDATED_GCM_IV)
            .gcmIvContentType(UPDATED_GCM_IV_CONTENT_TYPE)
            .authTag(UPDATED_AUTH_TAG)
            .authTagContentType(UPDATED_AUTH_TAG_CONTENT_TYPE)
            .updatedAt(UPDATED_UPDATED_AT);
        SecretEntryDTO secretEntryDTO = secretEntryMapper.toDto(updatedSecretEntry);

        restSecretEntryMockMvc
            .perform(
                put(ENTITY_API_URL_ID, secretEntryDTO.getSecretKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(secretEntryDTO))
            )
            .andExpect(status().isOk());

        // Validate the SecretEntry in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedSecretEntryToMatchAllProperties(updatedSecretEntry);
    }

    @Test
    @Transactional
    void putNonExistingSecretEntry() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        secretEntry.setSecretKey(UUID.randomUUID().toString());

        // Create the SecretEntry
        SecretEntryDTO secretEntryDTO = secretEntryMapper.toDto(secretEntry);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restSecretEntryMockMvc
            .perform(
                put(ENTITY_API_URL_ID, secretEntryDTO.getSecretKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(secretEntryDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the SecretEntry in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchSecretEntry() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        secretEntry.setSecretKey(UUID.randomUUID().toString());

        // Create the SecretEntry
        SecretEntryDTO secretEntryDTO = secretEntryMapper.toDto(secretEntry);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSecretEntryMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(secretEntryDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the SecretEntry in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamSecretEntry() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        secretEntry.setSecretKey(UUID.randomUUID().toString());

        // Create the SecretEntry
        SecretEntryDTO secretEntryDTO = secretEntryMapper.toDto(secretEntry);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSecretEntryMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(secretEntryDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the SecretEntry in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateSecretEntryWithPatch() throws Exception {
        // Initialize the database
        secretEntry.setSecretKey(UUID.randomUUID().toString());
        insertedSecretEntry = secretEntryRepository.saveAndFlush(secretEntry);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the secretEntry using partial update
        SecretEntry partialUpdatedSecretEntry = new SecretEntry();
        partialUpdatedSecretEntry.setSecretKey(secretEntry.getSecretKey());

        partialUpdatedSecretEntry
            .gcmIv(UPDATED_GCM_IV)
            .gcmIvContentType(UPDATED_GCM_IV_CONTENT_TYPE)
            .authTag(UPDATED_AUTH_TAG)
            .authTagContentType(UPDATED_AUTH_TAG_CONTENT_TYPE)
            .updatedAt(UPDATED_UPDATED_AT);

        restSecretEntryMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedSecretEntry.getSecretKey())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedSecretEntry))
            )
            .andExpect(status().isOk());

        // Validate the SecretEntry in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertSecretEntryUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedSecretEntry, secretEntry),
            getPersistedSecretEntry(secretEntry)
        );
    }

    @Test
    @Transactional
    void fullUpdateSecretEntryWithPatch() throws Exception {
        // Initialize the database
        secretEntry.setSecretKey(UUID.randomUUID().toString());
        insertedSecretEntry = secretEntryRepository.saveAndFlush(secretEntry);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the secretEntry using partial update
        SecretEntry partialUpdatedSecretEntry = new SecretEntry();
        partialUpdatedSecretEntry.setSecretKey(secretEntry.getSecretKey());

        partialUpdatedSecretEntry
            .scope(UPDATED_SCOPE)
            .cipherText(UPDATED_CIPHER_TEXT)
            .cipherTextContentType(UPDATED_CIPHER_TEXT_CONTENT_TYPE)
            .gcmIv(UPDATED_GCM_IV)
            .gcmIvContentType(UPDATED_GCM_IV_CONTENT_TYPE)
            .authTag(UPDATED_AUTH_TAG)
            .authTagContentType(UPDATED_AUTH_TAG_CONTENT_TYPE)
            .updatedAt(UPDATED_UPDATED_AT);

        restSecretEntryMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedSecretEntry.getSecretKey())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedSecretEntry))
            )
            .andExpect(status().isOk());

        // Validate the SecretEntry in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertSecretEntryUpdatableFieldsEquals(partialUpdatedSecretEntry, getPersistedSecretEntry(partialUpdatedSecretEntry));
    }

    @Test
    @Transactional
    void patchNonExistingSecretEntry() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        secretEntry.setSecretKey(UUID.randomUUID().toString());

        // Create the SecretEntry
        SecretEntryDTO secretEntryDTO = secretEntryMapper.toDto(secretEntry);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restSecretEntryMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, secretEntryDTO.getSecretKey())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(secretEntryDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the SecretEntry in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchSecretEntry() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        secretEntry.setSecretKey(UUID.randomUUID().toString());

        // Create the SecretEntry
        SecretEntryDTO secretEntryDTO = secretEntryMapper.toDto(secretEntry);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSecretEntryMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(secretEntryDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the SecretEntry in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamSecretEntry() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        secretEntry.setSecretKey(UUID.randomUUID().toString());

        // Create the SecretEntry
        SecretEntryDTO secretEntryDTO = secretEntryMapper.toDto(secretEntry);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSecretEntryMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(secretEntryDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the SecretEntry in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteSecretEntry() throws Exception {
        // Initialize the database
        secretEntry.setSecretKey(UUID.randomUUID().toString());
        insertedSecretEntry = secretEntryRepository.saveAndFlush(secretEntry);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the secretEntry
        restSecretEntryMockMvc
            .perform(delete(ENTITY_API_URL_ID, secretEntry.getSecretKey()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return secretEntryRepository.count();
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

    protected SecretEntry getPersistedSecretEntry(SecretEntry secretEntry) {
        return secretEntryRepository.findById(secretEntry.getSecretKey()).orElseThrow();
    }

    protected void assertPersistedSecretEntryToMatchAllProperties(SecretEntry expectedSecretEntry) {
        assertSecretEntryAllPropertiesEquals(expectedSecretEntry, getPersistedSecretEntry(expectedSecretEntry));
    }

    protected void assertPersistedSecretEntryToMatchUpdatableProperties(SecretEntry expectedSecretEntry) {
        assertSecretEntryAllUpdatablePropertiesEquals(expectedSecretEntry, getPersistedSecretEntry(expectedSecretEntry));
    }
}
