package normalmanv2.normalDiscGolf.impl.course;

import java.util.HashSet;
import java.util.Set;

public class Tile {

    private final String type;
    private final Set<String> allowedNeighbors;

    public Tile(String type) {
        this.type = type;
        this.allowedNeighbors = new HashSet<>();
    }

    public String getType() {
        return this.type;
    }

    public void addAllowedNeighbor(String neighbor) {
        this.allowedNeighbors.add(neighbor);
    }

    public boolean canBeAdjacent(String neighbor) {
        return this.allowedNeighbors.contains(neighbor);
    }
}
