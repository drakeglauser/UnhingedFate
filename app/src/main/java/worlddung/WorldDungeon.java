package worlddung;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class WorldDungeon {
    public static void main(String[] args) {
        GameModeManager manager = new GameModeManager(selectedMode -> {
            JFrame frame = new JFrame("World Dungeon - " + (selectedMode == GameModeManager.GameMode.PLAYER ? "Player Mode" : "Game Master Mode"));
            frame.setSize(1600, 1200);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            CustomPanel panel = new CustomPanel(selectedMode);
            panel.setFocusable(true);
            
            JButton uiButton = new JButton("Open UI");
            uiButton.setFocusable(false);
            uiButton.addActionListener(e -> {
                UI uiPopup = new UI(frame);
                uiPopup.setVisible(true);
                panel.requestFocusInWindow();
            });
            
            JButton minimapButton = new JButton("Minimap");
            minimapButton.setFocusable(false);
            minimapButton.addActionListener(e -> {
                panel.toggleMinimap();
                panel.requestFocusInWindow();
            });
            
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            buttonPanel.setOpaque(false);
            buttonPanel.add(uiButton);
            buttonPanel.add(minimapButton);
            
            frame.add(buttonPanel, BorderLayout.NORTH);
            frame.add(panel, BorderLayout.CENTER);
            frame.setVisible(true);
            SwingUtilities.invokeLater(() -> panel.requestFocusInWindow());
        });
        manager.showMenu();
    }
}

class CustomPanel extends JPanel implements MouseWheelListener {
    private Player player;
    private MinimapTracker minimapTracker;
    private MinimapWindow minimapWindow;
    private double scale = 1.0;
    private int translateX = 0;
    private int translateY = 0;
    private GameModeManager.GameMode gameMode;
    
    // Player mode camera settings
    private static final double PLAYER_ZOOM_LEVEL = 15.0;
    private static final int PLAYER_VIEW_RADIUS = 2000;
    private static final int MINIMAP_VIEW_RADIUS = 1000; // How far the minimap tracks
    
    public double getScale() { return scale; }
    public int getTranslateX() { return translateX; }
    public int getTranslateY() { return translateY; }
    
    private static final int[] DX = { 0, 1, 0, -1 };
    private static final int[] DY = { -1, 0, 1, 0 };
    private static int opposite(int dir) { return (dir + 2) % 4; }
    
    private int lastMouseX, lastMouseY;
    
    // Grid cell size
    private final int GRID_SIZE = 4;
    private static final int CHUNK_SIZE_CELLS = 2048;
    private final long globalSeed = 123456789L;
    
    // Use ConcurrentHashMap for thread-safe operations during maze generation
    private final Map<Point, List<Wall>> wallsByChunk = new ConcurrentHashMap<>();
    
    // Dungeon circle parameters
    private final int DIAMETER = 10000;
    private final int CENTER_X = 0;
    private final int CENTER_Y = 0;
    private final int RADIUS = DIAMETER / 2;
    
    private final Map<Point, Integer> zoneScale = new HashMap<>();
    private final Random zoneRng = new Random(globalSeed ^ 0xDEADBEEF);
    
    // Cached values for performance
    private Rectangle lastVisibleRect = new Rectangle();
    private Path2D.Double cachedVisionPolygon = null;
    private Point lastPlayerPosition = new Point();
    private boolean visionCacheValid = false;
    
    // Optimized stroke objects
    private static final BasicStroke THICK_STROKE = new BasicStroke(27.0f);
    private static final BasicStroke THIN_STROKE = new BasicStroke(1.0f);
    private static final BasicStroke GRID_STROKE = new BasicStroke(0.5f);
    
    // Pre-calculated constants
    private final int CHUNK_WORLD_SIZE = CHUNK_SIZE_CELLS * GRID_SIZE;
    private final int ZONE_BOUND = (RADIUS/GRID_SIZE)/CHUNK_SIZE_CELLS + 2;
    
