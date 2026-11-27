package normalmanv2.normalDiscGolf.impl.course.grid;

import java.util.Locale;

public enum HoleArchetype {
    TUNNEL("Tunnel"), DOGLEG("Dogleg"), ISLAND_GREEN("Island Green"), GAP_SHOT("Gap Shot"),
    DOUBLE_FAIRWAY("Double Fairway"), CLIFFSIDE("Cliffside"), GUARDIAN_GREEN("Guardian Green"),
    AIR_SHOT("Air Shot"), RISK_REWARD("Risk vs. Reward"), ELEVATED_BASKET("Elevated Basket");

    private final String label;

    HoleArchetype(String label) { this.label = label; }
    public String label() { return label; }

    public static HoleArchetype parse(String text) {
        try {
            return valueOf(text.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
