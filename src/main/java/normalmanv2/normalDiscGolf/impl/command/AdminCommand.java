package normalmanv2.normalDiscGolf.impl.command;

import normalmanv2.normalDiscGolf.impl.util.Bounds;
import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.admin.AdminSettings;
import normalmanv2.normalDiscGolf.impl.admin.SettingsSchema;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGenerationProfile;
import normalmanv2.normalDiscGolf.impl.gui.AdminSettingsMenu;
import normalmanv2.normalDiscGolf.impl.gui.SetupWizardMenu;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

final class AdminCommand {
    private static final List<String> ACTIONS = List.of("wizard", "backup", "upgrade", "status", "check", "list", "get", "set", "reset", "apply");

    private AdminCommand() {}

    static void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(AdminSettings.PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to manage NormalDiscGolf settings.");
            return;
        }
        var plugin = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class);
        var settings = plugin.adminSettings();
        try {
            if (args.length == 1 && sender instanceof Player player) {
                AdminSettingsMenu.open(player, settings);
                return;
            }
            String action = args.length == 1 ? "status" : args[1].toLowerCase(Locale.ROOT);
            switch (action) {
                case "wizard" -> wizard(sender, plugin, args);
                case "backup" -> AdminBackupCommand.backup(sender, plugin, args);
                case "upgrade" -> AdminBackupCommand.upgrade(sender, plugin, args);
                case "set" -> {
                    if (args.length < 4) throw new IllegalArgumentException("Use /ndg admin set <setting> <value>. Use reset to remove an override.");
                    boolean changed = settings.stage(args[2], String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                    staged(sender, settings, args[2], changed);
                }
                case "reset" -> {
                    requireCount(args, 3);
                    staged(sender, settings, args[2], settings.reset(args[2]));
                }
                case "get" -> {
                    requireCount(args, 3);
                    String key = args[2];
                    if (!settings.schema().known(key)) throw new IllegalArgumentException("Unknown setting " + key + ".");
                    sender.sendMessage(ChatColor.AQUA + key);
                    sender.sendMessage(ChatColor.GRAY + "Active " + AdminSettings.display(key, plugin.getConfig().get(key))
                            + " • Saved " + AdminSettings.display(key, settings.read().get(key)));
                }
                case "list" -> {
                    if (args.length > 4) throw new IllegalArgumentException("Use /ndg admin list [prefix] [page].");
                    var candidate = settings.read();
                    String prefix = args.length >= 3 ? args[2] : "";
                    int page = args.length == 4 ? Integer.parseInt(args[3]) : 1;
                    var keys = settings.schema().keys(candidate).stream().filter(key -> key.startsWith(prefix)).toList();
                    int pages = Math.max(1, Bounds.ceilDiv(keys.size(), 12));
                    if (page < 1 || page > pages) throw new IllegalArgumentException("Choose a page from 1 to " + pages + ".");
                    sender.sendMessage(ChatColor.GOLD + "NormalDiscGolf settings • Page " + page + "/" + pages);
                    if (keys.isEmpty()) sender.sendMessage(ChatColor.YELLOW + "No settings match that prefix.");
                    for (String key : keys.subList((page - 1) * 12, Math.min(page * 12, keys.size())))
                        sender.sendMessage(ChatColor.GRAY + key + " = " + AdminSettings.display(key, candidate.get(key)));
                }
                case "check" -> {
                    requireCount(args, 2);
                    showCheck(sender, settings.schema().check(settings.read()));
                }
                case "apply" -> {
                    requireCount(args, 2);
                    var candidate = settings.read();
                    switch (settings.apply(candidate)) {
                        case APPLIED -> sender.sendMessage(ChatColor.GREEN + "Saved settings are now active.");
                        case NOTHING_PENDING -> sender.sendMessage(ChatColor.GRAY + "The saved settings are already active.");
                        case INVALID -> showCheck(sender, settings.schema().check(candidate));
                        case RESTART_REQUIRED -> sender.sendMessage(ChatColor.YELLOW + "Saved changes include startup settings. Restart the server to apply them together.");
                        case BUSY -> sender.sendMessage(ChatColor.YELLOW + "Apply again when disc golf has no active, queued, or preparing rounds.");
                        case BACKUP_BUSY -> sender.sendMessage(ChatColor.YELLOW + "Apply again when the backup operation finishes.");
                    }
                }
                case "status" -> status(sender, plugin, settings);
                default -> sender.sendMessage(ChatColor.YELLOW + "Use /ndg admin [wizard|backup|upgrade|status|check|list|get|set|reset|apply].");
            }
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage(ChatColor.RED + invalid.getMessage());
        } catch (IOException failure) {
            sender.sendMessage(ChatColor.RED + "Could not read or write NormalDiscGolf settings. Check config.yml and the server log.");
            plugin.getLogger().log(Level.WARNING, "Admin settings action failed", failure);
        }
    }

    private static void wizard(CommandSender sender, NormalDiscGolfPlugin plugin, String[] args) throws IOException {
        if (args.length == 2 && sender instanceof Player player) {
            SetupWizardMenu.open(player);
        } else if (args.length == 4 && args[2].equalsIgnoreCase("url")) {
            plugin.setupWizard().setPackUrl(args[3]);
            sender.sendMessage(ChatColor.GREEN + "Saved the HTTPS pack URL. Verify the download before restarting to use it.");
            if (sender instanceof Player player) SetupWizardMenu.open(player);
        } else if (args.length == 3 && args[2].equalsIgnoreCase("verify")) {
            plugin.setupWizard().verify(sender, () -> {});
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Open /ndg admin wizard in game. Use wizard url <https-url> or wizard verify from the console.");
        }
    }

    private static void staged(CommandSender sender, AdminSettings settings, String key, boolean changed) {
        if (!changed) {
            sender.sendMessage(ChatColor.GRAY + "The saved value is already set that way.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Saved " + key + " to config.yml.");
        sender.sendMessage(ChatColor.GRAY + (settings.schema().restartOnly(key)
                ? "Restart the server to apply this startup setting."
                : "Use /ndg admin apply when disc golf is idle."));
    }

    private static void status(CommandSender sender, NormalDiscGolfPlugin plugin, AdminSettings settings) throws IOException {
        var config = plugin.getConfig();
        var course = CourseGenerationProfile.serverDefault(config);
        var manager = NDGManager.getInstance();
        sender.sendMessage(ChatColor.GOLD + "NormalDiscGolf setup");
        sender.sendMessage(ChatColor.GRAY + "Default course • " + course.holes() + " holes • " + course.terrains().stream()
                .map(terrain -> terrain.label()).collect(java.util.stream.Collectors.joining(", ")));
        sender.sendMessage(ChatColor.GRAY + "Clubhouse • " + (manager.getClubhouse().available() ? "Ready"
                : config.getBoolean("clubhouse.enabled", true) ? "Unavailable" : "Disabled"));
        String pack;
        try {
            var integration = manager.getPacked();
            pack = !config.getBoolean("resource-pack.enabled", true) ? "Disabled"
                    : integration.getPackUrl().isBlank() ? "Exported ZIP, delivery unavailable"
                    : integration.isLocalOnly() ? "Local clients only" : "Configured for remote clients";
        } catch (IOException failure) {
            pack = "Unavailable, see server log";
        }
        sender.sendMessage(ChatColor.GRAY + "Resource pack • " + pack);
        var pending = settings.pending(settings.read());
        sender.sendMessage(ChatColor.GRAY + "Saved changes pending • " + pending.size()
                + (pending.stream().anyMatch(settings.schema()::restartOnly) ? " • Server restart required" : ""));
        if (sender instanceof Player) sender.sendMessage(ChatColor.GRAY + "Use /ndg admin to open the settings menu.");
    }

    private static void showCheck(CommandSender sender, SettingsSchema.Check check) {
        sender.sendMessage(check.valid() ? ChatColor.GREEN + "Config check passed."
                : ChatColor.RED + "Config check failed. Correct the errors before applying.");
        for (String issue : check.errors()) sender.sendMessage(ChatColor.RED + issue);
        for (String warning : check.warnings()) sender.sendMessage(ChatColor.YELLOW + warning);
    }

    private static void requireCount(String[] args, int length) {
        if (args.length != length) throw new IllegalArgumentException("Check the syntax with /ndg admin.");
    }

    static List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(AdminSettings.PERMISSION)) return List.of();
        if (args.length == 2) return ACTIONS;
        if (args.length == 3 && args[1].equalsIgnoreCase("wizard")) return List.of("url", "verify");
        if (args.length == 3 && args[1].equalsIgnoreCase("backup"))
            return List.of("status", "create", "list", "preview", "restore", "cancel", "probe", "upload", "verify", "download");
        if (args.length == 3 && args[1].equalsIgnoreCase("upgrade")) return List.of("check");
        if (args.length != 3 || !List.of("list", "get", "set", "reset").contains(args[1].toLowerCase(Locale.ROOT))) return List.of();
        var plugin = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class);
        return List.copyOf(plugin.adminSettings().schema().keys(plugin.getConfig()));
    }

}
