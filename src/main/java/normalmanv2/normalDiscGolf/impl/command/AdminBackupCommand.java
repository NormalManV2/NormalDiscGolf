package normalmanv2.normalDiscGolf.impl.command;

import normalmanv2.normalDiscGolf.impl.util.Bounds;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.admin.AdminSettings;
import normalmanv2.normalDiscGolf.impl.admin.BackupTasks;
import normalmanv2.normalDiscGolf.impl.admin.DataBackups;
import normalmanv2.normalDiscGolf.impl.admin.DataBackups.RestoreAction;
import normalmanv2.normalDiscGolf.impl.gui.AdminBackupsMenu;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

final class AdminBackupCommand {
    private static final int PAGE_SIZE = 10;
    private static final Map<String, Review> REVIEWS = new HashMap<>();
    private record Review(String archive, String fingerprint, String code, Instant expires) {}
    private AdminBackupCommand() {}

    static void backup(CommandSender sender, NormalDiscGolfPlugin plugin, String[] args) {
        var backups = plugin.dataBackups();
        if (args.length == 2 && sender instanceof Player player) {
            AdminBackupsMenu.open(player);
            return;
        }
        String action = args.length == 2 ? "list" : args[2].toLowerCase(Locale.ROOT);
        if (action.equals("status") && args.length == 3) {
            var schedule = plugin.backupTasks();
            var policy = schedule.policy();
            sender.sendMessage(ChatColor.GOLD + "Automatic backups • " + (policy.enabled() ? "Enabled" : "Disabled"));
            sender.sendMessage(ChatColor.GRAY + "Interval " + policy.intervalSeconds() + " seconds • Keep "
                    + policy.keep() + " automatic backups");
            var offsite = schedule.offsite();
            sender.sendMessage(ChatColor.GRAY + "Off-server copies • " + (offsite == null ? "Invalid settings"
                    : offsite.enabled() ? "Configured" : "Disabled"));
            if (schedule.next() != null)
                sender.sendMessage(ChatColor.GRAY + "Next backup due " + schedule.next().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
            if (schedule.running()) sender.sendMessage(ChatColor.GRAY + "A backup operation is in progress.");
            sender.sendMessage(ChatColor.GRAY + "Use /ndg admin list backups to view the settings.");
        } else if (action.equals("create") && args.length == 3) {
            var offsite = plugin.backupTasks().offsite();
            run(sender, plugin, true, () -> {
                var created = backups.create();
                String remoteReport = mirror(offsite, created.filename());
                return to -> {
                    to.sendMessage(ChatColor.GREEN + "Backup created • " + created.filename());
                    to.sendMessage(ChatColor.GRAY + "Files " + created.files() + " • Course baselines " + created.checkedCourses()
                            + " • Courses to review " + created.flaggedCourses());
                    if (remoteReport != null) to.sendMessage(remoteReport);
                    showArchiveActions(to, created.filename());
                };
            });
        } else if (action.equals("probe") && args.length == 3) {
            var offsite = plugin.backupTasks().offsite();
            if (offsite == null || !offsite.enabled()) throw new IllegalArgumentException("Set backups.offsite.url and apply the settings first.");
            run(sender, plugin, false, () -> {
                String filename = offsite.probe();
                return to -> {
                    to.sendMessage(ChatColor.GREEN + "Off-server probe passed. ZIP upload, SHA-256 read-back, and overwrite protection verified.");
                    to.sendMessage(ChatColor.GRAY + "Synthetic files created: " + filename + " and " + filename + ".sha256");
                };
            });
        } else if (args.length == 4 && List.of("upload", "verify", "download").contains(action)) {
            var offsite = plugin.backupTasks().offsite();
            if (offsite == null || !offsite.enabled()) throw new IllegalArgumentException("Set backups.offsite.url and apply the settings first.");
            String filename = args[3];
            run(sender, plugin, false, () -> {
                String digest = switch (action) {
                    case "upload" -> offsite.upload(filename);
                    case "verify" -> offsite.verify(filename);
                    default -> offsite.download(filename);
                };
                return to -> {
                    to.sendMessage(ChatColor.GREEN + (action.equals("download") ? "Off-server backup downloaded and verified: "
                            : "Off-server backup verified: ") + filename);
                    to.sendMessage(ChatColor.GRAY + "SHA-256 " + digest);
                    if (action.equals("download")) showArchiveActions(to, filename);
                };
            });
        } else if (action.equals("list") && args.length <= 4) {
            int page = args.length == 4 ? page(args[3]) : 1;
            run(sender, plugin, false, () -> {
                var names = backups.list();
                String pending = plugin.dataRestore().pendingArchive();
                return to -> {
                    to.sendMessage(ChatColor.GOLD + "NormalDiscGolf backups");
                    if (pending != null) to.sendMessage(ChatColor.YELLOW + "Restore scheduled for next restart: "
                            + pending + "  •  /ndg admin backup cancel");
                    if (names.isEmpty()) to.sendMessage(ChatColor.GRAY + "Use /ndg admin backup create to save your first archive.");
                    for (String filename : page(to, names, page)) {
                        to.sendMessage(ChatColor.AQUA + filename);
                        showArchiveActions(to, filename);
                    }
                };
            });
        } else if (action.equals("preview") && (args.length == 4 || args.length == 5)) {
            int page = args.length == 5 ? page(args[4]) : 1;
            run(sender, plugin, false, () -> {
                var preview = backups.preview(args[3]);
                return to -> showPreview(to, args[3], preview, page);
            });
        } else if (action.equals("restore") && args.length == 5) {
            var review = REVIEWS.get(senderKey(sender));
            if (review == null || !review.expires().isAfter(Instant.now())
                    || !review.archive().equals(args[3]) || !review.code().equals(args[4])) {
                sender.sendMessage(ChatColor.YELLOW + "Open a fresh restore preview for this backup and use its confirmation code.");
                return;
            }
            run(sender, plugin, false, () -> {
                plugin.dataRestore().queue(review.archive(), review.fingerprint());
                return to -> {
                    REVIEWS.remove(senderKey(to), review);
                    to.sendMessage(ChatColor.GREEN + "Restore scheduled for next restart: " + review.archive());
                    to.sendMessage(ChatColor.YELLOW + "Restart the server to create the pre-restore backup and apply the changes. "
                            + "Use /ndg admin backup cancel to cancel before restarting.");
                };
            });
        } else if (action.equals("cancel") && args.length == 3) {
            run(sender, plugin, false, () -> {
                boolean canceled = plugin.dataRestore().cancel();
                return to -> to.sendMessage(ChatColor.YELLOW + (canceled ? "Scheduled restore canceled." : "No restore is scheduled."));
            });
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Use /ndg admin backup status, create, list [page], preview <archive.zip> [page], "
                    + "restore <archive.zip> <confirmation-code>, or cancel.");
            sender.sendMessage(ChatColor.GRAY + "Off-server commands: /ndg admin backup probe or upload|verify|download <archive.zip>.");
        }
    }

