package com.legatus.studio.controllers;

import java.io.IOException;
import javafx.fxml.FXML;
import com.legatus.studio.Application;

public class PrimaryController {

    @FXML
    private void switchToSecondary() throws IOException {
        Application.setRoot("secondary");
    }
}
