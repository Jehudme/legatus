package dev.legatus.server.web.rest;

import dev.legatus.server.config.LegatusProperties;
import dev.legatus.server.domain.SolixFunction;
import dev.legatus.server.domain.enumeration.TaskPriority;
import dev.legatus.server.repository.SolixFunctionRepository;
import dev.legatus.server.service.TaskGatewayService;
import dev.legatus.server.service.dto.task.SubmitTaskRequest;
import dev.legatus.server.service.dto.task.SubmitTaskRequest.BytecodePayload;
import dev.legatus.server.service.dto.task.SubmitTaskRequest.ConstraintsPayload;
import dev.legatus.server.service.dto.task.SubmitTaskResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for third-party webhook ingestion.
 * <p>
 * Complies with {@code server-spec.md} Section 6.1 (Issue #210):
 * <ul>
 *   <li>Toggled via {@code legatus.features.webhooks} (default: {@code true}).</li>
 *   <li>Exposes {@code POST /api/v1/webhooks/{function_name}} to ingest third-party JSON payloads directly into tasks.</li>
 *   <li>Supports optional HMAC / shared secret verification via {@code X-Legatus-Webhook-Secret}.</li>
 *   <li>Maps incoming payloads directly into function execution arguments with idempotency headers.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookGatewayResource {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookGatewayResource.class);

    private final LegatusProperties legatusProperties;
    private final SolixFunctionRepository solixFunctionRepository;
    private final TaskGatewayService taskGatewayService;

    public WebhookGatewayResource(
        LegatusProperties legatusProperties,
        SolixFunctionRepository solixFunctionRepository,
        TaskGatewayService taskGatewayService
    ) {
        this.legatusProperties = legatusProperties;
        this.solixFunctionRepository = solixFunctionRepository;
        this.taskGatewayService = taskGatewayService;
    }

    /**
     * {@code POST /api/v1/webhooks/{function_name}} : Ingests a third-party webhook payload and triggers the target Solix function.
     *
     * @param functionName target Solix function name
     * @param payload optional incoming JSON payload
     * @param priority optional priority override
     * @param idempotencyKey optional idempotency tracking key header
     * @param secretHeader optional shared secret header
     * @param secretParam optional shared secret query parameter
     * @param servletRequest HTTP request for remote address resolution
     * @return 202 Accepted with task dispatch confirmation
     */
    @PostMapping("/{function_name}")
    public ResponseEntity<SubmitTaskResponse> ingestWebhook(
        @PathVariable("function_name") String functionName,
        @RequestBody(required = false) Map<String, Object> payload,
        @RequestParam(value = "priority", required = false) TaskPriority priority,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
        @RequestHeader(value = "X-Legatus-Webhook-Secret", required = false) String secretHeader,
        @RequestParam(value = "secret", required = false) String secretParam,
        HttpServletRequest servletRequest
    ) {
        // 1. Feature toggle verification
        if (!legatusProperties.getFeatures().isWebhooks() || !legatusProperties.getWebhooks().isEnabled()) {
            LOG.warn("Rejected webhook for '{}': Webhook ingestion gateway is disabled", functionName);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook ingestion gateway is disabled");
        }

        // 2. Secret authentication check if configured
        String configuredSecret = legatusProperties.getWebhooks().getSecret();
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            String incomingSecret = secretHeader != null && !secretHeader.isBlank() ? secretHeader : secretParam;
            if (incomingSecret == null || !MessageDigest.isEqual(
                configuredSecret.getBytes(StandardCharsets.UTF_8),
                incomingSecret.getBytes(StandardCharsets.UTF_8)
            )) {
                LOG.warn("Rejected webhook for '{}': Invalid or missing webhook secret from IP {}", functionName, servletRequest.getRemoteAddr());
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook secret");
            }
        }

        // 3. Target function resolution
        Optional<SolixFunction> functionOpt = solixFunctionRepository.findFirstByNameOrderByCreatedAtDesc(functionName);
        if (functionOpt.isEmpty()) {
            LOG.warn("Webhook target function '{}' not found in registry", functionName);
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Function '" + functionName + "' not registered in cluster"
            );
        }

        SolixFunction function = functionOpt.get();

        String payloadBase64 = null;
        if (function.getBytecodeBlob() != null && function.getBytecodeBlob().length > 0) {
            payloadBase64 = Base64.getEncoder().encodeToString(function.getBytecodeBlob());
        }

        TaskPriority resolvedPriority = priority != null ? priority : TaskPriority.NORMAL;
        String sourceIp = servletRequest.getRemoteAddr();

        Map<String, String> env = new HashMap<>();
        env.put("WEBHOOK_SOURCE_IP", sourceIp != null ? sourceIp : "unknown");
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            env.put("WEBHOOK_IDEMPOTENCY_KEY", idempotencyKey);
        }

        SubmitTaskRequest request = new SubmitTaskRequest(
            "webhook-" + functionName + "-" + UUID.randomUUID().toString().substring(0, 8),
            resolvedPriority,
            50,
            idempotencyKey != null && !idempotencyKey.isBlank(),
            2,
            function.getEntryPoint(),
            new BytecodePayload(
                function.getBytecodeFormat(),
                "base64",
                function.getBytecodeSha256(),
                payloadBase64
            ),
            new ConstraintsPayload(
                function.getDefaultBudget(),
                function.getDefaultMemoryBytes(),
                function.getDefaultTimeoutMs()
            ),
            payload != null ? payload : Map.of(),
            env,
            List.of()
        );

        LOG.info("Ingesting webhook for function '{}' from IP {} [idempotent: {}]", functionName, sourceIp, request.idempotent());
        SubmitTaskResponse response = taskGatewayService.submitTask(request, sourceIp);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
