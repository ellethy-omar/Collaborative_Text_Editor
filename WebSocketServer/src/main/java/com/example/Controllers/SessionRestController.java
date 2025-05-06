package com.example.Controllers;

import com.example.Services.SessionService;
import com.example.Services.SessionTokens;
import example.com.example.Controllers.CRDT.OperationEntry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Queue;

@RestController
@RequestMapping("/api/sessions")
public class SessionRestController {
    private final SessionService sessions;
    public SessionRestController(SessionService sessions) { this.sessions = sessions; }

    @PostMapping
    public ResponseEntity<Map<String,String>> create(@RequestBody(required=false) Map<String,String> body) {
        String initial = body != null ? body.getOrDefault("text", "") : "";
        SessionTokens tokens = sessions.createSession(initial);
        String editor = tokens.editorToken();

        if (initial.isEmpty()) {
            System.out.println("initial is empty");
            return ResponseEntity.ok(Map.of(
                    "sessionId", tokens.editorToken(),
                    "viewerCode", tokens.viewerToken()
            ));
        }

        System.out.println(initial);

        Queue<Map<String,Object>> storage = sessions.getByToken(editor).getStorage();

        Object[] prevUserID = null;
        long ts = System.currentTimeMillis();

        for (int i = 0; i < initial.toCharArray().length; i ++) {
            ts++;

            Object[] userID = new Object[]{ "user1", ts };

            OperationEntry entry = new OperationEntry("insert", initial.toCharArray()[i], userID);

            entry.setParentID(prevUserID);

            storage.add(entry.toMap());

            prevUserID = userID;

            System.out.println(entry);
        }

        System.out.println(sessions.getByToken(editor).getStorage());

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

    @GetMapping("/{token}/getStorage")
    public  ResponseEntity<Map<String, Queue<Map<String, Object>>>> getFirstImpressions(@PathVariable String token) {
        if(!sessions.exists(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(Map.of("storage", sessions.getByToken(token).getStorage()));
    }
}