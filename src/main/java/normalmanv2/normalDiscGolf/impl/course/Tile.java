package normalmanv2.normalDiscGolf.impl.course;

import normalmanv2.normalDiscGolf.impl.course.obstacle.ObstacleImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Tile {
    private final List<TileTypes> possibleStates;
    private TileTypes collapsedState;
    private boolean isCollapsed;
    private final Location location;

    public Tile(List<TileTypes> initialStates, Location location) {
        this.possibleStates = new ArrayList<>(initialStates);
        this.isCollapsed = false;
        this.location = location;
    }

    public boolean isCollapsed() {
        return isCollapsed;
    }

    public int getEntropy() {
        return possibleStates.size();
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
        return switch (state) {
            case WATER -> List.of(TileTypes.WATER, TileTypes.FAIRWAY);
            case OUT_OF_BOUNDS -> List.of(TileTypes.OBSTACLE, TileTypes.WATER);
            default -> List.of(TileTypes.FAIRWAY);
        };
    }

    public TileTypes getCollapsedState() {
        return this.collapsedState;
    }

    public Location getLocation() {
        return this.location;
    }
}