package worlddung;

import java.awt.Point;
import java.util.HashSet;
import java.util.Set;

public class MinimapTracker {
    // stores grid-cell coords the player has uncovered
    private final Set<Point> visitedCells = new HashSet<>();

    // how many world units out from the player get marked (adjustable by skills)
    private int viewRadiusUnits;

    // grid cell size must match your CustomPanel.GRID_SIZE
    private final int gridSize;
    
    // Cache for radius calculations to avoid repeated math
    private int cachedViewRadiusUnits = -1;
    private int cachedRadiusCells = -1;
    private Point[] cachedCircleOffsets = null;

    public MinimapTracker(int gridSize, int initialViewRadiusUnits) {
        this.gridSize = gridSize;
        this.viewRadiusUnits = initialViewRadiusUnits;
        updateRadiusCache();
    }

    /** Call whenever the player moves to mark newly uncovered cells */
    public void markVisited(int worldX, int worldY) {
        // convert world coords to cell coords
        int cellX = floorDiv(worldX, gridSize);
        int cellY = floorDiv(worldY, gridSize);

        // Use cached circle offsets instead of recalculating
        if (cachedCircleOffsets != null) {
            for (Point offset : cachedCircleOffsets) {
                visitedCells.add(new Point(cellX + offset.x, cellY + offset.y));
            }
        }
    }
    
    /** Pre-calculate circle offsets when radius changes */
    private void updateRadiusCache() {
        if (viewRadiusUnits == cachedViewRadiusUnits) {
            return; // Already cached
        }
        
        cachedViewRadiusUnits = viewRadiusUnits;
        cachedRadiusCells = (int) Math.ceil((double)viewRadiusUnits / gridSize);
        
        // Pre-calculate all valid circle offsets
        Set<Point> offsets = new HashSet<>();
        int rCells = cachedRadiusCells;
        int rSquared = rCells * rCells;
        
        for (int dx = -rCells; dx <= rCells; dx++) {
            for (int dy = -rCells; dy <= rCells; dy++) {
                if (dx * dx + dy * dy <= rSquared) {
                    offsets.add(new Point(dx, dy));
                }
            }
        }
        
        cachedCircleOffsets = offsets.toArray(new Point[0]);
    }

    /** Check if a given world point is in the visited set */
    public boolean isVisited(int worldX, int worldY) {
        int cellX = floorDiv(worldX, gridSize);
        int cellY = floorDiv(worldY, gridSize);
        return visitedCells.contains(new Point(cellX, cellY));
    }

    /** Adjust how far the minimap records (e.g. when a buff/skill changes your sight) */
    public void setViewRadiusUnits(int worldUnits) {
        if (this.viewRadiusUnits != worldUnits) {
            this.viewRadiusUnits = worldUnits;
            updateRadiusCache();
        }
    }

    public int getViewRadiusUnits() {
        return viewRadiusUnits;
    }
    
    /** Get all visited cells - useful for batch operations */
    public Set<Point> getVisitedCells() {
        return new HashSet<>(visitedCells); // Return defensive copy
    }

    // java's floor division for negatives
    private int floorDiv(int x, int y) {
        int r = x / y;
        if ((x ^ y) < 0 && r * y != x) r--;
        return r;
    }
}