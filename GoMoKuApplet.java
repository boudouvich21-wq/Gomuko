import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

/**
 * GoMoKu (Connect-5) Game Client - Java Applet
 * 
 * Communicates with GoMoKuServer via TCP stream sockets.
 * Displays the 10x10 board, player names, and game status.
 * Uses the provided GIF files for board background and stones.
 */

public class GoMoKuApplet extends Applet implements MouseListener {
    // Board dimensions
    private static final int SIZE = 10;
    private static final int CELL_SIZE = 45;
    private static final int BOARD_PIXEL_SIZE = SIZE * CELL_SIZE;
    
    // Connection
    private Socket socket;
    private PrintWriter serverOut;
    private BufferedReader serverIn;
    private String playerName = "";
    private String myColor = "";
    private String opponentName = "Waiting...";
    
    // Game state
    private int[][] board = new int[SIZE][SIZE]; // 0=empty, 1=black, 2=white
    private String statusMsg = "Connecting to server...";
    private boolean myTurn = false;
    private boolean gameActive = false;
    private boolean gameOver = false;
    
    // Images
    private Image boardBg;
    private Image blackStone;
    private Image whiteStone;
    private Image offscreenImage;
    private Graphics offscreenGraphics;
    
    // UI Fields
    private TextField nameField;
    private Button joinButton;
    private Panel startPanel;
    private boolean inGame = false;
    
    // Fonts
    private Font titleFont = new Font("SansSerif", Font.BOLD, 24);
    private Font nameFont = new Font("SansSerif", Font.BOLD, 14);
    private Font statusFont = new Font("SansSerif", Font.PLAIN, 13);
    private Font smallFont = new Font("SansSerif", Font.PLAIN, 11);

    @Override
    public void init() {
        setLayout(new BorderLayout());
        setBackground(new Color(0x1a, 0x1f, 0x2e));
        setSize(500, 650);
        
        // Load images
        boardBg = getImage(getDocumentBase(), "background.gif");
        blackStone = getImage(getDocumentBase(), "blackStone.gif");
        whiteStone = getImage(getDocumentBase(), "whiteStone.gif");
        
        // Wait for images to load
        MediaTracker tracker = new MediaTracker(this);
        tracker.addImage(boardBg, 0);
        tracker.addImage(blackStone, 1);
        tracker.addImage(whiteStone, 2);
        try {
            tracker.waitForAll();
        } catch (InterruptedException e) {}
        
        // Create start panel
        startPanel = new Panel(new GridBagLayout());
        startPanel.setBackground(new Color(0x1a, 0x1f, 0x2e));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        Label titleLabel = new Label("GoMoKu");
        titleLabel.setFont(titleFont);
        titleLabel.setForeground(Color.WHITE);
        gbc.gridx = 0; gbc.gridy = 0;
        startPanel.add(titleLabel, gbc);
        
        Label nameLabel = new Label("Enter Your Name:");
        nameLabel.setFont(statusFont);
        nameLabel.setForeground(new Color(0x94, 0xa3, 0xb8));
        gbc.gridy = 1;
        startPanel.add(nameLabel, gbc);
        
        nameField = new TextField(15);
        nameField.setFont(nameFont);
        nameField.setBackground(new Color(0x0d, 0x11, 0x17));
        nameField.setForeground(Color.WHITE);
        gbc.gridy = 2;
        startPanel.add(nameField, gbc);
        
        joinButton = new Button("Enter Arena");
        joinButton.setFont(nameFont);
        joinButton.setBackground(new Color(0x63, 0x66, 0xf1));
        joinButton.setForeground(Color.WHITE);
        joinButton.addActionListener(e -> startGame());
        gbc.gridy = 3;
        startPanel.add(joinButton, gbc);
        
        add(startPanel, BorderLayout.CENTER);
        
        addMouseListener(this);
    }

    private void startGame() {
        playerName = nameField.getText().trim();
        if (playerName.isEmpty()) {
            playerName = "Player";
        }
        
        // Get server address from document base or use parameter
        String serverHost = getParameter("server");
        if (serverHost == null || serverHost.isEmpty()) {
            serverHost = getDocumentBase().getHost();
        }
        if (serverHost == null || serverHost.isEmpty() || serverHost.equals("")) {
            serverHost = "127.0.0.1";
        }
        
        int serverPort = 8765;
        String portParam = getParameter("port");
        if (portParam != null && !portParam.isEmpty()) {
            try { serverPort = Integer.parseInt(portParam); } catch (NumberFormatException e) {}
        }
        
        try {
            socket = new Socket(serverHost, serverPort);
            serverOut = new PrintWriter(socket.getOutputStream(), true);
            serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Send join message
            serverOut.println("JOIN:" + playerName);
            
            // Start listener thread
            new Thread(this::listenToServer).start();
            
            // Show game UI
            remove(startPanel);
            inGame = true;
            statusMsg = "Connected. Waiting for opponent...";
            repaint();
            
        } catch (IOException e) {
            statusMsg = "Connection failed: " + e.getMessage();
            repaint();
        }
    }

