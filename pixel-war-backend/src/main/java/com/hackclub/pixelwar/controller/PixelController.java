package com.hackclub.pixelwar.controller;

import com.hackclub.pixelwar.model.BoardState;
import com.hackclub.pixelwar.model.PixelEvent;
import com.hackclub.pixelwar.service.RedisStreamService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for the Pixel War game.
 * Provides endpoints for human players and board state queries.
 */
@RestController
@RequestMapping("/api")
public class PixelController {
    
    private final RedisStreamService pixelService;
    
    public PixelController(RedisStreamService pixelService) {
        this.pixelService = pixelService;
    }
    
    /**
     * Paint a pixel - for human players clicking the canvas.
     * POST /api/paint
     */
    @PostMapping("/paint")
    public ResponseEntity<Map<String, String>> paint(@RequestBody PixelEvent event) {
        event.setSource("HUMAN");
        pixelService.paintPixel(event);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", String.format("Painted %s at (%d, %d)", 
                event.getColor(), event.getX(), event.getY()));
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get the full board state - for new clients joining.
     * GET /api/board
     */
    @GetMapping("/board")
    public ResponseEntity<Map<String, Object>> getBoard() {
        BoardState state = pixelService.getBoardState();
        
        Map<String, Object> response = new HashMap<>();
        response.put("width", state.getWidth());
        response.put("height", state.getHeight());
        response.put("pixels", state.getAllPixels());
        response.put("pixelCount", state.getPixelCount());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get recent events - useful for AI agents and new clients.
     * GET /api/events/recent
     */
    @GetMapping("/events/recent")
    public ResponseEntity<List<PixelEvent>> getRecentEvents() {
        return ResponseEntity.ok(pixelService.getRecentEvents());
    }
    
    /**
     * Health check endpoint.
     * GET /api/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "pixel-war-backend");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get game stats.
     * GET /api/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        BoardState state = pixelService.getBoardState();
        List<PixelEvent> recent = pixelService.getRecentEvents();
        
        long humanMoves = recent.stream().filter(e -> "HUMAN".equals(e.getSource())).count();
        long aiMoves = recent.stream().filter(e -> "AI_AGENT".equals(e.getSource())).count();
        
        Map<String, Object> response = new HashMap<>();
        response.put("totalPixels", state.getPixelCount());
        response.put("boardSize", state.getWidth() + "x" + state.getHeight());
        response.put("recentHumanMoves", humanMoves);
        response.put("recentAiMoves", aiMoves);
        
        return ResponseEntity.ok(response);
    }
}
