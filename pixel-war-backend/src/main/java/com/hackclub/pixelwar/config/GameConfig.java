package com.hackclub.pixelwar.config;

import com.hackclub.pixelwar.model.BoardState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Game configuration - sets up the board state.
 */
@Configuration
public class GameConfig {
    
    @Value("${game.board.width:100}")
    private int boardWidth;
    
    @Value("${game.board.height:100}")
    private int boardHeight;
    
    @Bean
    public BoardState boardState() {
        return new BoardState(boardWidth, boardHeight);
    }
}
