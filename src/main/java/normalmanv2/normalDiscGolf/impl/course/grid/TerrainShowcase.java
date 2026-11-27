package normalmanv2.normalDiscGolf.impl.course.grid;

enum TerrainShowcase {
    SANDSTONE_GATE("Sandstone Gate", "Keep the drive low through the arch.", "Hold the center of the canyon."),
    PAINTED_RIM("Painted Rim", "Follow the fairway below the canyon rim.", null),
    SPLIT_MESA("Split Mesa", "Follow the gold line or play the other rim.", null),
    CLOUD_CROSSING("Cloud Crossing", "Follow the gold line across the gap.", "Follow the ridge toward the basket."),
    DRIFTWOOD_GREEN("Driftwood Green", "Carry the gap to the basket island.", "Aim for the round basket green."),
    SKY_CROWN("Sky Crown", "Aim up the ridge to the raised basket.", null);

    private final String title;
    private final String hint;
    private final String clearHint;

    TerrainShowcase(String title, String hint, String clearHint) {
        this.title = title;
        this.hint = hint;
        this.clearHint = clearHint;
    }

    String title() { return title; }
    String hint(boolean featurePresent) { return featurePresent || clearHint == null ? hint : clearHint; }

    static TerrainShowcase forArchetype(CourseTerrain terrain, HoleArchetype archetype) {
        return switch (terrain) {
            case DESERT -> switch (archetype) {
                case GAP_SHOT -> SANDSTONE_GATE;
                case CLIFFSIDE -> PAINTED_RIM;
                case DOUBLE_FAIRWAY -> SPLIT_MESA;
                default -> null;
            };
            case SKY -> switch (archetype) {
                case AIR_SHOT -> CLOUD_CROSSING;
                case ISLAND_GREEN -> DRIFTWOOD_GREEN;
                case ELEVATED_BASKET -> SKY_CROWN;
                default -> null;
            };
            default -> null;
        };
    }
}
