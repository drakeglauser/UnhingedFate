package worlddung;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JFrame;
import javax.swing.JPanel;

public class MinimapWindow extends JFrame {
    private MinimapPanel minimapPanel;
    private Player player;
    private MinimapTracker tracker;
    
    public MinimapWindow(Player player, MinimapTracker tracker, 
                        Map<Point, List<CustomPanel.Wall>> wallsByChunk,
                        int dungeonCenterX, int dungeonCenterY, int dungeonRadius) {
        super("Minimap");
        this.player = player;
        this.tracker = tracker;
        
        minimapPanel = new MinimapPanel(player, tracker, wallsByChunk, 
                                       dungeonCenterX, dungeonCenterY, dungeonRadius);
        
        add(minimapPanel);
        setSize(400, 400);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setResizable(true);
        
        // Center on screen
        setLocationRelativeTo(null);
    }
    
    public void updateMinimap() {
        if (minimapPanel != null) {
            minimapPanel.repaint();
        }
    }
    
    private static class MinimapPanel extends JPanel implements MouseWheelListener {
        private Player player;
        private MinimapTracker tracker;
        private Map<Point, List<CustomPanel.Wall>> wallsByChunk;
        private int dungeonCenterX, dungeonCenterY, dungeonRadius;
        
        private double scale = 0.1; // Start zoomed out to see more of the map
        private int translateX = 0;
        private int translateY = 0;
        private int lastMouseX, lastMouseY;
        
        private static final int GRID_SIZE = 4;
        private static final int CHUNK_SIZE_CELLS = 2048;
        
        // Cached objects to reduce GC pressure
        private Rectangle visibleRect = new Rectangle();
        private Rectangle2D.Double tempRect = new Rectangle2D.Double();
        
        // Cached strokes and colors
        private final Color EXPLORED_COLOR = new Color(60, 60, 60);
        private final Color WALL_COLOR = Color.LIGHT_GRAY;
        private final Color PLAYER_COLOR = Color.RED;
        private final Color PLAYER_OUTLINE_COLOR = Color.BLACK;
        private final Color VIEW_RADIUS_FILL = new Color(255, 255, 0, 50);
        private final Color VIEW_RADIUS_OUTLINE = new Color(255, 255, 0, 150);
        
        // Cached stroke objects
        private BasicStroke wallStroke;
        private BasicStroke playerStroke;
        private BasicStroke viewRadiusStroke;
        private BasicStroke boundaryStroke;
        
        // Performance tracking
        private long lastFrameTime = 0;
        private static final long MIN_FRAME_INTERVAL = 16; // ~60 FPS cap
        
        public MinimapPanel(Player player, MinimapTracker tracker,
                           Map<Point, List<CustomPanel.Wall>> wallsByChunk,
                           int dungeonCenterX, int dungeonCenterY, int dungeonRadius) {
            this.player = player;
            this.tracker = tracker;
            this.wallsByChunk = wallsByChunk;
            this.dungeonCenterX = dungeonCenterX;
            this.dungeonCenterY = dungeonCenterY;
            this.dungeonRadius = dungeonRadius;
            
            setBackground(Color.DARK_GRAY);
            addMouseWheelListener(this);
            
            // Initialize cached strokes
            updateCachedStrokes();
            
            // Add mouse handling for panning
            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                }
                
                @Override
                public void mouseDragged(MouseEvent e) {
                    int dx = e.getX() - lastMouseX;
                    int dy = e.getY() - lastMouseY;
                    translateX += dx;
                    translateY += dy;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    requestRepaint();
                }
            };
            
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
            
