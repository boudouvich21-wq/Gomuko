import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

/**
 * GoMoKu (Connect-5) Game Client - Standalone Java Application
 * 
 * Uses Swing for the GUI. Communicates with GoMoKuServer via TCP stream sockets.
 * Supports the classic Gomoku game: two players, 10x10 board, 5 in a row wins.
 */

public class GoMoKuClient extends JFrame {
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
    
    // UI Components
    private JPanel mainPanel;
    private JPanel startPanel;
    private JPanel gamePanel;
    private BoardPanel boardPanel;
    private JTextField nameField;
    private JTextField serverField;
    private JTextField portField;
    private JButton joinButton;
    private JLabel statusLabel;
    private JLabel p1NameLabel;
    private JLabel p2NameLabel;
    private JLabel p1Highlight;
    private JLabel p2Highlight;
    
    // Images
    private Image boardBg;
    private Image blackStone;
    private Image whiteStone;

    public GoMoKuClient() {
        setTitle("GoMoKu - Connect-5");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        
        // Load images
        loadImages();
        
        // Build UI
        buildStartPanel();
        buildGamePanel();
        
        // Show start panel
        mainPanel = new JPanel(new CardLayout());
        mainPanel.add(startPanel, "start");
        mainPanel.add(gamePanel, "game");
        add(mainPanel);
        
        pack();
        setLocationRelativeTo(null);
    }

    private void loadImages() {
        try {
            boardBg = new ImageIcon("background.gif").getImage();
            blackStone = new ImageIcon("blackStone.gif").getImage();
            whiteStone = new ImageIcon("whiteStone.gif").getImage();
        } catch (Exception e) {
            System.err.println("Could not load images: " + e.getMessage());
        }
    }

