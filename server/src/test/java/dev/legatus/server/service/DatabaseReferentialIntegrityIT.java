package dev.legatus.server.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.IntegrationTest;
import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.SolixFunction;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.domain.enumeration.CpuArchitecture;
import dev.legatus.server.domain.enumeration.OperatingSystem;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.domain.enumeration.TaskStatus;
import dev.legatus.server.domain.enumeration.WorkerState;
import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.repository.SolixFunctionRepository;
import dev.legatus.server.repository.WorkerNodeRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test verifying database referential integrity and ON DELETE SET NULL cascades
 * when workers and functions associated with historical tasks are deleted.
 * Complies with MS-012 Section 4.1.
 */
@IntegrationTest
@Transactional
class DatabaseReferentialIntegrityIT {

    @Autowired
    private WorkerEnrollmentService workerEnrollmentService;

    @Autowired
    private SolixFunctionService solixFunctionService;

    @Autowired
    private WorkerNodeRepository workerNodeRepository;

    @Autowired
    private SolixFunctionRepository solixFunctionRepository;

    @Autowired
    private ComputeTaskRepository computeTaskRepository;

    public static WorkerNode createWorker(String workerId) {
        return new WorkerNode()
            .workerId(workerId)
            .enrollmentId("enr-" + UUID.randomUUID())
            .hostname("worker-" + workerId)
            .os(OperatingSystem.LINUX)
            .arch(CpuArchitecture.X86_64)
            .agentVersion("1.0.0")
            .ed25519Pubkey("MCowBQYDK2VwAyEA" + UUID.randomUUID().toString().replace("-", ""))
            .fingerprint("4E:8A:BC:31:09:FF:7D:12")
            .visualCode("TIGER-GALAXY-77")
            .state(WorkerState.ONLINE)
            .assignedCluster("production-us-east")
            .sourceIp("127.0.0.1")
            .physicalCores(4)
            .logicalCores(4)
            .allocatableCores(4)
            .totalMemoryBytes(16L * 1024 * 1024 * 1024)
            .maxMemoryBytes(16L * 1024 * 1024 * 1024)
            .maxInstructionBudget(100_000_000L)
            .gpuPresent(false)
            .draining(false)
            .active(true)
            .createdAt(Instant.now());
    }

    @Test
    @DisplayName("Worker Deletion Cascade: Deleting worker with historical tasks unlinks tasks with assignedWorker = null")
    void testWorkerDeletionUnlinksHistoricalTasks() {
        String workerId = "wk-cascade-" + UUID.randomUUID().toString().substring(0, 8);
        WorkerNode worker = createWorker(workerId);
        worker = workerNodeRepository.saveAndFlush(worker);

        List<String> taskIds = new ArrayList<>();

        // Create 10 completed tasks
        for (int i = 0; i < 10; i++) {
            String tid = "task-completed-" + i + "-" + UUID.randomUUID().toString().substring(0, 6);
            taskIds.add(tid);
            createTask(tid, worker, TaskStatus.COMPLETED);
        }

        // Create 2 failed tasks
        for (int i = 0; i < 2; i++) {
            String tid = "task-failed-" + i + "-" + UUID.randomUUID().toString().substring(0, 6);
            taskIds.add(tid);
            createTask(tid, worker, TaskStatus.FAILED);
        }

        // Create 1 aborted task
        String abortedTid = "task-aborted-" + UUID.randomUUID().toString().substring(0, 6);
        taskIds.add(abortedTid);
        createTask(abortedTid, worker, TaskStatus.ABORTED);

        assertThat(taskIds).hasSize(13);

        // Delete the worker
        workerEnrollmentService.deleteWorker(workerId, "admin-operator");

        // Assert worker is deleted from database
        assertThat(workerNodeRepository.findById(workerId)).isEmpty();

        // Assert all 13 tasks still exist and have assignedWorker unlinked (null)
        for (String tid : taskIds) {
            ComputeTask task = computeTaskRepository.findById(tid).orElseThrow();
            assertThat(task.getAssignedWorker()).isNull();
        }
    }

    @Test
    @DisplayName("Function Deletion Cascade: Deleting function with historical tasks unlinks tasks with function = null")
    void testFunctionDeletionUnlinksHistoricalTasks() {
        SolixFunction function = new SolixFunction()
            .name("cascade-reporter-" + UUID.randomUUID().toString().substring(0, 6))
            .semver("1.0.0")
            .entryPoint("main")
            .bytecodeFormat("SOLIX_V1")
            .bytecodeSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
            .bytecodeBlob(new byte[] { 0x01, 0x02 })
            .bytecodeBlobContentType("application/octet-stream")
            .defaultBudget(50_000_000L)
            .defaultMemoryBytes(67_108_864L)
            .defaultTimeoutMs(30_000)
            .createdAt(Instant.now());
        function = solixFunctionRepository.saveAndFlush(function);
        UUID funcId = function.getFunctionId();
        assertThat(funcId).isNotNull();

        String tid = "task-func-cascade-" + UUID.randomUUID().toString().substring(0, 8);
        ComputeTask task = new ComputeTask()
            .taskId(tid)
            .name("historical-task")
            .status(TaskStatus.COMPLETED)
            .priority(TaskPriority.NORMAL)
            .priorityWeight(50)
            .idempotent(false)
            .entryPoint("main")
            .bytecodeSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
            .instructionBudget(50_000_000L)
            .maxMemoryBytes(67_108_864L)
            .timeoutMs(30_000)
            .attemptCount(1)
            .maxRetries(1)
            .createdAt(Instant.now())
            .completedAt(Instant.now())
            .function(function);
        computeTaskRepository.saveAndFlush(task);

        // Delete function
        solixFunctionService.delete(funcId);

        // Assert function is deleted
        assertThat(solixFunctionRepository.findById(funcId)).isEmpty();

        // Assert task retains history with function = null
        ComputeTask retainedTask = computeTaskRepository.findById(tid).orElseThrow();
        assertThat(retainedTask.getFunction()).isNull();
    }

    private void createTask(String taskId, WorkerNode worker, TaskStatus status) {
        ComputeTask task = new ComputeTask()
            .taskId(taskId)
            .name("task-" + taskId)
            .status(status)
            .priority(TaskPriority.NORMAL)
            .priorityWeight(50)
            .idempotent(false)
            .entryPoint("main")
            .bytecodeSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
            .instructionBudget(50_000_000L)
            .maxMemoryBytes(67_108_864L)
            .timeoutMs(30_000)
            .attemptCount(1)
            .maxRetries(1)
            .createdAt(Instant.now())
            .completedAt(Instant.now())
            .assignedWorker(worker);
        computeTaskRepository.saveAndFlush(task);
    }
}
