# GoMoKu Connect-5 Game Documentation

## 1. Project Overview

GoMoKu Connect-5 is a browser-based, real-time strategy game for two players.
Players take turns placing black and white stones on a 10 x 10 board. The first
player to place five stones in a continuous horizontal, vertical, or diagonal
line wins.

The application uses a server-authoritative multiplayer model. The browser
displays the game and sends player actions, while the Python server controls
matchmaking, validates moves, changes turns, detects wins and draws, and
synchronizes both players.

### Main features

- Automatic two-player matchmaking
- Real-time play through WebSockets
- Server-authoritative move validation
- Independent concurrent matches
- Horizontal, vertical, and diagonal win detection
- Responsive desktop, tablet, and mobile interface
- Current-turn banner with animated thinking text
- Visual 30-second turn timer
- Active-player cards and circular timer indicators
- Cell-centered stones with hover previews and placement animation
- Last-move and winning-stone highlights
- Glowing line through the five winning stones
- Victory and game-over modal
- Move sounds, victory sound, and confetti
- Two-player rematch confirmation
- Opponent-disconnect handling
- Automated server and rendered-browser tests

## 2. Game Rules

1. Two players enter the matchmaking queue.
2. The first player is assigned Black and the second player is assigned White.
3. Black always takes the first turn.
4. On a turn, the player selects one empty square.
5. A stone is placed in the center of that square.
6. Players alternate turns after every accepted move.
7. The first player with five consecutive stones horizontally, vertically, or
   diagonally wins.
8. If all 100 squares are occupied without a winner, the game is a draw.
9. A player cannot place a stone on an occupied square or play during the
   opponent's turn.

The displayed 30-second timer is currently a visual urgency indicator. Reaching
zero does not skip the turn or cause a forfeit.

## 3. How to Play

### Joining a game

1. Open the game in a web browser.
2. Enter a player name.
3. Select **Connect to Game**.
4. The first connected player sees **Waiting for opponent...**.
5. When a second player connects, the match starts automatically.

### During the match

- The banner above the board shows the live game state.
- **Your Turn - Choose a position** means the board is available to you.
- When the opponent is active, text such as **MED is thinking...** appears with
  a typing animation.
- The active player's card has a brighter border and avatar ring.
- Hovering over a valid empty square displays a transparent preview stone.
- The newest stone receives a brief highlight.
- Invalid squares and the opponent's turn cannot be selected.

### Winning or drawing

When a player wins:

- The board becomes non-interactive.
- The five winning stones glow.
- A gold line connects the centers of the winning stones.
- Other stones are visually dimmed.
- The winner receives a **Victory!** modal.
- The losing player receives a **Game Over** modal naming the winner.
- The victory sound plays for the completed match, and confetti is shown to the
  winner.

For a draw, both players receive a draw result.

### Rematch

After a completed match:

1. Select **Rematch**.
2. The button changes to a waiting state.
3. The opponent must also select **Rematch**.
4. When both players confirm, the board is cleared.
5. The same players and colors are preserved.
6. Black takes the first turn again.

### Leaving

Selecting **Leave** or **Leave Game** closes the current connection and returns
that player to the start screen. The remaining player is informed that the
opponent disconnected, and the board is locked.

## 4. User Interface

### Status banner

The status banner is the main location for live game information. It displays:

- Connection and matchmaking state
- Waiting state
- Current turn
- Animated opponent-thinking text
- Game-over result
- Connection errors

The typing animation is cancelled immediately when the turn or game state
changes. It is disabled when the operating system requests reduced motion.

### Timer

The active turn starts with a visual timer of `00:30`.

- More than 10 seconds: green
- 6 to 10 seconds: yellow/orange warning
- 0 to 5 seconds: red pulse

The timer appears in the status banner and active player card. A circular
progress ring surrounds the active avatar.

### Player cards

Each card contains only player identity information:

- Player name
- Black or White stone assignment
- Local-player label
- Turn timer
- Avatar and timer ring

Thinking and waiting messages are intentionally kept out of the player cards.

### Board

The board is a responsive square with a 10 x 10 grid. Stones occupy
approximately 75% of their square, leaving visible space around every stone.
They are placed inside cells rather than on grid intersections.

