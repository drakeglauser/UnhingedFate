package worlddung;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

public class GameModeManager {
    public enum GameMode {
        GAME_MASTER,  // Free view of the entire maze
        PLAYER        // View locked to player
    }
    
    private JFrame menuFrame;
    private GameMode selectedMode;
    private final Consumer<GameMode> onModeSelected;

    public GameModeManager(Consumer<GameMode> callback) {
    this.onModeSelected = callback;
    createMenuFrame();
    }         
    
    private void createMenuFrame() {
        menuFrame = new JFrame("World Dungeon - Select Mode");
        menuFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        menuFrame.setSize(500, 350);
        menuFrame.setLocationRelativeTo(null);
        menuFrame.setResizable(false);
        
        // Main panel with padding
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        mainPanel.setBackground(new Color(45, 45, 45));
        
        // Title
        JLabel titleLabel = new JLabel("World Dungeon", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        
        // Subtitle
        JLabel subtitleLabel = new JLabel("Choose your viewing mode", SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        subtitleLabel.setForeground(Color.LIGHT_GRAY);
        subtitleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 30, 0));
        
        // Title panel
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(new Color(45, 45, 45));
        titlePanel.add(titleLabel, BorderLayout.NORTH);
        titlePanel.add(subtitleLabel, BorderLayout.SOUTH);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 0, 15));
        buttonPanel.setBackground(new Color(45, 45, 45));
        
        // Game Master button
        JButton gmButton = createModeButton(
            "Game Master Mode",
            "Free view of the entire maze",
            new Color(70, 130, 180),
            new Color(100, 149, 237)
        );
        gmButton.addActionListener(e -> selectMode(GameMode.GAME_MASTER));
        
        // Player button
        JButton playerButton = createModeButton(
            "Player Mode", 
            "View locked to player",
            new Color(180, 70, 70),
            new Color(205, 92, 92)
        );
        playerButton.addActionListener(e -> selectMode(GameMode.PLAYER));
        
        buttonPanel.add(gmButton);
        buttonPanel.add(playerButton);
        
        // Assembly
        mainPanel.add(titlePanel, BorderLayout.NORTH);
        mainPanel.add(buttonPanel, BorderLayout.CENTER);
        
        menuFrame.add(mainPanel);
    }
    
    private JButton createModeButton(String title, String description, Color normalColor, Color hoverColor) {
        JButton button = new JButton();
        button.setLayout(new BorderLayout());
        button.setPreferredSize(new Dimension(400, 80));
        button.setBackground(normalColor);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createRaisedBevelBorder(),
            BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));
        button.setFocusPainted(false);
        
        // Title label
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setHorizontalAlignment(SwingConstants.LEFT);
        
        // Description label
        JLabel descLabel = new JLabel(description);
        descLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        descLabel.setForeground(Color.LIGHT_GRAY);
        descLabel.setHorizontalAlignment(SwingConstants.LEFT);
        
        // Button content panel
        JPanel buttonContent = new JPanel(new BorderLayout());
        buttonContent.setOpaque(false);
        buttonContent.add(titleLabel, BorderLayout.NORTH);
        buttonContent.add(descLabel, BorderLayout.SOUTH);
        
        button.add(buttonContent, BorderLayout.CENTER);
        
        // Hover effects
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(hoverColor);
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(normalColor);
            }
        });
        
        return button;
    }
    
    private void selectMode(GameMode mode) {
        selectedMode = mode;
        menuFrame.setVisible(false);
        menuFrame.dispose();
        if (onModeSelected != null) {
            onModeSelected.accept(selectedMode);
        }
    }
    
    public void showMenu() {
        menuFrame.setVisible(true);
    }
    
    public GameMode getSelectedMode() {
        return selectedMode;
    }
    
        public static void main(String[] args) {
        GameModeManager manager = new GameModeManager(selectedMode -> {
            System.out.println("Selected mode: " + selectedMode);
            System.exit(0);
        });
        manager.showMenu();
    }
}