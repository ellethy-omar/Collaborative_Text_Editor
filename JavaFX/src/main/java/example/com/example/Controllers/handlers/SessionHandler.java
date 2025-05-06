package example.com.example.Controllers.handlers;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static example.com.example.Controllers.utils.StorageConverter.*;
import example.com.example.Controllers.CRDT.OperationEntry;
import example.com.example.Controllers.CRDT.TreeCrdt;
import javafx.beans.value.ChangeListener;
import javafx.scene.layout.Pane;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import java.util.Timer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import javafx.application.Platform;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;

public class SessionHandler {
    private final TextArea editorArea;
    private final ListView<String> usersList;
    private final CursorHandler cursorHandler;
    private final String sessionId;
    private final String username;
    private StompSession stompSession;
    private List<OperationEntry> operationLog = new ArrayList<>();
    private final List<OperationEntry> operationsToBeSent = new ArrayList<>();
    private final ChangeListener<String> textChangeListener;
    private TreeCrdt crdt = new TreeCrdt();
    private final Deque<String>    undoOps   = new ArrayDeque<>();
    private final Deque<Character> undoChars = new ArrayDeque<>();
    private final Deque<Integer>   undoPos   = new ArrayDeque<>();

    private final Deque<String>    redoOps   = new ArrayDeque<>();
    private final Deque<Character> redoChars = new ArrayDeque<>();
    private final Deque<Integer>   redoPos   = new ArrayDeque<>();

    // flag to distinguish real user input vs. our undo/redo edits
    private boolean programmaticChange = false;

