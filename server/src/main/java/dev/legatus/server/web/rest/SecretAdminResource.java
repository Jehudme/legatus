package dev.legatus.server.web.rest;

import dev.legatus.server.service.SecretEntryService;
import dev.legatus.server.service.dto.CreateSecretRequest;
import dev.legatus.server.service.dto.SecretMetadataResponse;
import dev.legatus.server.service.dto.UpdateSecretRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for secret vault administrative management.
 * <p>
 * Complies with {@code api-endpoint.md} Section 1.1 line 35 and {@code server-spec.md} Section 5.2:
 * <ul>
 *   <li>Requires {@code ROLE_CLUSTER_ADMIN} or {@code ROLE_ADMIN}.</li>
 *   <li>{@code GET /api/v1/admin/secrets} : Lists secret metadata (values masked).</li>
 *   <li>{@code GET /api/v1/admin/secrets/{key}} : Retrieves secret metadata.</li>
 *   <li>{@code POST /api/v1/admin/secrets} : Envelope-encrypts and persists a new secret.</li>
 *   <li>{@code PUT /api/v1/admin/secrets/{key}} : Updates an existing secret.</li>
 *   <li>{@code DELETE /api/v1/admin/secrets/{key}} : Removes a secret from the vault.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/secrets")
public class SecretAdminResource {

    private static final Logger LOG = LoggerFactory.getLogger(SecretAdminResource.class);

    private final SecretEntryService secretEntryService;

    public SecretAdminResource(SecretEntryService secretEntryService) {
        this.secretEntryService = secretEntryService;
    }

    /**
     * {@code GET /api/v1/admin/secrets} : Lists metadata for all registered secrets.
     */
    @GetMapping("")
    public ResponseEntity<List<SecretMetadataResponse>> listSecrets() {
        LOG.debug("REST request to list all vault secrets metadata");
        List<SecretMetadataResponse> secrets = secretEntryService.listSecretMetadata();
        return ResponseEntity.ok(secrets);
    }

    /**
     * {@code GET /api/v1/admin/secrets/{key}} : Retrieves metadata for a specific secret.
     */
    @GetMapping("/{key}")
    public ResponseEntity<SecretMetadataResponse> getSecret(@PathVariable("key") String key) {
        LOG.debug("REST request to get secret metadata for key: {}", key);
        return secretEntryService.getSecretMetadata(key)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * {@code POST /api/v1/admin/secrets} : Creates and envelope-encrypts a new secret.
     */
    @PostMapping("")
    public ResponseEntity<SecretMetadataResponse> createSecret(
        @Valid @RequestBody CreateSecretRequest request,
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        String actorId = resolveActorId(authentication);
        String clientIp = resolveClientIp(httpRequest);
        LOG.info("REST request by {} to create secret {} from IP {}", actorId, request.secretKey(), clientIp);

        SecretMetadataResponse response = secretEntryService.createSecret(
            request,
            actorId,
            "ROLE_CLUSTER_ADMIN",
            clientIp
        );

        return ResponseEntity.created(URI.create("/api/v1/admin/secrets/" + response.secretKey()))
            .body(response);
    }

    /**
     * {@code PUT /api/v1/admin/secrets/{key}} : Updates an existing secret.
     */
    @PutMapping("/{key}")
    public ResponseEntity<SecretMetadataResponse> updateSecret(
        @PathVariable("key") String key,
        @Valid @RequestBody UpdateSecretRequest request,
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        String actorId = resolveActorId(authentication);
        String clientIp = resolveClientIp(httpRequest);
        LOG.info("REST request by {} to update secret {} from IP {}", actorId, key, clientIp);

        SecretMetadataResponse response = secretEntryService.updateSecret(
            key,
            request,
            actorId,
            "ROLE_CLUSTER_ADMIN",
            clientIp
        );

        return ResponseEntity.ok(response);
    }

    /**
     * {@code DELETE /api/v1/admin/secrets/{key}} : Deletes a secret from the vault.
     */
    @DeleteMapping("/{key}")
    public ResponseEntity<Void> deleteSecret(
        @PathVariable("key") String key,
        Authentication authentication,
        HttpServletRequest httpRequest
    ) {
        String actorId = resolveActorId(authentication);
        String clientIp = resolveClientIp(httpRequest);
        LOG.info("REST request by {} to delete secret {} from IP {}", actorId, key, clientIp);

        boolean deleted = secretEntryService.deleteSecret(key, actorId, "ROLE_CLUSTER_ADMIN", clientIp);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    private String resolveActorId(Authentication authentication) {
        if (authentication != null && authentication.getName() != null) {
            return authentication.getName();
        }
        return "system-admin";
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
