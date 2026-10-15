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
    requires static jdk.httpserver;

    opens com.legatus.studio to javafx.fxml;
    opens com.legatus.studio.controllers to javafx.fxml;
    opens com.legatus.studio.config to com.fasterxml.jackson.databind;
    opens com.legatus.studio.model to com.fasterxml.jackson.databind, javafx.base;
    opens com.legatus.studio.protocol to com.fasterxml.jackson.databind;

    exports com.legatus.studio;
    exports com.legatus.studio.controllers;
    exports com.legatus.studio.service;
    exports com.legatus.studio.config;
    exports com.legatus.studio.security;
    exports com.legatus.studio.model;
    exports com.legatus.studio.protocol;
    exports com.legatus.studio.transport;
    exports com.legatus.studio.viewmodel;
}
