package com.legatus.studio.controllers;

import java.io.IOException;
import javafx.fxml.FXML;
import com.legatus.studio.Application;

public class SecondaryController {

    @FXML
    private void switchToPrimary() throws IOException {
        Application.setRoot("primary");
    }
}