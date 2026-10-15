package dev.legatus.server.web.rest;

import static dev.legatus.server.domain.WorkerNodeAsserts.*;
import static dev.legatus.server.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.CpuArchitecture;
import dev.legatus.server.domain.enumeration.OperatingSystem;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.service.dto.WorkerNodeDTO;
import dev.legatus.server.service.mapper.WorkerNodeMapper;
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
 * Integration tests for the {@link WorkerNodeResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class WorkerNodeResourceIT {

    private static final String DEFAULT_ENROLLMENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_ENROLLMENT_ID = "BBBBBBBBBB";

    private static final String DEFAULT_HOSTNAME = "AAAAAAAAAA";
    private static final String UPDATED_HOSTNAME = "BBBBBBBBBB";

    private static final OperatingSystem DEFAULT_OS = OperatingSystem.LINUX;
    private static final OperatingSystem UPDATED_OS = OperatingSystem.WINDOWS;

    private static final CpuArchitecture DEFAULT_ARCH = CpuArchitecture.X86_64;
    private static final CpuArchitecture UPDATED_ARCH = CpuArchitecture.AARCH64;

    private static final String DEFAULT_AGENT_VERSION = "AAAAAAAAAA";
    private static final String UPDATED_AGENT_VERSION = "BBBBBBBBBB";

    private static final String DEFAULT_ED_25519_PUBKEY = "AAAAAAAAAA";
    private static final String UPDATED_ED_25519_PUBKEY = "BBBBBBBBBB";

    private static final String DEFAULT_FINGERPRINT = "AAAAAAAAAA";
    private static final String UPDATED_FINGERPRINT = "BBBBBBBBBB";

    private static final String DEFAULT_VISUAL_CODE = "AAAAAAAAAA";
    private static final String UPDATED_VISUAL_CODE = "BBBBBBBBBB";

    private static final String DEFAULT_SECRET_HASH = "AAAAAAAAAA";
    private static final String UPDATED_SECRET_HASH = "BBBBBBBBBB";

    private static final WorkerState DEFAULT_STATE = WorkerState.PENDING_APPROVAL;
    private static final WorkerState UPDATED_STATE = WorkerState.APPROVED;

    private static final String DEFAULT_ASSIGNED_CLUSTER = "AAAAAAAAAA";
    private static final String UPDATED_ASSIGNED_CLUSTER = "BBBBBBBBBB";

    private static final String DEFAULT_SOURCE_IP = "AAAAAAAAAA";
    private static final String UPDATED_SOURCE_IP = "BBBBBBBBBB";

    private static final Integer DEFAULT_PHYSICAL_CORES = 1;
    private static final Integer UPDATED_PHYSICAL_CORES = 2;
    private static final Integer SMALLER_PHYSICAL_CORES = 1 - 1;

    private static final Integer DEFAULT_LOGICAL_CORES = 1;
    private static final Integer UPDATED_LOGICAL_CORES = 2;
    private static final Integer SMALLER_LOGICAL_CORES = 1 - 1;

    private static final Long DEFAULT_TOTAL_MEMORY_BYTES = 1L;
    private static final Long UPDATED_TOTAL_MEMORY_BYTES = 2L;
    private static final Long SMALLER_TOTAL_MEMORY_BYTES = 1L - 1L;

    private static final Boolean DEFAULT_GPU_PRESENT = false;
    private static final Boolean UPDATED_GPU_PRESENT = true;

    private static final String DEFAULT_GPU_MODEL = "AAAAAAAAAA";
    private static final String UPDATED_GPU_MODEL = "BBBBBBBBBB";

    private static final Integer DEFAULT_ALLOCATABLE_CORES = 1;
    private static final Integer UPDATED_ALLOCATABLE_CORES = 2;
    private static final Integer SMALLER_ALLOCATABLE_CORES = 1 - 1;

    private static final Long DEFAULT_MAX_MEMORY_BYTES = 1048576L;
    private static final Long UPDATED_MAX_MEMORY_BYTES = 1048577L;
    private static final Long SMALLER_MAX_MEMORY_BYTES = 1048576L - 1L;

    private static final Long DEFAULT_MAX_INSTRUCTION_BUDGET = 1000L;
    private static final Long UPDATED_MAX_INSTRUCTION_BUDGET = 1001L;
    private static final Long SMALLER_MAX_INSTRUCTION_BUDGET = 1000L - 1L;

    private static final Boolean DEFAULT_DRAINING = false;
    private static final Boolean UPDATED_DRAINING = true;

    private static final String DEFAULT_ACTIVE_SESSION_ID = "AAAAAAAAAA";
    private static final String UPDATED_ACTIVE_SESSION_ID = "BBBBBBBBBB";

    private static final Instant DEFAULT_ENROLLMENT_EXPIRES_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_ENROLLMENT_EXPIRES_AT = Instant.ofEpochMilli(1703883753520L);

    private static final Instant DEFAULT_LAST_SEEN_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_LAST_SEEN_AT = Instant.ofEpochMilli(1703883753520L);

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.ofEpochMilli(1703883753520L);

    private static final String ENTITY_API_URL = "/api/worker-nodes";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{workerId}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private WorkerNodeRepository workerNodeRepository;

    @Autowired
    private WorkerNodeMapper workerNodeMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restWorkerNodeMockMvc;

    private WorkerNode workerNode;

    private WorkerNode insertedWorkerNode;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static WorkerNode createEntity() {
        return new WorkerNode()
            .workerId(UUID.randomUUID().toString())
            .enrollmentId(DEFAULT_ENROLLMENT_ID)
            .hostname(DEFAULT_HOSTNAME)
            .os(DEFAULT_OS)
            .arch(DEFAULT_ARCH)
            .agentVersion(DEFAULT_AGENT_VERSION)
            .ed25519Pubkey(DEFAULT_ED_25519_PUBKEY)
            .fingerprint(DEFAULT_FINGERPRINT)
            .visualCode(DEFAULT_VISUAL_CODE)
            .secretHash(DEFAULT_SECRET_HASH)
            .state(DEFAULT_STATE)
            .assignedCluster(DEFAULT_ASSIGNED_CLUSTER)
            .sourceIp(DEFAULT_SOURCE_IP)
            .physicalCores(DEFAULT_PHYSICAL_CORES)
            .logicalCores(DEFAULT_LOGICAL_CORES)
            .totalMemoryBytes(DEFAULT_TOTAL_MEMORY_BYTES)
            .gpuPresent(DEFAULT_GPU_PRESENT)
            .gpuModel(DEFAULT_GPU_MODEL)
            .allocatableCores(DEFAULT_ALLOCATABLE_CORES)
            .maxMemoryBytes(DEFAULT_MAX_MEMORY_BYTES)
            .maxInstructionBudget(DEFAULT_MAX_INSTRUCTION_BUDGET)
            .draining(DEFAULT_DRAINING)
            .activeSessionId(DEFAULT_ACTIVE_SESSION_ID)
            .enrollmentExpiresAt(DEFAULT_ENROLLMENT_EXPIRES_AT)
            .lastSeenAt(DEFAULT_LAST_SEEN_AT)
            .createdAt(DEFAULT_CREATED_AT);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static WorkerNode createUpdatedEntity() {
        return new WorkerNode()
            .workerId(UUID.randomUUID().toString())
            .enrollmentId(UPDATED_ENROLLMENT_ID)
            .hostname(UPDATED_HOSTNAME)
            .os(UPDATED_OS)
            .arch(UPDATED_ARCH)
            .agentVersion(UPDATED_AGENT_VERSION)
            .ed25519Pubkey(UPDATED_ED_25519_PUBKEY)
            .fingerprint(UPDATED_FINGERPRINT)
            .visualCode(UPDATED_VISUAL_CODE)
            .secretHash(UPDATED_SECRET_HASH)
            .state(UPDATED_STATE)
            .assignedCluster(UPDATED_ASSIGNED_CLUSTER)
            .sourceIp(UPDATED_SOURCE_IP)
            .physicalCores(UPDATED_PHYSICAL_CORES)
            .logicalCores(UPDATED_LOGICAL_CORES)
            .totalMemoryBytes(UPDATED_TOTAL_MEMORY_BYTES)
            .gpuPresent(UPDATED_GPU_PRESENT)
            .gpuModel(UPDATED_GPU_MODEL)
            .allocatableCores(UPDATED_ALLOCATABLE_CORES)
            .maxMemoryBytes(UPDATED_MAX_MEMORY_BYTES)
            .maxInstructionBudget(UPDATED_MAX_INSTRUCTION_BUDGET)
            .draining(UPDATED_DRAINING)
            .activeSessionId(UPDATED_ACTIVE_SESSION_ID)
            .enrollmentExpiresAt(UPDATED_ENROLLMENT_EXPIRES_AT)
            .lastSeenAt(UPDATED_LAST_SEEN_AT)
            .createdAt(UPDATED_CREATED_AT);
    }

    @BeforeEach
    void initTest() {
        workerNode = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedWorkerNode != null) {
            workerNodeRepository.delete(insertedWorkerNode);
            insertedWorkerNode = null;
        }
    }

    @Test
    @Transactional
    void createWorkerNode() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the WorkerNode
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);
        var returnedWorkerNodeDTO = om.readValue(
            restWorkerNodeMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            WorkerNodeDTO.class
        );

        // Validate the WorkerNode in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedWorkerNode = workerNodeMapper.toEntity(returnedWorkerNodeDTO);
        assertWorkerNodeUpdatableFieldsEquals(returnedWorkerNode, getPersistedWorkerNode(returnedWorkerNode));

        insertedWorkerNode = returnedWorkerNode;
    }

    @Test
    @Transactional
    void createWorkerNodeWithExistingId() throws Exception {
        // Create the WorkerNode with an existing ID
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        // Validate the WorkerNode in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkHostnameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setHostname(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkOsIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setOs(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkArchIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setArch(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkAgentVersionIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setAgentVersion(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkEd25519PubkeyIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setEd25519Pubkey(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkFingerprintIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setFingerprint(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkVisualCodeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setVisualCode(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkStateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setState(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkPhysicalCoresIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setPhysicalCores(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkLogicalCoresIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setLogicalCores(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkTotalMemoryBytesIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setTotalMemoryBytes(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkGpuPresentIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setGpuPresent(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkAllocatableCoresIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setAllocatableCores(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkMaxMemoryBytesIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setMaxMemoryBytes(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkMaxInstructionBudgetIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setMaxInstructionBudget(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkDrainingIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setDraining(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workerNode.setCreatedAt(null);

        // Create the WorkerNode, which fails.
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        restWorkerNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllWorkerNodes() throws Exception {
        // Initialize the database
        workerNode.setWorkerId(UUID.randomUUID().toString());
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList
        restWorkerNodeMockMvc
            .perform(get(ENTITY_API_URL + "?sort=workerId,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].workerId").value(hasItem(workerNode.getWorkerId())))
            .andExpect(jsonPath("$.[*].enrollmentId").value(hasItem(DEFAULT_ENROLLMENT_ID)))
            .andExpect(jsonPath("$.[*].hostname").value(hasItem(DEFAULT_HOSTNAME)))
            .andExpect(jsonPath("$.[*].os").value(hasItem(DEFAULT_OS.toString())))
            .andExpect(jsonPath("$.[*].arch").value(hasItem(DEFAULT_ARCH.toString())))
            .andExpect(jsonPath("$.[*].agentVersion").value(hasItem(DEFAULT_AGENT_VERSION)))
            .andExpect(jsonPath("$.[*].ed25519Pubkey").value(hasItem(DEFAULT_ED_25519_PUBKEY)))
            .andExpect(jsonPath("$.[*].fingerprint").value(hasItem(DEFAULT_FINGERPRINT)))
            .andExpect(jsonPath("$.[*].visualCode").value(hasItem(DEFAULT_VISUAL_CODE)))
            .andExpect(jsonPath("$.[*].secretHash").value(hasItem(DEFAULT_SECRET_HASH)))
            .andExpect(jsonPath("$.[*].state").value(hasItem(DEFAULT_STATE.toString())))
            .andExpect(jsonPath("$.[*].assignedCluster").value(hasItem(DEFAULT_ASSIGNED_CLUSTER)))
            .andExpect(jsonPath("$.[*].sourceIp").value(hasItem(DEFAULT_SOURCE_IP)))
            .andExpect(jsonPath("$.[*].physicalCores").value(hasItem(DEFAULT_PHYSICAL_CORES)))
            .andExpect(jsonPath("$.[*].logicalCores").value(hasItem(DEFAULT_LOGICAL_CORES)))
            .andExpect(jsonPath("$.[*].totalMemoryBytes").value(hasItem(DEFAULT_TOTAL_MEMORY_BYTES.intValue())))
            .andExpect(jsonPath("$.[*].gpuPresent").value(hasItem(DEFAULT_GPU_PRESENT)))
            .andExpect(jsonPath("$.[*].gpuModel").value(hasItem(DEFAULT_GPU_MODEL)))
            .andExpect(jsonPath("$.[*].allocatableCores").value(hasItem(DEFAULT_ALLOCATABLE_CORES)))
            .andExpect(jsonPath("$.[*].maxMemoryBytes").value(hasItem(DEFAULT_MAX_MEMORY_BYTES.intValue())))
            .andExpect(jsonPath("$.[*].maxInstructionBudget").value(hasItem(DEFAULT_MAX_INSTRUCTION_BUDGET.intValue())))
            .andExpect(jsonPath("$.[*].draining").value(hasItem(DEFAULT_DRAINING)))
            .andExpect(jsonPath("$.[*].activeSessionId").value(hasItem(DEFAULT_ACTIVE_SESSION_ID)))
            .andExpect(jsonPath("$.[*].enrollmentExpiresAt").value(hasItem(DEFAULT_ENROLLMENT_EXPIRES_AT.toString())))
            .andExpect(jsonPath("$.[*].lastSeenAt").value(hasItem(DEFAULT_LAST_SEEN_AT.toString())))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())));
    }

    @Test
    @Transactional
    void getWorkerNode() throws Exception {
        // Initialize the database
        workerNode.setWorkerId(UUID.randomUUID().toString());
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get the workerNode
        restWorkerNodeMockMvc
            .perform(get(ENTITY_API_URL_ID, workerNode.getWorkerId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.workerId").value(workerNode.getWorkerId()))
            .andExpect(jsonPath("$.enrollmentId").value(DEFAULT_ENROLLMENT_ID))
            .andExpect(jsonPath("$.hostname").value(DEFAULT_HOSTNAME))
            .andExpect(jsonPath("$.os").value(DEFAULT_OS.toString()))
            .andExpect(jsonPath("$.arch").value(DEFAULT_ARCH.toString()))
            .andExpect(jsonPath("$.agentVersion").value(DEFAULT_AGENT_VERSION))
            .andExpect(jsonPath("$.ed25519Pubkey").value(DEFAULT_ED_25519_PUBKEY))
            .andExpect(jsonPath("$.fingerprint").value(DEFAULT_FINGERPRINT))
            .andExpect(jsonPath("$.visualCode").value(DEFAULT_VISUAL_CODE))
            .andExpect(jsonPath("$.secretHash").value(DEFAULT_SECRET_HASH))
            .andExpect(jsonPath("$.state").value(DEFAULT_STATE.toString()))
            .andExpect(jsonPath("$.assignedCluster").value(DEFAULT_ASSIGNED_CLUSTER))
            .andExpect(jsonPath("$.sourceIp").value(DEFAULT_SOURCE_IP))
            .andExpect(jsonPath("$.physicalCores").value(DEFAULT_PHYSICAL_CORES))
            .andExpect(jsonPath("$.logicalCores").value(DEFAULT_LOGICAL_CORES))
            .andExpect(jsonPath("$.totalMemoryBytes").value(DEFAULT_TOTAL_MEMORY_BYTES.intValue()))
            .andExpect(jsonPath("$.gpuPresent").value(DEFAULT_GPU_PRESENT))
            .andExpect(jsonPath("$.gpuModel").value(DEFAULT_GPU_MODEL))
            .andExpect(jsonPath("$.allocatableCores").value(DEFAULT_ALLOCATABLE_CORES))
            .andExpect(jsonPath("$.maxMemoryBytes").value(DEFAULT_MAX_MEMORY_BYTES.intValue()))
            .andExpect(jsonPath("$.maxInstructionBudget").value(DEFAULT_MAX_INSTRUCTION_BUDGET.intValue()))
            .andExpect(jsonPath("$.draining").value(DEFAULT_DRAINING))
            .andExpect(jsonPath("$.activeSessionId").value(DEFAULT_ACTIVE_SESSION_ID))
            .andExpect(jsonPath("$.enrollmentExpiresAt").value(DEFAULT_ENROLLMENT_EXPIRES_AT.toString()))
            .andExpect(jsonPath("$.lastSeenAt").value(DEFAULT_LAST_SEEN_AT.toString()))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()));
    }

    @Test
    @Transactional
    void getWorkerNodesByIdFiltering() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        String id = workerNode.getWorkerId();

        defaultWorkerNodeFiltering("workerId.equals=" + id, "workerId.notEquals=" + id);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByEnrollmentIdIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where enrollmentId equals to
        defaultWorkerNodeFiltering("enrollmentId.equals=" + DEFAULT_ENROLLMENT_ID, "enrollmentId.equals=" + UPDATED_ENROLLMENT_ID);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByEnrollmentIdIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where enrollmentId in
        defaultWorkerNodeFiltering(
            "enrollmentId.in=" + DEFAULT_ENROLLMENT_ID + "," + UPDATED_ENROLLMENT_ID,
            "enrollmentId.in=" + UPDATED_ENROLLMENT_ID
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByEnrollmentIdIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where enrollmentId is not null
        defaultWorkerNodeFiltering("enrollmentId.specified=true", "enrollmentId.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByEnrollmentIdContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where enrollmentId contains
        defaultWorkerNodeFiltering("enrollmentId.contains=" + DEFAULT_ENROLLMENT_ID, "enrollmentId.contains=" + UPDATED_ENROLLMENT_ID);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByEnrollmentIdNotContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where enrollmentId does not contain
        defaultWorkerNodeFiltering(
            "enrollmentId.doesNotContain=" + UPDATED_ENROLLMENT_ID,
            "enrollmentId.doesNotContain=" + DEFAULT_ENROLLMENT_ID
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByHostnameIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where hostname equals to
        defaultWorkerNodeFiltering("hostname.equals=" + DEFAULT_HOSTNAME, "hostname.equals=" + UPDATED_HOSTNAME);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByHostnameIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where hostname in
        defaultWorkerNodeFiltering("hostname.in=" + DEFAULT_HOSTNAME + "," + UPDATED_HOSTNAME, "hostname.in=" + UPDATED_HOSTNAME);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByHostnameIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where hostname is not null
        defaultWorkerNodeFiltering("hostname.specified=true", "hostname.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByHostnameContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where hostname contains
        defaultWorkerNodeFiltering("hostname.contains=" + DEFAULT_HOSTNAME, "hostname.contains=" + UPDATED_HOSTNAME);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByHostnameNotContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where hostname does not contain
        defaultWorkerNodeFiltering("hostname.doesNotContain=" + UPDATED_HOSTNAME, "hostname.doesNotContain=" + DEFAULT_HOSTNAME);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByOsIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where os equals to
        defaultWorkerNodeFiltering("os.equals=" + DEFAULT_OS, "os.equals=" + UPDATED_OS);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByOsIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where os in
        defaultWorkerNodeFiltering("os.in=" + DEFAULT_OS + "," + UPDATED_OS, "os.in=" + UPDATED_OS);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByOsIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where os is not null
        defaultWorkerNodeFiltering("os.specified=true", "os.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByArchIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where arch equals to
        defaultWorkerNodeFiltering("arch.equals=" + DEFAULT_ARCH, "arch.equals=" + UPDATED_ARCH);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByArchIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where arch in
        defaultWorkerNodeFiltering("arch.in=" + DEFAULT_ARCH + "," + UPDATED_ARCH, "arch.in=" + UPDATED_ARCH);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByArchIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where arch is not null
        defaultWorkerNodeFiltering("arch.specified=true", "arch.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAgentVersionIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where agentVersion equals to
        defaultWorkerNodeFiltering("agentVersion.equals=" + DEFAULT_AGENT_VERSION, "agentVersion.equals=" + UPDATED_AGENT_VERSION);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAgentVersionIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where agentVersion in
        defaultWorkerNodeFiltering(
            "agentVersion.in=" + DEFAULT_AGENT_VERSION + "," + UPDATED_AGENT_VERSION,
            "agentVersion.in=" + UPDATED_AGENT_VERSION
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAgentVersionIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where agentVersion is not null
        defaultWorkerNodeFiltering("agentVersion.specified=true", "agentVersion.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAgentVersionContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where agentVersion contains
        defaultWorkerNodeFiltering("agentVersion.contains=" + DEFAULT_AGENT_VERSION, "agentVersion.contains=" + UPDATED_AGENT_VERSION);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAgentVersionNotContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where agentVersion does not contain
        defaultWorkerNodeFiltering(
            "agentVersion.doesNotContain=" + UPDATED_AGENT_VERSION,
            "agentVersion.doesNotContain=" + DEFAULT_AGENT_VERSION
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByEd25519PubkeyIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where ed25519Pubkey equals to
        defaultWorkerNodeFiltering("ed25519Pubkey.equals=" + DEFAULT_ED_25519_PUBKEY, "ed25519Pubkey.equals=" + UPDATED_ED_25519_PUBKEY);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByEd25519PubkeyIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where ed25519Pubkey in
        defaultWorkerNodeFiltering(
            "ed25519Pubkey.in=" + DEFAULT_ED_25519_PUBKEY + "," + UPDATED_ED_25519_PUBKEY,
            "ed25519Pubkey.in=" + UPDATED_ED_25519_PUBKEY
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByEd25519PubkeyIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where ed25519Pubkey is not null
        defaultWorkerNodeFiltering("ed25519Pubkey.specified=true", "ed25519Pubkey.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByEd25519PubkeyContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where ed25519Pubkey contains
        defaultWorkerNodeFiltering(
            "ed25519Pubkey.contains=" + DEFAULT_ED_25519_PUBKEY,
            "ed25519Pubkey.contains=" + UPDATED_ED_25519_PUBKEY
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByEd25519PubkeyNotContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where ed25519Pubkey does not contain
        defaultWorkerNodeFiltering(
            "ed25519Pubkey.doesNotContain=" + UPDATED_ED_25519_PUBKEY,
            "ed25519Pubkey.doesNotContain=" + DEFAULT_ED_25519_PUBKEY
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByFingerprintIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where fingerprint equals to
        defaultWorkerNodeFiltering("fingerprint.equals=" + DEFAULT_FINGERPRINT, "fingerprint.equals=" + UPDATED_FINGERPRINT);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByFingerprintIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where fingerprint in
        defaultWorkerNodeFiltering(
            "fingerprint.in=" + DEFAULT_FINGERPRINT + "," + UPDATED_FINGERPRINT,
            "fingerprint.in=" + UPDATED_FINGERPRINT
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByFingerprintIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where fingerprint is not null
        defaultWorkerNodeFiltering("fingerprint.specified=true", "fingerprint.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByFingerprintContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where fingerprint contains
        defaultWorkerNodeFiltering("fingerprint.contains=" + DEFAULT_FINGERPRINT, "fingerprint.contains=" + UPDATED_FINGERPRINT);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByFingerprintNotContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where fingerprint does not contain
        defaultWorkerNodeFiltering(
            "fingerprint.doesNotContain=" + UPDATED_FINGERPRINT,
            "fingerprint.doesNotContain=" + DEFAULT_FINGERPRINT
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByVisualCodeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where visualCode equals to
        defaultWorkerNodeFiltering("visualCode.equals=" + DEFAULT_VISUAL_CODE, "visualCode.equals=" + UPDATED_VISUAL_CODE);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByVisualCodeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where visualCode in
        defaultWorkerNodeFiltering(
            "visualCode.in=" + DEFAULT_VISUAL_CODE + "," + UPDATED_VISUAL_CODE,
            "visualCode.in=" + UPDATED_VISUAL_CODE
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByVisualCodeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where visualCode is not null
        defaultWorkerNodeFiltering("visualCode.specified=true", "visualCode.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByVisualCodeContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where visualCode contains
        defaultWorkerNodeFiltering("visualCode.contains=" + DEFAULT_VISUAL_CODE, "visualCode.contains=" + UPDATED_VISUAL_CODE);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByVisualCodeNotContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where visualCode does not contain
        defaultWorkerNodeFiltering("visualCode.doesNotContain=" + UPDATED_VISUAL_CODE, "visualCode.doesNotContain=" + DEFAULT_VISUAL_CODE);
    }

    @Test
    @Transactional
    void getAllWorkerNodesBySecretHashIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where secretHash equals to
        defaultWorkerNodeFiltering("secretHash.equals=" + DEFAULT_SECRET_HASH, "secretHash.equals=" + UPDATED_SECRET_HASH);
    }

    @Test
    @Transactional
    void getAllWorkerNodesBySecretHashIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where secretHash in
        defaultWorkerNodeFiltering(
            "secretHash.in=" + DEFAULT_SECRET_HASH + "," + UPDATED_SECRET_HASH,
            "secretHash.in=" + UPDATED_SECRET_HASH
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesBySecretHashIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where secretHash is not null
        defaultWorkerNodeFiltering("secretHash.specified=true", "secretHash.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesBySecretHashContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where secretHash contains
        defaultWorkerNodeFiltering("secretHash.contains=" + DEFAULT_SECRET_HASH, "secretHash.contains=" + UPDATED_SECRET_HASH);
    }

    @Test
    @Transactional
    void getAllWorkerNodesBySecretHashNotContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where secretHash does not contain
        defaultWorkerNodeFiltering("secretHash.doesNotContain=" + UPDATED_SECRET_HASH, "secretHash.doesNotContain=" + DEFAULT_SECRET_HASH);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByStateIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where state equals to
        defaultWorkerNodeFiltering("state.equals=" + DEFAULT_STATE, "state.equals=" + UPDATED_STATE);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByStateIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where state in
        defaultWorkerNodeFiltering("state.in=" + DEFAULT_STATE + "," + UPDATED_STATE, "state.in=" + UPDATED_STATE);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByStateIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where state is not null
        defaultWorkerNodeFiltering("state.specified=true", "state.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAssignedClusterIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where assignedCluster equals to
        defaultWorkerNodeFiltering(
            "assignedCluster.equals=" + DEFAULT_ASSIGNED_CLUSTER,
            "assignedCluster.equals=" + UPDATED_ASSIGNED_CLUSTER
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAssignedClusterIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where assignedCluster in
        defaultWorkerNodeFiltering(
            "assignedCluster.in=" + DEFAULT_ASSIGNED_CLUSTER + "," + UPDATED_ASSIGNED_CLUSTER,
            "assignedCluster.in=" + UPDATED_ASSIGNED_CLUSTER
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAssignedClusterIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where assignedCluster is not null
        defaultWorkerNodeFiltering("assignedCluster.specified=true", "assignedCluster.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAssignedClusterContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where assignedCluster contains
        defaultWorkerNodeFiltering(
            "assignedCluster.contains=" + DEFAULT_ASSIGNED_CLUSTER,
            "assignedCluster.contains=" + UPDATED_ASSIGNED_CLUSTER
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAssignedClusterNotContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where assignedCluster does not contain
        defaultWorkerNodeFiltering(
            "assignedCluster.doesNotContain=" + UPDATED_ASSIGNED_CLUSTER,
            "assignedCluster.doesNotContain=" + DEFAULT_ASSIGNED_CLUSTER
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesBySourceIpIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where sourceIp equals to
        defaultWorkerNodeFiltering("sourceIp.equals=" + DEFAULT_SOURCE_IP, "sourceIp.equals=" + UPDATED_SOURCE_IP);
    }

    @Test
    @Transactional
    void getAllWorkerNodesBySourceIpIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where sourceIp in
        defaultWorkerNodeFiltering("sourceIp.in=" + DEFAULT_SOURCE_IP + "," + UPDATED_SOURCE_IP, "sourceIp.in=" + UPDATED_SOURCE_IP);
    }

    @Test
    @Transactional
    void getAllWorkerNodesBySourceIpIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where sourceIp is not null
        defaultWorkerNodeFiltering("sourceIp.specified=true", "sourceIp.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesBySourceIpContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where sourceIp contains
        defaultWorkerNodeFiltering("sourceIp.contains=" + DEFAULT_SOURCE_IP, "sourceIp.contains=" + UPDATED_SOURCE_IP);
    }

    @Test
    @Transactional
    void getAllWorkerNodesBySourceIpNotContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where sourceIp does not contain
        defaultWorkerNodeFiltering("sourceIp.doesNotContain=" + UPDATED_SOURCE_IP, "sourceIp.doesNotContain=" + DEFAULT_SOURCE_IP);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByPhysicalCoresIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where physicalCores equals to
        defaultWorkerNodeFiltering("physicalCores.equals=" + DEFAULT_PHYSICAL_CORES, "physicalCores.equals=" + UPDATED_PHYSICAL_CORES);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByPhysicalCoresIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where physicalCores in
        defaultWorkerNodeFiltering(
            "physicalCores.in=" + DEFAULT_PHYSICAL_CORES + "," + UPDATED_PHYSICAL_CORES,
            "physicalCores.in=" + UPDATED_PHYSICAL_CORES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByPhysicalCoresIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where physicalCores is not null
        defaultWorkerNodeFiltering("physicalCores.specified=true", "physicalCores.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByPhysicalCoresIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where physicalCores is greater than or equal to
        defaultWorkerNodeFiltering(
            "physicalCores.greaterThanOrEqual=" + DEFAULT_PHYSICAL_CORES,
            "physicalCores.greaterThanOrEqual=" + UPDATED_PHYSICAL_CORES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByPhysicalCoresIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where physicalCores is less than or equal to
        defaultWorkerNodeFiltering(
            "physicalCores.lessThanOrEqual=" + DEFAULT_PHYSICAL_CORES,
            "physicalCores.lessThanOrEqual=" + SMALLER_PHYSICAL_CORES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByPhysicalCoresIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where physicalCores is less than
        defaultWorkerNodeFiltering("physicalCores.lessThan=" + UPDATED_PHYSICAL_CORES, "physicalCores.lessThan=" + DEFAULT_PHYSICAL_CORES);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByPhysicalCoresIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where physicalCores is greater than
        defaultWorkerNodeFiltering(
            "physicalCores.greaterThan=" + SMALLER_PHYSICAL_CORES,
            "physicalCores.greaterThan=" + DEFAULT_PHYSICAL_CORES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByLogicalCoresIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where logicalCores equals to
        defaultWorkerNodeFiltering("logicalCores.equals=" + DEFAULT_LOGICAL_CORES, "logicalCores.equals=" + UPDATED_LOGICAL_CORES);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByLogicalCoresIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where logicalCores in
        defaultWorkerNodeFiltering(
            "logicalCores.in=" + DEFAULT_LOGICAL_CORES + "," + UPDATED_LOGICAL_CORES,
            "logicalCores.in=" + UPDATED_LOGICAL_CORES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByLogicalCoresIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where logicalCores is not null
        defaultWorkerNodeFiltering("logicalCores.specified=true", "logicalCores.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByLogicalCoresIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where logicalCores is greater than or equal to
        defaultWorkerNodeFiltering(
            "logicalCores.greaterThanOrEqual=" + DEFAULT_LOGICAL_CORES,
            "logicalCores.greaterThanOrEqual=" + UPDATED_LOGICAL_CORES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByLogicalCoresIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where logicalCores is less than or equal to
        defaultWorkerNodeFiltering(
            "logicalCores.lessThanOrEqual=" + DEFAULT_LOGICAL_CORES,
            "logicalCores.lessThanOrEqual=" + SMALLER_LOGICAL_CORES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByLogicalCoresIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where logicalCores is less than
        defaultWorkerNodeFiltering("logicalCores.lessThan=" + UPDATED_LOGICAL_CORES, "logicalCores.lessThan=" + DEFAULT_LOGICAL_CORES);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByLogicalCoresIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where logicalCores is greater than
        defaultWorkerNodeFiltering(
            "logicalCores.greaterThan=" + SMALLER_LOGICAL_CORES,
            "logicalCores.greaterThan=" + DEFAULT_LOGICAL_CORES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByTotalMemoryBytesIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where totalMemoryBytes equals to
        defaultWorkerNodeFiltering(
            "totalMemoryBytes.equals=" + DEFAULT_TOTAL_MEMORY_BYTES,
            "totalMemoryBytes.equals=" + UPDATED_TOTAL_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByTotalMemoryBytesIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where totalMemoryBytes in
        defaultWorkerNodeFiltering(
            "totalMemoryBytes.in=" + DEFAULT_TOTAL_MEMORY_BYTES + "," + UPDATED_TOTAL_MEMORY_BYTES,
            "totalMemoryBytes.in=" + UPDATED_TOTAL_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByTotalMemoryBytesIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where totalMemoryBytes is not null
        defaultWorkerNodeFiltering("totalMemoryBytes.specified=true", "totalMemoryBytes.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByTotalMemoryBytesIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where totalMemoryBytes is greater than or equal to
        defaultWorkerNodeFiltering(
            "totalMemoryBytes.greaterThanOrEqual=" + DEFAULT_TOTAL_MEMORY_BYTES,
            "totalMemoryBytes.greaterThanOrEqual=" + (DEFAULT_TOTAL_MEMORY_BYTES + 1)
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByTotalMemoryBytesIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where totalMemoryBytes is less than or equal to
        defaultWorkerNodeFiltering(
            "totalMemoryBytes.lessThanOrEqual=" + DEFAULT_TOTAL_MEMORY_BYTES,
            "totalMemoryBytes.lessThanOrEqual=" + SMALLER_TOTAL_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByTotalMemoryBytesIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where totalMemoryBytes is less than
        defaultWorkerNodeFiltering(
            "totalMemoryBytes.lessThan=" + (DEFAULT_TOTAL_MEMORY_BYTES + 1),
            "totalMemoryBytes.lessThan=" + DEFAULT_TOTAL_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByTotalMemoryBytesIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where totalMemoryBytes is greater than
        defaultWorkerNodeFiltering(
            "totalMemoryBytes.greaterThan=" + SMALLER_TOTAL_MEMORY_BYTES,
            "totalMemoryBytes.greaterThan=" + DEFAULT_TOTAL_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByGpuPresentIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where gpuPresent equals to
        defaultWorkerNodeFiltering("gpuPresent.equals=" + DEFAULT_GPU_PRESENT, "gpuPresent.equals=" + UPDATED_GPU_PRESENT);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByGpuPresentIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where gpuPresent in
        defaultWorkerNodeFiltering(
            "gpuPresent.in=" + DEFAULT_GPU_PRESENT + "," + UPDATED_GPU_PRESENT,
            "gpuPresent.in=" + UPDATED_GPU_PRESENT
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByGpuPresentIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where gpuPresent is not null
        defaultWorkerNodeFiltering("gpuPresent.specified=true", "gpuPresent.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByGpuModelIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where gpuModel equals to
        defaultWorkerNodeFiltering("gpuModel.equals=" + DEFAULT_GPU_MODEL, "gpuModel.equals=" + UPDATED_GPU_MODEL);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByGpuModelIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where gpuModel in
        defaultWorkerNodeFiltering("gpuModel.in=" + DEFAULT_GPU_MODEL + "," + UPDATED_GPU_MODEL, "gpuModel.in=" + UPDATED_GPU_MODEL);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByGpuModelIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where gpuModel is not null
        defaultWorkerNodeFiltering("gpuModel.specified=true", "gpuModel.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByGpuModelContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where gpuModel contains
        defaultWorkerNodeFiltering("gpuModel.contains=" + DEFAULT_GPU_MODEL, "gpuModel.contains=" + UPDATED_GPU_MODEL);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByGpuModelNotContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where gpuModel does not contain
        defaultWorkerNodeFiltering("gpuModel.doesNotContain=" + UPDATED_GPU_MODEL, "gpuModel.doesNotContain=" + DEFAULT_GPU_MODEL);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAllocatableCoresIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where allocatableCores equals to
        defaultWorkerNodeFiltering(
            "allocatableCores.equals=" + DEFAULT_ALLOCATABLE_CORES,
            "allocatableCores.equals=" + UPDATED_ALLOCATABLE_CORES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAllocatableCoresIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where allocatableCores in
        defaultWorkerNodeFiltering(
            "allocatableCores.in=" + DEFAULT_ALLOCATABLE_CORES + "," + UPDATED_ALLOCATABLE_CORES,
            "allocatableCores.in=" + UPDATED_ALLOCATABLE_CORES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAllocatableCoresIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where allocatableCores is not null
        defaultWorkerNodeFiltering("allocatableCores.specified=true", "allocatableCores.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAllocatableCoresIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where allocatableCores is greater than or equal to
        defaultWorkerNodeFiltering(
            "allocatableCores.greaterThanOrEqual=" + DEFAULT_ALLOCATABLE_CORES,
            "allocatableCores.greaterThanOrEqual=" + UPDATED_ALLOCATABLE_CORES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAllocatableCoresIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where allocatableCores is less than or equal to
        defaultWorkerNodeFiltering(
            "allocatableCores.lessThanOrEqual=" + DEFAULT_ALLOCATABLE_CORES,
            "allocatableCores.lessThanOrEqual=" + SMALLER_ALLOCATABLE_CORES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAllocatableCoresIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where allocatableCores is less than
        defaultWorkerNodeFiltering(
            "allocatableCores.lessThan=" + UPDATED_ALLOCATABLE_CORES,
            "allocatableCores.lessThan=" + DEFAULT_ALLOCATABLE_CORES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByAllocatableCoresIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where allocatableCores is greater than
        defaultWorkerNodeFiltering(
            "allocatableCores.greaterThan=" + SMALLER_ALLOCATABLE_CORES,
            "allocatableCores.greaterThan=" + DEFAULT_ALLOCATABLE_CORES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByMaxMemoryBytesIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where maxMemoryBytes equals to
        defaultWorkerNodeFiltering(
            "maxMemoryBytes.equals=" + DEFAULT_MAX_MEMORY_BYTES,
            "maxMemoryBytes.equals=" + UPDATED_MAX_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByMaxMemoryBytesIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where maxMemoryBytes in
        defaultWorkerNodeFiltering(
            "maxMemoryBytes.in=" + DEFAULT_MAX_MEMORY_BYTES + "," + UPDATED_MAX_MEMORY_BYTES,
            "maxMemoryBytes.in=" + UPDATED_MAX_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByMaxMemoryBytesIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where maxMemoryBytes is not null
        defaultWorkerNodeFiltering("maxMemoryBytes.specified=true", "maxMemoryBytes.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByMaxMemoryBytesIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where maxMemoryBytes is greater than or equal to
        defaultWorkerNodeFiltering(
            "maxMemoryBytes.greaterThanOrEqual=" + DEFAULT_MAX_MEMORY_BYTES,
            "maxMemoryBytes.greaterThanOrEqual=" + (DEFAULT_MAX_MEMORY_BYTES + 1)
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByMaxMemoryBytesIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where maxMemoryBytes is less than or equal to
        defaultWorkerNodeFiltering(
            "maxMemoryBytes.lessThanOrEqual=" + DEFAULT_MAX_MEMORY_BYTES,
            "maxMemoryBytes.lessThanOrEqual=" + SMALLER_MAX_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByMaxMemoryBytesIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where maxMemoryBytes is less than
        defaultWorkerNodeFiltering(
            "maxMemoryBytes.lessThan=" + (DEFAULT_MAX_MEMORY_BYTES + 1),
            "maxMemoryBytes.lessThan=" + DEFAULT_MAX_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByMaxMemoryBytesIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where maxMemoryBytes is greater than
        defaultWorkerNodeFiltering(
            "maxMemoryBytes.greaterThan=" + SMALLER_MAX_MEMORY_BYTES,
            "maxMemoryBytes.greaterThan=" + DEFAULT_MAX_MEMORY_BYTES
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByMaxInstructionBudgetIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where maxInstructionBudget equals to
        defaultWorkerNodeFiltering(
            "maxInstructionBudget.equals=" + DEFAULT_MAX_INSTRUCTION_BUDGET,
            "maxInstructionBudget.equals=" + UPDATED_MAX_INSTRUCTION_BUDGET
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByMaxInstructionBudgetIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where maxInstructionBudget in
        defaultWorkerNodeFiltering(
            "maxInstructionBudget.in=" + DEFAULT_MAX_INSTRUCTION_BUDGET + "," + UPDATED_MAX_INSTRUCTION_BUDGET,
            "maxInstructionBudget.in=" + UPDATED_MAX_INSTRUCTION_BUDGET
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByMaxInstructionBudgetIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where maxInstructionBudget is not null
        defaultWorkerNodeFiltering("maxInstructionBudget.specified=true", "maxInstructionBudget.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByMaxInstructionBudgetIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where maxInstructionBudget is greater than or equal to
        defaultWorkerNodeFiltering(
            "maxInstructionBudget.greaterThanOrEqual=" + DEFAULT_MAX_INSTRUCTION_BUDGET,
            "maxInstructionBudget.greaterThanOrEqual=" + (DEFAULT_MAX_INSTRUCTION_BUDGET + 1)
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByMaxInstructionBudgetIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where maxInstructionBudget is less than or equal to
        defaultWorkerNodeFiltering(
            "maxInstructionBudget.lessThanOrEqual=" + DEFAULT_MAX_INSTRUCTION_BUDGET,
            "maxInstructionBudget.lessThanOrEqual=" + SMALLER_MAX_INSTRUCTION_BUDGET
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByMaxInstructionBudgetIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where maxInstructionBudget is less than
        defaultWorkerNodeFiltering(
            "maxInstructionBudget.lessThan=" + (DEFAULT_MAX_INSTRUCTION_BUDGET + 1),
            "maxInstructionBudget.lessThan=" + DEFAULT_MAX_INSTRUCTION_BUDGET
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByMaxInstructionBudgetIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where maxInstructionBudget is greater than
        defaultWorkerNodeFiltering(
            "maxInstructionBudget.greaterThan=" + SMALLER_MAX_INSTRUCTION_BUDGET,
            "maxInstructionBudget.greaterThan=" + DEFAULT_MAX_INSTRUCTION_BUDGET
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByDrainingIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where draining equals to
        defaultWorkerNodeFiltering("draining.equals=" + DEFAULT_DRAINING, "draining.equals=" + UPDATED_DRAINING);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByDrainingIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where draining in
        defaultWorkerNodeFiltering("draining.in=" + DEFAULT_DRAINING + "," + UPDATED_DRAINING, "draining.in=" + UPDATED_DRAINING);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByDrainingIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where draining is not null
        defaultWorkerNodeFiltering("draining.specified=true", "draining.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByActiveSessionIdIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where activeSessionId equals to
        defaultWorkerNodeFiltering(
            "activeSessionId.equals=" + DEFAULT_ACTIVE_SESSION_ID,
            "activeSessionId.equals=" + UPDATED_ACTIVE_SESSION_ID
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByActiveSessionIdIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where activeSessionId in
        defaultWorkerNodeFiltering(
            "activeSessionId.in=" + DEFAULT_ACTIVE_SESSION_ID + "," + UPDATED_ACTIVE_SESSION_ID,
            "activeSessionId.in=" + UPDATED_ACTIVE_SESSION_ID
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByActiveSessionIdIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where activeSessionId is not null
        defaultWorkerNodeFiltering("activeSessionId.specified=true", "activeSessionId.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByActiveSessionIdContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where activeSessionId contains
        defaultWorkerNodeFiltering(
            "activeSessionId.contains=" + DEFAULT_ACTIVE_SESSION_ID,
            "activeSessionId.contains=" + UPDATED_ACTIVE_SESSION_ID
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByActiveSessionIdNotContainsSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where activeSessionId does not contain
        defaultWorkerNodeFiltering(
            "activeSessionId.doesNotContain=" + UPDATED_ACTIVE_SESSION_ID,
            "activeSessionId.doesNotContain=" + DEFAULT_ACTIVE_SESSION_ID
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByEnrollmentExpiresAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where enrollmentExpiresAt equals to
        defaultWorkerNodeFiltering(
            "enrollmentExpiresAt.equals=" + DEFAULT_ENROLLMENT_EXPIRES_AT,
            "enrollmentExpiresAt.equals=" + UPDATED_ENROLLMENT_EXPIRES_AT
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByEnrollmentExpiresAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where enrollmentExpiresAt in
        defaultWorkerNodeFiltering(
            "enrollmentExpiresAt.in=" + DEFAULT_ENROLLMENT_EXPIRES_AT + "," + UPDATED_ENROLLMENT_EXPIRES_AT,
            "enrollmentExpiresAt.in=" + UPDATED_ENROLLMENT_EXPIRES_AT
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByEnrollmentExpiresAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where enrollmentExpiresAt is not null
        defaultWorkerNodeFiltering("enrollmentExpiresAt.specified=true", "enrollmentExpiresAt.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByLastSeenAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where lastSeenAt equals to
        defaultWorkerNodeFiltering("lastSeenAt.equals=" + DEFAULT_LAST_SEEN_AT, "lastSeenAt.equals=" + UPDATED_LAST_SEEN_AT);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByLastSeenAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where lastSeenAt in
        defaultWorkerNodeFiltering(
            "lastSeenAt.in=" + DEFAULT_LAST_SEEN_AT + "," + UPDATED_LAST_SEEN_AT,
            "lastSeenAt.in=" + UPDATED_LAST_SEEN_AT
        );
    }

    @Test
    @Transactional
    void getAllWorkerNodesByLastSeenAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where lastSeenAt is not null
        defaultWorkerNodeFiltering("lastSeenAt.specified=true", "lastSeenAt.specified=false");
    }

    @Test
    @Transactional
    void getAllWorkerNodesByCreatedAtIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where createdAt equals to
        defaultWorkerNodeFiltering("createdAt.equals=" + DEFAULT_CREATED_AT, "createdAt.equals=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByCreatedAtIsInShouldWork() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where createdAt in
        defaultWorkerNodeFiltering("createdAt.in=" + DEFAULT_CREATED_AT + "," + UPDATED_CREATED_AT, "createdAt.in=" + UPDATED_CREATED_AT);
    }

    @Test
    @Transactional
    void getAllWorkerNodesByCreatedAtIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        // Get all the workerNodeList where createdAt is not null
        defaultWorkerNodeFiltering("createdAt.specified=true", "createdAt.specified=false");
    }

    private void defaultWorkerNodeFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultWorkerNodeShouldBeFound(shouldBeFound);
        defaultWorkerNodeShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultWorkerNodeShouldBeFound(String filter) throws Exception {
        restWorkerNodeMockMvc
            .perform(get(ENTITY_API_URL + "?sort=workerId,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].workerId").value(hasItem(workerNode.getWorkerId())))
            .andExpect(jsonPath("$.[*].enrollmentId").value(hasItem(DEFAULT_ENROLLMENT_ID)))
            .andExpect(jsonPath("$.[*].hostname").value(hasItem(DEFAULT_HOSTNAME)))
            .andExpect(jsonPath("$.[*].os").value(hasItem(DEFAULT_OS.toString())))
            .andExpect(jsonPath("$.[*].arch").value(hasItem(DEFAULT_ARCH.toString())))
            .andExpect(jsonPath("$.[*].agentVersion").value(hasItem(DEFAULT_AGENT_VERSION)))
            .andExpect(jsonPath("$.[*].ed25519Pubkey").value(hasItem(DEFAULT_ED_25519_PUBKEY)))
            .andExpect(jsonPath("$.[*].fingerprint").value(hasItem(DEFAULT_FINGERPRINT)))
            .andExpect(jsonPath("$.[*].visualCode").value(hasItem(DEFAULT_VISUAL_CODE)))
            .andExpect(jsonPath("$.[*].secretHash").value(hasItem(DEFAULT_SECRET_HASH)))
            .andExpect(jsonPath("$.[*].state").value(hasItem(DEFAULT_STATE.toString())))
            .andExpect(jsonPath("$.[*].assignedCluster").value(hasItem(DEFAULT_ASSIGNED_CLUSTER)))
            .andExpect(jsonPath("$.[*].sourceIp").value(hasItem(DEFAULT_SOURCE_IP)))
            .andExpect(jsonPath("$.[*].physicalCores").value(hasItem(DEFAULT_PHYSICAL_CORES)))
            .andExpect(jsonPath("$.[*].logicalCores").value(hasItem(DEFAULT_LOGICAL_CORES)))
            .andExpect(jsonPath("$.[*].totalMemoryBytes").value(hasItem(DEFAULT_TOTAL_MEMORY_BYTES.intValue())))
            .andExpect(jsonPath("$.[*].gpuPresent").value(hasItem(DEFAULT_GPU_PRESENT)))
            .andExpect(jsonPath("$.[*].gpuModel").value(hasItem(DEFAULT_GPU_MODEL)))
            .andExpect(jsonPath("$.[*].allocatableCores").value(hasItem(DEFAULT_ALLOCATABLE_CORES)))
            .andExpect(jsonPath("$.[*].maxMemoryBytes").value(hasItem(DEFAULT_MAX_MEMORY_BYTES.intValue())))
            .andExpect(jsonPath("$.[*].maxInstructionBudget").value(hasItem(DEFAULT_MAX_INSTRUCTION_BUDGET.intValue())))
            .andExpect(jsonPath("$.[*].draining").value(hasItem(DEFAULT_DRAINING)))
            .andExpect(jsonPath("$.[*].activeSessionId").value(hasItem(DEFAULT_ACTIVE_SESSION_ID)))
            .andExpect(jsonPath("$.[*].enrollmentExpiresAt").value(hasItem(DEFAULT_ENROLLMENT_EXPIRES_AT.toString())))
            .andExpect(jsonPath("$.[*].lastSeenAt").value(hasItem(DEFAULT_LAST_SEEN_AT.toString())))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())));

        // Check, that the count call also returns 1
        restWorkerNodeMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=workerId,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultWorkerNodeShouldNotBeFound(String filter) throws Exception {
        restWorkerNodeMockMvc
            .perform(get(ENTITY_API_URL + "?sort=workerId,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restWorkerNodeMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=workerId,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingWorkerNode() throws Exception {
        // Get the workerNode
        restWorkerNodeMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingWorkerNode() throws Exception {
        // Initialize the database
        workerNode.setWorkerId(UUID.randomUUID().toString());
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the workerNode
        WorkerNode updatedWorkerNode = workerNodeRepository.findById(workerNode.getWorkerId()).orElseThrow();
        // Disconnect from session so that the updates on updatedWorkerNode are not directly saved in db
        em.detach(updatedWorkerNode);
        updatedWorkerNode
            .enrollmentId(UPDATED_ENROLLMENT_ID)
            .hostname(UPDATED_HOSTNAME)
            .os(UPDATED_OS)
            .arch(UPDATED_ARCH)
            .agentVersion(UPDATED_AGENT_VERSION)
            .ed25519Pubkey(UPDATED_ED_25519_PUBKEY)
            .fingerprint(UPDATED_FINGERPRINT)
            .visualCode(UPDATED_VISUAL_CODE)
            .secretHash(UPDATED_SECRET_HASH)
            .state(UPDATED_STATE)
            .assignedCluster(UPDATED_ASSIGNED_CLUSTER)
            .sourceIp(UPDATED_SOURCE_IP)
            .physicalCores(UPDATED_PHYSICAL_CORES)
            .logicalCores(UPDATED_LOGICAL_CORES)
            .totalMemoryBytes(UPDATED_TOTAL_MEMORY_BYTES)
            .gpuPresent(UPDATED_GPU_PRESENT)
            .gpuModel(UPDATED_GPU_MODEL)
            .allocatableCores(UPDATED_ALLOCATABLE_CORES)
            .maxMemoryBytes(UPDATED_MAX_MEMORY_BYTES)
            .maxInstructionBudget(UPDATED_MAX_INSTRUCTION_BUDGET)
            .draining(UPDATED_DRAINING)
            .activeSessionId(UPDATED_ACTIVE_SESSION_ID)
            .enrollmentExpiresAt(UPDATED_ENROLLMENT_EXPIRES_AT)
            .lastSeenAt(UPDATED_LAST_SEEN_AT)
            .createdAt(UPDATED_CREATED_AT);
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(updatedWorkerNode);

        restWorkerNodeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, workerNodeDTO.getWorkerId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(workerNodeDTO))
            )
            .andExpect(status().isOk());

        // Validate the WorkerNode in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedWorkerNodeToMatchAllProperties(updatedWorkerNode);
    }

    @Test
    @Transactional
    void putNonExistingWorkerNode() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workerNode.setWorkerId(UUID.randomUUID().toString());

        // Create the WorkerNode
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restWorkerNodeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, workerNodeDTO.getWorkerId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(workerNodeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the WorkerNode in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchWorkerNode() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workerNode.setWorkerId(UUID.randomUUID().toString());

        // Create the WorkerNode
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restWorkerNodeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(workerNodeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the WorkerNode in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamWorkerNode() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workerNode.setWorkerId(UUID.randomUUID().toString());

        // Create the WorkerNode
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restWorkerNodeMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the WorkerNode in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateWorkerNodeWithPatch() throws Exception {
        // Initialize the database
        workerNode.setWorkerId(UUID.randomUUID().toString());
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the workerNode using partial update
        WorkerNode partialUpdatedWorkerNode = new WorkerNode();
        partialUpdatedWorkerNode.setWorkerId(workerNode.getWorkerId());

        partialUpdatedWorkerNode
            .enrollmentId(UPDATED_ENROLLMENT_ID)
            .hostname(UPDATED_HOSTNAME)
            .agentVersion(UPDATED_AGENT_VERSION)
            .fingerprint(UPDATED_FINGERPRINT)
            .secretHash(UPDATED_SECRET_HASH)
            .state(UPDATED_STATE)
            .assignedCluster(UPDATED_ASSIGNED_CLUSTER)
            .sourceIp(UPDATED_SOURCE_IP)
            .physicalCores(UPDATED_PHYSICAL_CORES)
            .totalMemoryBytes(UPDATED_TOTAL_MEMORY_BYTES)
            .gpuPresent(UPDATED_GPU_PRESENT)
            .gpuModel(UPDATED_GPU_MODEL)
            .allocatableCores(UPDATED_ALLOCATABLE_CORES)
            .maxMemoryBytes(UPDATED_MAX_MEMORY_BYTES)
            .maxInstructionBudget(UPDATED_MAX_INSTRUCTION_BUDGET)
            .enrollmentExpiresAt(UPDATED_ENROLLMENT_EXPIRES_AT)
            .lastSeenAt(UPDATED_LAST_SEEN_AT);

        restWorkerNodeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedWorkerNode.getWorkerId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedWorkerNode))
            )
            .andExpect(status().isOk());

        // Validate the WorkerNode in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertWorkerNodeUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedWorkerNode, workerNode),
            getPersistedWorkerNode(workerNode)
        );
    }

    @Test
    @Transactional
    void fullUpdateWorkerNodeWithPatch() throws Exception {
        // Initialize the database
        workerNode.setWorkerId(UUID.randomUUID().toString());
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the workerNode using partial update
        WorkerNode partialUpdatedWorkerNode = new WorkerNode();
        partialUpdatedWorkerNode.setWorkerId(workerNode.getWorkerId());

        partialUpdatedWorkerNode
            .enrollmentId(UPDATED_ENROLLMENT_ID)
            .hostname(UPDATED_HOSTNAME)
            .os(UPDATED_OS)
            .arch(UPDATED_ARCH)
            .agentVersion(UPDATED_AGENT_VERSION)
            .ed25519Pubkey(UPDATED_ED_25519_PUBKEY)
            .fingerprint(UPDATED_FINGERPRINT)
            .visualCode(UPDATED_VISUAL_CODE)
            .secretHash(UPDATED_SECRET_HASH)
            .state(UPDATED_STATE)
            .assignedCluster(UPDATED_ASSIGNED_CLUSTER)
            .sourceIp(UPDATED_SOURCE_IP)
            .physicalCores(UPDATED_PHYSICAL_CORES)
            .logicalCores(UPDATED_LOGICAL_CORES)
            .totalMemoryBytes(UPDATED_TOTAL_MEMORY_BYTES)
            .gpuPresent(UPDATED_GPU_PRESENT)
            .gpuModel(UPDATED_GPU_MODEL)
            .allocatableCores(UPDATED_ALLOCATABLE_CORES)
            .maxMemoryBytes(UPDATED_MAX_MEMORY_BYTES)
            .maxInstructionBudget(UPDATED_MAX_INSTRUCTION_BUDGET)
            .draining(UPDATED_DRAINING)
            .activeSessionId(UPDATED_ACTIVE_SESSION_ID)
            .enrollmentExpiresAt(UPDATED_ENROLLMENT_EXPIRES_AT)
            .lastSeenAt(UPDATED_LAST_SEEN_AT)
            .createdAt(UPDATED_CREATED_AT);

        restWorkerNodeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedWorkerNode.getWorkerId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedWorkerNode))
            )
            .andExpect(status().isOk());

        // Validate the WorkerNode in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertWorkerNodeUpdatableFieldsEquals(partialUpdatedWorkerNode, getPersistedWorkerNode(partialUpdatedWorkerNode));
    }

    @Test
    @Transactional
    void patchNonExistingWorkerNode() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workerNode.setWorkerId(UUID.randomUUID().toString());

        // Create the WorkerNode
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restWorkerNodeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, workerNodeDTO.getWorkerId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(workerNodeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the WorkerNode in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchWorkerNode() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workerNode.setWorkerId(UUID.randomUUID().toString());

        // Create the WorkerNode
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restWorkerNodeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(workerNodeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the WorkerNode in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamWorkerNode() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workerNode.setWorkerId(UUID.randomUUID().toString());

        // Create the WorkerNode
        WorkerNodeDTO workerNodeDTO = workerNodeMapper.toDto(workerNode);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restWorkerNodeMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(workerNodeDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the WorkerNode in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteWorkerNode() throws Exception {
        // Initialize the database
        workerNode.setWorkerId(UUID.randomUUID().toString());
        insertedWorkerNode = workerNodeRepository.saveAndFlush(workerNode);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the workerNode
        restWorkerNodeMockMvc
            .perform(delete(ENTITY_API_URL_ID, workerNode.getWorkerId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return workerNodeRepository.count();
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

    protected WorkerNode getPersistedWorkerNode(WorkerNode workerNode) {
        return workerNodeRepository.findById(workerNode.getWorkerId()).orElseThrow();
    }

    protected void assertPersistedWorkerNodeToMatchAllProperties(WorkerNode expectedWorkerNode) {
        assertWorkerNodeAllPropertiesEquals(expectedWorkerNode, getPersistedWorkerNode(expectedWorkerNode));
    }

    protected void assertPersistedWorkerNodeToMatchUpdatableProperties(WorkerNode expectedWorkerNode) {
        assertWorkerNodeAllUpdatablePropertiesEquals(expectedWorkerNode, getPersistedWorkerNode(expectedWorkerNode));
    }
}
