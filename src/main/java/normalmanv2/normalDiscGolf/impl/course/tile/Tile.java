package normalmanv2.normalDiscGolf.impl.course.tile;

import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Tile {

    private final List<TileTypes> possibleStates;
    private TileTypes collapsedState;
    private boolean isCollapsed;
    private final CourseGrid.GridPoint gridPoint;

    public Tile(List<TileTypes> initialStates, CourseGrid.GridPoint gridPoint) {
        this.possibleStates = new ArrayList<>(initialStates);
        this.isCollapsed = false;
        this.gridPoint = gridPoint;
    }

    public TileTypes collapse(Random random) {
        this.collapsedState = possibleStates.get(random.nextInt(possibleStates.size()));
        this.isCollapsed = true;
        return collapsedState;
    }

    public void collapseTo(TileTypes type) {
        possibleStates.clear();
        possibleStates.add(type);
        this.isCollapsed = true;
        this.collapsedState = type;
    }

    public void setCollapsedState(TileTypes collapsedState) {
        this.collapsedState = collapsedState;
        this.isCollapsed = true;
    }

    public boolean updatePossibleStatesBasedOn(Tile neighbor) {
        boolean stateRemoved = false;

        TileTypes neighborState = neighbor.collapsedState;
        List<TileTypes> compatibleStates = getCompatibleStates(neighborState);

        int initialEntropy = getEntropy();
        possibleStates.removeIf(state -> !compatibleStates.contains(state));

        if (possibleStates.size() < initialEntropy) {
            stateRemoved = true;
        }

        return stateRemoved;
    }

    private List<TileTypes> getCompatibleStates(TileTypes state) {
        // expanded for roughs, sand, etc.
        return switch (state) {
            case WATER -> List.of(TileTypes.WATER, TileTypes.HEAVY_ROUGH);
            case OUT_OF_BOUNDS -> List.of(TileTypes.OUT_OF_BOUNDS);
            case PIN -> List.of(TileTypes.FAIRWAY, TileTypes.LIGHT_ROUGH, TileTypes.HEAVY_ROUGH, TileTypes.SAND);
            case FAIRWAY -> List.of(TileTypes.FAIRWAY, TileTypes.LIGHT_ROUGH);
            case LIGHT_ROUGH -> List.of(TileTypes.LIGHT_ROUGH, TileTypes.FAIRWAY, TileTypes.HEAVY_ROUGH);
            case HEAVY_ROUGH -> List.of(TileTypes.HEAVY_ROUGH, TileTypes.LIGHT_ROUGH, TileTypes.OBSTACLE);
            default -> List.of(TileTypes.HEAVY_ROUGH, TileTypes.LIGHT_ROUGH);
        };
    }

    public BoundingBox getBoundingBox() {
        double x = gridPoint.x();
        double y = gridPoint.y();
        double z = gridPoint.z();

        return new BoundingBox(
                x, y, z,
                x + Constants.DEFAULT_TILE_SIZE, y + 256, z + Constants.DEFAULT_TILE_SIZE
        );
    }

    public TileTypes getCollapsedState() {
        return this.collapsedState;
    }

    public CourseGrid.GridPoint getGridPoint() {
        return this.gridPoint;
    }

    public boolean isCollapsed() {
        return isCollapsed;
    }

    public int getEntropy() {
        return possibleStates.size();
    }


}