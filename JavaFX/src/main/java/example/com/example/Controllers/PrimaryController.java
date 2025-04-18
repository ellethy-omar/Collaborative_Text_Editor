package example.com.example.Controllers;

import example.com.example.App;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.util.Map;

public class PrimaryController {
    @FXML private TextField sessionCodeField;
    @FXML private TextArea  editorArea;  // only used for preview

    private final RestTemplate rest = new RestTemplate();
    private final String baseUrl = "http://localhost:8080/api/sessions";

    private Stage getStage() {
        return (Stage) sessionCodeField.getScene().getWindow();
    }

    @FXML
    private void handleNewDoc() {
        try {
            Map<String, String> resp = rest.postForObject(baseUrl, Map.of(), Map.class);
            String sessionId = resp.get("sessionId");
            rest.postForLocation(baseUrl + "/" + sessionId + "/user", Map.of());
            loadCollabView(sessionId, "");
        } catch (Exception e) {
            showAlert("Failed to create a new session. Please try again.");
        }
    }

    @FXML
    private void handleJoin() {
        String code = sessionCodeField.getText().trim();
        if (code.isEmpty()) {
            showAlert("Please enter a session code.");
            return;
        }
        try {
            Boolean exists = rest.getForObject(baseUrl + "/" + code + "/exists", Boolean.class);
            if (!Boolean.TRUE.equals(exists)) {
                showAlert("Session does not exist.");
                return;
            }
            rest.postForLocation(baseUrl + "/" + code + "/user", null);
            loadCollabView(code, "");
        } catch (Exception e) {
            showAlert("Unable to join session. Please try again.");
        }
    }

    @FXML
    private void handleBrowse() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Text File");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("TXT files", "*.txt")
        );
        File file = chooser.showOpenDialog(getStage());
        if (file == null) return;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException ex) {
            showAlert("Error reading file");
            return;
        }
        try {
            Map<String, String> resp = rest.postForObject(baseUrl, Map.of(), Map.class);
            String sessionId = resp.get("sessionId");
            rest.postForLocation(baseUrl + "/" + sessionId + "/user", Map.of());
            loadCollabView(sessionId, sb.toString());
        } catch (Exception e) {
            showAlert("Unable to import and start session.");
        }
    }

    private void loadCollabView(String sessionId, String initialText) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    App.class.getResource("collaboration.fxml")
            );
            Parent root = loader.load();
            CollaborationController ctrl = loader.getController();
            ctrl.initSession(sessionId, initialText);
            getStage().setScene(new Scene(root, 800, 600));
        } catch (IOException e) {
            showAlert("Could not open collaboration view");
        }
    }

    private void showAlert(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }
}