    private void listenToServer() {
        try {
            String line;
            while ((line = serverIn.readLine()) != null) {
                processServerMessage(line);
            }
        } catch (IOException e) {
            if (gameActive) {
                statusMsg = "Connection lost!";
                gameActive = false;
                repaint();
            }
        }
    }

    private void processServerMessage(String message) {
        System.out.println("Received: " + message);
        
        if (message.startsWith("INIT:")) {
            // INIT:color:myName:opponentName
            String[] parts = message.split(":", 4);
            myColor = parts[1];
            playerName = parts[2];
            opponentName = parts.length > 3 ? parts[3] : "Waiting...";
            myTurn = myColor.equals("black"); // Black goes first
            statusMsg = "Waiting for Player 2 to join...";
            repaint();
            
        } else if (message.startsWith("START:")) {
            // START:color:myName:opponentName
            String[] parts = message.split(":", 4);
            myColor = parts[1];
            playerName = parts[2];
            opponentName = parts[3];
            gameActive = true;
            myTurn = myColor.equals("black");
            statusMsg = "Game Started! " + (myTurn ? "Your turn (Black)" : "Opponent's turn (White)");
            repaint();
            
        } else if (message.startsWith("UPDATE:")) {
            // UPDATE:row,col,color
            String[] parts = message.substring(7).split(",");
            int row = Integer.parseInt(parts[0].trim());
            int col = Integer.parseInt(parts[1].trim());
            String color = parts[2].trim();
            
            int val = color.equals("black") ? 1 : 2;
            board[row][col] = val;
            
            // Switch turns
            myTurn = !myTurn;
            if (!gameOver) {
                statusMsg = myTurn ? "Your turn (" + (myColor.equals("black") ? "Black" : "White") + ")" 
                                   : "Opponent's turn...";
            }
            repaint();
            
        } else if (message.startsWith("WIN:")) {
            // WIN:winnerName
            String winnerName = message.substring(4);
            gameActive = false;
            gameOver = true;
            if (winnerName.equals(playerName)) {
                statusMsg = "You won! Congratulations!";
            } else {
                statusMsg = winnerName + " won!";
            }
            repaint();
            
        } else if (message.startsWith("ERROR:")) {
            statusMsg = message.substring(6);
            repaint();
            
        } else if (message.startsWith("OPPONENT_DISCONNECTED:")) {
            String discName = message.substring(21);
            gameActive = false;
            statusMsg = discName + " disconnected. Game over.";
            repaint();
        }
    }

