package com.example.Controllers;

import com.example.CRDT.Session;
import com.example.Services.SessionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;

@Controller
public class WebSocketController {
    private static final Logger log = LoggerFactory.getLogger(WebSocketController.class);

    private final SessionService sessions;
    private final SimpMessagingTemplate tpl;

    private final ConcurrentMap<String, Queue<List<Map<String,Object>>>> tempBuffers = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public WebSocketController(SessionService sessions, SimpMessagingTemplate tpl) {
        this.sessions = sessions;
        this.tpl = tpl;
    }

    @PostConstruct
    public void startBatchBroadcast() {
        scheduler.scheduleAtFixedRate(this::flushTempBuffers, 100, 100, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stopBatchBroadcast() {
        scheduler.shutdown();
    }

    private void flushTempBuffers() {
        for (Map.Entry<String, Queue<List<Map<String,Object>>>> entry : tempBuffers.entrySet()) {
            String token = entry.getKey();
            Queue<List<Map<String,Object>>> queue = entry.getValue();

            // aggregate all waiting batches
            List<Map<String,Object>> aggregated = new ArrayList<>();
            List<Map<String,Object>> batch;
            while ((batch = queue.poll()) != null) {
                aggregated.addAll(batch);
            }

            if (!aggregated.isEmpty()) {
                long ts = Instant.now().toEpochMilli();
                tpl.convertAndSend(
                        "/topic/session/" + token + "/operation/batch",
                        Map.of("operations", aggregated, "timestamp", ts)
                );
                log.info("Broadcasted {} batched ops for session {} @ {}", aggregated.size(), token, Instant.ofEpochMilli(ts));
            }
        }
    }

    @MessageMapping("/join/{token}")
    public void join(@DestinationVariable String token, @Payload Map<String,String> p, StompHeaderAccessor sha) {
        if (!sessions.exists(token)) return;
        String editor = sessions.getEditorFor(token);

        String username = p.get("username");
        sha.getSessionAttributes().put("token",     token);
        sha.getSessionAttributes().put("username",  username);

        String sessionId = sha.getSessionId();
        tpl.convertAndSendToUser(sessionId, "/queue/storage", sessions.getByToken(token).getStorage());

        var users = sessions.getUsers(token);
        List<String> targets = sessions.getAllTokensForEditor(editor).stream().toList();
        targets.forEach(t -> tpl.convertAndSend("/topic/session/"+t+"/users", users));
    }

    @MessageMapping("/cursor/{token}")
    public void cursor(@DestinationVariable String token, @Payload Map<String,Object> p) {
        if (!sessions.isEditorToken(token)) return;

        String editor = sessions.getEditorFor(token);
        sessions.getAllTokensForEditor(editor)
                .forEach(t -> tpl.convertAndSend("/topic/session/"+t+"/cursor", p));
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
        if (ops == null || ops.isEmpty()) {
            // either drop it, or only ACK back to the sender
            return;
        }
        long ts = ((Number) payload.get("timestamp")).longValue();
        Instant instantTs = Instant.ofEpochMilli(ts);

        log.info("Received {} ops from {} @ {}: {}", ops.size(), user, instantTs, ops);

        Session session = sessions.getByToken(token);
        if (session != null) {
            synchronized (session.getStorage()) {
                session.getStorage().addAll(ops);
                log.info("Thread‑safe: Added {} ops to session {} by {} at {}",
                        ops.size(), session.getSessionId(), user, instantTs);
            }
        }

        tpl.convertAndSend(
                "/topic/session/" + token + "/operation/ack",
                Map.of("username", user, "operations", ops, "timestamp", ts, "status", "SERVER_RECEIVED")
        );

        log.info("Logged and ACKed operations for session {}", token);
    }
}