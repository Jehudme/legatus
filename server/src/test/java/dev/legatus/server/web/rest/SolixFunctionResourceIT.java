package dev.legatus.server.web.rest;

import static dev.legatus.server.domain.SolixFunctionAsserts.*;
import static dev.legatus.server.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.SolixFunction;
import dev.legatus.server.repository.SolixFunctionRepository;
import dev.legatus.server.service.dto.SolixFunctionDTO;
import dev.legatus.server.service.mapper.SolixFunctionMapper;
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
 * Integration tests for the {@link SolixFunctionResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class SolixFunctionResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_SEMVER = "AAAAAAAAAA";
    private static final String UPDATED_SEMVER = "BBBBBBBBBB";

    private static final String DEFAULT_ENTRY_POINT = "AAAAAAAAAA";
    private static final String UPDATED_ENTRY_POINT = "BBBBBBBBBB";

    private static final String DEFAULT_BYTECODE_FORMAT = "AAAAAAAAAA";
    private static final String UPDATED_BYTECODE_FORMAT = "BBBBBBBBBB";

    private static final String DEFAULT_BYTECODE_SHA_256 = "AAAAAAAAAA";
    private static final String UPDATED_BYTECODE_SHA_256 = "BBBBBBBBBB";

    private static final byte[] DEFAULT_BYTECODE_BLOB = TestUtil.createByteArray(1, "0");
    private static final byte[] UPDATED_BYTECODE_BLOB = TestUtil.createByteArray(1, "1");
    private static final String DEFAULT_BYTECODE_BLOB_CONTENT_TYPE = "image/jpg";
    private static final String UPDATED_BYTECODE_BLOB_CONTENT_TYPE = "image/png";

    private static final Long DEFAULT_DEFAULT_BUDGET = 1000L;
    private static final Long UPDATED_DEFAULT_BUDGET = 1001L;

    private static final Long DEFAULT_DEFAULT_MEMORY_BYTES = 8388608L;
    private static final Long UPDATED_DEFAULT_MEMORY_BYTES = 8388609L;

    private static final Integer DEFAULT_DEFAULT_TIMEOUT_MS = 100;
    private static final Integer UPDATED_DEFAULT_TIMEOUT_MS = 101;

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.ofEpochMilli(1703883753520L);

    private static final String ENTITY_API_URL = "/api/solix-functions";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{functionId}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private SolixFunctionRepository solixFunctionRepository;

    @Autowired
    private SolixFunctionMapper solixFunctionMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restSolixFunctionMockMvc;

    private SolixFunction solixFunction;

    private SolixFunction insertedSolixFunction;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static SolixFunction createEntity() {
        return new SolixFunction()
            .name(DEFAULT_NAME)
            .semver(DEFAULT_SEMVER)
            .entryPoint(DEFAULT_ENTRY_POINT)
            .bytecodeFormat(DEFAULT_BYTECODE_FORMAT)
            .bytecodeSha256(DEFAULT_BYTECODE_SHA_256)
            .bytecodeBlob(DEFAULT_BYTECODE_BLOB)
            .bytecodeBlobContentType(DEFAULT_BYTECODE_BLOB_CONTENT_TYPE)
            .defaultBudget(DEFAULT_DEFAULT_BUDGET)
            .defaultMemoryBytes(DEFAULT_DEFAULT_MEMORY_BYTES)
            .defaultTimeoutMs(DEFAULT_DEFAULT_TIMEOUT_MS)
            .createdAt(DEFAULT_CREATED_AT);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static SolixFunction createUpdatedEntity() {
        return new SolixFunction()
            .name(UPDATED_NAME)
            .semver(UPDATED_SEMVER)
            .entryPoint(UPDATED_ENTRY_POINT)
            .bytecodeFormat(UPDATED_BYTECODE_FORMAT)
            .bytecodeSha256(UPDATED_BYTECODE_SHA_256)
            .bytecodeBlob(UPDATED_BYTECODE_BLOB)
            .bytecodeBlobContentType(UPDATED_BYTECODE_BLOB_CONTENT_TYPE)
            .defaultBudget(UPDATED_DEFAULT_BUDGET)
            .defaultMemoryBytes(UPDATED_DEFAULT_MEMORY_BYTES)
            .defaultTimeoutMs(UPDATED_DEFAULT_TIMEOUT_MS)
            .createdAt(UPDATED_CREATED_AT);
    }

    @BeforeEach
    void initTest() {
        solixFunction = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedSolixFunction != null) {
            solixFunctionRepository.delete(insertedSolixFunction);
            insertedSolixFunction = null;
        }
    }

    @Test
    @Transactional
    void createSolixFunction() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the SolixFunction
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);
        var returnedSolixFunctionDTO = om.readValue(
            restSolixFunctionMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(solixFunctionDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            SolixFunctionDTO.class
        );

        // Validate the SolixFunction in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedSolixFunction = solixFunctionMapper.toEntity(returnedSolixFunctionDTO);
        assertSolixFunctionUpdatableFieldsEquals(returnedSolixFunction, getPersistedSolixFunction(returnedSolixFunction));

        insertedSolixFunction = returnedSolixFunction;
    }

    @Test
    @Transactional
    void createSolixFunctionWithExistingId() throws Exception {
        // Create the SolixFunction with an existing ID
        insertedSolixFunction = solixFunctionRepository.saveAndFlush(solixFunction);
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restSolixFunctionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(solixFunctionDTO)))
            .andExpect(status().isBadRequest());

        // Validate the SolixFunction in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        solixFunction.setName(null);

        // Create the SolixFunction, which fails.
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);

        restSolixFunctionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(solixFunctionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkSemverIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        solixFunction.setSemver(null);

        // Create the SolixFunction, which fails.
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);

        restSolixFunctionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(solixFunctionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkEntryPointIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        solixFunction.setEntryPoint(null);

        // Create the SolixFunction, which fails.
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);

        restSolixFunctionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(solixFunctionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkBytecodeFormatIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        solixFunction.setBytecodeFormat(null);

        // Create the SolixFunction, which fails.
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);

        restSolixFunctionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(solixFunctionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkBytecodeSha256IsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        solixFunction.setBytecodeSha256(null);

        // Create the SolixFunction, which fails.
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);

        restSolixFunctionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(solixFunctionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkDefaultBudgetIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        solixFunction.setDefaultBudget(null);

        // Create the SolixFunction, which fails.
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);

        restSolixFunctionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(solixFunctionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkDefaultMemoryBytesIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        solixFunction.setDefaultMemoryBytes(null);

        // Create the SolixFunction, which fails.
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);

        restSolixFunctionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(solixFunctionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkDefaultTimeoutMsIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        solixFunction.setDefaultTimeoutMs(null);

        // Create the SolixFunction, which fails.
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);

        restSolixFunctionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(solixFunctionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        solixFunction.setCreatedAt(null);

        // Create the SolixFunction, which fails.
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);

        restSolixFunctionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(solixFunctionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllSolixFunctions() throws Exception {
        // Initialize the database
        insertedSolixFunction = solixFunctionRepository.saveAndFlush(solixFunction);

        // Get all the solixFunctionList
        restSolixFunctionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=functionId,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].functionId").value(hasItem(solixFunction.getFunctionId().toString())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].semver").value(hasItem(DEFAULT_SEMVER)))
            .andExpect(jsonPath("$.[*].entryPoint").value(hasItem(DEFAULT_ENTRY_POINT)))
            .andExpect(jsonPath("$.[*].bytecodeFormat").value(hasItem(DEFAULT_BYTECODE_FORMAT)))
            .andExpect(jsonPath("$.[*].bytecodeSha256").value(hasItem(DEFAULT_BYTECODE_SHA_256)))
            .andExpect(jsonPath("$.[*].bytecodeBlobContentType").value(hasItem(DEFAULT_BYTECODE_BLOB_CONTENT_TYPE)))
            .andExpect(jsonPath("$.[*].bytecodeBlob").value(hasItem(Base64.getEncoder().encodeToString(DEFAULT_BYTECODE_BLOB))))
            .andExpect(jsonPath("$.[*].defaultBudget").value(hasItem(DEFAULT_DEFAULT_BUDGET.intValue())))
            .andExpect(jsonPath("$.[*].defaultMemoryBytes").value(hasItem(DEFAULT_DEFAULT_MEMORY_BYTES.intValue())))
            .andExpect(jsonPath("$.[*].defaultTimeoutMs").value(hasItem(DEFAULT_DEFAULT_TIMEOUT_MS)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())));
    }

    @Test
    @Transactional
    void getSolixFunction() throws Exception {
        // Initialize the database
        insertedSolixFunction = solixFunctionRepository.saveAndFlush(solixFunction);

        // Get the solixFunction
        restSolixFunctionMockMvc
            .perform(get(ENTITY_API_URL_ID, solixFunction.getFunctionId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.functionId").value(solixFunction.getFunctionId().toString()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.semver").value(DEFAULT_SEMVER))
            .andExpect(jsonPath("$.entryPoint").value(DEFAULT_ENTRY_POINT))
            .andExpect(jsonPath("$.bytecodeFormat").value(DEFAULT_BYTECODE_FORMAT))
            .andExpect(jsonPath("$.bytecodeSha256").value(DEFAULT_BYTECODE_SHA_256))
            .andExpect(jsonPath("$.bytecodeBlobContentType").value(DEFAULT_BYTECODE_BLOB_CONTENT_TYPE))
            .andExpect(jsonPath("$.bytecodeBlob").value(Base64.getEncoder().encodeToString(DEFAULT_BYTECODE_BLOB)))
            .andExpect(jsonPath("$.defaultBudget").value(DEFAULT_DEFAULT_BUDGET.intValue()))
            .andExpect(jsonPath("$.defaultMemoryBytes").value(DEFAULT_DEFAULT_MEMORY_BYTES.intValue()))
            .andExpect(jsonPath("$.defaultTimeoutMs").value(DEFAULT_DEFAULT_TIMEOUT_MS))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()));
    }

    @Test
    @Transactional
    void getNonExistingSolixFunction() throws Exception {
        // Get the solixFunction
        restSolixFunctionMockMvc.perform(get(ENTITY_API_URL_ID, UUID.randomUUID().toString())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingSolixFunction() throws Exception {
        // Initialize the database
        insertedSolixFunction = solixFunctionRepository.saveAndFlush(solixFunction);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the solixFunction
        SolixFunction updatedSolixFunction = solixFunctionRepository.findById(solixFunction.getFunctionId()).orElseThrow();
        // Disconnect from session so that the updates on updatedSolixFunction are not directly saved in db
        em.detach(updatedSolixFunction);
        updatedSolixFunction
            .name(UPDATED_NAME)
            .semver(UPDATED_SEMVER)
            .entryPoint(UPDATED_ENTRY_POINT)
            .bytecodeFormat(UPDATED_BYTECODE_FORMAT)
            .bytecodeSha256(UPDATED_BYTECODE_SHA_256)
            .bytecodeBlob(UPDATED_BYTECODE_BLOB)
            .bytecodeBlobContentType(UPDATED_BYTECODE_BLOB_CONTENT_TYPE)
            .defaultBudget(UPDATED_DEFAULT_BUDGET)
            .defaultMemoryBytes(UPDATED_DEFAULT_MEMORY_BYTES)
            .defaultTimeoutMs(UPDATED_DEFAULT_TIMEOUT_MS)
            .createdAt(UPDATED_CREATED_AT);
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(updatedSolixFunction);

        restSolixFunctionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, solixFunctionDTO.getFunctionId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(solixFunctionDTO))
            )
            .andExpect(status().isOk());

        // Validate the SolixFunction in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedSolixFunctionToMatchAllProperties(updatedSolixFunction);
    }

    @Test
    @Transactional
    void putNonExistingSolixFunction() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        solixFunction.setFunctionId(UUID.randomUUID());

        // Create the SolixFunction
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restSolixFunctionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, solixFunctionDTO.getFunctionId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(solixFunctionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the SolixFunction in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchSolixFunction() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        solixFunction.setFunctionId(UUID.randomUUID());

        // Create the SolixFunction
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSolixFunctionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(solixFunctionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the SolixFunction in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamSolixFunction() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        solixFunction.setFunctionId(UUID.randomUUID());

        // Create the SolixFunction
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSolixFunctionMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(solixFunctionDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the SolixFunction in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateSolixFunctionWithPatch() throws Exception {
        // Initialize the database
        insertedSolixFunction = solixFunctionRepository.saveAndFlush(solixFunction);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the solixFunction using partial update
        SolixFunction partialUpdatedSolixFunction = new SolixFunction();
        partialUpdatedSolixFunction.setFunctionId(solixFunction.getFunctionId());

        partialUpdatedSolixFunction
            .name(UPDATED_NAME)
            .bytecodeSha256(UPDATED_BYTECODE_SHA_256)
            .bytecodeBlob(UPDATED_BYTECODE_BLOB)
            .bytecodeBlobContentType(UPDATED_BYTECODE_BLOB_CONTENT_TYPE)
            .defaultTimeoutMs(UPDATED_DEFAULT_TIMEOUT_MS);

        restSolixFunctionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedSolixFunction.getFunctionId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedSolixFunction))
            )
            .andExpect(status().isOk());

        // Validate the SolixFunction in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertSolixFunctionUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedSolixFunction, solixFunction),
            getPersistedSolixFunction(solixFunction)
        );
    }

    @Test
    @Transactional
    void fullUpdateSolixFunctionWithPatch() throws Exception {
        // Initialize the database
        insertedSolixFunction = solixFunctionRepository.saveAndFlush(solixFunction);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the solixFunction using partial update
        SolixFunction partialUpdatedSolixFunction = new SolixFunction();
        partialUpdatedSolixFunction.setFunctionId(solixFunction.getFunctionId());

        partialUpdatedSolixFunction
            .name(UPDATED_NAME)
            .semver(UPDATED_SEMVER)
            .entryPoint(UPDATED_ENTRY_POINT)
            .bytecodeFormat(UPDATED_BYTECODE_FORMAT)
            .bytecodeSha256(UPDATED_BYTECODE_SHA_256)
            .bytecodeBlob(UPDATED_BYTECODE_BLOB)
            .bytecodeBlobContentType(UPDATED_BYTECODE_BLOB_CONTENT_TYPE)
            .defaultBudget(UPDATED_DEFAULT_BUDGET)
            .defaultMemoryBytes(UPDATED_DEFAULT_MEMORY_BYTES)
            .defaultTimeoutMs(UPDATED_DEFAULT_TIMEOUT_MS)
            .createdAt(UPDATED_CREATED_AT);

        restSolixFunctionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedSolixFunction.getFunctionId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedSolixFunction))
            )
            .andExpect(status().isOk());

        // Validate the SolixFunction in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertSolixFunctionUpdatableFieldsEquals(partialUpdatedSolixFunction, getPersistedSolixFunction(partialUpdatedSolixFunction));
    }

    @Test
    @Transactional
    void patchNonExistingSolixFunction() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        solixFunction.setFunctionId(UUID.randomUUID());

        // Create the SolixFunction
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restSolixFunctionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, solixFunctionDTO.getFunctionId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(solixFunctionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the SolixFunction in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchSolixFunction() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        solixFunction.setFunctionId(UUID.randomUUID());

        // Create the SolixFunction
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSolixFunctionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(solixFunctionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the SolixFunction in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamSolixFunction() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        solixFunction.setFunctionId(UUID.randomUUID());

        // Create the SolixFunction
        SolixFunctionDTO solixFunctionDTO = solixFunctionMapper.toDto(solixFunction);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSolixFunctionMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(solixFunctionDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the SolixFunction in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteSolixFunction() throws Exception {
        // Initialize the database
        insertedSolixFunction = solixFunctionRepository.saveAndFlush(solixFunction);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the solixFunction
        restSolixFunctionMockMvc
            .perform(delete(ENTITY_API_URL_ID, solixFunction.getFunctionId().toString()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return solixFunctionRepository.count();
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

    protected SolixFunction getPersistedSolixFunction(SolixFunction solixFunction) {
        return solixFunctionRepository.findById(solixFunction.getFunctionId()).orElseThrow();
    }

    protected void assertPersistedSolixFunctionToMatchAllProperties(SolixFunction expectedSolixFunction) {
        assertSolixFunctionAllPropertiesEquals(expectedSolixFunction, getPersistedSolixFunction(expectedSolixFunction));
    }

    protected void assertPersistedSolixFunctionToMatchUpdatableProperties(SolixFunction expectedSolixFunction) {
        assertSolixFunctionAllUpdatablePropertiesEquals(expectedSolixFunction, getPersistedSolixFunction(expectedSolixFunction));
    }
}
