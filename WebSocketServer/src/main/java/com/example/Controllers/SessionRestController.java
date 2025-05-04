package com.example.Controllers;

import com.example.Services.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionRestController {
    private final SessionService sessions;

    public SessionRestController(SessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody(required = false) Map<String, String> body) {
        String initialText = body != null ? body.getOrDefault("text", "") : "";

        String id = sessions.createSession(initialText);

        return ResponseEntity.ok(Map.of("sessionId", id));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionId) {
        if (!sessions.exists(sessionId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(
                Map.of(
                        "sessionId", sessionId,
                        "users", sessions.getUsers(sessionId),
                        "text", sessions.getSessionText(sessionId)
                )
        );
    }

    @GetMapping("/{sessionId}/exists")
    public ResponseEntity<Boolean> exists(@PathVariable String sessionId) {
        return ResponseEntity.ok(sessions.exists(sessionId));
    }

    @PostMapping("/{sessionId}/user")
    public ResponseEntity<Map<String, String>> addUser(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, String> body) {
        String username = body != null ? body.get("username") : null;
        String assigned = sessions.addUser(sessionId, username);
        return ResponseEntity.ok(Map.of("username", assigned));
    }

    @DeleteMapping("/{sessionId}/user/{username}")
    public ResponseEntity<Void> removeUser(
            @PathVariable String sessionId,
            @PathVariable String username) {
        boolean removed = sessions.removeUser(sessionId, username);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PutMapping("/{sessionId}/text")
    public ResponseEntity<Void> updateText(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        sessions.updateSessionText(sessionId, body.get("text"));
        return ResponseEntity.noContent().build();
    }
}