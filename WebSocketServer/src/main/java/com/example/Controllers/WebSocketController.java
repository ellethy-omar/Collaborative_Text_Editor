package com.example.Controllers;

import com.example.Services.SessionService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

@Controller
public class WebSocketController {
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
}