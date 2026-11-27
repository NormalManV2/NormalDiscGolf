package normalmanv2.normalDiscGolf.impl.course.grid;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum CourseTerrain {
    PARKLAND("Parkland", "plains", 65, 4, 14, .24, .25,
            Material.GRASS_BLOCK, Material.DIRT, Material.STONE, Material.PODZOL, Material.MOSS_BLOCK),
    WOODLAND("Woodland", "old_growth_pine_taiga", 70, 7, 13, .68, .24,
            Material.GRASS_BLOCK, Material.DIRT, Material.STONE, Material.PODZOL, Material.MOSS_BLOCK),
    MOUNTAIN("Mountain", "windswept_hills", 104, 24, 15, .43, .24,
            Material.STONE, Material.ANDESITE, Material.STONE, Material.SNOW_BLOCK, Material.GRASS_BLOCK),
    DESERT("Desert", "badlands", 76, 11, 17, .33, .37,
            Material.SAND, Material.SANDSTONE, Material.TERRACOTTA, Material.RED_SAND, Material.SMOOTH_SANDSTONE),
    ISLAND("Island", "beach", 68, 5, 17, .46, .50,
            Material.GRASS_BLOCK, Material.SANDSTONE, Material.STONE, Material.SAND, Material.MOSS_BLOCK),
    NETHER("Nether", "crimson_forest", 72, 12, 15, .53, .55,
            Material.CRIMSON_NYLIUM, Material.NETHERRACK, Material.BLACKSTONE, Material.BASALT, Material.WARPED_NYLIUM),
    SKY("Sky", "end_highlands", 138, 11, 16, .32, .38,
            Material.GRASS_BLOCK, Material.TUFF, Material.CALCITE, Material.MOSS_BLOCK, Material.MOSS_BLOCK);

    private final String label;
    private final String biome;
    final int altitude;
    final int elevation;
    final int fairwayWidth;
    final double obstacles;
    final double hazards;
    final Material surface;
    final Material fill;
    final Material rock;
    final Material accent;
    final Material green;

    CourseTerrain(String label, String biome, int altitude, int elevation, int fairwayWidth,
                  double obstacles, double hazards, Material surface, Material fill, Material rock,
                  Material accent, Material green) {
        this.label = label;
        this.biome = biome;
        this.altitude = altitude;
        this.elevation = elevation;
        this.fairwayWidth = fairwayWidth;
        this.obstacles = obstacles;
        this.hazards = hazards;
        this.surface = surface;
        this.fill = fill;
        this.rock = rock;
        this.accent = accent;
        this.green = green;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String label() { return label; }
    public String biome() { return biome; }
    public static CourseTerrain parse(String text) {
        return Arrays.stream(values()).filter(terrain -> terrain.id().equalsIgnoreCase(text.trim())).findFirst().orElse(null);
    }
    public static List<CourseTerrain> environments() {
        return List.of(WOODLAND, MOUNTAIN, DESERT, ISLAND, NETHER, SKY);
    }
}
