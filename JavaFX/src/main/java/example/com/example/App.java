package example.com.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {
    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        scene = new Scene(loadFXML("startUp"), 640, 480);
        stage.setScene(scene);
        stage.setTitle("Collaborative Text Editor");
        stage.show();
    }

    public static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        var resource = App.class.getResource(fxml + ".fxml");
        if (resource == null)
            throw new IOException("FXML file not found: " + fxml + ".fxml");
        FXMLLoader loader = new FXMLLoader(resource);
        return loader.load();
    }

    public static void main(String[] args) {
        launch();
    }
}