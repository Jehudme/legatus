package com.legatus.studio.service;

import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class ViewLoaderServiceTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            javafx.application.Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // already initialized
        }
    }

    @Test
    void testLoadExistingView() throws IOException {
        var result = ViewLoaderService.load("primary");
        assertNotNull(result);
        assertNotNull(result.root());
        assertNotNull(result.controller());
    }

    @Test
    void testLoadWithExtension() throws IOException {
        var result = ViewLoaderService.load("primary.fxml");
        assertNotNull(result);
        assertNotNull(result.root());
    }

    @Test
    void testSwitchView() throws IOException {
        Pane container = new StackPane();
        assertEquals(0, container.getChildren().size());

        Object controller = ViewLoaderService.switchView(container, "primary");
        assertNotNull(controller);
        assertEquals(1, container.getChildren().size());
    }

    @Test
    void testLoadNonExistentViewThrows() {
        assertThrows(NullPointerException.class, () -> {
            ViewLoaderService.load("non_existent_view_404");
        });
    }
}
