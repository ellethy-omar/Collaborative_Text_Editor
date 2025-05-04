package com.example.Controllers;

import com.example.Services.SessionService;
import com.example.Services.SessionTokens;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/sessions")
public class SessionRestController {
    private final SessionService sessions;
    public SessionRestController(SessionService sessions) { this.sessions = sessions; }

    @PostMapping
    public ResponseEntity<Map<String,String>> create(@RequestBody(required=false) Map<String,String> body) {
        String initial = body != null ? body.getOrDefault("text", "") : "";
        SessionTokens tokens = sessions.createSession(initial);
        return ResponseEntity.ok(Map.of(
                "sessionId", tokens.editorToken(),
                "viewerCode", tokens.viewerToken()
        ));
    }

    @GetMapping("/{token}")
    public ResponseEntity<Map<String,Object>> getSession(@PathVariable String token) {
        if (!sessions.exists(token)) return ResponseEntity.notFound().build();
        String editor = sessions.getEditorFor(token);
        String viewer = sessions.isEditorToken(token)
                ? sessions.getAllTokensForEditor(editor).stream()
                .filter(t -> !t.equals(editor)).findFirst().orElse(editor)
                : token;
        return ResponseEntity.ok(Map.of(
                "editorCode", editor,
                "viewerCode", viewer,
                "sessionId", token,
                "text", sessions.getSessionText(token),
                "users", sessions.getUsers(token)
        ));
    }

    @GetMapping("/{token}/exists")
    public ResponseEntity<Boolean> exists(@PathVariable String token) {
        return ResponseEntity.ok(sessions.exists(token));
    }

    @PostMapping("/{token}/user")
    public ResponseEntity<Map<String,String>> addUser(@PathVariable String token,
                                                      @RequestBody(required=false) Map<String,String> body) {
        if (!sessions.isEditorToken(token))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String assigned = sessions.addUser(token, body != null ? body.get("username") : null);
        return ResponseEntity.ok(Map.of("username", assigned));
    }

    @DeleteMapping("/{token}/user/{username}")
    public ResponseEntity<Void> removeUser(@PathVariable String token,
                                           @PathVariable String username) {
        boolean removed = sessions.removeUser(token, username);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PutMapping("/{token}/text")
    public ResponseEntity<Void> updateText(@PathVariable String token,
                                           @RequestBody Map<String,String> body) {
        if (!sessions.isEditorToken(token))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        sessions.updateSessionText(token, body.get("text"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{token}")
    public ResponseEntity<Void> closeSession(@PathVariable String token) {
        if (!sessions.exists(token)) {
            return ResponseEntity.notFound().build();
        }
        // only the editor (owner) may tear it down:
        if (!sessions.isEditorToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        sessions.destroySession(token);
        return ResponseEntity.noContent().build();
    }
}