"""
Autonomous AI Agent for War of the Pixels
==========================================

This script demonstrates how to create an autonomous agent that plays
the pixel war game 24/7 without using Claude Desktop.

It uses the MCP client SDK to communicate with the MCP server.

Prerequisites:
    pip install mcp httpx asyncio

Usage:
    python autonomous_agent.py

The agent will:
1. Connect to the MCP server
2. Periodically check the board state
3. Execute painting strategies
4. React to human moves
"""

import asyncio
import json
import random
import subprocess
import os
from typing import Optional

# You can also use the MCP Python SDK directly
# from mcp import ClientSession, StdioServerParameters
# from mcp.client.stdio import stdio_client

# For simplicity, this agent uses HTTP to talk to Spring Boot directly
import httpx

# Configuration
BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080")
BOARD_SIZE = 100


class PixelWarAgent:
    """An autonomous agent that plays War of the Pixels."""
    
    def __init__(self, name: str = "ChaosBot"):
        self.name = name
        self.client = httpx.AsyncClient(base_url=BACKEND_URL)
        self.my_pixels = set()  # Track pixels we've painted
        self.colors = [
            "#FF0000", "#FF6B6B", "#00FF00", "#48DBFB",
            "#9B59B6", "#E91E63", "#FF9500", "#0066FF"
        ]
    
    async def paint_pixel(self, x: int, y: int, color: str, message: Optional[str] = None):
        """Paint a single pixel."""
        payload = {
            "x": x,
            "y": y,
            "color": color,
            "source": f"AI_AGENT:{self.name}"
        }
        if message:
            payload["message"] = message
        
        try:
            response = await self.client.post("/api/paint", json=payload)
            if response.status_code == 200:
                self.my_pixels.add((x, y))
                print(f"🎨 Painted {color} at ({x}, {y})")
                return True
        except Exception as e:
            print(f"❌ Failed to paint: {e}")
        return False
    
    async def get_board_state(self):
        """Get the current board state."""
        try:
            response = await self.client.get("/api/board")
            return response.json()
        except Exception as e:
            print(f"❌ Failed to get board: {e}")
            return None
    
    async def get_recent_events(self):
        """Get recent events to react to."""
        try:
            response = await self.client.get("/api/events/recent")
            return response.json()
        except Exception as e:
            print(f"❌ Failed to get events: {e}")
            return []
    
    # ==========================================
    # Strategies
    # ==========================================
    
    async def strategy_random_chaos(self):
        """Paint random pixels with random colors."""
        x = random.randint(0, BOARD_SIZE - 1)
        y = random.randint(0, BOARD_SIZE - 1)
        color = random.choice(self.colors)
        
        taunts = [
            "Chaos reigns!",
            "You cannot stop me!",
            "This pixel is mine now.",
            "The void consumes all.",
            None  # Sometimes no taunt
        ]
        
        await self.paint_pixel(x, y, color, random.choice(taunts))
    
    async def strategy_draw_line(self, start_x: int, start_y: int, length: int, direction: str, color: str):
        """Draw a line of pixels."""
        for i in range(length):
            if direction == "horizontal":
                await self.paint_pixel(start_x + i, start_y, color)
            else:
                await self.paint_pixel(start_x, start_y + i, color)
            await asyncio.sleep(0.1)  # Small delay between pixels
    
    async def strategy_defend_territory(self):
        """Check if humans painted over our pixels and reclaim them."""
        events = await self.get_recent_events()
        
        for event in events:
            if event.get("source") == "HUMAN":
                coord = (event["x"], event["y"])
                if coord in self.my_pixels:
                    print(f"⚔️ Human invaded ({coord[0]}, {coord[1]})! Reclaiming...")
                    await self.paint_pixel(
                        coord[0], coord[1],
                        random.choice(self.colors),
                        "This is MY territory!"
                    )
    
    async def strategy_draw_pattern(self, pattern: str, start_x: int, start_y: int, color: str):
        """Draw a predefined pattern."""
        patterns = {
            "square": [
                (0, 0), (1, 0), (2, 0),
                (0, 1),        (2, 1),
                (0, 2), (1, 2), (2, 2)
            ],
            "cross": [
                        (1, 0),
                (0, 1), (1, 1), (2, 1),
                        (1, 2)
            ],
            "heart": [
                    (1, 0), (2, 0),     (4, 0), (5, 0),
                (0, 1), (1, 1), (2, 1), (3, 1), (4, 1), (5, 1), (6, 1),
                (0, 2), (1, 2), (2, 2), (3, 2), (4, 2), (5, 2), (6, 2),
                    (1, 3), (2, 3), (3, 3), (4, 3), (5, 3),
                        (2, 4), (3, 4), (4, 4),
                            (3, 5)
            ]
        }
        
        if pattern in patterns:
            for dx, dy in patterns[pattern]:
                x, y = start_x + dx, start_y + dy
                if 0 <= x < BOARD_SIZE and 0 <= y < BOARD_SIZE:
                    await self.paint_pixel(x, y, color)
                    await asyncio.sleep(0.05)
    
    # ==========================================
    # Main Loop
    # ==========================================
    
    async def run(self, interval: float = 2.0):
        """Main agent loop."""
        print(f"🤖 {self.name} is starting...")
        print(f"📡 Connecting to {BACKEND_URL}")
        
        # Test connection
        board = await self.get_board_state()
        if board:
            print(f"✅ Connected! Board size: {board['width']}x{board['height']}")
        else:
            print("❌ Could not connect to server. Is it running?")
            return
        
        iteration = 0
        
        while True:
            try:
                iteration += 1
                print(f"\n--- Iteration {iteration} ---")
                
                # Mix of strategies
                action = random.choice([
                    "chaos",
                    "chaos",
                    "defend",
                    "pattern",
                    "line"
                ])
                
                if action == "chaos":
                    await self.strategy_random_chaos()
                    
                elif action == "defend":
                    await self.strategy_defend_territory()
                    
                elif action == "pattern":
                    pattern = random.choice(["square", "cross", "heart"])
                    x = random.randint(5, BOARD_SIZE - 15)
                    y = random.randint(5, BOARD_SIZE - 15)
                    color = random.choice(self.colors)
                    print(f"🎨 Drawing {pattern} at ({x}, {y})")
                    await self.strategy_draw_pattern(pattern, x, y, color)
                    
                elif action == "line":
                    x = random.randint(0, BOARD_SIZE - 20)
                    y = random.randint(0, BOARD_SIZE - 1)
                    direction = random.choice(["horizontal", "vertical"])
                    length = random.randint(5, 15)
                    color = random.choice(self.colors)
                    print(f"🎨 Drawing {direction} line at ({x}, {y})")
                    await self.strategy_draw_line(x, y, length, direction, color)
                
                await asyncio.sleep(interval)
                
            except KeyboardInterrupt:
                print("\n👋 Agent shutting down...")
                break
            except Exception as e:
                print(f"❌ Error: {e}")
                await asyncio.sleep(5)
    
    async def close(self):
        """Cleanup."""
        await self.client.aclose()


async def main():
    """Entry point."""
    agent = PixelWarAgent(name="ChaosBot")
    try:
        await agent.run(interval=1.5)  # Act every 1.5 seconds
    finally:
        await agent.close()


if __name__ == "__main__":
    print("""
    ╔═══════════════════════════════════════╗
    ║     War of the Pixels - AI Agent      ║
    ║         🤖 Autonomous Mode 🤖          ║
    ╚═══════════════════════════════════════╝
    """)
    asyncio.run(main())
