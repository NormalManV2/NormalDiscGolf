package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;

import java.util.List;

import static normalmanv2.normalDiscGolf.impl.disc.DiscAppearance.Shape.*;

public record DiscModel(String id, String displayName, DiscType type, int price,
                        int speed, int glide, int turn, int fade, DiscAppearance appearance) {
    public static final DiscModel STARTER_PUTTER = builtIn("starter-putter", "Starter Putter", DiscType.PUTTER,
            0, 2, 4, 0, 1, CLASSIC, 0x8fce55, 0x39753d, 0xe8ffca);
    public static final DiscModel FLOAT = builtIn("float", "Float", DiscType.PUTTER,
            60, 2, 6, 0, 0, DOME, 0xd8f4ff, 0x5488b0, 0x70e0d2);
    public static final DiscModel ANCHOR = builtIn("anchor", "Anchor", DiscType.PUTTER,
            70, 3, 3, 0, 2, PUDDLE, 0xc45d43, 0x57333a, 0xffca87);
    public static final DiscModel LANTERN = builtIn("lantern", "Lantern", DiscType.PUTTER,
            85, 3, 5, -1, 1, ROUNDED, 0xffc63d, 0x9b511c, 0xfff5b2);
    public static final DiscModel STARTER_MIDRANGE = builtIn("starter-midrange", "Starter Midrange", DiscType.MID_RANGE,
            0, 5, 5, -1, 1, ROUNDED, 0x68b6ec, 0x304e8b, 0xd1f8ff);
    public static final DiscModel GLIDER = builtIn("glider", "Glider", DiscType.MID_RANGE,
            90, 5, 7, -2, 0, DOME, 0xac9bf0, 0x4a396f, 0xfce3ff);
    public static final DiscModel CORNERSTONE = builtIn("cornerstone", "Cornerstone", DiscType.MID_RANGE,
            100, 6, 4, 0, 3, BEVELED, 0x96978b, 0x40494d, 0xf3da95);
    public static final DiscModel MARSHLIGHT = builtIn("marshlight", "Marshlight", DiscType.MID_RANGE,
            115, 6, 6, -3, 1, PUDDLE, 0x42b896, 0x245463, 0xd1ffb1);
    public static final DiscModel STARTER_DRIVER = builtIn("starter-driver", "Starter Driver", DiscType.DRIVER,
            0, 9, 5, -2, 2, BEVELED, 0xfa9751, 0xa54f2e, 0xffe5b4);
    public static final DiscModel TAILWIND = builtIn("tailwind", "Tailwind", DiscType.DRIVER,
            140, 10, 7, -3, 1, DOME, 0xe96080, 0x71324f, 0xffdc99);
    public static final DiscModel SWITCHBACK = builtIn("switchback", "Switchback", DiscType.DRIVER,
            150, 11, 4, 0, 4, LOW_PROFILE, 0x464d75, 0x29283d, 0xe0c1ff);
    public static final DiscModel SKYLARK = builtIn("skylark", "Skylark", DiscType.DRIVER,
            175, 12, 6, -1, 3, LOW_PROFILE, 0xf5e6b6, 0x397aa6, 0xee795f);

    private static final List<DiscModel> BUILT_INS = List.of(STARTER_PUTTER, FLOAT, ANCHOR, LANTERN,
            STARTER_MIDRANGE, GLIDER, CORNERSTONE, MARSHLIGHT, STARTER_DRIVER, TAILWIND, SWITCHBACK, SKYLARK);

    public static List<DiscModel> builtIns() { return BUILT_INS; }

    private static DiscModel builtIn(String id, String name, DiscType type, int price, int speed,
                                    int glide, int turn, int fade, DiscAppearance.Shape shape,
                                    int plate, int rim, int accent) {
        return new DiscModel(id, name, type, price, speed, glide, turn, fade, new DiscAppearance(shape, plate, rim, accent));
    }

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
}