    private static String mirror(normalmanv2.normalDiscGolf.impl.admin.OffsiteBackups offsite, String filename) {
        if (offsite == null || !offsite.enabled()) return null;
        try {
            offsite.upload(filename);
            return ChatColor.GREEN + "Off-server backup verified: " + filename;
        } catch (IOException failure) {
            return ChatColor.YELLOW + "Local ZIP saved. Off-server upload failed. " + failure.getMessage();
        }
    }

    static void upgrade(CommandSender sender, NormalDiscGolfPlugin plugin, String[] args) {
        if (args.length < 3 || args.length > 5 || !args[2].equalsIgnoreCase("check")) {
            sender.sendMessage(ChatColor.YELLOW + "Use /ndg admin upgrade check [archive.zip|current] [page].");
            return;
        }
        String filename = args.length >= 4 && !args[3].equalsIgnoreCase("current") ? args[3] : null;
        int page = args.length == 5 ? page(args[4]) : 1;
        run(sender, plugin, false, () -> {
            var check = plugin.dataBackups().check(filename);
            return to -> showUpgrade(to, check, page);
        });
    }

    private static void showPreview(CommandSender sender, String archive, DataBackups.RestorePreview preview, int page) {
        var rows = preview.rows();
        long replacements = count(rows, RestoreAction.REPLACE);
        long additions = count(rows, RestoreAction.ADD);
        sender.sendMessage(ChatColor.GOLD + "Restore preview • Backup build " + preview.fromVersion());
        sender.sendMessage(ChatColor.AQUA + "Replace " + replacements
                + " • Add " + additions + " • Keep current " + count(rows, RestoreAction.KEEP_CURRENT)
                + " • Identical " + count(rows, RestoreAction.IDENTICAL));
        for (var row : page(sender, rows, page)) {
            String action = row.action().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            sender.sendMessage((row.action() == RestoreAction.REPLACE ? ChatColor.YELLOW : ChatColor.GRAY)
                    + action + " • " + row.file());
        }
        if (replacements + additions == 0 || page > Bounds.ceilDiv(rows.size(), PAGE_SIZE)) return;
        sender.sendMessage(ChatColor.YELLOW + "Confirm to schedule this restore for the next server restart. "
                + "NDG backs up current data first and checks that the reviewed changes still match.");
        var now = Instant.now();
        REVIEWS.values().removeIf(review -> !review.expires().isAfter(now));
        String key = senderKey(sender);
        var review = REVIEWS.get(key);
        if (review == null || !review.archive().equals(archive) || !review.fingerprint().equals(preview.fingerprint())) {
            review = new Review(archive, preview.fingerprint(), UUID.randomUUID().toString().substring(0, 8), now.plusSeconds(900));
            REVIEWS.put(key, review);
        }
        String confirm = "/ndg admin backup restore " + archive + " " + review.code();
        if (sender instanceof Player player) {
            var button = new TextComponent("[Confirm Restore on Restart]");
            button.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
            button.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, confirm));
            player.spigot().sendMessage(button);
        }
        sender.sendMessage(ChatColor.GRAY + (sender instanceof Player ? confirm : confirm.substring(1)));
    }

    private static String senderKey(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId().toString() : sender.getClass().getName() + "/" + sender.getName();
    }

    private static long count(List<DataBackups.RestoreRow> rows, RestoreAction action) {
        return rows.stream().filter(row -> row.action() == action).count();
    }

    private static void showUpgrade(CommandSender sender, DataBackups.UpgradeCheck check, int page) {
        var rows = check.rows();
        long passed = rows.stream().filter(DataBackups.CourseRow::compatible).count();
        sender.sendMessage(ChatColor.GOLD + "Generated course upgrade check • Build " + check.currentVersion());
        if (check.baselineVersion() != null) sender.sendMessage(ChatColor.GRAY + "Backup baseline • Build " + check.baselineVersion());
        sender.sendMessage((passed == rows.size() ? ChatColor.GREEN : ChatColor.YELLOW)
                + "Compatible " + passed + " • Review " + (rows.size() - passed));
        if (rows.isEmpty()) sender.sendMessage(ChatColor.GRAY + "No generated Course Library layouts found.");
        for (var row : page(sender, rows, page)) sender.sendMessage((row.compatible() ? ChatColor.GRAY : ChatColor.YELLOW)
                + row.file() + " • " + row.detail());
    }

    private static void showArchiveActions(CommandSender sender, String filename) {
        if (!(sender instanceof Player player)) return;
        var preview = new TextComponent("[Preview Restore] ");
        preview.setColor(net.md_5.bungee.api.ChatColor.AQUA);
        preview.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ndg admin backup preview " + filename));
        var check = new TextComponent("[Check Saved Layouts]");
        check.setColor(net.md_5.bungee.api.ChatColor.AQUA);
        check.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ndg admin upgrade check " + filename));
        player.spigot().sendMessage(preview, check);
    }

    private static int page(String value) {
        try {
            int number = Integer.parseInt(value);
            if (number > 0) return number;
        } catch (NumberFormatException invalid) { }
        throw new IllegalArgumentException("Choose a positive page number.");
    }

    private static <T> List<T> page(CommandSender sender, List<T> rows, int page) {
        int pages = Math.max(1, Bounds.ceilDiv(rows.size(), PAGE_SIZE));
        if (page > pages) {
            sender.sendMessage(ChatColor.YELLOW + "Choose a page from 1 to " + pages + ".");
            return List.of();
        }
        if (pages > 1) sender.sendMessage(ChatColor.GRAY + "Page " + page + "/" + pages);
        return rows.subList((page - 1) * PAGE_SIZE, Math.min(page * PAGE_SIZE, rows.size()));
    }

    private static void run(CommandSender sender, NormalDiscGolfPlugin plugin, boolean flush,
                            BackupTasks.Work<Consumer<CommandSender>> job) {
        var tasks = plugin.backupTasks();
        if (tasks.running()) {
            sender.sendMessage(ChatColor.YELLOW + "A backup or upgrade check is already running.");
            return;
        }
        sender.sendMessage(ChatColor.GRAY + "Working on NDG data…");
        tasks.submit(flush, job, (result, failure) -> {
            boolean reachable = sender.hasPermission(AdminSettings.PERMISSION)
                    && (!(sender instanceof Player player) || player.isOnline());
            if (failure == null) {
                if (reachable) result.accept(sender);
            } else if (failure instanceof IOException) {
                if (reachable) sender.sendMessage(ChatColor.RED + failure.getMessage());
            } else {
                plugin.getLogger().log(Level.WARNING, "NDG backup operation failed", failure);
                if (reachable) sender.sendMessage(ChatColor.RED + "The backup operation failed. See the server log.");
            }
        });
    }
}