    private void buildStartPanel() {
        startPanel = new JPanel(new GridBagLayout());
        startPanel.setBackground(new Color(0x1a, 0x1f, 0x2e));
        startPanel.setPreferredSize(new Dimension(400, 400));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 20, 8, 20);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        
        // Title
        JLabel titleLabel = new JLabel("GoMoKu");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 36));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 0;
        gbc.insets = new Insets(30, 20, 20, 20);
        startPanel.add(titleLabel, gbc);
        
        // Subtitle
        JLabel subLabel = new JLabel("Connect-5 Game");
        subLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subLabel.setForeground(new Color(0x94, 0xa3, 0xb8));
        subLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 20, 25, 20);
        startPanel.add(subLabel, gbc);
        
        // Server section
        JLabel serverLabel = new JLabel("Server Address:");
        serverLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        serverLabel.setForeground(new Color(0x94, 0xa3, 0xb8));
        gbc.gridy = 2;
        gbc.insets = new Insets(5, 20, 2, 20);
        startPanel.add(serverLabel, gbc);
        
        serverField = new JTextField("localhost");
        serverField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        serverField.setBackground(new Color(0x0d, 0x11, 0x17));
        serverField.setForeground(Color.WHITE);
        serverField.setCaretColor(Color.WHITE);
        serverField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x33, 0x3a, 0x4d)),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        gbc.gridy = 3;
        gbc.insets = new Insets(2, 20, 5, 20);
        startPanel.add(serverField, gbc);
        
        // Port section
        JLabel portLabel = new JLabel("Port:");
        portLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        portLabel.setForeground(new Color(0x94, 0xa3, 0xb8));
        gbc.gridy = 4;
        gbc.insets = new Insets(5, 20, 2, 20);
        startPanel.add(portLabel, gbc);
        
        portField = new JTextField("8765");
        portField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        portField.setBackground(new Color(0x0d, 0x11, 0x17));
        portField.setForeground(Color.WHITE);
        portField.setCaretColor(Color.WHITE);
        portField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x33, 0x3a, 0x4d)),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        gbc.gridy = 5;
        gbc.insets = new Insets(2, 20, 5, 20);
        startPanel.add(portField, gbc);
        
        // Name section
        JLabel nameLabel = new JLabel("Your Name:");
        nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        nameLabel.setForeground(new Color(0x94, 0xa3, 0xb8));
        gbc.gridy = 6;
        gbc.insets = new Insets(5, 20, 2, 20);
        startPanel.add(nameLabel, gbc);
        
        nameField = new JTextField("Player");
        nameField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        nameField.setBackground(new Color(0x0d, 0x11, 0x17));
        nameField.setForeground(Color.WHITE);
        nameField.setCaretColor(Color.WHITE);
        nameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x33, 0x3a, 0x4d)),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        gbc.gridy = 7;
        gbc.insets = new Insets(2, 20, 15, 20);
        startPanel.add(nameField, gbc);
        
        // Join button
        joinButton = new JButton("Enter Arena");
        joinButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        joinButton.setBackground(new Color(0x63, 0x66, 0xf1));
        joinButton.setForeground(Color.WHITE);
        joinButton.setFocusPainted(false);
        joinButton.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        joinButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        joinButton.addActionListener(e -> startGame());
        
        // Hover effect
        joinButton.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                joinButton.setBackground(new Color(0x81, 0x8c, 0xf8));
            }
            public void mouseExited(MouseEvent e) {
                joinButton.setBackground(new Color(0x63, 0x66, 0xf1));
            }
        });
        
        gbc.gridy = 8;
        gbc.insets = new Insets(5, 20, 30, 20);
        startPanel.add(joinButton, gbc);
    }

    private void buildGamePanel() {
        gamePanel = new JPanel(new BorderLayout());
        gamePanel.setBackground(new Color(0x1a, 0x1f, 0x2e));
        gamePanel.setPreferredSize(new Dimension(500, 650));
        
        // Top bar with player info
        JPanel topBar = new JPanel(new GridLayout(3, 2, 5, 2));
        topBar.setBackground(new Color(0x22, 0x28, 0x3a));
        topBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x33, 0x3a, 0x4d)),
            BorderFactory.createEmptyBorder(8, 15, 8, 15)));
        
        JLabel p1Title = new JLabel("Player 1 (Black)");
        p1Title.setFont(new Font("SansSerif", Font.PLAIN, 11));
        p1Title.setForeground(new Color(0x94, 0xa3, 0xb8));
        
        JLabel p2Title = new JLabel("Player 2 (White)");
        p2Title.setFont(new Font("SansSerif", Font.PLAIN, 11));
        p2Title.setForeground(new Color(0x94, 0xa3, 0xb8));
        p2Title.setHorizontalAlignment(SwingConstants.RIGHT);
        
        p1NameLabel = new JLabel("Waiting...");
        p1NameLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        p1NameLabel.setForeground(Color.WHITE);
        
        p2NameLabel = new JLabel("Waiting...");
        p2NameLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        p2NameLabel.setForeground(Color.WHITE);
        p2NameLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        
        p1Highlight = new JLabel("");
        p2Highlight = new JLabel("");
        
        topBar.add(p1Title);
        topBar.add(p2Title);
        topBar.add(p1NameLabel);
        topBar.add(p2NameLabel);
        topBar.add(p1Highlight);
        topBar.add(p2Highlight);
        
        // Status label
        statusLabel = new JLabel("Connecting...");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        statusLabel.setForeground(new Color(0x38, 0xbd, 0xf8));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        
        // Board panel
        boardPanel = new BoardPanel();
        boardPanel.setPreferredSize(new Dimension(BOARD_PIXEL_SIZE + 20, BOARD_PIXEL_SIZE + 20));
        
        // Bottom bar with quit button
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomBar.setBackground(new Color(0x1a, 0x1f, 0x2e));
        
        JButton quitButton = new JButton("Quit Game");
        quitButton.setFont(new Font("SansSerif", Font.PLAIN, 12));
        quitButton.setBackground(new Color(0x33, 0x3a, 0x4d));
        quitButton.setForeground(new Color(0x94, 0xa3, 0xb8));
        quitButton.setFocusPainted(false);
        quitButton.addActionListener(e -> {
            sendQuit();
            dispose();
            System.exit(0);
        });
        bottomBar.add(quitButton);
        
        // Assemble game panel
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(new Color(0x1a, 0x1f, 0x2e));
        centerPanel.add(statusLabel, BorderLayout.NORTH);
        centerPanel.add(boardPanel, BorderLayout.CENTER);
        
        gamePanel.add(topBar, BorderLayout.NORTH);
        gamePanel.add(centerPanel, BorderLayout.CENTER);
        gamePanel.add(bottomBar, BorderLayout.SOUTH);
    }

    private void startGame() {
        String serverHost = serverField.getText().trim();
        int serverPort;
        
        try {
            serverPort = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid port number.");
            return;
        }
        
        playerName = nameField.getText().trim();
        if (playerName.isEmpty()) {
            playerName = "Player";
        }
        
        try {
            socket = new Socket(serverHost, serverPort);
            serverOut = new PrintWriter(socket.getOutputStream(), true);
            serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            serverOut.println("JOIN:" + playerName);
            
            // Switch to game view
            CardLayout cl = (CardLayout) mainPanel.getLayout();
            cl.show(mainPanel, "game");
            pack();
            setLocationRelativeTo(null);
            
            statusMsg = "Connected. Waiting for opponent...";
            statusLabel.setText(statusMsg);
            
            // Start listener thread
            new Thread(this::listenToServer).start();
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, 
                "Connection failed: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void listenToServer() {
        try {
            String line;
            while ((line = serverIn.readLine()) != null) {
                final String msg = line;
                SwingUtilities.invokeLater(() -> processServerMessage(msg));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                if (gameActive) {
                    statusMsg = "Connection lost!";
                    statusLabel.setText(statusMsg);
                    gameActive = false;
                }
            });
        }
    }

    private void processServerMessage(String message) {
        System.out.println("Received: " + message);
        
        if (message.startsWith("INIT:")) {
            String[] parts = message.split(":", 4);
            myColor = parts[1];
            playerName = parts[2];
            opponentName = parts.length > 3 ? parts[3] : "Waiting...";
            myTurn = myColor.equals("black");
            statusMsg = "Waiting for Player 2 to join...";
            updateUI();
            
        } else if (message.startsWith("START:")) {
            String[] parts = message.split(":", 4);
            myColor = parts[1];
            playerName = parts[2];
            opponentName = parts[3];
            gameActive = true;
            myTurn = myColor.equals("black");
            statusMsg = "Game Started! " + (myTurn ? "Your turn (Black)" : "Opponent's turn (White)");
            updateUI();
            
        } else if (message.startsWith("UPDATE:")) {
            String[] parts = message.substring(7).split(",");
            int row = Integer.parseInt(parts[0].trim());
            int col = Integer.parseInt(parts[1].trim());
            String color = parts[2].trim();
            
            int val = color.equals("black") ? 1 : 2;
            board[row][col] = val;
            
            myTurn = !myTurn;
            if (!gameOver) {
                statusMsg = myTurn ? "Your turn (" + (myColor.equals("black") ? "Black" : "White") + ")" 
                                   : "Opponent's turn...";
            }
            updateUI();
            
        } else if (message.startsWith("WIN:")) {
            String winnerName = message.substring(4);
            gameActive = false;
            gameOver = true;
            statusMsg = winnerName.equals(playerName) ? "You won! Congratulations!" : winnerName + " won!";
            updateUI();
            JOptionPane.showMessageDialog(this, statusMsg, "Game Over", JOptionPane.INFORMATION_MESSAGE);
            
        } else if (message.startsWith("ERROR:")) {
            statusMsg = message.substring(6);
            statusLabel.setText(statusMsg);
            
        } else if (message.startsWith("OPPONENT_DISCONNECTED:")) {
            String discName = message.substring(21);
            gameActive = false;
            statusMsg = discName + " disconnected. Game over.";
            statusLabel.setText(statusMsg);
            JOptionPane.showMessageDialog(this, statusMsg, "Disconnected", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void updateUI() {
        // Update player names
        if (myColor.equals("black")) {
            p1NameLabel.setForeground(new Color(0x81, 0x8c, 0xf8));
            p1NameLabel.setText(playerName + " (You)");
            p2NameLabel.setForeground(Color.WHITE);
            p2NameLabel.setText(opponentName);
        } else {
            p1NameLabel.setForeground(Color.WHITE);
            p1NameLabel.setText(opponentName);
            p2NameLabel.setForeground(new Color(0x81, 0x8c, 0xf8));
            p2NameLabel.setText(playerName + " (You)");
        }
        
        statusLabel.setText(statusMsg);
        boardPanel.repaint();
    }

    private void sendMove(int row, int col) {
        if (serverOut != null) {
            serverOut.println("MOVE:" + row + "," + col);
            myTurn = false;
            statusMsg = "Waiting for opponent's move...";
            statusLabel.setText(statusMsg);
        }
    }

    private void sendQuit() {
        if (serverOut != null) {
            serverOut.println("QUIT");
        }
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {}
    }

    // ===== BOARD PANEL =====
    
    private class BoardPanel extends JPanel implements MouseListener {
        
        BoardPanel() {
            setBackground(new Color(0x1a, 0x1f, 0x2e));
            addMouseListener(this);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int boardX = (getWidth() - BOARD_PIXEL_SIZE) / 2;
            int boardY = (getHeight() - BOARD_PIXEL_SIZE) / 2;
            
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
            
            // Grid lines
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
                            g2d.setColor(board[r][c] == 1 ? Color.BLACK : Color.WHITE);
                            g2d.fillOval(stoneX, stoneY, stoneSize, stoneSize);
                            g2d.setColor(Color.GRAY);
                            g2d.drawOval(stoneX, stoneY, stoneSize, stoneSize);
                        }
                    }
                }
            }
            
            // Game over overlay
            if (gameOver) {
                g2d.setColor(new Color(0, 0, 0, 150));
                g2d.fillRect(boardX, boardY + BOARD_PIXEL_SIZE / 2 - 25, BOARD_PIXEL_SIZE, 50);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("SansSerif", Font.BOLD, 18));
                String endMsg = "Game Over - " + statusMsg;
                FontMetrics fm = g2d.getFontMetrics();
                int msgX = boardX + (BOARD_PIXEL_SIZE - fm.stringWidth(endMsg)) / 2;
                g2d.drawString(endMsg, msgX, boardY + BOARD_PIXEL_SIZE / 2 + 8);
            }
        }
        
        @Override
        public void mouseClicked(MouseEvent e) {
            if (!gameActive || !myTurn || gameOver) return;
            
            int boardX = (getWidth() - BOARD_PIXEL_SIZE) / 2;
            int boardY = (getHeight() - BOARD_PIXEL_SIZE) / 2;
            
            int mx = e.getX();
            int my = e.getY();
            
            if (mx >= boardX && mx < boardX + BOARD_PIXEL_SIZE &&
                my >= boardY && my < boardY + BOARD_PIXEL_SIZE) {
                
                int col = (mx - boardX) / CELL_SIZE;
                int row = (my - boardY) / CELL_SIZE;
                
                if (row >= 0 && row < SIZE && col >= 0 && col < SIZE) {
                    if (board[row][col] == 0) {
                        sendMove(row, col);
                    } else {
                        statusMsg = "Spot already taken!";
                        statusLabel.setText(statusMsg);
                    }
                }
            }
        }
        
        @Override public void mousePressed(MouseEvent e) {}
        @Override public void mouseReleased(MouseEvent e) {}
        @Override public void mouseEntered(MouseEvent e) {}
        @Override public void mouseExited(MouseEvent e) {}
    }

    // ===== MAIN =====
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {}
            new GoMoKuClient().setVisible(true);
        });
    }
}
