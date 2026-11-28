package normalmanv2.normalDiscGolf.impl.course.grid;

enum TerrainShowcase {
    GLACIER_PASS("Glacier Pass", "Aim between the blue ice walls.", "Hold the center of the pass."),
    EAGLE_RIDGE("Eagle Ridge", "Follow the ridge above the drop.", null),
    SUMMIT_STEPS("Summit Steps", "Aim uphill to the raised basket.", null),
    SANDSTONE_GATE("Sandstone Gate", "Keep the drive low through the arch.", "Hold the center of the canyon."),
    PAINTED_RIM("Painted Rim", "Follow the fairway below the canyon rim.", null),
    SPLIT_MESA("Split Mesa", "Follow the gold line or play the other rim.", null),
    WRECK_LAGOON("Wreck Lagoon", "Carry the lagoon along the gold line.", "Follow the shoreline to the basket."),
    LANTERN_CAY("Lantern Cay", "Carry the water to the basket island.", "Aim for the round basket green."),
    PALM_COVE("Palm Cove", "Play to the bend before turning to the green.", null),
    BASALT_GATE("Basalt Gate", "Thread the basalt pillars.", "Hold the center of the passage."),
    EMBER_CROSSING("Ember Crossing", "Carry the lava to the far bank.", "Follow the fairway toward the basket."),
    ASHEN_KEEP("Ashen Keep", "Hold the center on the approach to the keep.", "Hold the center on the approach."),
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
    boolean straight() { return this == SANDSTONE_GATE || this == GLACIER_PASS || this == BASALT_GATE; }

    static TerrainShowcase forArchetype(CourseTerrain terrain, HoleArchetype archetype) {
        return switch (terrain) {
            case MOUNTAIN -> switch (archetype) {
                case GAP_SHOT -> GLACIER_PASS;
                case CLIFFSIDE -> EAGLE_RIDGE;
                case ELEVATED_BASKET -> SUMMIT_STEPS;
                default -> null;
            };
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
            case ISLAND -> switch (archetype) {
                case AIR_SHOT -> WRECK_LAGOON;
                case ISLAND_GREEN -> LANTERN_CAY;
                case DOGLEG -> PALM_COVE;
                default -> null;
            };
            case NETHER -> switch (archetype) {
                case GAP_SHOT -> BASALT_GATE;
                case AIR_SHOT -> EMBER_CROSSING;
                case GUARDIAN_GREEN -> ASHEN_KEEP;
                default -> null;
            };
            default -> null;
        };
    }
}
