package normalmanv2.normalDiscGolf.impl.cosmetic;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Short particle designs drawn above the basket using vanilla client effects. */
public enum ScoreDecal {
    NONE("No Decal", 0, Material.BARRIER, "Use the standard basket celebration."),
    GOLD_RING("Gold Ring", 60, Material.GOLD_NUGGET, "A gold halo capped with sparks."),
    AURORA_SPIRAL("Aurora Spiral", 90, Material.AMETHYST_SHARD, "Twin teal and violet spirals."),
    EMBER_BLOOM("Ember Bloom", 120, Material.BLAZE_POWDER, "A red and orange starburst.");

    private final String displayName;
    private final int price;
    private final Material icon;
    private final String description;

    ScoreDecal(String displayName, int price, Material icon, String description) {
        this.displayName = displayName;
        this.price = price;
        this.icon = icon;
        this.description = description;
    }

    public String id() { return name().toLowerCase(Locale.ROOT).replace('_', '-'); }
    public String displayName() { return displayName; }
    public int price() { return price; }
    public Material icon() { return icon; }
    public String description() { return description; }

    public static ScoreDecal find(String id) {
        for (ScoreDecal decal : values()) if (decal.id().equalsIgnoreCase(id)) return decal;
        return null;
    }

    public void show(Location origin) {
        draw(origin, null);
    }

    public void preview(Player player) {
        Location origin = player.getEyeLocation();
        origin.add(origin.getDirection().multiply(3)).subtract(0, .7, 0);
        draw(origin, player);
    }

    private void draw(Location origin, Player viewer) {
        switch (this) {
            case NONE -> { }
            case GOLD_RING -> {
                var gold = new Particle.DustOptions(Color.fromRGB(255, 195, 58), 1.25f);
                for (int i = 0; i < 24; i++) {
                    double angle = i * Math.TAU / 24;
                    dust(origin, viewer, gold, 1.45, angle, .15);
                    if (i % 4 == 0) {
                        var spark = offset(origin, 1.45, angle, .43);
                        emit(spark, viewer, Particle.ELECTRIC_SPARK, null);
                    }
                }
            }
            case AURORA_SPIRAL -> {
                var teal = new Particle.DustOptions(Color.fromRGB(65, 255, 213), 1.15f);
                var violet = new Particle.DustOptions(Color.fromRGB(171, 102, 255), 1.15f);
                for (int i = 0; i < 18; i++) {
                    double step = i / 17.0;
                    double angle = step * 1.3 * Math.TAU;
                    double radius = 1.15 - .55 * step;
                    dust(origin, viewer, teal, radius, angle, step * 1.7);
                    dust(origin, viewer, violet, radius, angle + Math.PI, step * 1.7);
                }
                emit(origin.clone().add(0, 1.75, 0), viewer, Particle.END_ROD, null);
            }
            case EMBER_BLOOM -> {
                var orange = new Particle.DustOptions(Color.fromRGB(255, 134, 33), 1.3f);
                var red = new Particle.DustOptions(Color.fromRGB(239, 49, 34), 1.2f);
                for (int spoke = 0; spoke < 8; spoke++) {
                    double angle = spoke * Math.TAU / 8;
                    for (int step = 1; step <= 4; step++) {
                        dust(origin, viewer, step < 3 ? orange : red, step * .36, angle, step * .23);
                    }
                    emit(offset(origin, 1.65, angle, 1.1), viewer, Particle.FLAME, null);
                }
            }
        }
    }

    private static Location offset(Location origin, double radius, double angle, double height) {
        return origin.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
    }

    private static void dust(Location origin, Player viewer, Particle.DustOptions color,
                             double radius, double angle, double height) {
        emit(offset(origin, radius, angle, height), viewer, Particle.DUST, color);
    }

    private static <T> void emit(Location at, Player viewer, Particle particle, T data) {
        if (viewer != null) viewer.spawnParticle(particle, at, 1, 0, 0, 0, 0, data);
        else at.getWorld().spawnParticle(particle, at, 1, 0, 0, 0, 0, data, true);
    }
}
