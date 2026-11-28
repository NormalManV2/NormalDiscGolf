package normalmanv2.normalDiscGolf.impl.course.library;

import normalmanv2.normalDiscGolf.impl.course.grid.CourseGenerationSettings;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseTerrain;
import normalmanv2.normalDiscGolf.impl.course.grid.HoleArchetype;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.round.RoundFormat;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class FileCourseLibraryStore implements CourseLibraryStore {
    private static final int SCHEMA_VERSION = 1;
    private final Path directory;
    private final Logger logger;
    private final FileCourseShares shares;

    public FileCourseLibraryStore(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
        this.shares = new FileCourseShares(directory.resolve("shares"), logger);
    }

    @Override public synchronized boolean save(UUID owner, CourseLibraryEntry entry) throws IOException {
        Path target = entry(owner, entry.course().id());
        if (Files.exists(target)) {
            read(target);
            return false;
        }
        writeAtomically(target, encode(entry).saveToString());
        return true;
    }

    @Override public synchronized boolean rename(UUID owner, UUID course, String name) throws IOException {
        String validName = CourseLibraryEntry.validateName(name);
        var saved = find(owner, course);
        if (saved.isEmpty()) return false;
        var renamed = new CourseLibraryEntry(saved.get().course(), validName, saved.get().favorite());
        writeAtomically(entry(owner, course), encode(renamed).saveToString());
        return true;
    }

    @Override public synchronized Optional<CourseLibraryEntry> setFavorite(UUID owner, UUID course, boolean favorite) throws IOException {
        var saved = find(owner, course);
        if (saved.isEmpty() || saved.get().favorite() == favorite) return saved;
        var updated = saved.get().withFavorite(favorite);
        writeAtomically(entry(owner, course), encode(updated).saveToString());
        return Optional.of(updated);
    }

    static void writeAtomically(Path target, String data) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "entry-", ".tmp");
        try {
            Files.writeString(temp, data);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override public List<CourseLibraryEntry> list(UUID owner) throws IOException {
        Path folder = directory.resolve(owner.toString());
        if (Files.notExists(folder)) return List.of();
        var courses = new ArrayList<CourseLibraryEntry>();
        try (var files = Files.list(folder)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".yml")).sorted().toList()) {
                try {
                    courses.add(read(file));
                } catch (IOException invalid) {
                    logger.warning("Could not load Course Library entry " + file.getFileName() + ": " + invalid.getMessage());
                }
            }
        }
        return List.copyOf(courses);
    }

    @Override public Optional<CourseLibraryEntry> find(UUID owner, UUID course) throws IOException {
        Path file = entry(owner, course);
        return Files.notExists(file) ? Optional.empty() : Optional.of(read(file));
    }

    @Override public synchronized Optional<String> shareCode(UUID owner, UUID course) throws IOException {
        return shares.findCode(owner, course);
    }

    @Override public synchronized Optional<String> share(UUID owner, UUID course) throws IOException {
        return find(owner, course).isEmpty() ? Optional.empty() : Optional.of(shares.publish(owner, course));
    }

    @Override public synchronized boolean revokeShare(UUID owner, UUID course) throws IOException {
        return shares.revoke(owner, course);
    }

    @Override public synchronized Optional<CourseLibraryEntry> resolveShare(String code) throws IOException {
        var share = shares.resolve(code);
        return share == null ? Optional.empty() : find(share.owner(), share.course()).map(entry -> entry.withFavorite(false));
    }

    @Override public synchronized List<FeaturedCourse> featuredCourses() throws IOException {
        var featured = new ArrayList<FeaturedCourse>();
        for (String code : shares.featuredCodes()) {
            try {
                resolveFeatured(code).ifPresent(entry -> featured.add(new FeaturedCourse(code, entry)));
            } catch (IOException invalid) {
                logger.warning("Could not load featured course " + code + ": " + invalid.getMessage());
            }
        }
        featured.sort(Comparator.comparing((FeaturedCourse course) -> course.entry().displayName(),
                String.CASE_INSENSITIVE_ORDER).thenComparing(FeaturedCourse::code));
        return List.copyOf(featured);
    }

    @Override public synchronized Optional<CourseLibraryEntry> resolveFeatured(String code) throws IOException {
        var share = shares.resolve(code);
        return share == null || !share.featured() ? Optional.empty()
                : find(share.owner(), share.course()).map(entry -> entry.withFavorite(false));
    }

    @Override public synchronized PinResult pin(String code) throws IOException {
        var share = shares.resolve(code);
        if (share == null || find(share.owner(), share.course()).isEmpty()) return PinResult.UNAVAILABLE;
        if (share.featured()) return PinResult.ALREADY_PINNED;
        shares.setFeatured(code, share, true);
        return PinResult.PINNED;
    }

    @Override public synchronized boolean unpin(String code) throws IOException {
        var share = shares.resolve(code);
        if (share == null || !share.featured()) return false;
        shares.setFeatured(code, share, false);
        return true;
    }

    private Path entry(UUID owner, UUID course) {
        return directory.resolve(owner.toString()).resolve(course + ".yml");
    }

    private static YamlConfiguration encode(CourseLibraryEntry entry) {
        var course = entry.course();
        var yaml = new YamlConfiguration();
        yaml.set("schema", SCHEMA_VERSION);
        yaml.set("generator", CourseGrid.GENERATOR_VERSION);
        yaml.set("seed", course.seed());
        yaml.set("holes", course.holes());
        yaml.set("grid-width", course.width());
        yaml.set("grid-depth", course.depth());
        yaml.set("tile-types", course.tileTypes().stream().map(Enum::name).toList());
        yaml.set("layout", course.layout().name());
        yaml.set("terrain", course.settings().terrain().name());
        yaml.set("elevation", course.settings().elevation());
        yaml.set("fairway-width", course.settings().fairwayWidth());
        yaml.set("obstacle-density", course.settings().obstacleDensity());
        yaml.set("hazard-density", course.settings().hazardDensity());
        yaml.set("archetypes", course.settings().archetypes().stream().map(Enum::name).toList());
        yaml.set("replay-format", course.replayFormat().name());
        yaml.set("preset", course.preset());
        yaml.set("par", course.par());
        yaml.set("difficulty", course.difficulty());
        yaml.set("name", entry.name());
        if (entry.favorite()) yaml.set("favorite", true);
        return yaml;
    }

    private static CourseLibraryEntry read(Path file) throws IOException {
        try (var reader = Files.newBufferedReader(file)) {
            return readRecipe(file.getFileName().toString(), reader);
        }
    }

    public static CourseLibraryEntry readRecipe(String filename, Reader reader) throws IOException {
        var yaml = new YamlConfiguration();
        try {
            yaml.load(reader);
            if (yaml.getInt("schema") != SCHEMA_VERSION || yaml.getInt("generator") != CourseGrid.GENERATOR_VERSION)
                throw new IOException("Unsupported course file or generator version");
            if (!yaml.isLong("seed") && !yaml.isInt("seed")) throw new IOException("Invalid course seed");
            var settings = new CourseGenerationSettings(CourseTerrain.valueOf(yaml.getString("terrain", "")),
                    yaml.getInt("elevation", -1), yaml.getInt("fairway-width", -1),
                    yaml.getDouble("obstacle-density", Double.NaN), yaml.getDouble("hazard-density", Double.NaN),
                    yaml.getStringList("archetypes").stream().map(HoleArchetype::valueOf).toList());
            var course = new SavedCourse(yaml.getLong("seed"), yaml.getInt("holes"), yaml.getInt("grid-width"),
                    yaml.getInt("grid-depth"), yaml.getStringList("tile-types").stream().map(TileTypes::valueOf).toList(),
                    CourseGrid.Layout.valueOf(yaml.getString("layout", "")), settings,
                    RoundFormat.valueOf(yaml.getString("replay-format", "")), yaml.getString("preset"),
                    yaml.getInt("par"), yaml.getInt("difficulty", -1));
            if (!filename.equals(course.id() + ".yml")) throw new IOException("Course recipe does not match its ID");
            return new CourseLibraryEntry(course, yaml.getString("name"), yaml.getBoolean("favorite"));
        } catch (InvalidConfigurationException | IllegalArgumentException invalid) {
            throw new IOException("Invalid course data", invalid);
        }
    }
}
