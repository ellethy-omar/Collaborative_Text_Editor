module example.com.example {
    // JavaFX modules
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    // Extra libraries
    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.almasb.fxgl.all;

    // Spring for WebSocket
    requires spring.messaging;
    requires spring.websocket;
    requires spring.core;

    opens example.com.example to javafx.fxml;

    exports example.com.example;
}
