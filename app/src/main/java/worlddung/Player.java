package worlddung;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;
import java.util.Map;

public class Player implements KeyListener, MouseListener {
    private int gridX, gridY;           // Player position in grid coordinates
    private final int GRID_SIZE;        // Size of each grid cell
    private final int CENTER_X, CENTER_Y; // World center coordinates
    private final int RADIUS;           // Dungeon radius
    private final CustomPanel dungeonPanel;
    private final int movementSpeed;      // Base speed from stats.json
    private int remainingMovement;        // How many tiles left this turn
    private boolean turnActive = false;
    private final Map<Point, List<CustomPanel.Wall>> wallsByChunk;
    
    // Player visual properties
    private final Color playerColor = Color.RED;
    private final int playerSize;       // Player sphere radius (fits in grid)

    public Player(CustomPanel panel, int gridSize, int centerX, int centerY, int radius,
                  Map<Point, List<CustomPanel.Wall>> walls) {
        this.dungeonPanel = panel;
        this.GRID_SIZE = gridSize;
        this.CENTER_X = centerX;
        this.CENTER_Y = centerY;
        this.RADIUS = radius;
         this.movementSpeed      = UI.stats.movementSpeed;
        // start the very first turn
        startTurn();
        this.wallsByChunk = walls;
        this.playerSize = GRID_SIZE / 3; // Player fits comfortably in grid cell
        
        // Start player at center of dungeon
        this.gridX = 0;
        this.gridY = 0;
        
        // Find a valid starting position if center is blocked
        findValidStartingPosition();
    }
     public void startTurn() {
        turnActive        = true;
        remainingMovement = movementSpeed;
        System.out.println("New turn! You have " + remainingMovement + " moves.");
    }

    /** Disables moving until next startTurn(). */
    public void endTurn() {
        turnActive = false;
        System.out.println("Turn ended.");
        startTurn();
    }

