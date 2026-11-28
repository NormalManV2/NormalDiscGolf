package normalmanv2.normalDiscGolf.impl.command;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.command.CourseLibraryCommand.SharedAction;
import normalmanv2.normalDiscGolf.impl.course.library.CourseLibraryStore;
import normalmanv2.normalDiscGolf.impl.gui.FeaturedCoursesMenu;
import normalmanv2.normalDiscGolf.impl.manager.round.queue.RoundQueueManager.QueueResult;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Level;

final class FeaturedCoursesCommand {
    private FeaturedCoursesCommand() {}

    static void execute(CommandSender sender, String[] args, Function<QueueResult, String> queueMessage) {
        if (args.length == 1 && sender instanceof Player player) {
            FeaturedCoursesMenu.open(player);
            return;
        }
        if (args.length != 3) {
            usage(sender);
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        String code = args[2].toUpperCase(Locale.ROOT);
        var manager = NDGManager.getInstance();
        var store = manager.getCourseLibrary();
        try {
            if (action.equals("pin") || action.equals("unpin")) {
                update(sender, store, action, code);
                return;
            }
            SharedAction sharedAction = switch (action) {
                case "preview" -> SharedAction.PREVIEW;
                case "save" -> SharedAction.SAVE;
                case "play" -> SharedAction.PLAY;
                case "board" -> SharedAction.BOARD;
                case "records" -> SharedAction.RECORDS;
                case "play-record" -> SharedAction.PLAY_RECORD;
                default -> null;
            };
            if (!(sender instanceof Player player) || sharedAction == null) {
                usage(sender);
                return;
            }
            var entry = store.resolveFeatured(code).orElse(null);
            if (entry == null) {
                player.sendMessage(ChatColor.RED + "That course is no longer on the Featured Courses shelf.");
                FeaturedCoursesMenu.open(player);
                return;
            }
            var party = manager.getPartyManager().getPartyById(player.getUniqueId());
            if (sharedAction.queues() && !CourseLibraryCommand.canQueue(player, party)) return;
            CourseLibraryCommand.useSharedEntry(player, store, sharedAction, code, entry, party, true, queueMessage);
        } catch (IOException failure) {
            sender.sendMessage(ChatColor.RED + "Could not complete that Featured Courses action. Please try again.");
            NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getLogger()
                    .log(Level.WARNING, "Featured Courses " + action + " failed", failure);
        }
    }

    private static void update(CommandSender sender, CourseLibraryStore store, String action, String code) throws IOException {
        if (!sender.hasPermission(FeaturedCoursesMenu.MANAGE_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to manage Featured Courses.");
            return;
        }
        if (action.equals("pin")) {
            switch (store.pin(code)) {
                case UNAVAILABLE -> {
                    sender.sendMessage(ChatColor.RED + "That course share code is invalid or unavailable.");
                    return;
                }
                case ALREADY_PINNED -> {
                    sender.sendMessage(ChatColor.YELLOW + "That course is already featured.");
                    return;
                }
                case PINNED -> sender.sendMessage(ChatColor.GREEN + "Course pinned to Featured Courses.");
            }
        } else if (store.unpin(code)) {
            sender.sendMessage(ChatColor.GREEN + "Course removed from Featured Courses.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "That course is no longer on the Featured Courses shelf.");
        }
        if (sender instanceof Player player) FeaturedCoursesMenu.open(player);
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + (sender.hasPermission(FeaturedCoursesMenu.MANAGE_PERMISSION)
                ? "Use /ndg featured pin <code> or /ndg featured unpin <code>."
                : "Use /ndg featured to browse Featured Courses."));
    }
}
