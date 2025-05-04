package com.example.Services;
/*

import jakarta.annotation.PostConstruct;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CharacterStreamer {
    private final SimpMessagingTemplate tpl;
    private final AtomicInteger index = new AtomicInteger(0);
    private final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public CharacterStreamer(SimpMessagingTemplate tpl) {
        this.tpl = tpl;
    }

    @Scheduled(fixedRate = 100)
    public void streamChar() {
        char c = chars.charAt(index.getAndIncrement() % chars.length());
        tpl.convertAndSend("/topic/char-stream", String.valueOf(c));
        System.out.println("[SERVER] Sent char: " + c);
    }
}
*/