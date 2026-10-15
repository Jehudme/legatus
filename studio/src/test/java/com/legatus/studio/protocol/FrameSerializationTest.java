package com.legatus.studio.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class FrameSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void testSessionInitFrameSerialization() throws Exception {
        SessionInitPayload payload = new SessionInitPayload(
                "1.0.0",
                List.of("CLUSTER_METRICS", "WORKER_TOPOLOGY"),
                Map.of("limit", 50)
        );
        Frame<SessionInitPayload> frame = Frame.create("STUDIO_SESSION_INIT", payload);

        String json = mapper.writeValueAsString(frame);
        assertNotNull(json);

        JsonNode tree = mapper.readTree(json);
        assertEquals("STUDIO_SESSION_INIT", tree.get("type").asText());
        assertNotNull(tree.get("id").asText());
        assertTrue(tree.get("timestamp").asLong() > 0);
        assertEquals("1.0.0", tree.get("payload").get("studio_version").asText());
    }

    @Test
    void testClusterMetricTickSerialization() throws Exception {
        ClusterMetricTickPayload payload = new ClusterMetricTickPayload(
                14, 1, 0, 224, 96, 42.8, 240518168576L, 103079215104L, 4, 96, 1420, 2
        );
        Frame<ClusterMetricTickPayload> frame = Frame.create("EVENT_CLUSTER_METRIC_TICK", payload);

        String json = mapper.writeValueAsString(frame);
        JsonNode tree = mapper.readTree(json);

        assertEquals("EVENT_CLUSTER_METRIC_TICK", tree.get("type").asText());
        assertEquals(14, tree.get("payload").get("nodes_online").asInt());
        assertEquals(42.8, tree.get("payload").get("cluster_cpu_load_pct").asDouble());
        assertEquals(240518168576L, tree.get("payload").get("total_allocatable_memory_bytes").asLong());
    }

    @Test
    void testEnrollmentRequestedSerialization() throws Exception {
        EnrollmentRequestedPayload payload = new EnrollmentRequestedPayload(
                "enr-8821", "worker-42", "wk-42.internal", "10.24.18.42", "linux", "x86_64",
                "4E:8A:BC:31:09:FF:7D:12", "Tiger-Galaxy-77", 12, 17179869184L, 1728908100000L
        );
        Frame<EnrollmentRequestedPayload> frame = Frame.create("EVENT_ENROLLMENT_REQUESTED", payload);

        String json = mapper.writeValueAsString(frame);
        JsonNode tree = mapper.readTree(json);

        assertEquals("EVENT_ENROLLMENT_REQUESTED", tree.get("type").asText());
        assertEquals("Tiger-Galaxy-77", tree.get("payload").get("visual_code").asText());
        assertEquals("4E:8A:BC:31:09:FF:7D:12", tree.get("payload").get("fingerprint_hex").asText());
    }

    @Test
    void testTaskLifecycleSerialization() throws Exception {
        TaskLifecyclePayload payload = new TaskLifecyclePayload(
                "task-001", "matrix-factorization", "COMPLETED", 0, "worker-42",
                1284500L, 14680064L, 2341L
        );
        Frame<TaskLifecyclePayload> frame = Frame.create("EVENT_TASK_LIFECYCLE", payload);

        String json = mapper.writeValueAsString(frame);
        JsonNode tree = mapper.readTree(json);

        assertEquals("EVENT_TASK_LIFECYCLE", tree.get("type").asText());
        assertEquals("COMPLETED", tree.get("payload").get("status").asText());
        assertEquals(0, tree.get("payload").get("exit_code").asInt());
    }

    @Test
    void testRestDtoLoginRequestResponse() throws Exception {
        RestDto.LoginRequest req = new RestDto.LoginRequest("admin", "password123");
        String jsonReq = mapper.writeValueAsString(req);
        RestDto.LoginRequest parsedReq = mapper.readValue(jsonReq, RestDto.LoginRequest.class);
        assertEquals("admin", parsedReq.username());
        assertEquals("password123", parsedReq.password());

        RestDto.LoginResponse res = new RestDto.LoginResponse(
                "acc-tok", "ref-tok", 3600, "acc-1", "admin", "CLUSTER_ADMIN"
        );
        String jsonRes = mapper.writeValueAsString(res);
        RestDto.LoginResponse parsedRes = mapper.readValue(jsonRes, RestDto.LoginResponse.class);
        assertEquals("CLUSTER_ADMIN", parsedRes.role());
        assertEquals("acc-tok", parsedRes.access_token());
    }
}
