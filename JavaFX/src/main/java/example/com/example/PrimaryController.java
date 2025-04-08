package example.com.example;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Stage;


import java.io.IOException;
public class PrimaryController {

    @FXML
    private TextField usernameField;

    @FXML
    private TextField roomIdField;


    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String roomId = roomIdField.getText().trim();
        //Check all fields are not empty
        if (username.isEmpty() || roomId.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Validation Error");
            alert.setHeaderText("Missing Information");
            alert.setContentText("Both username and room ID are required.");
            alert.showAndWait();
        } else {
            navigateToSecondaryView(username, roomId);
        }
    }

    
    private void navigateToSecondaryView(String username, String roomId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("secondary.fxml"));
            Scene secondaryScene = new Scene(loader.load());
    
            // Pass the StompSession to the SecondaryController
            SecondaryController secondaryController = loader.getController();
            secondaryController.initializeData(username, roomId);
    
            // Get the current stage and set the new scene
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(secondaryScene);
            stage.setTitle("Chat Room - " + roomId);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to Load Secondary View");
            alert.setContentText("An error occurred while loading the secondary view.");
            alert.showAndWait();
        }
    }
}


