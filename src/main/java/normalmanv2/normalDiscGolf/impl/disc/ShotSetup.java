package normalmanv2.normalDiscGolf.impl.disc;

import java.util.Locale;

public record ShotSetup(Angle angle, Stance stance, boolean timing, boolean preview) {
    public static final ShotSetup DEFAULT = new ShotSetup(Angle.FLAT, Stance.NORMAL, false, true);
    public static final double PUTTING_RANGE = 15;

    public enum Angle {
        FLAT(0), HYZER(1), ANHYZER(-1);

        private final int bank;
        Angle(int bank) { this.bank = bank; }
        public int bank() { return bank; }
        public Angle next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum Stance {
        NORMAL("Throw"), PUTT("Putt"), ROLLER("Roller"), FLEX("Flex"), TOMAHAWK("Tomahawk"), THUMBER("Thumber");

        private final String label;
        Stance(String label) { this.label = label; }
        public String label() { return label; }
        public String id() { return name().toLowerCase(Locale.ROOT); }
        public boolean isTrickShot() { return this == FLEX || isOverhand(); }
        public boolean isOverhand() { return this == TOMAHAWK || this == THUMBER; }
    }

    public ShotSetup withAngle(Angle value) { return new ShotSetup(value, stance, timing, preview); }

    public boolean usesPutting(boolean putter, double distance) {
        return putter && distance <= PUTTING_RANGE && stance == Stance.PUTT;
    }

    public static double timingError(double position, int accuracyLevel) {
        return timingError(position, accuracyLevel, 0);
    }

    public static double timingError(double position, int accuracyLevel, double situationalWindow) {
        double accuracyWindow = Math.min(.18, .08 + .025 * Math.max(0, accuracyLevel - 1));
        double cleanWindow = Math.min(.22, accuracyWindow + situationalWindow);
        return Math.max(0, (Math.abs(position - .5) - cleanWindow) / (.5 - cleanWindow));
    }
}
