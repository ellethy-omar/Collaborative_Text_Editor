package com.example;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {

    // Handles messages sent to "/chat/{roomId}" destination
    @MessageMapping("/chat/{roomId}")
    @SendTo("/topic/chat/{roomId}")
    public ChatMessage handleMessage(ChatMessage message, @DestinationVariable String roomId) {
        System.out.println("Received message in room " + roomId + ": " + message);
        return message;
    }
}
