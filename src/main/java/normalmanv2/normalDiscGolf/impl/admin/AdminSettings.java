package normalmanv2.normalDiscGolf.impl.admin;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public final class AdminSettings {
    public static final String PERMISSION = "ndg.admin.config";

    public enum ApplyResult { APPLIED, NOTHING_PENDING, INVALID, RESTART_REQUIRED, BUSY, BACKUP_BUSY }

    private record Disk(YamlConfiguration config, byte[] contents) {}

    private final NormalDiscGolfPlugin plugin;
    private final Configuration defaults;
    private final Path file;
    private final SettingsSchema schema;

    public AdminSettings(NormalDiscGolfPlugin plugin) {
        this.plugin = plugin;
        this.defaults = Objects.requireNonNull(plugin.getConfig().getDefaults());
        this.schema = new SettingsSchema(defaults);
        this.file = plugin.getDataFolder().toPath().resolve("config.yml");
    }

    public SettingsSchema schema() { return schema; }

    public static String display(String key, Object value) {
        if (value == null) return "Inherited";
        if (value instanceof String text) return text.isBlank() ? "(empty)"
                : key.equals("resource-pack.url") || key.equals("backups.offsite.url") ? "(configured)" : text;
        return value.toString();
    }

    public YamlConfiguration read() throws IOException { return disk().config(); }

    private Disk disk() throws IOException {
        byte[] contents = Files.readAllBytes(file);
        var yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(new String(contents, StandardCharsets.UTF_8));
        } catch (InvalidConfigurationException invalid) {
            throw new IOException("config.yml contains invalid YAML. Correct the file and check it again.", invalid);
        }
        yaml.setDefaults(defaults);
        return new Disk(yaml, contents);
    }

    public boolean stage(String key, String value) throws IOException {
        return change(key, schema.parse(key, value));
    }

    public boolean stage(Map<String, String> edits) throws IOException {
        var parsed = new LinkedHashMap<String, Object>();
        edits.forEach((key, value) -> parsed.put(key, schema.parse(key, value)));
        return change(parsed);
    }

    public boolean reset(String key) throws IOException {
        if (!schema.known(key)) throw new IllegalArgumentException("Unknown setting " + key + ".");
        return change(key, null);
    }

    private boolean change(String key, Object value) throws IOException {
        return change(java.util.Collections.singletonMap(key, value));
    }

    private boolean change(Map<String, Object> edits) throws IOException {
        var snapshot = disk();
        var yaml = snapshot.config();
        boolean changed = false;
        for (var edit : edits.entrySet()) changed |= set(yaml, edit.getKey(), edit.getValue());
        if (!changed) return false;
        if (!Arrays.equals(snapshot.contents(), Files.readAllBytes(file)))
            throw new IOException("config.yml changed during the edit. Review the file and try again.");
        write(file.resolveSibling("config.yml.bak"), snapshot.contents());
        write(file, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
        return true;
    }

    private static boolean set(YamlConfiguration yaml, String key, Object value) {
        boolean explicit = yaml.contains(key, true);
        if (value == null ? !explicit : explicit && Objects.equals(yaml.get(key), value)) return false;
        for (int dot = key.indexOf('.'); dot >= 0; dot = key.indexOf('.', dot + 1)) {
            String section = key.substring(0, dot);
            if (yaml.contains(section, true) && !yaml.isConfigurationSection(section))
                throw new IllegalArgumentException("Correct " + section + " into a YAML section before editing " + key + ".");
        }
        yaml.set(key, value);
        return true;
    }

    public List<String> pending(ConfigurationSection candidate) {
        var keys = new TreeSet<String>(candidate.getKeys(true));
        var current = plugin.getConfig();
        keys.addAll(current.getKeys(true));
        return keys.stream().filter(key -> {
            boolean explicitChanged = candidate.contains(key, true) != current.contains(key, true);
            if (candidate.get(key) instanceof ConfigurationSection || current.get(key) instanceof ConfigurationSection)
                return explicitChanged;
            // The presence of an explicit preset setting can affect which lobbies are offered.
            return explicitChanged || !Objects.equals(candidate.get(key), current.get(key));
        }).toList();
    }

    public ApplyResult apply(YamlConfiguration candidate) {
        if (!schema.check(candidate).valid()) return ApplyResult.INVALID;
        var pending = pending(candidate);
        if (pending.isEmpty()) return ApplyResult.NOTHING_PENDING;
        if (pending.stream().anyMatch(schema::restartOnly)) return ApplyResult.RESTART_REQUIRED;
        if (busy()) return ApplyResult.BUSY;
        boolean updateBackups = pending.stream().anyMatch(key -> key.equals("backups") || key.startsWith("backups."));
        if (updateBackups && plugin.backupTasks().running()) return ApplyResult.BACKUP_BUSY;
        var current = plugin.getConfig();
        for (String key : current.getKeys(false)) current.set(key, null);
        copy(candidate, current);
        if (updateBackups) plugin.backupTasks().configure();
        return ApplyResult.APPLIED;
    }

    private static void copy(ConfigurationSection source, ConfigurationSection target) {
        for (String key : source.getKeys(false)) {
            Object value = source.get(key);
            if (value instanceof ConfigurationSection child) copy(child, target.createSection(key));
            else target.set(key, value);
        }
    }

    private static boolean busy() {
        var manager = NDGManager.getInstance();
        var rounds = manager.getRoundHandler();
        var queue = manager.getRoundQueueManager();
        if (rounds.getActiveRounds().stream().anyMatch(round -> !round.isOver()) || !rounds.getPreparingRounds().isEmpty()
                || !queue.getQueuedRounds().isEmpty()) return true;
        return Arrays.stream(Division.values()).anyMatch(division -> !queue.getPlayerQueue(division).isEmpty());
    }

    private static void write(Path destination, byte[] contents) throws IOException {
        Path temp = Files.createTempFile(destination.getParent(), "config-", ".tmp");
        try {
            Files.write(temp, contents);
            try {
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