    /** Centralizes the “can I move?” + budget decrement logic. */
    private void handleMove(int dx, int dy) {
        if (!turnActive) {
            // you could also flash the screen or show a message
            return;
        }
        if (remainingMovement <= 0) {
            // out of moves
            endTurn();
            return;
        }
        // only if the move actually happened do we decrement
        if (movePlayer(dx, dy)) {
            remainingMovement--;
            System.out.println("Moved. " + remainingMovement + " left.");
            if (remainingMovement == 0) {
                endTurn();
            }
        }
    }
    // Find a valid starting position near the center
    private void findValidStartingPosition() {
        // Try positions in expanding circles around center
        for (int radius = 0; radius < 50; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.abs(dx) == radius || Math.abs(dy) == radius) {
                        int testX = dx;
                        int testY = dy;
                        if (isValidPosition(testX, testY)) {
                            gridX = testX;
                            gridY = testY;
                            return;
                        }
                    }
                }
            }
        }
    }
    
    // Convert grid coordinates to world coordinates
    private int gridToWorldX(int gx) {
        return CENTER_X + gx * GRID_SIZE + GRID_SIZE / 2;
    }
    
    private int gridToWorldY(int gy) {
        return CENTER_Y + gy * GRID_SIZE + GRID_SIZE / 2;
    }
    
    // Convert world coordinates to grid coordinates
    private int worldToGridX(int wx) {
        return (wx - CENTER_X) / GRID_SIZE;
    }
    
    private int worldToGridY(int wy) {
        return (wy - CENTER_Y) / GRID_SIZE;
    }
    
    // Check if a grid position is valid (inside dungeon and not blocked by walls)
    private boolean isValidPosition(int gx, int gy) {
        // Check if position is inside dungeon circle
        int worldX = gridToWorldX(gx);
        int worldY = gridToWorldY(gy);
        double dx = worldX - CENTER_X;
        double dy = worldY - CENTER_Y;
        if (dx * dx + dy * dy > (double) RADIUS * RADIUS) {
            return false;
        }
        
        // For positioning, we only need to check if the position itself is valid
        // (not blocked by being directly on a wall)
        return !isBlockedByWalls(gx, gy);
    }
    
    // Check if moving from current position to target position would cross any walls
    private boolean isMovementBlocked(int fromGx, int fromGy, int toGx, int toGy) {
        int fromWorldX = gridToWorldX(fromGx);
        int fromWorldY = gridToWorldY(fromGy);
        int toWorldX = gridToWorldX(toGx);
        int toWorldY = gridToWorldY(toGy);

        int chunkSize = 2048 * GRID_SIZE;
        
        // Get all chunks that might contain walls affecting this movement
        int minChunkX = Math.min(Math.floorDiv(fromWorldX - CENTER_X, chunkSize), 
                                Math.floorDiv(toWorldX - CENTER_X, chunkSize)) - 1;
        int maxChunkX = Math.max(Math.floorDiv(fromWorldX - CENTER_X, chunkSize), 
                                Math.floorDiv(toWorldX - CENTER_X, chunkSize)) + 1;
        int minChunkY = Math.min(Math.floorDiv(fromWorldY - CENTER_Y, chunkSize), 
                                Math.floorDiv(toWorldY - CENTER_Y, chunkSize)) - 1;
        int maxChunkY = Math.max(Math.floorDiv(fromWorldY - CENTER_Y, chunkSize), 
                                Math.floorDiv(toWorldY - CENTER_Y, chunkSize)) + 1;

        // Check if the movement path crosses any walls
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
                Point chunkPoint = new Point(chunkX, chunkY);
                List<CustomPanel.Wall> walls = wallsByChunk.get(chunkPoint);
                if (walls == null) continue;

                for (CustomPanel.Wall wall : walls) {
                    if (movementCrossesWall(fromWorldX, fromWorldY, toWorldX, toWorldY, wall)) {
                        return true; // Movement is blocked by this wall
                    }
                }
            }
        }

        return false; // Movement is not blocked
    }
    
    // Check if a grid position is blocked by walls (used for initial positioning)
    private boolean isBlockedByWalls(int gx, int gy) {
        // For positioning checks, we just need to make sure the player's center
        // isn't directly on a wall (which shouldn't happen in a proper maze)
        int worldX = gridToWorldX(gx);
        int worldY = gridToWorldY(gy);

        int chunkSize = 2048 * GRID_SIZE; 
        int chunkX = Math.floorDiv(worldX - CENTER_X, chunkSize);
        int chunkY = Math.floorDiv(worldY - CENTER_Y, chunkSize);

        // Check current chunk and neighboring chunks
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Point neighborChunk = new Point(chunkX + dx, chunkY + dy);
                List<CustomPanel.Wall> walls = wallsByChunk.get(neighborChunk);
                if (walls == null) continue;

                for (CustomPanel.Wall wall : walls) {
                    // Check if player center is very close to a wall (shouldn't happen in grid-based maze)
                    if (distanceFromPointToWall(worldX, worldY, wall) < 2) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // Check if movement from one point to another crosses a wall
    private boolean movementCrossesWall(int fromX, int fromY, int toX, int toY, CustomPanel.Wall wall) {
        return linesIntersect(fromX, fromY, toX, toY, wall.x1, wall.y1, wall.x2, wall.y2);
    }
    
    // Check if two line segments intersect
    private boolean linesIntersect(int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4) {
        // Calculate the direction of the lines
        double denom = (double)(x1 - x2) * (y3 - y4) - (double)(y1 - y2) * (x3 - x4);
        
        // Lines are parallel if denominator is 0
        if (Math.abs(denom) < 1e-10) {
            return false;
        }
        
        // Calculate intersection parameters
        double t = ((double)(x1 - x3) * (y3 - y4) - (double)(y1 - y3) * (x3 - x4)) / denom;
        double u = -((double)(x1 - x2) * (y1 - y3) - (double)(y1 - y2) * (x1 - x3)) / denom;
        
        // Check if intersection point lies on both line segments
        return (t >= 0 && t <= 1 && u >= 0 && u <= 1);
    }
    
    // Calculate distance from a point to a wall (line segment)
    private double distanceFromPointToWall(int px, int py, CustomPanel.Wall wall) {
        int x1 = wall.x1, y1 = wall.y1, x2 = wall.x2, y2 = wall.y2;
        
        // Calculate the squared length of the wall
        double wallLengthSq = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
        
        if (wallLengthSq == 0) {
            // Wall is a point
            return Math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1));
        }
        
        // Calculate projection parameter
        double t = Math.max(0, Math.min(1, ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / wallLengthSq));
        
        // Calculate closest point on wall to the player
        double closestX = x1 + t * (x2 - x1);
        double closestY = y1 + t * (y2 - y1);
        
        // Return distance to closest point
        return Math.sqrt((px - closestX) * (px - closestX) + (py - closestY) * (py - closestY));
    }

    // Move player by grid units
    public boolean movePlayer(int deltaX, int deltaY) {
        
        int newX = gridX + deltaX;
        int newY = gridY + deltaY;
        
        // First check if target position is inside dungeon
        if (!isValidPosition(newX, newY)) {
            dungeonPanel.onPlayerMoved();
            return false;
        }
        
        // Then check if movement would cross any walls
        if (isMovementBlocked(gridX, gridY, newX, newY)) {
            dungeonPanel.onPlayerMoved(); // Player stays in current position
            return false;
        }
        
        // Movement is allowed
        gridX = newX;
        gridY = newY;
        dungeonPanel.onPlayerMoved();
        return true;
    }
    
    // Move player to specific grid position
    public boolean moveToPosition(int gx, int gy) {
        // First check if target position is inside dungeon
        if (!isValidPosition(gx, gy)) {
            dungeonPanel.onPlayerMoved();
            return false;
        }
        
        // Then check if movement would cross any walls
        if (isMovementBlocked(gridX, gridY, gx, gy)) {
            dungeonPanel.onPlayerMoved();// Player stays in current position
            return false;
        }
        
        // if all checks pass, move player
        gridX = gx;
        gridY = gy;
        dungeonPanel.onPlayerMoved();
        return true;
    }
    
    // Draw the player
    public void draw(Graphics2D g2d) {
        int worldX = gridToWorldX(gridX);
        int worldY = gridToWorldY(gridY);
        
        g2d.setColor(playerColor);
        g2d.fillOval(worldX - playerSize, worldY - playerSize, 
                    playerSize * 2, playerSize * 2);
        
        // Draw a small border for visibility
        g2d.setColor(Color.BLACK);
        g2d.drawOval(worldX - playerSize, worldY - playerSize, 
                    playerSize * 2, playerSize * 2);
    }
    
    // Get current position
    public Point getGridPosition() {
        return new Point(gridX, gridY);
    }
    
    public Point getWorldPosition() {
        return new Point(gridToWorldX(gridX), gridToWorldY(gridY));
    }
    
    // KeyListener implementation
    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_W:
                handleMove(0, -1);
                break;
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_S:
                handleMove(0, 1);
                break;
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_A:
                handleMove(-1, 0);
                break;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_D:
                handleMove(1, 0);
                break;
            case KeyEvent.VK_E:  // “E” to end turn early
                endTurn();
                break;
            default:
                break;
        }
    }
    
    @Override
    public void keyReleased(KeyEvent e) {
        // Not needed for this implementation
    }
    
    @Override
    public void keyTyped(KeyEvent e) {
        // Not needed for this implementation
    }
    
    // MouseListener implementation for clicking to move
    @Override
    public void mousePressed(MouseEvent e) {
        // Convert mouse coordinates to world coordinates
        double scale = dungeonPanel.getScale();
        int translateX = dungeonPanel.getTranslateX();
        int translateY = dungeonPanel.getTranslateY();
        
        int worldX = (int)((e.getX() - translateX) / scale);
        int worldY = (int)((e.getY() - translateY) / scale);
        
        // Convert to grid coordinates
        int targetGridX = worldToGridX(worldX);
        int targetGridY = worldToGridY(worldY);
        
        // Try to move to clicked position
        moveToPosition(targetGridX, targetGridY);
    }
    
    @Override
    public void mouseReleased(MouseEvent e) {
        // Not needed for this implementation
    }
    
    @Override
    public void mouseClicked(MouseEvent e) {
        // Handled in mousePressed
    }
    
    @Override
    public void mouseEntered(MouseEvent e) {
        // Not needed for this implementation
    }
    
    @Override
    public void mouseExited(MouseEvent e) {
        // Not needed for this implementation
    }
}