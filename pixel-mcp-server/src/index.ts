import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import Redis from "ioredis";

// ============================================
// Configuration - Upstash Redis
// ============================================
const REDIS_URL = process.env.REDIS_URL || "rediss://default:AZiZAAIncDI2NjQzMGQzNzI4NWQ0MDE3ODg2MGExYjNiNjY0MDQyOXAyMzkwNjU@darling-flea-39065.upstash.io:6379";

const STREAM_NAME = "pixel-events";
const CONSUMER_GROUP = "mcp-agent-group";
const CONSUMER_ID = `mcp-consumer-${Date.now()}`;
const BOARD_SIZE = { width: 100, height: 100 };

// ============================================
// Types
// ============================================
interface PixelEvent {
  x: number;
  y: number;
  color: string;
  source: string;
  message?: string;
  timestamp: number;
}

// ============================================
// Global State
// ============================================
let redis: Redis;
const recentEvents: PixelEvent[] = [];
const MAX_EVENTS = 50;
const boardState: Map<string, string> = new Map();

// ============================================
// Redis Setup
// ============================================
async function setupRedis(): Promise<void> {
  redis = new Redis(REDIS_URL, {
    tls: {
      rejectUnauthorized: false
    },
    maxRetriesPerRequest: 3,
    retryDelayOnFailover: 100
  });

  redis.on('connect', () => {
    console.error('✅ Connected to Upstash Redis');
  });

  redis.on('error', (err) => {
    console.error('❌ Redis connection error:', err.message);
  });

  // Create consumer group if it doesn't exist
  try {
    await redis.xgroup('CREATE', STREAM_NAME, CONSUMER_GROUP, '0', 'MKSTREAM');
    console.error(`✅ Created consumer group: ${CONSUMER_GROUP}`);
  } catch (err: any) {
    if (!err.message.includes('BUSYGROUP')) {
      console.error('Consumer group error:', err.message);
    }
    // BUSYGROUP means group already exists, which is fine
  }

  // Load existing events to rebuild board state
  await loadExistingEvents();

  // Start background consumer
  startConsumer();
}

async function loadExistingEvents(): Promise<void> {
  try {
    const records = await redis.xrange(STREAM_NAME, '-', '+', 'COUNT', '1000');
    
    for (const [id, fields] of records) {
      const event = parseStreamRecord(fields);
      if (event) {
        boardState.set(`${event.x},${event.y}`, event.color);
        recentEvents.push(event);
        if (recentEvents.length > MAX_EVENTS) {
          recentEvents.shift();
        }
      }
    }
    
    console.error(`📥 Loaded ${records.length} existing events from stream`);
  } catch (err) {
    console.error('Failed to load existing events:', err);
  }
}

function parseStreamRecord(fields: string[]): PixelEvent | null {
  try {
    const map: Record<string, string> = {};
    for (let i = 0; i < fields.length; i += 2) {
      map[fields[i]] = fields[i + 1];
    }
    
    return {
      x: parseInt(map.x),
      y: parseInt(map.y),
      color: map.color,
      source: map.source || 'UNKNOWN',
      message: map.message,
      timestamp: parseInt(map.timestamp) || Date.now()
    };
  } catch {
    return null;
  }
}

function startConsumer(): void {
  const consume = async () => {
    while (true) {
      try {
        // Read new messages from the stream
        const results = await redis.xreadgroup(
          'GROUP', CONSUMER_GROUP, CONSUMER_ID,
          'BLOCK', '2000',
          'COUNT', '10',
          'STREAMS', STREAM_NAME, '>'
        );

        if (results) {
          for (const [stream, messages] of results) {
            for (const [id, fields] of messages) {
              const event = parseStreamRecord(fields);
              if (event) {
                // Update board state
                boardState.set(`${event.x},${event.y}`, event.color);
                
                // Add to recent events
                recentEvents.push(event);
                if (recentEvents.length > MAX_EVENTS) {
                  recentEvents.shift();
                }
                
                console.error(`📥 Received: ${event.source} painted ${event.color} at (${event.x},${event.y})`);
                
                // Acknowledge the message
                await redis.xack(STREAM_NAME, CONSUMER_GROUP, id);
              }
            }
          }
        }
      } catch (err: any) {
        console.error('Consumer error:', err.message);
        await new Promise(resolve => setTimeout(resolve, 1000));
      }
    }
  };

  // Run consumer in background
  consume().catch(console.error);
  console.error('✅ Redis Stream consumer started');
}

// ============================================
// MCP Server Setup
// ============================================
const server = new McpServer({
  name: "PixelWarAgent",
  version: "1.0.0"
});

