package com.legatus.studio;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.module.ModuleDescriptor;

import static org.junit.jupiter.api.Assertions.*;

public class AppPackagingSmokeTest {

    @Test
    void testAppLauncherClassAvailable() {
        assertDoesNotThrow(() -> {
            Class<?> clazz = Class.forName("com.legatus.studio.App");
            assertNotNull(clazz);
            assertNotNull(clazz.getMethod("main", String[].class));
        });
    }

    @Test
    void testModularDescriptorResolution() {
        Module module = App.class.getModule();
        if (module.isNamed()) {
            ModuleDescriptor descriptor = module.getDescriptor();
            assertNotNull(descriptor);
            assertEquals("com.legatus.studio", descriptor.name());
            assertTrue(descriptor.packages().contains("com.legatus.studio.controllers"));
            assertTrue(descriptor.packages().contains("com.legatus.studio.service"));
            assertTrue(descriptor.packages().contains("com.legatus.studio.viewmodel"));
        }
    }

    @Test
    void testResourceBundleAssetsPackaging() {
        // Verify key css and fxml resource streams exist on classpath
        try (InputStream css = getClass().getResourceAsStream("/com/legatus/studio/styles.css")) {
            assertNotNull(css, "styles.css must be bundled on classpath");
        } catch (Exception e) {
            fail("Failed reading styles.css", e);
        }

        try (InputStream shell = getClass().getResourceAsStream("/com/legatus/studio/shell.fxml")) {
            assertNotNull(shell, "shell.fxml must be bundled on classpath");
        } catch (Exception e) {
            fail("Failed reading shell.fxml", e);
        }

        try (InputStream login = getClass().getResourceAsStream("/com/legatus/studio/login.fxml")) {
            assertNotNull(login, "login.fxml must be bundled on classpath");
        } catch (Exception e) {
            fail("Failed reading login.fxml", e);
        }
    }
}