    public CustomPanel(GameModeManager.GameMode mode) {
        this.gameMode = mode;
        
        // Initialize minimap tracker
        minimapTracker = new MinimapTracker(GRID_SIZE, MINIMAP_VIEW_RADIUS);
        
        MouseAdapter panAdapter = new MouseAdapter() {
            private boolean isDragging = false;
            
            @Override
            public void mousePressed(MouseEvent e) {
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                isDragging = false;
            }
            
            @Override
            public void mouseDragged(MouseEvent e) {
                if (gameMode == GameModeManager.GameMode.GAME_MASTER) {
                    isDragging = true;
                    int dx = e.getX() - lastMouseX;
                    int dy = e.getY() - lastMouseY;
                    translateX += dx;
                    translateY += dy;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    repaint();
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (!isDragging && player != null) {
                    if (gameMode == GameModeManager.GameMode.GAME_MASTER) {
                        player.mousePressed(e);
                    }
                }
            }
        };
        
        addMouseListener(panAdapter);
        addMouseMotionListener(panAdapter);
        initializeZoneScales();
        
        // Generate maze in background thread
        new Thread(this::generateMazeByZones).start();
        
        // Create player
        player = new Player(this, GRID_SIZE, CENTER_X, CENTER_Y, RADIUS, wallsByChunk);
        
        // Set up input listeners
        addKeyListener(player);
        if (gameMode == GameModeManager.GameMode.GAME_MASTER) {
            addMouseListener(player);
        }
        
        setFocusable(true);
        requestFocusInWindow();
        addMouseWheelListener(this);
        
        if (gameMode == GameModeManager.GameMode.PLAYER) {
            SwingUtilities.invokeLater(this::initializePlayerCamera);
        }
        
        // Initialize camera for player mode
        if (gameMode == GameModeManager.GameMode.PLAYER) {
            initializePlayerCamera();
        }
        
        // Update minimap with initial player position
        updateMinimapTracking();
    }
    
    public void toggleMinimap() {
        if (minimapWindow == null) {
            minimapWindow = new MinimapWindow(player, minimapTracker, wallsByChunk, 
                                             CENTER_X, CENTER_Y, RADIUS);
        }
        
        if (minimapWindow.isVisible()) {
            minimapWindow.setVisible(false);
        } else {
            minimapWindow.setVisible(true);
            minimapWindow.toFront();
        }
    }
    
    private void updateMinimapTracking() {
        if (player != null && minimapTracker != null) {
            Point playerWorld = player.getWorldPosition();
            minimapTracker.markVisited(playerWorld.x, playerWorld.y);
            
            // Update minimap window if it's open
            if (minimapWindow != null && minimapWindow.isVisible()) {
                minimapWindow.updateMinimap();
            }
        }
    }
    
    // Initialize camera settings for player mode
    private void initializePlayerCamera() {
        scale = PLAYER_ZOOM_LEVEL;
        centerCameraOnPlayer();
    }
    
    // Center the camera on the player (used in player mode)
    private void centerCameraOnPlayer() {
        if (player != null) {
            Point playerWorld = player.getWorldPosition();
            int frameWidth = getWidth();
            int frameHeight = getHeight();
            // Calculate translation to center player on screen
            translateX = frameWidth / 2 - (int)(playerWorld.x * scale);
            translateY = frameHeight / 2 - (int)(playerWorld.y * scale);
        }
    }
    
    // Override the player's move methods to update camera in player mode
    public void onPlayerMoved() {
        if (gameMode == GameModeManager.GameMode.PLAYER) {
            centerCameraOnPlayer();
            // Invalidate vision cache when player moves
            visionCacheValid = false;
        }
        updateMinimapTracking();
        repaint();
    }
    
    // Generate maze by processing each zone with its appropriate scale
    private void generateMazeByZones() {
        // Process each zone individually
        for(int czx = -ZONE_BOUND; czx <= ZONE_BOUND; czx++){
            for(int czy = -ZONE_BOUND; czy <= ZONE_BOUND; czy++){
                Point zonePoint = new Point(czx, czy);
                int zScale = zoneScale.getOrDefault(zonePoint, 1);
                generateZoneMaze(czx, czy, zScale);
            }
        }
        // Connect adjacent zones
        connectZones();
    }
    
    // Generate maze for a specific zone with the given scale
    private void generateZoneMaze(int czx, int czy, int zScale) {
        Random rnd = new Random(globalSeed ^ (czx * 31L + czy * 17L));
        // Calculate zone boundaries in world coordinates
        int zoneOriginX = CENTER_X + czx * CHUNK_WORLD_SIZE;
        int zoneOriginY = CENTER_Y + czy * CHUNK_WORLD_SIZE;
        // Calculate grid size for this zone
        int effectiveGridSize = GRID_SIZE * zScale;
        int zoneCellsX = CHUNK_WORLD_SIZE / effectiveGridSize;
        int zoneCellsY = CHUNK_WORLD_SIZE / effectiveGridSize;
        if (zoneCellsX <= 0 || zoneCellsY <= 0) return;
        
        // Create maze grid for this zone
        boolean[][] isRoom = new boolean[zoneCellsX][zoneCellsY];
        boolean[][] visited = new boolean[zoneCellsX][zoneCellsY];
        boolean[][][] cellWalls = new boolean[zoneCellsX][zoneCellsY][4];
        List<int[]> frontier = new ArrayList<>();
        
        // Initialize all walls as present
        for(int i = 0; i < zoneCellsX; i++) {
            for(int j = 0; j < zoneCellsY; j++) {
                Arrays.fill(cellWalls[i][j], true);
            }
        }
        
        // Generate rooms appropriate for this zone's scale
        int roomCount = Math.max(1, 16 / zScale);
        int minR = Math.max(1, 2 / zScale);
        int maxR = Math.max(minR + 1, 8 / zScale);
        
        outer:
        for(int k = 0; k < roomCount; k++){
            int rw = minR + rnd.nextInt(maxR - minR + 1);
            int rh = minR + rnd.nextInt(maxR - minR + 1);
            int rx = rnd.nextInt(Math.max(1, zoneCellsX - rw));
            int ry = rnd.nextInt(Math.max(1, zoneCellsY - rh));
            
            // Check for overlaps
            for(int i = rx; i < rx + rw && i < zoneCellsX; i++) {
                for(int j = ry; j < ry + rh && j < zoneCellsY; j++) {
                    if(isRoom[i][j]) continue outer;
                }
            }
            
            // Carve room
            for(int i = rx; i < rx + rw && i < zoneCellsX; i++){
                for(int j = ry; j < ry + rh && j < zoneCellsY; j++){
                    isRoom[i][j] = true;
                    visited[i][j] = true;
                    Arrays.fill(cellWalls[i][j], false);
                }
            }
        }
        
        // Seed frontier from room cells
        for(int i = 0; i < zoneCellsX; i++){
            for(int j = 0; j < zoneCellsY; j++){
                if(isRoom[i][j]) {
                    addFrontier(i, j, frontier, visited, zoneCellsX, zoneCellsY);
                }
            }
        }
        
        // If no rooms, start from corner
        if(frontier.isEmpty() && zoneCellsX > 0 && zoneCellsY > 0){
            visited[0][0] = true;
            addFrontier(0, 0, frontier, visited, zoneCellsX, zoneCellsY);
        }
        
        // Prim's algorithm to carve maze
        while(!frontier.isEmpty()){
            int idx = rnd.nextInt(frontier.size());
            int[] e = frontier.remove(idx);
            int cx0 = e[0], cy0 = e[1], dir = e[2];
            int nx = cx0 + DX[dir], ny = cy0 + DY[dir];
            if(nx < 0 || nx >= zoneCellsX || ny < 0 || ny >= zoneCellsY) continue;
            if(!visited[nx][ny]){
                cellWalls[cx0][cy0][dir] = false;
                cellWalls[nx][ny][opposite(dir)] = false;
                visited[nx][ny] = true;
                addFrontier(nx, ny, frontier, visited, zoneCellsX, zoneCellsY);
            }
        }
        
        // Convert maze to walls and add to chunk
        Point chunkPoint = new Point(czx, czy);
        List<Wall> zoneWalls = new ArrayList<>();
        
        for(int i = 0; i < zoneCellsX; i++){
            for(int j = 0; j < zoneCellsY; j++){
                int x0 = zoneOriginX + i * effectiveGridSize;
                int y0 = zoneOriginY + j * effectiveGridSize;
                int x1 = x0 + effectiveGridSize;
                int y1 = y0 + effectiveGridSize;
                
                // Only add walls that are inside the dungeon circle
                int mx = (x0 + x1) / 2;
                int my = (y0 + y1) / 2;
                if (!isInsideCircle(mx, my, CENTER_X, CENTER_Y, RADIUS)) continue;
                
                // Add walls for this cell
                if(cellWalls[i][j][0]) zoneWalls.add(new Wall(x0, y0, x1, y0)); // top
                if(cellWalls[i][j][1]) zoneWalls.add(new Wall(x1, y0, x1, y1)); // right
                if(cellWalls[i][j][2]) zoneWalls.add(new Wall(x0, y1, x1, y1)); // bottom
                if(cellWalls[i][j][3]) zoneWalls.add(new Wall(x0, y0, x0, y1)); // left
            }
        }
        
        wallsByChunk.put(chunkPoint, zoneWalls);
    }
    
    // Connect adjacent zones by creating openings at their boundaries
    private void connectZones() {
        Random rnd = new Random(globalSeed ^ 0xC0FFEE);
        for(int czx = -ZONE_BOUND; czx <= ZONE_BOUND; czx++){
            for(int czy = -ZONE_BOUND; czy <= ZONE_BOUND; czy++){
                Point currentZone = new Point(czx, czy);
                List<Wall> currentWalls = wallsByChunk.get(currentZone);
                if(currentWalls == null) continue;
                
                // Check right neighbor
                Point rightZone = new Point(czx + 1, czy);
                if(wallsByChunk.containsKey(rightZone)) {
                    createZoneConnection(currentZone, rightZone, true, rnd);
                }
                
                // Check bottom neighbor
                Point bottomZone = new Point(czx, czy + 1);
                if(wallsByChunk.containsKey(bottomZone)) {
                    createZoneConnection(currentZone, bottomZone, false, rnd);
                }
            }
        }
    }
    
    // Create connections between two adjacent zones
    private void createZoneConnection(Point zone1, Point zone2, boolean isHorizontal, Random rnd) {
        List<Wall> walls1 = wallsByChunk.get(zone1);
        List<Wall> walls2 = wallsByChunk.get(zone2);
        if(walls1 == null || walls2 == null) return;
        
        int scale1 = zoneScale.getOrDefault(zone1, 1);
        int scale2 = zoneScale.getOrDefault(zone2, 1);
        
        // Use the smaller scale for connection spacing to ensure alignment
        int connectionScale = Math.min(scale1, scale2);
        int connectionSize = GRID_SIZE * connectionScale;
        
        // Calculate boundary line between zones
        int zone1X = CENTER_X + zone1.x * CHUNK_WORLD_SIZE;
        int zone1Y = CENTER_Y + zone1.y * CHUNK_WORLD_SIZE;
        int zone2X = CENTER_X + zone2.x * CHUNK_WORLD_SIZE;
        int zone2Y = CENTER_Y + zone2.y * CHUNK_WORLD_SIZE;
        
        if(isHorizontal) {
            // Connecting left-right zones
            int boundaryX = Math.max(zone1X + CHUNK_WORLD_SIZE, zone2X);
            int startY = Math.max(zone1Y, zone2Y);
            int endY = Math.min(zone1Y + CHUNK_WORLD_SIZE, zone2Y + CHUNK_WORLD_SIZE);
            
            // Create multiple connection points along the boundary
            int numConnections = Math.max(1, (endY - startY) / (connectionSize * 4));
            for(int i = 0; i < numConnections; i++) {
                int connectionY = startY + (endY - startY) * i / numConnections;
                // Align to grid
                connectionY = (connectionY / connectionSize) * connectionSize;
                
                // Remove walls at this connection point
                removeWallsAt(walls1, boundaryX, connectionY, boundaryX, connectionY + connectionSize);
                removeWallsAt(walls2, boundaryX, connectionY, boundaryX, connectionY + connectionSize);
            }
        } else {
            // Connecting top-bottom zones
            int boundaryY = Math.max(zone1Y + CHUNK_WORLD_SIZE, zone2Y);
            int startX = Math.max(zone1X, zone2X);
            int endX = Math.min(zone1X + CHUNK_WORLD_SIZE, zone2X + CHUNK_WORLD_SIZE);
            
            // Create multiple connection points along the boundary
            int numConnections = Math.max(1, (endX - startX) / (connectionSize * 4));
            for(int i = 0; i < numConnections; i++) {
                int connectionX = startX + (endX - startX) * i / numConnections;
                // Align to grid
                connectionX = (connectionX / connectionSize) * connectionSize;
                
                // Remove walls at this connection point
                removeWallsAt(walls1, connectionX, boundaryY, connectionX + connectionSize, boundaryY);
                removeWallsAt(walls2, connectionX, boundaryY, connectionX + connectionSize, boundaryY);
            }
        }
    }
    
    // Remove walls that overlap with the specified line segment
    private void removeWallsAt(List<Wall> walls, int x1, int y1, int x2, int y2) {
        walls.removeIf(wall -> wallsOverlap(wall, x1, y1, x2, y2));
    }
    
    // Check if a wall overlaps with a line segment
    private boolean wallsOverlap(Wall wall, int x1, int y1, int x2, int y2) {
        // Check if the wall and line segment are the same
        return (wall.x1 == x1 && wall.y1 == y1 && wall.x2 == x2 && wall.y2 == y2) ||
               (wall.x1 == x2 && wall.y1 == y2 && wall.x2 == x1 && wall.y2 == y1) ||
               // Check if they overlap partially
               wallsIntersect(wall.x1, wall.y1, wall.x2, wall.y2, x1, y1, x2, y2);
    }
    
    // Check if two line segments intersect or overlap
    private boolean wallsIntersect(int ax1, int ay1, int ax2, int ay2, int bx1, int by1, int bx2, int by2) {
        // For simplicity, check if they're collinear and overlapping
        if(ax1 == ax2 && bx1 == bx2 && ax1 == bx1) {
            // Both vertical, same x coordinate
            int aMinY = Math.min(ay1, ay2), aMaxY = Math.max(ay1, ay2);
            int bMinY = Math.min(by1, by2), bMaxY = Math.max(by1, by2);
            return !(aMaxY < bMinY || bMaxY < aMinY);
        } else if(ay1 == ay2 && by1 == by2 && ay1 == by1) {
            // Both horizontal, same y coordinate
            int aMinX = Math.min(ax1, ax2), aMaxX = Math.max(ax1, ax2);
            int bMinX = Math.min(bx1, bx2), bMaxX = Math.max(bx1, bx2);
            return !(aMaxX < bMinX || bMaxX < aMinX);
        }
        return false;
    }
    
    private void addFrontier(int i, int j, List<int[]> front, boolean[][] vis, int maxX, int maxY) {
        for (int d = 0; d < 4; d++) {
            int ni = i + DX[d], nj = j + DY[d];
            if (ni >= 0 && ni < maxX && nj >= 0 && nj < maxY && !vis[ni][nj]) {
                front.add(new int[]{i, j, d});
            }
        }
    }
    
    // Check if a point (x,y) is within a circle.
    private boolean isInsideCircle(int x, int y, int centerX, int centerY, int radius) {
        long dx = x - centerX;
        long dy = y - centerY;
        return dx*dx + dy*dy <= (long)radius * radius;
    }
    
    private static int floorDiv(int x, int y) {
        int r = x / y;
        if ((x ^ y) < 0 && (r * y != x)) r--;
        return r;
    }
    
    // Initialize zone scales with random values.
    private void initializeZoneScales() {
        for(int czx = -ZONE_BOUND; czx <= ZONE_BOUND; czx++){
            for(int czy = -ZONE_BOUND; czy <= ZONE_BOUND; czy++){
                double r = zoneRng.nextDouble();
                int zoneScaleValue;
                // Assigning the size based off of weighted random values.
                if (r < 0.1) {
                    zoneScaleValue = 1;
                } else if (r < 0.1) {
                    zoneScaleValue = 2;
                } else if (r < 0.25) {
                    zoneScaleValue = 4;
                } else if (r < 0.45) {
                    zoneScaleValue = 8;
                } else if (r < 0.90) {
                    zoneScaleValue = 16;
                } else {
                    zoneScaleValue = 32;
                }
                zoneScale.put(new Point(czx, czy), zoneScaleValue);
            }
        }
    }

    /**
     * Solve for intersection of ray (px,py)+t*(dx,dy) with segment w.
     * @return {ix,iy,t} or null if no intersection in front of ray
     */
    private double[] raySegmentIntersect(double px, double py, double dx, double dy, Wall w) {
        double x1 = w.x1, y1 = w.y1;
        double x2 = w.x2, y2 = w.y2;
        double sx = x2 - x1, sy = y2 - y1;
        double det = dx * sy - dy * sx;
        if (Math.abs(det) < 1e-6) return null; // parallel
        double qpx = x1 - px, qpy = y1 - py;
        double t = (qpx * sy - qpy * sx) / det;
        double u = (qpx * dy - qpy * dx) / det;
        if (t >= 0 && u >= 0 && u <= 1) {
            return new double[]{ px + dx*t, py + dy*t, t };
        }
        return null;
    }
    
    // Optimized vision polygon computation with caching
    private Path2D.Double computeVisionPolygon() {
        Point currentPos = player.getWorldPosition();
        
        // Check if we can use cached version
        if (visionCacheValid && cachedVisionPolygon != null && 
            currentPos.equals(lastPlayerPosition)) {
            return cachedVisionPolygon;
        }
        
        double px = currentPos.x, py = currentPos.y;
        int rays = 120;  // Reduced for performance - still smooth enough
        double radius = PLAYER_VIEW_RADIUS;
        
        Path2D.Double vision = new Path2D.Double();
        
        // Pre-calculate angle increment
        double angleIncrement = 2 * Math.PI / rays;
        
        // Pre-calculate nearby chunks
        int minCX = floorDiv((int)(px - CENTER_X), CHUNK_WORLD_SIZE) - 1;
        int minCY = floorDiv((int)(py - CENTER_Y), CHUNK_WORLD_SIZE) - 1;
        
        // Collect all relevant walls once
        List<Wall> nearbyWalls = new ArrayList<>();
        for (int cx = minCX; cx <= minCX+2; cx++) {
            for (int cy = minCY; cy <= minCY+2; cy++) {
                List<Wall> chunkWalls = wallsByChunk.get(new Point(cx, cy));
                if (chunkWalls != null) {
                    nearbyWalls.addAll(chunkWalls);
                }
            }
        }
        
        for (int i = 0; i < rays; i++) {
            double angle = angleIncrement * i;
            double dx = Math.cos(angle), dy = Math.sin(angle);
            double maxT = radius;
            
            // Check against all nearby walls
            for (Wall w : nearbyWalls) {
                double[] hit = raySegmentIntersect(px, py, dx, dy, w);
                if (hit != null && hit[2] < maxT) {
                    maxT = hit[2];
                }
            }
            
            double endX = px + dx * maxT;
            double endY = py + dy * maxT;
            
            if (i == 0) vision.moveTo(endX, endY);
            else vision.lineTo(endX, endY);
        }
        vision.closePath();
        
        // Cache the result
        cachedVisionPolygon = vision;
        lastPlayerPosition = new Point(currentPos);
        visionCacheValid = true;
        
        return vision;
    }
    // Draw the dungeon and walls.
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.translate(translateX, translateY);
        g2d.scale(scale, scale);
        // In player mode, only draw what's near the player
        Rectangle visibleRect;
        if (gameMode == GameModeManager.GameMode.PLAYER && player != null) {
            Point playerWorld = player.getWorldPosition();
            visibleRect = new Rectangle(
                playerWorld.x - PLAYER_VIEW_RADIUS,
                playerWorld.y - PLAYER_VIEW_RADIUS,
                PLAYER_VIEW_RADIUS * 2,
                PLAYER_VIEW_RADIUS * 2
            );
        } else {
            visibleRect = getVisibleRect();
        }
        // Draw dungeon circle.
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.drawOval(CENTER_X - RADIUS - 1, CENTER_Y - RADIUS - 1, DIAMETER + 2, DIAMETER + 2);
        // Draw a light outline circle.
        g2d.setColor(Color.GRAY);
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.drawOval(CENTER_X - RADIUS, CENTER_Y - RADIUS, DIAMETER, DIAMETER);
        // Draw grid if zoomed in enough
        int frameWidth = getWidth();
        int frameHeight = getHeight();
        double gridThreshold = 25.00 * Math.min(frameWidth, frameHeight) / 10000;
        if (scale > gridThreshold) {
            g2d.setColor(Color.LIGHT_GRAY);
            g2d.setStroke(new BasicStroke(0.5f));
            int panelW = getWidth();
            int panelH = getHeight();
            double winMinX = (-translateX) / scale;
            double winMaxX = (panelW - translateX) / scale;
            double winMinY = (-translateY) / scale;
            double winMaxY = (panelH - translateY) / scale;
            int gridStartX = (int)(Math.floor(winMinX / GRID_SIZE) * GRID_SIZE);
            int gridEndX   = (int)(Math.ceil (winMaxX / GRID_SIZE) * GRID_SIZE);
            int gridStartY = (int)(Math.floor(winMinY / GRID_SIZE) * GRID_SIZE);
            int gridEndY   = (int)(Math.ceil (winMaxY / GRID_SIZE) * GRID_SIZE);
            // — 3) Draw vertical lines across the full height —
            for (int x = gridStartX; x <= gridEndX; x += GRID_SIZE) {
                g2d.drawLine(x, gridStartY, x, gridEndY);
            }
            // — 4) Draw horizontal lines across the full width —
            for (int y = gridStartY; y <= gridEndY; y += GRID_SIZE) {
                g2d.drawLine(gridStartX, y, gridEndX, y);
            }
        }
        // Calculate visible chunks
        Rectangle vr;
        if (gameMode == GameModeManager.GameMode.PLAYER && player != null) {
            Point playerWorld = player.getWorldPosition();
            vr = new Rectangle(
                playerWorld.x - PLAYER_VIEW_RADIUS,
                playerWorld.y - PLAYER_VIEW_RADIUS,
                PLAYER_VIEW_RADIUS * 2,
                PLAYER_VIEW_RADIUS * 2
            );
        } else {
            vr = getVisibleRect();
        }
        double winMinX = (vr.getMinX()-translateX)/scale;
        double winMaxX = (vr.getMaxX()-translateX)/scale;
        double winMinY = (vr.getMinY()-translateY)/scale;
        double winMaxY = (vr.getMaxY()-translateY)/scale;
        int cw = CHUNK_SIZE_CELLS * GRID_SIZE;
        int minCX = floorDiv((int)(winMinX - CENTER_X), cw) - 1;
        int maxCX = floorDiv((int)(winMaxX - CENTER_X), cw) + 1;
        int minCY = floorDiv((int)(winMinY - CENTER_Y), cw) - 1;
        int maxCY = floorDiv((int)(winMaxY - CENTER_Y), cw) + 1;
Shape oldClip = g2d.getClip();
// if Player mode, clip to vision polygon
Path2D.Double vision = null;
if (gameMode == GameModeManager.GameMode.PLAYER && player != null) {
    vision = computeVisionPolygon();
    g2d.clip(vision);
}
g2d.setColor(Color.BLACK);
g2d.setStroke(new BasicStroke(1.0f));
for (int cx = minCX; cx <= maxCX; cx++) {
    for (int cy = minCY; cy <= maxCY; cy++) {
        List<Wall> chunkWalls = wallsByChunk.get(new Point(cx, cy));
        if (chunkWalls == null) continue;
        for (Wall w : chunkWalls) {
            g2d.drawLine(w.x1, w.y1, w.x2, w.y2);
        }
    }
}
// restore previous clip so other UI elements (player sprite, UI) draw normally
g2d.setClip(oldClip);
// now draw the player's vision overlay
if (vision != null) {
    g2d.setColor(new Color(128,128,128,128));  // semi transparent grey
    g2d.fill(vision);
}
// finally draw the player on top
if (player != null) {
    player.draw(g2d);
}
    }
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        double oldScale = scale;
        // zoom in/out by 10% per notch
        if (e.getWheelRotation() < 0) {
            scale *= 1.1;
        } else {
            scale /= 1.1;
        }
        if (gameMode == GameModeManager.GameMode.GAME_MASTER) {
            double frameW   = getWidth();
            double frameH   = getHeight();
            double chunkWU  = CHUNK_SIZE_CELLS * GRID_SIZE;
            double minWU    = 16 * chunkWU;
            double maxWU    = 6  * GRID_SIZE;
            double minScaleGM = Math.max(frameW / minWU,
                                        frameH / minWU);
            double maxScaleGM = Math.min(frameW / maxWU,
                                        frameH / maxWU);
            scale = Math.max(minScaleGM, Math.min(scale, maxScaleGM));
            // recenter around mouse
            double scaleChange = scale / oldScale;
            translateX = (int)(e.getX() - scaleChange * (e.getX() - translateX));
            translateY = (int)(e.getY() - scaleChange * (e.getY() - translateY));
            revalidate();
            repaint();
        }
        else if (gameMode == GameModeManager.GameMode.PLAYER) {
            double minScale = 10.0;
            double maxScale = 50.0;
            scale = Math.max(minScale, Math.min(scale, maxScale));
            // center camera on the player and repaint
            onPlayerMoved();
        }
    }
    // Simple inner class representing a wall.
    static class Wall {
        int x1, y1, x2, y2;
        Wall(int x1, int y1, int x2, int y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }
    private static class Chunk {
        final int cx, cy;          // chunk coords
        final List<Wall> walls;    // walls in this chunk
        Chunk(int cx, int cy, List<Wall> walls) {
            this.cx = cx; this.cy = cy; this.walls = walls;
        }
    }
}
