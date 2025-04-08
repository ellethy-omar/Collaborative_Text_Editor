package example.com.example;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import java.lang.reflect.Type;

import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.util.concurrent.ListenableFuture;

import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

public class SecondaryController {

    @FXML
    private TextArea chatArea;

    @FXML
    private TextField messageField;

    private StompSession stompSession;
    private String username;
    private String roomId;

    public void initializeData( String username, String roomId) {
        this.username = username;
        this.roomId = roomId;
        connectToWebSocket(username, roomId);
        // Subscribe to the chat room
        String topic = "/topic/chat/" + roomId;
        stompSession.subscribe(topic, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return ChatMessage.class; // Expected payload type
                }
                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    ChatMessage message = (ChatMessage) payload;
                    Platform.runLater(() -> chatArea.appendText("[" + message.getUsername() + "]: " + message.getContent() + "\n"));
                }
        });
    }
    private void connectToWebSocket(String username, String roomId) {
        try {
            WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
            stompClient.setMessageConverter(new MappingJackson2MessageConverter());

            String url = "ws://localhost:8080/ws";

            ListenableFuture<StompSession> future = stompClient.connect(url, new MyStompSessionHandler());
            this.stompSession = future.get(); // Blocking call, waits for the connection

        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Connection Error");
            alert.setHeaderText("Failed to Connect to WebSocket");
            alert.setContentText("An error occurred while connecting to the WebSocket server.");
            alert.showAndWait();
        }
    }

    @FXML
    private void handleSendMessage() {
        String content = messageField.getText().trim();
        if (!content.isEmpty()) {
            // Send the message to the WebSocket server
            String destination = "/app/chat/" + roomId;
            ChatMessage message = new ChatMessage();
            message.setUsername(username);
            message.setContent(content);
            stompSession.send(destination, message);
            messageField.clear();
        }
    }
}
class MyStompSessionHandler extends StompSessionHandlerAdapter {
    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        System.out.println("Connected to WebSocket server!");
    }

    @Override
    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
        System.err.println("An error occurred: " + exception.getMessage());
    }
}