## 5. System Architecture

The project follows a browser client and Python server architecture.

```text
Browser A                         Browser B
   |                                 |
   |---- WebSocket messages ---------|
   |              |                  |
   +---------- Python Server --------+
                  |
           Matchmaking queue
           Authoritative games
```

### Client

The browser client consists of:

- `index.html`: page structure, styling, sounds, and modal markup
- `game.js`: WebSocket communication, client state, rendering, timers,
  animations, controls, and user feedback

The client does not decide whether a move is valid or whether a player has won.
It renders the authoritative state sent by the server.

### Server

`server.py` uses `aiohttp` to provide:

- `GET /`: game page
- `GET /game.js`: browser client logic
- `GET /health`: health-check response
- `GET /ws`: WebSocket game connection
- Static GIF assets used by the project

The server maintains:

- A first-in, first-out matchmaking queue
- A unique ID for each match
- One isolated `Game` object per active or rematch-eligible match
- Player connections and color assignments
- Board data and current turn
- Last-move and winning-cell coordinates
- Rematch confirmations

### Authoritative state

Every valid move is checked on the server. The server then broadcasts the
complete state to both players. This prevents the two browsers from maintaining
different boards and prevents basic client-side turn manipulation.

## 6. Game Data Model

### Player

Each connected player stores:

- WebSocket connection
- Player name
- Assigned color
- Current game ID

### Game

Each match stores:

- Game ID
- Black and White players
- 10 x 10 integer board
- Current turn
- Game status
- Result
- Last move
- Five winning cells
- Rematch confirmation set

### Board values

| Value | Meaning |
| --- | --- |
| `0` | Empty square |
| `1` | Black stone |
| `2` | White stone |

## 7. WebSocket Protocol

All messages are JSON objects.

### Client-to-server messages

#### Join matchmaking

```json
{
  "type": "join",
  "name": "Alice"
}
```

The join message must be the first message sent after opening the WebSocket.
Names are trimmed, must not be empty, and are limited to 40 characters.

#### Place a stone

```json
{
  "type": "move",
  "row": 4,
  "col": 5
}
```

Rows and columns are zero-based integers from `0` to `9`.

#### Request a rematch

```json
{
  "type": "rematch_request"
}
```

A rematch starts only after both players request it.

#### Leave the game

```json
{
  "type": "leave"
}
```

### Server-to-client message types

| Type | Purpose |
| --- | --- |
| `waiting` | First player is waiting for an opponent |
| `start` | Two players were paired or a rematch started |
| `state` | A valid move was accepted |
| `error` | A message or move was rejected |
| `game_over` | Win, draw, or disconnect result |
| `rematch_pending` | One player requested a rematch |

### State payload example

```json
{
  "type": "state",
  "game_id": 1,
  "names": {
    "black": "Alice",
    "white": "Bob"
  },
  "colors": {
    "black": 1,
    "white": 2
  },
  "board": [[1, 0], [0, 2]],
  "turn": "black",
  "status": "active",
  "result": null,
  "last_move": {
    "row": 1,
    "col": 1
  },
  "winning_cells": [],
  "your_color": "black",
  "message": "Alice's turn (Black)."
}
```

The real board payload always contains 10 rows with 10 values per row.

## 8. Validation and Error Handling

The server rejects:

- Invalid JSON
- Missing or invalid join messages
- Empty or overlong player names
- Unsupported message types
- Moves before a match starts
- Moves after a match ends
- Out-of-turn moves
- Boolean or non-integer coordinates
- Coordinates outside the board
- Moves on occupied squares
- Rematch requests before a completed game

Rejected actions do not modify the board. The requesting player receives an
`error` payload containing the authoritative state and an explanatory message.

If a player disconnects during an active or completed match, the server removes
the match, marks it abandoned, and notifies the connected opponent.

## 9. Win Detection

After each accepted move, the server searches from every matching stone in four
directions:

1. Horizontal `(0, 1)`
2. Vertical `(1, 0)`
3. Downward diagonal `(1, 1)`
4. Upward diagonal `(1, -1)`

When five matching values are found, the server returns their exact row and
column coordinates. The browser uses those coordinates to highlight the five
stones and calculate a line from the center of the first winning cell to the
center of the last winning cell.

