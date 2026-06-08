# Internet GoMoKu (Connect-5)

A browser-based, real-time two-player GoMoKu game. The Python server supports
multiple independent games concurrently and is authoritative for matchmaking,
turns, move validation, board state, wins, draws, and disconnects.

## Architecture

- **Client:** HTML, CSS, and JavaScript in `index.html`
- **Server:** Python and `aiohttp` in `server.py`
- **Transport:** WebSocket at `/ws`
- **HTTP:** page, GIF assets, and `/health` use the same public port
- **State:** in-memory queue and isolated `Game` objects

The browser automatically selects `ws://` locally and `wss://` when the page is
served over HTTPS.

## Run Locally

```bash
python3 -m venv venv
./venv/bin/pip install -r requirements.txt
./venv/bin/python server.py
```

Open <http://localhost:8080> in two browser windows. Enter a different player
name in each window. Black moves first.

Run the automated tests:

```bash
./venv/bin/python -m unittest discover -s tests -v
```

## Deploy on Coolify

1. Create an application from this repository.
2. Select **Dockerfile** as the build method.
3. Set the application/container port to **8080**.
4. Set the health-check path to **`/health`**.
5. Use exactly **one replica**.
6. Enable the normal HTTPS domain/proxy configuration and deploy.

Do not expose a second WebSocket port. HTTP and WebSocket traffic both pass
through port `8080`, and Coolify upgrades requests to `/ws`.

The application stores active matches in memory. A container restart ends
current games, and multiple replicas would create separate matchmaking queues.

## Game Protocol

Client messages:

```json
{"type": "join", "name": "Alice"}
{"type": "move", "row": 4, "col": 5}
```

Server message types:

- `waiting`: the first player is queued.
- `start`: two players have been matched.
- `state`: a valid move was accepted and broadcast.
- `error`: a move or message was rejected without changing the board.
- `game_over`: win, draw, or opponent disconnect.

State messages include the board, player names, assigned color, current turn,
game status, result, game ID, and a display message.

## Demo Checklist

1. Open the deployed URL in two separate browser windows.
2. Join as two players and confirm both names appear.
3. Confirm Black can move first and both boards update immediately.
4. Try moving twice as the same player and show that the server rejects it.
5. Complete five stones horizontally, vertically, or diagonally.
6. Open four windows to demonstrate two isolated concurrent games.
7. Close one active player window and show the opponent-disconnected result.

## Submission Files

- `DESIGN_DOCUMENT.docx`: printable two-page design document
- `DESIGN_DOCUMENT.md`: editable source copy of the design content
- `PROGRAM_LISTING.docx`: print-ready full client and server source listing
- `index.html`: full browser client program
- `server.py`: full server program
- `tests/test_server.py`: automated server and protocol tests

The original assignment requests Java applet and Java server technologies.
This implementation uses a modern browser client and Python server; confirm
that this technology substitution is acceptable to the lecturer.
