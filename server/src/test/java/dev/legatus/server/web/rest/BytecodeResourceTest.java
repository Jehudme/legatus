package dev.legatus.server.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import dev.legatus.server.domain.enumeration.AuditActionStatus;
import dev.legatus.server.service.WormAuditService;
import dev.legatus.server.service.dto.task.BytecodeUploadResponse;
import dev.legatus.server.storage.BytecodeStorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BytecodeResourceTest {

    @Mock
    private BytecodeStorageService bytecodeStorageService;

    @Mock
    private WormAuditService wormAuditService;

    private BytecodeResource bytecodeResource;

    private static final String VALID_SHA256 = "8a3f89b4c0e1d2c3b4a5f6e7d8c9b0a1f2e3d4c5b6a7f8e9d0c1b2a3f4e5d6c7";
    private static final byte[] BYTECODE_BYTES = "SOLIX_COMPILED_BYTECODE_BINARY_DATA".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        bytecodeResource = new BytecodeResource(bytecodeStorageService, wormAuditService);
    }

    @Test
    @DisplayName("Should successfully stream bytecode with ETag and Cache-Control headers")
    void testDownloadBytecodeSuccess() throws IOException {
        when(bytecodeStorageService.exists(VALID_SHA256)).thenReturn(true);
        when(bytecodeStorageService.openStream(VALID_SHA256)).thenReturn(Optional.of(new ByteArrayInputStream(BYTECODE_BYTES)));
        when(bytecodeStorageService.getSize(VALID_SHA256)).thenReturn((long) BYTECODE_BYTES.length);

        ResponseEntity<Resource> response = bytecodeResource.downloadBytecode(VALID_SHA256, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"" + VALID_SHA256 + "\"");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(BYTECODE_BYTES.length);
        assertThat(response.getHeaders().getCacheControl()).contains("public");
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Should return 304 Not Modified when If-None-Match matches ETag")
    void testDownloadBytecodeEtagNotModified() throws IOException {
        ResponseEntity<Resource> response = bytecodeResource.downloadBytecode(VALID_SHA256, "\"" + VALID_SHA256 + "\"");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"" + VALID_SHA256 + "\"");
        verify(bytecodeStorageService, never()).openStream(any());
    }

    @Test
    @DisplayName("Should return 404 Not Found when bytecode does not exist")
    void testDownloadBytecodeNotFound() {
        when(bytecodeStorageService.exists(VALID_SHA256)).thenReturn(false);

        assertThatThrownBy(() -> bytecodeResource.downloadBytecode(VALID_SHA256, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should return 400 Bad Request for malformed SHA-256 digest")
    void testDownloadBytecodeInvalidSha256() {
        assertThatThrownBy(() -> bytecodeResource.downloadBytecode("invalid-sha256-string", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should ingest raw binary octet-stream and record WORM audit log")
    void testUploadBytecodeStream() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.10.10.10");

        when(bytecodeStorageService.store(any(InputStream.class))).thenReturn(VALID_SHA256);
        when(bytecodeStorageService.getSize(VALID_SHA256)).thenReturn(1024L);

        ResponseEntity<BytecodeUploadResponse> response = bytecodeResource.uploadBytecodeStream(
            new ByteArrayInputStream(BYTECODE_BYTES),
            request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().sha256()).isEqualTo(VALID_SHA256);
        assertThat(response.getBody().sizeBytes()).isEqualTo(1024L);
        assertThat(response.getBody().bytecodeUrl()).isEqualTo("/api/v1/bytecode/" + VALID_SHA256);

        verify(wormAuditService).recordEvent(
            any(), any(), eq("BYTECODE_UPLOADED"), eq("BYTECODE"), eq(VALID_SHA256),
            eq(AuditActionStatus.SUCCESS), any(), eq("10.10.10.10"), any()
        );
    }

    @Test
    @DisplayName("Should ingest multipart bytecode file and return download URL")
    void testUploadBytecodeMultipart() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.20.30.40");

        MockMultipartFile file = new MockMultipartFile("file", "kernel.bc", "application/octet-stream", BYTECODE_BYTES);
        when(bytecodeStorageService.store(any(InputStream.class))).thenReturn(VALID_SHA256);
        when(bytecodeStorageService.getSize(VALID_SHA256)).thenReturn((long) BYTECODE_BYTES.length);

        ResponseEntity<BytecodeUploadResponse> response = bytecodeResource.uploadBytecodeMultipart(file, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().sha256()).isEqualTo(VALID_SHA256);
        assertThat(response.getBody().sizeBytes()).isEqualTo(BYTECODE_BYTES.length);
    }
}
