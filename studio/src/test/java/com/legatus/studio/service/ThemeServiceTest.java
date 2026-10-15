package com.legatus.studio.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ThemeServiceTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            javafx.application.Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // already initialized
        }
    }

    @Test
    void testThemeValuesAndToggle() {
        assertEquals("Primer Dark", ThemeService.Theme.PRIMER_DARK.getDisplayName());
        assertEquals("Nord Dark", ThemeService.Theme.NORD_DARK.getDisplayName());

        ThemeService.applyTheme(ThemeService.Theme.PRIMER_DARK);
        assertEquals(ThemeService.Theme.PRIMER_DARK, ThemeService.getCurrentTheme());

        ThemeService.toggleTheme();
        assertEquals(ThemeService.Theme.NORD_DARK, ThemeService.getCurrentTheme());

        ThemeService.toggleTheme();
        assertEquals(ThemeService.Theme.PRIMER_DARK, ThemeService.getCurrentTheme());
    }
}
