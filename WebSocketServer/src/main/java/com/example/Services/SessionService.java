package com.example.services;

import com.example.CRDT.Session;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public String createSession(String initialText) {
        String code;
        do {
            code = random.ints(6, 0, CHARS.length())
                    .mapToObj(i -> String.valueOf(CHARS.charAt(i)))
                    .reduce("", String::concat);
        } while (sessions.containsKey(code));

        Session session = new Session(code);
        session.setSessionText(initialText);
        sessions.put(code, session);
        return code;
    }

    public String createSession() {
        return createSession("");
    }

    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    public String addUser(String sessionId, String username) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        return session.addUser(username);
    }

    public boolean removeUser(String sessionId, String username) {
        Session session = sessions.get(sessionId);
        if (session == null) return false;
        return session.removeUser(username);
    }

    public Set<String> getUsers(String sessionId) {
        Session session = sessions.get(sessionId);
        return session != null ? session.getUsers() : Set.of();
    }

    public String getSessionText(String sessionId) {
        Session session = sessions.get(sessionId);
        return session != null ? session.getSessionText() : "";
    }

    public void updateSessionText(String sessionId, String newText) {
        Session session = sessions.get(sessionId);
        if (session != null) {
            session.setSessionText(newText);
        }
    }
}