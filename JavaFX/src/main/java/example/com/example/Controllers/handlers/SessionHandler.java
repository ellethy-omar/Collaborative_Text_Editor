package example.com.example.Controllers.handlers;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
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
    private final String wsUrl = "http://localhost:8080/ws";
    private final TextArea editorArea;
    private final ListView<String> usersList;
    private String sessionId;
    private final String username;
    private StompSession stompSession;
    private final BiConsumer<String, Integer> onRemoteCursor;
    private final List<OperationEntry> operationLog = new ArrayList<>();
    private final List<OperationEntry> operationsToBeSent = new ArrayList<>();

    public SessionHandler(TextArea editorArea,
                         ListView<String> usersList,
                         String sessionId,
                         String username,
                         BiConsumer<String, Integer> onRemoteCursor) {
        this.editorArea = editorArea;
        this.usersList = usersList;
        this.sessionId = sessionId;
        this.username = username;
        this.onRemoteCursor = onRemoteCursor;
        String[] previousText = {editorArea.getText()};
        
        editorArea.textProperty().addListener((observable, oldValue, newValue) -> {
            long timestamp = Instant.now().toEpochMilli();
                
            if (newValue.length() > oldValue.length()) {
                int length = newValue.length() - oldValue.length();
                int start = findDifferencePosition(oldValue, newValue);
                String insertedText = newValue.substring(start, start + length);
                for (int i = 0; i < length; i++) {
                    char c = insertedText.charAt(i);
                    if (c == '\r' || c == '\n') c = '\n';
                    handleTextOperation("insert", c, start + i, timestamp);
                }
            } else if (newValue.length() < oldValue.length()) {
                int length = oldValue.length() - newValue.length();
                int start = findDifferencePosition(newValue, oldValue);
                String deletedText = oldValue.substring(start, start + length);
                for (int i = length - 1; i >= 0; i--) {
                    char c = deletedText.charAt(i);
                    if (c == '\r' || c == '\n') c = '\n';
                    handleTextOperation("delete", c, start + i, timestamp);
                }
            }
            previousText[0] = newValue;
        });
    }

    private void handleTextOperation(String operation, char character, int position, long timestamp) {
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
            operationLog.add(position, entry);
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
        
//        System.out.println("Operation Log (" + operation + " at " + position + "):");
//        for (int i = 0; i < operationLog.size(); i++) {
//            System.out.println("[" + i + "]: " + operationLog.get(i));
//        }
//
//        System.out.println("OperationsToBeSent (" + operationsToBeSent.size() + " entries):");
//        for (int i = 0; i < operationsToBeSent.size(); i++) {
//            System.out.println("[" + i + "]: " + operationsToBeSent.get(i));
//        }
    }

    private int findDifferencePosition(String oldText, String newText) {
        int oldLen = oldText.length();
        int newLen = newText.length();
        
        // Check for prepend (new text ends with old text) FIRST
        if (newLen > oldLen && (oldText.isEmpty() ? newLen == 1 : newText.endsWith(oldText))) {
            return 0;
        }
        // Check for append (new text starts with old text)
        if (newLen > oldLen && newText.startsWith(oldText)) {
            return oldLen;
        }
        // Check for deletion from start (old text ends with new text)
        if (newLen < oldLen && oldText.endsWith(newText)) {
            return 0;
        }
        // Check for deletion from end (old text starts with new text)
        if (newLen < oldLen && oldText.startsWith(newText)) {
            return newLen;
        }
        // Find first differing character
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
        client.connect(wsUrl, new WebSocketHttpHeaders(), new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders headers) {
                stompSession = session;
                subscribeUsers(session);
                subscribeAckOperations(session);
                sendJoin(session);
                
                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(() -> {
                    if (editorArea == null || !editorArea.isFocused()) return;
                    int caret = editorArea.getCaretPosition();
                    sendCursorUpdate(caret);
                }, 0, 100, TimeUnit.MILLISECONDS);

                scheduler.scheduleAtFixedRate(() -> {
                    if (stompSession != null && stompSession.isConnected()) {
                        long ts = Instant.now().toEpochMilli();

                        // 1) convert your static entries to a List of Maps
                        List<Map<String,Object>> opsPayload = operationsToBeSent.stream()
                                .map(OperationEntry::toMap)
                                .collect(Collectors.toList());

                        // 2) build and send the message
                        Map<String,Object> payload = Map.of(
                                "username",   username,
                                "operations", opsPayload,
                                "timestamp",  ts
                        );
                        stompSession.send("/app/operation/" + sessionId, payload);
                        operationsToBeSent.clear();

                        System.out.printf("→ SENT operations %s @ %d%n", opsPayload, ts);
                    }
                }, 0, 2000, TimeUnit.MILLISECONDS);
                
                subscribeCursor(session);
            }
        });
    }

    private void subscribeAckOperations(StompSession session) {
        session.subscribe(
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
                        Number tsNum              = (Number) m.get("timestamp");
                        String status             = (String) m.get("status");
                        Instant ts                = Instant.ofEpochMilli(tsNum.longValue());

                        System.out.printf(
                                "← ACK of %d ops from %s @ %s [%s]: %s%n",
                                ops.size(), user, ts, status, ops
                        );
                    }
                }
        );
    }

    private void subscribeUsers(StompSession session) {
        session.subscribe("/topic/session/" + sessionId + "/users", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return Set.class; }
            @SuppressWarnings("unchecked")
            @Override public void handleFrame(StompHeaders h, Object p) {
                Set<String> users = (Set<String>)p;
                Platform.runLater(() -> usersList.getItems().setAll(users));
            }
        });
    }

    private void subscribeCursor(StompSession session) {
        session.subscribe("/topic/session/" + sessionId + "/cursor", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return Map.class; }
            @SuppressWarnings("unchecked")
            @Override public void handleFrame(StompHeaders h, Object p) {
                Map<String, String> m = (Map<String, String>) p;
                String sender = m.get("username");
                if (username.equals(sender)) return;
                
                int caret = Integer.parseInt(m.get("caret"));
                Platform.runLater(() -> onRemoteCursor.accept(sender, caret));
            }
        });
    }

    private void sendJoin(StompSession session) {
        session.send("/app/join/" + sessionId, Map.of("username", username));
    }

    public void sendCursorUpdate(int pos) {
        stompSession.send("/app/cursor/" + sessionId,
            Map.of("username", username, "cursor", pos));
    }
}

class OperationEntry {
    private String operation;
    private char character;
    // private int position;
    private Object[] userID;
    private Object[] parentID;

    public OperationEntry(String operation, char character, Object[] userID) {
        this.operation = operation;
        this.character = character;
        // this.position = position;
        this.userID = userID;
    }

    public String getOperation() { return operation; }
    public char getCharacter() { return character; }
    // public int getPosition() { return position; }
    public Object[] getUserID() { return userID; }
    public Object[] getParentID() { return parentID; }

    public void setParentID(Object[] parentID) {
        this.parentID = parentID;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("operation", operation);
        map.put("character", String.valueOf(character));
        // map.put("position", String.valueOf(position));
        map.put("userID", userID);
        map.put("parentID", parentID);
        return map;
    }

    @Override
    public String toString() {
        return String.format("{operation=%s, character=%c, " +
                             "userID=%s, parentID=%s}",
            operation,
            character,
            // position,
            Arrays.toString(userID),
            parentID != null ? Arrays.toString(parentID) : "null"
        );
    }
}