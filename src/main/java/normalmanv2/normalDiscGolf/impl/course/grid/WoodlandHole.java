package normalmanv2.normalDiscGolf.impl.course.grid;

import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid.GridPoint;

import java.util.List;

public enum WoodlandHole {
    CATHEDRAL_RUN(HoleArchetype.TUNNEL, "Cathedral Run", "Hold the center of the pine corridor."),
    FERNBEND(HoleArchetype.DOGLEG, "Fernbend", "Aim for the bend, then turn %s."),
    MOSSGATE(HoleArchetype.GAP_SHOT, "Mossgate", "Aim through the gap in the fairway.");

    public enum LandmarkType { OLD_GROWTH_PINE, MOSSY_BOULDER, RUINED_GATEPOST }
    public record Landmark(LandmarkType type, GridPoint position, int height) {
        public static final int RADIUS = 3;
    }

    private final HoleArchetype archetype;
    private final String label;
    private final String hint;

    WoodlandHole(HoleArchetype archetype, String label, String hint) {
        this.archetype = archetype;
        this.label = label;
        this.hint = hint;
    }

    public String label() { return label; }

    public String hint(List<GridPoint> route) {
        if (this != FERNBEND) return hint;
        GridPoint tee = route.get(0), bend = route.get(1), basket = route.get(route.size() - 1);
        int turn = (bend.x() - tee.x()) * (basket.z() - bend.z())
                - (bend.z() - tee.z()) * (basket.x() - bend.x());
        return hint.formatted(turn > 0 ? "right" : "left");
    }

    static WoodlandHole forArchetype(HoleArchetype archetype) {
        return switch (archetype) {
            case TUNNEL -> CATHEDRAL_RUN;
            case DOGLEG -> FERNBEND;
            case GAP_SHOT -> MOSSGATE;
            default -> null;
        };
    }
}
