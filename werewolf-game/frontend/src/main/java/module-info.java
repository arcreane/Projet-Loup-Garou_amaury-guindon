module com.werewolf {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.annotation;

    opens com.werewolf            to javafx.fxml;
    opens com.werewolf.controller to javafx.fxml;
    opens com.werewolf.model      to com.fasterxml.jackson.databind;

    exports com.werewolf;
    exports com.werewolf.controller;
    exports com.werewolf.model;
    exports com.werewolf.service;
}
