package com.legatus.studio;

import com.legatus.studio.service.ThemeService;
import com.legatus.studio.service.ViewLoaderService;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main JavaFX Application for Legatus Studio.
 */
public class Application extends javafx.application.Application {

    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        // Bootstrap AtlantaFX Dark-Mode theme
        ThemeService.applyTheme(ThemeService.Theme.PRIMER_DARK);

        var result = ViewLoaderService.load("primary");
        scene = new Scene(result.root(), 1280, 800);

        stage.setTitle("Legatus Studio");
        stage.setScene(scene);
        stage.show();
    }

    public static void setRoot(String fxml) throws IOException {
        var result = ViewLoaderService.load(fxml);
        scene.setRoot(result.root());
    }

    public static void main(String[] args) {
        launch();
    }
}