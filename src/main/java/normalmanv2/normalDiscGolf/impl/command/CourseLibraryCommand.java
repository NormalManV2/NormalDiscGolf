package normalmanv2.normalDiscGolf.impl.command;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.library.CourseLibraryEntry;
import normalmanv2.normalDiscGolf.impl.course.library.CourseLibraryStore;
import normalmanv2.normalDiscGolf.impl.gui.CourseDetailsMenu;
import normalmanv2.normalDiscGolf.impl.gui.CourseLibraryMenu;
import normalmanv2.normalDiscGolf.impl.manager.round.queue.RoundQueueManager.QueueResult;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;

final class CourseLibraryCommand {
    enum SharedAction {
        PREVIEW, SAVE, PLAY, BOARD, RECORDS, PLAY_RECORD;

        boolean queues() { return this == PLAY || this == PLAY_RECORD; }
    }

    private CourseLibraryCommand() {}

    static void execute(Player player, String[] args, Function<QueueResult, String> queueMessage) {
        if (args.length == 1) {
            CourseLibraryMenu.open(player);
            return;
        }
        var manager = NDGManager.getInstance();
        var store = manager.getCourseLibrary();
        String action = args[1].toLowerCase(Locale.ROOT);
        UUID owner = player.getUniqueId();
        try {
            if (action.equals("code") && args.length == 2) {
                CourseLibraryMenu.promptForCode(player);
                return;
            }
            if (args.length < 3 || (action.equals("rename") ? args.length < 4 : args.length != 3)) {
                usage(player);
                return;
            }

            Team party = manager.getPartyManager().getPartyById(owner);
            if ((action.equals("play") || action.equals("play-code") || action.equals("play-record")
                    || action.equals("record-code")) && !canQueue(player, party)) return;
            switch (action) {
                case "code", "save-code", "play-code", "board-code", "records-code", "record-code" ->
                        useShare(player, store, action, args[2], party, queueMessage);
                case "manage", "play", "rename", "share", "revoke", "board", "records", "play-record", "favorite", "unfavorite" ->
                        useOwnedCourse(player, store, action, args, party, queueMessage);
                default -> usage(player);
            }
        } catch (IOException failure) {
            player.sendMessage(ChatColor.RED + "Could not complete that Course Library action. Please try again.");
            NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getLogger()
                    .log(Level.WARNING, "Course Library " + action + " failed for " + owner, failure);
        }
    }

