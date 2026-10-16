package dev.legatus.server.web.rest;

import static dev.legatus.server.domain.ComputeTaskAsserts.*;
import static dev.legatus.server.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.SolixFunction;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.service.ComputeTaskService;
import dev.legatus.server.service.dto.ComputeTaskDTO;
import dev.legatus.server.service.mapper.ComputeTaskMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration tests for the {@link ComputeTaskResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class ComputeTaskResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final TaskStatus DEFAULT_STATUS = TaskStatus.QUEUED;
    private static final TaskStatus UPDATED_STATUS = TaskStatus.DISPATCHED;

    private static final TaskPriority DEFAULT_PRIORITY = TaskPriority.LOW;
    private static final TaskPriority UPDATED_PRIORITY = TaskPriority.NORMAL;

    private static final Integer DEFAULT_PRIORITY_WEIGHT = 0;
    private static final Integer UPDATED_PRIORITY_WEIGHT = 1;
    private static final Integer SMALLER_PRIORITY_WEIGHT = 0 - 1;

    private static final Boolean DEFAULT_IDEMPOTENT = false;
    private static final Boolean UPDATED_IDEMPOTENT = true;

    private static final String DEFAULT_ENTRY_POINT = "AAAAAAAAAA";
    private static final String UPDATED_ENTRY_POINT = "BBBBBBBBBB";

    private static final String DEFAULT_BYTECODE_SHA_256 = "AAAAAAAAAA";
    private static final String UPDATED_BYTECODE_SHA_256 = "BBBBBBBBBB";

    private static final Long DEFAULT_INSTRUCTION_BUDGET = 1000L;
    private static final Long UPDATED_INSTRUCTION_BUDGET = 1001L;
    private static final Long SMALLER_INSTRUCTION_BUDGET = 1000L - 1L;

    private static final Long DEFAULT_MAX_MEMORY_BYTES = 8388608L;
    private static final Long UPDATED_MAX_MEMORY_BYTES = 8388609L;
    private static final Long SMALLER_MAX_MEMORY_BYTES = 8388608L - 1L;

    private static final Integer DEFAULT_TIMEOUT_MS = 100;
    private static final Integer UPDATED_TIMEOUT_MS = 101;
    private static final Integer SMALLER_TIMEOUT_MS = 100 - 1;

    private static final Integer DEFAULT_EXIT_CODE = 1;
    private static final Integer UPDATED_EXIT_CODE = 2;
    private static final Integer SMALLER_EXIT_CODE = 1 - 1;

    private static final Long DEFAULT_INSTRUCTIONS_CONSUMED = 0L;
    private static final Long UPDATED_INSTRUCTIONS_CONSUMED = 1L;
    private static final Long SMALLER_INSTRUCTIONS_CONSUMED = 0L - 1L;

    private static final Long DEFAULT_PEAK_MEMORY_BYTES = 0L;
    private static final Long UPDATED_PEAK_MEMORY_BYTES = 1L;
    private static final Long SMALLER_PEAK_MEMORY_BYTES = 0L - 1L;

    private static final Long DEFAULT_WALL_TIME_MS = 0L;
    private static final Long UPDATED_WALL_TIME_MS = 1L;
    private static final Long SMALLER_WALL_TIME_MS = 0L - 1L;

    private static final String DEFAULT_INPUT_JSON = "AAAAAAAAAA";
    private static final String UPDATED_INPUT_JSON = "BBBBBBBBBB";

    private static final String DEFAULT_ENV_JSON = "AAAAAAAAAA";
    private static final String UPDATED_ENV_JSON = "BBBBBBBBBB";

    private static final String DEFAULT_SECRET_REFS_JSON = "AAAAAAAAAA";
    private static final String UPDATED_SECRET_REFS_JSON = "BBBBBBBBBB";

    private static final String DEFAULT_OUTPUT_JSON = "AAAAAAAAAA";
    private static final String UPDATED_OUTPUT_JSON = "BBBBBBBBBB";

    private static final String DEFAULT_ERROR_JSON = "AAAAAAAAAA";
    private static final String UPDATED_ERROR_JSON = "BBBBBBBBBB";

    private static final Integer DEFAULT_ATTEMPT_COUNT = 0;
    private static final Integer UPDATED_ATTEMPT_COUNT = 1;
    private static final Integer SMALLER_ATTEMPT_COUNT = 0 - 1;

    private static final Integer DEFAULT_MAX_RETRIES = 0;
    private static final Integer UPDATED_MAX_RETRIES = 1;
    private static final Integer SMALLER_MAX_RETRIES = 0 - 1;

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.ofEpochMilli(1703883753520L);

    private static final Instant DEFAULT_STARTED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_STARTED_AT = Instant.ofEpochMilli(1703883753520L);

    private static final Instant DEFAULT_COMPLETED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_COMPLETED_AT = Instant.ofEpochMilli(1703883753520L);

    private static final String ENTITY_API_URL = "/api/compute-tasks";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{taskId}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ComputeTaskRepository computeTaskRepository;

    @Mock
    private ComputeTaskRepository computeTaskRepositoryMock;

    @Autowired
    private ComputeTaskMapper computeTaskMapper;

    @Mock
    private ComputeTaskService computeTaskServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restComputeTaskMockMvc;

    private ComputeTask computeTask;

    private ComputeTask insertedComputeTask;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ComputeTask createEntity() {
        return new ComputeTask()
            .taskId(UUID.randomUUID().toString())
            .name(DEFAULT_NAME)
            .status(DEFAULT_STATUS)
            .priority(DEFAULT_PRIORITY)
            .priorityWeight(DEFAULT_PRIORITY_WEIGHT)
            .idempotent(DEFAULT_IDEMPOTENT)
            .entryPoint(DEFAULT_ENTRY_POINT)
            .bytecodeSha256(DEFAULT_BYTECODE_SHA_256)
            .instructionBudget(DEFAULT_INSTRUCTION_BUDGET)
            .maxMemoryBytes(DEFAULT_MAX_MEMORY_BYTES)
            .timeoutMs(DEFAULT_TIMEOUT_MS)
            .exitCode(DEFAULT_EXIT_CODE)
            .instructionsConsumed(DEFAULT_INSTRUCTIONS_CONSUMED)
            .peakMemoryBytes(DEFAULT_PEAK_MEMORY_BYTES)
            .wallTimeMs(DEFAULT_WALL_TIME_MS)
            .inputJson(DEFAULT_INPUT_JSON)
            .envJson(DEFAULT_ENV_JSON)
            .secretRefsJson(DEFAULT_SECRET_REFS_JSON)
            .outputJson(DEFAULT_OUTPUT_JSON)
            .errorJson(DEFAULT_ERROR_JSON)
            .attemptCount(DEFAULT_ATTEMPT_COUNT)
            .maxRetries(DEFAULT_MAX_RETRIES)
            .createdAt(DEFAULT_CREATED_AT)
            .startedAt(DEFAULT_STARTED_AT)
            .completedAt(DEFAULT_COMPLETED_AT);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ComputeTask createUpdatedEntity() {
        return new ComputeTask()
            .taskId(UUID.randomUUID().toString())
            .name(UPDATED_NAME)
            .status(UPDATED_STATUS)
            .priority(UPDATED_PRIORITY)
            .priorityWeight(UPDATED_PRIORITY_WEIGHT)
            .idempotent(UPDATED_IDEMPOTENT)
            .entryPoint(UPDATED_ENTRY_POINT)
            .bytecodeSha256(UPDATED_BYTECODE_SHA_256)
            .instructionBudget(UPDATED_INSTRUCTION_BUDGET)
            .maxMemoryBytes(UPDATED_MAX_MEMORY_BYTES)
            .timeoutMs(UPDATED_TIMEOUT_MS)
            .exitCode(UPDATED_EXIT_CODE)
            .instructionsConsumed(UPDATED_INSTRUCTIONS_CONSUMED)
            .peakMemoryBytes(UPDATED_PEAK_MEMORY_BYTES)
            .wallTimeMs(UPDATED_WALL_TIME_MS)
            .inputJson(UPDATED_INPUT_JSON)
            .envJson(UPDATED_ENV_JSON)
            .secretRefsJson(UPDATED_SECRET_REFS_JSON)
            .outputJson(UPDATED_OUTPUT_JSON)
            .errorJson(UPDATED_ERROR_JSON)
            .attemptCount(UPDATED_ATTEMPT_COUNT)
            .maxRetries(UPDATED_MAX_RETRIES)
            .createdAt(UPDATED_CREATED_AT)
            .startedAt(UPDATED_STARTED_AT)
            .completedAt(UPDATED_COMPLETED_AT);
    }

    @BeforeEach
    void initTest() {
        computeTask = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedComputeTask != null) {
            computeTaskRepository.deleteById(insertedComputeTask.getTaskId());
            insertedComputeTask = null;
        }
    }

    @Test
    @Transactional
    void createComputeTask() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the ComputeTask
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);
        var returnedComputeTaskDTO = om.readValue(
            restComputeTaskMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(computeTaskDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ComputeTaskDTO.class
        );

        // Validate the ComputeTask in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedComputeTask = computeTaskMapper.toEntity(returnedComputeTaskDTO);
        assertComputeTaskUpdatableFieldsEquals(returnedComputeTask, getPersistedComputeTask(returnedComputeTask));

        insertedComputeTask = returnedComputeTask;
    }

    @Test
    @Transactional
    void createComputeTaskWithExistingId() throws Exception {
        // Create the ComputeTask with an existing ID
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restComputeTaskMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(computeTaskDTO)))
            .andExpect(status().isBadRequest());

        // Validate the ComputeTask in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        computeTask.setName(null);

        // Create the ComputeTask, which fails.
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        restComputeTaskMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(computeTaskDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkStatusIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        computeTask.setStatus(null);

        // Create the ComputeTask, which fails.
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        restComputeTaskMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(computeTaskDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkPriorityIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        computeTask.setPriority(null);

        // Create the ComputeTask, which fails.
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        restComputeTaskMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(computeTaskDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkPriorityWeightIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        computeTask.setPriorityWeight(null);

        // Create the ComputeTask, which fails.
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        restComputeTaskMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(computeTaskDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkIdempotentIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        computeTask.setIdempotent(null);

        // Create the ComputeTask, which fails.
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        restComputeTaskMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(computeTaskDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkEntryPointIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        computeTask.setEntryPoint(null);

        // Create the ComputeTask, which fails.
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        restComputeTaskMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(computeTaskDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkBytecodeSha256IsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        computeTask.setBytecodeSha256(null);

        // Create the ComputeTask, which fails.
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        restComputeTaskMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(computeTaskDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkInstructionBudgetIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        computeTask.setInstructionBudget(null);

        // Create the ComputeTask, which fails.
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        restComputeTaskMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(computeTaskDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkMaxMemoryBytesIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        computeTask.setMaxMemoryBytes(null);

        // Create the ComputeTask, which fails.
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        restComputeTaskMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(computeTaskDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkTimeoutMsIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        computeTask.setTimeoutMs(null);

        // Create the ComputeTask, which fails.
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        restComputeTaskMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(computeTaskDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkAttemptCountIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        computeTask.setAttemptCount(null);

        // Create the ComputeTask, which fails.
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        restComputeTaskMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(computeTaskDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkMaxRetriesIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        computeTask.setMaxRetries(null);

        // Create the ComputeTask, which fails.
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        restComputeTaskMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(computeTaskDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        computeTask.setCreatedAt(null);

        // Create the ComputeTask, which fails.
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        restComputeTaskMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(computeTaskDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllComputeTasks() throws Exception {
        // Initialize the database
        computeTask.setTaskId(UUID.randomUUID().toString());
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList
        restComputeTaskMockMvc
            .perform(get(ENTITY_API_URL + "?sort=taskId,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].taskId").value(hasItem(computeTask.getTaskId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].priority").value(hasItem(DEFAULT_PRIORITY.toString())))
            .andExpect(jsonPath("$.[*].priorityWeight").value(hasItem(DEFAULT_PRIORITY_WEIGHT)))
            .andExpect(jsonPath("$.[*].idempotent").value(hasItem(DEFAULT_IDEMPOTENT)))
            .andExpect(jsonPath("$.[*].entryPoint").value(hasItem(DEFAULT_ENTRY_POINT)))
            .andExpect(jsonPath("$.[*].bytecodeSha256").value(hasItem(DEFAULT_BYTECODE_SHA_256)))
            .andExpect(jsonPath("$.[*].instructionBudget").value(hasItem(DEFAULT_INSTRUCTION_BUDGET.intValue())))
            .andExpect(jsonPath("$.[*].maxMemoryBytes").value(hasItem(DEFAULT_MAX_MEMORY_BYTES.intValue())))
            .andExpect(jsonPath("$.[*].timeoutMs").value(hasItem(DEFAULT_TIMEOUT_MS)))
            .andExpect(jsonPath("$.[*].exitCode").value(hasItem(DEFAULT_EXIT_CODE)))
            .andExpect(jsonPath("$.[*].instructionsConsumed").value(hasItem(DEFAULT_INSTRUCTIONS_CONSUMED.intValue())))
            .andExpect(jsonPath("$.[*].peakMemoryBytes").value(hasItem(DEFAULT_PEAK_MEMORY_BYTES.intValue())))
            .andExpect(jsonPath("$.[*].wallTimeMs").value(hasItem(DEFAULT_WALL_TIME_MS.intValue())))
            .andExpect(jsonPath("$.[*].inputJson").value(hasItem(DEFAULT_INPUT_JSON)))
            .andExpect(jsonPath("$.[*].envJson").value(hasItem(DEFAULT_ENV_JSON)))
            .andExpect(jsonPath("$.[*].secretRefsJson").value(hasItem(DEFAULT_SECRET_REFS_JSON)))
            .andExpect(jsonPath("$.[*].outputJson").value(hasItem(DEFAULT_OUTPUT_JSON)))
            .andExpect(jsonPath("$.[*].errorJson").value(hasItem(DEFAULT_ERROR_JSON)))
            .andExpect(jsonPath("$.[*].attemptCount").value(hasItem(DEFAULT_ATTEMPT_COUNT)))
            .andExpect(jsonPath("$.[*].maxRetries").value(hasItem(DEFAULT_MAX_RETRIES)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].startedAt").value(hasItem(DEFAULT_STARTED_AT.toString())))
            .andExpect(jsonPath("$.[*].completedAt").value(hasItem(DEFAULT_COMPLETED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllComputeTasksWithEagerRelationshipsIsEnabled() throws Exception {
        when(computeTaskServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restComputeTaskMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(computeTaskServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllComputeTasksWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(computeTaskServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restComputeTaskMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(computeTaskRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getComputeTask() throws Exception {
        // Initialize the database
        computeTask.setTaskId(UUID.randomUUID().toString());
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get the computeTask
        restComputeTaskMockMvc
            .perform(get(ENTITY_API_URL_ID, computeTask.getTaskId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.taskId").value(computeTask.getTaskId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.priority").value(DEFAULT_PRIORITY.toString()))
            .andExpect(jsonPath("$.priorityWeight").value(DEFAULT_PRIORITY_WEIGHT))
            .andExpect(jsonPath("$.idempotent").value(DEFAULT_IDEMPOTENT))
            .andExpect(jsonPath("$.entryPoint").value(DEFAULT_ENTRY_POINT))
            .andExpect(jsonPath("$.bytecodeSha256").value(DEFAULT_BYTECODE_SHA_256))
            .andExpect(jsonPath("$.instructionBudget").value(DEFAULT_INSTRUCTION_BUDGET.intValue()))
            .andExpect(jsonPath("$.maxMemoryBytes").value(DEFAULT_MAX_MEMORY_BYTES.intValue()))
            .andExpect(jsonPath("$.timeoutMs").value(DEFAULT_TIMEOUT_MS))
            .andExpect(jsonPath("$.exitCode").value(DEFAULT_EXIT_CODE))
            .andExpect(jsonPath("$.instructionsConsumed").value(DEFAULT_INSTRUCTIONS_CONSUMED.intValue()))
            .andExpect(jsonPath("$.peakMemoryBytes").value(DEFAULT_PEAK_MEMORY_BYTES.intValue()))
            .andExpect(jsonPath("$.wallTimeMs").value(DEFAULT_WALL_TIME_MS.intValue()))
            .andExpect(jsonPath("$.inputJson").value(DEFAULT_INPUT_JSON))
            .andExpect(jsonPath("$.envJson").value(DEFAULT_ENV_JSON))
            .andExpect(jsonPath("$.secretRefsJson").value(DEFAULT_SECRET_REFS_JSON))
            .andExpect(jsonPath("$.outputJson").value(DEFAULT_OUTPUT_JSON))
            .andExpect(jsonPath("$.errorJson").value(DEFAULT_ERROR_JSON))
            .andExpect(jsonPath("$.attemptCount").value(DEFAULT_ATTEMPT_COUNT))
            .andExpect(jsonPath("$.maxRetries").value(DEFAULT_MAX_RETRIES))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()))
            .andExpect(jsonPath("$.startedAt").value(DEFAULT_STARTED_AT.toString()))
            .andExpect(jsonPath("$.completedAt").value(DEFAULT_COMPLETED_AT.toString()));
    }

    @Test
    @Transactional
    void getComputeTasksByIdFiltering() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        String id = computeTask.getTaskId();

        defaultComputeTaskFiltering("taskId.equals=" + id, "taskId.notEquals=" + id);
    }

    @Test
    @Transactional
    void getAllComputeTasksByNameIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where name equals to
        defaultComputeTaskFiltering("name.equals=" + DEFAULT_NAME, "name.equals=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllComputeTasksByNameIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where name in
        defaultComputeTaskFiltering("name.in=" + DEFAULT_NAME + "," + UPDATED_NAME, "name.in=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllComputeTasksByNameIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where name is not null
        defaultComputeTaskFiltering("name.specified=true", "name.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByNameContainsSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where name contains
        defaultComputeTaskFiltering("name.contains=" + DEFAULT_NAME, "name.contains=" + UPDATED_NAME);
    }

    @Test
    @Transactional
    void getAllComputeTasksByNameNotContainsSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where name does not contain
        defaultComputeTaskFiltering("name.doesNotContain=" + UPDATED_NAME, "name.doesNotContain=" + DEFAULT_NAME);
    }

    @Test
    @Transactional
    void getAllComputeTasksByStatusIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where status equals to
        defaultComputeTaskFiltering("status.equals=" + DEFAULT_STATUS, "status.equals=" + UPDATED_STATUS);
    }

    @Test
    @Transactional
    void getAllComputeTasksByStatusIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where status in
        defaultComputeTaskFiltering("status.in=" + DEFAULT_STATUS + "," + UPDATED_STATUS, "status.in=" + UPDATED_STATUS);
    }

    @Test
    @Transactional
    void getAllComputeTasksByStatusIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where status is not null
        defaultComputeTaskFiltering("status.specified=true", "status.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByPriorityIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where priority equals to
        defaultComputeTaskFiltering("priority.equals=" + DEFAULT_PRIORITY, "priority.equals=" + UPDATED_PRIORITY);
    }

    @Test
    @Transactional
    void getAllComputeTasksByPriorityIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where priority in
        defaultComputeTaskFiltering("priority.in=" + DEFAULT_PRIORITY + "," + UPDATED_PRIORITY, "priority.in=" + UPDATED_PRIORITY);
    }

    @Test
    @Transactional
    void getAllComputeTasksByPriorityIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where priority is not null
        defaultComputeTaskFiltering("priority.specified=true", "priority.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByPriorityWeightIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where priorityWeight equals to
        defaultComputeTaskFiltering("priorityWeight.equals=" + DEFAULT_PRIORITY_WEIGHT, "priorityWeight.equals=" + UPDATED_PRIORITY_WEIGHT);
    }

    @Test
    @Transactional
    void getAllComputeTasksByPriorityWeightIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where priorityWeight in
        defaultComputeTaskFiltering(
            "priorityWeight.in=" + DEFAULT_PRIORITY_WEIGHT + "," + UPDATED_PRIORITY_WEIGHT,
            "priorityWeight.in=" + UPDATED_PRIORITY_WEIGHT
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByPriorityWeightIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where priorityWeight is not null
        defaultComputeTaskFiltering("priorityWeight.specified=true", "priorityWeight.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByPriorityWeightIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where priorityWeight is greater than or equal to
        defaultComputeTaskFiltering(
            "priorityWeight.greaterThanOrEqual=" + DEFAULT_PRIORITY_WEIGHT,
            "priorityWeight.greaterThanOrEqual=" + (DEFAULT_PRIORITY_WEIGHT + 1)
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByPriorityWeightIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where priorityWeight is less than or equal to
        defaultComputeTaskFiltering(
            "priorityWeight.lessThanOrEqual=" + DEFAULT_PRIORITY_WEIGHT,
            "priorityWeight.lessThanOrEqual=" + SMALLER_PRIORITY_WEIGHT
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByPriorityWeightIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where priorityWeight is less than
        defaultComputeTaskFiltering(
            "priorityWeight.lessThan=" + (DEFAULT_PRIORITY_WEIGHT + 1),
            "priorityWeight.lessThan=" + DEFAULT_PRIORITY_WEIGHT
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByPriorityWeightIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where priorityWeight is greater than
        defaultComputeTaskFiltering(
            "priorityWeight.greaterThan=" + SMALLER_PRIORITY_WEIGHT,
            "priorityWeight.greaterThan=" + DEFAULT_PRIORITY_WEIGHT
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByIdempotentIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where idempotent equals to
        defaultComputeTaskFiltering("idempotent.equals=" + DEFAULT_IDEMPOTENT, "idempotent.equals=" + UPDATED_IDEMPOTENT);
    }

    @Test
    @Transactional
    void getAllComputeTasksByIdempotentIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where idempotent in
        defaultComputeTaskFiltering(
            "idempotent.in=" + DEFAULT_IDEMPOTENT + "," + UPDATED_IDEMPOTENT,
            "idempotent.in=" + UPDATED_IDEMPOTENT
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByIdempotentIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where idempotent is not null
        defaultComputeTaskFiltering("idempotent.specified=true", "idempotent.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByEntryPointIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where entryPoint equals to
        defaultComputeTaskFiltering("entryPoint.equals=" + DEFAULT_ENTRY_POINT, "entryPoint.equals=" + UPDATED_ENTRY_POINT);
    }

    @Test
    @Transactional
    void getAllComputeTasksByEntryPointIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where entryPoint in
        defaultComputeTaskFiltering(
            "entryPoint.in=" + DEFAULT_ENTRY_POINT + "," + UPDATED_ENTRY_POINT,
            "entryPoint.in=" + UPDATED_ENTRY_POINT
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByEntryPointIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where entryPoint is not null
        defaultComputeTaskFiltering("entryPoint.specified=true", "entryPoint.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByEntryPointContainsSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where entryPoint contains
        defaultComputeTaskFiltering("entryPoint.contains=" + DEFAULT_ENTRY_POINT, "entryPoint.contains=" + UPDATED_ENTRY_POINT);
    }

    @Test
    @Transactional
    void getAllComputeTasksByEntryPointNotContainsSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where entryPoint does not contain
        defaultComputeTaskFiltering("entryPoint.doesNotContain=" + UPDATED_ENTRY_POINT, "entryPoint.doesNotContain=" + DEFAULT_ENTRY_POINT);
    }

    @Test
    @Transactional
    void getAllComputeTasksByBytecodeSha256IsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where bytecodeSha256 equals to
        defaultComputeTaskFiltering(
            "bytecodeSha256.equals=" + DEFAULT_BYTECODE_SHA_256,
            "bytecodeSha256.equals=" + UPDATED_BYTECODE_SHA_256
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByBytecodeSha256IsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where bytecodeSha256 in
        defaultComputeTaskFiltering(
            "bytecodeSha256.in=" + DEFAULT_BYTECODE_SHA_256 + "," + UPDATED_BYTECODE_SHA_256,
            "bytecodeSha256.in=" + UPDATED_BYTECODE_SHA_256
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByBytecodeSha256IsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where bytecodeSha256 is not null
        defaultComputeTaskFiltering("bytecodeSha256.specified=true", "bytecodeSha256.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByBytecodeSha256ContainsSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where bytecodeSha256 contains
        defaultComputeTaskFiltering(
            "bytecodeSha256.contains=" + DEFAULT_BYTECODE_SHA_256,
            "bytecodeSha256.contains=" + UPDATED_BYTECODE_SHA_256
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByBytecodeSha256NotContainsSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where bytecodeSha256 does not contain
        defaultComputeTaskFiltering(
            "bytecodeSha256.doesNotContain=" + UPDATED_BYTECODE_SHA_256,
            "bytecodeSha256.doesNotContain=" + DEFAULT_BYTECODE_SHA_256
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByInstructionBudgetIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where instructionBudget equals to
        defaultComputeTaskFiltering(
            "instructionBudget.equals=" + DEFAULT_INSTRUCTION_BUDGET,
            "instructionBudget.equals=" + UPDATED_INSTRUCTION_BUDGET
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByInstructionBudgetIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where instructionBudget in
        defaultComputeTaskFiltering(
            "instructionBudget.in=" + DEFAULT_INSTRUCTION_BUDGET + "," + UPDATED_INSTRUCTION_BUDGET,
            "instructionBudget.in=" + UPDATED_INSTRUCTION_BUDGET
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByInstructionBudgetIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where instructionBudget is not null
        defaultComputeTaskFiltering("instructionBudget.specified=true", "instructionBudget.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByInstructionBudgetIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where instructionBudget is greater than or equal to
        defaultComputeTaskFiltering(
            "instructionBudget.greaterThanOrEqual=" + DEFAULT_INSTRUCTION_BUDGET,
            "instructionBudget.greaterThanOrEqual=" + (DEFAULT_INSTRUCTION_BUDGET + 1)
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByInstructionBudgetIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where instructionBudget is less than or equal to
        defaultComputeTaskFiltering(
            "instructionBudget.lessThanOrEqual=" + DEFAULT_INSTRUCTION_BUDGET,
            "instructionBudget.lessThanOrEqual=" + SMALLER_INSTRUCTION_BUDGET
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByInstructionBudgetIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where instructionBudget is less than
        defaultComputeTaskFiltering(
            "instructionBudget.lessThan=" + (DEFAULT_INSTRUCTION_BUDGET + 1),
            "instructionBudget.lessThan=" + DEFAULT_INSTRUCTION_BUDGET
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByInstructionBudgetIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where instructionBudget is greater than
        defaultComputeTaskFiltering(
            "instructionBudget.greaterThan=" + SMALLER_INSTRUCTION_BUDGET,
            "instructionBudget.greaterThan=" + DEFAULT_INSTRUCTION_BUDGET
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByMaxMemoryBytesIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where maxMemoryBytes equals to
        defaultComputeTaskFiltering(
            "maxMemoryBytes.equals=" + DEFAULT_MAX_MEMORY_BYTES,
            "maxMemoryBytes.equals=" + UPDATED_MAX_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByMaxMemoryBytesIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where maxMemoryBytes in
        defaultComputeTaskFiltering(
            "maxMemoryBytes.in=" + DEFAULT_MAX_MEMORY_BYTES + "," + UPDATED_MAX_MEMORY_BYTES,
            "maxMemoryBytes.in=" + UPDATED_MAX_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByMaxMemoryBytesIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where maxMemoryBytes is not null
        defaultComputeTaskFiltering("maxMemoryBytes.specified=true", "maxMemoryBytes.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByMaxMemoryBytesIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where maxMemoryBytes is greater than or equal to
        defaultComputeTaskFiltering(
            "maxMemoryBytes.greaterThanOrEqual=" + DEFAULT_MAX_MEMORY_BYTES,
            "maxMemoryBytes.greaterThanOrEqual=" + (DEFAULT_MAX_MEMORY_BYTES + 1)
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByMaxMemoryBytesIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where maxMemoryBytes is less than or equal to
        defaultComputeTaskFiltering(
            "maxMemoryBytes.lessThanOrEqual=" + DEFAULT_MAX_MEMORY_BYTES,
            "maxMemoryBytes.lessThanOrEqual=" + SMALLER_MAX_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByMaxMemoryBytesIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where maxMemoryBytes is less than
        defaultComputeTaskFiltering(
            "maxMemoryBytes.lessThan=" + (DEFAULT_MAX_MEMORY_BYTES + 1),
            "maxMemoryBytes.lessThan=" + DEFAULT_MAX_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByMaxMemoryBytesIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where maxMemoryBytes is greater than
        defaultComputeTaskFiltering(
            "maxMemoryBytes.greaterThan=" + SMALLER_MAX_MEMORY_BYTES,
            "maxMemoryBytes.greaterThan=" + DEFAULT_MAX_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByTimeoutMsIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where timeoutMs equals to
        defaultComputeTaskFiltering("timeoutMs.equals=" + DEFAULT_TIMEOUT_MS, "timeoutMs.equals=" + UPDATED_TIMEOUT_MS);
    }

    @Test
    @Transactional
    void getAllComputeTasksByTimeoutMsIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where timeoutMs in
        defaultComputeTaskFiltering("timeoutMs.in=" + DEFAULT_TIMEOUT_MS + "," + UPDATED_TIMEOUT_MS, "timeoutMs.in=" + UPDATED_TIMEOUT_MS);
    }

    @Test
    @Transactional
    void getAllComputeTasksByTimeoutMsIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where timeoutMs is not null
        defaultComputeTaskFiltering("timeoutMs.specified=true", "timeoutMs.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByTimeoutMsIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where timeoutMs is greater than or equal to
        defaultComputeTaskFiltering(
            "timeoutMs.greaterThanOrEqual=" + DEFAULT_TIMEOUT_MS,
            "timeoutMs.greaterThanOrEqual=" + (DEFAULT_TIMEOUT_MS + 1)
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByTimeoutMsIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where timeoutMs is less than or equal to
        defaultComputeTaskFiltering("timeoutMs.lessThanOrEqual=" + DEFAULT_TIMEOUT_MS, "timeoutMs.lessThanOrEqual=" + SMALLER_TIMEOUT_MS);
    }

    @Test
    @Transactional
    void getAllComputeTasksByTimeoutMsIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where timeoutMs is less than
        defaultComputeTaskFiltering("timeoutMs.lessThan=" + (DEFAULT_TIMEOUT_MS + 1), "timeoutMs.lessThan=" + DEFAULT_TIMEOUT_MS);
    }

    @Test
    @Transactional
    void getAllComputeTasksByTimeoutMsIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where timeoutMs is greater than
        defaultComputeTaskFiltering("timeoutMs.greaterThan=" + SMALLER_TIMEOUT_MS, "timeoutMs.greaterThan=" + DEFAULT_TIMEOUT_MS);
    }

    @Test
    @Transactional
    void getAllComputeTasksByExitCodeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where exitCode equals to
        defaultComputeTaskFiltering("exitCode.equals=" + DEFAULT_EXIT_CODE, "exitCode.equals=" + UPDATED_EXIT_CODE);
    }

    @Test
    @Transactional
    void getAllComputeTasksByExitCodeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where exitCode in
        defaultComputeTaskFiltering("exitCode.in=" + DEFAULT_EXIT_CODE + "," + UPDATED_EXIT_CODE, "exitCode.in=" + UPDATED_EXIT_CODE);
    }

    @Test
    @Transactional
    void getAllComputeTasksByExitCodeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where exitCode is not null
        defaultComputeTaskFiltering("exitCode.specified=true", "exitCode.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByExitCodeIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where exitCode is greater than or equal to
        defaultComputeTaskFiltering("exitCode.greaterThanOrEqual=" + DEFAULT_EXIT_CODE, "exitCode.greaterThanOrEqual=" + UPDATED_EXIT_CODE);
    }

    @Test
    @Transactional
    void getAllComputeTasksByExitCodeIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where exitCode is less than or equal to
        defaultComputeTaskFiltering("exitCode.lessThanOrEqual=" + DEFAULT_EXIT_CODE, "exitCode.lessThanOrEqual=" + SMALLER_EXIT_CODE);
    }

    @Test
    @Transactional
    void getAllComputeTasksByExitCodeIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where exitCode is less than
        defaultComputeTaskFiltering("exitCode.lessThan=" + UPDATED_EXIT_CODE, "exitCode.lessThan=" + DEFAULT_EXIT_CODE);
    }

    @Test
    @Transactional
    void getAllComputeTasksByExitCodeIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where exitCode is greater than
        defaultComputeTaskFiltering("exitCode.greaterThan=" + SMALLER_EXIT_CODE, "exitCode.greaterThan=" + DEFAULT_EXIT_CODE);
    }

    @Test
    @Transactional
    void getAllComputeTasksByInstructionsConsumedIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where instructionsConsumed equals to
        defaultComputeTaskFiltering(
            "instructionsConsumed.equals=" + DEFAULT_INSTRUCTIONS_CONSUMED,
            "instructionsConsumed.equals=" + UPDATED_INSTRUCTIONS_CONSUMED
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByInstructionsConsumedIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where instructionsConsumed in
        defaultComputeTaskFiltering(
            "instructionsConsumed.in=" + DEFAULT_INSTRUCTIONS_CONSUMED + "," + UPDATED_INSTRUCTIONS_CONSUMED,
            "instructionsConsumed.in=" + UPDATED_INSTRUCTIONS_CONSUMED
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByInstructionsConsumedIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where instructionsConsumed is not null
        defaultComputeTaskFiltering("instructionsConsumed.specified=true", "instructionsConsumed.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByInstructionsConsumedIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where instructionsConsumed is greater than or equal to
        defaultComputeTaskFiltering(
            "instructionsConsumed.greaterThanOrEqual=" + DEFAULT_INSTRUCTIONS_CONSUMED,
            "instructionsConsumed.greaterThanOrEqual=" + UPDATED_INSTRUCTIONS_CONSUMED
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByInstructionsConsumedIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where instructionsConsumed is less than or equal to
        defaultComputeTaskFiltering(
            "instructionsConsumed.lessThanOrEqual=" + DEFAULT_INSTRUCTIONS_CONSUMED,
            "instructionsConsumed.lessThanOrEqual=" + SMALLER_INSTRUCTIONS_CONSUMED
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByInstructionsConsumedIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where instructionsConsumed is less than
        defaultComputeTaskFiltering(
            "instructionsConsumed.lessThan=" + UPDATED_INSTRUCTIONS_CONSUMED,
            "instructionsConsumed.lessThan=" + DEFAULT_INSTRUCTIONS_CONSUMED
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByInstructionsConsumedIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where instructionsConsumed is greater than
        defaultComputeTaskFiltering(
            "instructionsConsumed.greaterThan=" + SMALLER_INSTRUCTIONS_CONSUMED,
            "instructionsConsumed.greaterThan=" + DEFAULT_INSTRUCTIONS_CONSUMED
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByPeakMemoryBytesIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where peakMemoryBytes equals to
        defaultComputeTaskFiltering(
            "peakMemoryBytes.equals=" + DEFAULT_PEAK_MEMORY_BYTES,
            "peakMemoryBytes.equals=" + UPDATED_PEAK_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByPeakMemoryBytesIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where peakMemoryBytes in
        defaultComputeTaskFiltering(
            "peakMemoryBytes.in=" + DEFAULT_PEAK_MEMORY_BYTES + "," + UPDATED_PEAK_MEMORY_BYTES,
            "peakMemoryBytes.in=" + UPDATED_PEAK_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByPeakMemoryBytesIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where peakMemoryBytes is not null
        defaultComputeTaskFiltering("peakMemoryBytes.specified=true", "peakMemoryBytes.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByPeakMemoryBytesIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where peakMemoryBytes is greater than or equal to
        defaultComputeTaskFiltering(
            "peakMemoryBytes.greaterThanOrEqual=" + DEFAULT_PEAK_MEMORY_BYTES,
            "peakMemoryBytes.greaterThanOrEqual=" + UPDATED_PEAK_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByPeakMemoryBytesIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where peakMemoryBytes is less than or equal to
        defaultComputeTaskFiltering(
            "peakMemoryBytes.lessThanOrEqual=" + DEFAULT_PEAK_MEMORY_BYTES,
            "peakMemoryBytes.lessThanOrEqual=" + SMALLER_PEAK_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByPeakMemoryBytesIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where peakMemoryBytes is less than
        defaultComputeTaskFiltering(
            "peakMemoryBytes.lessThan=" + UPDATED_PEAK_MEMORY_BYTES,
            "peakMemoryBytes.lessThan=" + DEFAULT_PEAK_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByPeakMemoryBytesIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where peakMemoryBytes is greater than
        defaultComputeTaskFiltering(
            "peakMemoryBytes.greaterThan=" + SMALLER_PEAK_MEMORY_BYTES,
            "peakMemoryBytes.greaterThan=" + DEFAULT_PEAK_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByWallTimeMsIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where wallTimeMs equals to
        defaultComputeTaskFiltering("wallTimeMs.equals=" + DEFAULT_WALL_TIME_MS, "wallTimeMs.equals=" + UPDATED_WALL_TIME_MS);
    }

    @Test
    @Transactional
    void getAllComputeTasksByWallTimeMsIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where wallTimeMs in
        defaultComputeTaskFiltering(
            "wallTimeMs.in=" + DEFAULT_WALL_TIME_MS + "," + UPDATED_WALL_TIME_MS,
            "wallTimeMs.in=" + UPDATED_WALL_TIME_MS
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByWallTimeMsIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where wallTimeMs is not null
        defaultComputeTaskFiltering("wallTimeMs.specified=true", "wallTimeMs.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByWallTimeMsIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where wallTimeMs is greater than or equal to
        defaultComputeTaskFiltering(
            "wallTimeMs.greaterThanOrEqual=" + DEFAULT_WALL_TIME_MS,
            "wallTimeMs.greaterThanOrEqual=" + UPDATED_WALL_TIME_MS
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByWallTimeMsIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where wallTimeMs is less than or equal to
        defaultComputeTaskFiltering(
            "wallTimeMs.lessThanOrEqual=" + DEFAULT_WALL_TIME_MS,
            "wallTimeMs.lessThanOrEqual=" + SMALLER_WALL_TIME_MS
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByWallTimeMsIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where wallTimeMs is less than
        defaultComputeTaskFiltering("wallTimeMs.lessThan=" + UPDATED_WALL_TIME_MS, "wallTimeMs.lessThan=" + DEFAULT_WALL_TIME_MS);
    }

    @Test
    @Transactional
    void getAllComputeTasksByWallTimeMsIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where wallTimeMs is greater than
        defaultComputeTaskFiltering("wallTimeMs.greaterThan=" + SMALLER_WALL_TIME_MS, "wallTimeMs.greaterThan=" + DEFAULT_WALL_TIME_MS);
    }

    @Test
    @Transactional
    void getAllComputeTasksByAttemptCountIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where attemptCount equals to
        defaultComputeTaskFiltering("attemptCount.equals=" + DEFAULT_ATTEMPT_COUNT, "attemptCount.equals=" + UPDATED_ATTEMPT_COUNT);
    }

    @Test
    @Transactional
    void getAllComputeTasksByAttemptCountIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where attemptCount in
        defaultComputeTaskFiltering(
            "attemptCount.in=" + DEFAULT_ATTEMPT_COUNT + "," + UPDATED_ATTEMPT_COUNT,
            "attemptCount.in=" + UPDATED_ATTEMPT_COUNT
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByAttemptCountIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where attemptCount is not null
        defaultComputeTaskFiltering("attemptCount.specified=true", "attemptCount.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByAttemptCountIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where attemptCount is greater than or equal to
        defaultComputeTaskFiltering(
            "attemptCount.greaterThanOrEqual=" + DEFAULT_ATTEMPT_COUNT,
            "attemptCount.greaterThanOrEqual=" + UPDATED_ATTEMPT_COUNT
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByAttemptCountIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where attemptCount is less than or equal to
        defaultComputeTaskFiltering(
            "attemptCount.lessThanOrEqual=" + DEFAULT_ATTEMPT_COUNT,
            "attemptCount.lessThanOrEqual=" + SMALLER_ATTEMPT_COUNT
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByAttemptCountIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where attemptCount is less than
        defaultComputeTaskFiltering("attemptCount.lessThan=" + UPDATED_ATTEMPT_COUNT, "attemptCount.lessThan=" + DEFAULT_ATTEMPT_COUNT);
    }

    @Test
    @Transactional
    void getAllComputeTasksByAttemptCountIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where attemptCount is greater than
        defaultComputeTaskFiltering(
            "attemptCount.greaterThan=" + SMALLER_ATTEMPT_COUNT,
            "attemptCount.greaterThan=" + DEFAULT_ATTEMPT_COUNT
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByMaxRetriesIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where maxRetries equals to
        defaultComputeTaskFiltering("maxRetries.equals=" + DEFAULT_MAX_RETRIES, "maxRetries.equals=" + UPDATED_MAX_RETRIES);
    }

    @Test
    @Transactional
    void getAllComputeTasksByMaxRetriesIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where maxRetries in
        defaultComputeTaskFiltering(
            "maxRetries.in=" + DEFAULT_MAX_RETRIES + "," + UPDATED_MAX_RETRIES,
            "maxRetries.in=" + UPDATED_MAX_RETRIES
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByMaxRetriesIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where maxRetries is not null
        defaultComputeTaskFiltering("maxRetries.specified=true", "maxRetries.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByMaxRetriesIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where maxRetries is greater than or equal to
        defaultComputeTaskFiltering(
            "maxRetries.greaterThanOrEqual=" + DEFAULT_MAX_RETRIES,
            "maxRetries.greaterThanOrEqual=" + (DEFAULT_MAX_RETRIES + 1)
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByMaxRetriesIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where maxRetries is less than or equal to
        defaultComputeTaskFiltering(
            "maxRetries.lessThanOrEqual=" + DEFAULT_MAX_RETRIES,
            "maxRetries.lessThanOrEqual=" + SMALLER_MAX_RETRIES
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByMaxRetriesIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where maxRetries is less than
        defaultComputeTaskFiltering("maxRetries.lessThan=" + (DEFAULT_MAX_RETRIES + 1), "maxRetries.lessThan=" + DEFAULT_MAX_RETRIES);
    }

    @Test
    @Transactional
    void getAllComputeTasksByMaxRetriesIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where maxRetries is greater than
        defaultComputeTaskFiltering("maxRetries.greaterThan=" + SMALLER_MAX_RETRIES, "maxRetries.greaterThan=" + DEFAULT_MAX_RETRIES);
    }

    @Test
    @Transactional
    void getAllComputeTasksByCreatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where createdAt equals to
        defaultComputeTaskFiltering("createdAt.equals=" + DEFAULT_CREATED_AT, "createdAt.equals=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllComputeTasksByCreatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where createdAt in
        defaultComputeTaskFiltering("createdAt.in=" + DEFAULT_CREATED_AT + "," + UPDATED_CREATED_AT, "createdAt.in=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllComputeTasksByCreatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where createdAt is not null
        defaultComputeTaskFiltering("createdAt.specified=true", "createdAt.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByStartedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where startedAt equals to
        defaultComputeTaskFiltering("startedAt.equals=" + DEFAULT_STARTED_AT, "startedAt.equals=" + UPDATED_STARTED_AT);
    }

    @Test
    @Transactional
    void getAllComputeTasksByStartedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where startedAt in
        defaultComputeTaskFiltering("startedAt.in=" + DEFAULT_STARTED_AT + "," + UPDATED_STARTED_AT, "startedAt.in=" + UPDATED_STARTED_AT);
    }

    @Test
    @Transactional
    void getAllComputeTasksByStartedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where startedAt is not null
        defaultComputeTaskFiltering("startedAt.specified=true", "startedAt.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByCompletedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where completedAt equals to
        defaultComputeTaskFiltering("completedAt.equals=" + DEFAULT_COMPLETED_AT, "completedAt.equals=" + UPDATED_COMPLETED_AT);
    }

    @Test
    @Transactional
    void getAllComputeTasksByCompletedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where completedAt in
        defaultComputeTaskFiltering(
            "completedAt.in=" + DEFAULT_COMPLETED_AT + "," + UPDATED_COMPLETED_AT,
            "completedAt.in=" + UPDATED_COMPLETED_AT
        );
    }

    @Test
    @Transactional
    void getAllComputeTasksByCompletedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        // Get all the computeTaskList where completedAt is not null
        defaultComputeTaskFiltering("completedAt.specified=true", "completedAt.specified=false");
    }

    @Test
    @Transactional
    void getAllComputeTasksByFunctionIsEqualToSomething() throws Exception {
        SolixFunction function;
        if (TestUtil.findAll(em, SolixFunction.class).isEmpty()) {
            computeTaskRepository.saveAndFlush(computeTask);
            function = SolixFunctionResourceIT.createEntity();
        } else {
            function = TestUtil.findAll(em, SolixFunction.class).getFirst();
        }
        em.persist(function);
        em.flush();
        computeTask.setFunction(function);
        computeTaskRepository.saveAndFlush(computeTask);
        UUID functionId = function.getFunctionId();
        // Get all the computeTaskList where function equals to functionId
        defaultComputeTaskShouldBeFound("functionId.equals=" + functionId);

        // Get all the computeTaskList where function equals to UUID.randomUUID()
        defaultComputeTaskShouldNotBeFound("functionId.equals=" + UUID.randomUUID());
    }

    @Test
    @Transactional
    void getAllComputeTasksByAssignedWorkerIsEqualToSomething() throws Exception {
        WorkerNode assignedWorker;
        if (TestUtil.findAll(em, WorkerNode.class).isEmpty()) {
            computeTaskRepository.saveAndFlush(computeTask);
            assignedWorker = WorkerNodeResourceIT.createEntity();
        } else {
            assignedWorker = TestUtil.findAll(em, WorkerNode.class).getFirst();
        }
        em.persist(assignedWorker);
        em.flush();
        computeTask.setAssignedWorker(assignedWorker);
        computeTaskRepository.saveAndFlush(computeTask);
        String assignedWorkerId = assignedWorker.getWorkerId();
        // Get all the computeTaskList where assignedWorker equals to assignedWorkerId
        defaultComputeTaskShouldBeFound("assignedWorkerId.equals=" + assignedWorkerId);

        // Get all the computeTaskList where assignedWorker equals to "invalid-id"
        defaultComputeTaskShouldNotBeFound("assignedWorkerId.equals=" + "invalid-id");
    }

    private void defaultComputeTaskFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultComputeTaskShouldBeFound(shouldBeFound);
        defaultComputeTaskShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultComputeTaskShouldBeFound(String filter) throws Exception {
        restComputeTaskMockMvc
            .perform(get(ENTITY_API_URL + "?sort=taskId,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].taskId").value(hasItem(computeTask.getTaskId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].priority").value(hasItem(DEFAULT_PRIORITY.toString())))
            .andExpect(jsonPath("$.[*].priorityWeight").value(hasItem(DEFAULT_PRIORITY_WEIGHT)))
            .andExpect(jsonPath("$.[*].idempotent").value(hasItem(DEFAULT_IDEMPOTENT)))
            .andExpect(jsonPath("$.[*].entryPoint").value(hasItem(DEFAULT_ENTRY_POINT)))
            .andExpect(jsonPath("$.[*].bytecodeSha256").value(hasItem(DEFAULT_BYTECODE_SHA_256)))
            .andExpect(jsonPath("$.[*].instructionBudget").value(hasItem(DEFAULT_INSTRUCTION_BUDGET.intValue())))
            .andExpect(jsonPath("$.[*].maxMemoryBytes").value(hasItem(DEFAULT_MAX_MEMORY_BYTES.intValue())))
            .andExpect(jsonPath("$.[*].timeoutMs").value(hasItem(DEFAULT_TIMEOUT_MS)))
            .andExpect(jsonPath("$.[*].exitCode").value(hasItem(DEFAULT_EXIT_CODE)))
            .andExpect(jsonPath("$.[*].instructionsConsumed").value(hasItem(DEFAULT_INSTRUCTIONS_CONSUMED.intValue())))
            .andExpect(jsonPath("$.[*].peakMemoryBytes").value(hasItem(DEFAULT_PEAK_MEMORY_BYTES.intValue())))
            .andExpect(jsonPath("$.[*].wallTimeMs").value(hasItem(DEFAULT_WALL_TIME_MS.intValue())))
            .andExpect(jsonPath("$.[*].inputJson").value(hasItem(DEFAULT_INPUT_JSON)))
            .andExpect(jsonPath("$.[*].envJson").value(hasItem(DEFAULT_ENV_JSON)))
            .andExpect(jsonPath("$.[*].secretRefsJson").value(hasItem(DEFAULT_SECRET_REFS_JSON)))
            .andExpect(jsonPath("$.[*].outputJson").value(hasItem(DEFAULT_OUTPUT_JSON)))
            .andExpect(jsonPath("$.[*].errorJson").value(hasItem(DEFAULT_ERROR_JSON)))
            .andExpect(jsonPath("$.[*].attemptCount").value(hasItem(DEFAULT_ATTEMPT_COUNT)))
            .andExpect(jsonPath("$.[*].maxRetries").value(hasItem(DEFAULT_MAX_RETRIES)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].startedAt").value(hasItem(DEFAULT_STARTED_AT.toString())))
            .andExpect(jsonPath("$.[*].completedAt").value(hasItem(DEFAULT_COMPLETED_AT.toString())));

        // Check, that the count call also returns 1
        restComputeTaskMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=taskId,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultComputeTaskShouldNotBeFound(String filter) throws Exception {
        restComputeTaskMockMvc
            .perform(get(ENTITY_API_URL + "?sort=taskId,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restComputeTaskMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=taskId,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingComputeTask() throws Exception {
        // Get the computeTask
        restComputeTaskMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingComputeTask() throws Exception {
        // Initialize the database
        computeTask.setTaskId(UUID.randomUUID().toString());
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the computeTask
        ComputeTask updatedComputeTask = computeTaskRepository.findById(computeTask.getTaskId()).orElseThrow();
        // Disconnect from session so that the updates on updatedComputeTask are not directly saved in db
        em.detach(updatedComputeTask);
        updatedComputeTask
            .name(UPDATED_NAME)
            .status(UPDATED_STATUS)
            .priority(UPDATED_PRIORITY)
            .priorityWeight(UPDATED_PRIORITY_WEIGHT)
            .idempotent(UPDATED_IDEMPOTENT)
            .entryPoint(UPDATED_ENTRY_POINT)
            .bytecodeSha256(UPDATED_BYTECODE_SHA_256)
            .instructionBudget(UPDATED_INSTRUCTION_BUDGET)
            .maxMemoryBytes(UPDATED_MAX_MEMORY_BYTES)
            .timeoutMs(UPDATED_TIMEOUT_MS)
            .exitCode(UPDATED_EXIT_CODE)
            .instructionsConsumed(UPDATED_INSTRUCTIONS_CONSUMED)
            .peakMemoryBytes(UPDATED_PEAK_MEMORY_BYTES)
            .wallTimeMs(UPDATED_WALL_TIME_MS)
            .inputJson(UPDATED_INPUT_JSON)
            .envJson(UPDATED_ENV_JSON)
            .secretRefsJson(UPDATED_SECRET_REFS_JSON)
            .outputJson(UPDATED_OUTPUT_JSON)
            .errorJson(UPDATED_ERROR_JSON)
            .attemptCount(UPDATED_ATTEMPT_COUNT)
            .maxRetries(UPDATED_MAX_RETRIES)
            .createdAt(UPDATED_CREATED_AT)
            .startedAt(UPDATED_STARTED_AT)
            .completedAt(UPDATED_COMPLETED_AT);
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(updatedComputeTask);

        restComputeTaskMockMvc
            .perform(
                put(ENTITY_API_URL_ID, computeTaskDTO.getTaskId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(computeTaskDTO))
            )
            .andExpect(status().isOk());

        // Validate the ComputeTask in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedComputeTaskToMatchAllProperties(updatedComputeTask);
    }

    @Test
    @Transactional
    void putNonExistingComputeTask() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        computeTask.setTaskId(UUID.randomUUID().toString());

        // Create the ComputeTask
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restComputeTaskMockMvc
            .perform(
                put(ENTITY_API_URL_ID, computeTaskDTO.getTaskId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(computeTaskDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ComputeTask in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchComputeTask() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        computeTask.setTaskId(UUID.randomUUID().toString());

        // Create the ComputeTask
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restComputeTaskMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(computeTaskDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ComputeTask in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamComputeTask() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        computeTask.setTaskId(UUID.randomUUID().toString());

        // Create the ComputeTask
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restComputeTaskMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(computeTaskDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ComputeTask in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateComputeTaskWithPatch() throws Exception {
        // Initialize the database
        computeTask.setTaskId(UUID.randomUUID().toString());
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the computeTask using partial update
        ComputeTask partialUpdatedComputeTask = new ComputeTask();
        partialUpdatedComputeTask.setTaskId(computeTask.getTaskId());

        partialUpdatedComputeTask
            .status(UPDATED_STATUS)
            .priorityWeight(UPDATED_PRIORITY_WEIGHT)
            .idempotent(UPDATED_IDEMPOTENT)
            .bytecodeSha256(UPDATED_BYTECODE_SHA_256)
            .envJson(UPDATED_ENV_JSON)
            .secretRefsJson(UPDATED_SECRET_REFS_JSON)
            .attemptCount(UPDATED_ATTEMPT_COUNT)
            .maxRetries(UPDATED_MAX_RETRIES)
            .createdAt(UPDATED_CREATED_AT)
            .completedAt(UPDATED_COMPLETED_AT);

        restComputeTaskMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedComputeTask.getTaskId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedComputeTask))
            )
            .andExpect(status().isOk());

        // Validate the ComputeTask in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertComputeTaskUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedComputeTask, computeTask),
            getPersistedComputeTask(computeTask)
        );
    }

    @Test
    @Transactional
    void fullUpdateComputeTaskWithPatch() throws Exception {
        // Initialize the database
        computeTask.setTaskId(UUID.randomUUID().toString());
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the computeTask using partial update
        ComputeTask partialUpdatedComputeTask = new ComputeTask();
        partialUpdatedComputeTask.setTaskId(computeTask.getTaskId());

        partialUpdatedComputeTask
            .name(UPDATED_NAME)
            .status(UPDATED_STATUS)
            .priority(UPDATED_PRIORITY)
            .priorityWeight(UPDATED_PRIORITY_WEIGHT)
            .idempotent(UPDATED_IDEMPOTENT)
            .entryPoint(UPDATED_ENTRY_POINT)
            .bytecodeSha256(UPDATED_BYTECODE_SHA_256)
            .instructionBudget(UPDATED_INSTRUCTION_BUDGET)
            .maxMemoryBytes(UPDATED_MAX_MEMORY_BYTES)
            .timeoutMs(UPDATED_TIMEOUT_MS)
            .exitCode(UPDATED_EXIT_CODE)
            .instructionsConsumed(UPDATED_INSTRUCTIONS_CONSUMED)
            .peakMemoryBytes(UPDATED_PEAK_MEMORY_BYTES)
            .wallTimeMs(UPDATED_WALL_TIME_MS)
            .inputJson(UPDATED_INPUT_JSON)
            .envJson(UPDATED_ENV_JSON)
            .secretRefsJson(UPDATED_SECRET_REFS_JSON)
            .outputJson(UPDATED_OUTPUT_JSON)
            .errorJson(UPDATED_ERROR_JSON)
            .attemptCount(UPDATED_ATTEMPT_COUNT)
            .maxRetries(UPDATED_MAX_RETRIES)
            .createdAt(UPDATED_CREATED_AT)
            .startedAt(UPDATED_STARTED_AT)
            .completedAt(UPDATED_COMPLETED_AT);

        restComputeTaskMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedComputeTask.getTaskId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedComputeTask))
            )
            .andExpect(status().isOk());

        // Validate the ComputeTask in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertComputeTaskUpdatableFieldsEquals(partialUpdatedComputeTask, getPersistedComputeTask(partialUpdatedComputeTask));
    }

    @Test
    @Transactional
    void patchNonExistingComputeTask() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        computeTask.setTaskId(UUID.randomUUID().toString());

        // Create the ComputeTask
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restComputeTaskMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, computeTaskDTO.getTaskId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(computeTaskDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ComputeTask in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchComputeTask() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        computeTask.setTaskId(UUID.randomUUID().toString());

        // Create the ComputeTask
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restComputeTaskMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(computeTaskDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ComputeTask in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamComputeTask() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        computeTask.setTaskId(UUID.randomUUID().toString());

        // Create the ComputeTask
        ComputeTaskDTO computeTaskDTO = computeTaskMapper.toDto(computeTask);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restComputeTaskMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(computeTaskDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ComputeTask in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteComputeTask() throws Exception {
        // Initialize the database
        computeTask.setTaskId(UUID.randomUUID().toString());
        insertedComputeTask = computeTaskRepository.saveAndFlush(computeTask);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the computeTask
        restComputeTaskMockMvc
            .perform(delete(ENTITY_API_URL_ID, computeTask.getTaskId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return computeTaskRepository.count();
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

    protected ComputeTask getPersistedComputeTask(ComputeTask computeTask) {
        return computeTaskRepository.findById(computeTask.getTaskId()).orElseThrow();
    }

    protected void assertPersistedComputeTaskToMatchAllProperties(ComputeTask expectedComputeTask) {
        assertComputeTaskAllPropertiesEquals(expectedComputeTask, getPersistedComputeTask(expectedComputeTask));
    }

    protected void assertPersistedComputeTaskToMatchUpdatableProperties(ComputeTask expectedComputeTask) {
        assertComputeTaskAllUpdatablePropertiesEquals(expectedComputeTask, getPersistedComputeTask(expectedComputeTask));
    }
}
