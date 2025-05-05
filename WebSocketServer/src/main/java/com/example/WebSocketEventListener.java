package com.example;

import com.example.Services.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.core.AbstractDestinationResolvingMessagingTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Set;

@Component
public class WebSocketEventListener {

    @Autowired
    private SessionService sessions;
    private final SimpMessagingTemplate tpl;

    @Autowired
    public WebSocketEventListener(SessionService sessions,
                                  SimpMessagingTemplate tpl) {
        this.sessions = sessions;
        this.tpl      = tpl;
    }

    // 3ashan a3raf ab2a afdy el sessions odam 3ashan myb2ash 3andy memory leaks
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());

        String token    = (String) sha.getSessionAttributes().get("token");
        String username = (String) sha.getSessionAttributes().get("username");
        if (token != null && username != null) {
            sessions.removeUser(token, username);
            // SessionService.removeUser will auto‑destroy the session if it becomes empty
            Set<String> users = sessions.getUsers(token);

            // 3) Broadcast to every socket in this “room”
            String editor = sessions.getEditorFor(token);
            sessions.getAllTokensForEditor(editor).forEach(t ->
                    tpl.convertAndSend("/topic/session/" + t + "/users", users)
            );
        }
    }
}
