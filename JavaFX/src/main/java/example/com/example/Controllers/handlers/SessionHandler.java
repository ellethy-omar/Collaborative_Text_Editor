package example.com.example.Controllers.handlers;

import javafx.application.Platform;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;

import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class SessionHandler {
    private final String wsUrl = "http://localhost:8080/ws";
    private final TextArea editorArea;
    private final ListView<String> usersList;
    private String sessionId;
    private final String username;
    private StompSession stompSession;
    private final BiConsumer<String,Integer> onRemoteCursor;  // new


    public SessionHandler(TextArea editorArea,
                          ListView<String> usersList,
                          String sessionId,
                          String username,
                          BiConsumer<String,Integer> onRemoteCursor
                          ) {
        this.editorArea = editorArea;
        this.usersList = usersList;
        this.sessionId = sessionId;
        this.username  = username;
        this.onRemoteCursor = onRemoteCursor;
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
                subscribeEdit(session);
                subscribeUsers(session);
                sendJoin(session);
                // after subscribeUsers(session);
                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(() -> {
                    if (editorArea == null || !editorArea.isFocused()) return;

                    int caret = editorArea.getCaretPosition();
                    System.out.println("Inside connection caret = " + caret);

                    Map<String, String> payload = new HashMap<>();
                    payload.put("username", username);
                    payload.put("caret", String.valueOf(caret));
                    payload.put("sessionId", sessionId);

                    sendCursorUpdate(caret);
                }, 0, 100, TimeUnit.MILLISECONDS);

                subscribeCursor(session);
            }
        });
    }

    private void subscribeEdit(StompSession session) {
        session.subscribe("/topic/session/" + sessionId + "/edit", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return Map.class; }
            @SuppressWarnings("unchecked")
            @Override public void handleFrame(StompHeaders h, Object p) {
                Map<String, String> m = (Map<String,String>)p;
                String sender = m.get("username");
                String text   = m.get("text");
                // don’t reapply your own edit
                if (username.equals(sender)) return;

                // preserve the user’s current caret position
                int caretPos = editorArea.getCaretPosition();
                Platform.runLater(() -> {
                    editorArea.setText(text);
                    System.out.println(text);
                    // put the caret back where it was
                    editorArea.positionCaret(Math.min(caretPos, text.length()));
                    System.out.println(caretPos);
                });
            }
        });
    }

    private void subscribeUsers(StompSession session) {
        session.subscribe("/topic/session/" + sessionId + "/users", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return Set.class; }
            @SuppressWarnings("unchecked")
            @Override public void handleFrame(StompHeaders h, Object p) {
                Set<String> s = (Set<String>)p;
                Platform.runLater(() -> {
                    usersList.getItems().setAll(s);
                });
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
                if (username.equals(sender)) return; // ignore own cursor update

                int caret = Integer.parseInt(m.get("caret"));

                // Call the callback on the JavaFX thread
                Platform.runLater(() -> onRemoteCursor.accept(sender, caret));
            }
        });
    }


    private void sendJoin(StompSession session) {
        session.send("/app/join/" + sessionId, Map.of("username", username));
    }

    public void sendTextUpdate(String text) {
        if (stompSession != null && stompSession.isConnected()) {
            System.out.println("Inside sendTextUpdate " +  text);
            int caretPos = editorArea.getCaretPosition();
            System.out.println("Caret: " + caretPos);
            stompSession.send(
                    "/app/edit/" + sessionId,
                    Map.of("username", username, "text", text)
            );
        }
    }

    public void sendCursorUpdate(int pos) {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.send(
                    "/app/cursor/" + sessionId,
                    Map.of("username", username, "cursor", pos)
            );
        }
    }

    public void changeSession(String newSessionId) {
        this.sessionId = newSessionId;
        if (stompSession != null) {
            stompSession.disconnect();
        }
        connectAndSubscribe();
    }
}