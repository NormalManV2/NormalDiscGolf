package normalmanv2.normalDiscGolf.impl.admin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.util.regex.Pattern;

/** File-only backup operations. The command flushes player data before calling this off the server thread. */
public final class DataBackups {
    private static final LinkOption NOFOLLOW = LinkOption.NOFOLLOW_LINKS;
    private static final String MANIFEST = "ndg-backup.properties";
    static final int MANIFEST_LIMIT = 8 * 1024 * 1024;
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").withResolverStyle(ResolverStyle.STRICT).withZone(ZoneOffset.UTC);
    private static final String MANUAL_PREFIX = "ndg-";
    private static final String AUTOMATIC_PREFIX = "ndg-auto-";
    private static final Pattern AUTOMATIC_ARCHIVE = Pattern.compile("ndg-auto-(\\d{8}-\\d{6})-[0-9a-f]{8}\\.zip");
    private static final Set<String> DIRECTORIES = Set.of("player-profiles", "course-library", "custom-courses",
            "custom-discs", "daily-courses", "obstacles");
    private static final Set<String> ROOT_FILES = Set.of("config.yml", "clubhouse-site.yml", "ranked-seasons.yml", "weekly-tournaments.yml");
    private static final String UUID_TEXT = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}";
    private static final java.util.regex.Pattern SAVED_RECIPE =
            java.util.regex.Pattern.compile("course-library/" + UUID_TEXT + "/" + UUID_TEXT + "\\.yml");
    private final Path data;
    private final Path backups;
    private final String pluginVersion;

    public enum RestoreAction { REPLACE, ADD, KEEP_CURRENT, IDENTICAL }

    public record Created(String filename, int files, int checkedCourses, int flaggedCourses) {}
    public record RestoreRow(RestoreAction action, String file) {}
    public record RestorePreview(String fromVersion, List<RestoreRow> rows, String fingerprint) {}
    public record CourseRow(String file, boolean compatible, String detail) {}
    public record UpgradeCheck(String baselineVersion, String currentVersion, List<CourseRow> rows) {}

    private record Stamp(long bytes, FileTime modified, Object key) {}
    private record Manifest(String pluginVersion, Map<String, String> files, Map<String, String> layouts) {}
    record RestoreChange(String before, String after) {}
    record RestorePlan(RestorePreview preview, Map<String, RestoreChange> changes) {}

    public DataBackups(Path data, String pluginVersion) {
        this.data = data.toAbsolutePath().normalize();
        this.backups = this.data.resolve("backups");
        this.pluginVersion = pluginVersion;
    }

    public Created create() throws IOException {
        return create(MANUAL_PREFIX);
    }

    Created createAutomatic() throws IOException {
        return create(AUTOMATIC_PREFIX);
    }

    private Created create(String prefix) throws IOException {
        var files = inventory();
        Files.createDirectories(backups);
        if (!Files.isDirectory(backups, NOFOLLOW)) throw new IOException("The backup directory must be a regular directory.");
        String filename = prefix + TIMESTAMP.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 8) + ".zip";
        Path temp = Files.createTempFile(backups, "backup-", ".tmp");
        var manifest = new Properties();
        manifest.setProperty("format", "1");
        manifest.setProperty("plugin-version", pluginVersion);
        int checked = 0;
        int flagged = 0;
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(temp))) {
                for (var file : files.entrySet()) {
                    String name = file.getKey();
                    Path path = data.resolve(name);
                    zip.putNextEntry(new ZipEntry(name));
                    try (var in = Files.newInputStream(path, NOFOLLOW)) {
                        manifest.setProperty("file." + name, transfer(in, zip, file.getValue().bytes()));
                    }
                    zip.closeEntry();
                    if (isRecipe(name)) {
                        var proof = verifyRecipe(path);
                        if (proof.passed()) {
                            manifest.setProperty("layout." + name, proof.signature());
                            checked++;
                        } else flagged++;
                    }
                }
                var metadata = new ByteArrayOutputStream();
                manifest.store(metadata, "NormalDiscGolf backup");
                if (metadata.size() > MANIFEST_LIMIT) throw new IOException("The backup manifest exceeds the supported size.");
                zip.putNextEntry(new ZipEntry(MANIFEST));
                metadata.writeTo(zip);
                zip.closeEntry();
            }
            if (!files.equals(inventory()))
                throw new IOException("NDG data changed during backup. Try again when the server is quieter.");
            Path destination = backups.resolve(filename);
            try {
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temp, destination);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return new Created(filename, files.size(), checked, flagged);
    }

    public List<String> list() throws IOException {
        if (Files.notExists(backups, NOFOLLOW)) return List.of();
        if (!Files.isDirectory(backups, NOFOLLOW)) throw new IOException("The backup directory must be a regular directory.");
        try (var entries = Files.list(backups)) {
            return entries.filter(path -> Files.isRegularFile(path, NOFOLLOW))
                    .map(path -> path.getFileName().toString()).filter(DataBackups::archiveName)
                    .sorted(Comparator.comparing((String name) -> name.startsWith(AUTOMATIC_PREFIX)
                            ? MANUAL_PREFIX + name.substring(AUTOMATIC_PREFIX.length()) : name).reversed())
                    .toList();
        }
    }

    Instant lastAutomatic() throws IOException {
        return list().stream().map(DataBackups::automaticTime).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    int pruneAutomatic(String fresh, int keep, String restoreSource) throws IOException {
        manifest(fresh);
        var candidates = new ArrayList<>(list().stream().filter(name -> automaticTime(name) != null).toList());
        candidates.remove(fresh);
        candidates.add(0, fresh);
        int removed = 0;
        for (int i = keep; i < candidates.size(); i++) {
            String name = candidates.get(i);
            Path file = backups.resolve(name);
            if (!name.equals(restoreSource) && Files.isRegularFile(file, NOFOLLOW) && Files.deleteIfExists(file)) removed++;
        }
        return removed;
    }

    private static Instant automaticTime(String filename) {
        var parsed = AUTOMATIC_ARCHIVE.matcher(filename);
        if (!parsed.matches()) return null;
        try {
            return LocalDateTime.parse(parsed.group(1), TIMESTAMP).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    public RestorePreview preview(String filename) throws IOException {
        return plan(filename).preview();
    }

    RestorePlan plan(String filename) throws IOException {
        var archive = manifest(filename);
        var current = inventory();
        var paths = new TreeSet<>(archive.files().keySet());
        paths.addAll(current.keySet());
        var rows = new ArrayList<RestoreRow>();
        var changes = new TreeMap<String, RestoreChange>();
        var fingerprint = CourseUpgradeCheck.sha256();
        fingerprint.update((archive.pluginVersion() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (String path : paths) {
            var savedHash = archive.files().get(path);
            var live = current.get(path);
            String liveHash = null;
            RestoreAction action;
            if (savedHash == null) action = RestoreAction.KEEP_CURRENT;
            else if (live == null) action = RestoreAction.ADD;
            else {
                try (var in = Files.newInputStream(data.resolve(path), NOFOLLOW)) {
                    liveHash = transfer(in, null, live.bytes());
                    action = savedHash.equals(liveHash) ? RestoreAction.IDENTICAL : RestoreAction.REPLACE;
                }
            }
            if (action == RestoreAction.REPLACE || action == RestoreAction.ADD) {
                changes.put(path, new RestoreChange(liveHash, savedHash));
                fingerprint.update((path + "\n" + liveHash + "\n" + savedHash + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            rows.add(new RestoreRow(action, path));
        }
        if (!current.equals(inventory()))
            throw new IOException("NDG data changed during the preview. Open a fresh preview.");
        rows.sort(java.util.Comparator.comparing(RestoreRow::action).thenComparing(RestoreRow::file));
        return new RestorePlan(new RestorePreview(archive.pluginVersion(), List.copyOf(rows),
                HexFormat.of().formatHex(fingerprint.digest())), Map.copyOf(changes));
    }

    public UpgradeCheck check(String filename) throws IOException {
        var baseline = filename == null ? null : manifest(filename);
        var current = inventory();
        var recipes = new TreeSet<String>();
        current.keySet().stream().filter(DataBackups::isRecipe).forEach(recipes::add);
        if (baseline != null) baseline.files().keySet().stream().filter(DataBackups::isRecipe).forEach(recipes::add);
        var rows = new ArrayList<CourseRow>();
        for (String name : recipes) {
            if (!current.containsKey(name)) {
                rows.add(new CourseRow(name, false, "This archive's saved layout is missing on the server."));
                continue;
            }
            var regenerated = verifyRecipe(data.resolve(name));
            if (!regenerated.passed()) {
                rows.add(new CourseRow(name, false, regenerated.problem()));
                continue;
            }
            String expected = baseline == null ? null : baseline.layouts().get(name);
            if (expected == null) {
                boolean unverified = baseline != null && baseline.files().containsKey(name);
                rows.add(new CourseRow(name, !unverified, unverified
                        ? "Regenerated. The archive has no verified baseline for this course." : "Regenerated."));
            } else if (expected.equals(regenerated.signature())) {
                rows.add(new CourseRow(name, true, "Matches backup layout."));
            } else {
                rows.add(new CourseRow(name, false, "Regenerated anchors, routes, or sampled terrain differ from the backup."));
            }
        }
        if (!recipeStamps(current).equals(recipeStamps(inventory())))
            throw new IOException("Saved courses changed during the check. Run it again.");
        rows.sort(java.util.Comparator.comparing(CourseRow::compatible).thenComparing(CourseRow::file));
        return new UpgradeCheck(baseline == null ? null : baseline.pluginVersion(), pluginVersion, List.copyOf(rows));
    }

    private static Map<String, Stamp> recipeStamps(Map<String, Stamp> files) {
        var recipes = new TreeMap<String, Stamp>();
        files.forEach((path, stamp) -> { if (isRecipe(path)) recipes.put(path, stamp); });
        return recipes;
    }

    private Manifest manifest(String filename) throws IOException {
        return inspect(filename, null, Map.of());
    }

    Path verifiedArchive(String filename) throws IOException {
        manifest(filename);
        return backups.resolve(filename);
    }

    Path downloadTemp(String filename) throws IOException {
        Path destination = downloadDestination(filename);
        return Files.createTempFile(destination.getParent(), "download-", ".tmp");
    }

    void importDownload(String filename, Path temp) throws IOException {
        Path destination = downloadDestination(filename);
        inspect(temp, null, Map.of());
        Files.move(temp, destination);
    }

    private Path downloadDestination(String filename) throws IOException {
        if (!archiveName(filename)) throw new IOException("Enter a backup ZIP filename.");
        Files.createDirectories(backups);
        Path destination = backups.resolve(filename);
        if (!Files.isDirectory(backups, NOFOLLOW) || !Files.notExists(destination, NOFOLLOW))
            throw new IOException("The local backup already exists or the backup directory is unavailable.");
        return destination;
    }

    void verifyFiles(String filename, Map<String, String> expected) throws IOException {
        inspect(filename, null, expected);
    }

    void stage(String filename, Path target, Map<String, String> expected) throws IOException {
        inspect(filename, target, expected);
    }

    private Manifest inspect(String filename, Path target, Map<String, String> expected) throws IOException {
        if (!archiveName(filename)) throw new IOException("Choose a ZIP filename from /ndg admin backup list.");
        Path path = backups.resolve(filename);
        if (!Files.isDirectory(backups, NOFOLLOW) || !Files.isRegularFile(path, NOFOLLOW))
            throw new IOException("Backup " + filename + " is unavailable.");
        return inspect(path, target, expected);
    }

    private Manifest inspect(Path path, Path target, Map<String, String> expected) throws IOException {
        var properties = new Properties();
        var files = new TreeMap<String, String>();
        var layouts = new TreeMap<String, String>();
        try (var zip = new ZipFile(path.toFile())) {
            ZipEntry index = zip.getEntry(MANIFEST);
            if (index == null || index.getSize() > MANIFEST_LIMIT) throw new IOException("Backup manifest is missing or too large.");
            try (var in = zip.getInputStream(index)) {
                byte[] bytes = in.readNBytes(MANIFEST_LIMIT + 1);
                if (bytes.length > MANIFEST_LIMIT) throw new IOException("Backup manifest is too large.");
                properties.load(new ByteArrayInputStream(bytes));
            }
            String version = properties.getProperty("plugin-version", "");
            if (!"1".equals(properties.getProperty("format")) || !version.matches("[A-Za-z0-9.+_-]{1,80}"))
                throw new IOException("Unsupported backup manifest.");
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith("file.")) files.put(key.substring(5), properties.getProperty(key));
                if (key.startsWith("layout.")) layouts.put(key.substring(7), properties.getProperty(key));
            }
            for (var entry : expected.entrySet()) if (!entry.getValue().equals(files.get(entry.getKey())))
                throw new IOException("Backup contents changed for " + entry.getKey() + ".");
            for (var proof : layouts.entrySet()) if (!isRecipe(proof.getKey()) || !files.containsKey(proof.getKey())
                    || !proof.getValue().matches("[0-9a-f]{64}")) throw new IOException("Invalid course baseline in the backup.");
            if (zip.size() != files.size() + 1) throw new IOException("Backup contents differ from the manifest.");
            var names = new HashSet<String>();
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
                var item = entries.nextElement();
                String name = item.getName();
                if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate entry in the backup.");
                if (name.equals(MANIFEST)) continue;
                if (item.isDirectory() || !allowed(name) || !files.containsKey(name)) throw new IOException("Unexpected file in the backup.");
                try (var in = zip.getInputStream(item);
                     var out = target != null && expected.containsKey(name) ? stagedFile(target.resolve(name)) : null) {
                    if (!files.get(name).equals(transfer(in, out, item.getSize())))
                        throw new IOException("Backup contents failed the checksum for " + name + ".");
                }
            }
            return new Manifest(version, files, layouts);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid backup manifest.", invalid);
        }
    }

    private static OutputStream stagedFile(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.newOutputStream(path, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
    }

    private Map<String, Stamp> inventory() throws IOException {
        var result = new TreeMap<String, Stamp>();
        for (String root : ROOT_FILES) {
            Path path = data.resolve(root);
            if (!Files.notExists(path, NOFOLLOW)) result.put(root, stamp(path));
        }
        for (String root : DIRECTORIES) {
            Path folder = data.resolve(root);
            if (Files.notExists(folder, NOFOLLOW)) continue;
            if (!Files.isDirectory(folder, NOFOLLOW)) throw new IOException(root + " must be a regular directory.");
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.toList()) {
                    if (Files.isDirectory(path, NOFOLLOW) || path.getFileName().toString().endsWith(".tmp")) continue;
                    String relative = data.relativize(path).toString().replace('\\', '/');
                    if (!allowed(relative)) throw new IOException("A managed data file has a name that cannot be backed up safely.");
                    result.put(relative, stamp(path));
                }
            } catch (java.io.UncheckedIOException failure) {
                throw failure.getCause();
            }
        }
        var caseInsensitive = new HashSet<String>();
        for (String name : result.keySet()) if (!caseInsensitive.add(name.toLowerCase(Locale.ROOT)))
            throw new IOException("Managed data files differ only in capitalization.");
        return result;
    }

    private static Stamp stamp(Path path) throws IOException {
        var info = Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW);
        if (!info.isRegularFile()) throw new IOException("Managed data must be a regular file: " + path.getFileName());
        return new Stamp(info.size(), info.lastModifiedTime(), info.fileKey());
    }

    static String transfer(InputStream in, OutputStream out, long size) throws IOException {
        if (size < 0) throw new IOException("Invalid file size in the backup.");
        var digest = CourseUpgradeCheck.sha256();
        byte[] buffer = new byte[16_384];
        long remaining = size;
        while (remaining > 0) {
            int count = in.read(buffer, 0, (int) Math.min(remaining, buffer.length));
            if (count < 0) throw new IOException("A data file changed length during the operation.");
            digest.update(buffer, 0, count);
            if (out != null) out.write(buffer, 0, count);
            remaining -= count;
        }
        if (in.read() != -1) throw new IOException("A data file changed length during the operation.");
        return size + ":" + HexFormat.of().formatHex(digest.digest());
    }

    private static CourseUpgradeCheck.Result verifyRecipe(Path path) throws IOException {
        try (var in = Files.newBufferedReader(path)) {
            return CourseUpgradeCheck.verify(path.getFileName().toString(), in);
        }
    }

    private static boolean isRecipe(String name) { return SAVED_RECIPE.matcher(name).matches(); }

    static boolean archiveName(String name) { return name != null && name.matches("[A-Za-z0-9_-]{1,80}\\.zip"); }

    static boolean allowed(String path) {
        if (ROOT_FILES.contains(path)) return true;
        String[] parts = path.split("/", -1);
        if (parts.length < 2 || !DIRECTORIES.contains(parts[0])) return false;
        for (String part : parts) if (!part.matches("[A-Za-z0-9_ .-]{1,180}") || part.endsWith(".") || part.endsWith(" ")
                || part.matches("(?i)(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9]) *(\\..*)?")) return false;
        return true;
    }
}
