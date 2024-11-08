package normalmanv2.normalDiscGolf.impl.course;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

public class CourseGrid {

    private Tile[][] grid;
    private int width, height;
    private List<String> tileTypes;
    private Random random;

    public CourseGrid(int width, int height, List<String> tileTypes) {
        this.width = width;
        this.height = height;
        this.tileTypes = tileTypes;
        this.grid = new Tile[width][height];

        initializeSuperposition();
    }

    private void initializeSuperposition() {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                grid[x][y] = null;
            }
        }
    }

    public Tile getTileAt(int x, int y) {
        return grid[x][y];
    }

    public void setTileAt(int x, int y, Tile tile) {
        grid[x][y] = tile;
    }

    public void collapse() {
        // While there are cells to collapse, find the cell with the lowest entropy
        while (!allCellsCollapsed()) {
            Position pos = findLowestEntropyCell();
            if (pos != null) {
                Tile chosenTile = chooseRandomTile(pos);
                setTileAt(pos.x, pos.y, chosenTile);
                propagateConstraints(pos.x, pos.y);
            }
        }
    }

    private Position findLowestEntropyCell() {
        // Find a cell with the fewest possible tiles (not yet collapsed)
        // This function returns the (x, y) coordinates of that cell
        return new Position(x, y); // Return position of cell with lowest entropy
    }

    private String chooseRandomTile(Position pos) {
        // Choose a random tile from the possible tiles at `pos`
        return tileTypes.get(random.nextInt(tileTypes.size()));
    }

    private void propagateConstraints(int x, int y) {
        Queue<Position> queue = new LinkedList<>();
        queue.add(new Position(x, y));

        while (!queue.isEmpty()) {
            Position current = queue.poll();
            // For each neighboring cell, remove invalid tiles based on constraints
            for (Position neighbor : getNeighbors(current)) {
                // If constraints have been updated, add neighbor to the queue
                if (updateNeighborOptions(current, neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
    }

    private boolean updateNeighborOptions(Position current, Position neighbor) {
        // Implement adjacency rules here
        // Return true if neighbor's options have been updated
        return false;
    }

}