            // Initialize camera to center on player
            centerOnPlayer();
        }
        
        private void updateCachedStrokes() {
            float strokeWidth = (float)(2.0 / scale);
            wallStroke = new BasicStroke(strokeWidth);
            playerStroke = new BasicStroke(strokeWidth);
            viewRadiusStroke = new BasicStroke((float)(1.0 / scale));
            boundaryStroke = new BasicStroke((float)(20.0 / scale));
        }
        
        private void requestRepaint() {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastFrameTime >= MIN_FRAME_INTERVAL) {
                lastFrameTime = currentTime;
                repaint();
            }
        }
        
        private void centerOnPlayer() {
            if (player != null) {
                Point playerWorld = player.getWorldPosition();
                translateX = getWidth() / 2 - (int)(playerWorld.x * scale);
                translateY = getHeight() / 2 - (int)(playerWorld.y * scale);
            }
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            
            // Enable anti-aliasing for smoother rendering
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            
            // Update cached strokes if scale changed
            updateCachedStrokes();
            
            g2d.translate(translateX, translateY);
            g2d.scale(scale, scale);
            
            // Calculate visible world bounds once
            updateVisibleRect();
            
            // Draw dungeon boundary circle
            g2d.setColor(Color.WHITE);
            g2d.setStroke(boundaryStroke);
            g2d.drawOval(dungeonCenterX - dungeonRadius, dungeonCenterY - dungeonRadius, 
                        dungeonRadius * 2, dungeonRadius * 2);
            
            // Draw explored areas as a darker background
            drawExploredAreas(g2d);
            
            // Draw walls in explored areas
            drawExploredWalls(g2d);
            
            // Draw player position
            drawPlayer(g2d);
            
            // Draw player's current view radius
            drawViewRadius(g2d);
        }
        
        private void updateVisibleRect() {
            int panelW = getWidth();
            int panelH = getHeight();
            
            double worldMinX = (-translateX) / scale;
            double worldMaxX = (panelW - translateX) / scale;
            double worldMinY = (-translateY) / scale;
            double worldMaxY = (panelH - translateY) / scale;
            
            visibleRect.setRect(worldMinX, worldMinY, 
                              worldMaxX - worldMinX, worldMaxY - worldMinY);
        }
        
        private void drawExploredAreas(Graphics2D g2d) {
            g2d.setColor(EXPLORED_COLOR);
            
            // Get visited cells once and iterate through them
            Set<Point> visitedCells = tracker.getVisitedCells();
            
            // Only draw cells that are visible
            int minX = (int)(visibleRect.getMinX() - GRID_SIZE);
            int maxX = (int)(visibleRect.getMaxX() + GRID_SIZE);
            int minY = (int)(visibleRect.getMinY() - GRID_SIZE);
            int maxY = (int)(visibleRect.getMaxY() + GRID_SIZE);
            
            for (Point cell : visitedCells) {
                int worldX = cell.x * GRID_SIZE;
                int worldY = cell.y * GRID_SIZE;
                
                // Skip cells outside visible area
                if (worldX < minX || worldX > maxX || worldY < minY || worldY > maxY) {
                    continue;
                }
                
                // Use cached rectangle to reduce object creation
                tempRect.setRect(worldX - GRID_SIZE/2.0, worldY - GRID_SIZE/2.0, GRID_SIZE, GRID_SIZE);
                g2d.fill(tempRect);
            }
        }
        
        private void drawExploredWalls(Graphics2D g2d) {
            g2d.setColor(WALL_COLOR);
            g2d.setStroke(wallStroke);
            
            // Calculate visible chunks based on current view
            int chunkWorldSize = CHUNK_SIZE_CELLS * GRID_SIZE;
            
            int minCX = floorDiv((int)(visibleRect.getMinX() - dungeonCenterX), chunkWorldSize) - 1;
            int maxCX = floorDiv((int)(visibleRect.getMaxX() - dungeonCenterX), chunkWorldSize) + 1;
            int minCY = floorDiv((int)(visibleRect.getMinY() - dungeonCenterY), chunkWorldSize) - 1;
            int maxCY = floorDiv((int)(visibleRect.getMaxY() - dungeonCenterY), chunkWorldSize) + 1;
            
            // Reuse Point object to reduce GC pressure
            Point chunkPoint = new Point();
            
            // Only draw walls in areas the player has visited
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cy = minCY; cy <= maxCY; cy++) {
                    chunkPoint.setLocation(cx, cy);
                    List<CustomPanel.Wall> chunkWalls = wallsByChunk.get(chunkPoint);
                    if (chunkWalls == null) continue;
                    
                    for (CustomPanel.Wall wall : chunkWalls) {
                        // Quick bounds check before expensive visited check
                        if (!wallIntersectsVisibleArea(wall)) {
                            continue;
                        }
                        
                        // Only draw wall if the area around it has been visited
                        int wallCenterX = (wall.x1 + wall.x2) / 2;
                        int wallCenterY = (wall.y1 + wall.y2) / 2;
                        
                        if (tracker.isVisited(wallCenterX, wallCenterY)) {
                            g2d.drawLine(wall.x1, wall.y1, wall.x2, wall.y2);
                        }
                    }
                }
            }
        }
        
        private boolean wallIntersectsVisibleArea(CustomPanel.Wall wall) {
            // Quick bounds check - expand visible area slightly for line thickness
            double buffer = 10.0 / scale;
            return !(Math.max(wall.x1, wall.x2) < visibleRect.getMinX() - buffer ||
                     Math.min(wall.x1, wall.x2) > visibleRect.getMaxX() + buffer ||
                     Math.max(wall.y1, wall.y2) < visibleRect.getMinY() - buffer ||
                     Math.min(wall.y1, wall.y2) > visibleRect.getMaxY() + buffer);
        }
        
        private void drawPlayer(Graphics2D g2d) {
            if (player == null) return;
            
            Point playerWorld = player.getWorldPosition();
            int playerSize = Math.max(4, (int)(20 / scale)); // Minimum size for visibility
            
            // Draw player as a red circle
            g2d.setColor(PLAYER_COLOR);
            g2d.fillOval(playerWorld.x - playerSize/2, playerWorld.y - playerSize/2, 
                        playerSize, playerSize);
            
            // Draw black outline
            g2d.setColor(PLAYER_OUTLINE_COLOR);
            g2d.setStroke(playerStroke);
            g2d.drawOval(playerWorld.x - playerSize/2, playerWorld.y - playerSize/2, 
                        playerSize, playerSize);
        }
        
        private void drawViewRadius(Graphics2D g2d) {
            if (player == null || tracker == null) return;
            
            Point playerWorld = player.getWorldPosition();
            int viewRadius = tracker.getViewRadiusUnits();
            
            // Only draw if view radius is visible and reasonably sized
            if (viewRadius * scale < 2) return; // Too small to see
            
            // Draw view radius as a semi-transparent circle
            g2d.setColor(VIEW_RADIUS_FILL);
            g2d.fillOval(playerWorld.x - viewRadius, playerWorld.y - viewRadius,
                        viewRadius * 2, viewRadius * 2);
            
            // Draw view radius outline
            g2d.setColor(VIEW_RADIUS_OUTLINE);
            g2d.setStroke(viewRadiusStroke);
            g2d.drawOval(playerWorld.x - viewRadius, playerWorld.y - viewRadius,
                        viewRadius * 2, viewRadius * 2);
        }
        
        private static int floorDiv(int x, int y) {
            int r = x / y;
            if ((x ^ y) < 0 && (r * y != x)) r--;
            return r;
        }
        
        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            double oldScale = scale;
            
            // Zoom in/out by 20% per notch
            if (e.getWheelRotation() < 0) {
                scale *= 1.2;
            } else {
                scale /= 1.2;
            }
            
            // Limit zoom levels
            scale = Math.max(0.01, Math.min(scale, 2.0));
            
            // Zoom towards mouse position
            double scaleChange = scale / oldScale;
            translateX = (int)(e.getX() - scaleChange * (e.getX() - translateX));
            translateY = (int)(e.getY() - scaleChange * (e.getY() - translateY));
            
            requestRepaint();
        }
    }
}