package dev.legatus.server.web.rest;

import static dev.legatus.server.domain.AuditLogAsserts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.AuditLog;
import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.repository.AuditLogRepository;
import dev.legatus.server.service.mapper.AuditLogMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
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
 * Integration tests for the {@link AuditLogResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class AuditLogResourceIT {

    private static final AtomicLong sequenceGenerator = new AtomicLong(100000L);

    private static final Instant DEFAULT_TIMESTAMP = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_TIMESTAMP = Instant.ofEpochMilli(1703883753520L);

    private static final String DEFAULT_ACTOR_ID = "AAAAAAAAAA";
    private static final String UPDATED_ACTOR_ID = "BBBBBBBBBB";

    private static final String DEFAULT_ACTOR_ROLE = "AAAAAAAAAA";
    private static final String UPDATED_ACTOR_ROLE = "BBBBBBBBBB";

    private static final String DEFAULT_EVENT_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_EVENT_TYPE = "BBBBBBBBBB";

    private static final String DEFAULT_TARGET_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_TARGET_TYPE = "BBBBBBBBBB";

    private static final String DEFAULT_TARGET_ID = "AAAAAAAAAA";
    private static final String UPDATED_TARGET_ID = "BBBBBBBBBB";

    private static final AuditActionStatus DEFAULT_ACTION_STATUS = AuditActionStatus.SUCCESS;
    private static final AuditActionStatus UPDATED_ACTION_STATUS = AuditActionStatus.DENIED;

    private static final String DEFAULT_METADATA_JSON = "AAAAAAAAAA";
    private static final String UPDATED_METADATA_JSON = "BBBBBBBBBB";

    private static final String DEFAULT_SOURCE_IP = "AAAAAAAAAA";
    private static final String UPDATED_SOURCE_IP = "BBBBBBBBBB";

    private static final String DEFAULT_PREV_RECORD_HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String UPDATED_PREV_RECORD_HASH = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";

    private static final String DEFAULT_RECORD_HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String UPDATED_RECORD_HASH = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";

    private static final String DEFAULT_COMPLIANCE_FLAG = "AAAAAAAAAA";
    private static final String UPDATED_COMPLIANCE_FLAG = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/audit-logs";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{eventId}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restAuditLogMockMvc;

    private AuditLog auditLog;

    private AuditLog insertedAuditLog;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static AuditLog createEntity() {
        return new AuditLog()
            .sequenceNum(sequenceGenerator.incrementAndGet())
            .timestamp(DEFAULT_TIMESTAMP)
            .actorId(DEFAULT_ACTOR_ID)
            .actorRole(DEFAULT_ACTOR_ROLE)
            .eventType(DEFAULT_EVENT_TYPE)
            .targetType(DEFAULT_TARGET_TYPE)
            .targetId(DEFAULT_TARGET_ID)
            .actionStatus(DEFAULT_ACTION_STATUS)
            .metadataJson(DEFAULT_METADATA_JSON)
            .sourceIp(DEFAULT_SOURCE_IP)
            .prevRecordHash(DEFAULT_PREV_RECORD_HASH)
            .recordHash(DEFAULT_RECORD_HASH)
            .complianceFlag(DEFAULT_COMPLIANCE_FLAG);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static AuditLog createUpdatedEntity() {
        return new AuditLog()
            .sequenceNum(sequenceGenerator.incrementAndGet())
            .timestamp(UPDATED_TIMESTAMP)
            .actorId(UPDATED_ACTOR_ID)
            .actorRole(UPDATED_ACTOR_ROLE)
            .eventType(UPDATED_EVENT_TYPE)
            .targetType(UPDATED_TARGET_TYPE)
            .targetId(UPDATED_TARGET_ID)
            .actionStatus(UPDATED_ACTION_STATUS)
            .metadataJson(UPDATED_METADATA_JSON)
            .sourceIp(UPDATED_SOURCE_IP)
            .prevRecordHash(UPDATED_PREV_RECORD_HASH)
            .recordHash(UPDATED_RECORD_HASH)
            .complianceFlag(UPDATED_COMPLIANCE_FLAG);
    }

    @BeforeEach
    void initTest() {
        auditLog = createEntity();
    }

    @AfterEach
    void cleanup() {
        insertedAuditLog = null;
    }

    @Test
    @Transactional
    void getAllAuditLogs() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList
        restAuditLogMockMvc
            .perform(get(ENTITY_API_URL + "?sort=eventId,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].eventId").value(hasItem(auditLog.getEventId().toString())))
            .andExpect(jsonPath("$.[*].sequenceNum").value(hasItem(auditLog.getSequenceNum().intValue())))
            .andExpect(jsonPath("$.[*].timestamp").value(hasItem(DEFAULT_TIMESTAMP.toString())))
            .andExpect(jsonPath("$.[*].actorId").value(hasItem(DEFAULT_ACTOR_ID)))
            .andExpect(jsonPath("$.[*].actorRole").value(hasItem(DEFAULT_ACTOR_ROLE)))
            .andExpect(jsonPath("$.[*].eventType").value(hasItem(DEFAULT_EVENT_TYPE)))
            .andExpect(jsonPath("$.[*].targetType").value(hasItem(DEFAULT_TARGET_TYPE)))
            .andExpect(jsonPath("$.[*].targetId").value(hasItem(DEFAULT_TARGET_ID)))
            .andExpect(jsonPath("$.[*].actionStatus").value(hasItem(DEFAULT_ACTION_STATUS.toString())))
            .andExpect(jsonPath("$.[*].metadataJson").value(hasItem(DEFAULT_METADATA_JSON)))
            .andExpect(jsonPath("$.[*].sourceIp").value(hasItem(DEFAULT_SOURCE_IP)))
            .andExpect(jsonPath("$.[*].prevRecordHash").value(hasItem(DEFAULT_PREV_RECORD_HASH)))
            .andExpect(jsonPath("$.[*].recordHash").value(hasItem(DEFAULT_RECORD_HASH)))
            .andExpect(jsonPath("$.[*].complianceFlag").value(hasItem(DEFAULT_COMPLIANCE_FLAG)));
    }

    @Test
    @Transactional
    void getAuditLog() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get the auditLog
        restAuditLogMockMvc
            .perform(get(ENTITY_API_URL_ID, auditLog.getEventId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.eventId").value(auditLog.getEventId().toString()))
            .andExpect(jsonPath("$.sequenceNum").value(auditLog.getSequenceNum().intValue()))
            .andExpect(jsonPath("$.timestamp").value(DEFAULT_TIMESTAMP.toString()))
            .andExpect(jsonPath("$.actorId").value(DEFAULT_ACTOR_ID))
            .andExpect(jsonPath("$.actorRole").value(DEFAULT_ACTOR_ROLE))
            .andExpect(jsonPath("$.eventType").value(DEFAULT_EVENT_TYPE))
            .andExpect(jsonPath("$.targetType").value(DEFAULT_TARGET_TYPE))
            .andExpect(jsonPath("$.targetId").value(DEFAULT_TARGET_ID))
            .andExpect(jsonPath("$.actionStatus").value(DEFAULT_ACTION_STATUS.toString()))
            .andExpect(jsonPath("$.metadataJson").value(DEFAULT_METADATA_JSON))
            .andExpect(jsonPath("$.sourceIp").value(DEFAULT_SOURCE_IP))
            .andExpect(jsonPath("$.prevRecordHash").value(DEFAULT_PREV_RECORD_HASH))
            .andExpect(jsonPath("$.recordHash").value(DEFAULT_RECORD_HASH))
            .andExpect(jsonPath("$.complianceFlag").value(DEFAULT_COMPLIANCE_FLAG));
    }

    @Test
    @Transactional
    void getAuditLogsByIdFiltering() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        UUID id = auditLog.getEventId();

        defaultAuditLogFiltering("eventId.equals=" + id, "eventId.notEquals=" + id);
    }

    @Test
    @Transactional
    void getAllAuditLogsBySequenceNumIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        Long seq = auditLog.getSequenceNum();
        // Get all the auditLogList where sequenceNum equals to
        defaultAuditLogFiltering("sequenceNum.equals=" + seq, "sequenceNum.equals=" + (seq + 100));
    }

    @Test
    @Transactional
    void getAllAuditLogsBySequenceNumIsInShouldWork() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        Long seq = auditLog.getSequenceNum();
        // Get all the auditLogList where sequenceNum in
        defaultAuditLogFiltering(
            "sequenceNum.in=" + seq + "," + (seq + 100),
            "sequenceNum.in=" + (seq + 100)
        );
    }

    @Test
    @Transactional
    void getAllAuditLogsBySequenceNumIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where sequenceNum is not null
        defaultAuditLogFiltering("sequenceNum.specified=true", "sequenceNum.specified=false");
    }

    @Test
    @Transactional
    void getAllAuditLogsBySequenceNumIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        Long seq = auditLog.getSequenceNum();
        // Get all the auditLogList where sequenceNum is greater than or equal to
        defaultAuditLogFiltering(
            "sequenceNum.greaterThanOrEqual=" + seq,
            "sequenceNum.greaterThanOrEqual=" + (seq + 100)
        );
    }

    @Test
    @Transactional
    void getAllAuditLogsBySequenceNumIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        Long seq = auditLog.getSequenceNum();
        // Get all the auditLogList where sequenceNum is less than or equal to
        defaultAuditLogFiltering(
            "sequenceNum.lessThanOrEqual=" + seq,
            "sequenceNum.lessThanOrEqual=" + (seq - 1)
        );
    }

    @Test
    @Transactional
    void getAllAuditLogsBySequenceNumIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        Long seq = auditLog.getSequenceNum();
        // Get all the auditLogList where sequenceNum is less than
        defaultAuditLogFiltering("sequenceNum.lessThan=" + (seq + 100), "sequenceNum.lessThan=" + seq);
    }

    @Test
    @Transactional
    void getAllAuditLogsBySequenceNumIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        Long seq = auditLog.getSequenceNum();
        // Get all the auditLogList where sequenceNum is greater than
        defaultAuditLogFiltering("sequenceNum.greaterThan=" + (seq - 1), "sequenceNum.greaterThan=" + seq);
    }

    @Test
    @Transactional
    void getAllAuditLogsByTimestampIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where timestamp equals to
        defaultAuditLogFiltering("timestamp.equals=" + DEFAULT_TIMESTAMP, "timestamp.equals=" + UPDATED_TIMESTAMP);
    }

    @Test
    @Transactional
    void getAllAuditLogsByTimestampIsInShouldWork() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where timestamp in
        defaultAuditLogFiltering("timestamp.in=" + DEFAULT_TIMESTAMP + "," + UPDATED_TIMESTAMP, "timestamp.in=" + UPDATED_TIMESTAMP);
    }

    @Test
    @Transactional
    void getAllAuditLogsByTimestampIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where timestamp is not null
        defaultAuditLogFiltering("timestamp.specified=true", "timestamp.specified=false");
    }

    @Test
    @Transactional
    void getAllAuditLogsByActorIdIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where actorId equals to
        defaultAuditLogFiltering("actorId.equals=" + DEFAULT_ACTOR_ID, "actorId.equals=" + UPDATED_ACTOR_ID);
    }

    @Test
    @Transactional
    void getAllAuditLogsByActorIdIsInShouldWork() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where actorId in
        defaultAuditLogFiltering("actorId.in=" + DEFAULT_ACTOR_ID + "," + UPDATED_ACTOR_ID, "actorId.in=" + UPDATED_ACTOR_ID);
    }

    @Test
    @Transactional
    void getAllAuditLogsByActorIdIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where actorId is not null
        defaultAuditLogFiltering("actorId.specified=true", "actorId.specified=false");
    }

    @Test
    @Transactional
    void getAllAuditLogsByActorIdContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where actorId contains
        defaultAuditLogFiltering("actorId.contains=" + DEFAULT_ACTOR_ID, "actorId.contains=" + UPDATED_ACTOR_ID);
    }

    @Test
    @Transactional
    void getAllAuditLogsByActorIdNotContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where actorId does not contain
        defaultAuditLogFiltering("actorId.doesNotContain=" + UPDATED_ACTOR_ID, "actorId.doesNotContain=" + DEFAULT_ACTOR_ID);
    }

    @Test
    @Transactional
    void getAllAuditLogsByActorRoleIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where actorRole equals to
        defaultAuditLogFiltering("actorRole.equals=" + DEFAULT_ACTOR_ROLE, "actorRole.equals=" + UPDATED_ACTOR_ROLE);
    }

    @Test
    @Transactional
    void getAllAuditLogsByActorRoleIsInShouldWork() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where actorRole in
        defaultAuditLogFiltering("actorRole.in=" + DEFAULT_ACTOR_ROLE + "," + UPDATED_ACTOR_ROLE, "actorRole.in=" + UPDATED_ACTOR_ROLE);
    }

    @Test
    @Transactional
    void getAllAuditLogsByActorRoleIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where actorRole is not null
        defaultAuditLogFiltering("actorRole.specified=true", "actorRole.specified=false");
    }

    @Test
    @Transactional
    void getAllAuditLogsByActorRoleContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where actorRole contains
        defaultAuditLogFiltering("actorRole.contains=" + DEFAULT_ACTOR_ROLE, "actorRole.contains=" + UPDATED_ACTOR_ROLE);
    }

    @Test
    @Transactional
    void getAllAuditLogsByActorRoleNotContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where actorRole does not contain
        defaultAuditLogFiltering("actorRole.doesNotContain=" + UPDATED_ACTOR_ROLE, "actorRole.doesNotContain=" + DEFAULT_ACTOR_ROLE);
    }

    @Test
    @Transactional
    void getAllAuditLogsByEventTypeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where eventType equals to
        defaultAuditLogFiltering("eventType.equals=" + DEFAULT_EVENT_TYPE, "eventType.equals=" + UPDATED_EVENT_TYPE);
    }

    @Test
    @Transactional
    void getAllAuditLogsByEventTypeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where eventType in
        defaultAuditLogFiltering("eventType.in=" + DEFAULT_EVENT_TYPE + "," + UPDATED_EVENT_TYPE, "eventType.in=" + UPDATED_EVENT_TYPE);
    }

    @Test
    @Transactional
    void getAllAuditLogsByEventTypeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where eventType is not null
        defaultAuditLogFiltering("eventType.specified=true", "eventType.specified=false");
    }

    @Test
    @Transactional
    void getAllAuditLogsByEventTypeContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where eventType contains
        defaultAuditLogFiltering("eventType.contains=" + DEFAULT_EVENT_TYPE, "eventType.contains=" + UPDATED_EVENT_TYPE);
    }

    @Test
    @Transactional
    void getAllAuditLogsByEventTypeNotContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where eventType does not contain
        defaultAuditLogFiltering("eventType.doesNotContain=" + UPDATED_EVENT_TYPE, "eventType.doesNotContain=" + DEFAULT_EVENT_TYPE);
    }

    @Test
    @Transactional
    void getAllAuditLogsByTargetTypeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where targetType equals to
        defaultAuditLogFiltering("targetType.equals=" + DEFAULT_TARGET_TYPE, "targetType.equals=" + UPDATED_TARGET_TYPE);
    }

    @Test
    @Transactional
    void getAllAuditLogsByTargetTypeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where targetType in
        defaultAuditLogFiltering(
            "targetType.in=" + DEFAULT_TARGET_TYPE + "," + UPDATED_TARGET_TYPE,
            "targetType.in=" + UPDATED_TARGET_TYPE
        );
    }

    @Test
    @Transactional
    void getAllAuditLogsByTargetTypeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where targetType is not null
        defaultAuditLogFiltering("targetType.specified=true", "targetType.specified=false");
    }

    @Test
    @Transactional
    void getAllAuditLogsByTargetTypeContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where targetType contains
        defaultAuditLogFiltering("targetType.contains=" + DEFAULT_TARGET_TYPE, "targetType.contains=" + UPDATED_TARGET_TYPE);
    }

    @Test
    @Transactional
    void getAllAuditLogsByTargetTypeNotContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where targetType does not contain
        defaultAuditLogFiltering("targetType.doesNotContain=" + UPDATED_TARGET_TYPE, "targetType.doesNotContain=" + DEFAULT_TARGET_TYPE);
    }

    @Test
    @Transactional
    void getAllAuditLogsByTargetIdIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where targetId equals to
        defaultAuditLogFiltering("targetId.equals=" + DEFAULT_TARGET_ID, "targetId.equals=" + UPDATED_TARGET_ID);
    }

    @Test
    @Transactional
    void getAllAuditLogsByTargetIdIsInShouldWork() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where targetId in
        defaultAuditLogFiltering("targetId.in=" + DEFAULT_TARGET_ID + "," + UPDATED_TARGET_ID, "targetId.in=" + UPDATED_TARGET_ID);
    }

    @Test
    @Transactional
    void getAllAuditLogsByTargetIdIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where targetId is not null
        defaultAuditLogFiltering("targetId.specified=true", "targetId.specified=false");
    }

    @Test
    @Transactional
    void getAllAuditLogsByTargetIdContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where targetId contains
        defaultAuditLogFiltering("targetId.contains=" + DEFAULT_TARGET_ID, "targetId.contains=" + UPDATED_TARGET_ID);
    }

    @Test
    @Transactional
    void getAllAuditLogsByTargetIdNotContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where targetId does not contain
        defaultAuditLogFiltering("targetId.doesNotContain=" + UPDATED_TARGET_ID, "targetId.doesNotContain=" + DEFAULT_TARGET_ID);
    }

    @Test
    @Transactional
    void getAllAuditLogsByActionStatusIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where actionStatus equals to
        defaultAuditLogFiltering("actionStatus.equals=" + DEFAULT_ACTION_STATUS, "actionStatus.equals=" + UPDATED_ACTION_STATUS);
    }

    @Test
    @Transactional
    void getAllAuditLogsByActionStatusIsInShouldWork() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where actionStatus in
        defaultAuditLogFiltering(
            "actionStatus.in=" + DEFAULT_ACTION_STATUS + "," + UPDATED_ACTION_STATUS,
            "actionStatus.in=" + UPDATED_ACTION_STATUS
        );
    }

    @Test
    @Transactional
    void getAllAuditLogsByActionStatusIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where actionStatus is not null
        defaultAuditLogFiltering("actionStatus.specified=true", "actionStatus.specified=false");
    }

    @Test
    @Transactional
    void getAllAuditLogsBySourceIpIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where sourceIp equals to
        defaultAuditLogFiltering("sourceIp.equals=" + DEFAULT_SOURCE_IP, "sourceIp.equals=" + UPDATED_SOURCE_IP);
    }

    @Test
    @Transactional
    void getAllAuditLogsBySourceIpIsInShouldWork() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where sourceIp in
        defaultAuditLogFiltering("sourceIp.in=" + DEFAULT_SOURCE_IP + "," + UPDATED_SOURCE_IP, "sourceIp.in=" + UPDATED_SOURCE_IP);
    }

    @Test
    @Transactional
    void getAllAuditLogsBySourceIpIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where sourceIp is not null
        defaultAuditLogFiltering("sourceIp.specified=true", "sourceIp.specified=false");
    }

    @Test
    @Transactional
    void getAllAuditLogsBySourceIpContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where sourceIp contains
        defaultAuditLogFiltering("sourceIp.contains=" + DEFAULT_SOURCE_IP, "sourceIp.contains=" + UPDATED_SOURCE_IP);
    }

    @Test
    @Transactional
    void getAllAuditLogsBySourceIpNotContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where sourceIp does not contain
        defaultAuditLogFiltering("sourceIp.doesNotContain=" + UPDATED_SOURCE_IP, "sourceIp.doesNotContain=" + DEFAULT_SOURCE_IP);
    }

    @Test
    @Transactional
    void getAllAuditLogsByPrevRecordHashIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where prevRecordHash equals to
        defaultAuditLogFiltering("prevRecordHash.equals=" + DEFAULT_PREV_RECORD_HASH, "prevRecordHash.equals=" + UPDATED_PREV_RECORD_HASH);
    }

    @Test
    @Transactional
    void getAllAuditLogsByPrevRecordHashIsInShouldWork() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where prevRecordHash in
        defaultAuditLogFiltering(
            "prevRecordHash.in=" + DEFAULT_PREV_RECORD_HASH + "," + UPDATED_PREV_RECORD_HASH,
            "prevRecordHash.in=" + UPDATED_PREV_RECORD_HASH
        );
    }

    @Test
    @Transactional
    void getAllAuditLogsByPrevRecordHashIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where prevRecordHash is not null
        defaultAuditLogFiltering("prevRecordHash.specified=true", "prevRecordHash.specified=false");
    }

    @Test
    @Transactional
    void getAllAuditLogsByPrevRecordHashContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where prevRecordHash contains
        defaultAuditLogFiltering(
            "prevRecordHash.contains=" + DEFAULT_PREV_RECORD_HASH,
            "prevRecordHash.contains=" + UPDATED_PREV_RECORD_HASH
        );
    }

    @Test
    @Transactional
    void getAllAuditLogsByPrevRecordHashNotContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where prevRecordHash does not contain
        defaultAuditLogFiltering(
            "prevRecordHash.doesNotContain=" + UPDATED_PREV_RECORD_HASH,
            "prevRecordHash.doesNotContain=" + DEFAULT_PREV_RECORD_HASH
        );
    }

    @Test
    @Transactional
    void getAllAuditLogsByRecordHashIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where recordHash equals to
        defaultAuditLogFiltering("recordHash.equals=" + DEFAULT_RECORD_HASH, "recordHash.equals=" + UPDATED_RECORD_HASH);
    }

    @Test
    @Transactional
    void getAllAuditLogsByRecordHashIsInShouldWork() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where recordHash in
        defaultAuditLogFiltering(
            "recordHash.in=" + DEFAULT_RECORD_HASH + "," + UPDATED_RECORD_HASH,
            "recordHash.in=" + UPDATED_RECORD_HASH
        );
    }

    @Test
    @Transactional
    void getAllAuditLogsByRecordHashIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where recordHash is not null
        defaultAuditLogFiltering("recordHash.specified=true", "recordHash.specified=false");
    }

    @Test
    @Transactional
    void getAllAuditLogsByRecordHashContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where recordHash contains
        defaultAuditLogFiltering("recordHash.contains=" + DEFAULT_RECORD_HASH, "recordHash.contains=" + UPDATED_RECORD_HASH);
    }

    @Test
    @Transactional
    void getAllAuditLogsByRecordHashNotContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where recordHash does not contain
        defaultAuditLogFiltering("recordHash.doesNotContain=" + UPDATED_RECORD_HASH, "recordHash.doesNotContain=" + DEFAULT_RECORD_HASH);
    }

    @Test
    @Transactional
    void getAllAuditLogsByComplianceFlagIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where complianceFlag equals to
        defaultAuditLogFiltering("complianceFlag.equals=" + DEFAULT_COMPLIANCE_FLAG, "complianceFlag.equals=" + UPDATED_COMPLIANCE_FLAG);
    }

    @Test
    @Transactional
    void getAllAuditLogsByComplianceFlagIsInShouldWork() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where complianceFlag in
        defaultAuditLogFiltering(
            "complianceFlag.in=" + DEFAULT_COMPLIANCE_FLAG + "," + UPDATED_COMPLIANCE_FLAG,
            "complianceFlag.in=" + UPDATED_COMPLIANCE_FLAG
        );
    }

    @Test
    @Transactional
    void getAllAuditLogsByComplianceFlagIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where complianceFlag is not null
        defaultAuditLogFiltering("complianceFlag.specified=true", "complianceFlag.specified=false");
    }

    @Test
    @Transactional
    void getAllAuditLogsByComplianceFlagContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where complianceFlag contains
        defaultAuditLogFiltering(
            "complianceFlag.contains=" + DEFAULT_COMPLIANCE_FLAG,
            "complianceFlag.contains=" + UPDATED_COMPLIANCE_FLAG
        );
    }

    @Test
    @Transactional
    void getAllAuditLogsByComplianceFlagNotContainsSomething() throws Exception {
        // Initialize the database
        insertedAuditLog = auditLogRepository.saveAndFlush(auditLog);

        // Get all the auditLogList where complianceFlag does not contain
        defaultAuditLogFiltering(
            "complianceFlag.doesNotContain=" + UPDATED_COMPLIANCE_FLAG,
            "complianceFlag.doesNotContain=" + DEFAULT_COMPLIANCE_FLAG
        );
    }

    private void defaultAuditLogFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultAuditLogShouldBeFound(shouldBeFound);
        defaultAuditLogShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultAuditLogShouldBeFound(String filter) throws Exception {
        restAuditLogMockMvc
            .perform(get(ENTITY_API_URL + "?sort=eventId,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].eventId").value(hasItem(auditLog.getEventId().toString())))
            .andExpect(jsonPath("$.[*].sequenceNum").value(hasItem(auditLog.getSequenceNum().intValue())))
            .andExpect(jsonPath("$.[*].timestamp").value(hasItem(DEFAULT_TIMESTAMP.toString())))
            .andExpect(jsonPath("$.[*].actorId").value(hasItem(DEFAULT_ACTOR_ID)))
            .andExpect(jsonPath("$.[*].actorRole").value(hasItem(DEFAULT_ACTOR_ROLE)))
            .andExpect(jsonPath("$.[*].eventType").value(hasItem(DEFAULT_EVENT_TYPE)))
            .andExpect(jsonPath("$.[*].targetType").value(hasItem(DEFAULT_TARGET_TYPE)))
            .andExpect(jsonPath("$.[*].targetId").value(hasItem(DEFAULT_TARGET_ID)))
            .andExpect(jsonPath("$.[*].actionStatus").value(hasItem(DEFAULT_ACTION_STATUS.toString())))
            .andExpect(jsonPath("$.[*].metadataJson").value(hasItem(DEFAULT_METADATA_JSON)))
            .andExpect(jsonPath("$.[*].sourceIp").value(hasItem(DEFAULT_SOURCE_IP)))
            .andExpect(jsonPath("$.[*].prevRecordHash").value(hasItem(DEFAULT_PREV_RECORD_HASH)))
            .andExpect(jsonPath("$.[*].recordHash").value(hasItem(DEFAULT_RECORD_HASH)))
            .andExpect(jsonPath("$.[*].complianceFlag").value(hasItem(DEFAULT_COMPLIANCE_FLAG)));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultAuditLogShouldNotBeFound(String filter) throws Exception {
        restAuditLogMockMvc
            .perform(get(ENTITY_API_URL + "?sort=eventId,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].eventId").value(not(hasItem(auditLog.getEventId().toString()))));
    }

    @Test
    @Transactional
    void getNonExistingAuditLog() throws Exception {
        // Get the auditLog
        restAuditLogMockMvc.perform(get(ENTITY_API_URL_ID, UUID.randomUUID().toString())).andExpect(status().isNotFound());
    }

    protected long getRepositoryCount() {
        return auditLogRepository.count();
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

    protected AuditLog getPersistedAuditLog(AuditLog auditLog) {
        return auditLogRepository.findById(auditLog.getEventId()).orElseThrow();
    }

    protected void assertPersistedAuditLogToMatchAllProperties(AuditLog expectedAuditLog) {
        assertAuditLogAllPropertiesEquals(expectedAuditLog, getPersistedAuditLog(expectedAuditLog));
    }

    protected void assertPersistedAuditLogToMatchUpdatableProperties(AuditLog expectedAuditLog) {
        assertAuditLogAllUpdatablePropertiesEquals(expectedAuditLog, getPersistedAuditLog(expectedAuditLog));
    }
}
