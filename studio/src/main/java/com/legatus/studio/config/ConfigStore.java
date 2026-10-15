package com.legatus.studio.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Thread-safe atomic loader and persister for studio.json.
 */
public class ConfigStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Path configPath;
    private StudioConfig cachedConfig;

    public ConfigStore() {
        this(PathResolver.getConfigPath());
    }

    public ConfigStore(Path configPath) {
        this.configPath = Objects.requireNonNull(configPath, "configPath must not be null");
    }

    public synchronized StudioConfig load() {
        if (cachedConfig != null) {
            return cachedConfig;
        }
        if (!Files.exists(configPath)) {
            cachedConfig = StudioConfig.defaultConfiguration();
            return cachedConfig;
        }

        try {
            cachedConfig = MAPPER.readValue(configPath.toFile(), StudioConfig.class);
            return cachedConfig;
        } catch (IOException e) {
            // Corrupted or unreadable, fall back to default
            cachedConfig = StudioConfig.defaultConfiguration();
            return cachedConfig;
        }
    }

    public synchronized void save(StudioConfig config) throws IOException {
        Objects.requireNonNull(config, "config must not be null");
        Path parent = configPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Path tempPath = configPath.resolveSibling(configPath.getFileName().toString() + ".tmp");
        MAPPER.writeValue(tempPath.toFile(), config);

        try {
            Files.move(tempPath, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Fallback if atomic move is unsupported on this filesystem
            Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING);
        }

        this.cachedConfig = config;
    }

    public Path getConfigPath() {
        return configPath;
    }
}
