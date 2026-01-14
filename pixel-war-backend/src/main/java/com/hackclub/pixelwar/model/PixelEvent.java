package com.hackclub.pixelwar.model;

import java.time.Instant;

/**
 * Represents a single pixel paint event in the game.
 */
public class PixelEvent {
    
    private int x;
    private int y;
    private String color;
    private String source; // "HUMAN" or "AI_AGENT"
    private String message; // Optional taunt/message from AI
    private long timestamp;
    
    public PixelEvent() {
        this.timestamp = Instant.now().toEpochMilli();
    }
    
    public PixelEvent(int x, int y, String color, String source) {
        this();
        this.x = x;
        this.y = y;
        this.color = color;
        this.source = source;
    }
    
    // Getters and Setters
    
    public int getX() {
        return x;
    }
    
    public void setX(int x) {
        this.x = x;
    }
    
    public int getY() {
        return y;
    }
    
    public void setY(int y) {
        this.y = y;
    }
    
    public String getColor() {
        return color;
    }
    
    public void setColor(String color) {
        this.color = color;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String toString() {
        return String.format("PixelEvent{x=%d, y=%d, color='%s', source='%s', message='%s'}", 
                x, y, color, source, message);
    }
}
