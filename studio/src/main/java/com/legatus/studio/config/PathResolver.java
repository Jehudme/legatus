package com.legatus.studio.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Resolves configuration, certificate, and log file paths conforming to
 * OS standards (XDG on Linux, Library on macOS, AppData on Windows).
 */
public final class PathResolver {

    public enum OS {
        LINUX, MACOS, WINDOWS, OTHER
    }

    private static Path configOverride;
    private static Path logDirOverride;

    private PathResolver() {}

    public static OS detectOS() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("mac") || osName.contains("darwin")) {
            return OS.MACOS;
        } else if (osName.contains("win")) {
            return OS.WINDOWS;
        } else if (osName.contains("linux") || osName.contains("nix") || osName.contains("nux")) {
            return OS.LINUX;
        }
        return OS.OTHER;
    }

    public static void setConfigOverride(Path path) {
        configOverride = path;
    }

    public static void setLogDirOverride(Path dir) {
        logDirOverride = dir;
    }

    public static Path getConfigPath() {
        if (configOverride != null) {
            return configOverride;
        }
        String sysProp = System.getProperty("legatus.config.path");
        if (sysProp != null && !sysProp.isBlank()) {
            return Paths.get(sysProp);
        }

        String home = System.getProperty("user.home", ".");
        return switch (detectOS()) {
            case LINUX, OTHER -> {
                String xdg = System.getenv("XDG_CONFIG_HOME");
                Path base = (xdg != null && !xdg.isBlank()) ? Paths.get(xdg) : Paths.get(home, ".config");
                yield base.resolve("legatus").resolve("studio.json");
            }
            case MACOS -> Paths.get(home, "Library", "Application Support", "Legatus", "studio.json");
            case WINDOWS -> {
                String appData = System.getenv("APPDATA");
                Path base = (appData != null && !appData.isBlank()) ? Paths.get(appData) : Paths.get(home, "AppData", "Roaming");
                yield base.resolve("Legatus").resolve("studio.json");
            }
        };
    }

    public static Path getCertsDir() {
        Path configPath = getConfigPath();
        return configPath.getParent().resolve("certs");
    }

    public static Path getLogPath() {
        if (logDirOverride != null) {
            return logDirOverride.resolve("studio.log");
        }
        String sysProp = System.getProperty("legatus.log.dir");
        if (sysProp != null && !sysProp.isBlank()) {
            return Paths.get(sysProp).resolve("studio.log");
        }

        String home = System.getProperty("user.home", ".");
        return switch (detectOS()) {
            case LINUX, OTHER -> {
                String xdgState = System.getenv("XDG_STATE_HOME");
                Path base = (xdgState != null && !xdgState.isBlank()) ? Paths.get(xdgState) : Paths.get(home, ".local", "state");
                yield base.resolve("legatus").resolve("studio.log");
            }
            case MACOS -> Paths.get(home, "Library", "Logs", "Legatus", "studio.log");
            case WINDOWS -> {
                String localAppData = System.getenv("LOCALAPPDATA");
                Path base = (localAppData != null && !localAppData.isBlank()) ? Paths.get(localAppData) : Paths.get(home, "AppData", "Local");
                yield base.resolve("Legatus").resolve("logs").resolve("studio.log");
            }
        };
    }

    public static Path getKeyFilePath() {
        Path configPath = getConfigPath();
        return configPath.getParent().resolve(".studio.key");
    }
}