    public SessionHandler(TextArea editorArea,
                          ListView<String> usersList,
                          Pane cursorOverlay,
                          String sessionId,
                          String username) {
        this.editorArea = editorArea;
        this.usersList = usersList;
        this.sessionId = sessionId;
        this.username = username;
        String[] previousText = {editorArea.getText()};
        this.cursorHandler = new CursorHandler(editorArea, cursorOverlay);

        textChangeListener = (observable, oldValue, newValue) -> {

            long baseTimestamp = Instant.now().toEpochMilli();

            if (newValue.length() > oldValue.length()) {
                int length = newValue.length() - oldValue.length();
                int start = findDifferencePosition(oldValue, newValue);
                String insertedText = newValue.substring(start, start + length);

                // Use incrementing timestamps for each character
                for (int i = 0; i < length; i++) {
                    char c = insertedText.charAt(i);
                    if (c == '\r' || c == '\n') c = '\n';
                    // Add i milliseconds to the base timestamp
                    handleTextOperation("insert", c, start + i, baseTimestamp + i);
                }
            } else if (newValue.length() < oldValue.length()) {
                int length = oldValue.length() - newValue.length();
                int start = findDifferencePosition(newValue, oldValue);
                String deletedText = oldValue.substring(start, start + length);

                // Use incrementing timestamps for deletes too
                for (int i = length - 1; i >= 0; i--) {
                    char c = deletedText.charAt(i);
                    if (c == '\r' || c == '\n') c = '\n';
                    // Add index milliseconds to base timestamp
                    handleTextOperation("delete", c, start + i, baseTimestamp + (length - 1 - i));
                }
            }
        };

        editorArea.textProperty().addListener(textChangeListener);

        editorArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (stompSession != null && stompSession.isConnected() && editorArea.isFocused()) {
                sendCursorUpdate(newPos.intValue());
            }
        });
    }

    public void undo() {
        if (undoOps.isEmpty()) return;

        // pop the last user op
        String lastOp = undoOps.pop();
        char   c      = undoChars.pop();
        int    pos    = undoPos.pop();

        // flip insert↔delete
        String inverse = lastOp.equals("insert") ? "delete" : "insert";

        // mark for the listener
        programmaticChange = true;
        if (inverse.equals("insert")) {
            editorArea.insertText(pos, String.valueOf(c));
        } else {
            editorArea.deleteText(pos, pos + 1);
        }
        programmaticChange = false;
    }

    public void redo() {
        if (redoOps.isEmpty()) return;

        String op = redoOps.pop();
        char   c  = redoChars.pop();
        int    pos= redoPos.pop();

        programmaticChange = true;
        if (op.equals("insert")) {
            editorArea.insertText(pos, String.valueOf(c));
        } else {
            editorArea.deleteText(pos, pos + 1);
        }
        programmaticChange = false;
    }

    private void handleTextOperation(String operation, char character, int position, long timestamp) {
        if (!programmaticChange) {
            // real typing → you can undo this
            undoOps.push(operation);
            undoChars.push(character);
            undoPos.push(position);
            // fresh user op invalidates any redo history
            redoOps.clear(); redoChars.clear(); redoPos.clear();
        } else {
            // this came from our undo() call → make it redo-able
            redoOps.push(operation);
            redoChars.push(character);
            redoPos.push(position);
        }

        OperationEntry entry = new OperationEntry(
            operation,
            character,
            // position,
            new Object[]{username, timestamp}
        );

        if ("insert".equals(operation)) {
            // If inserting at position 0, parentID should always be null
            if (position == 0) {
                entry.setParentID(null);
            } else if (position > 0 && position <= operationLog.size()) {
                entry.setParentID(operationLog.get(position - 1).getUserID());
            }
            int idx = Math.min(position, operationLog.size());
            operationLog.add(idx, entry);
            // operationsToBeSent.add(entry);

        } else if ("delete".equals(operation)) {
            if (position < operationLog.size()) {
                OperationEntry original = operationLog.remove(position);
                entry = new OperationEntry(
                        "delete",  // Set operation directly to "delete"
                        original.getCharacter(),
                        original.getUserID()
                );
                entry.setParentID(original.getParentID());
            }
        }

        operationsToBeSent.add(entry);
    }

    private int findDifferencePosition(String oldText, String newText) {
        int oldLen = oldText.length();
        int newLen = newText.length();
        
        if (newLen > oldLen && (oldText.isEmpty() ? newLen == 1 : newText.endsWith(oldText))) {
            return 0;
        }
        if (newLen > oldLen && newText.startsWith(oldText)) {
            return oldLen;
        }
        if (newLen < oldLen && oldText.endsWith(newText)) {
            return 0;
        }
        if (newLen < oldLen && oldText.startsWith(newText)) {
            return newLen;
        }

        int minLength = Math.min(oldLen, newLen);
        for (int i = 0; i < minLength; i++) {
            if (oldText.charAt(i) != newText.charAt(i)) {
                return i;
            }
        }
        return minLength;
    }

    public void connectAndSubscribe() {
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJs = new SockJsClient(transports);
        WebSocketStompClient client = new WebSocketStompClient(sockJs);
        client.setMessageConverter(new MappingJackson2MessageConverter());
        String wsUrl = "http://localhost:8080/ws";
        client.connect(wsUrl, new WebSocketHttpHeaders(), new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders headers) {
                stompSession = session;

                subscribeUsers();
                subscribeAckOperations();
                sendJoin();

                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(() -> {
                    sendArrayOfOperations();

                }, 0, 100, TimeUnit.MILLISECONDS);

                subscribeCursor();
            }
        });

    }

    private void subscribeAckOperations() {
        stompSession.subscribe(
                "/topic/session/" + sessionId + "/operation/ack",
                new StompFrameHandler() {
                    @Override public Type getPayloadType(StompHeaders headers) {
                        return Map.class;
                    }
                    @SuppressWarnings("unchecked")
                    @Override public void handleFrame(StompHeaders headers, Object payload) {
                        Map<String,Object> m       = (Map<String,Object>) payload;
                        String user               = (String) m.get("username");
                        @SuppressWarnings("unchecked")
                        List<Map<String,Object>> ops = (List<Map<String,Object>>) m.get("operations");
                        if (ops == null || ops.isEmpty()) {
                            return;
                        }

                        Number tsNum              = (Number) m.get("timestamp");
                        String status             = (String) m.get("status");
                        Instant ts                = Instant.ofEpochMilli(tsNum.longValue());

                            List<OperationEntry> operationEntries = convertToOperationEntries(ops);

                        Platform.runLater(() -> {
                            int oldCaret = editorArea.getCaretPosition();
                            String oldText = editorArea.getText();

                            editorArea.textProperty().removeListener(textChangeListener);

                            crdt.integrateAll(operationEntries);
                            String newText = crdt.getSequenceText();
                            operationLog = crdt.exportVisibleOperations();

                            int newCaret = calculateNewCaretPosition(oldText, newText, oldCaret);

                            editorArea.setText(newText);
                            editorArea.positionCaret(Math.min(newCaret, newText.length()));

                            editorArea.textProperty().addListener(textChangeListener);
                        });
                    }
                }
        );
    }

    private void subscribeUsers() {
        stompSession.subscribe("/topic/session/" + sessionId + "/users", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return Set.class; }
            @SuppressWarnings("unchecked")
            @Override public void handleFrame(StompHeaders h, Object p) {
                Set<String> users = (Set<String>)p;
                Platform.runLater(() -> usersList.getItems().setAll(users));

                Set<String> currentUsers = new HashSet<>(users);
                Set<String> previousUsers = new HashSet<>(usersList.getItems());
                previousUsers.removeAll(currentUsers);

                // Remove cursors for users who left
                for (String leftUser : previousUsers) {
                    if (!leftUser.equals(username)) {
                        cursorHandler.removeCursor(leftUser);
                    }
                }
            }
        });
    }

    private void subscribeCursor() {
        stompSession.subscribe("/topic/session/" + sessionId + "/cursor", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return Map.class; }
            @SuppressWarnings("unchecked")
            @Override public void handleFrame(StompHeaders h, Object p) {
                Platform.runLater(() -> {  // Add Platform.runLater to update UI on FX thread
                    Map<String, String> m = (Map<String, String>) p;
                    String sender = m.get("username");
                    if (username.equals(sender)) return;

                    int caret = Integer.parseInt(m.get("caret"));
                    cursorHandler.updateCursor(sender, caret);
                });
            }
        });
    }

    private void sendJoin() {
        stompSession.send("/app/join/" + sessionId, Map.of("username", username));
        fetchAndApplyStorage();
    }

    public void sendCursorUpdate(int pos) {
        // Check if session is active before sending
        if (stompSession != null && stompSession.isConnected()) {
            try {
                // Use synchronized block to prevent concurrent send operations
                synchronized (stompSession) {
                    stompSession.send("/app/cursor/" + sessionId,
                            Map.of("username", username, "caret", String.valueOf(pos)));
                }
            } catch (Exception e) {
                // Handle exceptions without crashing the application
                System.err.println("Error sending cursor update: " + e.getMessage());
            }
        }
    }

    public void sendArrayOfOperations() {
        if (operationsToBeSent.isEmpty()) {
            return;
        }
        long ts = Instant.now().toEpochMilli();

        List<Map<String,Object>> opsPayload = operationsToBeSent.stream()
                .map(OperationEntry::toMap)
                .collect(Collectors.toList());

        Map<String,Object> payload = Map.of(
                "username",   username,
                "operations", opsPayload,
                "timestamp",  ts
        );
        stompSession.send("/app/operation/" + sessionId, payload);

        operationsToBeSent.clear();
    }

    public void fetchAndApplyStorage() {

        RestTemplate rest = new RestTemplate();

        // Use a proper response type to handle the nested structure
        ResponseEntity<Map> response = rest.getForEntity(
                "http://localhost:8080/api/sessions/" + sessionId + "/getStorage",
                Map.class
        );

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            // Extract the storage value
            Object storageObj = response.getBody().get("storage");

            if (storageObj != null) {
                try {
                    // Temporarily disable text listener to prevent operation creation
                    editorArea.textProperty().removeListener(textChangeListener);

                    // Convert the storage object to a list of operation entries
                    List<OperationEntry> operations = convertStorageToOperationEntries(storageObj);

                    // Now you can integrate the operations
                    crdt.integrateAll(operations);

                    // Update the editor text
                    Platform.runLater(() -> {
                        editorArea.setText(crdt.getSequenceText());
                        operationLog = crdt.exportVisibleOperations();
                        editorArea.textProperty().addListener(textChangeListener);
                    });
                } catch (Exception e) {
                    System.err.println("Error processing storage data: " + e.getMessage());
                    e.printStackTrace();

                    editorArea.textProperty().addListener(textChangeListener);
                }
            } else {
                System.err.println("Storage data is null");
            }
        } else {
            System.err.println("Failed to fetch storage data: " + response.getStatusCode());
        }
    }
}