package com.example;

import com.example.Services.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener {

    @Autowired
    private SessionService sessions;

    // 3ashan a3raf ab2a afdy el sessions odam 3ashan myb2ash 3andy memory leaks
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        // these get populated in your join handler
        String token    = (String) sha.getSessionAttributes().get("token");
        String username = (String) sha.getSessionAttributes().get("username");
        if (token != null && username != null) {
            sessions.removeUser(token, username);
            // SessionService.removeUser will auto‑destroy the session if it becomes empty
        }
    }
}
