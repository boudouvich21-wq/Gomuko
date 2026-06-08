import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * GoMoKu (Connect-5) Game Server
 * 
 * Supports multiple concurrent game pairs using thread pools.
 * Also serves HTTP for health checks (on a separate thread).
 */

public class GoMoKuServer {
    private static final Map<Integer, GameRoom> games = new ConcurrentHashMap<>();
    private static int gameCounter = 0;
    private static PlayerData waitingPlayer = null;
    private static final Object waitLock = new Object();
    private static final int SIZE = 10;
    
    private static int gamePort;
    private static int httpPort;

    public static void main(String[] args) {
        gamePort = 8765;
        httpPort = 8080;
        if (args.length > 0) {
            try { gamePort = Integer.parseInt(args[0]); } catch (NumberFormatException e) {}
        }
        if (args.length > 1) {
            try { httpPort = Integer.parseInt(args[1]); } catch (NumberFormatException e) {}
        }

        System.out.println("GoMoKu Server starting...");
        System.out.println("  - Game server (TCP):  port " + gamePort);
        System.out.println("  - HTTP health check:  port " + httpPort);
        
        // Start HTTP server on separate thread
        new Thread(() -> startHttpServer()).start();
        
        // Start game server (main thread)
        startGameServer();
    }

    private static void startHttpServer() {
        try (ServerSocket serverSocket = new ServerSocket(httpPort)) {
            System.out.println("HTTP server listening on port " + httpPort);
            ExecutorService threadPool = Executors.newCachedThreadPool();
            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(() -> handleHttpRequest(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("HTTP server error: " + e.getMessage());
        }
    }

    static void handleHttpRequest(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream out = socket.getOutputStream();
            
            String requestLine = reader.readLine();
            if (requestLine == null) {
                socket.close();
                return;
            }
            
            String header;
            while ((header = reader.readLine()) != null && !header.isEmpty()) {}
            
            String path = "/";
            try {
                String[] parts = requestLine.split(" ");
                if (parts.length >= 2) {
                    path = parts[1];
                }
            } catch (Exception e) {}
            
            if (path.equals("/")) {
                path = "/index.html";
            }
            
            String filename = path.startsWith("/") ? path.substring(1) : path;
            
            // MIME types
            Map<String, String> mimeTypes = new HashMap<>();
            mimeTypes.put("html", "text/html");
            mimeTypes.put("jar", "application/java-archive");
            mimeTypes.put("gif", "image/gif");
            mimeTypes.put("png", "image/png");
            mimeTypes.put("jpg", "image/jpeg");
            mimeTypes.put("css", "text/css");
            mimeTypes.put("js", "application/javascript");
            mimeTypes.put("class", "application/java-vm");
            
            File file = new File(filename);
            byte[] fileData = null;
            String mimeType = "application/octet-stream";
            
            if (file.exists() && !file.isDirectory()) {
                String ext = "";
                int dotIndex = filename.lastIndexOf('.');
                if (dotIndex > 0) {
                    ext = filename.substring(dotIndex + 1).toLowerCase();
                }
                String mime = mimeTypes.get(ext);
                if (mime != null) mimeType = mime;
                
                FileInputStream fis = new FileInputStream(file);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                fis.close();
                fileData = baos.toByteArray();
            }
            
            if (fileData != null) {
                String response = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + fileData.length + "\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "\r\n";
                out.write(response.getBytes());
                out.write(fileData);
            } else {
                String body = "<html><body><h1>GoMoKu Server Running</h1>" +
                             "<p>Game server on port " + gamePort + "</p></body></html>";
                String response = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/html\r\n" +
                                "Content-Length: " + body.length() + "\r\n" +
                                "\r\n" + body;
                out.write(response.getBytes());
            }
            
            out.flush();
            socket.close();
        } catch (Exception e) {
            try { socket.close(); } catch (IOException ex) {}
        }
    }

    private static void startGameServer() {
        try (ServerSocket serverSocket = new ServerSocket(gamePort)) {
            System.out.println("Game server listening on port " + gamePort);
            ExecutorService threadPool = Executors.newCachedThreadPool();
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Game server error: " + e.getMessage());
        }
    }

    // ===== GAME LOGIC =====

    static boolean checkWin(int[][] board, int playerVal) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] == playerVal) {
                    if (c <= SIZE - 5) {
                        boolean win = true;
                        for (int i = 1; i < 5; i++) {
                            if (board[r][c + i] != playerVal) { win = false; break; }
                        }
                        if (win) return true;
                    }
                    if (r <= SIZE - 5) {
                        boolean win = true;
                        for (int i = 1; i < 5; i++) {
                            if (board[r + i][c] != playerVal) { win = false; break; }
                        }
                        if (win) return true;
                    }
                    if (r <= SIZE - 5 && c <= SIZE - 5) {
                        boolean win = true;
                        for (int i = 1; i < 5; i++) {
                            if (board[r + i][c + i] != playerVal) { win = false; break; }
                        }
                        if (win) return true;
                    }
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
        volatile String currentTurn = "black";

        GameRoom(int id) { this.id = id; }

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

    static class ClientHandler implements Runnable {
        private final Socket socket;

        ClientHandler(Socket socket) { this.socket = socket; }

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

                synchronized (waitLock) {
                    if (waitingPlayer == null) {
                        waitingPlayer = player;
                        myColor = "black";
                        gameId = gameCounter++;
                        
                        GameRoom newGame = new GameRoom(gameId);
                        newGame.black = player;
                        games.put(gameId, newGame);
                        game = newGame;
                        
                        player.out.println("INIT:black:" + myName + ":Waiting for opponent...");
                        System.out.println("  -> Waiting for opponent. Game ID: " + gameId);
                        
                        try { waitLock.wait(); }
                        catch (InterruptedException e) { return; }
                        
                        game = games.get(gameId);
                        if (game != null && game.white != null) {
                            game.active = true;
                            game.black.out.println("START:black:" + game.black.name + ":" + game.white.name);
                            game.white.out.println("START:white:" + game.white.name + ":" + game.black.name);
                            System.out.println("Game " + gameId + " started: " + game.black.name + " (black) vs " + game.white.name + " (white)");
                        }
                    } else {
                        PlayerData firstPlayer = waitingPlayer;
                        waitingPlayer = null;
                        
                        myColor = "white";
                        gameId = gameCounter - 1;
                        game = games.get(gameId);
                        game.white = player;
                        
                        waitLock.notify();
                    }
                }

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
                } catch (IOException e) {}

            } catch (IOException e) {
                System.err.println("Connection error: " + e.getMessage());
            } finally {
                if (game != null && game.active) {
                    game.active = false;
                    PlayerData opponent = game.getOpponent(myColor);
                    if (opponent != null) {
                        try { opponent.out.println("OPPONENT_DISCONNECTED:" + myName); }
                        catch (Exception e) {}
                    }
                    System.out.println("Game " + gameId + " ended - " + myName + " disconnected");
                }
                
                synchronized (waitLock) {
                    if (waitingPlayer == player) {
                        waitingPlayer = null;
                        waitLock.notify();
                        if (game != null) games.remove(gameId);
                    }
                }
                
                try { socket.close(); } catch (IOException e) {}
                System.out.println("Player '" + myName + "' disconnected");
            }
        }
    }
}