    @Override
    public void paint(Graphics g) {
        if (!inGame) return;
        
        // Offscreen buffer
        if (offscreenImage == null || offscreenImage.getWidth(null) != getWidth() 
                                     || offscreenImage.getHeight(null) != getHeight()) {
            offscreenImage = createImage(getWidth(), getHeight());
            offscreenGraphics = offscreenImage.getGraphics();
        }
        
        Graphics2D g2d = (Graphics2D) offscreenGraphics;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Background
        g2d.setColor(new Color(0x1a, 0x1f, 0x2e));
        g2d.fillRect(0, 0, getWidth(), getHeight());
        
        // Title
        g2d.setFont(titleFont);
        g2d.setColor(Color.WHITE);
        String title = "GoMoKu";
        FontMetrics fm = g2d.getFontMetrics();
        int titleX = (getWidth() - fm.stringWidth(title)) / 2;
        g2d.drawString(title, titleX, 40);
        
        // Player names bar
        int barY = 55;
        int barWidth = BOARD_PIXEL_SIZE + 20;
        int barX = (getWidth() - barWidth) / 2;
        
        g2d.setColor(new Color(0x22, 0x28, 0x3a));
        g2d.fillRoundRect(barX, barY, barWidth, 70, 10, 10);
        g2d.setColor(new Color(0x33, 0x3a, 0x4d));
        g2d.drawRoundRect(barX, barY, barWidth, 70, 10, 10);
        
        // Player 1 (Black)
        g2d.setFont(smallFont);
        g2d.setColor(new Color(0x94, 0xa3, 0xb8));
        String p1Label = "Player 1 (Black)";
        String p2Label = "Player 2 (White)";
        int col1X = barX + 20;
        int col2X = barX + barWidth / 2 + 10;
        g2d.drawString(p1Label, col1X, barY + 18);
        g2d.drawString(p2Label, col2X, barY + 18);
        
        // Player names
        g2d.setFont(nameFont);
        String blackName = myColor.equals("black") ? playerName : opponentName;
        String whiteName = myColor.equals("white") ? playerName : opponentName;
        
        // If names are empty yet (waiting)
        if (myColor.isEmpty()) {
            // Waiting state - don't show
        }
        
        g2d.setColor(Color.WHITE);
        if (myColor.equals("black")) {
            g2d.setColor(new Color(0x81, 0x8c, 0xf8)); // Highlight local player
        }
        g2d.drawString(blackName, col1X, barY + 40);
        
        g2d.setColor(Color.WHITE);
        if (myColor.equals("white")) {
            g2d.setColor(new Color(0x81, 0x8c, 0xf8)); // Highlight local player
        }
        g2d.drawString(whiteName, col2X, barY + 40);
        
        // Status
        g2d.setColor(new Color(0x38, 0xbd, 0xf8));
        g2d.setFont(statusFont);
        int statusY = barY + 62;
        g2d.drawString(statusMsg, barX + 10, statusY);
        
        // Board
        int boardX = (getWidth() - BOARD_PIXEL_SIZE) / 2;
        int boardY = barY + 80;
        
        // Draw board background
        if (boardBg != null) {
            g2d.drawImage(boardBg, boardX, boardY, BOARD_PIXEL_SIZE, BOARD_PIXEL_SIZE, this);
        } else {
            g2d.setColor(new Color(0x8B, 0x73, 0x55));
            g2d.fillRect(boardX, boardY, BOARD_PIXEL_SIZE, BOARD_PIXEL_SIZE);
        }
        
        // Board border
        g2d.setColor(new Color(0x18, 0x1d, 0x26));
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRect(boardX - 2, boardY - 2, BOARD_PIXEL_SIZE + 4, BOARD_PIXEL_SIZE + 4);
        
        // Draw grid lines
        g2d.setColor(new Color(0, 0, 0, 60));
        g2d.setStroke(new BasicStroke(1));
        for (int i = 0; i <= SIZE; i++) {
            int x = boardX + i * CELL_SIZE;
            int y = boardY + i * CELL_SIZE;
            g2d.drawLine(x, boardY, x, boardY + BOARD_PIXEL_SIZE);
            g2d.drawLine(boardX, y, boardX + BOARD_PIXEL_SIZE, y);
        }
        
        // Draw stones
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] != 0) {
                    int stoneX = boardX + c * CELL_SIZE + 3;
                    int stoneY = boardY + r * CELL_SIZE + 3;
                    int stoneSize = CELL_SIZE - 6;
                    
                    if (board[r][c] == 1 && blackStone != null) {
                        g2d.drawImage(blackStone, stoneX, stoneY, stoneSize, stoneSize, this);
                    } else if (board[r][c] == 2 && whiteStone != null) {
                        g2d.drawImage(whiteStone, stoneX, stoneY, stoneSize, stoneSize, this);
                    } else {
                        // Fallback: draw circle
                        g2d.setColor(board[r][c] == 1 ? Color.BLACK : Color.WHITE);
                        g2d.fillOval(stoneX, stoneY, stoneSize, stoneSize);
                        g2d.setColor(Color.GRAY);
                        g2d.drawOval(stoneX, stoneY, stoneSize, stoneSize);
                    }
                }
            }
        }
        
        // Draw instructions if game over
        if (gameOver) {
            g2d.setColor(new Color(0, 0, 0, 150));
            g2d.fillRect(boardX, boardY + BOARD_PIXEL_SIZE / 2 - 25, BOARD_PIXEL_SIZE, 50);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 18));
            String endMsg = "Game Over - " + statusMsg;
            fm = g2d.getFontMetrics();
            int msgX = boardX + (BOARD_PIXEL_SIZE - fm.stringWidth(endMsg)) / 2;
            g2d.drawString(endMsg, msgX, boardY + BOARD_PIXEL_SIZE / 2 + 8);
        }
        
        // Draw to screen
        g.drawImage(offscreenImage, 0, 0, this);
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (!gameActive || !myTurn || gameOver) return;
        
        int boardX = (getWidth() - BOARD_PIXEL_SIZE) / 2;
        int barY = 55;
        int boardY = barY + 80;
        
        int mx = e.getX();
        int my = e.getY();
        
        // Check if click is within board
        if (mx >= boardX && mx < boardX + BOARD_PIXEL_SIZE &&
            my >= boardY && my < boardY + BOARD_PIXEL_SIZE) {
            
            int col = (mx - boardX) / CELL_SIZE;
            int row = (my - boardY) / CELL_SIZE;
            
            // Validate bounds
            if (row >= 0 && row < SIZE && col >= 0 && col < SIZE) {
                // Check if spot is empty
                if (board[row][col] == 0) {
                    sendMove(row, col);
                } else {
                    statusMsg = "Spot already taken!";
                    repaint();
                }
            }
        }
    }

    private void sendMove(int row, int col) {
        if (serverOut != null) {
            serverOut.println("MOVE:" + row + "," + col);
            // Don't update locally - wait for server broadcast
            myTurn = false;
            statusMsg = "Waiting for opponent's move...";
            repaint();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {}
    @Override
    public void mouseReleased(MouseEvent e) {}
    @Override
    public void mouseEntered(MouseEvent e) {}
    @Override
    public void mouseExited(MouseEvent e) {}
    
    @Override
    public void destroy() {
        try {
            if (serverOut != null) serverOut.println("QUIT");
            if (socket != null) socket.close();
        } catch (IOException e) {}
    }
}
