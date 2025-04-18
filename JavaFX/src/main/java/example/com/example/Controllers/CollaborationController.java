package example.com.example.Controllers;

import example.com.example.Controllers.handlers.DocumentHandler;
import example.com.example.Controllers.handlers.SessionHandler;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;

import java.util.ArrayDeque;
import java.util.Deque;

public class CollaborationController {
    @FXML private TextField sessionCodeField;
    @FXML private TextArea editorArea;
    @FXML private ListView<String> activeUsersList;

    private DocumentHandler documentHandler;
    private SessionHandler sessionHandler;

    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    private boolean isUndoOrRedo = false;

    private String username;
    private String sessionId;

    public void initSession(String sessionId, String initialText) {
        this.sessionId = sessionId;
        editorArea.setText(initialText);

        username = System.getProperty("user.name");
        documentHandler = new DocumentHandler(editorArea);
        sessionHandler = new SessionHandler(editorArea, activeUsersList, sessionId, username);
        sessionHandler.connectAndSubscribe();

        // Text edit listeners
        editorArea.textProperty().addListener((obs, oldText, newText) -> {
            if (!isUndoOrRedo) {
                undoStack.push(oldText);
                redoStack.clear();
            }
            sessionHandler.sendTextUpdate(newText);
        });
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

    @FXML
    private void handleUndo() {
        if (!undoStack.isEmpty()) {
            isUndoOrRedo = true;
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

    @FXML
    private void handleJoinSession() {
        String code = sessionCodeField.getText().trim();
        if (!code.isEmpty()) {
            sessionHandler.changeSession(code);
        }
    }

    @FXML
    private void handleExit() {
        Platform.exit();
    }
}
