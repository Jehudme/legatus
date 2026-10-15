package com.legatus.studio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AppSmokeTest {

    @Test
    void testAppClassLoadable() {
        assertNotNull(App.class);
        assertNotNull(Application.class);
    }
}