## 10. Local Development

### Requirements

- Python 3.12 or compatible modern Python version
- `pip`
- A modern browser
- Node.js only for the rendered browser smoke test

### Installation

```bash
python3 -m venv venv
./venv/bin/pip install -r requirements.txt
```

### Start the server

```bash
./venv/bin/python server.py
```

The default endpoints are:

- Game: <http://localhost:8080>
- WebSocket: `ws://localhost:8080/ws`
- Health check: <http://localhost:8080/health>

Open the game in two browser windows and join with different names.

To use a different port:

```bash
PORT=8082 ./venv/bin/python server.py
```

## 11. Testing

### Python tests

```bash
./venv/bin/python -m unittest discover -s tests -v
```

The automated suite covers:

- All four winning directions
- Full-board detection
- HTTP and JavaScript asset serving
- Matchmaking and color assignment
- Synchronized moves
- Turn enforcement
- Occupied and invalid coordinates
- Multiple isolated matches
- Winning-cell coordinates
- Draw handling
- Rematch confirmation and reset
- Waiting, active, and finished-game disconnect cleanup

### Rendered browser smoke test

The browser test uses Chrome DevTools Protocol to control two real game pages.
It verifies:

- Two-player joining
- Status hierarchy and animated thinking text
- Typing-animation cancellation on turn change
- Square board geometry
- Hover-preview and stone sizing
- Active and inactive player cards
- Timer warning and danger states
- Placement and last-move animation
- Winning stones and line
- Winner and loser modals
- Sounds and confetti
- Mobile layout without horizontal overflow
- Rematch controls and timer reset
- Leave and opponent-disconnect behavior
- Browser console errors

Start the application on port `8082`, launch Chrome with remote debugging on
port `9222`, and run:

```bash
APP_URL=http://127.0.0.1:8082 node tests/browser_smoke.mjs
```

Screenshots are written to `/tmp/gomoku-browser-smoke`.

### Deployment verification

```bash
./venv/bin/python verify_deployment.py https://your-domain.example
```

This verifies the health endpoint, browser assets, two-player matchmaking,
move synchronization, and rejection of an invalid consecutive move.

## 12. Docker and Coolify Deployment

The Docker image:

- Uses `python:3.12-slim`
- Installs dependencies from `requirements.txt`
- Copies the server, HTML, JavaScript, and image assets
- Exposes port `8080`
- Runs an internal `/health` check

### Coolify settings

1. Use the repository's `Dockerfile`.
2. Configure application port `8080`.
3. Configure health-check path `/health`.
4. Use exactly one replica.
5. Attach the desired HTTPS domain.
6. Allow the proxy to upgrade `/ws` requests to WebSockets.

HTTP and WebSocket traffic use the same port. No second WebSocket port is
required.

## 13. Operational Limitations

- Game state is stored only in memory.
- Restarting the server ends active matches.
- The server must run as one replica because replicas would have separate
  matchmaking queues and game states.
- There is no user account, database, spectator mode, chat, or persistent match
  history.
- The turn timer is visual only and is not synchronized or enforced by the
  server.
- Rematches preserve the original Black and White assignments.

## 14. Project Files

| File | Purpose |
| --- | --- |
| `index.html` | Page structure and visual design |
| `game.js` | Client state, rendering, controls, and WebSocket handling |
| `server.py` | HTTP server, matchmaking, validation, and game rules |
| `tests/test_server.py` | Unit and WebSocket integration tests |
| `tests/browser_smoke.mjs` | Rendered two-browser interaction test |
| `verify_deployment.py` | Public deployment verification |
| `Dockerfile` | Production container definition |
| `requirements.txt` | Python dependencies |
| `README.md` | Quick-start project summary |
| `DESIGN_DOCUMENT.md` | Assignment-oriented design summary |

## 15. Future Improvements

Possible future additions include:

- Server-enforced turn timeouts
- Player accounts and authentication
- Private room codes
- Persistent match history and statistics
- Spectator mode
- In-game chat
- Color swapping between rematches
- Reconnection to an interrupted match
- Computer opponent and difficulty levels
- Configurable board dimensions and win length
