package com.example.Controllers;

import com.example.Services.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Controller
public class WebSocketController {
    private static final Logger log = LoggerFactory.getLogger(WebSocketController.class);

    private final SessionService sessions;
    private final SimpMessagingTemplate tpl;

    public WebSocketController(SessionService sessions, SimpMessagingTemplate tpl) {
        this.sessions = sessions;
        this.tpl = tpl;
    }

    @MessageMapping("/join/{token}")
    public void join(@DestinationVariable String token, @Payload Map<String,String> p) {
        if (!sessions.exists(token)) return;
        String editor = sessions.getEditorFor(token);
        var users = sessions.getUsers(token);
        List<String> targets = sessions.getAllTokensForEditor(editor).stream().toList();
        targets.forEach(t -> tpl.convertAndSend("/topic/session/"+t+"/users", users));
    }

    @MessageMapping("/edit/{token}")
    public void edit(@DestinationVariable String token, @Payload Map<String,String> p) {
        if (!sessions.isEditorToken(token)) return;
        String editor = sessions.getEditorFor(token);
        String user = p.get("username");
        String text = p.get("text");
        sessions.updateSessionText(token, text);
        var msg = Map.of("username",user,"text",text);
        sessions.getAllTokensForEditor(editor)
                .forEach(t -> tpl.convertAndSend("/topic/session/"+t+"/edit", msg));
    }

    @MessageMapping("/cursor/{token}")
    public void cursor(@DestinationVariable String token, @Payload Map<String,Object> p) {
        if (!sessions.isEditorToken(token)) return;
        String editor = sessions.getEditorFor(token);
        sessions.getAllTokensForEditor(editor)
                .forEach(t -> tpl.convertAndSend("/topic/session/"+t+"/cursors", p));
    }

    @MessageMapping("/operation/{token}")
    public void operation(@DestinationVariable String token,
                           @Payload Map<String,Object> payload) {
        if (!sessions.exists(token)) {
            log.warn("Unknown token {}", token);
            return;
        }

        String user = (String) payload.get("username");
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> ops = (List<Map<String,Object>>) payload.get("operations");
        long ts = ((Number) payload.get("timestamp")).longValue();
        Instant instantTs = Instant.ofEpochMilli(ts);

        // 1) Server-side log of the received OperationEntry array
        log.info("Received {} ops from {} @ {}: {}", ops.size(), user, instantTs, ops);

        var session = sessions.getByToken(token);
        if (session != null) {
            session.getStorage().addAll(ops);
        }

        // 2) Broadcast the operations list to all clients
        tpl.convertAndSend(
                "/topic/session/" + token + "/operation",
                Map.of(
                        "username",   user,
                        "operations", ops,
                        "timestamp",  ts
                )
        );

        // 3) Send an ACK back carrying the same operations list
        tpl.convertAndSend(
                "/topic/session/" + token + "/operation/ack",
                Map.of(
                        "username",   user,
                        "operations", ops,
                        "timestamp",  ts,
                        "status",     "SERVER_RECEIVED"
                )
        );

        System.out.println("Logged and ACKed operations for token " + token);
    }
}