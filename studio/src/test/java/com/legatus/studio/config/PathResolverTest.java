package com.legatus.studio.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class PathResolverTest {

    @AfterEach
    void resetOverrides() {
        PathResolver.setConfigOverride(null);
        PathResolver.setLogDirOverride(null);
        System.clearProperty("legatus.config.path");
        System.clearProperty("legatus.log.dir");
    }

    @Test
    void testDetectOsNotNull() {
        assertNotNull(PathResolver.detectOS());
    }

    @Test
    void testGetConfigPathNotNull() {
        Path configPath = PathResolver.getConfigPath();
        assertNotNull(configPath);
        assertTrue(configPath.toString().endsWith("studio.json"));
    }

    @Test
    void testGetCertsDirNotNull() {
        Path certsDir = PathResolver.getCertsDir();
        assertNotNull(certsDir);
        assertTrue(certsDir.toString().endsWith("certs"));
    }

    @Test
    void testGetLogPathNotNull() {
        Path logPath = PathResolver.getLogPath();
        assertNotNull(logPath);
        assertTrue(logPath.toString().endsWith("studio.log"));
    }

    @Test
    void testConfigOverride() {
        Path custom = Paths.get("/tmp/custom-legatus/studio.json");
        PathResolver.setConfigOverride(custom);

        assertEquals(custom, PathResolver.getConfigPath());
        assertEquals(Paths.get("/tmp/custom-legatus/certs"), PathResolver.getCertsDir());
    }

    @Test
    void testLogDirOverride() {
        Path customDir = Paths.get("/tmp/custom-logs");
        PathResolver.setLogDirOverride(customDir);

        assertEquals(Paths.get("/tmp/custom-logs/studio.log"), PathResolver.getLogPath());
    }
}
