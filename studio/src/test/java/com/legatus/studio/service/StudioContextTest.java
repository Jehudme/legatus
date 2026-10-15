package com.legatus.studio.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StudioContextTest {

    @Test
    void testStudioContextInitialization() {
        StudioContext ctx = StudioContext.getInstance();
        assertNotNull(ctx, "StudioContext instance should not be null");
        assertNotNull(ctx.getLogStreamBuffer(), "LogStreamBuffer should be initialized");
        assertNotNull(ctx.getMainViewModel(), "MainViewModel should be initialized");

        // Initialize session with mock URL
        ctx.initSession("http://127.0.0.1:8443", true);

        assertNotNull(ctx.getRestClient(), "RestClient should be initialized");
        assertNotNull(ctx.getAuthService(), "AuthService should be initialized");
        assertNotNull(ctx.getWorkerService(), "WorkerService should be initialized");
        assertNotNull(ctx.getTaskService(), "TaskService should be initialized");
        assertNotNull(ctx.getBytecodeService(), "BytecodeService should be initialized");
        assertNotNull(ctx.getSecretsService(), "SecretsService should be initialized");
        assertNotNull(ctx.getUserManagementService(), "UserManagementService should be initialized");
        assertNotNull(ctx.getAuditLogService(), "AuditLogService should be initialized");
        assertNotNull(ctx.getLstcpClient(), "LstcpClient should be initialized");
        assertNotNull(ctx.getTelemetryService(), "TelemetryService should be initialized");
    }
}
