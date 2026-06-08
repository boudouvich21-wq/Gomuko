import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * GoMoKu (Connect-5) Game Server
 * 
 * Supports multiple concurrent game pairs using thread pools.
 * Protocol: text-based over TCP streams.
 * - Client sends: "JOIN:<playerName>"
 * - Server responds: "INIT:<color>:<yourName>:<opponentName>" or
 *                     "START:<color>:<yourName>:<opponentName>"
 * - Client sends: "MOVE:<row>,<col>"
 * - Server broadcasts: "UPDATE:<row>,<col>,<color>" or 
 *                       "WIN:<winnerName>" or
 *                       "ERROR:<message>"
 * - Server sends: "OPPONENT_DISCONNECTED" on disconnect
 */

public class GoMoKuServer {
    // Shared game state
    private static final Map<Integer, GameRoom> games = new ConcurrentHashMap<>();
    private static int gameCounter = 0;
    
    // Waiting player
    private static PlayerData waitingPlayer = null;
    private static final Object waitLock = new Object();

    // Board size
    private static final int SIZE = 10;

    public static void main(String[] args) {
        int port = 8765;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException e) {}
        }

        System.out.println("GoMoKu Server starting on port " + port + "...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is running. Waiting for players...");
            
            // Use thread pool to handle multiple clients
            ExecutorService threadPool = Executors.newCachedThreadPool();
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    // ===== GAME LOGIC =====

    static boolean checkWin(int[][] board, int playerVal) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] == playerVal) {
                    // Horizontal
                    if (c <= SIZE - 5) {
                        boolean win = true;
                        for (int i = 1; i < 5; i++) {
                            if (board[r][c + i] != playerVal) { win = false; break; }
                        }
                        if (win) return true;
                    }
                    // Vertical
                    if (r <= SIZE - 5) {
                        boolean win = true;
                        for (int i = 1; i < 5; i++) {
                            if (board[r + i][c] != playerVal) { win = false; break; }
                        }
                        if (win) return true;
                    }
                    // Diagonal right-down
                    if (r <= SIZE - 5 && c <= SIZE - 5) {
                        boolean win = true;
                        for (int i = 1; i < 5; i++) {
                            if (board[r + i][c + i] != playerVal) { win = false; break; }
                        }
                        if (win) return true;
                    }
                    // Diagonal left-down
                    if (r <= SIZE - 5 && c >= 4) {
                        boolean win = true;
                        for (int i = 1; i < 5; i++) {
                            if (board[r + i][c - i] != playerVal) { win = false; break; }
                        }
                        if (win) return true;
                    }
                }
            }
        }
        return false;
    }

    // ===== DATA CLASSES =====

    static class PlayerData {
        Socket socket;
        PrintWriter out;
        BufferedReader in;
        String name;
        String color;

        PlayerData(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }
    }

    static class GameRoom {
        int id;
        PlayerData black;
        PlayerData white;
        int[][] board = new int[SIZE][SIZE];
        volatile boolean active = false;
        volatile String currentTurn = "black"; // black goes first

        GameRoom(int id) {
            this.id = id;
        }

        synchronized boolean isValidMove(int row, int col, String color) {
            if (!active) return false;
            if (!color.equals(currentTurn)) return false;
            if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return false;
            if (board[row][col] != 0) return false;
            return true;
        }

        synchronized void makeMove(int row, int col, String color) {
            int val = color.equals("black") ? 1 : 2;
            board[row][col] = val;
            // Switch turn
            currentTurn = color.equals("black") ? "white" : "black";
        }

        synchronized boolean checkWin(String color) {
            int val = color.equals("black") ? 1 : 2;
            return GoMoKuServer.checkWin(board, val);
        }

        PlayerData getOpponent(String color) {
            return color.equals("black") ? white : black;
        }

        PlayerData getPlayer(String color) {
            return color.equals("black") ? black : white;
        }
    }

    // ===== CLIENT HANDLER =====

    static class ClientHandler implements Runnable {
        private final Socket socket;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            PlayerData player = null;
            GameRoom game = null;
            String myColor = null;
            String myName = null;
            int gameId = -1;

            try {
                player = new PlayerData(socket);
                String initLine = player.in.readLine();
                if (initLine == null) return;

                if (!initLine.startsWith("JOIN:")) {
                    player.out.println("ERROR:Invalid join message");
                    return;
                }

                myName = initLine.substring(5).trim();
                System.out.println("Player '" + myName + "' joined from " + socket.getInetAddress());

                // Match with waiting player or wait
                synchronized (waitLock) {
                    if (waitingPlayer == null) {
                        // First player - wait
                        waitingPlayer = player;
                        myColor = "black";
                        gameId = gameCounter++;
                        
                        GameRoom newGame = new GameRoom(gameId);
                        newGame.black = player;
                        games.put(gameId, newGame);
                        game = newGame;
                        
                        player.out.println("INIT:black:" + myName + ":Waiting for opponent...");
                        System.out.println("  -> Waiting for opponent. Game ID: " + gameId);
                        
                        // Wait for opponent to join
                        try {
                            waitLock.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                        
                        // Game is now active
                        game = games.get(gameId);
                        if (game != null && game.white != null) {
                            game.active = true;
                            // Notify both players game has started
                            game.black.out.println("START:black:" + game.black.name + ":" + game.white.name);
                            game.white.out.println("START:white:" + game.white.name + ":" + game.black.name);
                            System.out.println("Game " + gameId + " started: " + game.black.name + " (black) vs " + game.white.name + " (white)");
                        }
                    } else {
                        // Second player - start game
                        PlayerData firstPlayer = waitingPlayer;
                        waitingPlayer = null;
                        
                        myColor = "white";
                        gameId = gameCounter - 1;
                        game = games.get(gameId);
                        game.white = player;
                        
                        // Signal the waiting player
                        waitLock.notify();
                    }
                }

                // ===== GAME LOOP =====
                try {
                    String inputLine;
                    while ((inputLine = player.in.readLine()) != null) {
                        if (game == null || !game.active) break;

                        if (inputLine.startsWith("MOVE:")) {
                            String moveData = inputLine.substring(5).trim();
                            String[] parts = moveData.split(",");
                            
                            if (parts.length != 2) {
                                player.out.println("ERROR:Invalid move format. Use MOVE:row,col");
                                continue;
                            }

                            try {
                                int row = Integer.parseInt(parts[0].trim());
                                int col = Integer.parseInt(parts[1].trim());

                                if (!game.isValidMove(row, col, myColor)) {
                                    player.out.println("ERROR:Invalid move. Wait for your turn or spot is taken.");
                                    continue;
                                }

                                game.makeMove(row, col, myColor);

                                if (game.checkWin(myColor)) {
                                    game.active = false;
                                    String winMsg = "WIN:" + myName;
                                    game.getPlayer("black").out.println(winMsg);
                                    game.getPlayer("white").out.println(winMsg);
                                    System.out.println("Game " + gameId + " - " + myName + " won!");
                                } else {
                                    // Broadcast move to both players
                                    String moveMsg = "UPDATE:" + row + "," + col + "," + myColor;
                                    game.getPlayer("black").out.println(moveMsg);
                                    game.getPlayer("white").out.println(moveMsg);
                                }
                            } catch (NumberFormatException e) {
                                player.out.println("ERROR:Invalid coordinates");
                            }
                        } else if (inputLine.equals("QUIT")) {
                            break;
                        }
                    }
                } catch (IOException e) {
                    // Client disconnected
                }

            } catch (IOException e) {
                System.err.println("Connection error: " + e.getMessage());
            } finally {
                // Cleanup
                if (game != null && game.active) {
                    game.active = false;
                    PlayerData opponent = game.getOpponent(myColor);
                    if (opponent != null) {
                        try {
                            opponent.out.println("OPPONENT_DISCONNECTED:" + myName);
                        } catch (Exception e) {}
                    }
                    System.out.println("Game " + gameId + " ended - " + myName + " disconnected");
                }
                
                // If this was the waiting player, clean up
                synchronized (waitLock) {
                    if (waitingPlayer == player) {
                        waitingPlayer = null;
                        waitLock.notify(); // Wake up in case someone is waiting
                        if (game != null) {
                            games.remove(gameId);
                        }
                    }
                }
                
                try {
                    socket.close();
                } catch (IOException e) {}
                
                System.out.println("Player '" + myName + "' disconnected");
            }
        }
    }
}
