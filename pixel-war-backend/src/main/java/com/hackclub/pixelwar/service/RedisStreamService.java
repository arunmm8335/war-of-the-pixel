package com.hackclub.pixelwar.service;

import com.google.gson.Gson;
import com.hackclub.pixelwar.model.BoardState;
import com.hackclub.pixelwar.model.PixelEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core service that bridges Redis Streams with WebSocket updates.
 * This is the "Game Engine" - the source of truth.
 * 
 * Uses Redis Streams instead of Kafka for event streaming.
 */
@Service
public class RedisStreamService {
    
    private static final Logger log = LoggerFactory.getLogger(RedisStreamService.class);
    private static final int MAX_RECENT_EVENTS = 100;
    
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate webSocket;
    private final BoardState boardState;
    private final Gson gson;
    
    @Value("${redis.stream.name:pixel-events}")
    private String streamName;
    
    @Value("${redis.stream.consumer-group:spring-backend-group}")
    private String consumerGroup;
    
    private final String consumerId = "consumer-" + System.currentTimeMillis();
    
    // Buffer of recent events for new clients
    private final ConcurrentLinkedDeque<PixelEvent> recentEvents;
    
    // Background consumer thread
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    public RedisStreamService(
            StringRedisTemplate redisTemplate,
            SimpMessagingTemplate webSocket,
            BoardState boardState) {
        this.redisTemplate = redisTemplate;
        this.webSocket = webSocket;
        this.boardState = boardState;
        this.gson = new Gson();
        this.recentEvents = new ConcurrentLinkedDeque<>();
    }
    
    @PostConstruct
    public void init() {
        // Create stream and consumer group if they don't exist
        try {
            redisTemplate.opsForStream().createGroup(streamName, consumerGroup);
            log.info("✅ Created consumer group: {}", consumerGroup);
        } catch (Exception e) {
            // Group might already exist, which is fine
            log.debug("Consumer group {} may already exist: {}", consumerGroup, e.getMessage());
        }
        
        // Load existing events from stream to rebuild state
        loadExistingEvents();
        
        // Start background consumer
        startConsumer();
        
        log.info("🚀 Redis Stream Service initialized");
        log.info("📋 Stream: {}, Consumer Group: {}, Consumer: {}", streamName, consumerGroup, consumerId);
    }
    
    @PreDestroy
    public void shutdown() {
        running.set(false);
        executor.shutdown();
        log.info("👋 Redis Stream Service shut down");
    }
    
    /**
     * Load existing events from Redis Stream to rebuild board state on startup.
     */
    private void loadExistingEvents() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .range(streamName, Range.unbounded());
            