async function setupMcpServer(): Promise<void> {
  
  // ========================================
  // TOOL: Paint a Pixel
  // ========================================
  server.tool(
    "paint_pixel",
    "Paint a single pixel on the canvas. Use this to claim territory or create art!",
    {
      x: z.number().min(0).max(BOARD_SIZE.width - 1).describe("X coordinate (0-99)"),
      y: z.number().min(0).max(BOARD_SIZE.height - 1).describe("Y coordinate (0-99)"),
      color: z.string().regex(/^#[0-9A-Fa-f]{6}$/).describe("Hex color code (e.g., #FF0000 for red)"),
      message: z.string().optional().describe("Optional taunt or message to display")
    },
    async ({ x, y, color, message }) => {
      const timestamp = Date.now();
      
      // Add to Redis Stream using XADD
      await redis.xadd(
        STREAM_NAME, '*',
        'x', String(x),
        'y', String(y),
        'color', color,
        'source', 'AI_AGENT',
        'timestamp', String(timestamp),
        ...(message ? ['message', message] : [])
      );

      const response = message 
        ? `🎨 Painted ${color} at (${x}, ${y}). Taunt: "${message}"`
        : `🎨 Painted ${color} at (${x}, ${y})`;

      return {
        content: [{ type: "text", text: response }]
      };
    }
  );

  // ========================================
  // TOOL: Paint Multiple Pixels
  // ========================================
  server.tool(
    "paint_batch",
    "Paint multiple pixels at once for creating patterns or shapes efficiently",
    {
      pixels: z.array(z.object({
        x: z.number().min(0).max(99),
        y: z.number().min(0).max(99),
        color: z.string().regex(/^#[0-9A-Fa-f]{6}$/)
      })).max(20).describe("Array of pixels to paint (max 20 per batch)")
    },
    async ({ pixels }) => {
      const timestamp = Date.now();
      
      // Use pipeline for batch operations
      const pipeline = redis.pipeline();
      
      for (const p of pixels) {
        pipeline.xadd(
          STREAM_NAME, '*',
          'x', String(p.x),
          'y', String(p.y),
          'color', p.color,
          'source', 'AI_AGENT',
          'timestamp', String(timestamp)
        );
      }
      
      await pipeline.exec();

      return {
        content: [{ 
          type: "text", 
          text: `🎨 Batch painted ${pixels.length} pixels successfully!` 
        }]
      };
    }
  );

  // ========================================
  // TOOL: Get Board Status
  // ========================================
  server.tool(
    "get_board_status",
    "Get current statistics about the game board",
    {},
    async () => {
      const humanMoves = recentEvents.filter(e => e.source === 'HUMAN').length;
      const aiMoves = recentEvents.filter(e => e.source === 'AI_AGENT').length;

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            boardSize: `${BOARD_SIZE.width}x${BOARD_SIZE.height}`,
            totalPixelsPainted: boardState.size,
            recentEventsBuffered: recentEvents.length,
            recentHumanMoves: humanMoves,
            recentAiMoves: aiMoves,
            lastEvent: recentEvents[recentEvents.length - 1] || null
          }, null, 2)
        }]
      };
    }
  );

  // ========================================
  // TOOL: Get Pixel Color
  // ========================================
  server.tool(
    "get_pixel",
    "Check the current color of a specific pixel",
    {
      x: z.number().min(0).max(99).describe("X coordinate"),
      y: z.number().min(0).max(99).describe("Y coordinate")
    },
    async ({ x, y }) => {
      const color = boardState.get(`${x},${y}`) || '#FFFFFF';
      return {
        content: [{
          type: "text",
          text: `Pixel at (${x}, ${y}) is ${color}`
        }]
      };
    }
  );

  // ========================================
  // RESOURCE: Recent Events
  // ========================================
  server.resource(
    "recent-moves",
    "redis://pixel-events/recent",
    async (uri) => {
      return {
        contents: [{
          uri: uri.href,
          mimeType: "application/json",
          text: JSON.stringify({
            description: "Recent pixel painting events",
            count: recentEvents.length,
            events: recentEvents.slice(-20) // Last 20 events
          }, null, 2)
        }]
      };
    }
  );

  // ========================================
  // RESOURCE: Board State
  // ========================================
  server.resource(
    "board-state",
    "game://pixel-war/board",
    async (uri) => {
      // Convert Map to object for JSON serialization
      const pixels: Record<string, string> = {};
      boardState.forEach((color, coord) => {
        pixels[coord] = color;
      });

      return {
        contents: [{
          uri: uri.href,
          mimeType: "application/json",
          text: JSON.stringify({
            width: BOARD_SIZE.width,
            height: BOARD_SIZE.height,
            pixelCount: boardState.size,
            pixels
          }, null, 2)
        }]
      };
    }
  );

  // ========================================
  // PROMPT: Strategy Suggestions
  // ========================================
  server.prompt(
    "strategy",
    "Get suggestions for pixel painting strategies",
    {},
    async () => {
      return {
        messages: [{
          role: "user",
          content: {
            type: "text",
            text: `You are a competitive pixel artist in War of the Pixels. 
            
Current board state:
- Total pixels painted: ${boardState.size}
- Recent events: ${recentEvents.length}

Suggest a creative strategy to dominate the board. Consider:
1. Creating recognizable patterns or art
2. Claiming unclaimed territory
3. Responding to human player moves
4. Using colors strategically`
          }
        }]
      };
    }
  );
}

// ============================================
// Main Entry Point
// ============================================
async function main(): Promise<void> {
  console.error('🚀 Starting Pixel War MCP Server (Redis Edition)...');
  
  try {
    await setupRedis();
    await setupMcpServer();
    
    // Connect to transport (stdio for Claude Desktop)
    const transport = new StdioServerTransport();
    await server.connect(transport);
    
    console.error('✅ MCP Server running on stdio');
    console.error('📋 Available tools: paint_pixel, paint_batch, get_board_status, get_pixel');
    console.error('📋 Available resources: recent-moves, board-state');
    
  } catch (error) {
    console.error('❌ Failed to start server:', error);
    process.exit(1);
  }
}

// Handle graceful shutdown
process.on('SIGINT', async () => {
  console.error('\n👋 Shutting down...');
  await redis?.quit();
  process.exit(0);
});

main();
