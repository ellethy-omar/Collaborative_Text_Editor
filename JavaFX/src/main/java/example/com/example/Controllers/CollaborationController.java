package example.com.example.Controllers;

import example.com.example.Controllers.handlers.CursorHandler;
import example.com.example.Controllers.handlers.DocumentHandler;
import example.com.example.Controllers.handlers.SessionHandler;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PreDestroy;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/*
* Create route that sends a char every 100 ms, log in server and client
*
* */
public class CollaborationController {
    @FXML private Label editorCodeLabel;
    @FXML private Label viewerCodeLabel;
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
    private String viewerCode;
    private String editorCode;

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

    public void initSession(String editorCode,
                            String viewerCode,
                            String initialText,
                            String username,
                            boolean isEditor) {
        this.sessionId = editorCode;
        this.viewerCode = viewerCode;
        this.username = username;

        editorArea.setText(initialText);
        editorArea.setEditable(isEditor);
        currentSessionLabel.setText(isEditor ? "Editing Session" : "Viewing Session");

        editorCodeLabel.setVisible(isEditor);
        editorCodeLabel.setText("Editor Code: " + editorCode);
        viewerCodeLabel.setVisible(true);
        viewerCodeLabel.setText("Viewer Code: " + viewerCode);

        documentHandler = new DocumentHandler(editorArea);
        cursorHandler = new CursorHandler(editorArea, cursorOverlay);
        sessionHandler = new SessionHandler(
                editorArea, activeUsersList,
                sessionId, username,
                cursorHandler::updateCursor
        );
        sessionHandler.connectAndSubscribe();

        if (isEditor) {
//            editorArea.textProperty().addListener((o, oldT, newT) -> {
//                sessionHandler.sendTextUpdate(newT);
//            });
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
        leaveSession();
        Platform.exit();
    }

    @PreDestroy
    private void onControllerDestroy() {
        leaveSession();
    }

    private void leaveSession() {
        try {
            String url = String.format("%s/%s/user/%s",
                    baseUrl, sessionId, username);
            rest.delete(url);
        } catch (Exception ex) {
            System.err.println("Could not notify server of leave: " + ex.getMessage());
        }
    }
}
