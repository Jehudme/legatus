module com.legatus.studio {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.legatus.studio to javafx.fxml;
    opens com.legatus.studio.controllers to javafx.fxml;
    exports com.legatus.studio;
    exports com.legatus.studio.controllers;
}
