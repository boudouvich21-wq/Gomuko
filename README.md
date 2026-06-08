# GoMoKu (Connect-5) Game

A real-time multiplayer Gomoku (5-in-a-row) game.

## Architecture

- **Server**: Python with WebSockets library
- **Client**: HTML + JavaScript (runs in any browser)
- **Protocol**: WebSocket (ws://)

## Deployment on Coolify

1. Create a **New Application**
2. Connect GitHub repo: `boudouvich21-wq/Gomuko`
3. **Branch**: `main`
4. **Build Pack**: **Dockerfile**
5. **Port**: `8080`
6. **Is it a static site?**: **No**
7. Deploy!

## How it works

| Port | Service |
|------|---------|
| `8080` | HTTP (serves index.html, GIFs) — Coolify healthcheck |
| `8081` | WebSocket (game logic) — auto-connected by browser |

## How to play

1. Open the deployed URL in two browser windows
2. Enter a name and click "Enter Arena" in both
3. The first player gets Black, second gets White
4. Click cells to place stones
5. Get 5 in a row to win!

## Local development

```bash
pip install websockets
python3 server.py
# Open http://localhost:8080
```
