package com.hackclub.pixelwar.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the current state of the game board.
 * Thread-safe for concurrent access.
 */
public class BoardState {
    
    private final int width;
    private final int height;
    private final Map<String, String> pixels; // "x,y" -> color
    
    public BoardState(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new ConcurrentHashMap<>();
    }
    
    public void setPixel(int x, int y, String color) {
        if (isValidCoordinate(x, y)) {
            pixels.put(coordKey(x, y), color);
        }
    }
    
    public String getPixel(int x, int y) {
        return pixels.getOrDefault(coordKey(x, y), "#FFFFFF");
    }
    
    public Map<String, String> getAllPixels() {
        return new ConcurrentHashMap<>(pixels);
    }
    
    public boolean isValidCoordinate(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }
    
    private String coordKey(int x, int y) {
        return x + "," + y;
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public int getPixelCount() {
        return pixels.size();
    }
}