    private static void useOwnedCourse(Player player, CourseLibraryStore store, String action, String[] args,
                                       Team party, Function<QueueResult, String> queueMessage) throws IOException {
        UUID id;
        try {
            id = UUID.fromString(args[2]);
        } catch (IllegalArgumentException invalid) {
            unavailable(player);
            return;
        }
        UUID owner = player.getUniqueId();
        var entry = store.find(owner, id).orElse(null);
        if (entry == null) {
            unavailable(player);
            return;
        }
        switch (action) {
            case "manage" -> openOwned(player, store, entry);
            case "play", "play-record" -> queue(player, entry, party, action.equals("play-record"), queueMessage);
            case "records" -> CourseRecordsCommand.records(player, entry);
            case "board" -> CourseRecordsCommand.board(player, entry, "library manage " + id, "library records " + id);
            case "favorite", "unfavorite" -> {
                boolean favorite = action.equals("favorite");
                var updated = store.setFavorite(owner, id, favorite).orElse(null);
                if (updated == null) {
                    unavailable(player);
                    return;
                }
                player.sendMessage(ChatColor.GREEN + (favorite ? "Added to your favorites." : "Removed from your favorites."));
                openOwned(player, store, updated);
            }
            case "rename" -> {
                String name;
                try {
                    name = CourseLibraryEntry.validateName(String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                } catch (IllegalArgumentException invalid) {
                    player.sendMessage(ChatColor.RED + invalid.getMessage());
                    return;
                }
                if (!store.rename(owner, id, name)) {
                    unavailable(player);
                    return;
                }
                player.sendMessage(ChatColor.GREEN + "Course renamed to " + name + ".");
                openOwned(player, store, new CourseLibraryEntry(entry.course(), name, entry.favorite()));
            }
            case "share" -> {
                var code = store.share(owner, id);
                if (code.isEmpty()) {
                    unavailable(player);
                    return;
                }
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Share code for " + entry.displayName() + ": " + code.get());
                var copy = new TextComponent("[Copy " + code.get() + "]");
                copy.setColor(net.md_5.bungee.api.ChatColor.AQUA);
                copy.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code.get()));
                player.spigot().sendMessage(copy);
            }
            case "revoke" -> {
                if (store.revokeShare(owner, id)) player.sendMessage(ChatColor.GREEN + "Course share code revoked.");
                openOwned(player, store, entry);
            }
            default -> throw new IllegalArgumentException("Unexpected Course Library action");
        }
    }

    private static void useShare(Player player, CourseLibraryStore store, String action, String input,
                                 Team party, Function<QueueResult, String> queueMessage) throws IOException {
        var entry = store.resolveShare(input).orElse(null);
        if (entry == null) {
            player.sendMessage(ChatColor.RED + "That course share code is invalid or unavailable.");
            return;
        }
        SharedAction sharedAction = switch (action) {
            case "play-code" -> SharedAction.PLAY;
            case "save-code" -> SharedAction.SAVE;
            case "board-code" -> SharedAction.BOARD;
            case "records-code" -> SharedAction.RECORDS;
            case "record-code" -> SharedAction.PLAY_RECORD;
            default -> SharedAction.PREVIEW;
        };
        useSharedEntry(player, store, sharedAction, input, entry, party, false, queueMessage);
    }

    static void useSharedEntry(Player player, CourseLibraryStore store, SharedAction action, String code,
                               CourseLibraryEntry entry, Team party, boolean featured,
                               Function<QueueResult, String> queueMessage) throws IOException {
        if (action.queues()) {
            queue(player, entry, party, action == SharedAction.PLAY_RECORD, queueMessage);
            return;
        }
        if (action == SharedAction.RECORDS) {
            CourseRecordsCommand.records(player, entry);
            return;
        }
        if (action == SharedAction.BOARD) {
            CourseRecordsCommand.board(player, entry, (featured ? "featured preview " : "library code ") + code,
                    (featured ? "featured records " : "library records-code ") + code);
            return;
        }
        UUID owner = player.getUniqueId();
        boolean saved;
        if (action == SharedAction.SAVE) {
            boolean added = store.save(owner, entry);
            player.sendMessage(ChatColor.GREEN + (added ? "Shared course saved to your Course Library."
                    : "This layout is already in your Course Library."));
            saved = true;
        } else {
            saved = store.find(owner, entry.course().id()).isPresent();
        }
        CourseDetailsMenu.openShared(player, entry, code.toUpperCase(Locale.ROOT), saved, featured);
    }

    static boolean canQueue(Player player, Team party) {
        if (party == null || party.getOwner().equals(player.getUniqueId())) return true;
        player.sendMessage(ChatColor.RED + "Only the party leader can queue the party.");
        return false;
    }

    private static void openOwned(Player player, CourseLibraryStore store, CourseLibraryEntry entry) throws IOException {
        var code = store.shareCode(player.getUniqueId(), entry.course().id()).orElse(null);
        CourseDetailsMenu.openOwned(player, entry, code);
    }

    private static void queue(Player player, CourseLibraryEntry entry, Team party, boolean forRecord,
                              Function<QueueResult, String> queueMessage) {
        var members = party == null ? List.of(player.getUniqueId()) : party.getTeamMembers();
        var result = NDGManager.getInstance().getRoundQueueManager().addPlayersToSavedCourse(members, entry.course(), forRecord);
        if (result != QueueResult.SUCCESS) {
            player.sendMessage(ChatColor.RED + queueMessage.apply(result));
            return;
        }
        player.closeInventory();
        String course = entry.displayName() + " from the Course Library.";
        if (party == null) {
            player.sendMessage(ChatColor.GREEN + "Queued " + course);
        } else {
            for (UUID member : members) {
                Player online = Bukkit.getPlayer(member);
                if (online != null) online.sendMessage(ChatColor.GREEN + "Your party queued " + course);
            }
        }
    }

    private static void unavailable(Player player) {
        player.sendMessage(ChatColor.RED + "That course is unavailable in your library.");
    }

    private static void usage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Use /ndg library to browse courses or /ndg library code <code> to open a shared layout.");
    }
}
