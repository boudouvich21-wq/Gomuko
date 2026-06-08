# GoMoKu - 5-in-a-Row Game

A real-time multiplayer Gomoku game with a WebSocket server.

## Deploy on Coolify

This project has **two components** that need to run:

### Option A: Dockerfile (Recommended - Single App)

1. In Coolify, create a **New Application**
2. Connect your GitHub repo: `boudouvich21-wq/Gomuko`
3. Set **Build Pack** to **Dockerfile**
4. Set **Port** to `8765` (this is the WebSocket port)
5. Click **Continue** and deploy

> **⚠️ Problem:** The Dockerfile only runs the WebSocket server. The HTML client needs to be served via HTTP too.

### Option B: Two Services (Best Approach)

#### Service 1: Static Site (for the HTML client)

1. Create a **New Application**
2. Set **Build Pack** → **Static**
3. Set **Port** to `80` or `8000`
4. **Base Directory** → leave empty
5. **Is it a static site?** → **Yes** (important!)
6. Deploy — this will serve `index.html` and the images

#### Service 2: Dockerfile (for the WebSocket server)

1. Create another **New Application**
2. Set **Build Pack** → **Dockerfile**
3. Set **Port** to `8765`
4. Deploy

> **Note:** The HTML connects to `ws://{hostname}:8765`. If both services are on the same Coolify domain but different ports, the JavaScript will try to connect to the correct WebSocket URL automatically.

### Option C: Single App with Nixpacks (Simplest)

1. Create a **New Application**
2. Set **Build Pack** → **Nixpacks**
3. Set **Port** to `8765`
4. Click **Continue**

But you'll still need to serve the static HTML separately.

## Alternative: Use a single-port solution

Modify `server.py` to also serve HTTP on the same port (using `asyncio`), so you only need **one app** and **one port** in Coolify. Ask me if you want this approach!

## Local Development

```bash
# Install dependencies
pip install websockets

# Start the WebSocket server
python server.py

# In another terminal, serve the frontend
python -m http.server 8000
```

Then open http://localhost:8000 in two browser tabs to play.
