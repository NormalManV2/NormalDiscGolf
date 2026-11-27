package normalmanv2.normalDiscGolf.impl.course.grid;

import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;

import java.util.List;

/** Each layout fits a seven-tile cell. Rotation and reflection keep the same safe spacing. */
public enum ParklandHole {
    MEADOW("Meadow", 3, new int[][]{{2,2},{4,3}}, 3, 4, TileTypes.SAND),
    RIDGE("Ridge Run", 4, new int[][]{{0,1},{6,5}}, 3, 2, TileTypes.SAND),
    GROVE("Birch Grove", 3, new int[][]{{1,3},{5,3}}, 4, 2, TileTypes.SAND),
    POND("Pond Bend", 4, new int[][]{{1,1},{1,4},{3,5},{5,5}}, 3, 3, TileTypes.WATER),
    QUARRY("Quarry Approach", 3, new int[][]{{1,1},{4,1},{5,2}}, 4, 2, TileTypes.SAND),
    PINES("Pine Switchback", 5, new int[][]{{0,1},{2,5},{4,5},{6,2}}, 4, 3, TileTypes.WATER);

    static final List<ParklandHole> SHORT_ROTATION = List.of(MEADOW, GROVE, QUARRY, POND);

    final String title;
    final int par;
    final int[][] waypoints;
    final int hazardX;
    final int hazardZ;
    final TileTypes hazard;

    ParklandHole(String title, int par, int[][] waypoints, int hazardX, int hazardZ, TileTypes hazard) {
        this.title = title;
        this.par = par;
        this.waypoints = waypoints;
        this.hazardX = hazardX;
        this.hazardZ = hazardZ;
        this.hazard = hazard;
    }

    int[][] shortWaypoints() {
        return switch (this) {
            case MEADOW -> waypoints;
            case GROVE -> new int[][]{{2,3},{3,4}};
            case QUARRY -> new int[][]{{2,1},{4,1}};
            case POND -> new int[][]{{1,2},{4,2}};
            default -> throw new IllegalStateException("No short route for " + title);
        };
    }

    static int[] transform(int x, int z, int orientation) {
        if (orientation >= 4) x = 6 - x;
        return switch (orientation % 4) {
            case 1 -> new int[]{6 - z, x};
            case 2 -> new int[]{6 - x, 6 - z};
            case 3 -> new int[]{z, 6 - x};
            default -> new int[]{x, z};
        };
    }
}