            if (records != null) {
                for (MapRecord<String, Object, Object> record : records) {
                    processRecord(record, false); // Don't broadcast during initial load
                }
                log.info("📥 Loaded {} existing events from stream", records.size());
            }
        } catch (Exception e) {
            log.warn("Could not load existing events: {}", e.getMessage());
        }
    }
    
    /**
     * Start the background consumer that listens for new events.
     */
    private void startConsumer() {
        running.set(true);
        executor.submit(() -> {
            log.info("🎧 Starting Redis Stream consumer...");
            
            while (running.get()) {
                try {
                    // Read new messages from the stream using consumer group
                    List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                            .read(
                                    Consumer.from(consumerGroup, consumerId),
                                    StreamReadOptions.empty()
                                            .count(10)
                                            .block(Duration.ofSeconds(2)),
                                    StreamOffset.create(streamName, ReadOffset.lastConsumed())
                            );
                    
                    if (records != null && !records.isEmpty()) {
                        for (MapRecord<String, Object, Object> record : records) {
                            processRecord(record, true); // Broadcast new events
                            
                            // Acknowledge the message
                            redisTemplate.opsForStream().acknowledge(streamName, consumerGroup, record.getId());
                        }
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        log.error("Error reading from stream: {}", e.getMessage());
                        try {
                            Thread.sleep(1000); // Wait before retrying
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            
            log.info("🛑 Redis Stream consumer stopped");
        });
    }
    
    /**
     * Process a record from the Redis Stream.
     */
    private void processRecord(MapRecord<String, Object, Object> record, boolean broadcast) {
        try {
            Map<Object, Object> map = record.getValue();
            
            PixelEvent event = new PixelEvent();
            event.setX(Integer.parseInt(String.valueOf(map.get("x"))));
            event.setY(Integer.parseInt(String.valueOf(map.get("y"))));
            event.setColor(String.valueOf(map.get("color")));
            event.setSource(String.valueOf(map.getOrDefault("source", "UNKNOWN")));
            
            Object messageObj = map.get("message");
            if (messageObj != null && !"null".equals(String.valueOf(messageObj))) {
                event.setMessage(String.valueOf(messageObj));
            }
            
            Object timestampObj = map.get("timestamp");
            if (timestampObj != null) {
                event.setTimestamp(Long.parseLong(String.valueOf(timestampObj)));
            }
            
            // Update local board state
            boardState.setPixel(event.getX(), event.getY(), event.getColor());
            
            // Add to recent events buffer
            recentEvents.addLast(event);
            while (recentEvents.size() > MAX_RECENT_EVENTS) {
                recentEvents.removeFirst();
            }
            
            if (broadcast) {
                log.info("🎨 New Move: {} painted {} at ({}, {})", 
                        event.getSource(), event.getColor(), event.getX(), event.getY());
                
                // Broadcast to all connected WebSocket clients
                String json = gson.toJson(event);
                webSocket.convertAndSend("/topic/board", json);
                
                // If there's a taunt message, broadcast it too
                if (event.getMessage() != null && !event.getMessage().isEmpty()) {
                    log.info("💬 Taunt from {}: {}", event.getSource(), event.getMessage());
                    webSocket.convertAndSend("/topic/chat", gson.toJson(new ChatMessage(
                            event.getSource(), 
                            event.getMessage()
                    )));
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to process record: {}", e.getMessage());
        }
    }
    
    /**
     * Paint a pixel - sends event to Redis Stream.
     * This is the ONLY way pixels should be painted.
     */
    public void paintPixel(PixelEvent event) {
        // Validate coordinates
        if (!boardState.isValidCoordinate(event.getX(), event.getY())) {
            log.warn("Invalid coordinates: {}, {}", event.getX(), event.getY());
            return;
        }
        
        // Validate color format
        if (event.getColor() == null || !event.getColor().matches("^#[0-9A-Fa-f]{6}$")) {
            log.warn("Invalid color format: {}", event.getColor());
            return;
        }
        
        // Ensure timestamp
        if (event.getTimestamp() == 0) {
            event.setTimestamp(System.currentTimeMillis());
        }
        
        // Create the message map for Redis Stream
        Map<String, String> message = new HashMap<>();
        message.put("x", String.valueOf(event.getX()));
        message.put("y", String.valueOf(event.getY()));
        message.put("color", event.getColor());
        message.put("source", event.getSource());
        message.put("timestamp", String.valueOf(event.getTimestamp()));
        
        if (event.getMessage() != null) {
            message.put("message", event.getMessage());
        }
        
        try {
            // Add to Redis Stream - the source of truth
            RecordId recordId = redisTemplate.opsForStream().add(streamName, message);
            log.debug("✅ Sent to Redis Stream: {} -> {}", recordId, message);
        } catch (Exception e) {
            log.error("❌ Failed to send to Redis Stream: {}", e.getMessage());
        }
    }
    
    /**
     * Get the current board state.
     */
    public BoardState getBoardState() {
        return boardState;
    }
    
    /**
     * Get recent events for new clients.
     */
    public List<PixelEvent> getRecentEvents() {
        return new ArrayList<>(recentEvents);
    }
    
    /**
     * Simple chat message record.
     */
    private record ChatMessage(String source, String message) {}
}
