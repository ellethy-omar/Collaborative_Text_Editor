package com.example.Services;

import com.example.CRDT.Session;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SessionService {
    private final Map<String,Session> sessions = new ConcurrentHashMap<>();
    private final Set<String> editorTokens = ConcurrentHashMap.newKeySet();
    private final SecureRandom random = new SecureRandom();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private String generateCode() {
        String code;
        do {
            code = random.ints(6, 0, CHARS.length())
                    .mapToObj(i -> String.valueOf(CHARS.charAt(i)))
                    .collect(Collectors.joining());
        } while (sessions.containsKey(code));
        return code;
    }

    public SessionTokens createSession(String initialText) {
        String editor = generateCode();
        String viewer = generateCode();
        Session sess = new Session(editor);
        sess.setSessionText(initialText);
        sessions.put(editor, sess);
        sessions.put(viewer, sess);
        editorTokens.add(editor);
        return new SessionTokens(editor, viewer);
    }

    public boolean exists(String token) { return sessions.containsKey(token); }
    public boolean isEditorToken(String token) { return editorTokens.contains(token); }
    public Session getByToken(String token) { return sessions.get(token); }
    public String getEditorFor(String token) {
        Session s = sessions.get(token);
        return s == null ? null : s.getSessionId();
    }
    public Set<String> getAllTokensForEditor(String editor) {
        return sessions.entrySet().stream()
                .filter(e -> e.getValue().getSessionId().equals(editor))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public String addUser(String token, String username) {
        if (!isEditorToken(token)) throw new IllegalStateException("Cannot add user on viewer token");
        return getByToken(token).addUser(username);
    }
    public boolean removeUser(String token, String username) {
        Session s = getByToken(token);
        boolean removed = s != null && s.removeUser(username);
        if (removed) {
            // if no more users in this session → destroy it
            if (s.getUsers().isEmpty()) {
                destroySession(s.getSessionId());
                System.out.println("Destroyed session with token: " + token);
            }
        }
        return removed;
    }
    public Set<String> getUsers(String token) {
        var s = getByToken(token);
        return s != null ? s.getUsers() : Set.of();
    }

    public Queue<Map<String,Object>> getSessionStorage(String token) {
        var s = getByToken(token);
        return s!= null ? s.getStorage() : null;
    }

    public String getSessionText(String token) {
        var s = getByToken(token);
        return s != null ? s.getSessionText() : "";
    }
    public void updateSessionText(String token, String text) {
        if (!isEditorToken(token)) return;
        var s = getByToken(token);
        if (s != null) s.setSessionText(text);
    }

    public void destroySession(String editorToken) {
        // find all tokens for this editor
        Set<String> all = getAllTokensForEditor(editorToken);
        // remove each mapping
        for (String tok : all) {
            sessions.remove(tok);
            editorTokens.remove(tok);
        }
    }
}