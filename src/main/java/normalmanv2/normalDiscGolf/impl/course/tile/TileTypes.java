package normalmanv2.normalDiscGolf.impl.course.tile;

public enum TileTypes {

    FAIRWAY,
    WATER,
    OBSTACLE,
    TEE,
    PIN,
    OUT_OF_BOUNDS,
    LIGHT_ROUGH,
    HEAVY_ROUGH,
    SAND,
    LAVA;

    public boolean isHazard() { return this == WATER || this == LAVA || this == OUT_OF_BOUNDS; }

}
