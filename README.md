# GoMoKu (Connect-5) Game

**Distributed Systems Project**

Group project implementing the classic GoMoKu (Connect-5) game using Java client-server architecture over TCP stream sockets.

## System Architecture

```
┌─────────────┐      TCP Sockets       ┌──────────────┐
│  Client 1   │ ◄─────────────────────► │              │
│ (Java       │                         │   GoMoKu     │
│  Swing GUI) │                         │   Server     │
├─────────────┤                         │              │
│  Client 2   │ ◄─────────────────────► │  (Java)      │
│ (Java       │                         │              │
│  Swing GUI) │                         └──────────────┘
└─────────────┘
```

- **Server**: Multi-threaded Java server supporting multiple concurrent game pairs using a thread pool. Also serves an HTTP health check endpoint for deployment monitoring.
- **Client**: Standalone Java Swing application. Can also be run as a Java Applet (legacy).

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
| `GoMoKuServer.java` | Game server + HTTP health endpoint |
| `GoMoKuClient.java` | Standalone game client (Swing GUI) |
| `GoMoKuApplet.java` | Java Applet client (legacy) |
| `index.html` | Web page with download links |
| `gomoku.jar` | Runnable JAR with all classes |
| `background.gif` | Board background image |
| `blackStone.gif` | Black stone image |
| `whiteStone.gif` | White stone image |

## How to Run

### Prerequisites
- Java 8 or later installed ([Download Java](https://java.com/download/))

### Quick Start (Client)
```bash
# Download the JAR and run:
java -jar gomoku.jar
```

### Start the Server
```bash
# Compile (if needed)
javac GoMoKuServer.java

# Run server (game on 8765, HTTP health on 8080)
java GoMoKuServer 8765 8080
```

### Play
1. Launch the client: `java -jar gomoku.jar`
2. Enter the server address (e.g., `localhost` for local, or your deployed IP)
3. Enter port `8765`, your name, and click "Enter Arena"
4. Wait for a second player to join
5. Click cells to place stones — get 5 in a row to win!

## Features

- ✅ Two-player gameplay (Black vs White)
- ✅ Multiple concurrent game pairs supported
- ✅ Win detection (5 in a row - horizontal, vertical, diagonal)
- ✅ Invalid move handling (wrong turn, occupied spot)
- ✅ Player disconnect handling
- ✅ Standalone Swing GUI (works in modern browsers/OS)
- ✅ Runnable JAR for easy distribution
- ✅ 10×10 game board with GIF stone images

## Deployed URL

**Game Server:** `http://m13b03onwpt6zxzpilg8jyph.153.92.221.98.sslip.io:8080/`
**Game Port:** `8765`

---

**Team Members**: [Your names/student numbers here]
