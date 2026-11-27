package normalmanv2.normalDiscGolf.impl.course.custom;

import normalmanv2.normalDiscGolf.impl.course.custom.CustomCourse.Hole;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid.GridPoint;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.logging.Logger;

public final class CustomCourseStore {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");
    private final Path directory;
    private final Logger logger;
    private final Map<String, CustomCourse> courses = new TreeMap<>();
    private final Map<UUID, Draft> drafts = new java.util.HashMap<>();

    public CustomCourseStore(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
        load();
    }

    public static boolean validId(String id) { return ID.matcher(id).matches(); }
    public CustomCourse get(String id) { return courses.get(id); }
    public Collection<CustomCourse> courses() { return java.util.Collections.unmodifiableCollection(courses.values()); }
    public Draft draft(UUID editor) { return drafts.get(editor); }
    public void openDraft(UUID editor, Draft draft) { drafts.put(editor, draft); }
    public void closeDraft(UUID editor) { drafts.remove(editor); }

    public boolean save(CustomCourse course, CustomCourse previous) throws IOException {
        if (!validId(course.id())) throw new IllegalArgumentException("Invalid course ID");
        if (get(course.id()) != previous) return false;
        var yaml = new YamlConfiguration();
        yaml.set("version", 1);
        yaml.set("name", course.name());
        yaml.set("owner", course.owner().toString());
        yaml.set("world-id", course.worldId().toString());
        yaml.set("world-name", course.worldName());
        course.layout().forEach((index, hole) -> {
            String path = "holes." + (index + 1);
            writePoint(yaml, path + ".tee", hole.tee());
            writePoint(yaml, path + ".basket", hole.basket());
            yaml.set(path + ".par", hole.par());
        });
        Files.createDirectories(directory);
        Path file = directory.resolve(course.id() + ".yml");
        Path temporary = Files.createTempFile(directory, course.id() + "-", ".tmp");
        try {
            yaml.save(temporary.toFile());
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        courses.put(course.id(), course);
        return true;
    }

    private static void writePoint(YamlConfiguration yaml, String path, GridPoint point) {
        yaml.set(path + ".x", point.x());
        yaml.set(path + ".y", point.y());
        yaml.set(path + ".z", point.z());
    }

    private void load() {
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            files.filter(file -> file.getFileName().toString().endsWith(".yml"))
                    .filter(file -> Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)).forEach(this::read);
        } catch (IOException failure) {
            logger.warning("Could not list custom courses: " + failure.getMessage());
        }
    }

    private void read(Path file) {
        String filename = file.getFileName().toString();
        String id = filename.substring(0, filename.length() - 4);
        if (!validId(id)) return;
        try {
            var yaml = new YamlConfiguration();
            yaml.load(file.toFile());
            if (yaml.getInt("version") != 1) throw new IllegalArgumentException("Unsupported course version");
            var holes = yaml.getConfigurationSection("holes");
            if (holes == null || holes.getKeys(false).isEmpty() || holes.getKeys(false).size() > CustomCourse.MAX_HOLES)
                throw new IllegalArgumentException("Invalid hole count");
            Map<Integer, Hole> layout = new TreeMap<>();
            for (int hole = 1; hole <= holes.getKeys(false).size(); hole++) {
                var current = holes.getConfigurationSection(Integer.toString(hole));
                if (current == null || current.getInt("par") < 2 || current.getInt("par") > 6)
                    throw new IllegalArgumentException("Invalid hole " + hole);
                layout.put(hole - 1, new Hole(readPoint(current, "tee"), readPoint(current, "basket"), current.getInt("par")));
            }
            String name = yaml.getString("name", id);
            courses.put(id, new CustomCourse(id, name, UUID.fromString(yaml.getString("owner", "")),
                    UUID.fromString(yaml.getString("world-id", "")), yaml.getString("world-name", ""), layout));
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException | IllegalArgumentException failure) {
            logger.warning("Could not load custom course " + id + ": " + failure.getMessage());
        }
    }

    private static GridPoint readPoint(ConfigurationSection config, String path) {
        if (!config.isInt(path + ".x") || !config.isInt(path + ".y") || !config.isInt(path + ".z"))
            throw new IllegalArgumentException("Incomplete " + path);
        return new GridPoint(config.getInt(path + ".x"), config.getInt(path + ".y"), config.getInt(path + ".z"));
    }

    public static final class Draft {
        private final String id;
        private final String name;
        private final UUID owner;
        private final UUID worldId;
        private final String worldName;
        private final CustomCourse previous;
        private final Map<Integer, Hole> layout = new TreeMap<>();

        public Draft(String id, String name, UUID owner, World world) {
            this(id, name, owner, world.getUID(), world.getName(), null);
        }

        public Draft(CustomCourse course) {
            this(course.id(), course.name(), course.owner(), course.worldId(), course.worldName(), course);
            layout.putAll(course.layout());
        }

        private Draft(String id, String name, UUID owner, UUID worldId, String worldName, CustomCourse previous) {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.worldId = worldId;
            this.worldName = worldName;
            this.previous = previous;
        }

        public String id() { return id; }
        public UUID worldId() { return worldId; }
        public CustomCourse previous() { return previous; }
        public Hole hole(int index) { return layout.getOrDefault(index, new Hole(null, null, null)); }
        public void set(int index, Hole hole) { layout.put(index, hole); }
        public boolean remove(int index) { return layout.remove(index) != null; }
        public CustomCourse snapshot() { return new CustomCourse(id, name, owner, worldId, worldName, layout); }
    }
}
