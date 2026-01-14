# War of the Pixels 🎨⚔️
<img width="1238" height="676" alt="Screenshot 2026-01-14 223206" src="https://github.com/user-attachments/assets/82877bcc-c27d-43f5-a3c7-1a4341056a83" />

A real-time multiplayer pixel canvas game demonstrating **Event-Driven Architecture** with:
- **Spring Boot** - Enterprise Java backend
- **Upstash Redis Streams** - Event streaming & source of truth (free tier!)
- **MCP (Model Context Protocol)** - AI agent interoperability
- **WebSockets** - Real-time browser updates

## Architecture Overview

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Human User    │     │    AI Agent     │     │  Claude Desktop │
│   (Browser)     │     │  (Python/etc)   │     │                 │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         │ HTTP/WebSocket        │ MCP Protocol          │ MCP Protocol
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐     ┌─────────────────────────────────────────┐
│  Spring Boot    │     │            MCP Server                   │
│  Backend        │     │         (TypeScript)                    │
└────────┬────────┘     └────────────────┬────────────────────────┘
         │                               │
         │         Redis Streams         │
         │                               │
         ▼                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Upstash Redis                                 │
│                Stream: pixel-events                              │
│            (The Single Source of Truth)                          │
└─────────────────────────────────────────────────────────────────┘
```

## Prerequisites

1. **Java 17+** - For Spring Boot
2. **Node.js 20+** - For MCP Server
3. **Upstash Redis** (Free tier) - https://upstash.com/

## Quick Start

### 1. Set Up Upstash Redis (Free!)

1. Go to [Upstash Console](https://console.upstash.com/)
2. Create a new Redis database
3. Copy your credentials:
   - Host: `your-instance.upstash.io`
   - Port: `6379`
   - Password: (from the dashboard)

### 2. Configure & Run Spring Boot Backend

```bash
cd pixel-war-backend

# Edit src/main/resources/application.properties
# Replace PASTE_YOUR_PASSWORD_HERE with your Upstash password

# Build and run
./mvnw spring-boot:run
```

The backend will start at `http://localhost:8080`

### 3. Configure & Run MCP Server

```bash
cd pixel-mcp-server

# Install dependencies
npm install

# Edit src/index.ts - update REDIS_URL with your password
# Or set environment variable:
export REDIS_URL="rediss://default:YOUR_PASSWORD@your-instance.upstash.io:6379"

# Build
npm run build

# Test run
npm start
```

### 4. Open the Game

Navigate to `http://localhost:8080` in your browser to see the pixel canvas!

## Environment Variables

### Spring Boot (application.properties)
```properties
spring.data.redis.host=darling-flea-39065.upstash.io
spring.data.redis.port=6379
spring.data.redis.username=default
spring.data.redis.password=YOUR_PASSWORD_HERE
spring.data.redis.ssl.enabled=true
```

### MCP Server
```
REDIS_URL=rediss://default:YOUR_PASSWORD@darling-flea-39065.upstash.io:6379
```

## Connecting AI Agents

### Option A: Claude Desktop

Add to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "pixel-war": {
      "command": "node",
      "args": ["C:/Projects/war-of-the-pixels/pixel-mcp-server/dist/index.js"],
      "env": {
        "REDIS_URL": "rediss://default:YOUR_PASSWORD@darling-flea-39065.upstash.io:6379"
      }
    }
  }
}
```

Then ask Claude: *"Paint a red heart pattern starting at coordinates 50,50"*

### Option B: Python Agent

See `agent/autonomous_agent.py` for a standalone Python agent that runs 24/7.

## MCP Tools Available

| Tool | Description |
|------|-------------|
| `paint_pixel` | Paint a single pixel at (x, y) with a hex color |
| `paint_batch` | Paint up to 20 pixels at once |
| `get_board_status` | Get current game statistics |
| `get_pixel` | Check the color of a specific pixel |

## MCP Resources Available

| Resource | URI | Description |
|----------|-----|-------------|
| Recent Moves | `redis://pixel-events/recent` | Last 20 pixel events |
| Board State | `game://pixel-war/board` | Current board pixels |

## API Endpoints (Spring Boot)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/paint` | POST | Paint a pixel (for humans) |
| `/api/board` | GET | Get full board state |
| `/api/events/recent` | GET | Get recent events |
| `/api/stats` | GET | Get game statistics |
| `/api/health` | GET | Health check |

## Project Structure

```
war-of-the-pixels/
├── pixel-war-backend/          # Spring Boot backend
│   ├── src/main/java/
│   │   └── com/hackclub/pixelwar/
│   │       ├── config/         # WebSocket, CORS, Game config
│   │       ├── controller/     # REST endpoints
│   │       ├── model/          # PixelEvent, BoardState
│   │       └── service/        # Kafka + WebSocket bridge
│   └── src/main/resources/
│       ├── static/             # Frontend HTML
│       └── application.properties
│
├── pixel-mcp-server/           # MCP Server (TypeScript)
│   ├── src/
│   │   └── index.ts           # MCP tools & resources
│   ├── package.json
│   └── tsconfig.json
│
└── agent/                      # Autonomous Python agent
    └── autonomous_agent.py
```

## Skills Demonstrated

This project showcases:

- ✅ **Event-Driven Architecture** - Redis Streams as the single source of truth
- ✅ **Enterprise Java** - Spring Boot with Redis integration
- ✅ **Real-time Systems** - WebSocket for live updates
- ✅ **AI Interoperability** - MCP for AI agent communication
- ✅ **Full-Stack Development** - Backend + Frontend + AI integration
- ✅ **Cloud Native** - Environment-based configuration
- ✅ **Free Tier Friendly** - Uses Upstash Redis free tier

## License

MIT
