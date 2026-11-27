package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.impl.resourcepack.CustomDiscAssets;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class DiscCatalog {
    private static final Set<String> RESERVED_IDS = Set.of("putter", "mid", "midrange", "driver");
    private static final String EXAMPLE_PATH = "custom-discs/_example/";
    private final List<DiscModel> models;
    private final Map<String, DiscModel> byId;
    private final Map<String, CustomDiscAssets> customAssets;

    private DiscCatalog(Map<String, DiscModel> models, Map<String, CustomDiscAssets> assets) {
        this.models = List.copyOf(models.values());
        this.byId = Map.copyOf(models);
        this.customAssets = Map.copyOf(assets);
    }

    public static DiscCatalog load(Path root, Logger logger) {
        Map<String, DiscModel> models = new LinkedHashMap<>();
        DiscModel.builtIns().forEach(model -> models.put(model.id(), model));
        Map<String, CustomDiscAssets> assets = new LinkedHashMap<>();
        try {
            Files.createDirectories(root);
            try {
                writeExample(root);
            } catch (IOException failure) {
                logger.warning("Could not create the custom disc example: " + failure.getMessage());
            }
            try (var folders = Files.list(root)) {
                for (Path folder : folders.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                    String id = folder.getFileName().toString();
                    if (id.startsWith("_") || id.startsWith(".")) continue;
                    if (!id.matches("[a-z0-9][a-z0-9-]{0,39}") || RESERVED_IDS.contains(id)
                            || ArcadeDisc.find(id) != null || models.containsKey(id)) {
                        logger.warning("Skipping custom disc folder " + id + ": use a unique lowercase ID with letters, digits and hyphens.");
                        continue;
                    }
                    try {
                        DiscModel disc = readDisc(folder, id);
                        if (models.values().stream().anyMatch(other -> other.displayName().equalsIgnoreCase(disc.displayName())))
                            throw new IllegalArgumentException("name is already used by another disc");
                        models.put(id, disc);
                        try {
                            CustomDiscAssets art = CustomDiscAssets.load(folder, id);
                            if (art != null) assets.put(id, art);
                        } catch (IOException failure) {
                            logger.warning("Using the generated model for " + id + ": " + failure.getMessage());
                        }
                    } catch (IOException | InvalidConfigurationException | IllegalArgumentException failure) {
                        logger.warning("Skipping custom disc " + id + ": " + failure.getMessage());
                    }
                }
            }
        } catch (IOException failure) {
            logger.warning("Could not read custom-discs: " + failure.getMessage());
        }
        if (models.size() > DiscModel.builtIns().size())
            logger.info("Loaded " + (models.size() - DiscModel.builtIns().size()) + " custom disc(s).");
        return new DiscCatalog(models, assets);
    }

    private static void writeExample(Path root) throws IOException {
        Path example = root.resolve("_example");
        if (Files.isSymbolicLink(example)) throw new IOException("_example must be a regular folder");
        Files.createDirectories(example);
        for (String name : List.of("disc.yml", "model.json.example")) {
            Path target = example.resolve(name);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue;
            try (var template = DiscCatalog.class.getClassLoader().getResourceAsStream(EXAMPLE_PATH + name)) {
                if (template == null) throw new IOException("missing example " + name);
                Files.copy(template, target);
            }
        }
    }

    private static DiscModel readDisc(Path folder, String id) throws IOException, InvalidConfigurationException {
        var yaml = new YamlConfiguration();
        Path file = folder.resolve("disc.yml");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 32 * 1024)
            throw new IOException("disc.yml is missing or too large");
        yaml.load(file.toFile());
        String name = yaml.getString("name", "").trim();
        if (name.isEmpty() || name.length() > 48 || name.chars().anyMatch(Character::isISOControl) || name.contains("§"))
            throw new IllegalArgumentException("name must be plain text of 1–48 characters");
        String category = yaml.getString("type", "").toUpperCase(Locale.ROOT).replace("-", "_");
        DiscType type;
        try {
            type = category.equals("MIDRANGE") ? DiscType.MID_RANGE : DiscType.valueOf(category);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("type must be putter, midrange or driver");
        }
        DiscModel starter = DiscModel.starter(type);
        DiscAppearance base = starter.appearance();
        var shape = DiscAppearance.Shape.valueOf(yaml.getString("appearance.shape", base.shape().name())
                .toUpperCase(Locale.ROOT).replace("-", "_"));
        var appearance = new DiscAppearance(shape, color(yaml, "plate", base.plate()),
                color(yaml, "rim", base.rim()), color(yaml, "accent", base.accent()));
        return new DiscModel(id, name, type, integer(yaml, "price", 100, 0, 1_000_000),
                integer(yaml, "speed", starter.speed(), 1, 14), integer(yaml, "glide", starter.glide(), 1, 7),
                integer(yaml, "turn", starter.turn(), -5, 2), integer(yaml, "fade", starter.fade(), 0, 5), appearance);
    }

    private static int integer(YamlConfiguration yaml, String key, int fallback, int min, int max) {
        if (!yaml.contains(key)) return fallback;
        int value = yaml.getInt(key);
        if (!yaml.isInt(key) || value < min || value > max)
            throw new IllegalArgumentException(key + " must be an integer from " + min + " to " + max);
        return value;
    }

    private static int color(YamlConfiguration yaml, String key, int fallback) {
        String path = "appearance." + key;
        if (!yaml.contains(path)) return fallback;
        String rgb = yaml.getString(path, "");
        if (!rgb.matches("#?[0-9a-fA-F]{6}")) throw new IllegalArgumentException(path + " must be a quoted hex color");
        return Integer.parseInt(rgb.replace("#", ""), 16);
    }

    public List<DiscModel> models() { return models; }
    public DiscModel find(String id) { return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT)); }
    public CustomDiscAssets assets(DiscModel model) { return customAssets.get(model.id()); }
}
