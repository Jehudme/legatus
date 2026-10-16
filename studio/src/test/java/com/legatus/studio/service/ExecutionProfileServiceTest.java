package com.legatus.studio.service;

import com.legatus.studio.model.ExecutionProfile;
import com.legatus.studio.protocol.RestDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ExecutionProfileServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void testExecutionProfileModelProperties() {
        UUID functionId = UUID.randomUUID();
        RestDto.ExecutionProfileDto dto = new RestDto.ExecutionProfileDto(
                "prof-123",
                "Benchmark Run",
                functionId,
                "{\"iterations\": 5000}",
                250_000L,
                false,
                4_000L,
                false,
                128L,
                "HIGH",
                true,
                1L
        );

        ExecutionProfile profile = ExecutionProfile.fromDto(dto);

        assertEquals("prof-123", profile.getProfileId());
        assertEquals(functionId, profile.getFunctionId());
        assertEquals("Benchmark Run", profile.getProfileName());
        assertEquals("{\"iterations\": 5000}", profile.getArgumentsJson());
        assertEquals(250_000L, profile.getInstructionBudget());
        assertEquals(4_000L, profile.getTimeLimitMs());
        assertTrue(profile.isNotifyOnFailure());
        assertEquals(1L, profile.getVersion());

        // Test mutators
        profile.setProfileName("Updated Benchmark");
        assertEquals("Updated Benchmark", profile.getProfileName());
        profile.setFunctionName("compute-heavy");
        assertEquals("compute-heavy", profile.getFunctionName());
        profile.setNotifyOnFailure(false);
        assertFalse(profile.isNotifyOnFailure());
        profile.setInstructionBudget(0L);
        assertEquals(0L, profile.getInstructionBudget());
        profile.setUnlimitedInstructions(true);
        assertTrue(profile.isUnlimitedInstructions());

        RestDto.ExecutionProfileDto converted = profile.toDto();
        assertEquals("prof-123", converted.profile_id());
        assertEquals("Updated Benchmark", converted.profile_name());
        assertEquals(0L, converted.instruction_budget());
        assertTrue(converted.unlimited_instructions());
    }

    @Test
    void testExportAndImportProfileJson() throws IOException {
        ExecutionProfileService service = new ExecutionProfileService(new MockRestClient());

        UUID functionId = UUID.randomUUID();
        RestDto.ExecutionProfileDto original = new RestDto.ExecutionProfileDto(
                "stress-id",
                "Stress-Test-Profile",
                functionId,
                "{\"threads\": 16, \"stress\": true}",
                5_000_000L,
                false,
                30_000L,
                false,
                256L,
                "NORMAL",
                true,
                0L
        );

        Path exportFile = tempDir.resolve("stress_profile.json");
        service.exportProfile(original, exportFile);

        assertTrue(Files.exists(exportFile));
        String jsonContent = Files.readString(exportFile);
        assertTrue(jsonContent.contains("Stress-Test-Profile"));
        assertTrue(jsonContent.contains("5000000"));

        RestDto.ExecutionProfileDto imported = service.importProfile(exportFile);
        assertNotNull(imported);
        assertEquals("Stress-Test-Profile", imported.profile_name());
        assertEquals("{\"threads\": 16, \"stress\": true}", imported.arguments_json());
        assertEquals(5_000_000L, imported.instruction_budget());
        assertEquals(30_000L, imported.time_limit_ms());
        assertTrue(imported.notify_on_failure());
    }

    private static class MockRestClient extends com.legatus.studio.transport.RestClient {
        MockRestClient() {
            super("http://localhost:8080");
        }
    }
}
