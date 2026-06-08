# GoMoKu - 5-in-a-Row Game

A real-time multiplayer Gomoku game with a combined HTTP + WebSocket server.

## 🚀 Deploy on Coolify (Single App ✓)

The server now serves **both the HTML frontend AND WebSocket game server** in one process!

1. In Coolify, create a **New Application**
2. Connect your GitHub repo: `boudouvich21-wq/Gomuko`
3. **Branch:** `main`
4. **Build Pack:** **Dockerfile**
5. **Port:** `8000`
6. **Is it a static site?** ❌ **No**
7. Click **Continue** and deploy

### How it works

- **HTTP (Web UI):** Runs on port `8000` (the PORT you set in Coolify)
- **WebSocket:** Runs on port `8001` (PORT + 1)
- The HTML client automatically connects to the WebSocket on port `8001`

> Coolify will route HTTP traffic on port 80/443 to your app on port 8000.
> The WebSocket connects on port 8001 — make sure your VPS firewall allows this port.

## Local Development

```bash
# Install dependencies
pip install websockets

# Start the unified server
python server.py

# Open in browser
open http://localhost:8000
```

Then open http://localhost:8000 in two browser tabs to play.
