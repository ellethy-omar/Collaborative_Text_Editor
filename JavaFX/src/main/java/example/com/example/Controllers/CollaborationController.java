package example.com.example.Controllers;

import example.com.example.Controllers.handlers.CursorHandler;
import example.com.example.Controllers.handlers.DocumentHandler;
import example.com.example.Controllers.handlers.SessionHandler;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

public class CollaborationController {
    @FXML private TextField sessionCodeField;
    @FXML private TextArea editorArea;
    @FXML private ListView<String> activeUsersList;
    @FXML private Label currentSessionLabel;
    @FXML private Pane cursorOverlay;
    private CursorHandler cursorHandler;

    private final RestTemplate rest = new RestTemplate();
    private final String       baseUrl = "http://localhost:8080/api/sessions";


    private DocumentHandler documentHandler;
    private SessionHandler sessionHandler;

    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    private boolean isUndoOrRedo = false;

    private String username;
    private String sessionId;

    /** JavaFX initialize: set up keyboard shortcuts. */
    /** Until I figure out why this undo removes the entire thing no one ask me

     @FXML
    public void initialize() {
        Platform.runLater(() -> {
            Scene scene = editorArea.getScene();
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN),
                    this::handleUndo
            );
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                    this::handleRedo
            );
        });
    }
    */


    public void initSession(String sessionId, String initialText, String inputUserName) {
        this.sessionId = sessionId;
        editorArea.setText(initialText);

        currentSessionLabel.setText(sessionId);

        this.username = inputUserName;

        documentHandler = new DocumentHandler(editorArea);
        cursorHandler = new CursorHandler(editorArea, cursorOverlay);
        sessionHandler = new SessionHandler(
                editorArea,
                activeUsersList,
                sessionId,
                username,
                cursorHandler::updateCursor    // new callback
        );
        sessionHandler.connectAndSubscribe();

        // Text edit listeners
        editorArea.textProperty().addListener((obs, oldText, newText) -> {
            if (!isUndoOrRedo) {
                if (undoStack.size() >= 3) {
                    undoStack.removeLast();
                }
                undoStack.push(oldText);
                redoStack.clear();
            }
            sessionHandler.sendTextUpdate(newText);
        });
    }

    @FXML
    private void handleJoinSession() {
        String newCode = sessionCodeField.getText().trim();
        if (newCode.isEmpty()) {
            showAlert("Please enter a session code.");
            return;
        }

        try {
            // 1) Validate it exists
            Boolean exists = rest.getForObject(baseUrl + "/" + newCode + "/exists", Boolean.class);
            if (!Boolean.TRUE.equals(exists)) {
                showAlert("Session does not exist.");
                return;
            }

            // 2) Fetch current session state (text + users)
            @SuppressWarnings("unchecked")
            Map<String,Object> sessionData = rest.getForObject(baseUrl + "/" + newCode, Map.class);
            String initialText = (String) sessionData.get("text");

            // 3) Claim your user‑slot
            @SuppressWarnings("unchecked")
            Map<String,String> userResp = rest.postForObject(
                    baseUrl + "/" + newCode + "/user",
                    Map.of(),
                    Map.class
            );
            String assignedUser = userResp.get("username");

            // 4) Switch your handler’s subscriptions
            this.username = assignedUser;
            sessionHandler.changeSession(newCode);

            // 5) Reset the editor state
            this.sessionId = newCode;
            currentSessionLabel.setText(newCode);

            editorArea.setText(initialText);
            undoStack.clear();
            redoStack.clear();

        } catch (Exception e) {
            showAlert("Unable to join session. Please try again.");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleNewDocument() {
        documentHandler.setCurrentFileName(null);
        editorArea.clear();
        undoStack.clear();
        redoStack.clear();
    }

    @FXML
    private void handleOpenDocument() {
        documentHandler.loadFile(editorArea.getScene().getWindow());
    }

    @FXML
    private void handleSaveDocument() {
        documentHandler.saveFile(editorArea.getScene().getWindow());
    }

    @FXML private void handleUndo() {
        if (!undoStack.isEmpty()) {
            isUndoOrRedo = true;
            // cap redo stack
            if (redoStack.size() >= 3) {
                redoStack.removeLast();
            }
            redoStack.push(editorArea.getText());
            editorArea.setText(undoStack.pop());
            isUndoOrRedo = false;
        }
    }

    @FXML
    private void handleRedo() {
        if (!redoStack.isEmpty()) {
            isUndoOrRedo = true;
            undoStack.push(editorArea.getText());
            editorArea.setText(redoStack.pop());
            isUndoOrRedo = false;
        }
    }

    private void showAlert(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }

    @FXML
    private void handleExit() {
        Platform.exit();
    }
}
