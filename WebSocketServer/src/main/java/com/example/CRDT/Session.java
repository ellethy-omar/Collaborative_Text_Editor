package com.example.CRDT;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Represents a collaborative editing session with auto-assigned user slots and text storage.
 */
public class Session {
    private final String sessionId;
    private final Set<String> users = new LinkedHashSet<>();
    private final Deque<String> availableNames = new ArrayDeque<>(
            Arrays.asList("user1", "user2", "user3", "user4")
    );
    private String sessionText = "";
    private final Queue<Map<String,Object>> storage = new ConcurrentLinkedQueue<>();


    public Session(String sessionId) {
        this.sessionId = sessionId;
    }

    public Queue<Map<String,Object>> getStorage() {
        return storage; // thread‑safe queue for concurrent addAll/iterations
    }

    public synchronized String addUser(String username) {
        if (users.size() >= 4) {
            throw new IllegalStateException("Session is full");
        }
        String assigned = username;
        if (assigned == null || assigned.isBlank()) {
            if (availableNames.isEmpty()) {
                throw new IllegalStateException("No available user slots");
            }
            assigned = availableNames.poll();
        }
        if (!users.add(assigned)) {
            throw new IllegalArgumentException("Username already in session: " + assigned);
        }
        return assigned;
    }

    public synchronized boolean removeUser(String username) {
        boolean removed = users.remove(username);
        if (removed) {
            availableNames.offer(username);
        }
        return removed;
    }

    public synchronized Set<String> getUsers() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(users));
    }

    public String getSessionId() {
        return sessionId;
    }

    public synchronized String getSessionText() {
        return sessionText;
    }

    public synchronized void setSessionText(String text) {
        this.sessionText = (text != null ? text : "");
    }
}
