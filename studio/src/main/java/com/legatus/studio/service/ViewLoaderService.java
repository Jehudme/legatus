package com.legatus.studio.service;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

/**
 * Service for loading FXML views, binding controllers, and switching views inside container panes.
 */
public class ViewLoaderService {

    public record ViewResult<C>(Parent root, C controller) {}

    /**
     * Load an FXML view from classpath by name (relative to /com/legatus/studio/ or absolute).
     */
    public static <C> ViewResult<C> load(String fxmlPath) throws IOException {
        String resolved = fxmlPath.startsWith("/") ? fxmlPath : "/com/legatus/studio/" + fxmlPath;
        if (!resolved.endsWith(".fxml")) {
            resolved += ".fxml";
        }

        URL resource = ViewLoaderService.class.getResource(resolved);
        Objects.requireNonNull(resource, "FXML resource not found at: " + resolved);

        FXMLLoader loader = new FXMLLoader(resource);
        Parent root = loader.load();
        C controller = loader.getController();

        return new ViewResult<>(root, controller);
    }

    /**
     * Replaces the contents of a target container Pane with the loaded FXML view.
     */
    public static <C> C switchView(Pane container, String fxmlPath) throws IOException {
        ViewResult<C> result = load(fxmlPath);
        container.getChildren().setAll(result.root());
        return result.controller();
    }
}
