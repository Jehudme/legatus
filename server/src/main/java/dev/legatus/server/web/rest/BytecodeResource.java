package dev.legatus.server.web.rest;

import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.security.AuthoritiesConstants;
import dev.legatus.server.security.SecurityUtils;
import dev.legatus.server.service.WormAuditService;
import dev.legatus.server.service.dto.task.BytecodeUploadResponse;
import dev.legatus.server.storage.BytecodeStorageService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for downloading and uploading raw compiled Solix bytecode binaries (api-endpoint.md Section 4.4).
 */
@RestController
@RequestMapping("/api/v1/bytecode")
public class BytecodeResource {

    private static final Logger LOG = LoggerFactory.getLogger(BytecodeResource.class);

    private final BytecodeStorageService bytecodeStorageService;
    private final WormAuditService wormAuditService;

    public BytecodeResource(BytecodeStorageService bytecodeStorageService, WormAuditService wormAuditService) {
        this.bytecodeStorageService = bytecodeStorageService;
        this.wormAuditService = wormAuditService;
    }

    /**
     * {@code GET /api/v1/bytecode/{sha256}} : Streams raw compiled Solix bytecode binary directly.
     * <p>
     * Supports HTTP 304 Not Modified cache validation via {@code If-None-Match} header.
     *
     * @param sha256 64-character lowercase hexadecimal SHA-256 digest
     * @param ifNoneMatch optional HTTP ETag cache validation header
     * @return binary octet-stream with ETag header or 304 Not Modified
     * @throws IOException on I/O failure
     */
    @GetMapping(value = "/{sha256}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> downloadBytecode(
        @PathVariable("sha256") String sha256,
        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) throws IOException {
        String normalizedSha256 = sha256.toLowerCase().trim();
        if (!normalizedSha256.matches("^[a-f0-9]{64}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SHA-256 digest format: " + sha256);
        }

        String quotedEtag = "\"" + normalizedSha256 + "\"";

        // HTTP 304 Not Modified cache validation
        if (ifNoneMatch != null) {
            String trimmedHeader = ifNoneMatch.trim();
            if (trimmedHeader.equals(quotedEtag) || trimmedHeader.equals(normalizedSha256) || trimmedHeader.equals("*")) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(quotedEtag)
                    .build();
            }
        }

        if (!bytecodeStorageService.exists(normalizedSha256)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bytecode with SHA-256 " + normalizedSha256 + " not found");
        }

        InputStream stream = bytecodeStorageService.openStream(normalizedSha256)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bytecode stream unavailable"));
        long size = bytecodeStorageService.getSize(normalizedSha256);

        LOG.debug("Streaming bytecode binary {} ({} bytes)", normalizedSha256, size);
        return ResponseEntity.ok()
            .eTag(quotedEtag)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(size)
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
            .body(new InputStreamResource(stream));
    }

    /**
     * {@code POST /api/v1/bytecode} : Ingests a raw bytecode binary stream directly.
     *
     * @param inputStream raw binary stream
     * @param servletRequest HTTP servlet request for client IP
     * @return 201 Created with digest, size, and download URL
     * @throws IOException on I/O failure
     */
    @PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BytecodeUploadResponse> uploadBytecodeStream(
        InputStream inputStream,
        HttpServletRequest servletRequest
    ) throws IOException {
        String sha256 = bytecodeStorageService.store(inputStream);
        long size = bytecodeStorageService.getSize(sha256);

        recordAuditBytecodeUpload(sha256, size, servletRequest.getRemoteAddr());
        LOG.info("Uploaded bytecode binary {} ({} bytes) via octet-stream", sha256, size);

        return ResponseEntity.status(HttpStatus.CREATED).body(
            new BytecodeUploadResponse(sha256, size, "/api/v1/bytecode/" + sha256)
        );
    }

    /**
     * {@code POST /api/v1/bytecode} : Ingests a raw bytecode binary via multipart form upload.
     *
     * @param file multipart file containing compiled bytecode
     * @param servletRequest HTTP servlet request for client IP
     * @return 201 Created with digest, size, and download URL
     * @throws IOException on I/O failure
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BytecodeUploadResponse> uploadBytecodeMultipart(
        @RequestParam("file") MultipartFile file,
        HttpServletRequest servletRequest
    ) throws IOException {
        try (InputStream is = file.getInputStream()) {
            String sha256 = bytecodeStorageService.store(is);
            long size = bytecodeStorageService.getSize(sha256);

            recordAuditBytecodeUpload(sha256, size, servletRequest.getRemoteAddr());
            LOG.info("Uploaded bytecode binary {} ({} bytes) via multipart", sha256, size);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                new BytecodeUploadResponse(sha256, size, "/api/v1/bytecode/" + sha256)
            );
        }
    }

    private void recordAuditBytecodeUpload(String sha256, long sizeBytes, String sourceIp) {
        String actor = SecurityUtils.getCurrentUserLogin().orElse("system");
        wormAuditService.recordEvent(
            actor,
            resolveActorRole(),
            "BYTECODE_UPLOADED",
            "BYTECODE",
            sha256,
            AuditActionStatus.SUCCESS,
            "{\"size_bytes\":" + sizeBytes + "}",
            sourceIp != null ? sourceIp : "127.0.0.1",
            WormAuditService.DEFAULT_COMPLIANCE_FLAG
        );
    }

    private String resolveActorRole() {
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.CLUSTER_ADMIN)) {
            return "CLUSTER_ADMIN";
        }
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.OPERATOR)) {
            return "OPERATOR";
        }
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            return "ADMIN";
        }
        return "SYSTEM";
    }
}
