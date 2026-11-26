package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;

import java.util.Arrays;
import java.util.Locale;

public enum DiscModel {
    STARTER_PUTTER("Starter Putter", DiscType.PUTTER, 0, 2, 4, 0, 1),
    FLOAT("Float", DiscType.PUTTER, 60, 2, 6, 0, 0),
    ANCHOR("Anchor", DiscType.PUTTER, 70, 3, 3, 0, 2),
    STARTER_MIDRANGE("Starter Midrange", DiscType.MID_RANGE, 0, 5, 5, -1, 1),
    GLIDER("Glider", DiscType.MID_RANGE, 90, 5, 7, -2, 0),
    CORNERSTONE("Cornerstone", DiscType.MID_RANGE, 100, 6, 4, 0, 3),
    STARTER_DRIVER("Starter Driver", DiscType.DRIVER, 0, 9, 5, -2, 2),
    TAILWIND("Tailwind", DiscType.DRIVER, 140, 10, 7, -3, 1),
    SWITCHBACK("Switchback", DiscType.DRIVER, 150, 11, 4, 0, 4);

    private final String displayName;
    private final DiscType type;
    private final int price, speed, glide, turn, fade;

    DiscModel(String displayName, DiscType type, int price, int speed, int glide, int turn, int fade) {
        this.displayName = displayName;
        this.type = type;
        this.price = price;
        this.speed = speed;
        this.glide = glide;
        this.turn = turn;
        this.fade = fade;
    }

    public String id() { return name().toLowerCase(Locale.ROOT).replace('_', '-'); }
    public String displayName() { return displayName; }
    public DiscType type() { return type; }
    public int price() { return price; }
    public int speed() { return speed; }
    public int glide() { return glide; }

    public String flightStats() {
        return "Speed " + speed + "  Glide " + glide + "  Turn " + turn + "  Fade " + fade;
    }

    public DiscImpl createDisc() {
        return switch (type) {
            case PUTTER -> new Putter(speed, glide, turn, fade, displayName);
            case MID_RANGE -> new MidRange(speed, glide, turn, fade, displayName);
            case DRIVER -> new Driver(speed, glide, turn, fade, displayName);
        };
    }

    public static DiscModel starter(DiscType type) {
        return switch (type) {
            case PUTTER -> STARTER_PUTTER;
            case MID_RANGE -> STARTER_MIDRANGE;
            case DRIVER -> STARTER_DRIVER;
        };
    }

    public static DiscModel find(String id) {
        if (id == null) return null;
        return Arrays.stream(values()).filter(model -> model.id().equalsIgnoreCase(id)).findFirst().orElse(null);
    }
}
