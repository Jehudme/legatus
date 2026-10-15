module com.legatus.studio {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;

    requires atlantafx.base;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.feather;

    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    requires java.net.http;
    requires java.desktop;

    opens com.legatus.studio to javafx.fxml;
    opens com.legatus.studio.controllers to javafx.fxml;

    exports com.legatus.studio;
    exports com.legatus.studio.controllers;
    exports com.legatus.studio.service;
}
