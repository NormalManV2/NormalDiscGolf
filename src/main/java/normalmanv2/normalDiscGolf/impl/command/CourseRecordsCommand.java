package normalmanv2.normalDiscGolf.impl.command;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.competition.StrokeStandings;
import normalmanv2.normalDiscGolf.impl.course.library.CourseLibraryEntry;
import normalmanv2.normalDiscGolf.impl.gui.CourseRecordsMenu;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

final class CourseRecordsCommand {
    private CourseRecordsCommand() {}

    static void board(Player player, CourseLibraryEntry entry, String back, String chat) throws IOException {
        CourseRecordsMenu.open(player, entry, standings(entry), "ndg " + back, "ndg " + chat);
    }

    static void records(Player player, CourseLibraryEntry entry) throws IOException {
        var standings = standings(entry);
        player.closeInventory();
        player.sendMessage(ChatColor.GOLD + "Course records • " + entry.displayName());
        if (standings.isEmpty()) player.sendMessage(ChatColor.GRAY + "Hole out on every hole in a saved-layout stroke round to post a score.");
        for (var standing : standings.subList(0, Math.min(10, standings.size()))) {
            player.sendMessage(ChatColor.GRAY + standing.line());
        }
        for (var standing : standings) if (standing.entry().player().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.AQUA + "Your course best: " + standing.entry().score() + " • Place " + standing.place());
            break;
        }
    }

    private static List<StrokeStandings.Standing> standings(CourseLibraryEntry entry) throws IOException {
        return StrokeStandings.rank(NDGManager.getInstance().getCourseRecords().scores(entry.course()).values());
    }

    static void ghost(Player player, GameRound active, String[] args) {
        if (!(active instanceof RoundImpl live) || !live.getCourse().isLibraryReplay() || live.play().ghosts() == null) {
            player.sendMessage(ChatColor.YELLOW + "Course ghosts are available while playing a saved-layout stroke round.");
            return;
        }
        live.play().ghosts().command(player, args.length == 2 ? "status"
                : args.length == 3 ? args[2].toLowerCase(Locale.ROOT) : "");
    }
}
