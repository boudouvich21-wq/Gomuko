# GoMoKu (Connect-5) Game

**Distributed Systems Project**

Group project implementing the classic GoMoKu (Connect-5) game using Java client-server architecture over TCP stream sockets.

## System Architecture

```
┌─────────────┐      TCP Sockets       ┌──────────────┐
│  Client 1   │ ◄─────────────────────► │              │
│ (Java       │                         │   GoMoKu     │
│  Applet)    │                         │   Server     │
├─────────────┤                         │              │
│  Client 2   │ ◄─────────────────────► │  (Java)      │
│ (Java       │                         │              │
│  Applet)    │                         └──────────────┘
└─────────────┘
```

- **Server**: Multi-threaded Java server supporting multiple concurrent game pairs using a thread pool.
- **Client**: Java Applet with GUI using AWT, communicating with the server via stream sockets.

## Protocol

Text-based protocol over TCP:

| Direction | Message | Description |
|-----------|---------|-------------|
| Client → Server | `JOIN:<name>` | Player joins the game |
| Server → Client | `INIT:color:myName:opponentName` | First player, waiting for opponent |
| Server → Client | `START:color:myName:opponentName` | Game started, both players ready |
| Client → Server | `MOVE:row,col` | Player makes a move |
| Server → Client | `UPDATE:row,col,color` | Broadcast move to both players |
| Server → Client | `WIN:winnerName` | A player has won |
| Server → Client | `ERROR:message` | Error notification |
| Server → Client | `OPPONENT_DISCONNECTED:name` | Opponent left |
| Client → Server | `QUIT` | Player disconnects |

## Files

| File | Description |
|------|-------------|
| `GoMoKuServer.java` | Game server - handles connections, manages games, validates moves |
| `GoMoKuApplet.java` | Game client - Java Applet with GUI |
| `index.html` | Web page to launch the applet |
| `background.gif` | Board background image |
| `blackStone.gif` | Black stone image |
| `whiteStone.gif` | White stone image |

## How to Run

### 1. Compile
```bash
javac GoMoKuServer.java GoMoKuApplet.java
jar cf gomoku.jar GoMoKuApplet.class GoMoKuServer*.class
```

### 2. Start the Server
```bash
java GoMoKuServer [port]
```
Default port: 8765

### 3. Play
- **Via Applet**: Open `index.html` in a browser (requires Java plugin)
- **Via direct connection**: Two clients connect to the server IP and port

## Features

- ✅ Two-player gameplay (Black vs White)
- ✅ Multiple concurrent game pairs supported
- ✅ Win detection (5 in a row - horizontal, vertical, diagonal)
- ✅ Invalid move handling (wrong turn, occupied spot)
- ✅ Player disconnect handling
- ✅ 10×10 game board with GIF images

## URL

*[Your deployed URL here]*

---

**Team Members**: [Your names/student numbers here]
