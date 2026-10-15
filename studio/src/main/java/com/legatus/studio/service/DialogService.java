package com.legatus.studio.service;

import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.util.Optional;

/**
 * Service managing modal dialogs and confirmations in Legatus Studio.
 */
public class DialogService {

    public static <C> ViewLoaderService.ViewResult<C> openModal(String fxmlPath, String title, Window owner) throws IOException {
        ViewLoaderService.ViewResult<C> result = ViewLoaderService.load(fxmlPath);

        Stage stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }

        Scene scene = new Scene(result.root());
        stage.setScene(scene);
        stage.show();

        return result;
    }

    public static boolean showConfirmation(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static void showError(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
