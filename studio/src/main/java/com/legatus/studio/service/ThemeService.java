package com.legatus.studio.service;

import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.PrimerDark;
import javafx.application.Application;

/**
 * Service managing AtlantaFX dark-mode themes and live switching.
 */
public class ThemeService {

    public enum Theme {
        PRIMER_DARK("Primer Dark"),
        NORD_DARK("Nord Dark");

        private final String displayName;

        Theme(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static Theme currentTheme = Theme.PRIMER_DARK;

    public static void applyTheme(Theme theme) {
        currentTheme = theme;
        switch (theme) {
            case PRIMER_DARK -> Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            case NORD_DARK -> Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());
        }
    }

    public static Theme getCurrentTheme() {
        return currentTheme;
    }

    public static void toggleTheme() {
        if (currentTheme == Theme.PRIMER_DARK) {
            applyTheme(Theme.NORD_DARK);
        } else {
            applyTheme(Theme.PRIMER_DARK);
        }
    }
}
