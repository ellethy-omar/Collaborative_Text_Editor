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
            // 1) Create the session
            Map<String, String> resp = rest.postForObject(baseUrl, Map.of(), Map.class);
            String sessionId = resp.get("sessionId");

            // 2) Add a user *and get back* the assigned username
            Map<String,String> userResp = rest.postForObject(
                    baseUrl + "/" + sessionId + "/user",
                    Map.of(),
                    Map.class
            );
            String assignedUser = userResp.get("username");

            // 3) Hand off sessionId + assignedUser
            loadCollabView(sessionId, "", assignedUser);
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

            @SuppressWarnings("unchecked")
            Map<String,Object> sessionData = rest.getForObject(
                    baseUrl + "/" + code,
                    Map.class
            );
            String initialText = (String)sessionData.get("text");

            @SuppressWarnings("unchecked")
            Map<String,String> userResp = rest.postForObject(
                    baseUrl + "/" + code + "/user",
                    Map.of(),
                    Map.class
            );
            String assignedUser = userResp.get("username");

            loadCollabView(code, initialText, assignedUser);

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
            Map<String,String> resp = rest.postForObject(baseUrl, Map.of(), Map.class);
            String sessionId = resp.get("sessionId");

            Map<String,String> userResp = rest.postForObject(
                    baseUrl + "/" + sessionId + "/user",
                    Map.of(),
                    Map.class
            );
            String assignedUser = userResp.get("username");
            loadCollabView(sessionId, sb.toString(), assignedUser);
        } catch (Exception e) {
            showAlert("Unable to import and start session.");
        }
    }

    private void loadCollabView(String sessionId, String initialText, String username) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    App.class.getResource("collaboration.fxml")
            );
            Parent root = loader.load();
            CollaborationController ctrl = loader.getController();
            ctrl.initSession(sessionId, initialText, username);
            getStage().setScene(new Scene(root, 800, 600));
        } catch (IOException e) {
            showAlert("Could not open collaboration view");
        }
    }

    private void showAlert(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }
}
