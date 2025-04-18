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
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SessionHandler {
    private final String wsUrl = "http://localhost:8080/ws";
    private final TextArea editorArea;
    private final ListView<String> usersList;
    private String sessionId;
    private final String username;
    private StompSession stompSession;

    public SessionHandler(TextArea editorArea,
                          ListView<String> usersList,
                          String sessionId,
                          String username) {
        this.editorArea = editorArea;
        this.usersList = usersList;
        this.sessionId = sessionId;
        this.username  = username;
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
            }
        });
    }

    private void subscribeEdit(StompSession session) {
        session.subscribe("/topic/session/" + sessionId + "/edit", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return Map.class; }
            @SuppressWarnings("unchecked")
            @Override public void handleFrame(StompHeaders h, Object p) {
                Map<String, String> m = (Map<String,String>)p;
                Platform.runLater(() -> editorArea.setText(m.get("text")));
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

    private void sendJoin(StompSession session) {
        session.send("/app/join/" + sessionId, Map.of("username", username));
    }

    public void sendTextUpdate(String text) {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.send("/app/edit/" + sessionId, Map.of("text", text));
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