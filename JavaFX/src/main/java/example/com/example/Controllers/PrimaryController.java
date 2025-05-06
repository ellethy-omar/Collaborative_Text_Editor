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
            // create session
            Map<String, String> resp = rest.postForObject(baseUrl, Map.of(), Map.class);
            String editorCode = resp.get("sessionId");
            String viewerCode = resp.get("viewerCode");

            // claim editor slot
            Map<String, String> userResp = rest.postForObject(
                    baseUrl + "/" + editorCode + "/user",
                    Map.of(), Map.class
            );
            String assignedUser = userResp.get("username");

            loadCollabView(editorCode, viewerCode, "", assignedUser, true);
        } catch (Exception e) {
            showAlert("Failed to create a new session. Please try again.");
        }
    }


    @FXML
    private void handleJoin() {
        String code = sessionCodeField.getText().trim();
        if (code.isEmpty()) { showAlert("Please enter a session code."); return; }

        try {
            Boolean exists = rest.getForObject(baseUrl + "/" + code + "/exists", Boolean.class);
            if (!Boolean.TRUE.equals(exists)) { showAlert("Session does not exist."); return; }

            Map<String,Object> meta = rest.getForObject(baseUrl + "/" + code, Map.class);
            String editorCode = (String) meta.get("editorCode");
            String viewerCode = (String) meta.get("viewerCode");
            String initialText = (String) meta.get("text");

            boolean isEditor = code.equals(editorCode);
            String assignedUser;
            if (isEditor) {
                Map<String,String> userResp = rest.postForObject(
                        baseUrl + "/" + editorCode + "/user",
                        Map.of(), Map.class
                );
                assignedUser = userResp.get("username");
            } else {
                assignedUser = "viewer";
            }

            loadCollabView(editorCode, viewerCode, initialText, assignedUser, isEditor);
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

        Map<String,String> createBody = Map.of("text", sb.toString());

        try {
            Map<String,String> resp = rest.postForObject(baseUrl, createBody, Map.class);
            String editorCode = resp.get("sessionId");
            String viewerCode = resp.get("viewerCode");

            Map<String,String> userResp = rest.postForObject(
                    baseUrl + "/" + editorCode + "/user",
                    Map.of(), Map.class
            );
            String assignedUser = userResp.get("username");

            loadCollabView(editorCode, viewerCode, sb.toString(), assignedUser, true);
        } catch (Exception e) {
            showAlert("Unable to import and start session.");
        }
    }

    private void loadCollabView(String editorCode,
                                String viewerCode,
                                String initialText,
                                String username,
                                boolean isEditor) {
        try {
            FXMLLoader loader = new FXMLLoader(App.class.getResource("collaboration.fxml"));
            Parent root = loader.load();
            CollaborationController ctrl = loader.getController();
            ctrl.initSession(editorCode, viewerCode, initialText, username, isEditor);
            getStage().setScene(new Scene(root, 800, 600));
        } catch (IOException e) {
            showAlert("Could not open collaboration view");
        }
    }

    private void showAlert(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }
}
