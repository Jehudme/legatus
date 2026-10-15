package dev.legatus.server.web.websocket.lctp;

import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.security.SecretVaultService;
import dev.legatus.server.service.dto.lctp.AbortTaskFrame;
import dev.legatus.server.service.dto.lctp.DispatchTaskFrame;
import dev.legatus.server.service.scheduler.TaskDispatcher;
import java.io.IOException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * LCTP WebSocket implementation of {@link TaskDispatcher}.
 * <p>
 * Complies with {@code worker-wire.md} Section 4.5 and {@code server-spec.md} Section 5.2:
 * <ul>
 *   <li>Constructs standard {@code DISPATCH_TASK} wire frames.</li>
 *   <li>Guarantees memory zeroization of all decrypted secrets immediately after WebSocket transmission.</li>
 *   <li>Tracks active in-flight task IDs in {@link LctpWorkerSession}.</li>
 * </ul>
 */
@Component
public class LctpTaskDispatcher implements TaskDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(LctpTaskDispatcher.class);

    private final WorkerSessionRegistry sessionRegistry;
    private final SecretVaultService secretVaultService;
    private final ComputeTaskRepository computeTaskRepository;
    private final ObjectMapper objectMapper;

    public LctpTaskDispatcher(
        WorkerSessionRegistry sessionRegistry,
        SecretVaultService secretVaultService,
        ComputeTaskRepository computeTaskRepository,
        ObjectMapper objectMapper
    ) {
        this.sessionRegistry = sessionRegistry;
        this.secretVaultService = secretVaultService;
        this.computeTaskRepository = computeTaskRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean dispatchTask(ComputeTask task, WorkerNode worker) {
        if (task == null || worker == null) {
            return false;
        }

        Optional<LctpWorkerSession> sessionOpt = sessionRegistry.getByWorkerId(worker.getWorkerId());
        if (sessionOpt.isEmpty()) {
            LOG.warn("Cannot dispatch task {}: worker {} has no active transport session",
                task.getTaskId(), worker.getWorkerId());
            return false;
        }

        LctpWorkerSession session = sessionOpt.get();
        if (!session.getWebSocketSession().isOpen()) {
            LOG.warn("Cannot dispatch task {}: worker {} transport session is closed",
                task.getTaskId(), worker.getWorkerId());
            return false;
        }

        List<String> secretRefs = parseSecretRefs(task.getSecretRefsJson());
        Map<String, Object> arguments = parseJsonMap(task.getInputJson());
        Map<String, String> env = parseStringMap(task.getEnvJson());

        try {
            secretVaultService.withDecryptedSecrets(secretRefs, resolvedSecrets -> {
                DispatchTaskFrame frame = new DispatchTaskFrame(
                    UUID.randomUUID(),
                    System.currentTimeMillis(),
                    new DispatchTaskFrame.Payload(
                        task.getTaskId(),
                        task.getEntryPoint(),
                        new DispatchTaskFrame.BytecodeSpec(
                            "solix_bc_v1",
                            "base64",
                            task.getBytecodeSha256(),
                            null,
                            "/api/v1/bytecode/" + task.getBytecodeSha256()
                        ),
                        new DispatchTaskFrame.Constraints(
                            task.getInstructionBudget() != null ? task.getInstructionBudget() : 1000000L,
                            task.getMaxMemoryBytes() != null ? task.getMaxMemoryBytes() : 67108864L,
                            task.getTimeoutMs() != null ? task.getTimeoutMs() : 30000
                        ),
                        arguments,
                        env,
                        resolvedSecrets
                    )
                );

                String json = objectMapper.writeValueAsString(frame);
                session.sendText(json);
                return null;
            });

            // Transition task state to EXECUTING upon successful socket transmission
            task.setStatus(TaskStatus.EXECUTING);
            computeTaskRepository.saveAndFlush(task);

            session.getActiveTaskIds().add(task.getTaskId());
            LOG.info("Dispatched task {} over LCTP session {} to worker {}",
                task.getTaskId(), session.getSessionId(), worker.getWorkerId());
            return true;
        } catch (Exception e) {
            LOG.error("Failed to transmit DISPATCH_TASK frame for task {} to worker {}",
                task.getTaskId(), worker.getWorkerId(), e);
            return false;
        }
    }

    @Override
    public boolean isBytecodeCached(String workerId, String bytecodeSha256) {
        // Future optimization: track cached bytecodes from worker reports
        return false;
    }

    @Override
    public Set<String> getActiveTaskIds(String workerId) {
        return sessionRegistry.getByWorkerId(workerId)
            .map(LctpWorkerSession::getActiveTaskIds)
            .orElse(Set.of());
    }

    @Override
    public boolean abortTask(String workerId, String taskId, String reason, int gracePeriodMs) {
        if (workerId == null || taskId == null) {
            return false;
        }
        Optional<LctpWorkerSession> sessionOpt = sessionRegistry.getByWorkerId(workerId);
        if (sessionOpt.isEmpty()) {
            LOG.warn("Cannot abort task {}: worker {} has no active transport session", taskId, workerId);
            return false;
        }
        LctpWorkerSession session = sessionOpt.get();
        if (!session.getWebSocketSession().isOpen()) {
            LOG.warn("Cannot abort task {}: worker {} transport session is closed", taskId, workerId);
            return false;
        }
        try {
            AbortTaskFrame frame = new AbortTaskFrame(
                UUID.randomUUID(),
                System.currentTimeMillis(),
                new AbortTaskFrame.Payload(taskId, reason != null ? reason : "OPERATOR_CANCELLED", gracePeriodMs)
            );
            String json = objectMapper.writeValueAsString(frame);
            session.sendText(json);
            LOG.info("Transmitted ABORT_TASK frame for task {} to worker {} (session: {})",
                taskId, workerId, session.getSessionId());
            return true;
        } catch (Exception e) {
            LOG.error("Failed to transmit ABORT_TASK frame for task {} to worker {}", taskId, workerId, e);
            return false;
        }
    }

    private List<String> parseSecretRefs(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            LOG.warn("Failed to parse secret_refs JSON: {}", json, e);
            return List.of();
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            LOG.warn("Failed to parse arguments JSON: {}", json, e);
            return Map.of();
        }
    }

    private Map<String, String> parseStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            LOG.warn("Failed to parse env JSON: {}", json, e);
            return Map.of();
        }
    }
}
