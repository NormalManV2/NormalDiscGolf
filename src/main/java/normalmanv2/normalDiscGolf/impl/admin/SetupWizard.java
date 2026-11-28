package normalmanv2.normalDiscGolf.impl.admin;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGenerationProfile;
import normalmanv2.normalDiscGolf.impl.manager.round.queue.RoundQueueManager.QueueResult;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

public final class SetupWizard {
    public enum SaveResult { ACTIVE, REVIEW_PENDING, RESTART_REQUIRED, BUSY, INVALID }

    public record Choices(String profile, int holes, String terrain) {
        public Choices withHoles(int value) { return new Choices(profile, value, terrain); }
        public Choices withTerrain(String value) { return new Choices(profile, holes, value); }
    }

    private final NormalDiscGolfPlugin plugin;
    private final AdminSettings settings;
    private boolean verifying;
    private String verifiedUrl;
    private PackVerification.Result lastResult;

    public SetupWizard(NormalDiscGolfPlugin plugin, AdminSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public Choices choices(YamlConfiguration saved) {
        String profile = saved.getString("course.profile", "").strip();
        var active = CourseGenerationProfile.activeSection(saved);
        if (!profile.isEmpty() && active == null)
            throw new IllegalArgumentException("The default profile is missing. Correct course.profile in Server Settings.");
        String baseTerrain = saved.getString("course.terrain", "random");
        String terrain = active == null ? baseTerrain : active.getString("terrain", baseTerrain);
        return new Choices(profile, CourseGenerationProfile.serverDefault(saved).holes(), terrain.strip().toLowerCase(Locale.ROOT));
    }

    public SaveResult save(Choices choices) throws IOException {
        var saved = settings.read();
        ensureProfile(saved, choices);
        String root = root(choices);
        var edits = Map.of(root + "holes", Integer.toString(choices.holes()), root + "terrain", choices.terrain());
        settings.stage(edits);
        var candidate = settings.read();
        var pending = settings.pending(candidate);
        if (pending.stream().anyMatch(settings.schema()::restartOnly)) return SaveResult.RESTART_REQUIRED;
        if (pending.stream().anyMatch(key -> !edits.containsKey(key)
                && edits.keySet().stream().noneMatch(edited -> edited.startsWith(key + ".")))) return SaveResult.REVIEW_PENDING;
        return switch (settings.apply(candidate)) {
            case APPLIED, NOTHING_PENDING -> SaveResult.ACTIVE;
            case INVALID -> SaveResult.INVALID;
            case RESTART_REQUIRED -> SaveResult.RESTART_REQUIRED;
            case BUSY, BACKUP_BUSY -> SaveResult.BUSY;
        };
    }

    public void setPackUrl(String input) throws IOException {
        String url = PackVerification.endpoint(input).toString();
        settings.stage(Map.of("resource-pack.url", url, "resource-pack.serve", "false", "resource-pack.enabled", "true"));
    }

    public String packStatus(String url) {
        if (url.isBlank()) return "Enter the HTTPS URL for the exported ZIP.";
        if (url.equals(verifiedUrl)) {
            if (verifying) return "Checking the HTTPS download…";
            if (lastResult != null) return lastResult.message();
        }
        return "Ready to verify the saved HTTPS URL.";
    }

    public void verify(CommandSender sender, Runnable refresh) throws IOException {
        if (verifying) {
            sender.sendMessage(ChatColor.YELLOW + "A pack verification is already running.");
            return;
        }
        String url = settings.read().getString("resource-pack.url", "").strip();
        if (url.isBlank()) throw new IllegalArgumentException("Set the HTTPS pack URL first.");
        var endpoint = PackVerification.endpoint(url);
        byte[] expected = NDGManager.getInstance().getPacked().getBytes();
        var export = plugin.getDataFolder().toPath().resolve("NormalDiscGolfPack.zip");
        verifying = true;
        verifiedUrl = url;
        lastResult = null;
        sender.sendMessage(ChatColor.AQUA + "Checking the HTTPS download…");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var result = PackVerification.check(endpoint, export, expected);
            Bukkit.getScheduler().runTask(plugin, () -> {
                verifying = false;
                lastResult = result;
                if (!sender.hasPermission(AdminSettings.PERMISSION)) return;
                if (sender instanceof Player player && !player.isOnline()) return;
                sender.sendMessage((result.matches() ? ChatColor.GREEN : ChatColor.YELLOW) + result.message());
                refresh.run();
            });
        });
    }

    public QueueResult testRound(Player player, Choices choices) throws IOException {
        var saved = settings.read();
        ensureProfile(saved, choices);
        String key = root(choices) + "terrain";
        saved.set(key, settings.schema().parse(key, choices.terrain()));
        var profile = CourseGenerationProfile.serverDefault(saved);
        long seed = profile.nextSeed();
        return NDGManager.getInstance().getRoundQueueManager().addSetupTest(player.getUniqueId(), profile.settings(seed), seed);
    }

    private void ensureProfile(YamlConfiguration saved, Choices choices) {
        if (!choices(saved).profile().equals(choices.profile()))
            throw new IllegalArgumentException("The active generation profile changed. Reopen the wizard and choose the defaults again.");
    }

    private static String root(Choices choices) {
        return choices.profile().isEmpty() ? "course." : "course.profiles." + choices.profile() + ".";
    }
}
