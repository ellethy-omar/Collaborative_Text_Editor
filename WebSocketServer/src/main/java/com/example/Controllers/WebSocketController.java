package com.example.Controllers;

import com.example.services.SessionService;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class WebSocketController {
    private final SessionService sessions;
    private final SimpMessagingTemplate tpl;

    public WebSocketController(SessionService sessions, SimpMessagingTemplate tpl) {
        this.sessions = sessions;
        this.tpl = tpl;
    }

    @MessageMapping("/join/{sessionId}")
    public void join(@DestinationVariable String sessionId,
                     @Payload Map<String, String> payload) {
        if (!sessions.exists(sessionId)) return;
        String username = payload.get("username");
        sessions.addUser(sessionId, username);
        tpl.convertAndSend(
                "/topic/session/" + sessionId + "/users",
                sessions.getUsers(sessionId)
        );
    }

    @MessageMapping("/edit/{sessionId}")
    public void edit(@DestinationVariable String sessionId,
                     @Payload Map<String, String> payload) {
        if (!sessions.exists(sessionId)) return;
        String text = payload.get("text");
        sessions.updateSessionText(sessionId, text);
        tpl.convertAndSend(
                "/topic/session/" + sessionId + "/edit",
                Map.of("text", text)
        );
    }
}