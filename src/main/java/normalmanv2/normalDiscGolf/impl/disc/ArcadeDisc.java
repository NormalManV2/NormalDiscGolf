package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.api.disc.DiscType;
import org.bukkit.Material;

import java.util.List;

public enum ArcadeDisc {
    PHANTOM("arcade-phantom", "Phantom Disc", DiscType.MID_RANGE, Material.ECHO_SHARD,
            new DiscAppearance(DiscAppearance.Shape.DOME, 0x9682ff, 0x36226e, 0x9ff9ff),
            "Phases through the first eligible obstacle block."),
    ROCKET("arcade-rocket", "Rocket Disc", DiscType.DRIVER, Material.FIREWORK_ROCKET,
            new DiscAppearance(DiscAppearance.Shape.LOW_PROFILE, 0xff8040, 0xa62d24, 0xffdb78),
            "Automatically boosts after five ticks in the air."),
    STICKY("arcade-sticky", "Sticky Disc", DiscType.PUTTER, Material.HONEYCOMB,
            new DiscAppearance(DiscAppearance.Shape.PUDDLE, 0xe8af4a, 0xa17132, 0xffeb96),
            "Stops on the first solid ground contact.");

    private final DiscModel model;
    private final Material icon;
    private final String description;
    private static final List<ArcadeDisc> ALL = List.of(values());
    private static final List<DiscModel> MODELS = ALL.stream().map(ArcadeDisc::model).toList();

    ArcadeDisc(String id, String name, DiscType type, Material icon, DiscAppearance appearance, String description) {
        var starter = DiscModel.starter(type);
        this.model = new DiscModel(id, name, type, 0, starter.speed(), starter.glide(), starter.turn(), starter.fade(), appearance);
        this.icon = icon;
        this.description = description;
    }

    public DiscModel model() { return model; }
    public Material icon() { return icon; }
    public String description() { return description; }
    public static List<ArcadeDisc> all() { return ALL; }
    public static List<DiscModel> models() { return MODELS; }

    public static ArcadeDisc find(String id) {
        for (var disc : ALL) if (disc.model.id().equals(id)) return disc;
        return null;
    }
}
