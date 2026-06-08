# Internet GoMoKu (Connect-5) - Design Document

**Group names / student numbers:** [INSERT NAMES AND STUDENT NUMBERS]  
**Deployed game URL:** [INSERT FINAL COOLIFY URL]  
**Demonstration date:** June 10, 2026

## 1. System Structure

The system follows a client-server model. The client is a browser application
implemented with HTML, CSS, and JavaScript. It displays the two player names,
the assigned stone color, game status, current turn, and a 10 x 10 board. The
client only sends join and move requests; it does not decide whether a move is
valid.

The Python server uses `aiohttp` and listens on one configurable port. It serves
the web page and GIF assets over HTTP, exposes `/health` for deployment health
checks, and accepts WebSocket connections at `/ws`. Using one public port lets
the Coolify reverse proxy handle normal HTTPS and secure WebSocket traffic
through the same application domain.

## 2. Client-Server Communication

After opening the page, a player enters a name. The browser opens `/ws` and
sends `{"type":"join","name":"..."}`. The first player receives `waiting`.
When a second player joins, the server creates a game and sends `start` to both
players. Black always moves first.

A move is sent as `{"type":"move","row":r,"col":c}`. The server validates the
request and, if accepted, broadcasts a complete authoritative state to both
players. Server messages use the types `waiting`, `start`, `state`, `error`,
and `game_over`. State data contains the game ID, both names, color assignment,
10 x 10 board, current turn, game status, result, and display message.

The browser derives `ws://` or `wss://` from the page protocol. This avoids
hard-coded hosts and ports and allows the same client to run locally or behind
Coolify HTTPS.

## 3. Concurrent Games and State

The server keeps a first-come, first-served waiting queue. When two available
players are found, it assigns them a unique game ID and creates an independent
`Game` object. Each game stores its two players, names, board, current turn,
status, and result. Therefore, moves in one game cannot affect another game.

All state is kept in memory and is managed by one asynchronous event loop.
Matchmaking changes are protected by an asynchronous lock. The deployment must
use one replica because separate replicas would have separate queues and game
memory. Restarting the container ends active games.

## 4. Validation and Game Rules

The server rejects malformed JSON, unsupported message types, missing or
overlong names, non-integer coordinates, coordinates outside 0-9, occupied
squares, moves before a game starts, and moves made out of turn. Rejected moves
return an `error` state and never change the board.

After each valid move, the server checks horizontal, vertical, downward
diagonal, and upward diagonal directions for five matching stones. A winning
move ends the game and sends `game_over` to both players. If every square is
occupied without a winner, the result is a draw. Otherwise, the turn changes
to the other color and the new state is broadcast.

## 5. Failure Handling, Testing, and Deployment

If a waiting player disconnects, the server removes that connection from the
queue. If an active player disconnects, the game is marked abandoned, the
opponent receives `game_over`, and the game is removed from memory. The client
locks the board while waiting, during the opponent's turn, after completion,
or after a connection failure.

Automated tests cover all four win directions, full-board detection,
single-port HTTP and WebSocket access, two-player synchronization, turn and
move validation, two concurrent isolated games, winning broadcasts, and
disconnect cleanup. The Docker image installs a pinned dependency, exposes
port 8080, and checks `/health`. Coolify must route one application port and
run one replica.

**Technology note:** The assignment text specifies Java applets and a Java
server. This submission uses a modern browser client and Python WebSocket
server because Java applets are no longer supported by current browsers.
